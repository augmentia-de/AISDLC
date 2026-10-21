package com.echobox.event.api;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import com.echobox.common.ValidationException;
import com.echobox.event.PayloadEncoding;

/**
 * Der Request-Body der Ereignis-Einlieferung.
 *
 * <p>Der Vertrag fordert <strong>genau eine</strong> der Nutzlasten
 * {@code payload} (JSON-Wert) und {@code payload_base64} (Bytefolge). Ein
 * {@code payload: null} verletzt das Schema und gilt <strong>nicht</strong> als
 * gesetzt (HTTP 422 {@code validation_failed}).
 *
 * <p>Bei {@code payload_encoding = pre_encrypted} ist {@code payload_base64}
 * <strong>Pflicht</strong> und {@code payload} <strong>verboten</strong>
 * (AC-008-2).
 *
 * @param eventType        der Ereignistyp
 * @param idempotencyKey   der Idempotenzschluessel (Geltungsbereich: Mandant + Schluessel)
 * @param payload          die Nutzlast als JSON-Wert (nullable)
 * @param payloadBase64    die Nutzlast als Base64 (nullable)
 * @param payloadEncoding  die Kodierung der Nutzlast (Default {@code plaintext})
 * @param payloadMediaType der Medientyp der Nutzlast (Default {@code application/json})
 * @param unknownFields    die unbekannten Felder des Request-Bodys (intern)
 */
public record EventIntakeRequest(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("payload") JsonNode payload,
        @JsonProperty("payload_base64") String payloadBase64,
        @JsonProperty("payload_encoding") String payloadEncoding,
        @JsonProperty("payload_media_type") String payloadMediaType,
        @JsonIgnore List<String> unknownFields) {

    /** Die groesste Laenge eines Ereignistyps. */
    public static final int MAX_EVENT_TYPE_LENGTH = 200;

    /** Die groesste Laenge eines Idempotenzschluessels. */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    /** Die groesste Laenge des Medientyps. */
    public static final int MAX_MEDIA_TYPE_LENGTH = 100;

    /** Der Vorgabewert des Medientyps. */
    public static final String DEFAULT_MEDIA_TYPE = "application/json";

    /**
     * Erzeugt den Request-Body. Die Sammlung der unbekannten Felder ist
     * veraenderlich — die JSON-Bindung traegt sie beim Binden ein.
     *
     * @param eventType        der Ereignistyp
     * @param idempotencyKey   der Idempotenzschluessel
     * @param payload          die Nutzlast als JSON-Wert
     * @param payloadBase64    die Nutzlast als Base64
     * @param payloadEncoding  die Kodierung der Nutzlast
     * @param payloadMediaType der Medientyp der Nutzlast
     * @param unknownFields    die unbekannten Felder des Request-Bodys
     */
    public EventIntakeRequest {
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
        if (eventType == null || eventType.isBlank()) {
            throw new ValidationException("event_type", "event_type_required",
                    "Der Ereignistyp ist erforderlich.");
        }
        if (eventType.length() > MAX_EVENT_TYPE_LENGTH) {
            throw new ValidationException("event_type", "event_type_too_long",
                    "Der Ereignistyp darf hoechstens " + MAX_EVENT_TYPE_LENGTH + " Zeichen lang sein.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("idempotency_key", "idempotency_key_required",
                    "Der Idempotenzschluessel ist erforderlich.");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ValidationException("idempotency_key", "idempotency_key_too_long",
                    "Der Idempotenzschluessel darf hoechstens " + MAX_IDEMPOTENCY_KEY_LENGTH
                            + " Zeichen lang sein.");
        }
        if (payloadMediaType != null && payloadMediaType.length() > MAX_MEDIA_TYPE_LENGTH) {
            throw new ValidationException("payload_media_type", "payload_media_type_too_long",
                    "Der Medientyp darf hoechstens " + MAX_MEDIA_TYPE_LENGTH + " Zeichen lang sein.");
        }
        encoding();

        boolean jsonPresent = payload != null && !payload.isNull();
        boolean base64Present = payloadBase64 != null;
        if (payload != null && payload.isNull()) {
            throw new ValidationException("payload", "payload_null",
                    "Die Nutzlast darf nicht null sein — das Feld gilt nur dann als gesetzt, "
                            + "wenn es einen Wert traegt.");
        }
        if (jsonPresent == base64Present) {
            throw new ValidationException("payload", "exactly_one_payload",
                    "Genau eine der Nutzlasten payload und payload_base64 muss gesetzt sein.");
        }
        if (encoding() == PayloadEncoding.PRE_ENCRYPTED && !base64Present) {
            throw new ValidationException("payload_base64", "pre_encrypted_requires_base64",
                    "Eine vorverschluesselte Einlieferung verlangt payload_base64.");
        }
    }

    /**
     * Die Kodierung der Nutzlast — ohne Angabe {@code plaintext}.
     *
     * @return die Kodierung
     */
    public PayloadEncoding encoding() {
        if (payloadEncoding == null || payloadEncoding.isBlank()) {
            return PayloadEncoding.PLAINTEXT;
        }
        try {
            return PayloadEncoding.fromWire(payloadEncoding);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("payload_encoding", "payload_encoding_invalid",
                    "Die Nutzlast-Kodierung muss 'plaintext' oder 'pre_encrypted' sein.");
        }
    }

    /**
     * Der Medientyp der Nutzlast — ohne Angabe {@code application/json}.
     *
     * @return der Medientyp
     */
    public String mediaType() {
        return payloadMediaType == null || payloadMediaType.isBlank() ? DEFAULT_MEDIA_TYPE : payloadMediaType;
    }

    /**
     * Die dekodierten Bytes der Base64-Nutzlast — {@code null}, wenn die
     * Einlieferung keine Base64-Nutzlast traegt.
     *
     * @return die Bytes
     */
    public byte[] base64Bytes() {
        if (payloadBase64 == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(payloadBase64);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("payload_base64", "payload_base64_invalid",
                    "Die Nutzlast ist keine gueltige Base64-Folge.");
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
