package com.echobox.common;

/**
 * Die Nutzlast ueberschreitet 1.048.576 Byte (HTTP 413, NFR-001).
 *
 * <p>HTTP 413 - Code {@code CODE}.
 */
public class PayloadTooLargeException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public PayloadTooLargeException() {
        super(ProblemFactory.PAYLOAD_TOO_LARGE, 413, "Payload too large", "Die Nutzlast ueberschreitet die zulaessige Groesse.");
    }
}
