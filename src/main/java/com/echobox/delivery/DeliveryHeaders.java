package com.echobox.delivery;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.echobox.crypto.SignatureService;

/**
 * Die Namen und Werte der ausgehenden Kopfzeilen — <strong>eingefroren</strong>
 * (FR-020, D-07).
 *
 * <p>Der Vertrag ({@code docs/api/openapi.yaml} -&gt;
 * {@code x-delivery-protocol.request.headers}) legt Namen, Werte und
 * Pflichtigkeit fest. Eine Aenderung an einem dieser Namen ist ein
 * <strong>breaking change</strong> und braucht eine neue Hauptversion.
 *
 * <p>Die Kopfzeilen tragen die fachlichen Kennungen der Zustellung: die
 * Ereignis-ID (Deduplizierungsschluessel des Empfaengers), die Zustelllauf-ID
 * (ueber alle Versuche eines Laufs gleich) und die Versuchs-ID (je Versuch
 * eindeutig).
 */
public final class DeliveryHeaders {

    /** Die Ereignis-ID — der fachliche Deduplizierungsschluessel des Empfaengers. */
    public static final String EVENT_ID = "X-EchoBox-Event-Id";

    /** Die Zustelllauf-ID — ueber alle Versuche eines Laufs identisch. */
    public static final String DELIVERY_RUN_ID = "X-EchoBox-Delivery-Run-Id";

    /** Die Versuchs-ID — fuer jeden HTTP-Versuch eindeutig. */
    public static final String ATTEMPT_ID = "X-EchoBox-Attempt-Id";

    /** Die Versuchsnummer innerhalb des Zustelllaufs (1-basiert). */
    public static final String ATTEMPT_NUMBER = "X-EchoBox-Attempt-Number";

    /** Der Ereignistyp des zugestellten Ereignisses. */
    public static final String EVENT_TYPE = "X-EchoBox-Event-Type";

    /** Der Zeitpunkt der Zustellung in UTC — Teil der Signier-Eingabe. */
    public static final String TIMESTAMP = "X-EchoBox-Timestamp";

    /** Die Signatur der Zustellung. */
    public static final String SIGNATURE = "X-EchoBox-Signature";

    /** Das Ablehnungssignal des Empfaengers (Antwortkopfzeile). */
    public static final String REJECTION = "X-EchoBox-Rejection";

    /** Der User-Agent — der Vertrag nennt ihn ausdruecklich. */
    public static final String USER_AGENT = "User-Agent";

    /** Der Versionspraefix des Signaturwerts. */
    public static final String SIGNATURE_VALUE_PREFIX = "v1=";

    /** Der feste User-Agent-Wert. */
    public static final String USER_AGENT_VALUE = "EchoBox-Delivery/1.0";

    /** Der Wert des Ablehnungssignals „dauerhaft“. */
    public static final String REJECTION_VALUE_PERMANENT = "permanent";

    private DeliveryHeaders() {
        // statische Konstanten — keine Instanz
    }

    /**
     * Baut die Kopfzeilen eines Versuchs.
     *
     * <p>Der Zeitstempel ist der ISO-8601-UTC-Wert, der <strong>exakt</strong>
     * in die Signier-Eingabe eingeht ({@link SignatureService#timestampHeaderValue}).
     *
     * @param eventId       die Ereignis-ID
     * @param deliveryRunId die Zustelllauf-ID
     * @param attemptId     die Versuchs-ID
     * @param attemptNumber die Versuchsnummer (1-basiert)
     * @param eventType     der Ereignistyp
     * @param timestamp     der Zeitpunkt der Zustellung
     * @param signature     der Signaturwert
     * @return die Kopfzeilen
     */
    public static Map<String, String> build(String eventId,
                                            String deliveryRunId,
                                            String attemptId,
                                            int attemptNumber,
                                            String eventType,
                                            Instant timestamp,
                                            String signature) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EVENT_ID, eventId);
        headers.put(DELIVERY_RUN_ID, deliveryRunId);
        headers.put(ATTEMPT_ID, attemptId);
        headers.put(ATTEMPT_NUMBER, Integer.toString(attemptNumber));
        headers.put(EVENT_TYPE, eventType);
        headers.put(TIMESTAMP, SignatureService.timestampHeaderValue(timestamp));
        headers.put(SIGNATURE, signature);
        headers.put(USER_AGENT, USER_AGENT_VALUE);
        return headers;
    }
}
