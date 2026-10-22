package com.echobox.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Das <strong>Anfragelimit der API</strong> (S-24).
 *
 * <p>Der eingefrorene Vertrag ({@code docs/api/openapi.yaml}) sagt fuer
 * <em>jede</em> Operation HTTP <strong>429</strong> {@code rate_limit_exceeded}
 * mit dem Header {@code Retry-After} zu — „API-seitiges Anfragelimit (nicht das
 * Endpunkt-Limit aus FR-033)“. Dieser Zaehler ist diese Zusage: er begrenzt
 * die Zahl der Anfragen eines Mandanten je Zeitfenster.
 *
 * <p>Der Zaehler ist <strong>je Instanz</strong> und im Speicher: er schuetzt
 * die Instanz vor der Last eines einzelnen Mandanten. Das Endpunkt-Limit
 * (FR-033) bleibt davon unberuehrt — es zaehlt je Endpunkt und wird in der
 * Zustellung durchgesetzt.
 *
 * <p>Die Werte kommen aus der Betriebskonfiguration
 * ({@code echobox.rate-limit.limit}, {@code echobox.rate-limit.window-seconds});
 * im Code ist kein Zahlwert verdrahtet (ADR-009).
 */
public class ApiRateLimiter {

    /** Ab dieser Zahl von Mandanten werden abgelaufene Zaehler entfernt. */
    private static final int PURGE_THRESHOLD = 1024;

    private final int limit;

    private final Duration window;

    private final ClockPort clock;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * Erzeugt den Zaehler.
     *
     * @param limit  die Zahl der erlaubten Anfragen je Fenster
     * @param window die Laenge des Fensters
     * @param clock  die Uhr
     */
    public ApiRateLimiter(int limit, Duration window, ClockPort clock) {
        this.limit = Math.max(0, limit);
        this.window = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(1)
                : window;
        this.clock = clock;
    }

    /**
     * Zaehlt eine Anfrage und entscheidet, ob sie erlaubt ist.
     *
     * @param key der Schluessel des Zaehlers (der Mandant)
     * @return die Entscheidung
     */
    public Decision acquire(String key) {
        Instant now = clock.now();
        purgeExpired(now);

        Counter counter = counters.compute(key, (name, existing) -> {
            if (existing == null || !existing.startedAt().plus(window).isAfter(now)) {
                // Neues Fenster — der Zaehler beginnt von vorn.
                return new Counter(now, 1);
            }
            return new Counter(existing.startedAt(), existing.count() + 1);
        });

        if (counter.count() <= limit) {
            return new Decision(true, 0);
        }
        long retryAfterSeconds = Math.max(1,
                Duration.between(now, counter.startedAt().plus(window)).toSeconds() + 1);
        return new Decision(false, retryAfterSeconds);
    }

    /**
     * Liefert die erlaubte Zahl der Anfragen je Fenster.
     *
     * @return die Grenze
     */
    public int limit() {
        return limit;
    }

    /**
     * Liefert die Laenge des Fensters.
     *
     * @return das Fenster
     */
    public Duration window() {
        return window;
    }

    private void purgeExpired(Instant now) {
        if (counters.size() < PURGE_THRESHOLD) {
            return;
        }
        counters.entrySet().removeIf(entry -> !entry.getValue().startedAt().plus(window).isAfter(now));
    }

    /** Das Ergebnis einer Anfrage am Limit. */
    public record Decision(boolean permitted, long retryAfterSeconds) {
    }

    /** Der Zaehler eines Mandanten im laufenden Fenster. */
    private record Counter(Instant startedAt, int count) {
    }
}
