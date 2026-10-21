package com.echobox.delivery.api;

import java.util.ArrayList;
import java.util.List;

import com.echobox.common.ValidationException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Der Request-Body des Replays — optional.
 *
 * <p>Der Vertrag erlaubt genau ein Feld: {@code reason} — eine
 * Freitextbegruendung fuer das Audit-Protokoll. Weitere Felder sind nicht
 * erlaubt ({@code additionalProperties: false}) — ein unbekanntes Feld ist ein
 * Verstoss gegen das Schema des Request-Bodys (HTTP 422
 * {@code validation_failed}).
 *
 * @param reason        die Begruendung des Replays (optional)
 * @param unknownFields die unbekannten Felder des Request-Bodys (intern)
 */
public record ReplayRequest(
        @JsonProperty("reason") String reason,
        @JsonIgnore List<String> unknownFields) {

    /** Die groesste Laenge der Begruendung. */
    public static final int MAX_REASON_LENGTH = 500;

    /**
     * Erzeugt den Request-Body. Die Sammlung der unbekannten Felder ist
     * veraenderlich — die JSON-Bindung traegt sie beim Binden ein.
     *
     * @param reason        die Begruendung des Replays
     * @param unknownFields die unbekannten Felder des Request-Bodys
     */
    public ReplayRequest {
        unknownFields = unknownFields == null ? new ArrayList<>() : unknownFields;
    }

    /**
     * Prueft den Request-Body gegen das Schema des Vertrags.
     *
     * @throws ValidationException wenn ein Feld das Schema verletzt
     */
    public void validate() {
        for (String field : unknownFields) {
            throw new ValidationException(field, "unknown_field",
                    "Unbekanntes Feld im Request-Body: " + field);
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new ValidationException("reason", "reason_too_long",
                    "Die Begruendung darf hoechstens " + MAX_REASON_LENGTH + " Zeichen lang sein.");
        }
    }

    /**
     * Sammelt ein unbekanntes Feld des Request-Bodys.
     *
     * @param name  der Name des unbekannten Feldes
     * @param value der Wert (wird nicht ausgewertet)
     */
    @JsonAnySetter
    public void collectUnknownField(String name, Object value) {
        unknownFields.add(name);
    }
}
