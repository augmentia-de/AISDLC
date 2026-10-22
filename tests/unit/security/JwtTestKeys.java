package com.echobox.security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpServer;

/**
 * Testhilfe: ein Schluesselpaar, ein JWKS-Endpunkt und die Tokenbausteine fuer
 * die Pruefung des <strong>echten</strong> JWT-Decoders.
 *
 * <p>Die Integrationstests benutzen den Post-Processor {@code jwt()} aus
 * {@code spring-security-test}. Der er setzt eine bereits angemeldete Identitaet
 * — der Decoder, die Signaturpruefung, der Algorithmus und die Ablaufpruefung
 * werden dabei <strong>nicht</strong> durchlaufen. Diese Klasse schliesst genau
 * diese Luecke: sie erzeugt echte JWT (RS256, HS256, {@code none}) und stellt
 * einen echten JWKS-Endpunkt bereit.
 *
 * <p>Der JWKS-Endpunkt laeuft auf einem freien Port des Loopback-Interface —
 * kein Netzzugriff nach aussen.
 */
public final class JwtTestKeys implements AutoCloseable {

    /** Die Kennung des Schluessels im JWKS. */
    public static final String KEY_ID = "k1";

    private final KeyPair keyPair;

    private final KeyPair foreignKeyPair;

    private final HttpServer jwksServer;

    private final String jwkSetUri;

    /**
     * Erzeugt das Schluesselpaar und startet den JWKS-Endpunkt.
     *
     * @throws Exception wenn die Krypto- oder die Server-Erzeugung scheitert
     */
    public JwtTestKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        this.keyPair = generator.generateKeyPair();
        this.foreignKeyPair = generator.generateKeyPair();

        this.jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.jwksServer.createContext("/jwks", exchange -> {
            byte[] body = jwks().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(body);
            }
        });
        this.jwksServer.start();
        this.jwkSetUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    /**
     * Liefert die Adresse des JWKS-Endpunkts.
     *
     * @return die JWKS-Adresse
     */
    public String jwkSetUri() {
        return jwkSetUri;
    }

    /**
     * Liefert das oeffentliche Schluesselmaterial — der geheime Teil des
     * Schluessels wird nirgends offengelegt.
     *
     * @return die Base64-Kodierung des X.509-Schluessels
     */
    public byte[] publicKeyBytes() {
        return keyPair.getPublic().getEncoded();
    }

    /**
     * Erzeugt ein gueltiges Token (RS256) mit den gesuchten Claims.
     *
     * @param claims die Claims
     * @return das Token
     * @throws Exception wenn die Signatur nicht gebildet werden kann
     */
    public String signRs256(Map<String, Object> claims) throws Exception {
        String header = header("RS256");
        String payload = payload(claims);
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature.sign());
    }

    /**
     * Erzeugt ein Token mit dem Algorithmus {@code none} — ohne Signatur.
     *
     * @param claims die Claims
     * @return das Token
     */
    public String signWithNone(Map<String, Object> claims) {
        return header("none") + "." + payload(claims) + ".";
    }

    /**
     * Erzeugt ein Token mit dem Algorithmus {@code HS256}, wobei der HMAC-Schluessel
     * das oeffentliche RSA-Schluesselmaterial ist (Algorithmus-Verwirrung).
     *
     * @param claims die Claims
     * @return das Token
     * @throws Exception wenn der MAC nicht gebildet werden kann
     */
    public String signWithHs256(Map<String, Object> claims) throws Exception {
        String header = header("HS256");
        String payload = payload(claims);
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(publicKeyBytes(), "HmacSHA256"));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Erzeugt ein Token, das mit einem **fremden** Schluesselpaar signiert ist.
     *
     * @param claims die Claims
     * @return das Token
     * @throws Exception wenn die Signatur nicht gebildet werden kann
     */
    public String signWithForeignKey(Map<String, Object> claims) throws Exception {
        String header = header("RS256");
        String payload = payload(claims);
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(foreignKeyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature.sign());
    }

    /**
     * Baut die Claims eines Tokens mit Ablaufzeit.
     *
     * @param organisationId der Mandant
     * @param roles          die Rollen
     * @param expiresAt      der Ablaufzeitpunkt (Sekunden seit Epoch) — {@code null} ohne Ablauf
     * @param audience       die Zielgruppe — {@code null} ohne Zielgruppe
     * @return die Claims
     */
    public static Map<String, Object> claims(String organisationId, java.util.List<String> roles,
                                              Long expiresAt, String audience) {
        Map<String, Object> claims = claimsWithAudiences(organisationId, roles, expiresAt, null);
        if (audience != null) {
            claims.put("aud", audience);
        }
        return claims;
    }

    /**
     * Baut die Claims eines Tokens mit Ablaufzeit und mehreren Zielgruppen.
     *
     * <p>Der Claim {@code aud} darf im JWT eine Liste sein — der Decoder muss
     * beide Formen lesen koennen.
     *
     * @param organisationId der Mandant
     * @param roles          die Rollen
     * @param expiresAt      der Ablaufzeitpunkt (Sekunden seit Epoch) — {@code null} ohne Ablauf
     * @param audiences      die Zielgruppen — {@code null} ohne Zielgruppe
     * @return die Claims
     */
    public static Map<String, Object> claimsWithAudiences(String organisationId, java.util.List<String> roles,
                                                          Long expiresAt, java.util.List<String> audiences) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "tester");
        claims.put("iss", "https://idp.example.invalid");
        claims.put("organisation_id", organisationId);
        claims.put("roles", roles);
        if (expiresAt != null) {
            claims.put("exp", expiresAt);
        }
        if (audiences != null) {
            claims.put("aud", audiences);
        }
        return claims;
    }

    @Override
    public void close() {
        jwksServer.stop(0);
    }

    private String header(String algorithm) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\",\"kid\":\"" + KEY_ID + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String payload(Map<String, Object> claims) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof java.util.Collection<?> collection) {
                json.append('[');
                boolean innerFirst = true;
                for (Object element : collection) {
                    if (!innerFirst) {
                        json.append(',');
                    }
                    innerFirst = false;
                    json.append('"').append(element).append('"');
                }
                json.append(']');
            } else if (value instanceof Number number) {
                json.append(number);
            } else {
                json.append('"').append(value).append('"');
            }
        }
        json.append('}');
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String jwks() {
        java.security.interfaces.RSAPublicKey publicKey =
                (java.security.interfaces.RSAPublicKey) keyPair.getPublic();
        String modulus = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsigned(publicKey.getModulus().toByteArray()));
        String exponent = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsigned(publicKey.getPublicExponent().toByteArray()));
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + KEY_ID
                + "\",\"n\":\"" + modulus + "\",\"e\":\"" + exponent + "\"}]}";
    }

    private static byte[] toUnsigned(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            byte[] trimmed = new byte[value.length - 1];
            System.arraycopy(value, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return value;
    }

    /**
     * Liest den JWKS-Endpunkt — nur zum Nachweis, dass er antwortet.
     *
     * @return der JWKS als Zeichenfolge
     * @throws IOException wenn der Abruf scheitert
     */
    public String fetchJwks() throws IOException {
        return new String(java.net.URI.create(jwkSetUri).toURL().openStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
