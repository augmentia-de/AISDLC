package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Bereits verschluesselte Einlieferung (US-008, FR-013).
 *
 * <p>Getestet wird die Wertelogik der Nutzlast: bei
 * {@code payload_encoding = pre_encrypted} werden die Bytes unveraendert
 * uebernommen — gespeichert und zugestellt wird exakt das Gelieferte, ohne
 * vorheriges Entschlüsseln. Die Groessengrenze gilt fuer dieselben Bytes.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code EventIntakeRequest.payload_encoding} und
 * {@code x-delivery-protocol.request.body}.
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-008-1, AC-008-3.
 */
class PreEncryptedEventTest {

    private static final byte[] CIPHRE = "AEAD-Chiffre-ohne-Klartextanteil".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("AC-008-1: vorverschluesselte Bytes werden unveraendert uebernommen")
    void pre_encrypted_bytes_are_taken_over_unchanged() {
        EventPayload payload = EventPayload.preEncrypted(CIPHRE);

        assertThat(payload.encoding()).isEqualTo(PayloadEncoding.PRE_ENCRYPTED);
        assertThat(payload.bytes()).isEqualTo(CIPHRE);
        assertThat(payload.sizeBytes()).isEqualTo(CIPHRE.length);
    }

    @Test
    @DisplayName("AC-008-1: eine vorverschluesselte Nutzlast wird nicht entschluesselt")
    void pre_encrypted_payload_is_not_decrypted() {
        EventPayload payload = EventPayload.preEncrypted(CIPHRE);

        assertThat(payload.isPreEncrypted()).isTrue();
        // `doesNotContain(byte[])` prueft, ob **keines** der Einzelbytes
        // vorkommt — das waere hier nicht erfuellbar, weil der Fixture-Wert
        // z. B. das Byte 'A' enthaelt. Geprueft wird deshalb, ob die
        // Klartextmarke **als Zeichenfolge** in den Bytes enthalten ist
        // (gleiche Messgroesse wie in PayloadCipherTest und PreEncryptedEventIT).
        assertThat(new String(payload.bytes(), StandardCharsets.ISO_8859_1))
                .doesNotContain("TOP-SECRET-PAYLOAD");
    }

    @Test
    @DisplayName("AC-008-3: dieselbe Grenze gilt fuer die Chiffre")
    void the_same_size_limit_applies_to_the_ciphertext() {
        byte[] atTheLimit = new byte[PayloadSize.MAX_PAYLOAD_BYTES];
        byte[] oneByteOver = new byte[PayloadSize.MAX_PAYLOAD_BYTES + 1];

        assertThat(PayloadSize.exceedsPayloadLimit(EventPayload.preEncrypted(atTheLimit).sizeBytes())).isFalse();
        assertThat(PayloadSize.exceedsPayloadLimit(EventPayload.preEncrypted(oneByteOver).sizeBytes())).isTrue();
    }

    @Test
    @DisplayName("AC-008-1: eine Klartextnutzlast wird als Klartext gekennzeichnet")
    void plaintext_payload_is_marked_as_plaintext() {
        EventPayload payload = EventPayload.plaintext("{\"order_id\":4711}".getBytes(StandardCharsets.UTF_8));

        assertThat(payload.encoding()).isEqualTo(PayloadEncoding.PLAINTEXT);
        assertThat(payload.isPreEncrypted()).isFalse();
    }
}
