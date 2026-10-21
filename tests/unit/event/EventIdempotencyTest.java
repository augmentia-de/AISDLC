package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Idempotente Einlieferung (US-006, FR-011).
 *
 * <p>Getestet wird die Entscheidung, die aus dem Vergleich des Idempotenzschluessels
 * und des Nutzlast-Fingerprints folgt. Der Fingerprint erlaubt den Vergleich
 * "gleiche oder andere Nutzlast", ohne den Klartext zu beruehren (D-05).
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-contract-rules.idempotency};
 * `.agent/knowledge/schema.sql` -&gt; {@code events_idempotency_uq} und
 * {@code payload_fingerprint}.
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-006-1, AC-006-2, AC-006-4.
 */
class EventIdempotencyTest {

    private static final UUID EVENT_ID = UUID.fromString("3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d");

    private final com.echobox.crypto.PayloadCipher payloadCipher = new com.echobox.crypto.PayloadCipher(
            new javax.crypto.spec.SecretKeySpec(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES"));

    @Test
    @DisplayName("AC-006-1: ein unbekannter Schluessel ergibt ein neues Ereignis")
    void unknown_key_yields_a_new_event() {
        IdempotencyDecision decision = IdempotencyDecision.of(Optional.empty(), payloadCipher.fingerprint("{}".getBytes(StandardCharsets.UTF_8)));

        assertThat(decision).isEqualTo(IdempotencyDecision.NEW);
    }

    @Test
    @DisplayName("AC-006-1: dieselbe Nutzlast ergibt eine Wiederholung, kein zweites Ereignis")
    void same_payload_yields_a_replay() {
        byte[] payload = "{\"order_id\":4711}".getBytes(StandardCharsets.UTF_8);
        StoredEvent existing = new StoredEvent(EVENT_ID, "m-1", "order-4711-changed", payloadCipher.fingerprint(payload));

        IdempotencyDecision decision = IdempotencyDecision.of(Optional.of(existing), payloadCipher.fingerprint(payload));

        assertThat(decision).isEqualTo(IdempotencyDecision.REPLAYED);
    }

    @Test
    @DisplayName("AC-006-2: der Fingerprint ist bei gleicher Nutzlast identisch")
    void fingerprint_is_stable_for_equal_payloads() {
        byte[] payload = "{\"order_id\":4711}".getBytes(StandardCharsets.UTF_8);

        assertThat(payloadCipher.fingerprint(payload)).isEqualTo(payloadCipher.fingerprint(payload));
    }

    @Test
    @DisplayName("AC-006-4: eine abweichende Nutzlast ergibt einen Konflikt (HTTP 409)")
    void differing_payload_yields_a_conflict() {
        byte[] stored = "{\"order_id\":4711}".getBytes(StandardCharsets.UTF_8);
        byte[] incoming = "{\"order_id\":4712}".getBytes(StandardCharsets.UTF_8);
        StoredEvent existing = new StoredEvent(EVENT_ID, "m-1", "order-4711-changed", payloadCipher.fingerprint(stored));

        IdempotencyDecision decision = IdempotencyDecision.of(Optional.of(existing), payloadCipher.fingerprint(incoming));

        assertThat(decision).isEqualTo(IdempotencyDecision.CONFLICT);
    }

    @Test
    @DisplayName("AC-006-2: der Fingerprint enthaelt keinen Klartextanteil")
    void fingerprint_does_not_contain_the_plaintext() {
        byte[] payload = "TOP-SECRET-PAYLOAD".getBytes(StandardCharsets.UTF_8);

        byte[] fingerprint = payloadCipher.fingerprint(payload);

        assertThat(fingerprint).hasSize(32);
        assertThat(new String(fingerprint, StandardCharsets.ISO_8859_1)).doesNotContain("TOP-SECRET-PAYLOAD");
    }
}
