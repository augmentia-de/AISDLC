package com.echobox.protocol.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.delivery.DeliveryRepository;

/**
 * Eine tote Zustellung — die Projektion des Vertrags
 * {@code components.schemas.DeadDelivery}.
 *
 * <p>Die Totenliste enthaelt <strong>ausschliesslich aufgegebene
 * Zustellungen</strong> — Status {@code abandoned} mit gesetztem
 * {@code abandoned_at} (GLOSSARY „Tote Zustellung“). Der Zeitpunkt der Aufgabe
 * steht in {@code abandoned_at}; er ist <strong>immer</strong> gefuellt (die
 * Datenbank erzwingt das fuer jeden aufgegebenen Lauf).
 *
 * <p>{@code organisation_id} ist in <strong>jeder</strong> Zeile gefuellt: die
 * Liste ist mandantenuebergreifend, und der Client erkennt so die Herkunft
 * (AC-016-1).
 *
 * <p>Die Liste fuehrt <strong>keine Nutzlast</strong> (ADR-017 §6).
 *
 * @param deliveryRunId      die Zustelllauf-ID
 * @param eventId            die Ereignis-ID
 * @param organisationId     der besitzende Mandant — immer gefuellt
 * @param endpointId         der Endpunkt
 * @param endpointUrl        die Ziel-URL des Endpunkts (nullable)
 * @param eventType          der Ereignistyp
 * @param status             der Zustand — immer {@code abandoned}
 * @param attemptCount       die Zahl der ausgefuehrten Versuche
 * @param lastResponseStatus der Antwortcode des Empfaengers im letzten Versuch (nullable)
 * @param lastAttemptAt      der Zeitpunkt des letzten Versuchs (nullable)
 * @param abandonedAt        der Zeitpunkt der Aufgabe — immer gefuellt
 * @param createdAt          der Anlegezeitpunkt
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeadDeliveryResponse(
        @JsonProperty("delivery_run_id") String deliveryRunId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("organisation_id") String organisationId,
        @JsonProperty("endpoint_id") String endpointId,
        @JsonProperty("endpoint_url") String endpointUrl,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("status") String status,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("last_response_status") Integer lastResponseStatus,
        @JsonProperty("last_attempt_at") Instant lastAttemptAt,
        @JsonProperty("abandoned_at") Instant abandonedAt,
        @JsonProperty("created_at") Instant createdAt) {

    /**
     * Bildet die Antwort aus dem Fachmodell.
     *
     * @param dead die tote Zustellung
     * @return die Antwort
     */
    public static DeadDeliveryResponse of(DeliveryRepository.DeadDelivery dead) {
        return new DeadDeliveryResponse(
                dead.deliveryRunId().toString(),
                dead.eventId().toString(),
                dead.organisationId(),
                dead.endpointId() == null ? null : dead.endpointId().toString(),
                dead.endpointUrl(),
                dead.eventType(),
                dead.status(),
                dead.attemptCount(),
                dead.lastResponseStatus(),
                dead.lastAttemptAt(),
                dead.abandonedAt(),
                dead.createdAt());
    }
}
