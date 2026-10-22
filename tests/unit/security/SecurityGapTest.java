package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.echobox.config.CoreConfiguration;
import com.echobox.config.EchoBoxProperties;
import com.echobox.config.SecurityConfiguration;
import com.echobox.delivery.DeliveryHttpClient;
import com.echobox.delivery.DeliveryRequest;
import com.echobox.delivery.DeliveryResponse;

import com.sun.net.httpserver.HttpServer;

/**
 * Unit-Ebene — die <strong>belegten Luecken</strong> des Sicherheits-Audits.
 *
 * <p><strong>Diese Tests schlagen fehl.</strong> Sie sind kein Fehler im Test,
 * sondern der Nachweis der Luecke: jeder von ihnen beschreibt genau eine
 * Eigenschaft, die der Dienst laut Vertrag, ADR oder Audit-Vorgabe haben
 * <em>sollte</em>, die die Implementierung aber nicht hat. Sie schlagen genau an
 * der Luecke fehl — nicht an einem Randfall daneben.
 *
 * <p>Nach Schritt 8 (Ruecksprung zu Schritt 6/7) sind sie die Abnahme der
 * Behebung: sie werden gruen, sobald die jeweilige Luecke geschlossen ist.
 *
 * <table>
 *   <caption>Die belegten Luecken</caption>
 *   <tr><th>Test</th><th>Befund</th><th>Schwere</th></tr>
 *   <tr><td>S-GAP-21a</td><td>Zielgruppe (Audience) wird nicht geprueft</td><td>Medium</td></tr>
 *   <tr><td>S-GAP-21b</td><td>Ein Token ohne Ablaufzeit wird angenommen</td><td>Medium</td></tr>
 *   <tr><td>S-GAP-22</td><td>Ein 16-Byte-Schluessel wird stillschweigend akzeptiert</td><td>Medium</td></tr>
 *   <tr><td>S-GAP-23</td><td>Die Antwort des Empfaengers wird ohne Groessengrenze gelesen</td><td>Medium</td></tr>
 * </table>
 */
class SecurityGapTest {

    /** Die Groessengrenze, die ADR-003 fuer den Verschluesselungsschluessel festlegt. */
    private static final int REQUIRED_KEY_BYTES = 32;

    /** Die Hoechstgroesse, die ein Antwortkoerper eines Empfaengers haben darf. */
    private static final int MAX_RESPONSE_BODY_BYTES = 1024 * 1024;

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
    @DisplayName("LUECKE (Befund S-21): ein Token mit fremder Zielgruppe muss abgelehnt werden")
    void a_token_with_a_foreign_audience_must_be_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "einen-anderen-client"));

        assertThatThrownBy(() -> decoder.decode(token))
                .as("Ein Token, das fuer eine andere Zielgruppe ausgestellt wurde, "
                        + "darf von EchoBox nicht angenommen werden.")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("LUECKE (Befund S-21): ein Token ohne Ablaufzeit muss abgelehnt werden")
    void a_token_without_an_expiry_must_be_rejected() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri());
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"), null, "echobox"));

        assertThatThrownBy(() -> decoder.decode(token))
                .as("Ein Token ohne 'exp' ist unbefristet gueltig und muss abgelehnt werden.")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("LUECKE (Befund S-22): ein 16-Byte-Schluessel muss den Start verhindern")
    void a_sixteen_byte_key_must_prevent_the_start() {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getCrypto().setKey(Base64.getEncoder().encodeToString(new byte[16]));
        properties.getCrypto().setKeyRequired(true);

        assertThatThrownBy(() -> new CoreConfiguration().payloadEncryptionKey(properties))
                .as("ADR-003 legt 256 Bit fest. Ein 16-Byte-Schluessel wuerde stillschweigend "
                        + "AES-128 bedeuten und muss den Start verhindern.")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("LUECKE (Befund S-23): die Antwort des Empfaengers muss begrenzt gelesen werden")
    void the_response_body_of_the_receiver_must_be_read_with_a_limit() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024];
        java.util.Arrays.fill(oversized, (byte) 'x');

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(200, oversized.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(oversized);
            }
        });
        server.start();
        try {
            DeliveryHttpClient client = new DeliveryHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(5));
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

            DeliveryResponse response = client.deliver(new DeliveryRequest(
                    target, Map.of(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json"));

            assertThat(response.body().length())
                    .as("Der Antwortkoerper eines Empfaengers darf nicht unbegrenzt in den Speicher "
                            + "geladen werden.")
                    .isLessThanOrEqualTo(MAX_RESPONSE_BODY_BYTES);
        } finally {
            server.stop(0);
        }
    }

    private static JwtDecoder decoderWith(String jwkSetUri) {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getSecurity().setJwkSetUri(jwkSetUri);
        return new SecurityConfiguration().jwtDecoder(properties);
    }
}
