package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — Zustellkennungen und at-least-once (US-010, FR-021).
 *
 * <p>Getestet werden die Kopfzeilen des ausgehenden Zustellvertrags: ihre Namen
 * und ihr Inhalt. Der Vertrag ist eingefroren — ein Vertragstest haelt die Namen
 * zusaetzlich gegen {@code docs/api/openapi.yaml} fest.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-delivery-protocol.request.headers}
 * und {@code x-delivery-protocol.duplicate_handling}.
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-010-1, AC-010-2, AC-010-4.
 */
class DeliveryIdentifiersTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-21T09:15:22.481Z");

    @Test
    @DisplayName("AC-010-1: jeder Versuch traegt Ereignis-ID, Zustelllauf-ID und Versuchs-ID")
    void every_attempt_carries_the_three_identifiers() {
        Map<String, String> headers = DeliveryHeaders.build(
                "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d",
                "7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d",
                "b1a2c3d4-1111-4222-8333-444455556666",
                1,
                "order.changed",
                TIMESTAMP,
                "v1=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThat(headers)
                .containsEntry(DeliveryHeaders.EVENT_ID, "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d")
                .containsEntry(DeliveryHeaders.DELIVERY_RUN_ID, "7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d")
                .containsEntry(DeliveryHeaders.ATTEMPT_ID, "b1a2c3d4-1111-4222-8333-444455556666")
                .containsEntry(DeliveryHeaders.ATTEMPT_NUMBER, "1")
                .containsEntry(DeliveryHeaders.EVENT_TYPE, "order.changed")
                .containsEntry(DeliveryHeaders.TIMESTAMP, "2026-10-21T09:15:22.481Z")
                .containsEntry(DeliveryHeaders.USER_AGENT, "EchoBox-Delivery/1.0");
    }

    @Test
    @DisplayName("AC-010-1: die Header-Namen sind die eingefrorenen Namen des Vertrags")
    void the_header_names_are_the_frozen_contract_names() {
        assertThat(DeliveryHeaders.EVENT_ID).isEqualTo("X-EchoBox-Event-Id");
        assertThat(DeliveryHeaders.DELIVERY_RUN_ID).isEqualTo("X-EchoBox-Delivery-Run-Id");
        assertThat(DeliveryHeaders.ATTEMPT_ID).isEqualTo("X-EchoBox-Attempt-Id");
        assertThat(DeliveryHeaders.ATTEMPT_NUMBER).isEqualTo("X-EchoBox-Attempt-Number");
        assertThat(DeliveryHeaders.EVENT_TYPE).isEqualTo("X-EchoBox-Event-Type");
        assertThat(DeliveryHeaders.TIMESTAMP).isEqualTo("X-EchoBox-Timestamp");
        assertThat(DeliveryHeaders.SIGNATURE).isEqualTo("X-EchoBox-Signature");
        assertThat(DeliveryHeaders.REJECTION).isEqualTo("X-EchoBox-Rejection");
        assertThat(DeliveryHeaders.USER_AGENT).isEqualTo("User-Agent");
    }

    @Test
    @DisplayName("AC-010-2: zwei Versuche desselben Laufs tragen dieselbe Zustelllauf-ID, aber verschiedene Versuchs-IDs")
    void two_attempts_of_one_run_share_the_run_id_but_not_the_attempt_id() {
        String eventId = "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d";
        String deliveryRunId = "7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d";

        Map<String, String> first = DeliveryHeaders.build(eventId, deliveryRunId, "attempt-1", 1,
                "order.changed", TIMESTAMP, "v1=aa");
        Map<String, String> second = DeliveryHeaders.build(eventId, deliveryRunId, "attempt-2", 2,
                "order.changed", TIMESTAMP.plusSeconds(60), "v1=bb");

        assertThat(first.get(DeliveryHeaders.DELIVERY_RUN_ID)).isEqualTo(second.get(DeliveryHeaders.DELIVERY_RUN_ID));
        assertThat(first.get(DeliveryHeaders.EVENT_ID)).isEqualTo(second.get(DeliveryHeaders.EVENT_ID));
        assertThat(first.get(DeliveryHeaders.ATTEMPT_ID)).isNotEqualTo(second.get(DeliveryHeaders.ATTEMPT_ID));
        assertThat(first.get(DeliveryHeaders.ATTEMPT_NUMBER)).isEqualTo("1");
        assertThat(second.get(DeliveryHeaders.ATTEMPT_NUMBER)).isEqualTo("2");
    }

    @Test
    @DisplayName("AC-010-4: zwei Laeufe desselben Ereignisses tragen verschiedene Zustelllauf-IDs")
    void two_runs_of_one_event_carry_different_run_ids() {
        Map<String, String> toFirstEndpoint = DeliveryHeaders.build(
                "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d", "run-a", "attempt-1", 1,
                "order.changed", TIMESTAMP, "v1=aa");
        Map<String, String> toSecondEndpoint = DeliveryHeaders.build(
                "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d", "run-b", "attempt-2", 1,
                "order.changed", TIMESTAMP, "v1=bb");

        assertThat(toFirstEndpoint.get(DeliveryHeaders.EVENT_ID)).isEqualTo(toSecondEndpoint.get(DeliveryHeaders.EVENT_ID));
        assertThat(toFirstEndpoint.get(DeliveryHeaders.DELIVERY_RUN_ID))
                .isNotEqualTo(toSecondEndpoint.get(DeliveryHeaders.DELIVERY_RUN_ID));
    }

    @Test
    @DisplayName("AC-010-1: der Nutzer-Agent meldet den Zustelldienst")
    void the_user_agent_identifies_the_delivery_service() {
        Map<String, String> headers = DeliveryHeaders.build(
                "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d", "7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d",
                "b1a2c3d4-1111-4222-8333-444455556666", 1, "order.changed", TIMESTAMP, "v1=aa");

        assertThat(headers.get(DeliveryHeaders.USER_AGENT)).isEqualTo("EchoBox-Delivery/1.0");
    }

    @Test
    @DisplayName("AC-009-3: der Signatur-Header traegt den Wert v1=<hex-lowercase>")
    void the_signature_header_carries_the_versioned_value() {
        Map<String, String> headers = DeliveryHeaders.build(
                "3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d", "7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d",
                "b1a2c3d4-1111-4222-8333-444455556666", 1, "order.changed", TIMESTAMP,
                "v1=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThat(headers.get(DeliveryHeaders.SIGNATURE))
                .startsWith(DeliveryHeaders.SIGNATURE_VALUE_PREFIX);
    }
}
