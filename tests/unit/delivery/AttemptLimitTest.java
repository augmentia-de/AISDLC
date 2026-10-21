package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Aufgabe nach erschoepten Versuchen (US-012, FR-023).
 *
 * <p>Getestet wird die Versuchsgrenze: nach dem letzten erlaubten Versuch endet
 * der Zustelllauf als <em>aufgegeben</em> (tote Zustellung) — und nicht sooner.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.retry_policy.configuration.max_attempts}
 * (Vorgabewert 8, F-04); `.agent/knowledge/schema.sql` -&gt;
 * {@code delivery_runs_abandoned_at_ck} und {@code delivery_runs_terminal_no_next_attempt_ck}.
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-012-1, AC-012-4.
 */
class AttemptLimitTest {

    @Test
    @DisplayName("AC-012-1: mit der Vorgabe von 8 Versuchen ist die Grenze nach dem 8. Versuch erreicht")
    void the_default_limit_is_reached_after_eight_attempts() {
        AttemptLimit limit = new AttemptLimit(8);

        assertThat(limit.maxAttempts()).isEqualTo(8);
        assertThat(limit.isExhausted(7)).isFalse();
        assertThat(limit.isExhausted(8)).isTrue();
        assertThat(limit.isExhausted(9)).isTrue();
    }

    @Test
    @DisplayName("AC-012-4: mit der konfigurierten Grenze 1 endet die Zustellung nach genau einem Versuch")
    void a_configured_limit_of_one_is_not_undershot() {
        AttemptLimit limit = new AttemptLimit(1);

        assertThat(limit.isExhausted(0)).isFalse();
        assertThat(limit.isExhausted(1))
                .as("die konfigurierte Grenze wird nicht unterlaufen")
                .isTrue();
        assertThat(limit.statusWhenExhausted()).isEqualTo(DeliveryStatus.ABANDONED);
    }

    @Test
    @DisplayName("AC-012-1: die aufgegebene Zustellung ist ein endgueltiger Zustand")
    void the_abandoned_state_is_terminal() {
        assertThat(DeliveryStatus.ABANDONED.isTerminal()).isTrue();
        assertThat(DeliveryStateMachine.canTransition(DeliveryStatus.ABANDONED, DeliveryStatus.SCHEDULED))
                .as("eine aufgegebene Zustellung wird nicht erneut eingeplant")
                .isFalse();
    }

    @Test
    @DisplayName("AC-012-3: eine aufgegebene Zustellung erhaelt keinen weiteren Versuchszeitpunkt")
    void an_abandoned_run_has_no_next_attempt() {
        assertThat(DeliveryStateMachine.nextAttemptAtFor(DeliveryStatus.ABANDONED))
                .as("endgueltige Zustaende tragen keinen naechsten Versuchszeitpunkt")
                .isNull();
    }
}
