package com.echobox.common;

/**
 * Der Dienst ist voruebergehend nicht bereit (HTTP 503).
 *
 * <p>HTTP 503 - Code {@code CODE}.
 */
public class ServiceUnavailableException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public ServiceUnavailableException() {
        super(ProblemFactory.SERVICE_UNAVAILABLE, 503, "Service unavailable", "Der Dienst ist derzeit nicht bereit.");
    }
}
