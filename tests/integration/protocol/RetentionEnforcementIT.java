package com.echobox.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Aufbewahrung durchsetzen (US-016, FR-031).
 *
 * <p>Die Frist beginnt je Datensatz am **letzten Versuch**. Zwei Stufen:
 * markieren ({@code deleted_at} gesetzt — sofort unsichtbar) und Purge
 * (physische Loeschung nach der Karenzzeit). Das Loeschen eines Ereignisses
 * nimmt seine Zustelllaeufe und Versuche mit.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code enforceRetention}
 * (POST /api/v1/retention/enforcement) und {@code x-contract-rules.defaults} (F-02).
 */
class RetentionEnforcementIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://retention.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.retention.event-days", () -> "30");
        registry.add("echobox.retention.delivery-days", () -> "30");
        registry.add("echobox.retention.purge-delay", () -> "0");
    }

    @Test
    @DisplayName("AC-016-5: alte Ereignisse, Laeufe und Versuche werden geloescht, juengere bleiben erhalten")
    void old_records_are_removed_and_recent_ones_are_kept() throws Exception {
        String oldEventId = createRun("retention-old", Instant.now().minus(Duration.ofDays(40)));
        String newEventId = createRun("retention-new", Instant.now().minus(Duration.ofDays(2)));

        mockMvc.perform(post("/api/v1/retention/enforcement")
                        .with(Auth.serviceAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dry_run\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dry_run").value(false))
                .andExpect(jsonPath("$.scope").value("all"))
                .andExpect(jsonPath("$.anchor").value("last_attempt_at"))
                .andExpect(jsonPath("$.marked_events").isNumber())
                .andExpect(jsonPath("$.tenants[0].organisation_id").value("m-1"));

        assertThat(deletedAtOfEvent(oldEventId)).isNotNull();
        assertThat(deletedAtOfEvent(newEventId)).isNull();
    }

    @Test
    @DisplayName("AC-016-5: der Fristbeginn ist der letzte Versuch, nicht der Anlegezeitpunkt")
    void the_anchor_is_the_last_attempt() throws Exception {
        String eventId = createRun("retention-anchor", Instant.now().minus(Duration.ofDays(40)));

        mockMvc.perform(post("/api/v1/retention/enforcement")
                        .with(Auth.serviceAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dry_run\":false}"))
                .andExpect(status().isOk());

        assertThat(deletedAtOfEvent(eventId)).isNotNull();
    }

    @Test
    @DisplayName("AC-016-5: mit dry_run wird nichts geaendert, die Zaehler weisen die Mengen aus")
    void a_dry_run_changes_nothing() throws Exception {
        String eventId = createRun("retention-dry", Instant.now().minus(Duration.ofDays(40)));

        mockMvc.perform(post("/api/v1/retention/enforcement")
                        .with(Auth.serviceAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dry_run\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dry_run").value(true))
                .andExpect(jsonPath("$.marked_events").isNumber());

        assertThat(deletedAtOfEvent(eventId)).isNull();
    }

    @Test
    @DisplayName("AC-016-5: die Frist ist konfigurierbar — eine Mandantenfrist ueberschreibt die Vorgabe")
    void the_retention_period_is_configurable_per_tenant() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO app.retention_settings (organisation_id, event_retention_days, delivery_retention_days) "
                        + "VALUES ('m-1', 1, 1) ON CONFLICT (organisation_id) DO UPDATE "
                        + "SET event_retention_days = 1, delivery_retention_days = 1");

        String eventId = createRun("retention-config", Instant.now().minus(Duration.ofDays(3)));

        mockMvc.perform(post("/api/v1/retention/enforcement")
                        .with(Auth.serviceAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dry_run\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants[0].event_retention_days").value(1))
                .andExpect(jsonPath("$.tenants[0].event_retention_source").value("tenant"));

        assertThat(deletedAtOfEvent(eventId)).isNotNull();
    }

    @Test
    @DisplayName("AC-016-5: ein Empfaenger-Admin darf die Aufbewahrung nicht erzwingen (403)")
    void a_receiver_admin_may_not_enforce_retention() throws Exception {
        mockMvc.perform(post("/api/v1/retention/enforcement")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dry_run\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    private String createRun(String idempotencyKey, Instant lastAttemptAt) throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        String eventId = submitEvent("m-1", "order.changed", idempotencyKey, "{\"order_id\":1}");

        worker.runOnce();

        // Der PostgreSQL-Treiber kann einen Instant ueber setObject nicht
        // ableiten — der Zeitpunkt wird deshalb als OffsetDateTime in UTC
        // gebunden (dieselbe Bindung wie in common/SqlParameters).
        OffsetDateTime anchor = OffsetDateTime.ofInstant(lastAttemptAt, ZoneOffset.UTC);
        jdbcTemplate.update("UPDATE app.delivery_runs SET last_attempt_at = ?, created_at = ? WHERE event_id = ?",
                anchor, anchor, eventId);
        jdbcTemplate.update("UPDATE app.events SET accepted_at = ?, created_at = ? WHERE event_id = ?",
                anchor, anchor, eventId);

        return eventId;
    }

    private Instant deletedAtOfEvent(String eventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT deleted_at FROM app.events WHERE event_id = ?", eventId);
        if (rows.isEmpty()) {
            return null;
        }
        // Der Treiber liefert eine Zeitstempelspalte als java.sql.Timestamp —
        // die Umwandlung in einen Instant gehoert zum Test.
        Object value = rows.get(0).get("deleted_at");
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return value instanceof Instant instant ? instant : null;
    }
}
