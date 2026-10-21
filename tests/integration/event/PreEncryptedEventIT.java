package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Bereits verschluesselte Einlieferung (US-008, FR-013).
 *
 * <p>Der Dienst uebernimmt die Bytes unveraendert: er verschluesselt nicht
 * erneut und entschluesselt nicht — beim Zustellen wird genau das Gelieferte
 * weitergereicht.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code EventIntakeRequest.payload_encoding} und
 * {@code x-delivery-protocol.request.body}.
 */
class PreEncryptedEventIT extends AbstractApplicationIT {

    private static final byte[] CIPHRE = "AEAD-Chiffre-ohne-Klartextanteil".getBytes(StandardCharsets.UTF_8);

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-008-1: die Bytes werden unveraendert gespeichert")
    void the_bytes_are_stored_unchanged() throws Exception {
        String eventId = submitPreEncrypted("preenc-1", CIPHRE);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT encode(payload_ciphertext, 'escape') AS ciphertext, payload_is_pre_encrypted "
                        + "FROM app.events WHERE event_id = ?", eventId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("payload_is_pre_encrypted")).isEqualTo(Boolean.TRUE);
        assertThat(String.valueOf(rows.get(0).get("ciphertext")))
                .as("die Chiffre steht unveraendert in der Spalte")
                .contains("AEAD-Chiffre");
    }

    @Test
    @DisplayName("AC-008-1: die Groesse der Chiffre wird ausgewiesen")
    void the_size_of_the_ciphertext_is_reported() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "preenc-2",
                                  "payload_encoding": "pre_encrypted",
                                  "payload_base64": "%s"
                                }""".formatted(Base64.getEncoder().encodeToString(CIPHRE))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.payload_size_bytes").value(CIPHRE.length));
    }

    @Test
    @DisplayName("AC-008-2: eine vorverschluesselte Einlieferung mit Klartextnutzlast wird mit 422 abgelehnt")
    void a_pre_encrypted_submission_with_plaintext_is_rejected() throws Exception {
        long before = countEvents();

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "preenc-3",
                                  "payload_encoding": "pre_encrypted",
                                  "payload": {"order_id": 4711}
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(countEvents()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-008-3: dieselbe Grenze gilt fuer die vorverschluesselte Einlieferung")
    void the_same_limit_applies_to_pre_encrypted_payloads() throws Exception {
        long before = countEvents();
        byte[] tooLarge = new byte[1_048_577];

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "preenc-4",
                                  "payload_encoding": "pre_encrypted",
                                  "payload_base64": "%s"
                                }""".formatted(Base64.getEncoder().encodeToString(tooLarge))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));

        assertThat(countEvents()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-008-2: vorverschluesselt verlangt payload_base64")
    void pre_encrypted_requires_base64() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "preenc-5",
                                  "payload_encoding": "pre_encrypted"
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    private String submitPreEncrypted(String idempotencyKey, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "%s",
                                  "payload_encoding": "pre_encrypted",
                                  "payload_base64": "%s"
                                }""".formatted(idempotencyKey, Base64.getEncoder().encodeToString(bytes))))
                .andExpect(status().isAccepted())
                .andReturn();

        return readJson(result).path("event_id").asText();
    }

    private long countEvents() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app.events", Long.class);
        return count == null ? 0L : count;
    }
}
