package com.echobox.protocol.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Kennzahlen eines Endpunkts — die Projektion des Vertrags
 * {@code components.schemas.EndpointMetrics}.
 *
 * <p><strong>Ohne Daten im Fenster</strong> gilt {@code data_available:
 * false}; dann sind {@code success_rate} und
 * {@code delivery_latency_p95_ms} <strong>{@code null}</strong> — <em>nicht</em>
 * 0 % und <em>nicht</em> 100 % (AC-017-3, ADR-011 §2). Eine Erfolgsquote von
 * 0 % wuerde „alles schlug fehl“ sagen — das waere eine Falschaussage.
 *
 * <p>Die Erfolgsquote ist {@code attempts_successful / attempts_total} im
 * Auswertungsfenster; die Zustelllatenz ist das p95 der Dauer von der
 * <strong>Annahme</strong> des Ereignisses bis zur <strong>erfolgreichen</strong>
 * Antwort (FR-032).
 *
 * @param endpointId           der Endpunkt
 * @param window               das Auswertungsfenster
 * @param dataAvailable        ob im Fenster ueberhaupt ein Versuch stattgefunden hat
 * @param attemptsTotal        die Zahl aller Versuche im Fenster (nullable)
 * @param attemptsSuccessful   die Zahl der erfolgreichen Versuche (nullable)
 * @param successRate          die Erfolgsquote (nullable)
 * @param deliveryLatencyP95Ms die p95-Zustelllatenz in Millisekunden (nullable)
 * @param rateLimit            die Beobachtung des Endpunkt-Limits — immer gefuellt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EndpointMetricsResponse(
        @JsonProperty("endpoint_id") String endpointId,
        @JsonProperty("window") MetricsWindowResponse window,
        @JsonProperty("data_available") boolean dataAvailable,
        @JsonProperty("attempts_total") Long attemptsTotal,
        @JsonProperty("attempts_successful") Long attemptsSuccessful,
        @JsonProperty("success_rate") Double successRate,
        @JsonProperty("delivery_latency_p95_ms") Long deliveryLatencyP95Ms,
        @JsonProperty("rate_limit") RateLimitObservationResponse rateLimit) {
}
