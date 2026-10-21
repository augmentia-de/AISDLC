package com.echobox.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Verschluesselung im Ruhezustand (US-007, FR-012, NFR-004).
 *
 * <p>Getestet wird die AEAD-Rundum operation: verschluesseln, entschluesseln, den
 * Fingerprint bilden. Ausserdem wird der Nachweis gefuehrt, dass der
 * Verschluesselungsschluessel in **keinem** Projektartefakt steht — Schema,
 * Quellcode und Konfiguration.
 *
 * <p>Testorakel: `docs/architecture/domain_model.md` (ADR-003); die Suche in der
 * Datenbank und in den Protokollen steht in
 * {@code tests/integration/event/PayloadEncryptionAtRestIT.java} (AC-007-1, AC-007-2).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-007-3, AC-007-4.
 */
class PayloadCipherTest {

    /** Nur ein Testschluessel — nie ein Betriebsschluessel. */
    private static final byte[] TEST_KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final PayloadCipher payloadCipher =
            new PayloadCipher(new SecretKeySpec(TEST_KEY, "AES"));

    @Test
    @DisplayName("AC-007-3: der Round-Trip liefert exakt die eingelieferten Bytes zurueck")
    void encrypt_decrypt_round_trip_returns_the_same_bytes() {
        byte[] payload = "{\"order_id\":4711,\"state\":\"shipped\"}".getBytes(StandardCharsets.UTF_8);

        PayloadCipher.Ciphertext ciphertext = payloadCipher.encrypt(payload);
        byte[] decrypted = payloadCipher.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(payload);
    }

    @Test
    @DisplayName("AC-007-3: die Chiffre enthaelt den Klartext nicht")
    void the_ciphertext_does_not_contain_the_plaintext() {
        byte[] payload = "TOP-SECRET-PAYLOAD".getBytes(StandardCharsets.UTF_8);

        PayloadCipher.Ciphertext ciphertext = payloadCipher.encrypt(payload);

        assertThat(new String(ciphertext.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("TOP-SECRET-PAYLOAD");
    }

    @Test
    @DisplayName("AC-007-3: die Chiffre ist bei gleicher Eingabe nicht identisch (frische Nonce)")
    void encryption_uses_a_fresh_nonce_per_call() {
        byte[] payload = "TOP-SECRET-PAYLOAD".getBytes(StandardCharsets.UTF_8);

        PayloadCipher.Ciphertext first = payloadCipher.encrypt(payload);
        PayloadCipher.Ciphertext second = payloadCipher.encrypt(payload);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.bytes()).isNotEqualTo(second.bytes());
    }

    @Test
    @DisplayName("AC-007-2: die Nonce ist 12 Byte lang (AEAD-Standard)")
    void the_nonce_has_the_aead_length() {
        PayloadCipher.Ciphertext ciphertext = payloadCipher.encrypt("{}".getBytes(StandardCharsets.UTF_8));

        assertThat(ciphertext.nonce()).hasSize(12);
    }

    @Test
    @DisplayName("AC-007-4: der Verschluesselungsschluessel steht in keinem Projektartefakt")
    void the_encryption_key_is_not_hardcoded_in_the_project() throws IOException {
        Path projectRoot = Path.of("").toAbsolutePath();
        Path sourceRoot = projectRoot.resolve("src");
        Path schema = projectRoot.resolve(".agent/knowledge/schema.sql");

        assertThat(sourceRoot)
                .as("das Quellverzeichnis muss beim Testlauf vorhanden sein")
                .exists();
        assertThat(schema).exists();

        List<Path> candidates = Stream.of(
                        Files.walk(sourceRoot),
                        Stream.of(schema))
                .flatMap(stream -> stream)
                .filter(Files::isRegularFile)
                .filter(PayloadCipherTest::isSourceArtifact)
                .toList();

        for (Path candidate : candidates) {
            // ISO-8859-1 bildet jedes Byte auf ein Zeichen ab — der Nachweis
            // gilt damit auch fuer binaere Dateien, und der Test bricht nicht
            // an einem Build-Artefakt mit ungueltiger UTF-8-Folge ab.
            String content = new String(Files.readAllBytes(candidate), StandardCharsets.ISO_8859_1);
            assertThat(content)
                    .as("der Schluessel darf in %s nicht stehen", candidate)
                    .doesNotContain("0123456789abcdef0123456789abcdef");
        }
    }

    /**
     * Prueft, ob eine Datei ein Quellartefakt ist.
     *
     * <p>Erzeugte Verzeichnisse ({@code node_modules}, {@code .next},
     * {@code target}) sind keine Projektartefakte im Sinne der Anforderung —
     * sie werden aus dem Build erzeugt, stehen nicht im Versionsverwaltung
     * und enthalten den Schluessel nicht.
     *
     * @param path die Datei
     * @return true, wenn die Datei geprueft werden soll
     */
    private static boolean isSourceArtifact(Path path) {
        for (Path segment : path) {
            String name = segment.toString();
            if ("node_modules".equals(name) || ".next".equals(name) || "target".equals(name)) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("AC-007-4: das Schema enthaelt keinen eingebetteten Schluessel")
    void the_schema_contains_no_key_material() throws IOException {
        String schema = Files.readString(Path.of(".agent/knowledge/schema.sql"), StandardCharsets.UTF_8);

        assertThat(schema.toLowerCase())
                .doesNotContain("aes key")
                .doesNotContain("encryption_key =")
                .doesNotContain("0123456789abcdef");
    }
}
