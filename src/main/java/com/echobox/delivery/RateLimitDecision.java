package com.echobox.delivery;

import java.time.Duration;
import java.time.Instant;

/**
 * Die Entscheidung des Endpunkt-Limits (FR-033).
 *
 * @param permitted ob der Versuch jetzt ausgefuehrt werden darf
 * @param waitTime  die Wartezeit, bis der Versuch erneut moeglich ist
 * @param retryAt   der Zeitpunkt, zu dem der Versuch erneut moeglich ist
 */
public record RateLimitDecision(boolean permitted, Duration waitTime, Instant retryAt) {

    /**
     * Die Entscheidung, die einen Versuch zulaesst.
     *
     * @param retryAt der Zeitpunkt, zu dem das Zaehlfenster endet
     * @return die Entscheidung
     */
    public static RateLimitDecision permitted(Instant retryAt) {
        return new RateLimitDecision(true, Duration.ZERO, retryAt);
    }

    /**
     * Die Entscheidung, die einen Versuch verschiebt.
     *
     * @param waitTime die Wartezeit
     * @param retryAt  der Zeitpunkt, zu dem der Versuch erneut moeglich ist
     * @return die Entscheidung
     */
    public static RateLimitDecision delayed(Duration waitTime, Instant retryAt) {
        return new RateLimitDecision(false, waitTime, retryAt);
    }
}
