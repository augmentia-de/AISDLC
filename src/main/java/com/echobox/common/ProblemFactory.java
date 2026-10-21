package com.echobox.common;

import java.util.List;

/**
 * Die **einzige** Stelle, an der Fehlerantworten entstehen (ADR-015 §4).
 *
 * <p>Es gibt keine Ad-hoc-Fehler-Bodies: jede Fehlerantwort wird aus einem
 * Fehlercode, einem Statuscode, einer Ueberschrift und dem Pfad der Anfrage
 * gebildet. Die Zuordnung Status → {@code code} steht im Vertrag
 * ({@code info.description}, Abschnitt „Fehlersemantik“).
 */
public final class ProblemFactory {

    /** Basis-URI der Fehlerklassen — stabil und dokumentiert. */
    public static final String ERROR_BASE_URI = "https://echobox.example/errors/";

    /** 400 — der Body ist kein gueltiges JSON. */
    public static final String MALFORMED_REQUEST = "malformed_request";

    /** 401 — kein oder ungueltiges Token. */
    public static final String UNAUTHORIZED = "unauthorized";

    /** 403 — die Rolle darf die Operation im eigenen Mandanten nicht ausfuehren. */
    public static final String FORBIDDEN = "forbidden";

    /** 404 — die Ressource existiert nicht oder gehoert einem fremden Mandanten. */
    public static final String NOT_FOUND = "not_found";

    /** 409 — gleicher Idempotenzschluessel mit abweichender Nutzlast. */
    public static final String IDEMPOTENCY_CONFLICT = "idempotency_conflict";

    /** 409 — der Zustelllauf ist nicht replaybar. */
    public static final String DELIVERY_STATE_CONFLICT = "delivery_state_conflict";

    /** 413 — die Nutzlast ueberschreitet 1.048.576 Byte. */
    public static final String PAYLOAD_TOO_LARGE = "payload_too_large";

    /** 422 — semantisch ungueltige Anfrage. */
    public static final String VALIDATION_FAILED = "validation_failed";

    /** 422 — ein Mandantenparameter wurde mitgeschickt. */
    public static final String TENANT_PARAMETER_REJECTED = "tenant_parameter_rejected";

    /** 429 — das Anfragelimit der API ist ueberschritten. */
    public static final String RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";

    /** 500 — unerwarteter Fehler. */
    public static final String INTERNAL_ERROR = "internal_error";

    /** 503 — der Dienst ist nicht bereit. */
    public static final String SERVICE_UNAVAILABLE = "service_unavailable";

    private ProblemFactory() {
        // statische Helferklasse
    }

    /**
     * Erzeugt eine Fehlerantwort.
     *
     * @param code       der maschinenlesbare Fehlercode
     * @param status     der HTTP-Statuscode
     * @param title      die kurze Ueberschrift
     * @param detail     die Handlungsanweisung (darf {@code null} sein)
     * @param instance   der Pfad der Anfrage
     * @param violations die feldbezogenen Verstoesse (darf {@code null} sein)
     * @return das Fehlerschema
     */
    public static Problem create(String code,
                                 int status,
                                 String title,
                                 String detail,
                                 String instance,
                                 List<ValidationViolation> violations) {
        return new Problem(ERROR_BASE_URI + slug(code), title, status, code, detail, instance, violations);
    }

    /**
     * Erzeugt eine Fehlerantwort ohne feldbezogene Verstoesse.
     *
     * @param code     der maschinenlesbare Fehlercode
     * @param status   the HTTP-Statuscode
     * @param title    die kurze Ueberschrift
     * @param detail   die Handlungsanweisung
     * @param instance der Pfad der Anfrage
     * @return das Fehlerschema
     */
    public static Problem create(String code, int status, String title, String detail, String instance) {
        return create(code, status, title, detail, instance, null);
    }

    private static String slug(String code) {
        return code.replace('_', '-');
    }
}
