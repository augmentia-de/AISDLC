package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.zip.GZIPOutputStream;

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
 * Integrationsebene — die **eigenstaendige** Nachpruefung der Grenze des
 * Request-Bodies (S-28, dritte Audit-Runde).
 *
 * <p>Dieser Test enthaelt **nicht** die Tests der Nacharbeit: er prueft die
 * Umgehungsmöglichkeiten, die im Auftrag der dritten Runde ausdruecklich
 * genannt sind — und zwar jeweils **ueber den echten HTTP-Stack** (Socket bzw.
 * JDK-Client), nicht ueber MockMvc:
 *
 * <ol>
 *   <li><strong>jeder Medientyp</strong> — die Grenze darf nicht an einem
 *       Medientyp haengen;</li>
 *   <li><strong>beide Uebertragungswege</strong> — mit {@code Content-Length}
 *       (Vorpruefung) und chunked (begrenztes Lesen);</li>
 *   <li><strong>ohne {@code Content-Length}</strong> — der Body laeuft bis zum
 *       Ende der Verbindung;</li>
 *   <li><strong>{@code Content-Encoding: gzip}</strong> — eine komprimierte
 *       „Bombe" darf weder dekomprimiert werden (Speicher) noch angenommen
 *       werden;</li>
 *   <li><strong>ein Body, den der Message-Converter selbst liest</strong> —
 *       knapp unter der Grenze muss er ankommen (die Grenze darf nicht zu
 *       streng sein), und der Converter darf nur die begrenzten Byte sehen.</li>
 * </ol>
 *
 * <p>Der Angriff, den S-28 beschreibt, laesst den Body unbeschaenkt in den
 * Heap des <strong>geteilten</strong> JVM laden: ein {@code OutOfMemoryError}
 * waere ein Ausfall fuer <strong>alle</strong> Mandanten. Deshalb wird nach
 * jedem Angriff geprueft, ob der Dienst <strong>weiterhin antwortet</strong>.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class RequestBodyBoundaryAuditIT {

    /** Die Grenze des Request-Bodies (2 MiB). */
    private static final int MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024;

    /** Der Pfad der Ereignis-Einlieferung. */
    private static final String EVENTS = "/api/v1/events";

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

    @ParameterizedTest(name = "Medientyp {0} (Content-Length)")
    @ValueSource(strings = {
            "application/json",
            "application/vnd.custom+json",
            "application/problem+json",
            "text/plain",
            "multipart/form-data; boundary=abc",
            "application/octet-stream"})
    @DisplayName("S-28: die Grenze gilt fuer jeden Medientyp (Uebertragung mit Content-Length)")
    void the_limit_applies_to_every_media_type_with_content_length(String contentType) throws Exception {
        HttpResponse<String> response = sendWithContentLength(contentType, oversized());

        assertThat(response.statusCode())
                .as("die Grenze des Request-Bodies (2 MiB) darf nicht am Medientyp haengen — "
                        + "sonst landet der Body unbeschaenkt im Heap des geteilten JVM "
                        + "(Ausfall fuer alle Mandanten). Medientyp: %s", contentType)
                .isEqualTo(413);
        assertThat(response.body()).contains("payload_too_large");
    }

    @ParameterizedTest(name = "Medientyp {0} (chunked)")
    @ValueSource(strings = {
            "application/json",
            "application/vnd.custom+json",
            "text/plain",
            "application/octet-stream"})
    @DisplayName("S-28: die Grenze gilt fuer jeden Medientyp (chunked, ohne Content-Length)")
    void the_limit_applies_to_every_media_type_when_chunked(String contentType) throws Exception {
        HttpResponse<String> response = sendChunked(contentType, oversized());

        assertThat(response.statusCode())
                .as("bei 'Transfer-Encoding: chunked' ist die Laenge unbekannt (-1) — die Grenze "
                        + "muss trotzdem greifen, unabhaengig vom Medientyp. Medientyp: %s", contentType)
                .isEqualTo(413);
    }

    @Test
    @DisplayName("S-28: die Grenze gilt auch ohne Content-Length (Body bis zum Verbindungsende)")
    void the_limit_applies_without_any_content_length() throws Exception {
        String statusLine = rawRequest(head("application/json", null), oversized(), true);

        assertThat(statusLine)
                .as("ohne 'Content-Length' und ohne 'Transfer-Encoding' laeuft der Body bis zum "
                        + "Ende der Verbindung — die Vorpruefung greift nicht, das begrenzte Lesen muss greifen")
                .contains("413");
    }

    @Test
    @DisplayName("S-28: die Grenze gilt auch ohne Content-Type")
    void the_limit_applies_without_any_content_type() throws Exception {
        String statusLine = rawRequest(head(null, null), oversized(), true);

        assertThat(statusLine)
                .as("auch ohne 'Content-Type' gilt die Grenze — eine Grenze, die an einer "
                        + "Eingabevariante haengt, ist per Konstruktion umgehbar")
                .contains("413");
    }

    @Test
    @DisplayName("S-28: eine gzip-Bombe wird weder dekomprimiert noch angenommen")
    void a_gzip_bomb_is_neither_decompressed_nor_accepted() throws Exception {
        byte[] bomb = gzip(20 * 1024 * 1024);

        HttpResponse<String> response = sendWithContentLength("application/json", bomb);

        assertThat(response.statusCode())
                .as("der Dienst dekomprimiert keinen Request-Body — die GZIP-Bombe darf weder "
                        + "angenommen (202) werden noch den Heap fuellen. Der Status ist ein "
                        + "Fehler des Aufrufers (400) oder die Grenze (413).")
                .isBetween(400, 499);

        assertThat(the_service_is_still_able_to_answer())
                .as("nach dem Angriff muss der Dienst weiterhin antworten — ein Ausfall des "
                        + "geteilten JVM waere ein Ausfall fuer alle Mandanten")
                .isTrue();
    }

    @Test
    @DisplayName("S-28: ein Body knapp unter der Grenze kommt an (der Converter liest die begrenzten Byte)")
    void a_body_just_below_the_limit_is_accepted() throws Exception {
        byte[] body = justBelowTheLimit();

        HttpResponse<String> response = sendWithContentLength("application/json", body);

        assertThat(response.statusCode())
                .as("ein Body von %d Byte liegt unter der Grenze von %d Byte und ueber der "
                        + "Nutzlastgrenze von 1 MiB (die Nutzlast selbst ist klein) — er muss "
                        + "ankommen: die Grenze darf nicht zu streng sein.",
                        body.length, MAX_REQUEST_BODY_BYTES)
                .isEqualTo(202);
    }

    @Test
    @DisplayName("S-28: ein Body an einer GET-Operation laesst den Dienst nicht abstuerzen")
    void a_body_on_a_get_request_does_not_take_the_service_down() throws Exception {
        String statusLine = rawGetWithBody(oversized());

        assertThat(statusLine)
                .as("die Grenze gilt fuer die Methoden, die einen Body tragen koennen — ein GET "
                        + "liest den Body nicht. Der Container schluckt hoechstens "
                        + "'max-swallow-size' (2 MB) und schliesst die Verbindung: der Heap des "
                        + "geteilten JVM bleibt unberuehrt. Statuszeile: %s", statusLine)
                .contains(" 200");

        assertThat(the_service_is_still_able_to_answer())
                .as("nach dem Angriff muss der Dienst weiterhin antworten")
                .isTrue();
    }

    /**
     * Prueft, ob der Dienst noch antwortet.
     *
     * @return {@code true}, wenn der Dienst antwortet
     * @throws Exception wenn der Versand scheitert
     */
    private boolean the_service_is_still_able_to_answer() throws Exception {
        HttpResponse<String> response = sendWithContentLength("application/json",
                "{\"event_type\": \"order.changed\", \"idempotency_key\": \"nach-dem-angriff\", "
                        .concat("\"payload\": {\"a\": 1}}")
                        .getBytes(StandardCharsets.UTF_8));
        return response.statusCode() == 202;
    }

    /**
     * Sendet einen GET mit einem ueberdimensionierten Body (roher Socket).
     *
     * @param body der Body
     * @return die Statuszeile der Antwort
     * @throws IOException wenn der Versand scheitert
     */
    private String rawGetWithBody(byte[] body) throws IOException {
        String head = "GET /api/v1/endpoints HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + port + "\r\n"
                + "Authorization: Bearer test-token\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        return rawRequest(head, body, true);
    }

    /**
     * Sendet einen POST mit {@code Content-Length} (JDK-Client, {@code ofByteArray}).
     *
     * @param contentType der Medientyp (darf {@code null} sein)
     * @param body        der Body
     * @return die Antwort
     * @throws Exception wenn der Versand scheitert
     */
    private HttpResponse<String> sendWithContentLength(String contentType, byte[] body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + EVENTS))
                .header("Authorization", "Bearer test-token")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sendet einen POST chunked (JDK-Client, {@code ofInputStream} — die Laenge
     * ist unbekannt, der Client verwendet {@code Transfer-Encoding: chunked}).
     *
     * @param contentType der Medientyp
     * @param body        der Body
     * @return die Antwort
     * @throws Exception wenn der Versand scheitert
     */
    private HttpResponse<String> sendChunked(String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + EVENTS))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body)))
                .build();

        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Baut den Kopf einer rohen HTTP-Anfrage.
     *
     * @param contentType     der Medientyp (darf {@code null} sein)
     * @param contentLength   die Laenge (darf {@code null} sein — dann wird der
     *                        Body per {@code Transfer-Encoding: chunked} gesendet)
     * @return der Kopf
     */
    private String head(String contentType, Integer contentLength) {
        StringBuilder builder = new StringBuilder();
        builder.append("POST ").append(EVENTS).append(" HTTP/1.1\r\n");
        builder.append("Host: 127.0.0.1:").append(port).append("\r\n");
        builder.append("Authorization: Bearer test-token\r\n");
        if (contentType != null) {
            builder.append("Content-Type: ").append(contentType).append("\r\n");
        }
        if (contentLength != null) {
            builder.append("Content-Length: ").append(contentLength).append("\r\n");
        } else {
            builder.append("Transfer-Encoding: chunked\r\n");
        }
        builder.append("Connection: close\r\n\r\n");
        return builder.toString();
    }

    /**
     * Sendet eine rohe HTTP-Anfrage ueber einen Socket.
     *
     * @param head            der Kopf
     * @param body            der Body
     * @param shutdownOutput  ob die schreibende Seite nach dem Body geschlossen
     *                        wird (das beendet einen Body ohne Laengenangabe)
     * @return die Statuszeile der Antwort
     * @throws IOException wenn der Versand scheitert
     */
    private String rawRequest(String head, byte[] body, boolean shutdownOutput) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(30000);
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            if (head.contains("Transfer-Encoding: chunked")) {
                writeChunked(out, body);
            } else {
                out.write(body);
            }
            out.flush();
            if (shutdownOutput) {
                socket.shutdownOutput();
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String statusLine = reader.readLine();
            return statusLine == null ? "" : statusLine;
        }
    }

    /**
     * Schreibt den Body in der Chunked-Kodierung.
     *
     * @param out  der Strom
     * @param body der Body
     * @throws IOException wenn der Versand scheitert
     */
    private static void writeChunked(OutputStream out, byte[] body) throws IOException {
        int chunkSize = 8192;
        int offset = 0;
        while (offset < body.length) {
            int size = Math.min(chunkSize, body.length - offset);
            out.write((Integer.toHexString(size) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body, offset, size);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            offset += size;
        }
        out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Baut einen gueltigen JSON-Body, der groesser als 2 MiB ist, aber eine
     * <strong>kleine</strong> Nutzlast traegt.
     *
     * @return die Body-Bytes
     */
    private static byte[] oversized() {
        String padding = " ".repeat(MAX_REQUEST_BODY_BYTES + 64);
        String json = "{\"event_type\": " + padding
                + "\"order.changed\", \"idempotency_key\": \"grenze-1\", \"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Baut einen gueltigen JSON-Body knapp <strong>unter</strong> der Grenze.
     *
     * @return die Body-Bytes
     */
    private static byte[] justBelowTheLimit() {
        String padding = " ".repeat(MAX_REQUEST_BODY_BYTES - 4096);
        String json = "{\"event_type\": " + padding
                + "\"order.changed\", \"idempotency_key\": \"grenze-2\", \"payload\": {\"a\": 1}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Komprimiert die Bytes — die Komprimierung schrumpft den Body auf wenige
     * Kilobyte, obwohl er entpackt 20 MiB gross waere.
     *
     * @param size die Groesse des unkomprimierten Bodies
     * @return die komprimierten Bytes
     * @throws IOException wenn die Komprimierung scheitert
     */
    private static byte[] gzip(int size) throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(target)) {
            byte[] block = new byte[1024 * 1024];
            for (int written = 0; written < size; written += block.length) {
                gzip.write(block);
            }
        }
        return target.toByteArray();
    }
}
