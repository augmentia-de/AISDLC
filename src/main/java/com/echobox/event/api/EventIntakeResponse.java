package com.echobox.event.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Antwort auf eine Ereignis-Einlieferung — die Projektion des Vertrags
 * {@code components.schemas.EventIntakeResponse}.
 *
 * <p>Bei einer idempotenten Wiederholung traegt die Antwort die Kennung des
 * <strong>ersten</strong> Ereignisses und {@code idempotent_replay: true}
 * (AC-006-1).
 *
 * @param eventId                 die Ereignis-ID
 * @param eventType               der Ereignistyp
 * @param idempotencyKey          der Idempotenzschluessel
 * @param status                  der Zustand ({@code accepted} oder {@code no_subscriber})
 * @param idempotentReplay        ob es eine Wiederholung war
 * @param subscribedEndpointCount die Zahl der Abonnenten des Ereignistyps
 * @param payloadSizeBytes        die Groesse der Nutzlastbytes
 * @param acceptedAt              der Annahmezeitpunkt
 */
public record EventIntakeResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("status") String status,
        @JsonProperty("idempotent_replay") boolean idempotentReplay,
        @JsonProperty("subscribed_endpoint_count") int subscribedEndpointCount,
        @JsonProperty("payload_size_bytes") long payloadSizeBytes,
        @JsonProperty("accepted_at") Instant acceptedAt) {
}
