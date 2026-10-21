package com.echobox.endpoint.api;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.ValidationException;
import com.echobox.endpoint.EndpointEventTypes;

/**
 * Der Request-Body der Endpunkt-Registrierung.
 *
 * <p>Der Vertrag (eingefroren) fordert genau drei Felder: {@code target_url},
 * {@code secret} und {@code event_types}. Weitere Felder sind nicht erlaubt
 * ({@code additionalProperties: false}) — ein unbekanntes Feld ist ein
 * Verstoß gegen das Schema des Request-Bodys (HTTP 422 {@code validation_failed}).
 *
 * <p>Die unbekannten Felder werden beim Binden <strong>gesammelt</strong> und
 * bei {@link #validate()} gemeldet — so entsteht der Verstoß <strong>ausserhalb</strong>
 * der JSON-Bindung und wird nicht von ihr verschluckt.
 *
 * <p><strong>Das Secret</strong> wird verschluesselt abgelegt und
 * <strong>nie</strong> im Klartext ausgegeben (AC-001-4). Der {@code toString()}
 * dieser Klasse gibt es deshalb <strong>nicht</strong> wieder.
 *
 * @param targetUrl     die Ziel-URL des Empfaengers
 * @param secret        das gemeinsame Secret des Endpunkts
 * @param eventTypes    die abonnierten Ereignistypen
 * @param unknownFields die unbekannten Felder des Request-Bodys (intern)
 */
public record EndpointCreateRequest(
        @JsonProperty("target_url") String targetUrl,
        @JsonProperty("secret") String secret,
        @JsonProperty("event_types") List<String> eventTypes,
        @JsonIgnore List<String> unknownFields) {

    /** Die groesste Laenge der Ziel-URL. */
    public static final int MAX_TARGET_URL_LENGTH = 2000;

    /** Die kleinste Laenge des Secrets. */
    public static final int MIN_SECRET_LENGTH = 16;

    /** Die groesste Laenge des Secrets. */
    public static final int MAX_SECRET_LENGTH = 512;

    /**
     * Erzeugt den Request-Body. Die Sammlung der unbekannten Felder ist
     * veraenderlich — die JSON-Bindung traegt sie beim Binden ein.
     *
     * @param targetUrl     die Ziel-URL des Empfaengers
     * @param secret        das gemeinsame Secret des Endpunkts
     * @param eventTypes    die abonnierten Ereignistypen
     * @param unknownFields die unbekannten Felder des Request-Bodys
     */
    public EndpointCreateRequest {
        unknownFields = unknownFields == null ? new ArrayList<>() : unknownFields;
    }

    /**
     * Prueft den Request-Body gegen das Schema des Vertrags.
     *
     * @throws ValidationException wenn ein Feld das Schema verletzt
     */
    public void validate() {
        rejectUnknownFields();
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new ValidationException("target_url", "target_url_required",
                    "Die Ziel-URL ist erforderlich.");
        }
        if (targetUrl.length() > MAX_TARGET_URL_LENGTH) {
            throw new ValidationException("target_url", "target_url_too_long",
                    "Die Ziel-URL darf hoechstens " + MAX_TARGET_URL_LENGTH + " Zeichen lang sein.");
        }
        validateSecret(secret);
        EndpointEventTypes.of(eventTypes);
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
        return "EndpointCreateRequest[targetUrl=" + targetUrl + ", secret=<verborgen>, eventTypes=" + eventTypes + "]";
    }

    /**
     * Weist ein unbekanntes Feld ab — das Schema des Request-Bodys kennt es
     * nicht.
     *
     * @param field der Name des Feldes
     */
    static void rejectUnknownField(String field) {
        throw new ValidationException(field, "unknown_field",
                "Unbekanntes Feld im Request-Body: " + field);
    }

    static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new ValidationException("secret", "secret_required",
                    "Das Secret ist erforderlich.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new ValidationException("secret", "secret_too_short",
                    "Das Secret muss mindestens " + MIN_SECRET_LENGTH + " Zeichen lang sein.");
        }
        if (secret.length() > MAX_SECRET_LENGTH) {
            throw new ValidationException("secret", "secret_too_long",
                    "Das Secret darf hoechstens " + MAX_SECRET_LENGTH + " Zeichen lang sein.");
        }
    }

    private void rejectUnknownFields() {
        for (String field : unknownFields) {
            rejectUnknownField(field);
        }
    }
}
