package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;

/**
 * Integrationsebene — Versuchsgrenze 1 (US-012, AC-012-4, FR-023).
 *
 * <p>Eigener Spring-Kontext mit {@code echobox.delivery.max-attempts=1}: die
 * Zustellung endet nach <em>genau einem</em> Versuch als aufgegeben — die
 * konfigurierte Grenze wird weder unterlaufen noch ueberschritten.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code x-delivery-protocol.retry_policy.configuration.max_attempts}
 * („beim Anlegen des Laufs festgeschrieben").
 */
class AttemptLimitIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://limit-one.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.max-attempts", () -> "1");
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("AC-012-4: mit der Grenze 1 endet die Zustellung nach genau einem Versuch")
    void the_delivery_ends_after_exactly_one_attempt() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "limit-1", "{\"order_id\":1}");

        worker.runOnce();
        clock.advance(Duration.ofMinutes(1));
        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(1);
        assertThat(attemptCountOfRun()).isEqualTo(1);
        assertThat(statusOfRun()).isEqualTo("abandoned");
    }

    @Test
    @DisplayName("AC-012-4: die Versuchsgrenze wird beim Anlegen des Laufs festgeschrieben")
    void the_limit_is_frozen_when_the_run_is_created() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "limit-2", "{\"order_id\":2}");

        Integer maxAttempts = jdbcTemplate.queryForObject(
                "SELECT max_attempts FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Integer.class);

        assertThat(maxAttempts).isEqualTo(1);
    }

    private int attemptCountOfRun() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Integer.class);
        return count == null ? 0 : count;
    }

    private String statusOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }
}
