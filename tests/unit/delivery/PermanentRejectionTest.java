package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit-Ebene — Endgueltige Ablehnung des Empfaengers (US-014, FR-025).
 *
 * <p>Getestet wird die Klassifikation der Empfangerantwort: das dokumentierte
 * Ablehnungssignal beendet die Zustellung sofort, ein 5xx oder ein Netzwerkfehler
 * wird wiederholt, und jede andere 4xx endet ohne weitere Versuche.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.response.outcomes}
 * (410 mit {@code X-EchoBox-Rejection: permanent} = endgueltig; 5xx/408/429 = wiederholbar;
 * uebrige 4xx und 3xx = endgueltig).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-014-1, AC-014-3, AC-014-4.
 */
class PermanentRejectionTest {

    @Test
    @DisplayName("AC-014-1: 410 mit dem Ablehnungssignal ist eine endgueltige Ablehnung")
    void the_rejection_signal_is_a_permanent_rejection() {
        DeliveryOutcome outcome = DeliveryOutcome.classify(410, true);

        assertThat(outcome).isEqualTo(DeliveryOutcome.PERMANENT_REJECTION);
        assertThat(outcome.isFinal()).isTrue();
    }

    @Test
    @DisplayName("AC-014-4: 410 ohne das Ablehnungssignal ist eine gewöhnliche Client-Ablehnung")
    void a_410_without_the_signal_is_a_client_error() {
        DeliveryOutcome outcome = DeliveryOutcome.classify(410, false);

        assertThat(outcome).isEqualTo(DeliveryOutcome.CLIENT_ERROR);
        assertThat(outcome.isFinal()).isTrue();
    }

    @ParameterizedTest(name = "AC-014-4: HTTP {0} endet ohne weitere Versuche")
    @ValueSource(ints = {400, 401, 403, 404, 422})
    @DisplayName("AC-014-4: jede uebrige 4xx wird ohne weitere Versuche aufgegeben")
    void every_other_4xx_is_final( Integer responseStatus) {
        DeliveryOutcome outcome = DeliveryOutcome.classify(responseStatus, false);

        assertThat(outcome).isEqualTo(DeliveryOutcome.CLIENT_ERROR);
        assertThat(outcome.isFinal()).isTrue();
    }

    @ParameterizedTest(name = "AC-014-3: HTTP {0} wird wiederholt")
    @ValueSource(ints = {500, 502, 503, 504, 408, 429})
    @DisplayName("AC-014-3: 5xx, 408 und 429 sind wiederholbare Fehler")
    void server_errors_are_retryable( Integer responseStatus) {
        DeliveryOutcome outcome = DeliveryOutcome.classify(responseStatus, false);

        assertThat(outcome).isEqualTo(DeliveryOutcome.RETRYABLE_FAILURE);
        assertThat(outcome.isFinal()).isFalse();
    }

    @Test
    @DisplayName("AC-014-4: eine Umleitung (3xx) wird nicht gefolgt und beendet den Lauf")
    void a_redirect_is_never_followed_and_ends_the_run() {
        DeliveryOutcome outcome = DeliveryOutcome.classify(302, false);

        assertThat(outcome).isEqualTo(DeliveryOutcome.CLIENT_ERROR);
        assertThat(outcome.isFinal()).isTrue();
    }

    @Test
    @DisplayName("AC-010-3: eine 2xx-Antwort ist ein Erfolg")
    void a_2xx_response_is_a_success() {
        assertThat(DeliveryOutcome.classify(200, false)).isEqualTo(DeliveryOutcome.SUCCESS);
        assertThat(DeliveryOutcome.classify(204, false)).isEqualTo(DeliveryOutcome.SUCCESS);
        assertThat(DeliveryOutcome.SUCCESS.isFinal()).isTrue();
    }

    @Test
    @DisplayName("AC-014-3: ein Netzwerkfehler wird wie ein 5xx behandelt")
    void a_network_error_is_retried_like_a_server_error() {
        assertThat(DeliveryOutcome.NETWORK_ERROR.isFinal()).isFalse();
        assertThat(DeliveryOutcome.TIMEOUT.isFinal()).isFalse();
        assertThat(DeliveryOutcome.CANCELLED.isFinal()).isTrue();
    }
}
