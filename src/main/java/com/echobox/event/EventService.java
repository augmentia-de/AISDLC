package com.echobox.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.CanonicalJson;
import com.echobox.common.ClockPort;
import com.echobox.common.ForbiddenException;
import com.echobox.common.IdempotencyConflictException;
import com.echobox.common.IdGenerator;
import com.echobox.common.NotFoundException;
import com.echobox.common.PayloadTooLargeException;
import com.echobox.config.EchoBoxProperties;
import com.echobox.crypto.PayloadCipher;
import com.echobox.delivery.DeliveryStatus;
import com.echobox.endpoint.EndpointRepository;
import com.echobox.endpoint.EndpointStatus;
import com.echobox.event.api.EventIntakeRequest;
import com.echobox.tenant.TenantContext;
import com.echobox.tenant.TenantTransactionRunner;

/**
 * Die Fachlogik des Ereignismoduls (FR-010 bis FR-013).
 *
 * <p><strong>Der ganze Vorgang laeuft in einer Transaktion</strong> (ADR-007):
 * Idempotenzabgleich, Ereignis und Zustelllaeufe entstehen gemeinsam — entweder
 * alle oder keine.
 *
 * <p><strong>Idempotenz</strong> (D-05): der Gueltigkeitsbereich des
 * Idempotenzschluessels ist das Paar (Mandant, Schluessel). Gleiche Nutzlast →
 * HTTP 202 mit {@code idempotent_replay: true} und der Kennung des
 * <strong>ersten</strong> Ereignisses; abweichende Nutzlast → HTTP 409. Die
 * erste Nutzlast bleibt <strong>unveraendert</strong>.
 *
 * <p><strong>Verschluesselung</strong> (ADR-003): Klartextnutzlast wird
 * verschluesselt abgelegt; vorverschluesselte Bytes werden <strong>unveraendert</strong>
 * uebernommen — kein zweites Entschluesseln, kein Neuverschluesseln (FR-013).
 *
 * <p><strong>Zustelllaeufe</strong> (D-04, D-03): fuer jeden <strong>aktiven</strong>
 * Endpunkt, der den Ereignistyp abonniert, entsteht ein Lauf. Ein
 * pausierter Endpunkt erhaelt <strong>keinen</strong> Lauf, zaehlt aber als
 * Abonnent. Ohne jeden Abnehmer traegt das Ereignis den Zustand
 * {@code no_subscriber} und <strong>einen</strong> Lauf ohne Endpunkt.
 */
@Service
public class EventService {

    private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

    /** Die Rolle, die Ereignisse einliefern darf. */
    public static final String ROLE_DEVELOPER = "developer";

    /** Die Laenge der Nonce — die Spalte verlangt genau 12 Byte. */
    private static final int NONCE_LENGTH = 12;

    private final EventRepository repository;

    private final IdempotencyStore idempotencyStore;

    private final EndpointRepository endpointRepository;

    private final PayloadCipher cipher;

    private final ClockPort clock;

    private final TenantContext tenantContext;

    private final TenantTransactionRunner tenantRunner;

    private final IdGenerator idGenerator;

    private final int maxAttempts;

    /**
     * Erzeugt die Fachlogik.
     *
     * @param repository         der JDBC-Adapter des Ereignismoduls
     * @param idempotencyStore   der Abgleich der Idempotenzschluessel
     * @param endpointRepository der JDBC-Adapter des Endpunktmoduls (Abonnenten)
     * @param cipher             die Chiffre der Nutzlast
     * @param clock              die Uhr des Dienstes
     * @param tenantContext      der Mandant aus der Identitaet
     * @param tenantRunner       der Mandantentransaktions-Runner
     * @param idGenerator        die Erzeugung der Kennungen (UUID-v7)
     * @param properties         die Betriebskonfiguration (Versuchsgrenze)
     */
    public EventService(EventRepository repository,
                        IdempotencyStore idempotencyStore,
                        EndpointRepository endpointRepository,
                        PayloadCipher cipher,
                        ClockPort clock,
                        TenantContext tenantContext,
                        TenantTransactionRunner tenantRunner,
                        IdGenerator idGenerator,
                        EchoBoxProperties properties) {
        this.repository = repository;
        this.idempotencyStore = idempotencyStore;
        this.endpointRepository = endpointRepository;
        this.cipher = cipher;
        this.clock = clock;
        this.tenantContext = tenantContext;
        this.tenantRunner = tenantRunner;
        this.idGenerator = idGenerator;
        this.maxAttempts = properties.getDelivery().getMaxAttempts();
    }

    /**
     * Liefert ein Ereignis ein.
     *
     * @param request der Request-Body
     * @return das Ereignis und die Kennung, ob es eine Wiederholung war
     */
    public IntakeResult submit(EventIntakeRequest request) {
        requireDeveloper();
        request.validate();
        byte[] payloadBytes = payloadBytes(request);
        if (PayloadSize.exceedsPayloadLimit(payloadBytes.length)) {
            throw new PayloadTooLargeException();
        }
        byte[] fingerprint = cipher.fingerprint(payloadBytes);
        String organisationId = tenantContext.organisationId();
        return tenantRunner.execute(organisationId, () -> {
            IdempotencyDecision decision = idempotencyStore.decide(organisationId,
                    request.idempotencyKey(), fingerprint);
            if (decision == IdempotencyDecision.CONFLICT) {
                LOG.info("Der Idempotenzschluessel wurde im Mandant {} mit abweichender Nutzlast verwendet.",
                        organisationId);
                throw new IdempotencyConflictException();
            }
            if (decision == IdempotencyDecision.REPLAYED) {
                Event existing = repository.findByIdempotencyKey(organisationId, request.idempotencyKey())
                        .orElseThrow(() -> new IdempotencyConflictException());
                return replay(existing, organisationId);
            }
            return create(organisationId, request, payloadBytes, fingerprint);
        });
    }

    /**
     * Liest die Metadaten eines Ereignisses — ein fremdes Ereignis ergibt
     * <strong>404</strong> (D-02).
     *
     * @param eventId die Ereignis-ID
     * @return das Ereignis
     */
    public Event get(UUID eventId) {
        String organisationId = tenantContext.organisationId();
        Event event = repository.findById(organisationId, eventId).orElseThrow(() -> {
            LOG.info("Ereignis {} existiert im Mandanten nicht.", eventId);
            return new NotFoundException();
        });
        return withSubscriberCount(event, organisationId);
    }

    private IntakeResult create(String organisationId,
                                 EventIntakeRequest request,
                                 byte[] payloadBytes,
                                 byte[] fingerprint) {
        Instant now = clock.now();
        List<EndpointRepository.Subscriber> subscribers =
                endpointRepository.subscribers(organisationId, request.eventType());
        boolean withoutSubscriber = subscribers.isEmpty();
        String status = withoutSubscriber ? Event.STATUS_NO_SUBSCRIBER : Event.STATUS_ACCEPTED;
        PayloadEncoding encoding = request.encoding();

        byte[] ciphertext;
        byte[] nonce;
        if (encoding == PayloadEncoding.PRE_ENCRYPTED) {
            // Die Bytes sind bereits verschluesselt: sie werden unveraendert
            // gespeichert und unveraendert zugestellt (FR-013).
            ciphertext = payloadBytes.clone();
            nonce = new byte[NONCE_LENGTH];
        } else {
            PayloadCipher.Ciphertext encrypted = cipher.encrypt(payloadBytes);
            ciphertext = encrypted.bytes();
            nonce = encrypted.nonce();
        }

        UUID eventId = idGenerator.newId();
        boolean inserted = repository.insertIfAbsent(organisationId, eventId, request.eventType(),
                request.idempotencyKey(), ciphertext, nonce, fingerprint,
                encoding == PayloadEncoding.PRE_ENCRYPTED, request.mediaType(), payloadBytes.length, status, now);
        if (!inserted) {
            // Ein gleichzeitiger Vorgang hat denselben Schluessel belegt.
            Event existing = repository.findByIdempotencyKey(organisationId, request.idempotencyKey())
                    .orElseThrow(() -> new IdempotencyConflictException());
            IdempotencyDecision decision = IdempotencyDecision.of(Optional.of(existing.toStoredEvent()),
                    fingerprint);
            if (decision == IdempotencyDecision.CONFLICT) {
                throw new IdempotencyConflictException();
            }
            return replay(existing, organisationId);
        }

        if (withoutSubscriber) {
            // Ohne Abnehmer entsteht genau ein sichtbarer Lauf (D-04).
            repository.insertDeliveryRun(organisationId, idGenerator.newId(), eventId, null,
                    request.eventType(), DeliveryStatus.NO_SUBSCRIBER.wireValue(), maxAttempts, null, now);
        } else {
            for (EndpointRepository.Subscriber subscriber : subscribers) {
                if (subscriber.status() == EndpointStatus.ACTIVE) {
                    repository.insertDeliveryRun(organisationId, idGenerator.newId(), eventId,
                            subscriber.endpointId(), request.eventType(), DeliveryStatus.PENDING.wireValue(),
                            maxAttempts, now, now);
                }
            }
        }
        LOG.info("Ereignis {} im Mandant {} angenommen ({} Abonnenten).", eventId, organisationId,
                subscribers.size());
        Event event = new Event(eventId, organisationId, request.eventType(), request.idempotencyKey(), fingerprint,
                encoding, request.mediaType(), payloadBytes.length, status, subscribers.size(), now, now, now);
        return new IntakeResult(event, false);
    }

    private IntakeResult replay(Event existing, String organisationId) {
        return new IntakeResult(withSubscriberCount(existing, organisationId), true);
    }

    private Event withSubscriberCount(Event event, String organisationId) {
        int subscribers = endpointRepository.subscribers(organisationId, event.eventType()).size();
        return new Event(event.eventId(), event.organisationId(), event.eventType(), event.idempotencyKey(),
                event.fingerprint(), event.payloadEncoding(), event.payloadMediaType(), event.payloadSizeBytes(),
                event.status(), subscribers, event.acceptedAt(), event.createdAt(), event.updatedAt());
    }

    /**
     * Die Nutzlastbytes des Ereignisses — bei {@code payload} die
     * <strong>kanonisch materialisierten</strong> Bytes, bei
     * {@code payload_base64} die <strong>exakt gelieferten</strong> Bytes.
     *
     * <p>Die kanonische Materialisierung (RFC-8785-artig: UTF-8, sortierte
     * Objektschluessel, keine bedeutungslosen Leerzeichen) liegt an
     * <strong>einer</strong> Stelle: {@link CanonicalJson}. Damit erzeugt
     * derselbe fachliche Inhalt denselben Fingerprint — unabhaengig von der
     * Schreibweise des Aufrufers (ADR-007).
     */
    private static byte[] payloadBytes(EventIntakeRequest request) {
        byte[] base64 = request.base64Bytes();
        if (base64 != null) {
            return base64;
        }
        return CanonicalJson.canonicalize(request.payload());
    }

    /**
     * Ereignisse einliefern darf <strong>nur</strong> der Entwickler — der
     * Empfaenger-Admin und der Plattform-Betrieb erhalten 403 (ADR-015 §1).
     */
    private void requireDeveloper() {
        if (!tenantContext.hasRole(ROLE_DEVELOPER)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Das Ergebnis einer Einlieferung — das Ereignis und die Kennung, ob es
     * eine idempotente Wiederholung war.
     *
     * @param event           das Ereignis
     * @param idempotentReplay ob es eine Wiederholung war
     */
    public record IntakeResult(Event event, boolean idempotentReplay) {
    }
}
