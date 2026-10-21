package com.echobox.delivery.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Antwort auf einen Replay — die Projektion des Vertrags
 * {@code components.schemas.ReplayResponse}.
 *
 * <p>Der Replay erzeugt einen <strong>neuen</strong> Zustelllauf mit
 * <strong>neuer</strong> ID und derselben Ereignis-ID; die Versuchsanzahl
 * beginnt wieder bei 0, der erste Versuch hat die Nummer 1 (AC-013-1,
 * AC-013-5). Der Vorgängerlauf bleibt im Protokoll unveraendert erhalten.
 *
 * @param previousDeliveryRunId der aufgegebene Zustelllauf
 * @param deliveryRunId         der neue Zustelllauf
 * @param eventId               die Ereignis-ID (identisch mit dem Vorgänger)
 * @param status                der Zustand des neuen Laufs ({@code pending})
 * @param attemptCount          die Zahl der Versuche (beginnt bei 0)
 * @param nextAttemptAt         der Zeitpunkt des ersten Versuchs (nullable)
 * @param replayedAt            der Zeitpunkt des Replays
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplayResponse(
        @JsonProperty("previous_delivery_run_id") String previousDeliveryRunId,
        @JsonProperty("delivery_run_id") String deliveryRunId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("status") String status,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("next_attempt_at") Instant nextAttemptAt,
        @JsonProperty("replayed_at") Instant replayedAt) {
}
