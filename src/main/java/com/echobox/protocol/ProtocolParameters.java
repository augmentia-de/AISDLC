package com.echobox.protocol;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

import com.echobox.common.ValidationException;

/**
 * Die Auswertung der Query-Parameter der Protokolloperationen.
 *
 * <p>Die Adapter binden die Query-Parameter als <strong>Zeichenfolge</strong>:
 * ein unbrauchbarer Wert ist ein Verstoss gegen das Schema der Anfrage und
 * wird mit HTTP 422 {@code validation_failed} beantwortet — nicht mit einem
 * 500er. Die Auswertung liegt an <strong>einer</strong> Stelle, damit die
 * beiden Adapter des Moduls dasselbe tun.
 *
 * <p>Die Zeitpunkte sind ISO-8601-Zeichenfolgen (RFC 3339, UTC) — der Vertrag
 * fuehrt sie als {@code date-time}.
 *
 * <p>Diese Klasse ist <strong>intern</strong>: sie gehoert zum Modul und wird
 * nicht nach aussen angeboten.
 */
final class ProtocolParameters {

    /** Die groesste Zahl der Eintraege je Seite. */
    static final int MAX_LIMIT = 200;

    /** Die Vorgabezahl der Eintraege je Seite. */
    static final int DEFAULT_LIMIT = 50;

    private ProtocolParameters() {
        // statische Helferklasse
    }

    /**
     * Wertet das Seitenlimit aus.
     *
     * @param limit der mitgeschickte Wert (nullable = Vorgabe)
     * @return das wirksame Limit
     * @throws ValidationException wenn das Limit ausserhalb 1..{@value #MAX_LIMIT} liegt
     */
    static int limit(Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_LIMIT) {
            throw new ValidationException("limit", "limit_out_of_range",
                    "Die Zahl der Eintraege je Seite muss zwischen 1 und " + MAX_LIMIT + " liegen.");
        }
        return effective;
    }

    /**
     * Wertet einen Zeitpunkt aus — {@code null} bedeutet „keine Angabe“.
     *
     * @param value der mitgeschickte Wert (nullable)
     * @param field der Name des Parameters (fuer die Verletzung)
     * @return der Zeitpunkt oder {@code null}
     * @throws ValidationException wenn der Wert keine ISO-8601-Zeichenfolge ist
     */
    static Instant instant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ValidationException(field, "invalid_timestamp",
                    "Der Zeitpunkt muss eine ISO-8601-Zeichenfolge sein (zum Beispiel 2026-10-21T09:15:22Z).");
        }
    }

    /**
     * Wertet eine kommaseparierte Liste aus — {@code null} bedeutet „keine
     * Angabe“.
     *
     * @param value der mitgeschickte Wert (nullable)
     * @return die einzelnen Werte (leer, wenn nichts angegeben wurde)
     */
    static List<String> values(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
