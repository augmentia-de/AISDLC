package com.echobox.delivery;

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

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Rate-Limit je Endpunkt (US-018, FR-033).
 *
 * <p>Der Schutz gilt je Endpunkt und Zeitfenster. Bei Ueberschreitung wird der
 * Versuch **verschoben**, nicht verworfen: kein Ereignis geht verloren, und die
 * Verschiebung ist im Protokoll sichtbar.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.rate_limit}
 * (Vorgabewert 60 Versuche je Endpunkt und Minute, Strategie `delay`, F-01).
 */
class RateLimitedDeliveryIT extends AbstractApplicationIT {

    private static final String FIRST_URL = "https://limit-first.example.invalid/hooks/echobox";
    private static final String SECOND_URL = "https://limit-second.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.rate-limit.limit", () -> "2");
        registry.add("echobox.delivery.rate-limit.window-seconds", () -> "60");
        registry.add("echobox.delivery.rate-limit.strategy", () -> "delay");
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("AC-018-1: der Versuch ueber dem Grenzwert wird verschoben, nicht verworfen")
    void the_exceeding_attempt_is_delayed_and_not_dropped() throws Exception {
        registerEndpoint("m-1", FIRST_URL, "order.changed");
        deliveryPort.respondWith(FIRST_URL, 500);
        submitEvent("m-1", "order.changed", "limit-1", "{\"order_id\":1}");
        submitEvent("m-1", "order.changed", "limit-2", "{\"order_id\":2}");
        submitEvent("m-1", "order.changed", "limit-3", "{\"order_id\":3}");

        worker.runOnce();

        assertThat(deliveryPort.requestCount())
                .as("nur die Versuche bis zum Grenzwert gehen raus")
                .isEqualTo(2);
        assertThat(countDelayedRuns()).isEqualTo(1);
        assertThat(countRuns()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-018-2: die Verschiebung ist im Protokoll erkennbar")
    void the_delay_is_visible_in_the_protocol() throws Exception {
        registerEndpoint("m-1", FIRST_URL, "order.changed");
        deliveryPort.respondWith(FIRST_URL, 500);
        submitEvent("m-1", "order.changed", "limit-4", "{\"order_id\":4}");
        submitEvent("m-1", "order.changed", "limit-5", "{\"order_id\":5}");
        submitEvent("m-1", "order.changed", "limit-6", "{\"order_id\":6}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.status == 'delayed')].next_attempt_at").isNotEmpty());
    }

    @Test
    @DisplayName("AC-018-3: die Begrenzung wirkt je Endpunkt — der andere Endpunkt wird nicht verzoegert")
    void the_limit_applies_per_endpoint() throws Exception {
        registerEndpoint("m-1", FIRST_URL, "order.changed");
        registerEndpoint("m-1", SECOND_URL, "order.changed");
        deliveryPort.respondWith(FIRST_URL, 500);
        deliveryPort.respondWith(SECOND_URL, 500);
        submitEvent("m-1", "order.changed", "limit-7", "{\"order_id\":7}");
        submitEvent("m-1", "order.changed", "limit-8", "{\"order_id\":8}");
        submitEvent("m-1", "order.changed", "limit-9", "{\"order_id\":9}");
        submitEvent("m-1", "order.changed", "limit-10", "{\"order_id\":10}");

        worker.runOnce();

        long secondEndpointAttempts = deliveryPort.requests().stream()
                .filter(request -> SECOND_URL.equals(request.targetUrl()))
                .count();

        assertThat(secondEndpointAttempts)
                .as("der andere Endpunkt wird nicht verzoegert")
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("AC-018-4: die Zaehlung laeuft unabhaengig vom Grenzwert und ist abrufbar")
    void the_counter_runs_independently_of_the_limit() throws Exception {
        String endpointId = registerEndpoint("m-1", FIRST_URL, "order.changed");
        deliveryPort.respondWith(FIRST_URL, 500);
        submitEvent("m-1", "order.changed", "limit-11", "{\"order_id\":11}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId + "/metrics")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate_limit").isNotEmpty())
                .andExpect(jsonPath("$.rate_limit.limit").isNumber())
                .andExpect(jsonPath("$.rate_limit.observed_attempts").isNumber())
                .andExpect(jsonPath("$.rate_limit.window_seconds").value(60))
                .andExpect(jsonPath("$.rate_limit.strategy").value("delay"));
    }

    @Test
    @DisplayName("AC-018-1: nach Ablauf des Fensters wird der verschobene Versuch nachgeholt")
    void the_delayed_attempt_is_made_after_the_window() throws Exception {
        registerEndpoint("m-1", FIRST_URL, "order.changed");
        deliveryPort.respondWith(FIRST_URL, 500);
        submitEvent("m-1", "order.changed", "limit-12", "{\"order_id\":12}");
        submitEvent("m-1", "order.changed", "limit-13", "{\"order_id\":13}");
        submitEvent("m-1", "order.changed", "limit-14", "{\"order_id\":14}");

        worker.runOnce();
        int before = deliveryPort.requestCount();

        clock.advance(Duration.ofMinutes(2));
        worker.runOnce();

        assertThat(deliveryPort.requestCount())
                .as("kein Ereignis geht verloren")
                .isGreaterThan(before);
    }

    private long countRuns() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app.delivery_runs", Long.class);
        return count == null ? 0L : count;
    }

    private long countDelayedRuns() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.delivery_runs WHERE status = 'delayed'", Long.class);
        return count == null ? 0L : count;
    }
}
