package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Tote Zustellung erneut ausloesen (US-013, FR-024).
 *
 * <p>Der Replay erzeugt einen **neuen** Zustelllauf mit neuer ID und
 * zurueckgesetzter Versuchsanzahl; der urspruengliche Datensatz bleibt
 * unveraendert. Ein Lauf ist **einmal** replaybar — der zweite Versuch ergibt
 * HTTP 409 {@code delivery_state_conflict}.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code replayDelivery}
 * (POST /api/v1/deliveries/{deliveryRunId}/replay) und
 * {@code x-contract-rules.defaults} (F-03).
 */
class RedeliveryIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://redelivery.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
        registry.add("echobox.delivery.max-attempts", () -> "2");
    }

    @Test
    @DisplayName("AC-013-1: der Replay startet einen neuen Lauf mit der Versuchsanzahl 1")
    void the_replay_starts_a_new_run() throws Exception {
        String runId = createAbandonedRun("replay-1");

        MvcResult result = mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.previous_delivery_run_id").value(runId))
                .andExpect(jsonPath("$.delivery_run_id").isNotEmpty())
                .andExpect(jsonPath("$.attempt_count").value(0))
                .andReturn();

        String newRunId = readJson(result).path("delivery_run_id").asText();

        assertThat(newRunId).isNotEqualTo(runId);
        assertThat(countRuns()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-013-2: der urspruengliche Datensatz bleibt unveraendert")
    void the_original_record_stays_unchanged() throws Exception {
        String runId = createAbandonedRun("replay-2");
        Map<String, Object> before = runById(runId);

        mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isAccepted());

        Map<String, Object> after = runById(runId);

        assertThat(after.get("status")).isEqualTo(before.get("status"));
        assertThat(after.get("attempt_count")).isEqualTo(before.get("attempt_count"));
        assertThat(after.get("delivery_run_id")).isEqualTo(before.get("delivery_run_id"));
    }

    @Test
    @DisplayName("AC-013-3: eine unberechtigte Rolle erhaelt 403 und es startet kein Lauf")
    void an_unauthorized_role_is_forbidden() throws Exception {
        String runId = createAbandonedRun("replay-3");
        long before = countRuns();

        mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.developer("m-1", "tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        assertThat(countRuns()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-013-4: der Replay eines pausierten Endpunkts startet keinen Versuch")
    void the_replay_of_a_paused_endpoint_starts_no_attempt() throws Exception {
        String runId = createAbandonedRun("replay-4");

        // Der Test pausiert den Endpunkt, zu dem der aufgegebene Lauf gehoert —
        // sonst hinge die Aussage von der Reihenfolge der Anlage ab.
        String endpointId = jdbcTemplate.queryForObject(
                "SELECT endpoint_id FROM app.delivery_runs WHERE delivery_run_id = ?", String.class, runId);

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("delivery_state_conflict"));

        int requestsBefore = deliveryPort.requestCount();
        worker.runOnce();

        assertThat(deliveryPort.requestCount())
                .as("der Replay eines pausierten Endpunkts startet keinen Versuch")
                .isEqualTo(requestsBefore);
    }

    @Test
    @DisplayName("AC-013-5: der neue Lauf traegt eine neue Zustelllauf-ID und dieselbe Ereignis-ID")
    void the_new_run_has_a_new_id_and_the_same_event_id() throws Exception {
        String runId = createAbandonedRun("replay-5");
        String eventId = eventIdOf(runId);

        MvcResult result = mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isAccepted())
                .andReturn();

        String newRunId = readJson(result).path("delivery_run_id").asText();

        assertThat(eventIdOf(newRunId)).isEqualTo(eventId);
        assertThat(newRunId).isNotEqualTo(runId);
        // Der Treiber liefert eine UUID-Spalte als UUID-Objekt — der Vergleich
        // mit der Kennung aus dem Protokoll erfolgt ueber die Zeichenkette.
        assertThat(String.valueOf(replayOf(newRunId))).isEqualTo(runId);
    }

    @Test
    @DisplayName("AC-013-1: ein zweiter Replay desselben Laufs ergibt 409 delivery_state_conflict")
    void a_second_replay_yields_a_state_conflict() throws Exception {
        String runId = createAbandonedRun("replay-6");

        mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("delivery_state_conflict"));
    }

    @Test
    @DisplayName("AC-013-1: beide Laeufe sind im Protokoll getrennt sichtbar")
    void both_runs_are_visible_separately_in_the_protocol() throws Exception {
        String runId = createAbandonedRun("replay-7");

        mockMvc.perform(post("/api/v1/deliveries/" + runId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isAccepted());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    private String createAbandonedRun(String idempotencyKey) throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", idempotencyKey, "{\"order_id\":1}");

        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.runOnce();
            clock.advance(Duration.ofMinutes(1));
            if ("abandoned".equals(statusOfRun())) {
                break;
            }
        }
        return runIdOfRun();
    }

    private long countRuns() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app.delivery_runs", Long.class);
        return count == null ? 0L : count;
    }

    private String runIdOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT delivery_run_id FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }

    private String eventIdOf(String runId) {
        return jdbcTemplate.queryForObject(
                "SELECT event_id FROM app.delivery_runs WHERE delivery_run_id = ?", String.class, runId);
    }

    private Object replayOf(String runId) {
        return jdbcTemplate.queryForObject(
                "SELECT replay_of FROM app.delivery_runs WHERE delivery_run_id = ?", Object.class, runId);
    }

    private Map<String, Object> runById(String runId) {
        return jdbcTemplate.queryForMap(
                "SELECT delivery_run_id, status, attempt_count FROM app.delivery_runs WHERE delivery_run_id = ?", runId);
    }

    private String statusOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }
}
