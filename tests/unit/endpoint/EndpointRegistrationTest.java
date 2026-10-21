package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.echobox.common.ValidationException;

/**
 * Unit-Ebene — Registrierung eines Endpunkts (US-001, FR-001).
 *
 * <p>Getestet wird die reine Wertelogik der Ziel-URL. Es gibt keine Datenbank,
 * keinen HTTP-Kontext und keine Fremdabhaengigkeit — der Dienst ist der einzige
 * zu testende Teil.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code components.schemas.
 * EndpointCreateRequest.x-validation-rules} (`target_url_https_only`,
 * `target_url_no_credentials`).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-001-1, AC-001-2, AC-001-3.
 */
class EndpointRegistrationTest {

    @Test
    @DisplayName("AC-001-1: eine https-Ziel-URL mit Host wird akzeptiert")
    void https_target_url_is_accepted() {
        EndpointTargetUrl targetUrl = EndpointTargetUrl.of("https://orders.example.invalid/hooks/echobox");

        assertThat(targetUrl.value()).isEqualTo("https://orders.example.invalid/hooks/echobox");
        assertThat(targetUrl.host()).isEqualTo("orders.example.invalid");
    }

    @Test
    @DisplayName("AC-001-1: eine https-Ziel-URL mit Port und Pfad wird akzeptiert")
    void https_target_url_with_port_and_path_is_accepted() {
        assertThatCode(() -> EndpointTargetUrl.of("https://orders.example.invalid:8443/hooks/echobox"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "AC-001-2: Schema {0} wird abgelehnt")
    @ValueSource(strings = {
            "http://orders.example.invalid/hooks/echobox",
            "ftp://orders.example.invalid/hooks/echobox",
            "file:///tmp/echobox-hook",
            "orders.example.invalid/hooks/echobox"
    })
    @DisplayName("AC-001-2: ohne https-Schema -> Verstoß scheme_not_allowed (HTTP 422)")
    void non_https_scheme_is_rejected(String targetUrl) {
        assertThatThrownBy(() -> EndpointTargetUrl.of(targetUrl))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> {
                    ValidationException violation = (ValidationException) thrown;
                    assertThat(violation.field()).isEqualTo("target_url");
                    assertThat(violation.code()).isEqualTo("scheme_not_allowed");
                });
    }

    @Test
    @DisplayName("AC-001-3: eingebettete Zugangsdaten werden abgelehnt (credentials_not_allowed)")
    void embedded_credentials_are_rejected() {
        assertThatThrownBy(() -> EndpointTargetUrl.of("https://user:pass@orders.example.invalid/hooks/echobox"))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> {
                    ValidationException violation = (ValidationException) thrown;
                    assertThat(violation.field()).isEqualTo("target_url");
                    assertThat(violation.code()).isEqualTo("credentials_not_allowed");
                })
                .satisfies(thrown -> assertThat(thrown.getMessage())
                        .as("die Meldung nennt den Weg ueber das Secret")
                        .contains("Secret"));
    }

    @Test
    @DisplayName("AC-001-3: der Benutzername allein ohne Passwort wird ebenfalls abgelehnt")
    void embedded_user_information_is_rejected() {
        assertThatThrownBy(() -> EndpointTargetUrl.of("https://user@orders.example.invalid/hooks/echobox"))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).code())
                        .isEqualTo("credentials_not_allowed"));
    }

    @Test
    @DisplayName("AC-001-2: die Pruefung erkennt das Schema vor den Zugangsdaten")
    void scheme_is_checked_before_credentials() {
        assertThatThrownBy(() -> EndpointTargetUrl.of("http://user:pass@orders.example.invalid/hooks/echobox"))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).code())
                        .as("http ist zuerst falsch — das Schema wird zuerst geprueft")
                        .isEqualTo("scheme_not_allowed"));
    }
}
