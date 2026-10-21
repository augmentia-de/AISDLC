package com.echobox.common;

/**
 * Die Ressource existiert nicht oder gehoert einem fremden Mandanten (HTTP 404).
 *
 * <p>HTTP 404 - Code {@code CODE}.
 */
public class NotFoundException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public NotFoundException() {
        super(ProblemFactory.NOT_FOUND, 404, "Not found", "Die Ressource wurde nicht gefunden.");
    }
}
