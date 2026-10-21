package com.echobox.protocol.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.delivery.DeliveryAttempt;

/**
 * Ein Zustellversuch in der Antwort — die Projektion des Vertrags
 * {@code components.schemas.DeliveryAttempt}.
 *
 * <p>Ein Versuch ist ein <strong>einzelner</strong> HTTP-Versuch an den
 * Empfaenger; die Versuchsnummer ist je Zustelllauf eindeutig und 1-basiert
 * (D-06).
 *
 * <p>Leere Felder stehen nicht im JSON: ein noch laufender Versuch hat kein
 * Ende, keine Dauer und keinen Antwortcode.
 *
 * @param attemptId      die Versuchs-ID
 * @param deliveryRunId  der Zustelllauf, zu dem der Versuch gehoert
 * @param attemptNumber  die Versuchsnummer innerhalb des Laufs (1-basiert)
 * @param startedAt      der Beginn des Versuchs
 * @param finishedAt     das Ende des Versuchs (nullable)
 * @param durationMs     die Dauer des Versuchs in Millisekunden (nullable)
 * @param responseStatus der HTTP-Statuscode der Antwort (nullable bei Transportfehlern)
 * @param outcome        das Ergebnis des Versuchs
 * @param errorCode      der Fehlercode bei Transportfehlern (nullable)
 * @param nextAttemptAt  der naechste Versuchszeitpunkt (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryAttemptResponse(
        @JsonProperty("attempt_id") String attemptId,
        @JsonProperty("delivery_run_id") String deliveryRunId,
        @JsonProperty("attempt_number") int attemptNumber,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("finished_at") Instant finishedAt,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("response_status") Integer responseStatus,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("next_attempt_at") Instant nextAttemptAt) {

    /**
     * Bildet die Antwort aus dem Fachmodell.
     *
     * @param attempt der Zustellversuch
     * @return die Antwort
     */
    public static DeliveryAttemptResponse of(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.attemptId().toString(),
                attempt.deliveryRunId().toString(),
                attempt.attemptNumber(),
                attempt.startedAt(),
                attempt.finishedAt(),
                attempt.durationMs(),
                attempt.responseStatus(),
                attempt.outcome().wireValue(),
                attempt.errorCode(),
                attempt.nextAttemptAt());
    }
}
