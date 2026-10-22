package com.echobox.common;

/**
 * Kein oder ungueltiges Token (HTTP 401).
 *
 * <p>HTTP 401 - Code {@code CODE}.
 */
public class UnauthenticatedException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public UnauthenticatedException() {
        this("Das Bearer-Token fehlt oder ist ungueltig.");
    }

    /**
     * Erzeugt den Fehler mit einer eigenen Handlungsanweisung.
     *
     * @param detail die Handlungsanweisung
     */
    public UnauthenticatedException(String detail) {
        super(ProblemFactory.UNAUTHORIZED, 401, "Unauthorized", detail);
    }
}
