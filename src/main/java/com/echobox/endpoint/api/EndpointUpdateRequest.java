package com.echobox.endpoint.api;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.ValidationException;
import com.echobox.endpoint.EndpointEventTypes;

/**
 * Der Request-Body der Endpunkt-Aktualisierung.
 *
 * <p>Es werden <strong>nur</strong> die mitgeschickten Felder geaendert. Das
 * Secret wird <strong>nicht</strong> ueber diesen Weg geaendert — dafuer gibt
 * es die Secret-Rotation.
 *
 * @param targetUrl     die neue Ziel-URL (optional)
 * @param eventTypes    die neuen Ereignistypen (optional)
 * @param unknownFields die unbekannten Felder des Request-Bodys (intern)
 */
public record EndpointUpdateRequest(
        @JsonProperty("target_url") String targetUrl,
        @JsonProperty("event_types") List<String> eventTypes,
        @JsonIgnore List<String> unknownFields) {

    /**
     * Erzeugt den Request-Body. Die Sammlung der unbekannten Felder ist
     * veraenderlich — die JSON-Bindung traegt sie beim Binden ein.
     *
     * @param targetUrl     die neue Ziel-URL (optional)
     * @param eventTypes    die neuen Ereignistypen (optional)
     * @param unknownFields die unbekannten Felder des Request-Bodys
     */
    public EndpointUpdateRequest {
        unknownFields = unknownFields == null ? new ArrayList<>() : unknownFields;
    }

    /**
     * Prueft den Request-Body gegen das Schema des Vertrags.
     *
     * @throws ValidationException wenn ein Feld das Schema verletzt
     */
    public void validate() {
        for (String field : unknownFields) {
            EndpointCreateRequest.rejectUnknownField(field);
        }
        boolean targetUrlPresent = targetUrl != null;
        boolean eventTypesPresent = eventTypes != null;
        if (!targetUrlPresent && !eventTypesPresent) {
            throw new ValidationException("body", "empty_update",
                    "Mindestens eines der Felder target_url oder event_types muss gesetzt sein.");
        }
        if (targetUrlPresent) {
            if (targetUrl.isBlank()) {
                throw new ValidationException("target_url", "target_url_required",
                        "Die Ziel-URL ist erforderlich.");
            }
            if (targetUrl.length() > EndpointCreateRequest.MAX_TARGET_URL_LENGTH) {
                throw new ValidationException("target_url", "target_url_too_long",
                        "Die Ziel-URL darf hoechstens " + EndpointCreateRequest.MAX_TARGET_URL_LENGTH
                                + " Zeichen lang sein.");
            }
        }
        if (eventTypesPresent) {
            EndpointEventTypes.of(eventTypes);
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
