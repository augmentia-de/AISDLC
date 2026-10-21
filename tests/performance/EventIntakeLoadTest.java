package com.echobox.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Lasttest — Annahme von 1.000 Einlieferungen (US-005, AC-005-5, NFR-002).
 *
 * <p>NFR-002: „Einliefern antwortet in unter 200 ms (p95); die Zustellung ist
 * asynchron." Der Lasttest misst genau das — 1.000 aufeinanderfolgende
 * Einlieferungen im Betrieb, ohne parallelen Zustellverkehr, damit die Messung
 * die Annahme und nichts anderes zeigt.
 *
 * <p><strong>Gehoert nicht in die normale Testsuite.</strong> Der Test wird
 * ueber ein eigenes Maven-Profil gestartet (Schritt 6 setzt es auf) und
 * laeuft mit eigenem Spring-Kontext. Das Verzeichnis {@code tests/performance/}
 * ist genau dafuer von der normalen Suite getrennt
 * ({@code docs/architecture/folder-structure.md}).
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code submitEvent}; NFR-002.
 */
class EventIntakeLoadTest extends AbstractApplicationIT {

    private static final int SAMPLES = 1_000;
    private static final int WARMUP = 50;
    private static final long P95_BUDGET_MILLIS = 200L;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-005-5: die p95 der Annahmeantworten liegt ueber 1.000 Einlieferungen unter 200 ms")
    void the_p95_over_a_thousand_intakes_is_below_200_millis() throws Exception {
        for (int warmup = 0; warmup < WARMUP; warmup++) {
            intake("load-warmup-" + warmup);
        }

        List<Long> durations = new ArrayList<>(SAMPLES);
        for (int sample = 0; sample < SAMPLES; sample++) {
            durations.add(intake("load-" + sample));
        }

        long p50 = percentile(durations, 50);
        long p95 = percentile(durations, 95);
        long p99 = percentile(durations, 99);
        long max = durations.stream().mapToLong(Long::longValue).max().orElse(0L);

        assertThat(p95)
                .as("p95 ueber %d Einlieferungen (p50=%d ms, p99=%d ms, max=%d ms)", SAMPLES, p50, p99, max)
                .isLessThan(P95_BUDGET_MILLIS);
    }

    @Test
    @DisplayName("AC-005-5: alle 1.000 Einlieferungen werden angenommen")
    void every_intake_is_accepted() throws Exception {
        for (int warmup = 0; warmup < WARMUP; warmup++) {
            intake("accepted-warmup-" + warmup);
        }

        for (int sample = 0; sample < SAMPLES; sample++) {
            mockMvc.perform(post("/api/v1/events")
                            .with(Auth.developer("m-1", "tester"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "event_type": "order.changed",
                                      "idempotency_key": "accepted-%d",
                                      "payload": {"order_id": 4711}
                                    }""".formatted(sample)))
                    .andExpect(status().isAccepted());
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.events WHERE organisation_id = 'm-1'", Long.class);

        assertThat(count).isGreaterThanOrEqualTo((long) SAMPLES);
    }

    private long intake(String idempotencyKey) throws Exception {
        long start = System.nanoTime();
        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "%s",
                                  "payload": {"order_id": 4711}
                                }""".formatted(idempotencyKey)))
                .andExpect(status().isAccepted());
        return (System.nanoTime() - start) / 1_000_000L;
    }

    /**
     * Berechnet den Perzentilwert einer Messreihe (naechstgelegener Rang).
     *
     * @param samples    die Messwerte in Millisekunden
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
