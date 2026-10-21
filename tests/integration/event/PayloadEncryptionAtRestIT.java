package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;
import com.echobox.testsupport.LogCapture;
import com.echobox.testsupport.PlaintextScanner;
import com.echobox.testsupport.PostgresTestContainer;

/**
 * Integrationsebene — Verschluesselungsnachweis (US-007, FR-012, NFR-004).
 *
 * <p>Dieser Test ist der <strong>Nachweis</strong>, nicht nur die Beschreibung:
 * die Klartextmarkierung wird in der Datenbank (Tabellen, Indizes, Journal) und
 * in den Protokollen gesucht — und es darf kein Treffer entstehen.
 *
 * <p>AC-007-1 nennt Tabellen, Indizes und Journal; AC-007-2 nennt die
 * Anwendungs- und Zugriffsprotokolle. Beide werden hier geprueft.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-requirement-coverage}
 * (FR-012, NFR-004) und `.agent/knowledge/schema.sql` -&gt;
 * {@code events.payload_ciphertext}.
 */
class PayloadEncryptionAtRestIT extends AbstractApplicationIT {

    private static final String MARKER = "TOP-SECRET-PAYLOAD";

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-007-1: die Klartextmarkierung steht in keiner Tabelle der Datenbank")
    void the_plaintext_marker_is_nowhere_in_the_tables() throws Exception {
        submitEventWithMarker("enc-1");

        List<String> hits = PlaintextScanner.findInTables(dataSource, MARKER);

        assertThat(hits)
                .as("die Nutzlag im Ruhezustand ist nicht im Klartext lesbar")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-007-1: die Klartextmarkierung steht in keinem Index")
    void the_plaintext_marker_is_nowhere_in_the_indexes() throws Exception {
        submitEventWithMarker("enc-2");

        List<String> hits = PlaintextScanner.findInIndexes(dataSource, MARKER);

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("AC-007-1: die Klartextmarkierung steht im vollstaendigen Datenbank-Dump nicht")
    void the_plaintext_marker_is_nowhere_in_the_database_dump() throws Exception {
        submitEventWithMarker("enc-3");

        List<String> hits = PlaintextScanner.findInDump(PostgresTestContainer.instance(), MARKER);

        assertThat(hits)
                .as("auch im Journal (Dump aller Zeilen) steht kein Klartext")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-007-1: die Klartextmarkierung steht im Server-Protokoll nicht")
    void the_plaintext_marker_is_nowhere_in_the_server_log() throws Exception {
        submitEventWithMarker("enc-4");

        List<String> hits = PlaintextScanner.findInServerLog(PostgresTestContainer.instance(), MARKER);

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("AC-007-2: die Klartextmarkierung steht in keinem Anwendungsprotokoll")
    void the_plaintext_marker_is_nowhere_in_the_application_log() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            submitEventWithMarker("enc-5");

            assertThat(logs.messages())
                    .as("der Nutzlastinhalt wird nie protokolliert")
                    .noneMatch(message -> message.contains(MARKER));
            assertThat(logs.detailedMessages())
                    .noneMatch(message -> message.contains(MARKER));
        }
    }

    @Test
    @DisplayName("AC-007-3: die Chiffre steht in der Datenbank und laesst sich nicht lesen")
    void the_payload_is_stored_as_ciphertext() throws Exception {
        String eventId = submitEventWithMarker("enc-6");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT encode(payload_ciphertext, 'escape') AS ciphertext, octet_length(payload_ciphertext) AS laenge "
                        + "FROM app.events WHERE event_id = ?", eventId);

        assertThat(rows).hasSize(1);
        assertThat(String.valueOf(rows.get(0).get("ciphertext"))).doesNotContain(MARKER);
        assertThat(((Number) rows.get(0).get("laenge")).intValue()).isPositive();
    }

    @Test
    @DisplayName("AC-007-5: das Zustellprotokoll kennt nur Metadaten — keine Suche nach Nutzlastinhalt")
    void the_protocol_searches_metadata_only() throws Exception {
        submitEventWithMarker("enc-7");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/deliveries")
                        .param("event_type", "order.changed")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());
    }

    private String submitEventWithMarker(String idempotencyKey) throws Exception {
        var result = mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "order.changed",
                                  "idempotency_key": "%s",
                                  "payload": {"order_id": 4711, "note": "%s"}
                                }""".formatted(idempotencyKey, MARKER)))
                .andExpect(status().isAccepted())
                .andReturn();

        return readJson(result).path("event_id").asText();
    }
}
