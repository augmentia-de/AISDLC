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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Integrationsebene — ein nicht unterstuetzter Medientyp ist ein Fehler des
 * <strong>Aufrufers</strong> (S-30).
 *
 * <p>Ohne eigenen Fall fiel {@code HttpMediaTypeNotSupportedException} in den
 * allgemeinen Handler: die Antwort war HTTP <strong>500</strong>
 * {@code internal_error} mit Stacktrace im Protokoll. Ein Aufrufer haette
 * damit <strong>unbegrenzt</strong> Serverfehler erzeugen koennen — das
 * Monitoring haette jeden davon als Serverfehler gesehen, nicht als Fehler des
 * Aufrufers.
 *
 * <p>Der eingefrorene Vertrag kennt fuer einen Fehler des Aufrufers nur 4xx
 * (400 {@code malformed_request}) — 415 waere ein Vertragswechsel. Die
 * Ablehnung traegt darum das Schema {@code Problem} des Vertrags und
 * <strong>kein</strong> internes Detail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class MediaTypeErrorIT {

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

    @ParameterizedTest(name = "Medientyp {0}")
    @ValueSource(strings = {
            "text/plain",
            "application/octet-stream",
            "application/xml",
            "multipart/form-data; boundary=abc",
            "application/vnd.custom+json"})
    @DisplayName("S-30: ein nicht unterstuetzter Medientyp ergibt 4xx, keinen 500")
    void an_unsupported_media_type_is_answered_with_a_client_error(String contentType) throws Exception {
        HttpResponse<String> response = send(contentType, smallButValidJson());

        assertThat(response.statusCode())
                .as("ein nicht unterstuetzter Medientyp ist ein Fehler des Aufrufers — der Vertrag "
                        + "kennt dafuer nur 4xx (400 malformed_request)")
                .isBetween(400, 499);
        assertThat(response.statusCode())
                .as("ein 500 waere ein Serverfehler — der Monitor wuerde ihn als solchen sehen")
                .isNotEqualTo(500);
        assertThat(response.body())
                .as("die Ablehnung traegt das Fehlerschema des Vertrags")
                .contains("malformed_request")
                .doesNotContain("internal_error");
    }

    @Test
    @DisplayName("S-30: die Ablehnung gibt kein internes Detail preis")
    void the_rejection_leaks_no_internal_detail() throws Exception {
        HttpResponse<String> response = send("text/plain", smallButValidJson());

        assertThat(response.body())
                .as("die Antwort traegt weder Stacktrace noch Klassen- oder Pfadnamen")
                .doesNotContain("org.springframework")
                .doesNotContain("com.echobox")
                .doesNotContain("Exception")
                .doesNotContain("java.");
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
     * Baut einen kleinen, gueltigen JSON-Body.
     *
     * @return die Body-Bytes
     */
    private static byte[] smallButValidJson() {
        String json = "{\"event_type\": \"order.changed\", \"idempotency_key\": \"medien-klein\", "
                + "\"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
