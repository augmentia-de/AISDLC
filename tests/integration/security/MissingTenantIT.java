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
 * Integrationsebene — ein Token <strong>ohne Mandant</strong> ist ein Fehler
 * des Aufrufers (S-33).
 *
 * <p>Der Mandant stammt ausschliesslich aus dem Claim {@code organisation_id}
 * der Identitaet. Fehlt der Claim, ist das Token nicht brauchbar: der Dienst
 * antwortet mit <strong>401</strong> {@code unauthorized} — nicht mit 500
 * {@code internal_error} und einem Stacktrace im Protokoll.
 *
 * <p>Der Mandant wird weiterhin <strong>nicht</strong> geraten und nicht
 * ersetzt (fail-closed): die Ablehnung ist die einzige moegliche Antwort.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class MissingTenantIT {

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
                .claim("roles", List.of("developer"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    @Test
    @DisplayName("S-33: ein Token ohne organisation_id ergibt 401, keinen 500")
    void a_token_without_a_tenant_is_answered_with_401() throws Exception {
        HttpResponse<String> response = send();

        assertThat(response.statusCode())
                .as("ein Token ohne Mandant ist ein Fehler des Aufrufers — der Mandant stammt "
                        + "ausschliesslich aus der Identitaet (D-01). Der Dienst darf daraus keinen "
                        + "Serverfehler machen.")
                .isEqualTo(401);
        assertThat(response.body())
                .as("die Ablehnung traegt das Fehlerschema des Vertrags")
                .contains("unauthorized")
                .doesNotContain("internal_error");
    }

    /**
     * Sendet einen POST an die Ereignis-Einlieferung.
     *
     * @return die Antwort
     * @throws Exception wenn der Versand scheitert
     */
    private HttpResponse<String> send() throws Exception {
        byte[] body = "{\"event_type\": \"order.changed\", \"idempotency_key\": \"ohne-mandant\", "
                .concat("\"payload\": {\"a\": 1}}")
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/events"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
