package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Wiederholung mit Backoff (US-011, FR-022).
 *
 * <p>Getestet wird die reine Wartezeitberechnung: Verdopplung mit Basisabstand,
 * Deckel und konfigurierbarer Streuung. Die Streuung selbst hat ihren eigenen
 * Testfall ({@code JitterTest}).
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.retry_policy.configuration}
 * (Vorgabewerte F-04: 8 Versuche, Basis 1 Minute, x2, Deckel 60 Minuten, +/- 20 %).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-011-1, AC-011-5.
 */
class BackoffPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:15:22.481Z");

    @Test
    @DisplayName("AC-011-1: der Abstand verdoppelt sich mit jedem Versuch")
    void the_delay_doubles_with_every_attempt() {
        BackoffPolicy policy = withoutJitter();

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMinutes(8));
    }

    @Test
    @DisplayName("AC-011-1: der Abstand ist mindestens doppelt so hoch wie der vorherige")
    void the_delay_is_at_least_double_the_previous_one() {
        BackoffPolicy policy = withoutJitter();

        Duration previous = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            Duration delay = policy.delayForAttempt(attempt);
            if (previous != null) {
                assertThat(delay)
                        .as("Versuch %d: der Abstand ist mindestens doppelt so hoch", attempt)
                        .isGreaterThanOrEqualTo(previous.multipliedBy(2));
            }
            previous = delay;
        }
    }

    @Test
    @DisplayName("AC-011-5: die Wartezeiten der ersten fuenf Versuche steigen monoton")
    void the_first_five_delays_increase_monotonically() {
        BackoffPolicy policy = withoutJitter();

        Duration previous = Duration.ZERO;
        for (int attempt = 1; attempt <= 5; attempt++) {
            Duration delay = policy.delayForAttempt(attempt);
            assertThat(delay).as("Versuch %d", attempt).isGreaterThan(previous);
            previous = delay;
        }
    }

    @Test
    @DisplayName("AC-011-5: kein geplanter Zeitpunkt liegt in der Vergangenheit")
    void no_planned_time_point_lies_in_the_past() {
        BackoffPolicy policy = withoutJitter();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(policy.nextAttemptAt(NOW, attempt))
                    .as("Versuch %d", attempt)
                    .isAfter(NOW);
        }
    }

    @Test
    @DisplayName("AC-011-1: der Deckel von 60 Minuten wird nicht ueberschritten")
    void the_delay_never_exceeds_the_ceiling() {
        BackoffPolicy policy = withoutJitter();

        assertThat(policy.delayForAttempt(7)).isEqualTo(Duration.ofMinutes(60));
        assertThat(policy.delayForAttempt(8)).isEqualTo(Duration.ofMinutes(60));
        assertThat(policy.delayForAttempt(20)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("AC-011-1: alle Werte sind konfigurierbar")
    void every_value_is_configurable() {
        BackoffPolicy policy = new BackoffPolicy(
                Duration.ofSeconds(30), 3.0, Duration.ofMinutes(5), 0.0, new Random(1));

        assertThat(policy.baseDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.multiplier()).isEqualTo(3.0);
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(90));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMinutes(5));
    }

    private static BackoffPolicy withoutJitter() {
        return new BackoffPolicy(Duration.ofMinutes(1), 2.0, Duration.ofMinutes(60), 0.0, new Random(20261021L));
    }
}
