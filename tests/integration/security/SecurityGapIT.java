package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.EchoBoxApplication;
import com.echobox.testsupport.PostgresTestContainer;
import com.echobox.testsupport.TestDeliveryConfiguration;

/**
 * Integrationsebene — die <strong>belegten Luecken</strong> des
 * Sicherheits-Audits auf HTTP-Ebene.
 *
 * <p><strong>Diese Tests schlagen fehl.</strong> Sie belegen zwei Luecken, die
 * nur am laufenden Dienst sichtbar werden:
 *
 * <table>
 *   <caption>Die belegten Luecken</caption>
 *   <tr><th>Test</th><th>Befund</th><th>Schwere</th></tr>
 *   <tr><td>S-GAP-20</td><td>Die 2-MiB-Grenze des Request-Bodies greift bei
 *       {@code Transfer-Encoding: chunked} nicht</td><td>High</td></tr>
 *   <tr><td>S-GAP-24</td><td>Das im Vertrag zugesagte Anfragelimit (HTTP 429)
 *       wird nie ausgeloest</td><td>Medium</td></tr>
 * </table>
 *
 * <p>Der Decoder wird ersetzt: der Test prueft die HTTP-Mechanik, nicht die
 * Anmeldung (die prueft {@code JwtDecoderHardeningTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class SecurityGapIT {

    /** Die Grenze des Request-Bodies (2 MiB). */
    private static final int MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024;

    /** Die Zahl der Anfragen, mit denen das Anfragelimit provoziert wird. */
    private static final int REQUESTS_TO_PROVOKE_THE_LIMIT = 300;

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("LUECKE (Befund S-20): ein chunked Request-Body ueber 2 MiB muss mit 413 abgelehnt werden")
    void a_chunked_request_body_above_two_mebibytes_must_be_rejected() throws Exception {
        byte[] body = oversizedButValidJson();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/events"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("Ohne 'Content-Length' (Transfer-Encoding: chunked) wird die Grenze des "
                        + "Request-Bodies nicht geprueft — der Body wird ungegrenzt gelesen.")
                .isEqualTo(413);
    }

    @Test
    @DisplayName("LUECKE (Befund S-24): das Anfragelimit muss mit 429 und 'Retry-After' greifen")
    void the_request_limit_must_answer_with_429() throws Exception {
        boolean limited = false;
        String retryAfter = null;

        for (int attempt = 0; attempt < REQUESTS_TO_PROVOKE_THE_LIMIT && !limited; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/endpoints")
                    .header("Authorization", "Bearer test-token")).andReturn();
            limited = result.getResponse().getStatus() == 429;
            if (limited) {
                retryAfter = result.getResponse().getHeader("Retry-After");
            }
        }

        assertThat(limited)
                .as("Der eingefrorene Vertrag (openapi.yaml, Fehlersemantik) nennt HTTP 429 "
                        + "'rate_limit_exceeded' mit 'Retry-After' fuer jede Operation. Die "
                        + "Implementierung hat keine eingehende Ratenbegrenzung.")
                .isTrue();
        assertThat(retryAfter)
                .as("die 429-Antwort muss den Header 'Retry-After' tragen")
                .isNotBlank();
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
                + "\"order.changed\", \"idempotency_key\": \"chunked-1\", \"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
