package com.echobox.common;

import java.security.SecureRandom;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Standardimplementierung des {@link IdGenerator}: **UUID-v7**.
 *
 * <p>Aufbau nach RFC 9562: die oberen 48 Bit tragen den Unix-Zeitstempel in
 * Millisekunden, danach folgen die Version (7) und die Zufallsbits. Innerhalb
 * derselben Millisekunde wird ein Zaehler erhoeht, damit die Kennungen
 * monoton bleiben.
 */
@Component
public class UuidV7IdGenerator implements IdGenerator {

    private static final int RANDOM_BYTES = 10;

    private final SecureRandom random = new SecureRandom();

    private long lastTimestamp = -1L;

    private int sequence;

    @Override
    public synchronized UUID newId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp == lastTimestamp) {
            sequence++;
            if (sequence > 0xFFF) {
                timestamp++;
                sequence = 0;
            }
        } else {
            lastTimestamp = timestamp;
            sequence = 0;
        }

        byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        bytes[6] = (byte) (0x70 | ((sequence >>> 8) & 0x0F));
        bytes[7] = (byte) sequence;
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        long mostSignificantBits = 0;
        long leastSignificantBits = 0;
        for (int index = 0; index < 8; index++) {
            mostSignificantBits = (mostSignificantBits << 8) | (bytes[index] & 0xFFL);
        }
        for (int index = 8; index < 16; index++) {
            leastSignificantBits = (leastSignificantBits << 8) | (bytes[index] & 0xFFL);
        }
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
