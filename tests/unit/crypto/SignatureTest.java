package com.echobox.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Signierte Zustellung (US-009, FR-020).
 *
 * <p>Getestet wird das Verfahren: HMAC-SHA256 ueber
 * {@code <timestamp>.<payload_bytes>}, hex-lowercase, mit dem Versionspraefix
 * {@code v1=}. Der Zeitstempel ist UTC-basiert.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.signature}
 * (eingefroren) und {@code x-delivery-protocol.request.headers.X-EchoBox-Timestamp}.
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-009-1, AC-009-2, AC-009-4, AC-009-5.
 */
class SignatureTest {

    private static final String SECRET = "s3cr3t-rotationswert";
    private static final byte[] PAYLOAD = "{\"order_id\":4711,\"state\":\"shipped\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant TIMESTAMP = Instant.parse("2026-10-21T09:15:22.481Z");

    private final SignatureService signatureService = new SignatureService();

    @Test
    @DisplayName("AC-009-1: der Empfaenger kann die Signatur mit dem Secret nachrechnen")
    void the_receiver_can_recompute_the_signature() {
        String signature = signatureService.sign(SECRET, TIMESTAMP, PAYLOAD);

        assertThat(signatureService.verify(SECRET, TIMESTAMP, PAYLOAD, signature)).isTrue();
    }

    @Test
    @DisplayName("AC-009-2: ein falsches Secret schlaegt fehl")
    void a_wrong_secret_fails_the_verification() {
        String signature = signatureService.sign(SECRET, TIMESTAMP, PAYLOAD);

        assertThat(signatureService.verify("anderes-secret", TIMESTAMP, PAYLOAD, signature)).isFalse();
    }

    @Test
    @DisplayName("AC-009-2: eine veraenderte Nutzlast schlaegt fehl")
    void a_tampered_payload_fails_the_verification() {
        String signature = signatureService.sign(SECRET, TIMESTAMP, PAYLOAD);
        byte[] tampered = "{\"order_id\":4711,\"state\":\"cancelled\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(signatureService.verify(SECRET, TIMESTAMP, tampered, signature)).isFalse();
    }

    @Test
    @DisplayName("AC-009-4: zwei Endpunkte mit derselben Nutzlast erhalten verschiedene Signaturen")
    void two_endpoints_produce_different_signatures_for_the_same_payload() {
        String signatureOfFirst = signatureService.sign("secret-des-ersten-endpunkts", TIMESTAMP, PAYLOAD);
        String signatureOfSecond = signatureService.sign("secret-des-zweiten-endpunkts", TIMESTAMP, PAYLOAD);

        assertThat(signatureOfFirst).isNotEqualTo(signatureOfSecond);
    }

    @Test
    @DisplayName("AC-009-5: die Signatur wird ueber genau die gesendeten Nutzlastbytes gebildet")
    void the_signature_covers_exactly_the_sent_payload_bytes() {
        byte[] signingInput = SignatureService.signingInput(TIMESTAMP, PAYLOAD);

        String expected = String.format("%s.%s",
                DateTimeFormatter.ISO_INSTANT.format(TIMESTAMP.atOffset(ZoneOffset.UTC)),
                new String(PAYLOAD, StandardCharsets.UTF_8));

        assertThat(new String(signingInput, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-009-5: der Zeitstempel ist UTC-basiert und endet auf Z")
    void the_timestamp_is_utc_based() {
        String headerValue = SignatureService.timestampHeaderValue(TIMESTAMP);

        assertThat(headerValue).isEqualTo("2026-10-21T09:15:22.481Z");
        assertThat(headerValue).endsWith("Z");
    }

    @Test
    @DisplayName("AC-009-1: der Headerwert traegt das Versionspraefix und hex-lowercase")
    void the_header_value_carries_the_version_prefix() {
        String signature = signatureService.sign(SECRET, TIMESTAMP, PAYLOAD);

        assertThat(signature).startsWith("v1=");
        assertThat(signature.substring(3)).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("AC-009-2: eine fehlende Signatur wird nicht akzeptiert")
    void a_missing_signature_is_not_accepted() {
        assertThat(signatureService.verify(SECRET, TIMESTAMP, PAYLOAD, null)).isFalse();
        assertThat(signatureService.verify(SECRET, TIMESTAMP, PAYLOAD, "")).isFalse();
        assertThat(signatureService.verify(SECRET, TIMESTAMP, PAYLOAD, "v1=abc")).isFalse();
    }
}
