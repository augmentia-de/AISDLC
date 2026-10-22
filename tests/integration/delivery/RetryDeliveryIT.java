package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Integrationsebene — Wiederholung mit Backoff (US-011, FR-022).
 *
 * <p>Die Wiederholung ist Sache des Hintergrundprozesses, nicht des
 * Annahme-Requests. Der Test steuert den Worker deshalb selbst
 * ({@code worker.runOnce()}) und schaltet die Streuung ab, damit die
 * Verdopplung exakt messbar ist — die Streuung selbst prueft
 * {@code tests/unit/delivery/JitterTest.java}.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code x-delivery-protocol.retry_policy.configuration} (F-04).
 */
class RetryDeliveryIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://retry.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "60s");
        registry.add("echobox.delivery.backoff.multiplier", () -> "2.0");
        registry.add("echobox.delivery.backoff.max-delay", () -> "60m");
    }

    @Test
    @DisplayName("AC-011-3: die Wiederholung laeuft im Hintergrundprozess, nicht im Annahme-Request")
    void the_retry_runs_in_the_background_process() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);

        submitEvent("m-1", "order.changed", "retry-async", "{\"order_id\":1}");

        assertThat(deliveryPort.requestCount())
                .as("der Annahme-Request blockiert die Zustellung nicht")
                .isZero();

        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-011-1: nach einem 5xx wird der naechste Versuch mit dem doppelten Abstand geplant")
    void the_next_attempt_is_planned_with_double_the_distance() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "retry-double", "{\"order_id\":2}");

        worker.runOnce();

        // AC-011-1 spricht vom **geplanten** naechsten Versuch: gemessen wird
        // deshalb der Zeitpunkt, zu dem der Dienst den Versuch terminiert
        // (next_attempt_at) — die tatsaechlichen Versuchszeitpunkte folgen der
        // Testuhr und koennen nicht doppelt so weit auseinanderliegen.
        Instant firstAttempt = lastAttemptAt();
        Instant firstPlanned = nextAttemptAt();
        Instant secondAttempt = firstAttempt.plus(Duration.ofMinutes(1));
        clock.setTo(secondAttempt);
        worker.runOnce();

        Instant secondPlanned = nextAttemptAt();

        Duration firstDistance = Duration.between(firstAttempt, firstPlanned);
        Duration secondDistance = Duration.between(secondAttempt, secondPlanned);

        assertThat(secondDistance)
                .as("der Abstand zum vorherigen Versuch ist mindestens doppelt so hoch")
                .isGreaterThanOrEqualTo(firstDistance.multipliedBy(2));
    }

    @Test
    @DisplayName("AC-011-5: die geplanten Wartezeiten steigen monoton und liegen nicht in der Vergangenheit")
    void the_planned_delays_increase_monotonically() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "retry-monoton", "{\"order_id\":3}");

        List<Instant> planned = new java.util.ArrayList<>();
        Instant now = clock.now();
        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.runOnce();
            Instant next = nextAttemptAt();
            assertThat(next).as("Versuch %d: kein Zeitpunkt in der Vergangenheit", attempt).isAfter(now);
            planned.add(next);
            clock.setTo(next);
        }

        for (int i = 1; i < planned.size(); i++) {
            assertThat(planned.get(i))
                    .as("die Wartezeiten steigen monoton")
                    .isAfter(planned.get(i - 1));
        }
    }

    @Test
    @DisplayName("AC-011-4: nach einer 2xx-Antwort wird kein weiterer Versuch geplant")
    void no_further_attempt_is_planned_after_a_success() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "retry-success", "{\"order_id\":4}");

        worker.runOnce();

        assertThat(statusOfRun()).isEqualTo("sent");
        assertThat(nextAttemptAt()).isNull();
        assertThat(deliveryPort.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-011-4: ein zweiter Durchlauf des Workers erzeugt keinen weiteren Versuch")
    void a_second_worker_run_makes_no_further_attempt() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "retry-once", "{\"order_id\":5}");

        worker.runOnce();
        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-011-1: ein Netzwerkfehler wird wie ein 5xx behandelt")
    void a_network_error_is_retried_like_a_server_error() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.script(new DeliveryResponse(null, "", java.util.Map.of(), DeliveryResponse.TransportFailure.NETWORK_ERROR));
        submitEvent("m-1", "order.changed", "retry-network", "{\"order_id\":6}");

        worker.runOnce();

        assertThat(statusOfRun()).isEqualTo("failed");
        assertThat(nextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-011-3: der Annahme-Request antwortet, obwohl der Empfaenger langsam ist")
    void the_intake_request_answers_although_the_receiver_is_slow() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500).respondAfter(Duration.ofSeconds(30));
        submitEvent("m-1", "order.changed", "retry-slow", "{\"order_id\":7}");

        assertThat(deliveryPort.requestCount())
                .as("der Annahme-Request wurde nicht blockiert")
                .isZero();
    }

    private Instant lastAttemptAt() {
        return jdbcTemplate.queryForObject(
                "SELECT last_attempt_at FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Instant.class);
    }

    private Instant nextAttemptAt() {
        return jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Instant.class);
    }

    private String statusOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }
}
