package com.echobox.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.config.CoreConfiguration;
import com.echobox.config.EchoBoxProperties;

/**
 * Unit-Ebene — die Schluessellaenge wird geprueft (S-22).
 *
 * <p>ADR-003 §1 legt <strong>AES-256</strong> fest (256 Bit = 32 Byte). Ein
 * 16-Byte-Schluessel wuerde stillschweigend AES-128 bedeuten — die Zusage
 * waere verletzt, ohne dass jemand es bemerkt. Der Dienst startet in diesem
 * Fall <strong>nicht</strong>.
 */
class CryptoKeyLengthTest {

    /** Die Schluessellaenge aus ADR-003 §1: 256 Bit. */
    private static final int AES_256_KEY_BYTES = 32;

    @Test
    @DisplayName("S-22: ein 32-Byte-Schluessel wird angenommen (AES-256)")
    void a_thirty_two_byte_key_is_accepted() {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getCrypto().setKey(Base64.getEncoder().encodeToString(new byte[AES_256_KEY_BYTES]));
        properties.getCrypto().setKeyRequired(true);

        SecretKey key = new CoreConfiguration().payloadEncryptionKey(properties);

        assertThat(key.getEncoded())
                .as("ADR-003 §1: 256 Bit")
                .hasSize(AES_256_KEY_BYTES);
        assertThat(key.getAlgorithm()).isEqualTo("AES");
    }

    @Test
    @DisplayName("S-22: ein 16-Byte-Schluessel verhindert den Start (kein stilles AES-128)")
    void a_sixteen_byte_key_is_refused() {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getCrypto().setKey(Base64.getEncoder().encodeToString(new byte[16]));
        properties.getCrypto().setKeyRequired(true);

        assertThatThrownBy(() -> new CoreConfiguration().payloadEncryptionKey(properties))
                .as("ein 16-Byte-Schluessel wuerde stillschweigend AES-128 bedeuten")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("S-22: ein 64-Byte-Schluessel verhindert den Start (kein AES-512 stillschweigend)")
    void a_sixty_four_byte_key_is_refused() {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getCrypto().setKey(Base64.getEncoder().encodeToString(new byte[64]));
        properties.getCrypto().setKeyRequired(true);

        assertThatThrownBy(() -> new CoreConfiguration().payloadEncryptionKey(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("S-22: der ephemere Schluessel (ohne Konfiguration) hat ebenfalls 32 Byte")
    void the_ephemeral_key_has_the_same_length() {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getCrypto().setKey("");
        properties.getCrypto().setKeyRequired(false);

        SecretKey key = new CoreConfiguration().payloadEncryptionKey(properties);

        assertThat(key.getEncoded()).hasSize(AES_256_KEY_BYTES);
    }
}
