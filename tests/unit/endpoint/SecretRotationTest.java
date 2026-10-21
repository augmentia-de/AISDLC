package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.crypto.SignatureService;

/**
 * Unit-Ebene — Secret-Rotation (US-003, FR-003).
 *
 * <p>Getestet wird die kryptografische Wirkung der Rotation: ab dem Wechsel gilt
 * ausschliesslich das neue Secret. Die Signatur wird ueber
 * {@code <timestamp>.<payload_bytes>} gebildet; eine Pruefung mit dem alten Secret
 * muss fehlschlagen, eine mit dem neuen Secret muss gelingen.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.signature}
 * (Verfahren und Header-Name eingefroren).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-003-1, AC-003-4.
 */
class SecretRotationTest {

    private static final String OLD_SECRET = "s3cr3t-rotationswert-01";
    private static final String NEW_SECRET = "neues-s3cr3t-rotationswert-02";
    private static final byte[] PAYLOAD = "{\"order_id\":4711}".getBytes(StandardCharsets.UTF_8);

    private final SignatureService signatureService = new SignatureService();

    @Test
    @DisplayName("AC-003-1: nach der Rotation wird ausschliesslich mit dem neuen Secret signiert")
    void after_rotation_the_signature_is_produced_with_the_new_secret_only() {
        Instant timestamp = Instant.parse("2026-10-21T12:05:11.204Z");

        String signatureAfterRotation = signatureService.sign(NEW_SECRET, timestamp, PAYLOAD);

        assertThat(signatureService.verify(NEW_SECRET, timestamp, PAYLOAD, signatureAfterRotation))
                .as("der Empfaenger kann mit dem neuen Secret nachrechnen")
                .isTrue();
        assertThat(signatureService.verify(OLD_SECRET, timestamp, PAYLOAD, signatureAfterRotation))
                .as("das alte Secret bestaetigt die neue Signatur nicht mehr")
                .isFalse();
    }

    @Test
    @DisplayName("AC-003-4: die Signatur mit S2 wird bestaetigt, die mit S1 schlaegt fehl")
    void signature_verification_follows_the_rotated_secret() {
        Instant timestamp = Instant.parse("2026-10-21T12:05:11.204Z");

        String signatureOverOldSecret = signatureService.sign(OLD_SECRET, timestamp, PAYLOAD);
        String signatureOverNewSecret = signatureService.sign(NEW_SECRET, timestamp, PAYLOAD);

        assertThat(signatureOverOldSecret).isNotEqualTo(signatureOverNewSecret);
        assertThat(signatureService.verify(OLD_SECRET, timestamp, PAYLOAD, signatureOverOldSecret)).isTrue();
        assertThat(signatureService.verify(NEW_SECRET, timestamp, PAYLOAD, signatureOverNewSecret)).isTrue();
        assertThat(signatureService.verify(NEW_SECRET, timestamp, PAYLOAD, signatureOverOldSecret))
                .as("mit S1 signiert, mit S2 geprueft -> die Pruefung schlaegt fehl")
                .isFalse();
    }

    @Test
    @DisplayName("AC-003-1: die Signatur traegt das Versionspraefix v1=")
    void signature_carries_the_version_prefix() {
        String signature = signatureService.sign(NEW_SECRET, Instant.parse("2026-10-21T12:05:11.204Z"), PAYLOAD);

        assertThat(signature).startsWith("v1=");
        assertThat(signature.substring("v1=".length()))
                .as("hex-lowercase")
                .matches("[0-9a-f]{64}");
    }
}
