package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Pause und Reaktivierung eines Endpunkts (US-002, FR-002).
 *
 * <p>Die Pause hat drei Wirkungen, die hier jeweils einzeln belegt werden:
 * kein Zustellversuch, keine Warteschlange, Abbruch eines offenen
 * Wiederholungsvorgangs.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code pauseEndpoint} und
 * {@code resumeEndpoint}; `docs/architecture/domain_model.md` Abschnitt 3.3.
 */
class EndpointPauseIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://pause.example.invalid/hooks/echobox";

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-002-1: nach der Pause entsteht kein Zustellversuch")
    void after_pause_no_attempt_is_made() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        submitEvent("m-1", "order.changed", "pause-1", "{\"order_id\":1}");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"));

        assertThat(countAttempts(endpointId)).isZero();
    }

    @Test
    @DisplayName("AC-002-1: fuer einen pausierten Endpunkt entsteht kein Zustellvorgang")
    void a_paused_endpoint_creates_no_delivery_run() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());

        String eventId = submitEvent("m-1", "order.changed", "pause-2", "{\"order_id\":2}");

        // Geprueft wird der Lauf **dieses** Endpunkts: andere Endpunkte des
        // Mandanten, die denselben Ereignistyp abonniert haben, bekommen
        // durchaus einen Lauf (D-04).
        assertThat(countRunsForEndpoint(eventId, endpointId)).isZero();
    }

    @Test
    @DisplayName("AC-002-2: nach der Reaktivierung wird nichts nachgeliefert")
    void after_resume_nothing_is_delivered_afterwards() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());

        String eventId = submitEvent("m-1", "order.changed", "resume-1", "{\"order_id\":3}");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/resume")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));

        assertThat(countRunsForEvent(eventId))
                .as("es gibt keinen nachzutragenden Eintrag")
                .isZero();
        assertThat(countAttempts(endpointId)).isZero();
    }

    @Test
    @DisplayName("AC-002-3: ein fremder Endpunkt ergibt 404 und bleibt unveraendert")
    void a_foreign_endpoint_yields_not_found() throws Exception {
        String endpointId = registerEndpoint("m-2", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));

        assertThat(statusOf(endpointId)).isEqualTo("active");
    }

    @Test
    @DisplayName("AC-002-4: ein offener Wiederholungsvorgang wird abgebrochen")
    void an_open_retry_is_cancelled() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        String eventId = submitEvent("m-1", "order.changed", "cancel-1", "{\"order_id\":4}");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT status, next_attempt_at FROM app.delivery_runs WHERE event_id = ?", eventId);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).get("status")).isEqualTo("cancelled");
        assertThat(runs.get(0).get("next_attempt_at"))
                .as("ein abgebrochener Lauf hat keinen naechsten Versuchszeitpunkt")
                .isNull();
    }

    @Test
    @DisplayName("AC-002-5: die Endpunktliste zeigt den Status 'pausiert'")
    void the_endpoint_list_shows_the_paused_status() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.endpoint_id == '" + endpointId + "')].status")
                        .value(org.hamcrest.Matchers.hasItem("paused")));
    }

    @Test
    @DisplayName("AC-002-5: die Anzahl der Zustellversuche bleibt unveraendert")
    void the_attempt_count_stays_unchanged() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        submitEvent("m-1", "order.changed", "count-1", "{\"order_id\":5}");
        long before = countAttempts(endpointId);

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());

        assertThat(countAttempts(endpointId)).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-002-3: ein Entwickler darf den Endpunkt nicht pausieren (403)")
    void a_developer_may_not_pause_an_endpoint() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/pause")
                        .with(Auth.developer("m-1", "tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    private long countAttempts(String endpointId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.delivery_attempts WHERE endpoint_id = ?", Long.class, endpointId);
        return count == null ? 0L : count;
    }

    private long countRunsForEvent(String eventId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.delivery_runs WHERE event_id = ?", Long.class, eventId);
        return count == null ? 0L : count;
    }

    private long countRunsForEndpoint(String eventId, String endpointId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.delivery_runs WHERE event_id = ? AND endpoint_id = ?",
                Long.class, eventId, endpointId);
        return count == null ? 0L : count;
    }

    private String statusOf(String endpointId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.endpoints WHERE endpoint_id = ?", String.class, endpointId);
    }
}
