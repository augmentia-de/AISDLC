package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Idempotente Einlieferung (US-006, FR-011).
 *
 * <p>Der Idempotenzschluessel gilt je Paar aus Mandant und Schluessel (D-05).
 * Die Wiederholung mit gleicher Nutzlast ist <em>erfolgreich</em>; die
 * Wiederholung mit abweichender Nutzlast ist ein Konflikt (409).
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-contract-rules.idempotency}
 * und {@code components.schemas.EventIntakeResponse}.
 */
class EventIdempotencyIT extends AbstractApplicationIT {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-006-1: die Wiederholung mit gleicher Nutzlast liefert die ID des ersten Ereignisses")
    void the_replay_returns_the_id_of_the_first_event() throws Exception {
        String first = submitEvent("m-1", "order.changed", "idem-1", "{\"order_id\":1}");

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "idem-1",
                                  "payload": {"order_id": 1}
                                }"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.event_id").value(first))
                .andExpect(jsonPath("$.idempotent_replay").value(true));
    }

    @Test
    @DisplayName("AC-006-2: es entsteht kein zweites Ereignis")
    void no_second_event_is_created() throws Exception {
        submitEvent("m-1", "order.changed", "idem-2", "{\"order_id\":2}");
        submitEvent("m-1", "order.changed", "idem-2", "{\"order_id\":2}");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT count(*) AS anzahl FROM app.events WHERE organisation_id = 'm-1' AND idempotency_key = 'idem-2'");

        assertThat(rows.get(0).get("anzahl")).isEqualTo(1L);
    }

    @Test
    @DisplayName("AC-006-2: Typ und Nutzlast des ersten Ereignisses bleiben unveraendert")
    void the_first_event_is_never_overwritten() throws Exception {
        submitEvent("m-1", "order.changed", "idem-3", "{\"order_id\":3}");

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "idem-3",
                                  "payload": {"order_id": 3}
                                }"""))
                .andExpect(status().isAccepted());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_type FROM app.events WHERE organisation_id = 'm-1' AND idempotency_key = 'idem-3'");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("event_type")).isEqualTo("order.changed");
    }

    @Test
    @DisplayName("AC-006-3: derselbe Schluessel in einem anderen Mandanten ergibt ein eigenes Ereignis")
    void the_same_key_in_another_tenant_creates_its_own_event() throws Exception {
        String first = submitEvent("m-1", "order.changed", "idem-4", "{\"order_id\":4}");

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-2", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "idem-4",
                                  "payload": {"order_id": 4}
                                }"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idempotent_replay").value(false))
                .andReturn();

        String second = readJson(result).path("event_id").asText();

        assertThat(second).isNotEqualTo(first);
        assertThat(countEventsIn("m-2", "idem-4")).isEqualTo(1L);
    }

    @Test
    @DisplayName("AC-006-3: die Wiederholung in M1 bleibt von M2 unberuehrt")
    void the_replay_in_m1_is_untouched_by_m2() throws Exception {
        String first = submitEvent("m-1", "order.changed", "idem-5", "{\"order_id\":5}");
        submitEvent("m-2", "order.changed", "idem-5", "{\"order_id\":5}");

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "idem-5",
                                  "payload": {"order_id": 5}
                                }"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.event_id").value(first))
                .andExpect(jsonPath("$.idempotent_replay").value(true));
    }

    @Test
    @DisplayName("AC-006-4: dieselbe Nutzlast mit abweichendem Inhalt ergibt 409")
    void a_differing_payload_yields_a_conflict() throws Exception {
        submitEvent("m-1", "order.changed", "idem-6", "{\"order_id\":6}");

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "idem-6",
                                  "payload": {"order_id": 999}
                                }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    @Test
    @DisplayName("AC-006-4: das erste Ereignis bleibt nach dem Konflikt unveraendert")
    void the_first_event_survives_the_conflict() throws Exception {
        submitEvent("m-1", "order.changed", "idem-7", "{\"order_id\":7}");

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "idem-7",
                                  "payload": {"order_id": 888}
                                }"""))
                .andExpect(status().isConflict());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT count(*) AS anzahl FROM app.events WHERE organisation_id = 'm-1' AND idempotency_key = 'idem-7'");

        assertThat(rows.get(0).get("anzahl")).isEqualTo(1L);
    }

    private long countEventsIn(String organisationId, String idempotencyKey) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.events WHERE organisation_id = ? AND idempotency_key = ?",
                Long.class, organisationId, idempotencyKey);
        return count == null ? 0L : count;
    }
}
