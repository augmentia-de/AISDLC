package com.echobox.delivery;

/**
 * Das Ergebnis eines einzelnen HTTP-Versuchs (D-08, ADR-009).
 *
 * <p>Die Klassifikation des Vertrags:
 *
 * <table>
 *   <caption>Ergebnis eines Versuchs</caption>
 *   <tr><th>Antwort</th><th>Ergebnis</th><th>Folge</th></tr>
 *   <tr><td>2xx</td><td>{@code success}</td><td>{@code sent}</td></tr>
 *   <tr><td>410 mit Ablehnungssignal</td><td>{@code permanent_rejection}</td><td>{@code rejected}</td></tr>
 *   <tr><td>uebrige 4xx, 3xx</td><td>{@code client_error}</td><td>{@code rejected}</td></tr>
 *   <tr><td>5xx, 408, 429</td><td>{@code retryable_failure}</td><td>{@code failed}</td></tr>
 *   <tr><td>Netzwerkfehler</td><td>{@code network_error}</td><td>{@code failed}</td></tr>
 *   <tr><td>Timeout</td><td>{@code timeout}</td><td>{@code failed}</td></tr>
 *   <tr><td>Endpunkt pausiert</td><td>{@code cancelled}</td><td>{@code cancelled}</td></tr>
 * </table>
 *
 * <p><strong>Ein 3xx wird nicht gefolgt</strong> (Redirects werden nicht
 * verfolgt): die Ziel-URL ist Teil der Signier-Eingabe, ein Folgeort wuerde die
 * signierte URL abweichen (ADR-009).
 */
public enum DeliveryOutcome {

    /** Die Zustellung war erfolgreich (2xx). */
    SUCCESS,

    /** Der Versuch wird wiederholt (5xx, 408, 429). */
    RETRYABLE_FAILURE,

    /** Der Empfaenger hat dauerhaft abgelehnt (410 mit Ablehnungssignal). */
    PERMANENT_REJECTION,

    /** Der Empfaenger hat abgelehnt (uebrige 4xx oder 3xx). */
    CLIENT_ERROR,

    /** DNS-, TLS- oder Verbindungsfehler ohne Timeout. */
    NETWORK_ERROR,

    /** Verbindungs- oder Lese-Timeout. */
    TIMEOUT,

    /** Der Endpunkt wurde waehrend des Versuchs pausiert. */
    CANCELLED;

    /**
     * Klassifiziert die Antwort des Empfaengers.
     *
     * @param status           der HTTP-Statuscode ({@code null} bei einem Transportfehler)
     * @param rejectionSignal  ob das Ablehnungssignal {@code X-EchoBox-Rejection: permanent} vorlag
     * @return das Ergebnis
     */
    public static DeliveryOutcome classify(Integer status, boolean rejectionSignal) {
        if (status == null) {
            return NETWORK_ERROR;
        }
        if (status >= 200 && status < 300) {
            return SUCCESS;
        }
        if (status == 410 && rejectionSignal) {
            return PERMANENT_REJECTION;
        }
        if (status == 408 || status == 429) {
            return RETRYABLE_FAILURE;
        }
        if (status >= 300 && status < 500) {
            return CLIENT_ERROR;
        }
        return RETRYABLE_FAILURE;
    }

    /**
     * Liest ein Ergebnis aus seinem Datenbankwert.
     *
     * @param value der Wert
     * @return das Ergebnis
     * @throws IllegalArgumentException wenn der Wert unbekannt ist
     */
    public static DeliveryOutcome fromWire(String value) {
        for (DeliveryOutcome outcome : values()) {
            if (outcome.wireValue().equals(value)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("Unbekanntes Versuchsergebnis: " + value);
    }

    /**
     * Liefert, ob das Ergebnis den Lauf endgueltig beendet.
     *
     * @return {@code true}, wenn kein weiterer Versuch erfolgt
     */
    public boolean isFinal() {
        return this == SUCCESS || this == PERMANENT_REJECTION || this == CLIENT_ERROR || this == CANCELLED;
    }

    /**
     * Liefert, ob der Versuch wiederholt wird.
     *
     * @return {@code true}, wenn ein weiterer Versuch geplant wird
     */
    public boolean isRetryable() {
        return !isFinal();
    }

    /**
     * Liefert den Wert, wie er in der Datenbank steht.
     *
     * @return der Wert in Kleinbuchstaben
     */
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
