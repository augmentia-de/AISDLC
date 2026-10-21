package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;

/**
 * Integrationsebene — Zustellkennungen und at-least-once (US-010, FR-021).
 *
 * <p>Der Empfaenger muss die Dublette erkennen koennen: gleiche Ereignis-ID,
 * gleiche Zustelllauf-ID, aber verschiedene Versuchs-IDs. Und ein Ereignis an
 * zwei Endpunkte erzeugt zwei Laeufe mit derselben Ereignis-ID.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code x-delivery-protocol.request.headers} und
 * {@code x-delivery-protocol.duplicate_handling}.
 */
class AtLeastOnceDeliveryIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://atleastonce.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("AC-010-1: jeder Versuch traegt Ereignis-ID, Zustelllauf-ID und Versuchs-ID")
    void every_attempt_carries_the_three_identifiers() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "aao-1", "{\"order_id\":1}");

        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(1);
        DeliveryRequest request = deliveryPort.request(1);
        assertThat(request.headers()).containsKeys(
                DeliveryHeaders.EVENT_ID,
                DeliveryHeaders.DELIVERY_RUN_ID,
                DeliveryHeaders.ATTEMPT_ID,
                DeliveryHeaders.ATTEMPT_NUMBER);
    }

    @Test
    @DisplayName("AC-010-2: die Timeout-Dublette traegt gleiche Lauf-ID, aber verschiedene Versuchs-IDs")
    void the_timeout_duplicate_shares_the_run_id_but_not_the_attempt_id() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.script(
                new DeliveryResponse(null, "", Map.of(), DeliveryResponse.TransportFailure.TIMEOUT),
                new DeliveryResponse(200, "", Map.of(), DeliveryResponse.TransportFailure.NONE));
        submitEvent("m-1", "order.changed", "aao-2", "{\"order_id\":2}");

        worker.runOnce();
        clock.advance(Duration.ofMinutes(1));
        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(2);
        DeliveryRequest first = deliveryPort.request(1);
        DeliveryRequest second = deliveryPort.request(2);

        assertThat(second.headers().get(DeliveryHeaders.DELIVERY_RUN_ID))
                .isEqualTo(first.headers().get(DeliveryHeaders.DELIVERY_RUN_ID));
        assertThat(second.headers().get(DeliveryHeaders.EVENT_ID))
                .isEqualTo(first.headers().get(DeliveryHeaders.EVENT_ID));
        assertThat(second.headers().get(DeliveryHeaders.ATTEMPT_ID))
                .isNotEqualTo(first.headers().get(DeliveryHeaders.ATTEMPT_ID));
        assertThat(second.headers().get(DeliveryHeaders.ATTEMPT_NUMBER)).isEqualTo("2");
    }

    @Test
    @DisplayName("AC-010-3: nach einer 2xx-Antwort erfolgt kein weiterer Versuch")
    void no_further_attempt_follows_a_2xx() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "aao-3", "{\"order_id\":3}");

        worker.runOnce();
        clock.advance(Duration.ofMinutes(10));
        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(1);
        assertThat(statusOfRun()).isEqualTo("sent");
    }

    @Test
    @DisplayName("AC-010-4: ein Ereignis an zwei Endpunkte erzeugt zwei Laeufe mit derselben Ereignis-ID")
    void two_endpoints_produce_two_runs_with_the_same_event_id() throws Exception {
        String firstEndpoint = registerEndpoint("m-1", "https://first.example.invalid/hook", "order.changed");
        String secondEndpoint = registerEndpoint("m-1", "https://second.example.invalid/hook", "order.changed");
        deliveryPort.respondWith("https://first.example.invalid/hook", 200);
        deliveryPort.respondWith("https://second.example.invalid/hook", 200);

        String eventId = submitEvent("m-1", "order.changed", "aao-4", "{\"order_id\":4}");

        worker.runOnce();

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT delivery_run_id, event_id FROM app.delivery_runs WHERE event_id = ?", eventId);

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).get("event_id")).isEqualTo(runs.get(1).get("event_id"));
        assertThat(runs.get(0).get("delivery_run_id")).isNotEqualTo(runs.get(1).get("delivery_run_id"));
        // Der Treiber liefert eine UUID-Spalte als UUID-Objekt — der Vergleich
        // mit der Kennung aus der Antwort erfolgt ueber die Zeichenkette.
        assertThat(runs).allSatisfy(run -> assertThat(String.valueOf(run.get("event_id"))).isEqualTo(eventId));
        assertThat(firstEndpoint).isNotEqualTo(secondEndpoint);
    }

    private String statusOfRun() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.delivery_runs ORDER BY created_at DESC LIMIT 1", String.class);
    }
}
