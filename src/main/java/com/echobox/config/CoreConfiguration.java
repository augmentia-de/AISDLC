package com.echobox.config;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.echobox.common.ApiRateLimiter;
import com.echobox.common.ClockPort;
import com.echobox.common.CursorCodec;
import com.echobox.crypto.PayloadCipher;
import com.echobox.crypto.SignatureService;

/**
 * Die Beans des Fundaments: Uhr, Verschluesselung, Cursor.
 *
 * <p>Der **Verschluesselungsschluessel** kommt aus der Umgebung, **nicht** aus
 * der Datenbank (ADR-003, NFR-004) und **nicht** aus einer Konfigurationsdatei.
 * Ist er nicht gesetzt, erzeugt der Dienst einen **ephemeralen** Schluessel aus
 * einer kryptografisch sicheren Zufallsquelle und warnt — im Staging- und
 * Produktivbetrieb ist das ein Fehler ({@code echobox.crypto.key-required}),
 * weil ein ephemerer Schluessel die Daten nach jedem Neustart unlesbar machen
 * wuerde.
 */
@Configuration
public class CoreConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CoreConfiguration.class);

    private static final int AES_256_KEY_BYTES = 32;

    /**
     * Die Systemuhr. Im Test ersetzt eine steuerbare Uhr diese Bean.
     *
     * @return die Uhr
     */
    @Bean
    public ClockPort systemClock() {
        return Instant::now;
    }

    /**
     * Der Verschluesselungsschluessel der Nutzlast und der Endpunkt-Secrets.
     *
     * @param properties die Betriebskonfiguration
     * @return der Schluessel
     */
    @Bean
    public SecretKey payloadEncryptionKey(EchoBoxProperties properties) {
        String configured = properties.getCrypto().getKey();
        if (configured == null || configured.isBlank()) {
            if (properties.getCrypto().isKeyRequired()) {
                throw new IllegalStateException(
                        "Der Verschluesselungsschluessel fehlt (ECHobox_CRYPTO_KEY). "
                                + "Ohne ihn waere die Nutzlasschiffre nach einem Neustart unlesbar.");
            }
            byte[] ephemeral = new byte[AES_256_KEY_BYTES];
            new SecureRandom().nextBytes(ephemeral);
            LOG.warn("Kein Verschluesselungsschluessel konfiguriert — es wird ein ephemerer Schluessel "
                    + "erzeugt. Die Nutzlast ist damit nur zur Laufzeit lesbar.");
            return new SecretKeySpec(ephemeral, "AES");
        }
        byte[] key = decode(configured);
        if (key.length != AES_256_KEY_BYTES) {
            // ADR-003 §1 legt 256 Bit fest. Ein kuerzerer Schluessel wuerde
            // stillschweigend AES-128 bedeuten — der Dienst startet dann nicht.
            throw new IllegalStateException(
                    "Der Verschluesselungsschluessel muss genau " + AES_256_KEY_BYTES
                            + " Byte (256 Bit, AES-256) lang sein, hat aber " + key.length
                            + " Byte. ADR-003 §1 legt AES-256 fest.");
        }
        return new SecretKeySpec(key, "AES");
    }

    /**
     * Die Verschluesselung der Nutzlast und der Secrets (AES-256-GCM).
     *
     * @param payloadEncryptionKey der Schluessel aus der Umgebung
     * @return die Verschluesselung
     */
    @Bean
    public PayloadCipher payloadCipher(SecretKey payloadEncryptionKey) {
        return new PayloadCipher(payloadEncryptionKey);
    }

    /**
     * Der Zaehler des Anfragelimits der API (S-24).
     *
     * <p>Der Vertrag sagt HTTP 429 {@code rate_limit_exceeded} mit
     * {@code Retry-After} fuer jede Operation zu. Der Zaehler begrenzt die
     * Anfragen je Mandant und Fenster; die Werte kommen aus der
     * Betriebskonfiguration ({@code echobox.rate-limit.*}).
     *
     * @param properties die Betriebskonfiguration
     * @param clock      die Uhr
     * @return der Zaehler
     */
    @Bean
    public ApiRateLimiter apiRateLimiter(EchoBoxProperties properties, ClockPort clock) {
        return new ApiRateLimiter(properties.getRateLimit().getLimit(),
                Duration.ofSeconds(properties.getRateLimit().getWindowSeconds()), clock);
    }

    /**
     * Der Codec der opaken Cursor.
     *
     * @param properties die Betriebskonfiguration
     * @return der Codec
     */
    @Bean
    public CursorCodec cursorCodec(EchoBoxProperties properties) {
        return new CursorCodec(properties.getSecurity().getCursorKey());
    }

    /**
     * Die Signatur der ausgehenden Zustellung (ADR-004).
     *
     * <p>Die Signatur ist HMAC-SHA256 ueber {@code <timestamp>.<payload_bytes>}
     * mit dem Endpunkt-Secret des Empfaengers.
     *
     * @return die Signatur
     */
    @Bean
    public SignatureService signatureService() {
        return new SignatureService();
    }

    private static byte[] decode(String configured) {
        try {
            return Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException ignored) {
            try {
                return java.util.HexFormat.of().parseHex(configured);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Der Verschluesselungsschluessel muss Base64 oder Hex kodiert sein.", exception);
            }
        }
    }
}
