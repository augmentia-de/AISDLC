package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
 * Unit-Ebene — die Absicherung des <strong>echten</strong> JWT-Decoders.
 *
 * <p>Die Integrationstests benutzen den Post-Processor {@code jwt()} aus
 * {@code spring-security-test}: er setzt eine fertige Identitaet und umgeht
 * damit genau den Teil der Kette, der das Token <em>prueft</em> — Signatur,
 * Algorithmus und Ablauf. Dieser Test schliesst die Luecke: er ruft den
 * Decoder der Produktion auf ({@link SecurityConfiguration#jwtDecoder}).
 *
 * <p>Erprobte Eigenschaften (Pruefung 1 des Sicherheits-Audits):
 * <ul>
 *   <li>die Signatur wird gegen den JWKS des Identity Providers geprueft,</li>
 *   <li>der Algorithmus ist auf RS256 beschraenkt — {@code none} und
 *       {@code HS256} (Algorithmus-Verwirrung) werden abgelehnt,</li>
 *   <li>ein abgelaufenes Token wird abgelehnt,</li>
 *   <li>ohne konfigurierten Identity Provider wird <strong>jedes</strong>
 *       Token abgelehnt (kein „fail-open“).</li>
 * </ul>
 */
class JwtDecoderHardeningTest {

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
    @DisplayName("S-HART-1: ein gueltiges RS256-Token wird angenommen und traegt die Claims")
    void a_valid_rs256_token_is_accepted() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox"));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getClaimAsString("organisation_id")).isEqualTo("m-1");
        assertThat(jwt.getSubject()).isEqualTo("tester");
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
    }

    @Test
    @DisplayName("S-HART-2: ein Token mit dem Algorithmus 'none' wird abgelehnt")
    void a_token_with_algorithm_none_is_rejected() {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signWithNone(JwtTestKeys.claims("m-1", List.of("service_admin"),
                Instant.now().plusSeconds(300).getEpochSecond(), null));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-HART-3: ein HS256-Token (Algorithmus-Verwirrung) wird abgelehnt")
    void a_token_with_algorithm_confusion_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signWithHs256(JwtTestKeys.claims("m-1", List.of("service_admin"),
                Instant.now().plusSeconds(300).getEpochSecond(), null));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-HART-4: ein abgelaufenes Token wird abgelehnt")
    void an_expired_token_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().minusSeconds(600).getEpochSecond(), "echobox"));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-HART-5: ein mit einem fremden Schluessel signiertes Token wird abgelehnt")
    void a_token_signed_with_a_foreign_key_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signWithForeignKey(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox"));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("S-HART-6: ohne konfigurierten Identity Provider wird jedes Token abgelehnt")
    void without_an_identity_provider_every_token_is_rejected() throws Exception {
        JwtDecoder decoder = decoderWith("");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox"));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Kein Identity Provider konfiguriert");
    }

    @Test
    @DisplayName("S-HART-7: die JWKS-Adresse wird aus der Konfiguration gelesen, nicht verdrahtet")
    void the_jwk_set_uri_comes_from_the_configuration() throws Exception {
        Map<String, Object> claims = JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "echobox");

        assertThat(keys.fetchJwks()).contains("\"kty\":\"RSA\"").contains("\"kid\":\"" + JwtTestKeys.KEY_ID + "\"");
        assertThat(decoderWith(keys.jwkSetUri()).decode(keys.signRs256(claims))).isNotNull();
    }

    private static JwtDecoder decoderWith(String jwkSetUri) {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getSecurity().setJwkSetUri(jwkSetUri);
        return new SecurityConfiguration().jwtDecoder(properties);
    }
}
