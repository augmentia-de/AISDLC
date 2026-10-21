package com.echobox.common;

/**
 * Ein feldbezogener Verstoß gegen das Schema eines Request-Bodys.
 *
 * <p>Der Vertrag nennt drei Felder: das betroffene {@code field}, den
 * maschinenlesbaren {@code code} und eine verstaendliche {@code message}.
 *
 * @param field   das betroffene Feld (Feldname, zum Beispiel {@code target_url})
 * @param code    der maschinenlesbare Verstoßcode
 * @param message die verstaendliche Meldung fuer den Aufrufer
 */
public record ValidationViolation(String field, String code, String message) {
}
