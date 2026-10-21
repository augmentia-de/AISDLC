package com.echobox.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;

/**
 * Integrationsebene — Horizontalbetrieb (US-023, AC-023-3, AC-023-4, NFR-006).
 *
 * <p>Zwei Worker-Instanzen laufen gegen <em>dieselbe</em> Datenbank. Genau eine
 * darf einen Versuch ausfuehren — keine Doppelzustellung durch konkurrierende
 * Worker. Und: stuerzt eine Instanz waehrend eines Versuchs ab, gibt die
 * Lease den Lauf frei, ohne dass die Versuchsanzahl sprunghaft steigt.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-requirement-coverage}
 * (NFR-006); `.agent/knowledge/schema.sql` -&gt; {@code delivery_runs.lease_owner}
 * und {@code delivery_runs.lease_expires_at}; ADR-006.
 */
class HorizontalScalingIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://scaling.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
        registry.add("echobox.delivery.lease-duration", () -> "PT1M");
    }

    @Test
    @DisplayName("AC-023-3: von zwei Instanzen fuehrt genau eine den Versuch aus")
    void exactly_one_instance_performs_the_attempt() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "scaling-1", "{\"order_id\":1}");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                await(start);
                worker.runOnce("instanz-a");
                return null;
            });
            pool.submit(() -> {
                await(start);
                worker.runOnce("instanz-b");
                return null;
            });
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(deliveryPort.requestCount())
                .as("genau ein Versuch — keine Doppelzustellung durch konkurrierende Worker")
                .isEqualTo(1);
        assertThat(countAttempts()).isEqualTo(1L);
    }

    @Test
    @DisplayName("AC-023-4: ein abgestuerzter Worker gibt den Lauf nach dem Lease-Ablauf frei")
    void a_crashed_worker_releases_the_run_after_the_lease_expires() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "scaling-2", "{\"order_id\":2}");

        jdbcTemplate.update("""
                UPDATE app.delivery_runs
                SET lease_owner = 'abgestuerzte-instanz',
                    lease_expires_at = now() - interval '1 minute'
                WHERE delivery_run_id = (SELECT delivery_run_id FROM app.delivery_runs LIMIT 1)""");

        long attemptsBefore = countAttempts();

        worker.runOnce("gesunde-instanz");

        assertThat(countAttempts())
                .as("der Lauf wurde uebernommen und genau einmal versucht")
                .isEqualTo(attemptsBefore + 1);
        assertThat(deliveryPort.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-023-4: die Versuchsanzahl steigt nicht sprunghaft")
    void the_attempt_count_does_not_jump() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "scaling-3", "{\"order_id\":3}");

        worker.runOnce("instanz-a");
        int afterFirstAttempt = attemptCountOfRun();

        jdbcTemplate.update("""
                UPDATE app.delivery_runs
                SET lease_owner = 'abgestuerzte-instanz',
                    lease_expires_at = now() - interval '1 minute'
                WHERE delivery_run_id = (SELECT delivery_run_id FROM app.delivery_runs LIMIT 1)""");

        clock.advance(Duration.ofMinutes(1));
        worker.runOnce("instanz-b");

        assertThat(attemptCountOfRun())
                .as("die Lease-Rettung zaehlt keinen Versuch")
                .isEqualTo(afterFirstAttempt + 1);
    }

    @Test
    @DisplayName("AC-023-3: der beanspruchte Lauf traegt den Namen der beanspruchenden Instanz")
    void the_claimed_run_names_the_claiming_instance() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "scaling-4", "{\"order_id\":4}");

        worker.runOnce("benannte-instanz");

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT lease_owner FROM app.delivery_runs WHERE delivery_run_id = "
                        + "(SELECT delivery_run_id FROM app.delivery_runs LIMIT 1)");

        assertThat(runs).hasSize(1);
        assertThat(String.valueOf(runs.get(0).get("lease_owner"))).contains("benannte-instanz");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long countAttempts() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app.delivery_attempts", Long.class);
        return count == null ? 0L : count;
    }

    private int attemptCountOfRun() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", Integer.class);
        return count == null ? 0 : count;
    }
}
