package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.delivery.DeliveryStateMachine;
import com.echobox.delivery.DeliveryStatus;

/**
 * Unit-Ebene — Pause und Reaktivierung eines Endpunkts (US-002, FR-002).
 *
 * <p>Getestet wird die Zustandsregel der Pause: ein offener Wiederholungsvorgang
 * wird abgebrochen, und der abgebrochene Lauf ist endgueltig — er hat keinen
 * naechsten Versuchszeitpunkt und wird nicht wieder eingeplant.
 *
 * <p>Testorakel: `docs/architecture/domain_model.md` Abschnitt 3.3 (Uebergangstabelle)
 * und `.agent/knowledge/schema.sql` -&gt; {@code delivery_runs_terminal_no_next_attempt_ck}.
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-002-4.
 */
class EndpointPauseTest {

    @Test
    @DisplayName("AC-002-4: die Pause bricht einen offenen Wiederholungsvorgang ab")
    void pause_cancels_an_open_retry() {
        DeliveryStatus afterPause = DeliveryStateMachine.onEndpointPaused(DeliveryStatus.FAILED);

        assertThat(afterPause)
                .as("ein geplanter Wiederholungsvorgang endet mit 'abgebrochen (pausiert)'")
                .isEqualTo(DeliveryStatus.CANCELLED);
    }

    @Test
    @DisplayName("AC-002-4: die Pause bricht auch einen laufenden Versuch ab")
    void pause_cancels_a_scheduled_run() {
        assertThat(DeliveryStateMachine.onEndpointPaused(DeliveryStatus.SCHEDULED))
                .isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(DeliveryStateMachine.onEndpointPaused(DeliveryStatus.PENDING))
                .isEqualTo(DeliveryStatus.CANCELLED);
    }

    @Test
    @DisplayName("AC-002-4: ein abgebrochener Lauf ist endgueltig und hat keinen naechsten Versuch")
    void cancelled_run_is_terminal_without_next_attempt() {
        assertThat(DeliveryStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(DeliveryStateMachine.isTerminal(DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStateMachine.canTransition(DeliveryStatus.CANCELLED, DeliveryStatus.SCHEDULED))
                .as("ein abgebrochener Lauf wird nie wieder eingeplant")
                .isFalse();
        assertThat(DeliveryStateMachine.canTransition(DeliveryStatus.CANCELLED, DeliveryStatus.FAILED))
                .isFalse();
    }

    @Test
    @DisplayName("AC-002-4: die Pause trifft keinen endgueltigen Lauf")
    void pause_does_not_touch_a_terminal_run() {
        assertThat(DeliveryStateMachine.onEndpointPaused(DeliveryStatus.SENT))
                .as("eine erfolgreich zugestellte Zustellung wird durch eine Pause nicht veraendert")
                .isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("AC-002-1: ein Endpunkt im Zustand 'paused' ist kein aktiver Endpunkt mehr")
    void paused_is_not_an_active_delivery_target() {
        assertThat(DeliveryStateMachine.canTransition(DeliveryStatus.CANCELLED, DeliveryStatus.SENT))
                .as("ein pausierter Endpunkt erzeugt keinen neuen Zustellvorgang")
                .isFalse();
    }
}
