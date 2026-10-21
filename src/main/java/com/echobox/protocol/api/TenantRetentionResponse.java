package com.echobox.protocol.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Aufbewahrung <strong> eines Mandanten</strong> — die Projektion des
 * Vertrags {@code components.schemas.TenantRetention}.
 *
 * <p>Die Fristen sind <strong>angewandte</strong> Werte — der Mandant kann
 * sie selbst gesetzt haben oder die Vorgabe nutzen. Welches von beiden gilt,
 * steht je Datenart in {@code event_retention_source} und
 * {@code delivery_retention_source}.
 *
 * <p>{@code organisation_id} steht <strong>im Datensatz</strong>, damit der
 * Client die Herkunft erkennt (AC-016-1).
 *
 * @param organisationId        der Mandant — immer gefuellt
 * @param eventRetentionDays    die angewandte Frist fuer Ereignisse in Tagen (nullable)
 * @param eventRetentionSource   {@code tenant}, {@code default} oder {@code null} (nullable)
 * @param deliveryRetentionDays die angewandte Frist fuer Zustellprotokolle in Tagen (nullable)
 * @param deliveryRetentionSource {@code tenant}, {@code default} oder {@code null} (nullable)
 * @param eventCutoff           der Stichtag fuer Ereignisse (nullable)
 * @param deliveryCutoff        der Stichtag fuer Zustelllaeufe und Versuche (nullable)
 * @param markedEvents          die in diesem Durchlauf markierten Ereignisse
 * @param markedDeliveryRuns    die in diesem Durchlauf markierten Zustelllaeufe
 * @param markedAttempts        die mit ihrem Lauf markierten Versuche
 * @param purgedEvents          die in diesem Durchlauf physisch geloeschten Ereignisse
 * @param purgedDeliveryRuns    die in diesem Durchlauf physisch geloeschten Zustelllaeufe
 * @param purgedAttempts        die in diesem Durchlauf physisch geloeschten Versuche
 */
// Die nullable Felder sind im Vertrag **Pflicht** — sie stehen auch dann in der
// Antwort, wenn fuer den Mandanten keine Frist wirksam ist (`null`).
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TenantRetentionResponse(
        @JsonProperty("organisation_id") String organisationId,
        @JsonProperty("event_retention_days") Integer eventRetentionDays,
        @JsonProperty("event_retention_source") String eventRetentionSource,
        @JsonProperty("delivery_retention_days") Integer deliveryRetentionDays,
        @JsonProperty("delivery_retention_source") String deliveryRetentionSource,
        @JsonProperty("event_cutoff") Instant eventCutoff,
        @JsonProperty("delivery_cutoff") Instant deliveryCutoff,
        @JsonProperty("marked_events") long markedEvents,
        @JsonProperty("marked_delivery_runs") long markedDeliveryRuns,
        @JsonProperty("marked_attempts") long markedAttempts,
        @JsonProperty("purged_events") long purgedEvents,
        @JsonProperty("purged_delivery_runs") long purgedDeliveryRuns,
        @JsonProperty("purged_attempts") long purgedAttempts) {
}
