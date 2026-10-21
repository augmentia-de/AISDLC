package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Streuung (Jitter) der Wiederholungen (US-011, FR-022).
 *
 * <p>Getestet wird die Streuung: 100 Endpunkte, die gleichzeitig fehlschlagen,
 * erhalten <em>nicht</em> denselben naechsten Versuchszeitpunkt. Ohne Streuung
 * waeren alle Zeitpunkte identisch — das ist die Vergleichsmessgroesse.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.retry_policy.configuration.jitter}
 * (Vorgabewert +/- 20 %, F-04).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-011-2.
 */
class JitterTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:15:22.481Z");
    private static final Duration BASE = Duration.ofMinutes(1);

    @Test
    @DisplayName("AC-011-2: 100 gleichzeitig fehlschlagende Endpunkte erhalten verschiedene Zeitpunkte")
    void a_hundred_simultaneous_failures_get_different_time_points() {
        BackoffPolicy policy = new BackoffPolicy(BASE, 2.0, Duration.ofMinutes(60), 0.20, new Random(7L));

        Set<Instant> timePoints = new HashSet<>();
        for (int endpoint = 0; endpoint < 100; endpoint++) {
            timePoints.add(policy.nextAttemptAt(NOW, 1));
        }

        assertThat(timePoints)
                .as("die Streuung muss die Zeitpunkte auffaechern")
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("AC-011-2: ohne Streuung sind die Zeitpunkte identisch — der Kontrast belegt die Streuung")
    void without_jitter_the_time_points_are_identical() {
        BackoffPolicy policy = new BackoffPolicy(BASE, 2.0, Duration.ofMinutes(60), 0.0, new Random(7L));

        Set<Instant> timePoints = new HashSet<>();
        for (int endpoint = 0; endpoint < 100; endpoint++) {
            timePoints.add(policy.nextAttemptAt(NOW, 1));
        }

        assertThat(timePoints).hasSize(1);
    }

    @Test
    @DisplayName("AC-011-2: die Streuung bleibt innerhalb der Konfiguration (+/- 20 %)")
    void the_jitter_stays_inside_the_configured_band() {
        BackoffPolicy policy = new BackoffPolicy(BASE, 2.0, Duration.ofMinutes(60), 0.20, new Random(13L));

        Duration lower = BASE.multipliedBy(80).dividedBy(100);
        Duration upper = BASE.multipliedBy(120).dividedBy(100);

        for (int endpoint = 0; endpoint < 100; endpoint++) {
            Duration delay = Duration.between(NOW, policy.nextAttemptAt(NOW, 1));
            assertThat(delay)
                    .as("Endpunkt %d: die Wartezeit liegt innerhalb +/- 20 %%", endpoint)
                    .isBetween(lower, upper);
        }
    }

    @Test
    @DisplayName("AC-011-2: die Streuung ist symmetrisch um den Basisabstand")
    void the_jitter_is_centred_on_the_base_delay() {
        BackoffPolicy policy = new BackoffPolicy(BASE, 2.0, Duration.ofMinutes(60), 0.20, new Random(13L));

        long totalMillis = 0L;
        int samples = 1000;
        for (int i = 0; i < samples; i++) {
            totalMillis += Duration.between(NOW, policy.nextAttemptAt(NOW, 1)).toMillis();
        }
        Duration average = Duration.ofMillis(totalMillis / samples);

        assertThat(average.toMillis())
                .as("im Mittel bleibt die Streuung beim Basisabstand")
                .isBetween(BASE.minusSeconds(3).toMillis(), BASE.plusSeconds(3).toMillis());
    }
}
