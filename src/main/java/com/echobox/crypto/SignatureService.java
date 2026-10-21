package com.echobox.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Die Signatur der ausgehenden Zustellung (ADR-004, FR-020).
 *
 * <p>Verfahren: <strong>HMAC-SHA256</strong> ueber die Signier-Eingabe
 * {@code <timestamp>.<payload_bytes>}. Der Headerwert traegt den
 * Versionspraefix {@code v1=} und den Hexwert in Kleinschreibung. Der
 * Zeitstempel ist UTC-basiert und endet auf {@code Z}.
 *
 * <p>Der Vergleich der Hexwerte ist <strong>zeitkonstant</strong>
 * ({@link MessageDigest#isEqual(byte[], byte[])}) — ein Angreifer kann aus der
 * Laufzeit keine Information ueber den Guettwert gewinnen.
 *
 * <p>Verfahren und Header-Name sind <strong>eingefroren</strong>
 * ({@code docs/api/openapi.yaml} -&gt; {@code x-delivery-protocol.signature}).
 */
public class SignatureService {

    /** Die MAC-Transformation. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Der Versionspraefix des Headerwerts — Teil der eingefrorenen Konvention. */
    private static final String VALUE_PREFIX = "v1=";

    /**
     * Berechnet die Signatur.
     *
     * @param secret    das Endpunkt-Secret des Empfaengers
     * @param timestamp der Zeitpunkt der Zustellung
     * @param payload   die gesendeten Nutzlastbytes
     * @return der Headerwert {@code v1=<hex-lowercase>}
     */
    public String sign(String secret, Instant timestamp, byte[] payload) {
        byte[] mac = hmac(secret, signingInput(timestamp, payload));
        return VALUE_PREFIX + HexFormat.of().formatHex(mac);
    }

    /**
     * Prueft eine Signatur — <strong>zeitkonstant</strong>.
     *
     * @param secret      das Endpunkt-Secret des Empfaengers
     * @param timestamp   der Zeitpunkt der Zustellung
     * @param payload     die gesendeten Nutzlastbytes
     * @param headerValue der Wert des Signatur-Headers
     * @return {@code true}, wenn die Signatur zum Secret und zur Nutzlast passt
     */
    public boolean verify(String secret, Instant timestamp, byte[] payload, String headerValue) {
        if (headerValue == null || headerValue.isEmpty() || !headerValue.startsWith(VALUE_PREFIX)) {
            return false;
        }
        byte[] provided = decodeHex(headerValue.substring(VALUE_PREFIX.length()));
        if (provided == null) {
            return false;
        }
        byte[] expected = hmac(secret, signingInput(timestamp, payload));
        return MessageDigest.isEqual(expected, provided);
    }

    /**
     * Die Signier-Eingabe: der Zeitstempel, ein Punkt, dann die exakt gesendeten
     * Nutzlastbytes — <strong>ohne</strong> weitere Normalisierung.
     *
     * @param timestamp der Zeitpunkt der Zustellung
     * @param payload   die gesendeten Nutzlastbytes
     * @return die Signier-Eingabe
     */
    public static byte[] signingInput(Instant timestamp, byte[] payload) {
        byte[] prefix = timestampHeaderValue(timestamp).getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[prefix.length + 1 + payload.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        input[prefix.length] = '.';
        System.arraycopy(payload, 0, input, prefix.length + 1, payload.length);
        return input;
    }

    /**
     * Der Wert des Zeitstempel-Headers — ISO-8601 in UTC, endet auf {@code Z}.
     *
     * @param timestamp der Zeitpunkt der Zustellung
     * @return der Headerwert
     */
    public static String timestampHeaderValue(Instant timestamp) {
        return DateTimeFormatter.ISO_INSTANT.format(timestamp.atOffset(ZoneOffset.UTC));
    }

    private static byte[] hmac(String secret, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Die Signatur konnte nicht gebildet werden.", e);
        }
    }

    private static byte[] decodeHex(String value) {
        if (value.isEmpty() || (value.length() % 2) != 0) {
            return null;
        }
        try {
            return HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
