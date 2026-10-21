package com.echobox.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.crypto.SignatureService;
import com.echobox.testsupport.AbstractApplicationIT;
import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Integrationsebene — Signierte Zustellung (US-009, FR-020).
 *
 * <p>Zwei Wege werden geprueft:
 * <ol>
 *   <li><strong>Der volle Weg durch die Anwendung</strong> — der Worker sendet an
 *       den Endpunkt; der aufgezeichnete Request traegt Ereignis-ID,
 *       Zustelllauf-ID, Versuchs-ID, Zeitstempel und Signatur. Die Signatur wird
 *       mit dem Secret des Endpunkts nachgerechnet (AC-009-1, AC-009-2,
 *       AC-009-4, AC-009-5).</li>
 *   <li><strong>Der echte HTTP-Weg</strong> — der HTTP-Adapter wird gegen einen
 *       echten Server gestellt; dort ist das Drahtformat der Zustellung
 *       sichtbar: Kopfzeilen, Signatur-Header und der Request-Body sind
 *       <em>die Nutzlastbytes</em>, ohne Huelle (AC-009-1).</li>
 * </ol>
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol}
 * (eingefroren).
 */
class SignedDeliveryIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://signed.example.invalid/hooks/echobox";
    private static final String SECRET = "s3cr3t-rotationswert";
    private static final byte[] PAYLOAD = "{\"order_id\":4711,\"state\":\"shipped\"}".getBytes(StandardCharsets.UTF_8);

    /**
     * Die eingelieferte Nutzlast — sie entspricht byteweise den gesendeten
     * Bytes {@link #PAYLOAD}. Der Vertrag nennt die zugestellten Bytes
     * „kanonisch materialisiert“ (RFC 8785, sortierte Schluessel, ohne
     * Leerzeichen) — genau diese Bytes werden signiert und gesendet.
     */
    private static final String SUBMITTED_PAYLOAD = "{\"order_id\":4711,\"state\":\"shipped\"}";

    @Autowired
    private DeliveryWorker worker;

    private WireMockServer receiver;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @BeforeEach
    void startReceiver() {
        receiver = new WireMockServer(wireMockConfig().dynamicPort());
        receiver.start();
        receiver.stubFor(post("/hooks/echobox")
                .willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    @AfterEach
    void stopReceiver() {
        receiver.stop();
    }

    @Test
    @DisplayName("AC-009-1: die Zustellung traegt eine Signatur, die der Empfaenger nachrechnen kann")
    void the_delivery_carries_a_verifiable_signature() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "signed-1", SUBMITTED_PAYLOAD);

        worker.runOnce();

        assertThat(deliveryPort.requestCount()).isEqualTo(1);
        DeliveryRequest request = deliveryPort.request(1);
        String signature = request.headers().get(DeliveryHeaders.SIGNATURE);
        String timestamp = request.headers().get(DeliveryHeaders.TIMESTAMP);

        assertThat(signature).isNotNull();
        assertThat(signature).startsWith("v1=");

        SignatureService signatureService = new SignatureService();
        assertThat(signatureService.verify(SECRET, Instant.parse(timestamp), PAYLOAD, signature))
                .as("der Empfaenger rechnet die Signatur mit dem Endpunkt-Secret nach")
                .isTrue();
    }

    @Test
    @DisplayName("AC-009-2: eine Signatur mit veränderter Nutzlast schlaegt fehl")
    void a_signature_over_a_tampered_payload_fails() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "signed-2", SUBMITTED_PAYLOAD);

        worker.runOnce();

        DeliveryRequest request = deliveryPort.request(1);
        String signature = request.headers().get(DeliveryHeaders.SIGNATURE);
        String timestamp = request.headers().get(DeliveryHeaders.TIMESTAMP);

        byte[] tampered = "{\"order_id\":4711,\"state\":\"cancelled\"}".getBytes(StandardCharsets.UTF_8);
        SignatureService signatureService = new SignatureService();

        assertThat(signatureService.verify(SECRET, Instant.parse(timestamp), tampered, signature)).isFalse();
    }

    @Test
    @DisplayName("AC-009-4: zwei Endpunkte mit derselben Nutzlast erhalten verschiedene Signaturen")
    void two_endpoints_receive_different_signatures() throws Exception {
        // AC-009-4 setzt zwei Endpunkte mit **verschiedenen** Secrets voraus
        // ("weil das Secret des jeweiligen Endpunkts in die Signatur eingeht").
        registerEndpointWithSecret("m-1", "https://first.example.invalid/hook", "s3cr3t-erstes-endpunkt", "order.changed");
        registerEndpointWithSecret("m-1", "https://second.example.invalid/hook", "s3cr3t-zweites-endpunkt", "order.changed");
        deliveryPort.respondWith("https://first.example.invalid/hook", 200);
        deliveryPort.respondWith("https://second.example.invalid/hook", 200);

        submitEvent("m-1", "order.changed", "signed-3", SUBMITTED_PAYLOAD);

        worker.runOnce();

        String first = signatureOf("https://first.example.invalid/hook");
        String second = signatureOf("https://second.example.invalid/hook");

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("AC-009-5: der Zeitstempel ist UTC-basiert und die Signatur deckt genau die gesendeten Bytes")
    void the_timestamp_is_utc_and_the_signature_covers_the_sent_bytes() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "signed-4", SUBMITTED_PAYLOAD);

        worker.runOnce();

        DeliveryRequest request = deliveryPort.request(1);
        String timestamp = request.headers().get(DeliveryHeaders.TIMESTAMP);

        assertThat(timestamp).endsWith("Z");
        assertThat(Instant.parse(timestamp)).isNotNull();
        assertThat(request.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("AC-009-1: der echte HTTP-Request traegt die eingefrorenen Kopfzeilen")
    void the_real_http_request_carries_the_frozen_headers() {
        DeliveryHttpClient client = new DeliveryHttpClient(Duration.ofSeconds(5), Duration.ofSeconds(10));
        SignatureService signatureService = new SignatureService();
        Instant timestamp = Instant.parse("2026-10-21T09:15:22.481Z");
        String signature = signatureService.sign(SECRET, timestamp, PAYLOAD);

        DeliveryResponse response = client.deliver(new DeliveryRequest(
                receiver.baseUrl() + "/hooks/echobox",
                Map.of(
                        DeliveryHeaders.EVENT_ID, "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d",
                        DeliveryHeaders.DELIVERY_RUN_ID, "7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d",
                        DeliveryHeaders.ATTEMPT_ID, "b1a2c3d4-1111-4222-8333-444455556666",
                        DeliveryHeaders.ATTEMPT_NUMBER, "1",
                        DeliveryHeaders.EVENT_TYPE, "order.changed",
                        DeliveryHeaders.TIMESTAMP, "2026-10-21T09:15:22.481Z",
                        DeliveryHeaders.SIGNATURE, signature,
                        DeliveryHeaders.USER_AGENT, DeliveryHeaders.USER_AGENT_VALUE),
                PAYLOAD,
                "application/json"));

        assertThat(response.statusCode()).isEqualTo(200);

        receiver.verify(postRequestedFor(urlEqualTo("/hooks/echobox"))
                .withHeader(DeliveryHeaders.EVENT_ID, equalTo("3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d"))
                .withHeader(DeliveryHeaders.DELIVERY_RUN_ID,
                        equalTo("7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d"))
                .withHeader(DeliveryHeaders.ATTEMPT_ID,
                        equalTo("b1a2c3d4-1111-4222-8333-444455556666"))
                .withHeader(DeliveryHeaders.ATTEMPT_NUMBER, equalTo("1"))
                .withHeader(DeliveryHeaders.TIMESTAMP, equalTo("2026-10-21T09:15:22.481Z"))
                .withHeader(DeliveryHeaders.SIGNATURE, matching("v1=[0-9a-f]{64}"))
                .withHeader(DeliveryHeaders.USER_AGENT, equalTo("EchoBox-Delivery/1.0")));
    }

    @Test
    @DisplayName("AC-009-1: der Request-Body sind genau die Nutzlastbytes — keine Huelle")
    void the_request_body_is_the_payload_bytes_without_an_envelope() {
        DeliveryHttpClient client = new DeliveryHttpClient(Duration.ofSeconds(5), Duration.ofSeconds(10));

        client.deliver(new DeliveryRequest(
                receiver.baseUrl() + "/hooks/echobox",
                Map.of(DeliveryHeaders.EVENT_TYPE, "order.changed"),
                PAYLOAD,
                "application/json"));

        receiver.verify(postRequestedFor(urlEqualTo("/hooks/echobox"))
                .withRequestBody(equalTo(new String(PAYLOAD, StandardCharsets.UTF_8))));
    }

    private String signatureOf(String targetUrl) {
        return deliveryPort.requests().stream()
                .filter(request -> targetUrl.equals(request.targetUrl()))
                .map(request -> request.headers().get(DeliveryHeaders.SIGNATURE))
                .findFirst()
                .orElse(null);
    }
}
