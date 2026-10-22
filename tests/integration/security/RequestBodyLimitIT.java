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

import com.echobox.EchoBoxApplication;
import com.echobox.testsupport.PostgresTestContainer;
import com.echobox.testsupport.TestDeliveryConfiguration;

/**
 * Integrationsebene — die Grenze des Request-Bodies gilt **unabhaengig** von
 * {@code Content-Length} (S-20).
 *
 * <p>Der Angriff: ein Mandant sendet einen POST mit
 * {@code Transfer-Encoding: chunked} und einem riesigen Body. Ohne
 * {@code Content-Length} liefert die Laengenangabe {@code -1} — eine Pruefung
 * gegen die Ziffer greift nicht, und der Body landet unbeschaenkt im Heap des
 * **geteilten** JVM (Ausfall fuer alle Mandanten).
 *
 * <p>Der Test beweist beide Seiten:
 * <ul>
 *   <li>ein chunked Body <strong>unter</strong> der Grenze wird angenommen —</li>
 *   <li>ein chunked Body <strong>ueber</strong> der Grenze ergibt HTTP 413.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class RequestBodyLimitIT {

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
    @DisplayName("S-20: ein chunked Body unter der Grenze wird angenommen")
    void a_chunked_request_body_below_the_limit_is_accepted() throws Exception {
        byte[] body = smallButValidJson();

        HttpResponse<String> response = send(body);

        assertThat(response.statusCode())
                .as("ein Body unter der Grenze darf nicht abgelehnt werden")
                .isNotEqualTo(413);
    }

    @Test
    @DisplayName("S-20: ein chunked Body ueber der Grenze ergibt 413 — unabhaengig von Content-Length")
    void a_chunked_request_body_above_the_limit_is_rejected() throws Exception {
        byte[] body = oversizedButValidJson();

        HttpResponse<String> response = send(body);

        assertThat(response.statusCode())
                .as("ohne 'Content-Length' (Transfer-Encoding: chunked) muss die Grenze trotzdem "
                        + "greifen — der Body darf nicht unbeschaenkt in den Heap gelesen werden")
                .isEqualTo(413);
        assertThat(response.body())
                .as("die Ablehnung traegt das Fehlerschema des Vertrags")
                .contains("payload_too_large");
    }

    private HttpResponse<String> send(byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/events"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
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
                + "\"order.changed\", \"idempotency_key\": \"chunked-1\", \"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Baut einen kleinen, gueltigen JSON-Body.
     *
     * @return die Body-Bytes
     */
    private static byte[] smallButValidJson() {
        String json = "{\"event_type\": \"order.changed\", \"idempotency_key\": \"chunked-klein\", "
                + "\"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
