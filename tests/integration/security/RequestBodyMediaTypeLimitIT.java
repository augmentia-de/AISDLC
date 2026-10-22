package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
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
 * Integrationsebene — die Grenze des Request-Bodies gilt **unabhaengig vom
 * Medientyp** (S-28).
 *
 * <p><strong>Der Angriff:</strong> die Grenze des Request-Bodies (2 MiB) haengt
 * an der Pruefung, ob der Content-Type mit {@code application/json} beginnt.
 * Spring MVC liest aber auch {@code application/*+json}
 * ({@code MappingJackson2HttpMessageConverter}) — ein Body dieses Medientyps
 * fiel an <strong>beiden</strong> Grenzen vorbei (Vorpruefung gegen
 * {@code Content-Length} und begrenztes Lesen) und landete unbeschaenkt im
 * Heap des <strong>geteilten</strong> JVM: ein {@code OutOfMemoryError} waere
 * ein Ausfall fuer <strong>alle</strong> Mandanten.
 *
 * <p>Der Test deckt <strong>jeden</strong> Medientyp ab — auch den, den der
 * Dienst gar nicht liest. Eine Grenze, die an einer Eingabevariante haengt, ist
 * per Konstruktion umgehbar; die Grenze muss an der <strong>Nutzlastgrenze</strong>
 * haengen, nicht an einer Medienpruefung.
 *
 * <p>Zusaetzlich belegt der Test, dass die Ablehnung des
 * <strong>Mandantenparameters</strong> (D-01) ebenfalls nicht am Medientyp
 * haengt: {@code application/*+json} darf die Pruefung nicht umgehen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class RequestBodyMediaTypeLimitIT {

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

    @ParameterizedTest(name = "Medientyp {0}")
    @ValueSource(strings = {
            "application/json",
            "application/vnd.custom+json",
            "application/problem+json",
            "text/plain",
            "multipart/form-data; boundary=abc",
            "application/octet-stream"})
    @DisplayName("S-28: die Grenze des Request-Bodies gilt fuer jeden Medientyp")
    void the_body_limit_applies_to_every_media_type(String contentType) throws Exception {
        HttpResponse<String> response = send(contentType, oversizedButValidJson());

        assertThat(response.statusCode())
                .as("die Grenze des Request-Bodies (2 MiB) darf nicht am Medientyp haengen. "
                        + "Sonst landet der Body unbeschaenkt im Heap des geteilten JVM "
                        + "(Ausfall fuer alle Mandanten). Medientyp: %s", contentType)
                .isEqualTo(413);
        assertThat(response.body())
                .as("die Ablehnung traegt das Fehlerschema des Vertrags")
                .contains("payload_too_large");
    }

    @Test
    @DisplayName("S-28: die Grenze des Request-Bodies gilt auch ohne Content-Type")
    void the_body_limit_applies_without_a_content_type() throws Exception {
        String statusLine = sendRawWithoutContentType(oversizedButValidJson());

        assertThat(statusLine)
                .as("auch ohne Content-Type gilt die Grenze — die Vorpruefung und das begrenzte "
                        + "Lesen benutzen dieselbe Bedingung")
                .contains("413");
    }

    @Test
    @DisplayName("S-28: der Mandantenparameter wird auch bei application/*+json abgelehnt")
    void the_tenant_parameter_is_rejected_for_every_json_media_type() throws Exception {
        byte[] body = "{\"organisation_id\": \"fremder-mandant\", \"event_type\": \"order.changed\", "
                .concat("\"idempotency_key\": \"s-28-1\", \"payload\": {\"a\": 1}}")
                .getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = send("application/vnd.custom+json", body);

        assertThat(response.statusCode())
                .as("der Mandant stammt ausschliesslich aus der Identitaet — die Pruefung des Bodies "
                        + "darf nicht am Medientyp haengen (D-01)")
                .isEqualTo(422);
        assertThat(response.body())
                .as("die Ablehnung traegt das Fehlerschema des Vertrags")
                .contains("tenant_parameter_rejected");
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
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body)))
                .build();

        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sendet einen POST **ohne** {@code Content-Type} — ueber einen rohen
     * Socket, weil der HTTP-Client der JDK sonst selbst einen Medientyp
     * setzt.
     *
     * @param body der Body
     * @return die Statuszeile der Antwort
     * @throws IOException wenn der Versand scheitert
     */
    private String sendRawWithoutContentType(byte[] body) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(20000);
            OutputStream out = socket.getOutputStream();
            String head = "POST /api/v1/events HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Authorization: Bearer test-token\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            try {
                out.write(body);
                out.flush();
            } catch (IOException exception) {
                // Der Server hat die Verbindung nach der Ablehnung geschlossen —
                // die Statuszeile ist die Antwort.
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String statusLine = reader.readLine();
            return statusLine == null ? "" : statusLine;
        }
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
                + "\"order.changed\", \"idempotency_key\": \"medien-1\", \"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
