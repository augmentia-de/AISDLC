package com.echobox.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Die Wartezeit zwischen zwei Versuchen (FR-022, ADR-008, F-04).
 *
 * <p>Der Abstand **verdoppelt** sich mit jedem Versuch: Versuch 1 wartet den
 * Basisabstand, Versuch 2 das Doppelte, Versuch 3 das Vierfache — bis zum
 * Deckel. Vorgabe (F-04): 8 Versuche, Basis 1 Minute, ×2, Deckel 60 Minuten,
 * Streuung ± 20 %.
 *
 * <p>Die Streuung verhindert, dass alle Endpunkte eines Ausfalls zur selben
 * Zeit wieder versucht werden. Sie liegt **symmetrisch** um den Basisabstand
 * und wird **nach** dem Deckel angewandt: bei Deckel 60 Minuten und Streuung
 * ± 20 % liegt die Wartezeit zwischen 48 und 72 Minuten — das entspricht dem
 * zugesagten Gesamtfenster von rund 2 h 28 min.
 *
 * <p>Alle Werte sind **konfigurierbar** — kein Wert ist im Code verdrahtet.
 */
public class BackoffPolicy {

    private final Duration baseDelay;

    private final double multiplier;

    private final Duration maxDelay;

    private final double jitter;

    private final Random random;

    /**
     * Erzeugt die Regel.
     *
     * @param baseDelay der Basisabstand des ersten Versuchs
     * @param multiplier der Verdopplungsfaktor
     * @param maxDelay  der Deckel
     * @param jitter    die Streuung als Anteil (0.2 = ± 20 %; 0 = keine Streuung)
     * @param random    die Zufallsquelle der Streuung
     */
    public BackoffPolicy(Duration baseDelay, double multiplier, Duration maxDelay, double jitter, Random random) {
        this.baseDelay = baseDelay;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay;
        this.jitter = jitter;
        this.random = random;
    }

    /**
     * Liefert die Wartezeit nach einem Versuch.
     *
     * @param attempt die Nummer des **beendeten** Versuchs (1-basiert)
     * @return die Wartezeit bis zum naechsten Versuch
     */
    public Duration delayForAttempt(int attempt) {
        int number = Math.max(1, attempt);
        double factor = Math.pow(multiplier, number - 1L);
        long millis = (long) Math.round(baseDelay.toMillis() * factor);
        millis = Math.min(millis, maxDelay.toMillis());
        if (jitter > 0.0) {
            double deviation = (random.nextDouble() * 2.0) - 1.0;
            millis = Math.round(millis * (1.0 + (deviation * jitter)));
            millis = Math.max(0L, millis);
        }
        return Duration.ofMillis(millis);
    }

    /**
     * Liefert den Zeitpunkt des naechsten Versuchs.
     *
     * @param now     der Bezugszeitpunkt
     * @param attempt die Nummer des **beendeten** Versuchs (1-basiert)
     * @return der Zeitpunkt des naechsten Versuchs — nie in der Vergangenheit
     */
    public Instant nextAttemptAt(Instant now, int attempt) {
        return now.plus(delayForAttempt(attempt));
    }

    /**
     * Liefert den Basisabstand.
     *
     * @return der Basisabstand
     */
    public Duration baseDelay() {
        return baseDelay;
    }

    /**
     * Liefert den Verdopplungsfaktor.
     *
     * @return der Faktor
     */
    public double multiplier() {
        return multiplier;
    }

    /**
     * Liefert den Deckel.
     *
     * @return der Deckel
     */
    public Duration maxDelay() {
        return maxDelay;
    }

    /**
     * Liefert die Streuung.
     *
     * @return die Streuung als Anteil
     */
    public double jitter() {
        return jitter;
    }
}
