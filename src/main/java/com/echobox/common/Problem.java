package com.echobox.common;

import java.util.List;

/**
 * Das einheitliche Fehlerschema des Dienstes (RFC-7807-artig).
 *
 * <p>Der Vertrag kennt **ein** Fehlerschema fuer alle Fehlerantworten. Der
 * maschinenlesbare Schluessel ist {@code code}; {@code type} ist die stabile,
 * dokumentierte Fehlerklasse.
 *
 * @param type        die stabile URI der Fehlerklasse
 * @param title       die kurze, nichttechnische Ueberschrift
 * @param status      der HTTP-Statuscode (identisch mit dem Response-Status)
 * @param code        der maschinenlesbare Fehlercode
 * @param detail      was zu tun ist (nullable)
 * @param instance    der Pfad der Anfrage (nullable)
 * @param violations  die feldbezogenen Verstoesse (nullable)
 */
public record Problem(String type,
                       String title,
                       int status,
                       String code,
                       String detail,
                       String instance,
                       List<ValidationViolation> violations) {

    /**
     * Liefert dasselbe Problem mit dem Pfad der Anfrage.
     *
     * @param requestPath der Pfad der Anfrage
     * @return das Problem mit gesetztem Pfad
     */
    public Problem withInstance(String requestPath) {
        return new Problem(type, title, status, code, detail, requestPath, violations);
    }
}
