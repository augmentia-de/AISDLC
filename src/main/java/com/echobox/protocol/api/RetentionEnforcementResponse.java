package com.echobox.protocol.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Das Ergebnis der Aufbewahrungsdurchsetzung — die Projektion des Vertrags
 * {@code components.schemas.RetentionEnforcementResponse}.
 *
 * <p>Die beiden Fristen sind <strong>getrennt</strong> fuer Ereignisse und
 * fuer Zustellprotokolle (F-02) und beginnen <strong>je Datensatz</strong> am
 * <strong>Anker</strong> ({@code last_attempt_at}; fuer Ereignisse ohne
 * Versuch {@code accepted_at}).
 *
 * <p>Die Zaehler sind <strong>nach Stufe</strong> getrennt: {@code marked_*}
 * zaehlt die in diesem Durchlauf <strong>markierten</strong> Datensaetze
 * ({@code deleted_at} gesetzt), {@code purged_*} die <strong>physisch
 * geloeschten</strong>. Bei {@code dry_run: true} wird nichts geaendert — die
 * Zaehler weisen dann die <strong>vorgemerkten</strong> Mengen aus.
 *
 * <p>Die Fristen und Stichtage auf oberster Ebene sind die <strong>kuerzeste</strong>
 * angewandte Frist ueber alle Mandanten (der aelteste Stichtag); je Mandant
 * stehen die eigenen Fristen, Stichtage und Zaehlerstaende in {@code tenants}.
 *
 * @param dryRun               ob nur gezaehlt wurde
 * @param scope                der Geltungsbereich — immer {@code all}
 * @param anchor               der Anker je Datensatz — immer {@code last_attempt_at}
 * @param purgeDelayDays       die Karenzzeit zwischen Markieren und Purge in Tagen (nullable)
 * @param eventRetentionDays   die kuerzeste angewandte Frist fuer Ereignisse (nullable)
 * @param deliveryRetentionDays die kuerzeste angewandte Frist fuer Zustellprotokolle (nullable)
 * @param eventCutoff          der Stichtag fuer Ereignisse (nullable)
 * @param deliveryCutoff       der Stichtag fuer Zustelllaeufe und Versuche (nullable)
 * @param markedEvents         die markierten Ereignisse ueber alle Mandanten
 * @param markedDeliveryRuns   die markierten Zustelllaeufe ueber alle Mandanten
 * @param markedAttempts       die markierten Versuche ueber alle Mandanten
 * @param purgedEvents         die geloeschten Ereignisse ueber alle Mandanten
 * @param purgedDeliveryRuns   die geloeschten Zustelllaeufe ueber alle Mandanten
 * @param purgedAttempts       die geloeschten Versuche ueber alle Mandanten
 * @param tenants              je Mandant die eigenen Fristen, Stichtage und Zaehler
 * @param executedAt           der Zeitpunkt der Durchsetzung
 */
// Die nullable Felder sind im Vertrag **Pflicht** — sie stehen auch dann in der
// Antwort, wenn kein Wert gilt (etwa `purge_delay_days: null`, weil die
// Karenzzeit keinen Vorgabewert hat, R-01/E-13).
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RetentionEnforcementResponse(
        @JsonProperty("dry_run") boolean dryRun,
        @JsonProperty("scope") String scope,
        @JsonProperty("anchor") String anchor,
        @JsonProperty("purge_delay_days") Integer purgeDelayDays,
        @JsonProperty("event_retention_days") Integer eventRetentionDays,
        @JsonProperty("delivery_retention_days") Integer deliveryRetentionDays,
        @JsonProperty("event_cutoff") Instant eventCutoff,
        @JsonProperty("delivery_cutoff") Instant deliveryCutoff,
        @JsonProperty("marked_events") long markedEvents,
        @JsonProperty("marked_delivery_runs") long markedDeliveryRuns,
        @JsonProperty("marked_attempts") long markedAttempts,
        @JsonProperty("purged_events") long purgedEvents,
        @JsonProperty("purged_delivery_runs") long purgedDeliveryRuns,
        @JsonProperty("purged_attempts") long purgedAttempts,
        @JsonProperty("tenants") List<TenantRetentionResponse> tenants,
        @JsonProperty("executed_at") Instant executedAt) {

    /**
     * Erzeugt das Ergebnis. Die Mandantenliste ist unabhaengig und
     * unveraenderlich.
     *
     * @param tenants die Mandanten mit ihren Fristen, Stichtagen und Zaehlern
     */
    public RetentionEnforcementResponse {
        tenants = tenants == null ? List.of() : List.copyOf(tenants);
    }
}
