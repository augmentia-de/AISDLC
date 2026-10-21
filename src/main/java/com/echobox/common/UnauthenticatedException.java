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
        super(ProblemFactory.UNAUTHORIZED, 401, "Unauthorized", "Das Bearer-Token fehlt oder ist ungueltig.");
    }
}
