package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Unit-Ebene — die Antwort des Empfaengers wird **begrenzt** gelesen (S-23).
 *
 * <p>Der Antwortkoerper eines Empfaengers ist vertragsseitig bedeutungslos —
 * nur Statuscode und Ablehnungssignal zaehlen. Ein boesartiger Empfaenger
 * darf den Zustell-Worker aber nicht mit einem riesigen Koerper belasten.
 *
 * <p>Der Test beweist beide Seiten: ein **normaler** Koerper kommt
 * vollstaendig an, ein **riesiger** Koerper wird nach der Grenze verworfen.
 */
class DeliveryResponseLimitTest {

    /** Die Hoechstgroesse, die ein Antwortkoerper eines Empfaengers haben darf. */
    private static final int MAX_RESPONSE_BODY_BYTES = 1024 * 1024;

    @Test
    @DisplayName("S-23: ein normaler Antwortkoerper kommt vollstaendig an")
    void a_normal_response_body_is_delivered_completely() throws Exception {
        String payload = "{\"ok\":true}";
        HttpServer server = serverRespondingWith(payload.getBytes(StandardCharsets.UTF_8));
        try {
            DeliveryHttpClient client = new DeliveryHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(5));
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

            DeliveryResponse response = client.deliver(new DeliveryRequest(
                    target, Map.of(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json"));

            assertThat(response.failure()).isEqualTo(DeliveryResponse.TransportFailure.NONE);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("S-23: ein uebergrosser Antwortkoerper wird nach der Grenze verworfen")
    void an_oversized_response_body_is_discarded_after_the_limit() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024];
        java.util.Arrays.fill(oversized, (byte) 'x');
        HttpServer server = serverRespondingWith(oversized);
        try {
            DeliveryHttpClient client = new DeliveryHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(5));
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

            DeliveryResponse response = client.deliver(new DeliveryRequest(
                    target, Map.of(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json"));

            assertThat(response.body().length())
                    .as("der Antwortkoerper darf nicht unbegrenzt in den Speicher geladen werden")
                    .isLessThanOrEqualTo(MAX_RESPONSE_BODY_BYTES);
            assertThat(response.statusCode())
                    .as("der Statuscode bleibt lesbar — er ist der einzige Bedeutungsinhalt")
                    .isEqualTo(200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("S-23: die Grenze ist konfigurierbar")
    void the_limit_is_configurable() throws Exception {
        byte[] oversized = new byte[64 * 1024];
        java.util.Arrays.fill(oversized, (byte) 'x');
        HttpServer server = serverRespondingWith(oversized);
        try {
            DeliveryHttpClient client = new DeliveryHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(5),
                    1024);
            String target = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

            DeliveryResponse response = client.deliver(new DeliveryRequest(
                    target, Map.of(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json"));

            assertThat(response.body().length()).isLessThanOrEqualTo(1024);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer serverRespondingWith(byte[] body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(body);
            }
        });
        server.start();
        return server;
    }
}
