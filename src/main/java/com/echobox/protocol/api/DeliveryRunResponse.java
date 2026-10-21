package com.echobox.protocol.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.delivery.DeliveryRun;

/**
 * Ein Zustelllauf in der Antwort — die Projektion des Vertrags
 * {@code components.schemas.DeliveryRun}.
 *
 * <p>Der Eintrag nennt <strong>Zustand</strong>, <strong>Anzahl der
 * Versuche</strong>, <strong>Zeitpunkt des letzten Versuchs</strong>,
 * <strong>Antwortcode des Empfaengers</strong> und <strong>naechsten
 * Versuchszeitpunkt</strong> (FR-030). Bei endgueltigen Zustaenden
 * ({@code sent}, {@code abandoned}, {@code rejected}, {@code cancelled},
 * {@code no_subscriber}) ist {@code next_attempt_at} <strong>leer</strong> — es
 * wird kein Zeitpunkt in der Vergangenheit oder in der Zukunft angezeigt
 * (AC-015-2).
 *
 * <p><strong>Leere Felder stehen nicht im JSON:</strong> {@code null} bedeutet
 * „nicht zutreffend“ (kein Endpunkt bei {@code no_subscriber}, kein
 * Replay-Vorgaenger, kein naechster Versuch in einem endgueltigen Zustand).
 *
 * <p>Das Protokoll fuehrt <strong>keine Nutzlast</strong>: die Suche laeuft
 * ausschliesslich ueber Metadaten (D-11, ADR-017).
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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryRunResponse(
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
        @JsonProperty("updated_at") Instant updatedAt) {

    /**
     * Bildet die Antwort aus dem Fachmodell.
     *
     * @param run der Zustelllauf
     * @return die Antwort
     */
    public static DeliveryRunResponse of(DeliveryRun run) {
        return new DeliveryRunResponse(
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
                run.updatedAt());
    }
}
