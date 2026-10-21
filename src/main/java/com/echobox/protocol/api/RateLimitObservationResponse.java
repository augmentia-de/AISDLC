package com.echobox.protocol.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Beobachtung des Endpunkt-Limits — die Projektion des Vertrags
 * {@code components.schemas.RateLimitObservation}.
 *
 * <p>Der Abschnitt ist <strong>immer</strong> Teil der Kennzahlen: die
 * Zaehlung laeuft unabhaengig vom Grenzwert (AC-018-4, V-42).
 *
 * <p><strong>{@code limit} ist nie {@code null}</strong> — es ist immer der
 * <strong>wirksame</strong> Grenzwert (V-03):
 * <ul>
 *   <li>kein Override des Endpunkts ⇒ es gilt die Vorgabe der
 *       Betriebskonfiguration (F-01: 60 je Endpunkt und Minute),</li>
 *   <li>Override {@code 0} ⇒ die Begrenzung ist fuer diesen Endpunkt
 *       <strong>abgeschaltet</strong> ⇒ {@code limited: false}.</li>
 * </ul>
 *
 * @param limit            der wirksame Grenzwert je Endpunkt und Zeitfenster
 * @param limited          ob die Begrenzung fuer diesen Endpunkt gilt
 * @param windowSeconds    die Laenge des Zaehlfensters in Sekunden
 * @param strategy         die angewandte Strategie ({@code delay} oder {@code block})
 * @param observedAttempts die Zahl der beobachteten Zustellversuche
 * @param delayedAttempts  die Zahl der durch das Limit verschobenen Versuche
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RateLimitObservationResponse(
        @JsonProperty("limit") int limit,
        @JsonProperty("limited") boolean limited,
        @JsonProperty("window_seconds") int windowSeconds,
        @JsonProperty("strategy") String strategy,
        @JsonProperty("observed_attempts") long observedAttempts,
        @JsonProperty("delayed_attempts") long delayedAttempts) {
}
