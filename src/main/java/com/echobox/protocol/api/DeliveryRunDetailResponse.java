package com.echobox.protocol.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.delivery.DeliveryAttempt;
import com.echobox.delivery.DeliveryRun;

/**
 * Ein Zustelllauf <strong>mit allen Versuchen</strong> — die Projektion des
 * Vertrags {@code components.schemas.DeliveryRunDetail}.
 *
 * <p>Der Vertrag bildet die Detailansicht aus {@code DeliveryRun} und dem
 * Zusatz {@code attempts}; die Felder des Laufs sind hier deshalb dieselben wie
 * in {@link DeliveryRunResponse}.
 *
 * <p>Die Versuche stehen <strong>chronologisch</strong> — die Versuchsnummer
 * ist 1-basiert und je Lauf eindeutig (I-4).
 *
 * @param deliveryRunId      die Zustelllauf-ID
 * @param eventId            die Ereignis-ID
 * @param endpointId         der Endpunkt (nullable bei {@code no_subscriber})
 * @param organisationId     der besitzende Mandant — immer gefuellt
 * @param eventType          der Ereignistyp
 * @param status             der Zustand des Laufs
 * @param attemptCount       die Zahl der bereits ausgefuehrten Versuche
 * @param maxAttempts        die Versuchsgrenze (nullable)
 * @param replayOf           der Vorgaengerlauf, wenn dieser Lauf ein Replay ist (nullable)
 * @param lastAttemptAt      der Zeitpunkt des letzten Versuchs (nullable)
 * @param nextAttemptAt      der naechste Versuchszeitpunkt (nullable)
 * @param lastResponseStatus der Antwortcode des Empfaengers im letzten Versuch (nullable)
 * @param lastErrorCode      der Fehlercode des letzten Versuchs (nullable)
 * @param rejectionReason    der Grund einer endgueltigen Ablehnung (nullable)
 * @param createdAt          der Anlegezeitpunkt
 * @param updatedAt          der Zeitpunkt der letzten Aenderung
 * @param attempts           die Versuche des Laufs, chronologisch
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryRunDetailResponse(
        @JsonProperty("delivery_run_id") String deliveryRunId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("endpoint_id") String endpointId,
        @JsonProperty("organisation_id") String organisationId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("status") String status,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("max_attempts") Integer maxAttempts,
        @JsonProperty("replay_of") String replayOf,
        @JsonProperty("last_attempt_at") Instant lastAttemptAt,
        @JsonProperty("next_attempt_at") Instant nextAttemptAt,
        @JsonProperty("last_response_status") Integer lastResponseStatus,
        @JsonProperty("last_error_code") String lastErrorCode,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("attempts") List<DeliveryAttemptResponse> attempts) {

    /**
     * Erzeugt die Detailansicht. Die Versuchsliste ist unabhaengig und
     * unveraenderlich.
     *
     * @param attempts die Versuche des Laufs
     */
    public DeliveryRunDetailResponse {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }

    /**
     * Bildet die Antwort aus dem Fachmodell.
     *
     * @param run      der Zustelllauf
     * @param attempts die Versuche des Laufs (chronologisch)
     * @return die Antwort
     */
    public static DeliveryRunDetailResponse of(DeliveryRun run, List<DeliveryAttempt> attempts) {
        List<DeliveryAttemptResponse> projected = attempts == null
                ? List.of()
                : attempts.stream().map(DeliveryAttemptResponse::of).toList();
        return new DeliveryRunDetailResponse(
                run.deliveryRunId().toString(),
                run.eventId().toString(),
                run.endpointId() == null ? null : run.endpointId().toString(),
                run.organisationId(),
                run.eventType(),
                run.status().wireValue(),
                run.attemptCount(),
                run.maxAttempts(),
                run.replayOf() == null ? null : run.replayOf().toString(),
                run.lastAttemptAt(),
                run.nextAttemptAt(),
                run.lastResponseStatus(),
                run.lastErrorCode(),
                run.rejectionReason(),
                run.createdAt(),
                run.updatedAt(),
                projected);
    }
}
