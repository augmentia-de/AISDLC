package com.echobox.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Das Fachmodell eines Zustelllaufs (FR-030, D-06).
 *
 * <p>Ein Zustelllauf ist die Kette <strong>ueber alle Versuche</strong> eines
 * Ereignisses an <strong>einen</strong> Endpunkt. Er ist zugleich
 * Warteschlangeneintrag und Protokollzeile — es gibt keine zweite
 * Warteschlange (ADR-006 §1).
 *
 * <p>Der Zustand wird ausschliesslich ueber {@link DeliveryStateMachine}
 * geaendert. Die Invarianten des Schemas gelten fuer dieses Modell:
 * <ul>
 *   <li>ein endgueltiger Zustand traegt <strong>keinen</strong> naechsten
 *       Versuchszeitpunkt,</li>
 *   <li>ein aufgegebener Lauf traegt den Zeitpunkt der Aufgabe,</li>
 *   <li>ein Lauf ohne Abnehmer traegt <strong>keinen</strong> Endpunkt.</li>
 * </ul>
 *
 * @param deliveryRunId       die Zustelllauf-ID
 * @param organisationId      der besitzende Mandant
 * @param eventId             die Ereignis-ID (stabil ueber alle Laeufe und Replays)
 * @param endpointId          der Endpunkt (nullable bei {@code no_subscriber})
 * @param eventType           der Ereignistyp (denormalisiert, ADR-017 §2)
 * @param status              der Zustand des Laufs
 * @param attemptCount        die Zahl der bereits ausgefuehrten Versuche
 * @param maxAttempts         die beim Anlegen festgeschriebene Versuchsgrenze (nullable)
 * @param nextAttemptAt       der naechste Versuchszeitpunkt (nullable bei endgueltigen Zustaenden)
 * @param lastAttemptAt       der Zeitpunkt des letzten Versuchs (nullable)
 * @param lastResponseStatus  der Antwortcode des Empfaengers im letzten Versuch (nullable)
 * @param lastErrorCode       der Fehlercode des letzten Versuchs (nullable)
 * @param rejectionReason     der Grund einer endgueltigen Ablehnung (nullable)
 * @param abandonedAt         der Zeitpunkt der Aufgabe (nullable)
 * @param replayOf            der Vorgängerlauf, wenn dieser Lauf ein Replay ist (nullable)
 * @param leaseOwner          die Worker-Instanz, die den Lauf beansprucht hat (nullable)
 * @param leaseExpiresAt      der Ablauf der Beanspruchung (nullable)
 * @param createdAt           der Anlegezeitpunkt
 * @param updatedAt           der Zeitpunkt der letzten Aenderung
 */
public record DeliveryRun(UUID deliveryRunId,
                           String organisationId,
                           UUID eventId,
                           UUID endpointId,
                           String eventType,
                           DeliveryStatus status,
                           int attemptCount,
                           Integer maxAttempts,
                           Instant nextAttemptAt,
                           Instant lastAttemptAt,
                           Integer lastResponseStatus,
                           String lastErrorCode,
                           String rejectionReason,
                           Instant abandonedAt,
                           UUID replayOf,
                           String leaseOwner,
                           Instant leaseExpiresAt,
                           Instant createdAt,
                           Instant updatedAt) {
}
