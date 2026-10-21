package com.echobox.delivery;

import java.util.Map;

/**
 * Die Antwort des Empfaengers auf einen Zustellversuch.
 *
 * <p>Der Antwort-Body wird <strong>nicht</strong> ausgewertet — nur der
 * Statuscode und das Ablehnungssignal zaehlen. Ein Transportfehler traegt
 * keinen Statuscode, sondern eine {@link TransportFailure}.
 *
 * @param statusCode der HTTP-Statuscode (bei einem Transportfehler {@code null})
 * @param body       der Antwort-Body (unbewertet)
 * @param headers    die Antwortkopfzeilen
 * @param failure    der Transportfehler — {@link TransportFailure#NONE} bei einer Antwort
 */
public record DeliveryResponse(Integer statusCode,
                                String body,
                                Map<String, String> headers,
                                TransportFailure failure) {

    /**
     * Erzeugt eine Antwort mit kopierten Feldern.
     *
     * @param statusCode der HTTP-Statuscode (bei einem Transportfehler {@code null})
     * @param body       der Antwort-Body (unbewertet)
     * @param headers    die Antwortkopfzeilen
     * @param failure    der Transportfehler
     */
    public DeliveryResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        failure = failure == null ? TransportFailure.NONE : failure;
    }

    /**
     * Erzeugt eine Antwort ohne Transportfehler.
     *
     * @param statusCode der HTTP-Statuscode
     * @param body       der Antwort-Body (unbewertet)
     * @param headers    die Antwortkopfzeilen
     * @return die Antwort
     */
    public DeliveryResponse(Integer statusCode, String body, Map<String, String> headers) {
        this(statusCode, body, headers, TransportFailure.NONE);
    }

    /**
     * Der Transportfehler eines Versuchs.
     *
     * <p>Die Trennlinie (ADR-009): {@link #TIMEOUT} = der Verbindungs- oder
     * Lese-Timeout hat ausgeloest; {@link #NETWORK_ERROR} = DNS-, TLS- oder
     * Verbindungsfehler <strong>ohne</strong> Timeout. Beide fuehren zum selben
     * Verhalten (Wiederholung nach Backoff), werden im Protokoll aber
     * unterschiedlich ausgewiesen.
     */
    public enum TransportFailure {

        /** Kein Transportfehler — der Empfaenger hat geantwortet. */
        NONE,

        /** Der Verbindungs- oder Lese-Timeout hat ausgeloest. */
        TIMEOUT,

        /** DNS-, TLS- oder Verbindungsfehler ohne Timeout. */
        NETWORK_ERROR
    }
}
