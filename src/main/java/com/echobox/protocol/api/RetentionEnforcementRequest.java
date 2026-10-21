package com.echobox.protocol.api;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.ValidationException;

/**
 * Der Request-Body der Aufbewahrungsdurchsetzung — die Projektion des Vertrags
 * {@code components.schemas.RetentionEnforcementRequest}.
 *
 * <p>Der Vertrag (eingefroren) kennt genau ein Feld: {@code dry_run}. Weitere
 * Felder sind nicht erlaubt ({@code additionalProperties: false}) — ein
 * unbekanntes Feld ist ein Verstoss gegen das Schema des Request-Bodys (HTTP
 * 422 {@code validation_failed}).
 *
 * <p>Die unbekannten Felder werden beim Binden <strong>gesammelt</strong> und
 * bei {@link #validate()} gemeldet — so entsteht der Verstoss
 * <strong>ausserhalb</strong> der JSON-Bindung und wird nicht von ihr
 * verschluckt.
 *
 * <p><strong>Der Body ist optional</strong>: ohne Body gilt {@code dry_run =
 * true} — ein versehentlicher Aufruf verliert keine Daten.
 *
 * @param dryRun       ob nur gezaehlt wird ({@code true}) oder tatsaechlich
 *                     geloescht wird ({@code false}); ohne Angabe {@code true}
 * @param unknownFields die unbekannten Felder des Request-Bodys (intern)
 */
public record RetentionEnforcementRequest(
        @JsonProperty("dry_run") Boolean dryRun,
        @JsonIgnore List<String> unknownFields) {

    /**
     * Erzeugt den Request-Body. Die Sammlung der unbekannten Felder ist
     * veraenderlich — die JSON-Bindung traegt sie beim Binden ein.
     *
     * @param dryRun        ob nur gezaehlt wird
     * @param unknownFields die unbekannten Felder des Request-Bodys
     */
    public RetentionEnforcementRequest {
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
    }

    /**
     * Der wirksame Wert von {@code dry_run} — ohne Angabe gilt {@code true}.
     *
     * @return {@code true}, wenn nur gezaehlt wird
     */
    public boolean dryRunEnabled() {
        return dryRun == null || dryRun.booleanValue();
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
