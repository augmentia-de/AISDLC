package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.testsupport.AbstractApplicationIT;

/**
 * Integrationsebene — Annahmeverhalten unter Last und Verzoegerung
 * (US-005, AC-005-4, AC-005-5, NFR-002).
 *
 * <p>Zwei Eigenschaften des Annahmevorgangs:
 * <ul>
 *   <li><strong>AC-005-4</strong> — der Annahme-Request wird vom Empfaenger nie
 *       blockiert: ein Empfaenger, der 30 Sekunden braucht, haelt die Antwort
 *       nicht auf;</li>
 *   <li><strong>AC-005-5</strong> — die Antwortzeit der Annahme liegt unter
 *       200 ms (p95).</li>
 * </ul>
 *
 * <p>Der <em>Lasttest</em> mit 1.000 Einlieferungen steht in
 * {@code tests/performance/EventIntakeLoadTest.java} — er gehoert nicht in die
 * normale Testsuite (folder-structure.md, Abschnitt „Tests"). Dieser Test
 * haelt die Regression im normalen Lauf fest.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code submitEvent};
 * NFR-002 (p95 &lt; 200 ms, asynchrone Zustellung).
 */
class EventIntakePerformanceIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://langsam.example.invalid/hooks/echobox";

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        enableDeliveryWorker(registry);
        registry.add("echobox.delivery.http.connect-timeout", () -> "2s");
        registry.add("echobox.delivery.http.read-timeout", () -> "2s");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("AC-005-4: ein langsamer Empfaenger blockiert den Annahme-Request nicht")
    void a_slow_receiver_does_not_block_the_intake_request() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200).respondAfter(Duration.ofSeconds(30));

        long start = System.nanoTime();
        mockMvc.perform(post("/api/v1/events")
                        .with(com.echobox.testsupport.Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "langsam-1",
                                  "payload": {"order_id": 1}
                                }"""))
                .andExpect(status().isAccepted());
        long durationMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(durationMillis)
                .as("die Annahmeantwort ist bereits gesendet, obwohl der Empfaenger 30 Sekunden braucht")
                .isLessThan(200L);
    }

    @Test
    @DisplayName("AC-005-5: die p95 der Annahmeantworten liegt unter 200 ms")
    void the_p95_of_the_intake_response_is_below_200_millis() throws Exception {
        int samples = 200;

        for (int warmup = 0; warmup < 20; warmup++) {
            intake("warmup-" + warmup);
        }

        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < samples; sample++) {
            durations.add(intake("last-" + sample));
        }

        long p95 = percentile(durations, 95);

        assertThat(p95)
                .as("p95 ueber %d Einlieferungen", samples)
                .isLessThan(200L);
    }

    private long intake(String idempotencyKey) throws Exception {
        long start = System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .with(com.echobox.testsupport.Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "%s",
                                  "payload": {"order_id": 4711}
                                }""".formatted(idempotencyKey)))
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        return (System.nanoTime() - start) / 1_000_000L;
    }

    /**
     * Berechnet den Perzentilwert einer Messreihe (naechstgelegener Rang).
     *
     * @param samples   die Messwerte in Millisekunden
     * @param percentile der gewuenschte Perzentilwert
     * @return der Perzentilwert
     */
    static long percentile(List<Long> samples, int percentile) {
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(rank, sorted.size() - 1)));
    }
}
