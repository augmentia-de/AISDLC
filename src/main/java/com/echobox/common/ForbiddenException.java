package com.echobox.common;

/**
 * Angemeldet, aber die Rolle darf die Operation nicht ausfuehren (HTTP 403).
 *
 * <p>HTTP 403 - Code {@code CODE}.
 */
public class ForbiddenException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public ForbiddenException() {
        super(ProblemFactory.FORBIDDEN, 403, "Forbidden", "Diese Rolle darf die Operation fuer diesen Mandanten nicht ausfuehren.");
    }
}
