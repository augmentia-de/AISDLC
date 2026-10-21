package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Aufgabe nach erschoepften Versuchen (US-012, FR-023).
 *
 * <p>Nach dem letzten erlaubten Versuch endet der Zustelllauf als
 * <em>aufgegeben</em>: er erscheint in der Totenliste und es erfolgt kein
 * weiterer Versuch.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code x-delivery-protocol.retry_policy.configuration.max_attempts} (F-04) und
 * {@code listDeadDeliveries}.
 */
class DeadLetterIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://deadletter.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
        registry.add("echobox.delivery.max-attempts", () -> "3");
    }

    @Test
    @DisplayName("AC-012-1: nach der Versuchsgrenze wird die Zustellung aufgegeben")
    void the_delivery_is_abandoned_after_the_attempt_limit() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "dead-1", "{\"order_id\":1}");

        runUntilExhausted();

        assertThat(statusOfRun()).isEqualTo("abandoned");
        assertThat(attemptCountOfRun()).isEqualTo(3);
        assertThat(abandonedAtOfRun()).isNotNull();
    }

    @Test
    @DisplayName("AC-012-1: die aufgegebene Zustellung erscheint in der Totenliste")
    void the_abandoned_delivery_appears_in_the_dead_letter_list() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "dead-2", "{\"order_id\":2}");

        runUntilExhausted();

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("abandoned"))
                .andExpect(jsonPath("$.items[0].attempt_count").value(3))
                .andExpect(jsonPath("$.items[0].abandoned_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].last_response_status").value(500));
    }

    @Test
    @DisplayName("AC-012-2: das Protokoll nennt Versuche, letzten Versuch und Antwortcode")
    void the_protocol_names_attempts_last_attempt_and_response_code() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 503);
        submitEvent("m-1", "order.changed", "dead-3", "{\"order_id\":3}");

        runUntilExhausted();

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].attempt_count").value(3))
                .andExpect(jsonPath("$.items[0].last_attempt_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].last_response_status").value(503))
                .andExpect(jsonPath("$.items[0].next_attempt_at").doesNotExist());
    }

    @Test
    @DisplayName("AC-012-3: es findet kein automatischer Neuversuch statt")
    void there_is_no_automatic_retry_after_abandonment() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "dead-4", "{\"order_id\":4}");

        runUntilExhausted();
        int attemptsAfterAbandonment = deliveryPort.requestCount();

        clock.advance(Duration.ofHours(1));
        worker.runOnce();
        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(attemptsAfterAbandonment);
        assertThat(attemptCountOfRun()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-012-4: die konfigurierte Versuchsgrenze wird nicht unterlaufen und nicht ueberschritten")
    void the_configured_limit_is_neither_undershot_nor_overshot() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "dead-5", "{\"order_id\":5}");

        runUntilExhausted();

        assertThat(attemptCountOfRun())
                .as("die konfigurierte Grenze wird nicht unterlaufen")
                .isEqualTo(3);
        assertThat(maxAttemptsOfRun()).isEqualTo(3);
    }

    private void runUntilExhausted() {
        for (int attempt = 1; attempt <= 10; attempt++) {
            worker.runOnce();
            clock.advance(Duration.ofMinutes(1));
            if ("abandoned".equals(statusOfRun())) {
                return;
            }
        }
    }

    private String statusOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }

    private int attemptCountOfRun() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Integer.class);
        return count == null ? 0 : count;
    }

    private int maxAttemptsOfRun() {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT max_attempts FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Integer.class);
        return max == null ? 0 : max;
    }

    private Instant abandonedAtOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT abandoned_at FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Instant.class);
    }
}
