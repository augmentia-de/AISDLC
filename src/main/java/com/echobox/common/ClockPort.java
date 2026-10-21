package com.echobox.common;

import java.time.Instant;

/**
 * Port fuer die Uhr des Dienstes.
 *
 * <p>Der Dienst rechnet mit der Zeit — Backoff, Lease-Ablauf, Aufbewahrung,
 * Zeitstempel der Antworten. Ein Test, der auf die Wanduhr wartet, waere weder
 * schnell noch deterministisch; darum geht jeder Zeitbezug ueber diesen Port.
 *
 * <p>Im Betrieb liefert die Systemuhr die Zeit, im Test eine steuerbare Uhr
 * (zum Beispiel {@code com.echobox.testsupport.MutableClock}).
 */
public interface ClockPort {

    /**
     * Liefert den aktuellen Zeitpunkt.
     *
     * @return der aktuelle Zeitpunkt
     */
    Instant now();
}
