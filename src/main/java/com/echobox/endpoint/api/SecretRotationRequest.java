package com.echobox.endpoint.api;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.ValidationException;

/**
 * Der Request-Body der Secret-Rotation.
 *
 * <p>Das neue Secret wird verschluesselt abgelegt; das alte Secret wird
 * <strong>nie</strong> ausgegeben (FR-003, AC-003-2). Der {@code toString()}
 * dieser Klasse gibt das Secret deshalb <strong>nicht</strong> wieder.
 *
 * @param secret        das neue Secret des Endpunkts
 * @param unknownFields die unbekannten Felder des Request-Bodys (intern)
 */
public record SecretRotationRequest(
        @JsonProperty("secret") String secret,
        @JsonIgnore List<String> unknownFields) {

    /**
     * Erzeugt den Request-Body. Die Sammlung der unbekannten Felder ist
     * veraenderlich — die JSON-Bindung traegt sie beim Binden ein.
     *
     * @param secret        das neue Secret des Endpunkts
     * @param unknownFields die unbekannten Felder des Request-Bodys
     */
    public SecretRotationRequest {
        unknownFields = unknownFields == null ? new ArrayList<>() : unknownFields;
    }

    /**
     * Prueft den Request-Body gegen das Schema des Vertrags.
     *
     * @throws ValidationException wenn das Secret das Schema verletzt
     */
    public void validate() {
        for (String field : unknownFields) {
            EndpointCreateRequest.rejectUnknownField(field);
        }
        EndpointCreateRequest.validateSecret(secret);
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

    @Override
    public String toString() {
        return "SecretRotationRequest[secret=<verborgen>]";
    }
}
