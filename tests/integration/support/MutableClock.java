package com.echobox.testsupport;

import java.time.Duration;
import java.time.Instant;

import com.echobox.common.ClockPort;

/**
 * Testbare Uhr.
 *
 * <p>Der Dienst rechnet mit der Zeit — Backoff, Lease-Ablauf, Aufbewahrung.
 * Ein Test, der auf die Wanduhr wartet, waere weder schnell noch
 * deterministisch. Die Testuhr wird ueber {@code echobox.clock.testable}
 * aktiviert; im Normalbetrieb gilt die Systemuhr.
 *
 * <p>Die Uhr startet an einem festen Zeitpunkt, damit die erwarteten
 * Zeitstempel in den Tests deterministisch sind.
 */
public final class MutableClock implements ClockPort {

    private Instant now = Instant.parse("2026-10-21T09:15:22.481Z");

    @Override
    public Instant now() {
        return now;
    }

    /**
     * Setzt die Uhr auf einen festen Zeitpunkt.
     *
     * @param instant der Zeitpunkt
     */
    public void setTo(Instant instant) {
        this.now = instant;
    }

    /**
     * Rueckt die Uhr vor.
     *
     * @param duration die Dauer
     */
    public void advance(Duration duration) {
        this.now = this.now.plus(duration);
    }
}
