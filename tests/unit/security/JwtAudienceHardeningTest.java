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

import com.echobox.config.EchoBoxProperties;
import com.echobox.config.SecurityConfiguration;

/**
 * Unit-Ebene — die Absicherung der **Zielgruppe** und der **Ablaufzeit**
 * (S-21).
 *
 * <p>Der Vertrag setzt keine Zielgruppe fest — der Dienst prueft aber, dass ein
 * Token, das der Identity Provider fuer einen <strong>anderen</strong> Client
 * ausgestellt hat, nicht angenommen wird. Zusaetzlich ist die Ablaufzeit
 * Pflicht: ein Token ohne {@code exp} waere unbegrenzt gueltig.
 *
 * <p>Die Abnahme dieser Zusage sind die Gap-Tests in {@code SecurityGapTest};
 * dieser Test belegt zusaetzlich, dass die **richtige** Zielgruppe angenommen
 * wird und die Zielgruppe **konfigurierbar** ist.
 */
class JwtAudienceHardeningTest {

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
    @DisplayName("S-21: ein Token mit der konfigurierten Zielgruppe wird angenommen")
    void a_token_with_the_configured_audience_is_accepted() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "echobox");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox"));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getAudience()).contains("echobox");
    }

    @Test
    @DisplayName("S-21: die Zielgruppe ist konfigurierbar — eine andere Audience wird abgelehnt")
    void the_audience_is_configurable() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "meine-andere-api");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "meine-andere-api"));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getAudience()).contains("meine-andere-api");
    }

    @Test
    @DisplayName("S-21: ein Token ohne Zielgruppe wird abgelehnt")
    void a_token_without_an_audience_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "echobox");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), null));

        assertThatThrownBy(() -> decoder.decode(token))
                .as("ohne Zielgruppe ist das Token nicht fuer diese API ausgegeben")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-21: eine Token mit mehreren Zielgruppen wird angenommen, wenn eine passt")
    void a_token_with_several_audiences_is_accepted_if_one_matches() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "echobox");
        String token = keys.signRs256(JwtTestKeys.claimsWithAudiences("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), List.of("anderer-client", "echobox")));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getAudience()).contains("echobox");
    }

    private static JwtDecoder decoderWith(String jwkSetUri, String audience) {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getSecurity().setJwkSetUri(jwkSetUri);
        properties.getSecurity().setAudience(audience);
        return new SecurityConfiguration().jwtDecoder(properties);
    }
}
