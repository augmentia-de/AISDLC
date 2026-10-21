package com.echobox.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Das Fachmodell eines Zustellversuchs (FR-030, D-06).
 *
 * <p>Ein Versuch ist ein <strong>einzelner</strong> HTTP-Versuch an den
 * Empfaenger. Die Versuchsnummer ist je Lauf eindeutig und 1-basiert
 * (DB-Constraint {@code delivery_attempts_run_number_uq}).
 *
 * <p>{@code eventAcceptedAt} ist denormalisiert: der Annahmezeitpunkt des
 * Ereignisses. Damit ist die Zustelllatenz (p95 von Annahme bis zur
 * erfolgreichen Antwort) ohne Join berechenbar (ADR-011, FR-032).
 *
 * @param attemptId        die Versuchs-ID — fuer jeden HTTP-Versuch eindeutig
 * @param organisationId   der besitzende Mandant
 * @param deliveryRunId    der Zustelllauf, zu dem der Versuch gehoert
 * @param eventId          die Ereignis-ID
 * @param endpointId       der Endpunkt (nullable)
 * @param eventAcceptedAt  der Annahmezeitpunkt des Ereignisses
 * @param attemptNumber    die Versuchsnummer innerhalb des Laufs (1-basiert)
 * @param startedAt        der Beginn des Versuchs
 * @param finishedAt       das Ende des Versuchs (nullable, wenn der Versuch laeuft)
 * @param durationMs       die Dauer des Versuchs in Millisekunden (nullable)
 * @param responseStatus   der HTTP-Statuscode der Antwort (nullable bei Transportfehlern)
 * @param outcome          das Ergebnis des Versuchs
 * @param errorCode        der Fehlercode bei Transportfehlern (nullable)
 * @param nextAttemptAt    der naechste Versuchszeitpunkt (nullable)
 */
public record DeliveryAttempt(UUID attemptId,
                               String organisationId,
                               UUID deliveryRunId,
                               UUID eventId,
                               UUID endpointId,
                               Instant eventAcceptedAt,
                               int attemptNumber,
                               Instant startedAt,
                               Instant finishedAt,
                               Long durationMs,
                               Integer responseStatus,
                               DeliveryOutcome outcome,
                               String errorCode,
                               Instant nextAttemptAt) {
}
