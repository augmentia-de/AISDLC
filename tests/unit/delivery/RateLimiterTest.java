package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Rate-Limit je Endpunkt (US-018, FR-033).
 *
 * <p>Getestet wird die Begrenzung: der Zaehler gilt **je Endpunkt**, die
 * konfigurierte Strategie greift, und kein Ereignis geht verloren — ein
 * ueberschrittener Versuch wird verschoben, nicht verworfen.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.rate_limit}
 * (Vorgabewert 60 Versuche je Endpunkt und Minute, Strategie `delay`, F-01).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-018-1, AC-018-2, AC-018-3.
 */
class RateLimiterTest {

    private static final UUID ENDPOINT = UUID.fromString("8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b");
    private static final UUID OTHER_ENDPOINT = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final Instant NOW = Instant.parse("2026-10-21T09:15:22.481Z");

    @Test
    @DisplayName("AC-018-1: Versuche bis zum Grenzwert werden durchgelassen")
    void attempts_below_the_limit_are_permitted() {
        EndpointRateLimiter limiter = new EndpointRateLimiter(3, Duration.ofMinutes(1), RateLimitStrategy.DELAY);

        assertThat(limiter.acquire(ENDPOINT, NOW).permitted()).isTrue();
        assertThat(limiter.acquire(ENDPOINT, NOW).permitted()).isTrue();
        assertThat(limiter.acquire(ENDPOINT, NOW).permitted()).isTrue();
    }

    @Test
    @DisplayName("AC-018-1: der ueberschrittene Versuch wird verschoben, nicht verworfen")
    void the_exceeding_attempt_is_delayed_and_not_dropped() {
        EndpointRateLimiter limiter = new EndpointRateLimiter(2, Duration.ofMinutes(1), RateLimitStrategy.DELAY);
        limiter.acquire(ENDPOINT, NOW);
        limiter.acquire(ENDPOINT, NOW);

        RateLimitDecision decision = limiter.acquire(ENDPOINT, NOW);

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.waitTime())
                .as("der Versuch wird verschoben — er geht nicht verloren")
                .isPositive();
        assertThat(limiter.delayedAttempts(ENDPOINT)).isEqualTo(1L);
        assertThat(limiter.observedAttempts(ENDPOINT))
                .as("die Zaehlung laeuft unabhaengig vom Grenzwert (AC-018-4)")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("AC-018-2: die Verschiebung nennt einen Zeitpunkt nach dem jetzt")
    void the_delay_names_a_point_in_the_future() {
        EndpointRateLimiter limiter = new EndpointRateLimiter(1, Duration.ofMinutes(1), RateLimitStrategy.DELAY);
        limiter.acquire(ENDPOINT, NOW);

        RateLimitDecision decision = limiter.acquire(ENDPOINT, NOW);

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.retryAt()).isAfter(NOW);
    }

    @Test
    @DisplayName("AC-018-3: die Begrenzung wirkt je Endpunkt")
    void the_limit_applies_per_endpoint() {
        EndpointRateLimiter limiter = new EndpointRateLimiter(1, Duration.ofMinutes(1), RateLimitStrategy.DELAY);
        limiter.acquire(ENDPOINT, NOW);
        limiter.acquire(ENDPOINT, NOW);

        RateLimitDecision decision = limiter.acquire(OTHER_ENDPOINT, NOW);

        assertThat(decision.permitted())
                .as("der andere Endpunkt wird nicht verzoegert")
                .isTrue();
    }

    @Test
    @DisplayName("AC-018-1: nach Ablauf des Fensters ist der Zaehler wieder frei")
    void the_counter_is_free_again_after_the_window() {
        EndpointRateLimiter limiter = new EndpointRateLimiter(1, Duration.ofMinutes(1), RateLimitStrategy.DELAY);
        limiter.acquire(ENDPOINT, NOW);
        assertThat(limiter.acquire(ENDPOINT, NOW).permitted()).isFalse();

        Instant nextWindow = NOW.plus(Duration.ofMinutes(1));

        assertThat(limiter.acquire(ENDPOINT, nextWindow).permitted()).isTrue();
    }

    @Test
    @DisplayName("AC-018-1: die Strategie 'block' stellt den Versuch zurueck")
    void the_block_strategy_defers_the_attempt() {
        EndpointRateLimiter limiter = new EndpointRateLimiter(1, Duration.ofMinutes(1), RateLimitStrategy.BLOCK);
        limiter.acquire(ENDPOINT, NOW);

        RateLimitDecision decision = limiter.acquire(ENDPOINT, NOW);

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.waitTime()).isGreaterThanOrEqualTo(Duration.ofMinutes(1));
    }
}
