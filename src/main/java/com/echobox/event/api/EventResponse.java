package com.echobox.event.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.event.Event;

/**
 * Die Metadaten eines Ereignisses — die Projektion des Vertrags
 * {@code components.schemas.Event}.
 *
 * <p>Die Nutzlast wird <strong>nie</strong> ausgegeben: sie ist im Ruhezustand
 * verschluesselt (FR-012) und die Suche laeuft ausschliesslich ueber
 * Metadaten (D-11).
 *
 * @param eventId                 die Ereignis-ID
 * @param eventType               der Ereignistyp
 * @param idempotencyKey          der Idempotenzschluessel
 * @param status                  der Zustand
 * @param payloadEncoding         die Kodierung der Nutzlast
 * @param payloadMediaType        der Medientyp der Nutzlast
 * @param payloadSizeBytes        die Groesse der Nutzlastbytes
 * @param subscribedEndpointCount die Zahl der Abonnenten des Ereignistyps
 * @param acceptedAt              der Annahmezeitpunkt
 * @param createdAt               der Anlegezeitpunkt
 * @param updatedAt               der Zeitpunkt der letzten Aenderung
 */
public record EventResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("status") String status,
        @JsonProperty("payload_encoding") String payloadEncoding,
        @JsonProperty("payload_media_type") String payloadMediaType,
        @JsonProperty("payload_size_bytes") long payloadSizeBytes,
        @JsonProperty("subscribed_endpoint_count") int subscribedEndpointCount,
        @JsonProperty("accepted_at") Instant acceptedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    /**
     * Bildet die Antwort aus dem Fachmodell.
     *
     * @param event das Ereignis
     * @return die Antwort
     */
    public static EventResponse of(Event event) {
        return new EventResponse(
                event.eventId().toString(),
                event.eventType(),
                event.idempotencyKey(),
                event.status(),
                event.payloadEncoding().wireValue(),
                event.payloadMediaType(),
                event.payloadSizeBytes(),
                event.subscribedEndpointCount(),
                event.acceptedAt(),
                event.createdAt(),
                event.updatedAt());
    }
}
