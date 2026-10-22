package com.echobox.endpoint.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.endpoint.Endpoint;

/**
 * Die Antwort auf einen Endpunkt-Vorgang — die Projektion des Vertrags
 * {@code components.schemas.Endpoint}.
 *
 * <p>Das Secret wird <strong>nie</strong> ausgegeben: die Antwort nennt nur
 * {@code secret_version} und {@code secret_rotated_at} (AC-001-4).
 *
 * @param endpointId      die Endpunkt-ID
 * @param organisationId  der besitzende Mandant — immer gefuellt
 * @param targetUrl       die Ziel-URL
 * @param status          der Zustand ({@code active} oder {@code paused})
 * @param eventTypes      die abonnierten Ereignistypen
 * @param secretVersion   die laufende Secret-Generation
 * @param secretRotatedAt der Zeitpunkt der letzten Rotation (nullable)
 * @param pausedAt        der Zeitpunkt der Pause (nullable)
 * @param createdAt       der Anlegezeitpunkt
 * @param updatedAt       der Zeitpunkt der letzten Aenderung
 */
public record EndpointResponse(
        @JsonProperty("endpoint_id") String endpointId,
        @JsonProperty("organisation_id") String organisationId,
        @JsonProperty("target_url") String targetUrl,
        @JsonProperty("status") String status,
        @JsonProperty("event_types") List<String> eventTypes,
        @JsonProperty("secret_version") int secretVersion,
        @JsonProperty("secret_rotated_at") Instant secretRotatedAt,
        @JsonProperty("paused_at") Instant pausedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    /**
     * Bildet die Antwort aus dem Fachmodell.
     *
     * @param endpoint der Endpunkt
     * @return die Antwort
     */
    public static EndpointResponse of(Endpoint endpoint) {
        return new EndpointResponse(
                endpoint.endpointId().toString(),
                endpoint.organisationId(),
                endpoint.targetUrl().value(),
                endpoint.status().wireValue(),
                endpoint.eventTypes(),
                endpoint.secretVersion(),
                endpoint.secretRotatedAt(),
                endpoint.pausedAt(),
                endpoint.createdAt(),
                endpoint.updatedAt());
    }
}
