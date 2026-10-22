package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.EchoBoxApplication;
import com.echobox.testsupport.PostgresTestContainer;
import com.echobox.testsupport.TestDeliveryConfiguration;

/**
 * Integrationsebene — die <strong>belegten Luecken der zweiten Audit-Runde</strong>.
 *
 * <p><strong>Diese Tests schlagen fehl.</strong> Sie belegen drei Luecken, die
 * erst durch die Nacharbeit der ersten Runde (S-19 bis S-27) entstanden oder
 * sichtbar wurden:
 *
 * <table>
 *   <caption>Die belegten Luecken der zweiten Runde</caption>
 *   <tr><th>Test</th><th>Befund</th><th>Schwere</th></tr>
 *   <tr><td>S-GAP-28</td><td>Die Grenze des Request-Bodies laesst sich ueber den
 *       Medientyp umgehen ({@code application/*+json}) — der Body wird
 *       unbeschaenkt gelesen</td><td>High</td></tr>
 *   <tr><td>S-GAP-30</td><td>Ein nicht unterstuetzter Medientyp ergibt HTTP 500
 *       statt eines 4xx — ein Fehler des Clients wird als Serverfehler
 *       protokolliert</td><td>Medium</td></tr>
 * </table>
 *
 * <p>Der Decoder wird ersetzt: der Test prueft die HTTP-Mechanik, nicht die
 * Anmeldung (die prueft {@code JwtDecoderHardeningTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class SecurityGapRound2IT {

    /** Die Grenze des Request-Bodies (2 MiB). */
    private static final int MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024;

    @LocalServerPort
    private int port;

    @MockBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerDataSource(registry);
        registry.add("echobox.delivery.worker.enabled", () -> "false");
    }

    @BeforeEach
    void authenticateEveryToken() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "tester")
                .claim("organisation_id", "m-1")
                .claim("roles", List.of("developer"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    @Test
    @DisplayName("LUECKE (Befund S-28): die Grenze des Request-Bodies darf nicht ueber den "
            + "Medientyp umgangen werden")
    void the_request_body_limit_must_not_be_bypassed_by_the_media_type() throws Exception {
        byte[] body = oversizedButValidJson();

        HttpResponse<String> response = send("application/vnd.custom+json", body);

        assertThat(response.statusCode())
                .as("Die Grenze des Request-Bodies (2 MiB) wird nur fuer den Medientyp "
                        + "'application/json' geprueft (TenantParameterFilter.isBodyRelevant). "
                        + "Spring MVC liest aber auch 'application/*+json' — der Body wird dann "
                        + "unbeschaenkt in den Heap des geteilten JVM gelesen "
                        + "(Ausfall fuer alle Mandanten).")
                .isEqualTo(413);
    }

    @Test
    @DisplayName("LUECKE (Befund S-30): ein nicht unterstuetzter Medientyp darf kein 500 ergeben")
    void an_unsupported_media_type_must_not_be_answered_with_500() throws Exception {
        byte[] body = oversizedButValidJson();

        HttpResponse<String> response = send("text/plain", body);

        assertThat(response.statusCode())
                .as("Ein nicht unterstuetzter Medientyp ist ein Fehler des Aufrufers. Er wird "
                        + "als 'internal_error' (500) mit Stacktrace protokolliert — der Vertrag "
                        + "kennt fuer einen Fehler des Aufrufers nur 4xx (400 'malformed_request').")
                .isBetween(400, 499);
    }

    /**
     * Sendet einen POST an die Ereignis-Einlieferung.
     *
     * @param contentType der Medientyp
     * @param body        der Body
     * @return die Antwort
     * @throws Exception wenn der Versand scheitert
     */
    private HttpResponse<String> send(String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/events"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Baut einen gueltigen JSON-Body, der groesser als 2 MiB ist, aber eine
     * **kleine** Nutzlast traegt: die Grenze der Nutzlast (1 MiB) darf nicht
     * greifen — nur die Grenze des Request-Bodies.
     *
     * @return die Body-Bytes
     */
    private static byte[] oversizedButValidJson() {
        String padding = " ".repeat(MAX_REQUEST_BODY_BYTES + 64);
        String json = "{\"event_type\": " + padding
                + "\"order.changed\", \"idempotency_key\": \"runde-2-1\", \"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
