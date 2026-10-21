package com.echobox.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Der Schutz eines Endpunkts gegen zu viele Zustellversuche in kurzer Zeit
 * (FR-033, D-14, ADR-010).
 *
 * <p>Der Zaehler gilt **je Endpunkt** und **je Zeitfenster**. Vorgabe (F-01):
 * 60 Versuche je Endpunkt und Minute, Strategie {@code delay} — bei
 * Ueberschreitung wird der Versuch **verschoben**, nicht verworfen: das Ereignis
 * geht nicht verloren, der Zustelllauf traegt den Zustand {@code delayed}.
 *
 * <p>Die Zaehlung laeuft **immer**, unabhaengig vom Grenzwert (AC-018-4) — sie
 * ist die Grundlage der Kennzahlen im Vertrag
 * ({@code EndpointMetrics.rate_limit.observed_attempts}).
 *
 * <p><strong>Zwei Wege, eine Regel:</strong> die statische Methode
 * {@link #decide(long, int, Duration, Instant, Instant)} ist die
 * <strong>einzige</strong> Entscheidungsregel. Im Betrieb zaehlt der
 * <strong>Datenbankzaehler</strong> ({@code app.endpoint_rate_limit_counters},
 * {@link RateLimitGuard}) — er ueberlebt einen Neustart, er ist mandantenfest
 * und ein zweiter Worker sieht dieselbe Zahl. Die Instanzmethode
 * {@link #acquire(UUID, Instant)} fuehrt denselben Zaehler im Arbeitsspeicher
 * und ist die nachpruefbare Variante der Regel (Unit-Ebene).
 *
 * <p>Das Zeitfenster beginnt mit dem **ersten** Versuch im Fenster und laeuft
 * {@code window} lang; danach ist der Zaehler wieder frei.
 */
public class EndpointRateLimiter {

    private final int limit;

    private final Duration window;

    private final RateLimitStrategy strategy;

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    /**
     * Erzeugt den Zaehler.
     *
     * @param limit    der Grenzwert je Endpunkt und Zeitfenster
     * @param window   die Laenge des Zeitfensters
     * @param strategy die Strategie bei Ueberschreitung
     */
    public EndpointRateLimiter(int limit, Duration window, RateLimitStrategy strategy) {
        this.limit = limit;
        this.window = window;
        this.strategy = strategy;
    }

    /**
     * Registriert einen Versuch und liefert die Entscheidung.
     *
     * @param endpointId der Endpunkt
     * @param now        der Zeitpunkt des Versuchs
     * @return die Entscheidung
     */
    public RateLimitDecision acquire(UUID endpointId, Instant now) {
        Window counter = windows.computeIfAbsent(endpointId, id -> new Window(now));
        synchronized (counter) {
            if (!now.isBefore(counter.start.plus(window))) {
                counter.reset(now);
            }
            counter.observed++;
            boolean exceeding = counter.observed > limit;
            if (exceeding) {
                counter.delayed++;
            }
            return decide(counter.observed, limit, window, counter.start, now);
        }
    }

    /**
     * Die Entscheidung des Endpunkt-Limits — die <strong>eine</strong> Stelle,
     * die entscheidet (ADR-010 §2).
     *
     * <p>Der Zaehler laeuft immer: die Zahl der beobachteten Versuche wird
     * unabhaengig vom Grenzwert fortgeschrieben (AC-018-4). Ueber dem
     * Grenzwert wird der Versuch <strong>verschoben</strong>, nicht verworfen —
     * kein Ereignis geht verloren (AC-018-1).
     *
     * @param observed    die Zahl der Versuche einschliesslich des aktuellen
     * @param limit       der wirksame Grenzwert ({@code 0} = abgeschaltet)
     * @param window      die Laenge des Zaehlfensters
     * @param windowStart der Beginn des Zaehlfensters
     * @param now         der Bezugszeitpunkt
     * @return die Entscheidung
     */
    public static RateLimitDecision decide(long observed,
                                            int limit,
                                            Duration window,
                                            Instant windowStart,
                                            Instant now) {
        Instant retryAt = windowStart.plus(window);
        if (limit <= 0 || observed <= limit) {
            return RateLimitDecision.permitted(retryAt);
        }
        Duration waitTime = Duration.between(now, retryAt);
        if (waitTime.isNegative() || waitTime.isZero()) {
            waitTime = window;
        }
        return RateLimitDecision.delayed(waitTime, retryAt);
    }

    /**
     * Liefert die Zahl der beobachteten Versuche je Endpunkt — unabhaengig vom
     * Grenzwert (AC-018-4).
     *
     * @param endpointId der Endpunkt
     * @return die Zahl der Versuche
     */
    public long observedAttempts(UUID endpointId) {
        Window counter = windows.get(endpointId);
        if (counter == null) {
            return 0L;
        }
        synchronized (counter) {
            return counter.observed;
        }
    }

    /**
     * Liefert die Zahl der durch das Limit verschobenen Versuche je Endpunkt.
     *
     * @param endpointId der Endpunkt
     * @return die Zahl der verschobenen Versuche
     */
    public long delayedAttempts(UUID endpointId) {
        Window counter = windows.get(endpointId);
        if (counter == null) {
            return 0L;
        }
        synchronized (counter) {
            return counter.delayed;
        }
    }

    /**
     * Liefert den Grenzwert.
     *
     * @return der Grenzwert
     */
    public int limit() {
        return limit;
    }

    /**
     * Liefert die Laenge des Zeitfensters.
     *
     * @return die Fensterlaenge
     */
    public Duration window() {
        return window;
    }

    /**
     * Liefert die Strategie.
     *
     * @return die Strategie
     */
    public RateLimitStrategy strategy() {
        return strategy;
    }

    /** Der Zaehler eines Endpunkts im aktuellen Zeitfenster. */
    private static final class Window {

        private Instant start;

        private long observed;

        private long delayed;

        private Window(Instant start) {
            this.start = start;
        }

        private void reset(Instant newStart) {
            this.start = newStart;
            this.observed = 0L;
            this.delayed = 0L;
        }
    }
}
