package com.echobox.common;

import java.util.List;

/**
 * Eine semantisch ungueltige Anfrage (HTTP 422 {@code validation_failed}).
 *
 * <p>Die Ausnahme traegt **genau eine** Verletzung: sie entsteht an der Stelle,
 * an der das Feld geprueft wurde (zum Beispiel bei der Ziel-URL), und wird
 * ausschlilich im {@link GlobalExceptionHandler} auf das Fehlerschema des
 * Vertrags abgebildet.
 *
 * <p>Ein **syntaktisch** fehlerhafter Body (kein gueltiges JSON) ist kein
 * Verstoß dieses Typs, sondern HTTP 400 {@code malformed_request}.
 */
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    private final String code;

    /**
     * Erzeugt eine Verletzung.
     *
     * @param field   das betroffene Feld
     * @param code    der maschinenlesbare Verstoßcode
     * @param message die verstaendliche Meldung
     */
    public ValidationException(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    /**
     * Liefert das betroffene Feld.
     *
     * @return der Feldname
     */
    public String field() {
        return field;
    }

    /**
     * Liefert den maschinenlesbaren Verstoßcode.
     *
     * @return der Verstoßcode
     */
    public String code() {
        return code;
    }

    /**
     * Liefert die Verletzung als Liste — genau ein Eintrag.
     *
     * @return die Liste der Verletzungen
     */
    public List<ValidationViolation> violations() {
        return List.of(new ValidationViolation(field, code, getMessage()));
    }
}
