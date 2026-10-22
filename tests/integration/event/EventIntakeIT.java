package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Ereignis einliefern (US-005, FR-010, NFR-001, NFR-002).
 *
 * <p>Der Annahmevorgang ist der heisseste Pfad des Dienstes: er muss schnell
 * antworten, die Groessengrenze durchsetzen und die Zustellung asynchron
 * ausloesen.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code submitEvent}
 * (POST /api/v1/events) und {@code components.schemas.EventIntakeRequest}.
 */
class EventIntakeIT extends AbstractApplicationIT {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-005-1: die Einlieferung wird mit 202 angenommen und liefert die Ereignis-ID")
    void the_event_is_accepted_with_202() throws Exception {
        // Der Test legt seinen Abnehmer **selbst** an (Teststrategie §2: "Jeder
        // Test legt seine Endpunkte ... selbst an") — ohne Abnehmer waere das
        // Ereignis ein `no_subscriber`-Ereignis (D-04), nicht `accepted`.
        registerEndpoint("m-1", "https://intake.example.invalid/hooks/echobox", "order.changed");

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "intake-1",
                                  "payload": {"order_id": 4711}
                                }"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.event_id").isNotEmpty())
                .andExpect(jsonPath("$.event_type").value("order.changed"))
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.idempotent_replay").value(false))
                .andExpect(jsonPath("$.accepted_at").isNotEmpty())
                .andExpect(jsonPath("$.payload_size_bytes").isNumber());
    }

    @Test
    @DisplayName("AC-005-1: die Zustellung startet erst nach der Antwort")
    void the_delivery_starts_after_the_response() throws Exception {
        submitEvent("m-1", "order.changed", "async-1", "{\"order_id\":4712}");

        assertThat(deliveryPort.requestCount())
                .as("die Annahme blockiert die Zustellung nicht")
                .isZero();
    }

    @Test
    @DisplayName("AC-005-2: eine Nutzlast von exakt 1.048.576 Byte wird angenommen")
    void a_payload_of_exactly_one_megabyte_is_accepted() throws Exception {
        String padding = "x".repeat(1_048_576 - "{\"note\":\"\",\"order_id\":1}".length());
        String payload = "{\"note\":\"" + padding + "\",\"order_id\":1}";

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "size-max",
                                  "payload": %s
                                }""".formatted(payload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payload_size_bytes").value(1_048_576));
    }

    @Test
    @DisplayName("AC-005-3: eine Nutzlast von 1.048.577 Byte wird mit 413 abgelehnt")
    void a_payload_one_byte_over_the_limit_is_rejected() throws Exception {
        long before = countEvents();

        String padding = "x".repeat(1_048_577 - "{\"note\":\"\",\"order_id\":1}".length());
        String payload = "{\"note\":\"" + padding + "\",\"order_id\":1}";

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "size-over",
                                  "payload": %s
                                }""".formatted(payload)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));

        assertThat(countEvents()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-005-6: eine Einlieferung ohne Ereignistyp wird mit 422 abgelehnt")
    void an_event_without_type_is_rejected() throws Exception {
        long before = countEvents();

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotency_key": "ohne-typ",
                                  "payload": {"order_id": 4713}
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(countEvents()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-005-6: eine Einlieferung ohne Idempotenzschluessel wird mit 422 abgelehnt")
    void an_event_without_idempotency_key_is_rejected() throws Exception {
        long before = countEvents();

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "payload": {"order_id": 4714}
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(countEvents()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-005-1: ein Empfaenger-Admin darf kein Ereignis einliefern (403)")
    void a_receiver_admin_may_not_submit_events() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "falsche-rolle",
                                  "payload": {"order_id": 4715}
                                }"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("AC-005-1: die Nutzlast wird in den Metadaten als Groesse ausgewiesen")
    void the_payload_size_is_reported_in_the_metadata() throws Exception {
        byte[] raw = "{\"order_id\":4716}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "groesse",
                                  "payload_base64": "%s"
                                }""".formatted(Base64.getEncoder().encodeToString(raw))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payload_size_bytes").value(raw.length));
    }

    @Test
    @DisplayName("AC-005-1: ohne Abnehmer traegt das Ereignis den Status 'no_subscriber'")
    void an_event_without_subscriber_is_visible_as_no_subscriber() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "ohne.abnehmer",
                                  "idempotency_key": "kein-abnehmer",
                                  "payload": {"order_id": 4717}
                                }"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("no_subscriber"))
                .andExpect(jsonPath("$.subscribed_endpoint_count").value(0));
    }

    private long countEvents() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app.events", Long.class);
        return count == null ? 0L : count;
    }
}
