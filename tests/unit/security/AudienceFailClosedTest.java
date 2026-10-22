package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.echobox.config.AudiencePolicy;
import com.echobox.config.EchoBoxProperties;
import com.echobox.config.SecurityConfiguration;

/**
 * Unit-Ebene — die Zielgruppen-Pruefung ist <strong>fail-closed</strong>
 * (S-29).
 *
 * <p>Die Luecke: die Pruefung wurde nur dann der Pruefkette hinzugefuegt, wenn
 * die Audience konfiguriert war. Ein <strong>leerer</strong> Secret-Wert
 * ({@code ECHobox_SECURITY_AUDIENCE=}) ergibt {@code ""} — die Pruefung fiel
 * still aus. Der Dienst nahm dann Token eines <em>anderen</em> Clients desselben
 * Identity Providers an, sofern Mandant und Rolle passten.
 *
 * <p>Die Abwehr hat <strong>zwei</strong> Seiten:
 * <ul>
 *   <li>der Decoder lehnt <strong>jedes</strong> Token ab, wenn keine
 *       Zielgruppe konfiguriert ist (die Pruefung wird nie abgeschaltet);</li>
 *   <li>der Dienst startet nicht, wenn die Zielgruppe leer ist — die
 *       Fehlkonfiguration wird nicht stillschweigend akzeptiert.</li>
 * </ul>
 */
class AudienceFailClosedTest {

    private JwtTestKeys keys;

    @BeforeEach
    void setUp() throws Exception {
        keys = new JwtTestKeys();
    }

    @AfterEach
    void tearDown() {
        if (keys != null) {
            keys.close();
        }
    }

    @Test
    @DisplayName("S-29: ohne konfigurierte Zielgruppe wird jedes Token abgelehnt")
    void without_a_configured_audience_every_token_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox"));

        assertThatThrownBy(() -> decoder.decode(token))
                .as("eine leere Zielgruppe darf die Pruefung nicht abschalten — das Token traegt "
                        + "zwar eine Zielgruppe, der Dienst kann sie aber nicht pruefen")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-29: ohne konfigurierte Zielgruppe wird auch ein Token ohne Zielgruppe abgelehnt")
    void without_a_configured_audience_a_token_without_audience_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "   ");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), null));

        assertThatThrownBy(() -> decoder.decode(token))
                .as("auch eine Konfiguration aus Leerzeichen ist eine leere Zielgruppe")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-29: eine leere Zielgruppe verhindert den Start des Dienstes")
    void an_empty_audience_prevents_the_start() {
        assertThatThrownBy(() -> AudiencePolicy.required(""))
                .as("der Dienst darf mit einer leeren Zielgruppe nicht starten — die Konfiguration "
                        + "waere sonst ein stilles Fail-open")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Zielgruppe");
        assertThatThrownBy(() -> AudiencePolicy.required(null))
                .as("auch ein fehlender Wert verhindert den Start")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("S-29: die konfigurierte Zielgruppe bleibt wirksam")
    void the_configured_audience_still_works() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "echobox");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox"));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getAudience()).contains("echobox");
        assertThat(AudiencePolicy.required("echobox").audience())
                .as("die Zielgruppe wird unveraendert uebernommen")
                .isEqualTo("echobox");
    }

    private static JwtDecoder decoderWith(String jwkSetUri, String audience) {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getSecurity().setJwkSetUri(jwkSetUri);
        properties.getSecurity().setAudience(audience);
        return new SecurityConfiguration().jwtDecoder(properties);
    }
}
