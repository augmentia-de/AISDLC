package com.echobox.testsupport;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.EchoBoxApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Basisklasse der Integrationstests gegen die laufende Anwendung.
 *
 * <p>Die Anwendung wird mit {@link MockMvc} aufgerufen — der HTTP-Vertrag wird
 * also gegen die <em>echte</em> Anwendung geprueft, nur ohne Netzwerk-Socket.
 * Die Datenbank ist eine echte PostgreSQL-16-Instanz aus Testcontainers: die
 * Mandantentrennung ist nur dort belegbar (AC-022-1, AC-022-2).
 *
 * <p>Der ausgehende Zustellzugriff wird ueber {@link RecordingDeliveryPort}
 * ersetzt — die Zustelllogik wird so ohne Netz geprueft. Die Tests, die den
 * Hintergrundprozess brauchen, schalten ihn ueber
 * {@code echobox.delivery.worker.enabled} aus bzw. ein.
 */
@SpringBootTest(classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
public abstract class AbstractApplicationIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RecordingDeliveryPort deliveryPort;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Hinterlegt die Verbindungsdaten des Testcontainers in der Spring-Konfiguration.
     *
     * @param registry die Property-Registry
     */
    @DynamicPropertySource
    protected static void dataSourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerDataSource(registry);
    }

    /**
     * Schaltet den Zustell-Hintergrundprozess ab — der Test treibt die Zustellung
     * selbst, damit die Reihenfolge der Versuche eindeutig ist.
     *
     * @param registry die Property-Registry
     */
    protected static void disableDeliveryWorker(DynamicPropertyRegistry registry) {
        registry.add("echobox.delivery.worker.enabled", () -> "false");
    }

    /**
     * Schaltet den Zustell-Hintergrundprozess ein.
     *
     * @param registry die Property-Registry
     */
    protected static void enableDeliveryWorker(DynamicPropertyRegistry registry) {
        registry.add("echobox.delivery.worker.enabled", () -> "true");
    }

    /**
     * Registriert einen Endpunkt und liefert dessen ID.
     *
     * @param organisationId der Mandant
     * @param targetUrl      die Ziel-URL
     * @param eventTypes     die abonnierten Ereignistypen
     * @return die Endpunkt-ID
     * @throws Exception wenn die Registrierung scheitert
     */
    protected String registerEndpoint(String organisationId, String targetUrl, String... eventTypes) throws Exception {
        String body = """
                {
                  "target_url": "%s",
                  "secret": "s3cr3t-rotationswert",
                  "event_types": [%s]
                }""".formatted(targetUrl, quoted(eventTypes));

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin(organisationId, "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        return readJson(result).path("endpoint_id").asText();
    }

    /**
     * Liefert einen Endpunkt und liefert dessen ID.
     *
     * @param organisationId der Mandant
     * @param endpointId      die Endpunkt-ID
     * @return die Antwort als JSON
     * @throws Exception wenn der Abruf scheitert
     */
    protected JsonNode getEndpoint(String organisationId, String endpointId) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/endpoints/" + endpointId)
                        .with(Auth.receiverAdmin(organisationId, "tester")))
                .andReturn();
        return readJson(result);
    }

    /**
     * Liefert ein Ereignis ein und liefert dessen ID.
     *
     * @param organisationId  der Mandant
     * @param eventType       der Ereignistyp
     * @param idempotencyKey  der Idempotenzschluessel
     * @param payload         die Nutzlast
     * @return die Ereignis-ID
     * @throws Exception wenn die Einlieferung scheitert
     */
    protected String submitEvent(String organisationId, String eventType, String idempotencyKey, String payload)
            throws Exception {
        String body = """
                {
                  "event_type": "%s",
                  "idempotency_key": "%s",
                  "payload": %s
                }""".formatted(eventType, idempotencyKey, payload);

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/events")
                        .with(Auth.developer(organisationId, "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        return readJson(result).path("event_id").asText();
    }

    /**
     * Liest die Antwort eines MockMvc-Aufrufs als JSON.
     *
     * @param result das Ergebnis
     * @return der JSON-Baum
     * @throws Exception wenn die Antwort kein JSON ist
     */
    protected JsonNode readJson(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (content == null || content.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(content);
    }

    private static String quoted(String... values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('"').append(values[i]).append('"');
        }
        return builder.toString();
    }
}
