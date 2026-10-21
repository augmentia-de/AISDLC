package com.echobox.delivery;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.echobox.common.ClockPort;
import com.echobox.common.IdGenerator;
import com.echobox.crypto.PayloadCipher;
import com.echobox.crypto.SignatureService;
import com.echobox.endpoint.EndpointRepository;
import com.echobox.endpoint.EndpointStatus;
import com.echobox.event.EventRepository;

/**
 * Der Faecher des Zustellzyklus: nimmt einen beanspruchten Lauf, holt die
 * Nutzlast, signiert, versendet, wertet aus und protokolliert (ADR-006 §3).
 *
 * <p><strong>Der Request-Body sind die Nutzlastbytes</strong> — keine Huelle,
 * kein Envelope (AC-007-3, ADR-005). Alle Metadaten stehen in den Kopfzeilen.
 *
 * <p><strong>Die Signatur</strong> ist HMAC-SHA256 ueber
 * {@code <timestamp>.<payload_bytes>} mit dem Endpunkt-Secret; der Headerwert
 * traegt den Praefix {@code v1=} (ADR-004). Bei vorverschluesselten Ereignissen
 * (FR-013) ist die Chiffre die gesendete Nutzlast — die Signatur deckt genau
 * diese Bytes.
 *
 * <p><strong>Der Zeitpunkt der Zustellung</strong> ist der Zeitstempel, der
 * auch in die Signier-Eingabe eingeht — er darf nicht ein zweites Mal
 * formatiert werden (ADR-004).
 */
@Component
public class DeliveryDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryDispatcher.class);

    /** Der Fehlercode eines Verbindungs- oder Lese-Timeouts. */
    public static final String ERROR_READ_TIMEOUT = "read_timeout";

    /** Der Fehlercode eines Netzwerkfehlers ohne Timeout. */
    public static final String ERROR_CONNECTION_REFUSED = "connection_refused";

    private final DeliveryRepository repository;

    private final EndpointRepository endpointRepository;

    private final EventRepository eventRepository;

    private final PayloadCipher cipher;

    private final SignatureService signatureService;

    private final DeliveryPort deliveryPort;

    private final RateLimitGuard rateLimitGuard;

    private final BackoffPolicy backoff;

    private final ClockPort clock;

    private final IdGenerator idGenerator;

    /**
     * Erzeugt den Faecher.
     *
     * @param repository         der JDBC-Adapter des Zustellmoduls
     * @param endpointRepository der JDBC-Adapter des Endpunktmoduls
     * @param eventRepository    der JDBC-Adapter des Ereignismoduls
     * @param cipher             die Chiffre der Nutzlast und der Secrets
     * @param signatureService   die Signatur der Zustellung
     * @param deliveryPort       der ausgehende Zustellzugriff
     * @param rateLimitGuard     der Schutz des Endpunkts
     * @param backoff            die Wartezeit zwischen zwei Versuchen
     * @param clock              die Uhr des Dienstes
     * @param idGenerator        die Erzeugung der Kennungen (UUID-v7)
     */
    public DeliveryDispatcher(DeliveryRepository repository,
                              EndpointRepository endpointRepository,
                              EventRepository eventRepository,
                              PayloadCipher cipher,
                              SignatureService signatureService,
                              DeliveryPort deliveryPort,
                              RateLimitGuard rateLimitGuard,
                              BackoffPolicy backoff,
                              ClockPort clock,
                              IdGenerator idGenerator) {
        this.repository = repository;
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.cipher = cipher;
        this.signatureService = signatureService;
        this.deliveryPort = deliveryPort;
        this.rateLimitGuard = rateLimitGuard;
        this.backoff = backoff;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Fuehrt den beanspruchten Lauf aus: Ziel und Nutzlast holen, signieren,
     * senden, auswerten und protokollieren.
     *
     * @param run        der beanspruchte Zustelllauf
     * @param instanceId die beanspruchende Instanz
     */
    public void dispatch(DeliveryRun run, String instanceId) {
        String organisationId = run.organisationId();
        Instant now = clock.now();

        Optional<EndpointRepository.DeliveryTarget> target =
                endpointRepository.findDeliveryTarget(organisationId, run.endpointId());
        if (target.isEmpty()) {
            cancel(run, "Der Endpunkt existiert nicht mehr.");
            return;
        }
        if (target.get().status() == EndpointStatus.PAUSED) {
            // Ein pausierter Endpunkt erhaelt keine Zustellung (D-03).
            cancel(run, "Der Endpunkt ist pausiert.");
            return;
        }

        Optional<EventRepository.PayloadMaterial> payload =
                eventRepository.findPayload(organisationId, run.eventId());
        if (payload.isEmpty()) {
            cancel(run, "Das Ereignis existiert nicht mehr.");
            return;
        }

        RateLimitDecision decision = rateLimitGuard.acquire(organisationId, run.endpointId(), now);
        if (!decision.permitted()) {
            delay(run, decision.retryAt());
            return;
        }

        byte[] body = payloadBytes(payload.get());
        String secret = secret(target.get());
        UUID attemptId = idGenerator.newId();
        int attemptNumber = run.attemptCount() + 1;
        Instant timestamp = clock.now();
        String signature = signatureService.sign(secret, timestamp, body);
        Map<String, String> headers = DeliveryHeaders.build(
                run.eventId().toString(),
                run.deliveryRunId().toString(),
                attemptId.toString(),
                attemptNumber,
                run.eventType(),
                timestamp,
                signature);

        DeliveryResponse response = deliveryPort.deliver(new DeliveryRequest(
                target.get().targetUrl(), headers, body, payload.get().mediaType()));

        Instant finishedAt = clock.now();
        record(run, instanceId, attemptId, attemptNumber, payload.get().eventAcceptedAt(),
                timestamp, finishedAt, response);
    }

    /**
     * Die zu sendenden Bytes: die entschluesselte Nutzlast — oder bei einem
     * vorverschluesselten Ereignis die Chiffre unveraendert (FR-013).
     *
     * @param payload das gespeicherte Material
     * @return die gesendeten Bytes
     */
    private byte[] payloadBytes(EventRepository.PayloadMaterial payload) {
        if (payload.preEncrypted()) {
            return payload.ciphertext();
        }
        return cipher.decrypt(new PayloadCipher.Ciphertext(payload.ciphertext(), payload.nonce()));
    }

    /**
     * Das Endpunkt-Secret im Klartext — nur fuer die Signatur, nie fuer eine
     * Ausgabe und nie fuer ein Protokoll (AC-001-4).
     *
     * @param target das Zustellziel
     * @return das Secret
     */
    private String secret(EndpointRepository.DeliveryTarget target) {
        byte[] plaintext = cipher.decrypt(new PayloadCipher.Ciphertext(
                target.secretCiphertext(), target.secretNonce()));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private void record(DeliveryRun run,
                        String instanceId,
                        UUID attemptId,
                        int attemptNumber,
                        Instant eventAcceptedAt,
                        Instant startedAt,
                        Instant finishedAt,
                        DeliveryResponse response) {
        DeliveryOutcome outcome = classify(response);
        Integer responseStatus = response.statusCode();
        String errorCode = null;
        String rejectionReason = null;
        Instant nextAttemptAt = null;
        Instant abandonedAt = null;
        DeliveryStatus status;

        switch (outcome) {
            case SUCCESS -> status = DeliveryStatus.SENT;
            case PERMANENT_REJECTION -> {
                status = DeliveryStatus.REJECTED;
                rejectionReason = DeliveryOutcome.PERMANENT_REJECTION.wireValue();
            }
            case CLIENT_ERROR -> {
                status = DeliveryStatus.REJECTED;
                rejectionReason = DeliveryOutcome.CLIENT_ERROR.wireValue();
            }
            case CANCELLED -> status = DeliveryStatus.CANCELLED;
            default -> {
                if (outcome == DeliveryOutcome.TIMEOUT) {
                    errorCode = ERROR_READ_TIMEOUT;
                } else if (outcome == DeliveryOutcome.NETWORK_ERROR) {
                    errorCode = ERROR_CONNECTION_REFUSED;
                }
                AttemptLimit limit = attemptLimit(run);
                if (limit != null && limit.isExhausted(attemptNumber)) {
                    status = limit.statusWhenExhausted();
                    abandonedAt = finishedAt;
                } else {
                    status = DeliveryStatus.FAILED;
                    nextAttemptAt = backoff.nextAttemptAt(finishedAt, attemptNumber);
                }
            }
        }

        DeliveryAttempt attempt = new DeliveryAttempt(attemptId, run.organisationId(), run.deliveryRunId(),
                run.eventId(), run.endpointId(), eventAcceptedAt, attemptNumber, startedAt, finishedAt,
                Duration.between(startedAt, finishedAt).toMillis(), responseStatus, outcome, errorCode,
                nextAttemptAt);

        boolean terminal = status.isTerminal();
        repository.recordAttempt(run.organisationId(), attempt, status, attemptNumber, nextAttemptAt,
                finishedAt, responseStatus, errorCode, rejectionReason, abandonedAt,
                terminal ? instanceId : null);

        LOG.info("Zustelllauf {} im Mandant {}: Versuch {} endete mit {} (Status {}, naechster Versuch {}).",
                run.deliveryRunId(), run.organisationId(), attemptNumber, outcome.wireValue(), status.wireValue(),
                nextAttemptAt);
    }

    /**
     * Die Versuchsgrenze des Laufs — beim Anlegen festgeschrieben (F-04).
     *
     * @param run der Zustelllauf
     * @return die Grenze oder {@code null}, wenn keine Grenze gesetzt ist
     */
    private static AttemptLimit attemptLimit(DeliveryRun run) {
        Integer maxAttempts = run.maxAttempts();
        return maxAttempts == null ? null : new AttemptLimit(maxAttempts);
    }

    /**
     * Klassifiziert die Antwort des Empfaengers (D-08, ADR-009).
     *
     * <p>Die Trennlinie zwischen Timeout und Netzwerkfehler ist der
     * Transportfehler: ein Timeout wird als {@code timeout}, ein DNS-, TLS-
     * oder Verbindungsfehler ohne Timeout als {@code network_error}
     * ausgewiesen (AC-011-3). Beide fuehren zum selben Verhalten.
     *
     * @param response die Antwort
     * @return das Ergebnis
     */
    private static DeliveryOutcome classify(DeliveryResponse response) {
        if (response.failure() == DeliveryResponse.TransportFailure.TIMEOUT) {
            return DeliveryOutcome.TIMEOUT;
        }
        if (response.failure() == DeliveryResponse.TransportFailure.NETWORK_ERROR) {
            return DeliveryOutcome.NETWORK_ERROR;
        }
        return DeliveryOutcome.classify(response.statusCode(), isPermanentRejection(response));
    }

    /**
     * Prueft das Ablehnungssignal des Empfaengers (ADR-009).
     *
     * <p>Das Signal ist {@code X-EchoBox-Rejection: permanent} bei HTTP 410.
     * Die Kopfzeilennamen werden **ohne Rücksicht auf die Schreibweise**
     * verglichen — der HTTP-Adapter liefert sie in Kleinschreibung.
     *
     * @param response die Antwort
     * @return {@code true}, wenn das Ablehnungssignal vorliegt
     */
    private static boolean isPermanentRejection(DeliveryResponse response) {
        String value = headerValue(response.headers(), DeliveryHeaders.REJECTION);
        return value != null
                && DeliveryHeaders.REJECTION_VALUE_PERMANENT.equalsIgnoreCase(value.trim());
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String exact = headers.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Bricht den Lauf ab — der Endpunkt wurde pausiert oder das Ereignis
     * existiert nicht mehr (D-03).
     *
     * @param run    der Zustelllauf
     * @param reason der Grund (nur fuer das Protokoll)
     */
    private void cancel(DeliveryRun run, String reason) {
        repository.releaseRun(run.organisationId(), run.deliveryRunId(), DeliveryStatus.CANCELLED, null, null);
        LOG.info("Zustelllauf {} im Mandant {} abgebrochen: {}", run.deliveryRunId(), run.organisationId(), reason);
    }

    /**
     * Verschiebt den Lauf — das Endpunkt-Limit ist ueberschritten (FR-033).
     *
     * @param run      der Zustelllauf
     * @param retryAt  der Zeitpunkt des naechsten Versuchs
     */
    private void delay(DeliveryRun run, Instant retryAt) {
        repository.releaseRun(run.organisationId(), run.deliveryRunId(),
                DeliveryStateMachine.onRateLimited(run.status()), retryAt, null);
        LOG.info("Zustelllauf {} im Mandant {} verschoben — der naechste Versuch ist zum {} geplant.",
                run.deliveryRunId(), run.organisationId(), retryAt);
    }
}
