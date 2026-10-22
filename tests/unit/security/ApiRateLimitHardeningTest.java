package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.common.ApiRateLimiter;
import com.echobox.common.ClockPort;

/**
 * Unit-Ebene — das <strong>Anfragelimit der API</strong> (S-24), neu.
 *
 * <p>Der eingefrorene Vertrag sagt fuer jede Operation HTTP 429
 * {@code rate_limit_exceeded} mit dem Header {@code Retry-After} zu. Der
 * Zaehler, der diese Zusage einloest, ist neu — diese Tests nageln seine
 * Eigenschaften fest, damit eine Aenderung an ihm die Zusage nicht still
 * verletzt:
 * <ul>
 *   <li>der Zaehler gilt <strong>je Mandant</strong> — ein zweiter Mandant
 *       bleibt unberuehrt;</li>
 *   <li>die Wartezeit ist eine <strong>Wartezeit in Sekunden</strong> (mindestens 1) —
 *       so beschreibt es der Vertrag ({@code RetryAfter}: {@code type: integer,
 *       minimum: 1}) — und sie ist <strong>nie kuerzer</strong> als die Wartezeit bis
 *       zum Ende des Fensters;</li>
 *   <li>ein <strong>neues Fenster</strong> beginnt von vorn;</li>
 *   <li>die Grenze kommt aus der <strong>Konfiguration</strong> (ADR-009) — im Code ist
 *       kein Zahlwert verdrahtet.</li>
 * </ul>
 */
class ApiRateLimitHardeningTest {

    /** Die Fensterlaenge der Tests — eine Minute. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** Der Startzeitpunkt der Testuhr. */
    private static final Instant START = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    @DisplayName("S-24: das Limit gilt je Mandant — ein zweiter Mandant bleibt unberuehrt")
    void the_limit_is_per_tenant() {
        ApiRateLimiter limiter = new ApiRateLimiter(2, WINDOW, clockAt(START));

        assertThat(limiter.acquire("m-1").permitted()).isTrue();
        assertThat(limiter.acquire("m-1").permitted()).isTrue();
        assertThat(limiter.acquire("m-1").permitted())
                .as("die dritte Anfrage des Mandanten m-1 ueberschreitet das Limit von 2")
                .isFalse();

        assertThat(limiter.acquire("m-2").permitted())
                .as("das Limit des einen Mandanten darf den anderen nicht treffen")
                .isTrue();
    }

    @Test
    @DisplayName("S-24: 'Retry-After' ist die Wartezeit in Sekunden (mindestens 1) und nie kuerzer "
            + "als die Wartezeit bis zum Fensterende")
    void the_retry_after_is_a_waiting_time_in_seconds() {
        AtomicReference<Instant> now = new AtomicReference<>(START);
        ApiRateLimiter limiter = new ApiRateLimiter(1, WINDOW, now::get);

        limiter.acquire("m-1");
        ApiRateLimiter.Decision atWindowStart = limiter.acquire("m-1");

        assertThat(atWindowStart.permitted()).isFalse();
        assertThat(atWindowStart.retryAfterSeconds())
                .as("der Vertrag verlangt einen ganzzahligen Wert von mindestens 1 Sekunde")
                .isGreaterThanOrEqualTo(1)
                .isGreaterThanOrEqualTo(WINDOW.toSeconds())
                .isLessThanOrEqualTo(WINDOW.toSeconds() + 1);

        // 59 Sekunden spaeter: das Fenster endet in einer Sekunde.
        now.set(START.plusSeconds(59));
        ApiRateLimiter.Decision nearWindowEnd = limiter.acquire("m-1");

        assertThat(nearWindowEnd.permitted()).isFalse();
        assertThat(nearWindowEnd.retryAfterSeconds())
                .as("am Ende des Fensters ist die Wartezeit kurz — aber mindestens eine Sekunde")
                .isGreaterThanOrEqualTo(1)
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("S-24: ein neues Fenster beginnt von vorn")
    void a_new_window_starts_over() {
        AtomicReference<Instant> now = new AtomicReference<>(START);
        ApiRateLimiter limiter = new ApiRateLimiter(1, WINDOW, now::get);

        assertThat(limiter.acquire("m-1").permitted()).isTrue();
        assertThat(limiter.acquire("m-1").permitted()).isFalse();

        now.set(START.plus(WINDOW));

        assertThat(limiter.acquire("m-1").permitted())
                .as("nach dem Ende des Fensters beginnt der Zaehler von vorn")
                .isTrue();
    }

    @Test
    @DisplayName("S-24: ein Limit von 0 lehnt jede Anfrage ab")
    void a_limit_of_zero_rejects_every_request() {
        ApiRateLimiter limiter = new ApiRateLimiter(0, WINDOW, clockAt(START));

        ApiRateLimiter.Decision decision = limiter.acquire("m-1");

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("S-24: die Grenze kommt aus der Konfiguration — im Code ist kein Wert verdrahtet")
    void the_limit_comes_from_the_configuration() {
        ApiRateLimiter limiter = new ApiRateLimiter(7, WINDOW, clockAt(START));

        assertThat(limiter.limit()).isEqualTo(7);
        assertThat(limiter.window()).isEqualTo(WINDOW);
    }

    /**
     * Liefert eine steuerbare Uhr.
     *
     * @param instant der Zeitpunkt, den die Uhr liefert
     * @return die Uhr
     */
    private static ClockPort clockAt(Instant instant) {
        return () -> instant;
    }
}
