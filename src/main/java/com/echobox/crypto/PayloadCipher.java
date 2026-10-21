package com.echobox.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AEAD-Chiffre fuer die Nutzlast im Ruhezustand (ADR-003).
 *
 * <p>Verfahren: <strong>AES-256-GCM</strong> mit einer <strong>zufaelligen
 * Nonce von 12 Byte je Verschluesselung</strong> und einem Authentikationstag
 * von 128 Bit. Die Chiffre enthaelt <strong>keinen Klartextanteil</strong> —
 * sie wird ausschliesslich verschluesselt abgelegt (FR-012, NFR-004).
 *
 * <p>Die Nonce steht <strong>nicht</strong> in der Chiffre, sondern getrennt in
 * der Spalte {@code payload_nonce} bzw. {@code secret_nonce}. Deshalb liefert
 * {@link Ciphertext#bytes()} die Chiffre ohne Nonce und {@link Ciphertext#nonce()}
 * die Nonce.
 *
 * <p><strong>Fingerprint (ADR-007):</strong> der Vergleich „gleiche oder andere
 * Nutzlast“ laeuft ueber einen <strong>gezielten HMAC-SHA256</strong> ueber die
 * Nutzlastbytes — nicht ueber einen ungeschuetzten Hash. Der Fingerprint
 * enthaelt keinen Klartextanteil und ist ausschliesslich zum Vergleich
 * geeignet. Ohne eigenen Fingerprint-Schluessel wird der Teilschluessel aus
 * dem Hauptschluessel abgeleitet.
 *
 * <p>Die Instanz ist <strong>threadsicher</strong>: je Aufruf entsteht eine
 * neue {@link Cipher}-Instanz, der Zufallsgenerator ist threadsicher.
 */
public class PayloadCipher {

    /** Die Transformation — AEAD ohne Padding. */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Die MAC-Transformation des Fingerprints. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Laenge der Nonce in Byte (AEAD-Standard). */
    private static final int NONCE_LENGTH = 12;

    /** Laenge des Authentikationstags in Bit. */
    private static final int TAG_LENGTH_BITS = 128;

    /** Laenge des Fingerprints in Byte (SHA-256). */
    private static final int FINGERPRINT_LENGTH = 32;

    /** Die Information, aus der der Teilschluessel fuer den Fingerprint abgeleitet wird. */
    private static final byte[] FINGERPRINT_KEY_INFO =
            "echobox-payload-fingerprint-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKey key;

    private final SecretKey fingerprintKey;

    private final SecureRandom random = new SecureRandom();

    /**
     * Erzeugt die Chiffre.
     *
     * @param key der Verschluesselungsschluessel (32 Byte fuer AES-256)
     */
    public PayloadCipher(SecretKey key) {
        this.key = key;
        this.fingerprintKey = deriveFingerprintKey(key);
    }

    /**
     * Verschluesselt die Nutzlast.
     *
     * @param plaintext die Klartextbytes
     * @return die Chiffre ohne Nonce und die Nonce
     */
    public Ciphertext encrypt(byte[] plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new Ciphertext(cipher.doFinal(plaintext), nonce);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Die Nutzlast konnte nicht verschluesselt werden.", e);
        }
    }

    /**
     * Entschluesselt die Nutzlast.
     *
     * @param ciphertext die Chiffre ohne Nonce und die Nonce
     * @return die Klartextbytes
     */
    public byte[] decrypt(Ciphertext ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, ciphertext.nonce()));
            return cipher.doFinal(ciphertext.bytes());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Die Nutzlast konnte nicht entschluesselt werden.", e);
        }
    }

    /**
     * Bildet den Fingerprint der Nutzlast — einen gezielten HMAC-SHA256 ueber
     * die Nutzlastbytes (ADR-007).
     *
     * <p>Der Fingerprint ist 32 Byte lang, enthaelt keinen Klartextanteil und
     * dient ausschliesslich dem Vergleich: gleicher Idempotenzschluessel mit
     * abweichender Nutzlast ergibt HTTP 409.
     *
     * @param payload die Nutzlastbytes
     * @return der Fingerprint
     */
    public byte[] fingerprint(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(fingerprintKey);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Der Nutzlast-Fingerprint konnte nicht gebildet werden.", e);
        }
    }

    private static SecretKey deriveFingerprintKey(SecretKey masterKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(masterKey);
            byte[] derived = mac.doFinal(FINGERPRINT_KEY_INFO);
            return new SecretKeySpec(derived, HMAC_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Der Fingerprint-Schluessel konnte nicht abgeleitet werden.", e);
        }
    }

    /**
     * Das Ergebnis einer Verschluesselung: die Chiffre <strong>ohne</strong> Nonce
     * und die Nonce getrennt.
     *
     * @param bytes die Chiffre ohne Nonce
     * @param nonce die Nonce (12 Byte)
     */
    public record Ciphertext(byte[] bytes, byte[] nonce) {

        /**
         * Erzeugt ein Ergebnis mit kopierten Feldern — der Aufrufer kann die
         * Byte-Arrays nicht nachtraeglich veraendern.
         *
         * @param bytes die Chiffre ohne Nonce
         * @param nonce die Nonce
         */
        public Ciphertext {
            bytes = bytes.clone();
            nonce = nonce.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Ciphertext that
                    && MessageDigest.isEqual(bytes, that.bytes)
                    && MessageDigest.isEqual(nonce, that.nonce);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(bytes) * 31 + java.util.Arrays.hashCode(nonce);
        }

        @Override
        public String toString() {
            return "Ciphertext[bytes=" + bytes.length + ", nonce=" + nonce.length + "]";
        }
    }
}
