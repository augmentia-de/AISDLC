package com.echobox.common;

import java.util.List;

/**
 * Basisklasse der fachlichen Fehler.
 *
 * <p>Die Fachlogik kennt **keine** HTTP-Typen: sie wirft diese Ausnahmen, und
 * **ausschliesslich** der {@link GlobalExceptionHandler} bildet sie auf den
 * Statuscode und das {@code Problem}-Schema des Vertrags ab.
 */
public abstract class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    private final int status;

    private final String title;

    private final String detail;

    /**
     * Erzeugt einen fachlichen Fehler.
     *
     * @param code   der maschinenlesbare Fehlercode
     * @param status der HTTP-Statuscode
     * @param title  die kurze Ueberschrift
     * @param detail die Handlungsanweisung
     */
    protected ApiException(String code, int status, String title, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    /**
     * Liefert den maschinenlesbaren Fehlercode.
     *
     * @return der Fehlercode
     */
    public String code() {
        return code;
    }

    /**
     * Liefert den HTTP-Statuscode.
     *
     * @return der Statuscode
     */
    public int status() {
        return status;
    }

    /**
     * Liefert die kurze Ueberschrift.
     *
     * @return die Ueberschrift
     */
    public String title() {
        return title;
    }

    /**
     * Liefert die Handlungsanweisung.
     *
     * @return die Handlungsanweisung
     */
    public String detail() {
        return detail;
    }

    /**
     * Liefert die feldbezogenen Verstoesse — standardmaessig keine.
     *
     * @return die Verstoesse
     */
    public List<ValidationViolation> violations() {
        return List.of();
    }
}
