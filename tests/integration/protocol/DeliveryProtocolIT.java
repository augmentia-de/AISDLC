package com.echobox.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Zustellprotokoll (US-015, FR-030).
 *
 * <p>Das Protokoll ist die Buchhaltung des Betriebs: Zustand, Versuche, letzter
 * Versuch, Antwortcode und naechster Versuchszeitpunkt — je Eintrag.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code listDeliveries} und
 * {@code getDeliveryRun}.
 */
class DeliveryProtocolIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://protocol.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("AC-015-1: der Eintrag zeigt Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch")
    void the_entry_shows_all_required_fields() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "prot-1", "{\"order_id\":1}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("failed"))
                .andExpect(jsonPath("$.items[0].attempt_count").value(1))
                .andExpect(jsonPath("$.items[0].last_attempt_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].last_response_status").value(500))
                .andExpect(jsonPath("$.items[0].next_attempt_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].delivery_run_id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].event_id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].endpoint_id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].organisation_id").value("m-1"));
    }

    @Test
    @DisplayName("AC-015-2: bei einem endgueltigen Zustand ist der naechste Versuchszeitpunkt leer")
    void a_terminal_state_has_no_next_attempt() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "prot-2", "{\"order_id\":2}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("sent"))
                .andExpect(jsonPath("$.items[0].next_attempt_at").doesNotExist());
    }

    @Test
    @DisplayName("AC-015-3: der Einzelabruf eines fremden Ereignisses ergibt 404")
    void reading_a_foreign_event_yields_not_found() throws Exception {
        registerEndpoint("m-2", TARGET_URL, "order.changed");
        String foreignEventId = submitEvent("m-2", "order.changed", "prot-3", "{\"order_id\":3}");

        mockMvc.perform(get("/api/v1/events/" + foreignEventId)
                        .with(Auth.developer("m-1", "tester")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.event_type").doesNotExist());
    }

    @Test
    @DisplayName("AC-015-4: die Filter sind kombinierbar und mandantengefiltert")
    void the_filters_are_combinable_and_tenant_scoped() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        registerEndpoint("m-2", "https://other.example.invalid/hook", "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        deliveryPort.respondWith("https://other.example.invalid/hook", 500);

        submitEvent("m-1", "order.changed", "prot-4", "{\"order_id\":4}");
        submitEvent("m-2", "order.changed", "prot-5", "{\"order_id\":5}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/deliveries")
                        .param("status", "failed")
                        .param("event_type", "order.changed")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("m-1"))))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("AC-015-4: ein ungueltiger Cursor wird mit 422 abgelehnt")
    void an_invalid_cursor_is_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries")
                        .param("cursor", "kein-gueltiger-cursor")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    @DisplayName("AC-007-5: die Suche laeuft ueber Metadaten — die Nutzlast wird nie ausgegeben")
    void the_search_runs_over_metadata_only() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        String eventId = submitEvent("m-1", "order.changed", "prot-6", "{\"order_id\":6}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/deliveries")
                        .param("event_id", eventId)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload_ciphertext").doesNotExist());
    }

    @Test
    @DisplayName("AC-015-1: der Einzelabruf eines Laufs zeigt alle Versuche")
    void the_single_run_shows_all_attempts() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "prot-7", "{\"order_id\":7}");

        worker.runOnce();

        String runId = jdbcTemplate.queryForObject(
                "SELECT delivery_run_id FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);

        mockMvc.perform(get("/api/v1/deliveries/" + runId)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].attempt_number").value(1))
                .andExpect(jsonPath("$.attempts[0].outcome").value("retryable_failure"))
                .andExpect(jsonPath("$.attempts[0].response_status").value(500));
    }

    @Test
    @DisplayName("AC-015-4: ein Ereignis ohne Abnehmer erscheint mit dem Zustand 'no_subscriber'")
    void an_event_without_subscriber_is_visible() throws Exception {
        submitEvent("m-1", "order.changed", "prot-8", "{\"order_id\":8}");

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("no_subscriber"))
                .andExpect(jsonPath("$.items[0].endpoint_id").doesNotExist());
    }
}
