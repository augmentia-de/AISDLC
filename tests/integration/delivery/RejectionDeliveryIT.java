package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Integrationsebene — Endgueltige Ablehnung des Empfaengers (US-014, FR-025).
 *
 * <p>Das dokumentierte Ablehnungssignal (HTTP 410 mit
 * {@code X-EchoBox-Rejection: permanent}) beendet die Zustellung sofort. Eine
 * gewöhnliche 4xx endet ebenfalls ohne weitere Versuche, ein 5xx wird
 * dagegen wiederholt.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code x-delivery-protocol.response.outcomes}.
 */
class RejectionDeliveryIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://rejection.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("AC-014-1: das Ablehnungssignal beendet die Zustellung sofort")
    void the_rejection_signal_ends_the_delivery_immediately() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.script(new DeliveryResponse(410, "{\"rejection\":\"permanent\"}",
                Map.of(DeliveryHeaders.REJECTION, "permanent"), DeliveryResponse.TransportFailure.NONE));
        submitEvent("m-1", "order.changed", "rejection-1", "{\"order_id\":1}");

        worker.runOnce();

        assertThat(statusOfRun()).isEqualTo("rejected");
        assertThat(attemptCountOfRun()).isEqualTo(1);
        assertThat(nextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("AC-014-1: das Protokoll zeigt den Status 'abgelehnt' mit dem Antwortcode")
    void the_protocol_shows_the_rejection_with_the_response_code() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.script(new DeliveryResponse(410, "{\"rejection\":\"permanent\"}",
                Map.of(DeliveryHeaders.REJECTION, "permanent"), DeliveryResponse.TransportFailure.NONE));
        submitEvent("m-1", "order.changed", "rejection-2", "{\"order_id\":2}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("rejected"))
                .andExpect(jsonPath("$.items[0].last_response_status").value(410))
                .andExpect(jsonPath("$.items[0].rejection_reason").value("permanent_rejection"));
    }

    @Test
    @DisplayName("AC-014-3: ein 5xx wird nicht als dauerhafte Ablehnung behandelt")
    void a_server_error_is_not_a_permanent_rejection() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 503);
        submitEvent("m-1", "order.changed", "rejection-3", "{\"order_id\":3}");

        worker.runOnce();

        assertThat(statusOfRun()).isEqualTo("failed");
        assertThat(nextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-014-3: ein Netzwerkfehler wird nicht als dauerhafte Ablehnung behandelt")
    void a_network_error_is_not_a_permanent_rejection() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.script(new DeliveryResponse(null, "", Map.of(), DeliveryResponse.TransportFailure.NETWORK_ERROR));
        submitEvent("m-1", "order.changed", "rejection-4", "{\"order_id\":4}");

        worker.runOnce();

        assertThat(statusOfRun()).isEqualTo("failed");
        assertThat(nextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-014-4: eine 4xx ohne das Ablehnungssignal endet ohne weitere Versuche")
    void a_4xx_without_the_signal_ends_without_further_attempts() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 422);
        submitEvent("m-1", "order.changed", "rejection-5", "{\"order_id\":5}");

        worker.runOnce();

        assertThat(statusOfRun()).isEqualTo("rejected");
        assertThat(attemptCountOfRun()).isEqualTo(1);
        assertThat(nextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("AC-014-1: die Ablehnung erscheint nicht in der Totenliste")
    void the_rejection_does_not_appear_in_the_dead_letter_list() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.script(new DeliveryResponse(410, "{\"rejection\":\"permanent\"}",
                Map.of(DeliveryHeaders.REJECTION, "permanent"), DeliveryResponse.TransportFailure.NONE));
        submitEvent("m-1", "order.changed", "rejection-6", "{\"order_id\":6}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
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

    private java.time.Instant nextAttemptAt() {
        return jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", java.time.Instant.class);
    }
}
