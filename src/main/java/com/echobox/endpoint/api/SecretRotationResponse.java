package com.echobox.endpoint.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Antwort auf eine Secret-Rotation — die Projektion des Vertrags
 * {@code components.schemas.SecretRotationResponse}.
 *
 * <p>Das neue Secret wird <strong>nie</strong> ausgegeben: die Antwort nennt
 * den Stand der Rotation und die ausloesende Identitaet (Audit).
 *
 * @param endpointId    die Endpunkt-ID
 * @param secretVersion die neue Secret-Generation
 * @param rotatedAt     der Zeitpunkt der Rotation
 * @param rotatedBy     die Identitaet der ausloesenden Rolle
 */
public record SecretRotationResponse(
        @JsonProperty("endpoint_id") String endpointId,
        @JsonProperty("secret_version") int secretVersion,
        @JsonProperty("rotated_at") Instant rotatedAt,
        @JsonProperty("rotated_by") String rotatedBy) {
}
