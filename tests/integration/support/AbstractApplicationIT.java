package com.echobox.testsupport;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
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

    /** Das Secret, das die Tests fuer ihre Endpunkte verwenden. */
    protected static final String DEFAULT_SECRET = "s3cr3t-rotationswert";

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
        // Das Anfragelimit der API (S-24) wird in `ApiRateLimitIT` geprueft.
        // Die uebrigen Integrationstests erzeugen weit mehr Anfragen, als das
        // Betriebslimit erlaubt (die Rollenmatrix allein rund 700 je Testklasse)
        // — sie wuerden sonst mit 429 antworten und nicht ihr eigentliches
        // Verhalten pruefen.
        registry.add("echobox.rate-limit.limit", () -> "100000");
    }

    /**
     * Beginnt jeden Testfall mit einem **leeren** Datenbestand und einem
     * zurueckgesetzten Testdouble.
     *
     * <p>Die Teststrategie verlangt: „Keine geteilten Fixtures, keine
     * Reihenfolgeabhaengigkeit. Jeder Test legt seine Mandanten, seine
     * Endpunkte und seine Ereignisse selbst an.“ Der Container wird zwar von
     * allen Testfaellen einer Klasse geteilt, die <strong>Daten</strong> aber
     * nicht: ohne das Leeren sammeln sich die Zeilen der Testfaelle einer
     * Klasse, und zaehlende Aussagen (etwa „die Liste enthaelt genau einen
     * Eintrag“) waeren von der Reihenfolge der Testfaelle abhaengig.
     */
    @BeforeEach
    void beginWithEmptyState() {
        deliveryPort.reset();
        jdbcTemplate.execute("TRUNCATE app.events, app.delivery_runs, app.delivery_attempts, "
                + "app.endpoints, app.endpoint_event_types, app.endpoint_secret_history, "
                + "app.endpoint_rate_limits, app.endpoint_rate_limit_counters, app.retention_settings, "
                + "app.audit_log CASCADE");
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
        return registerEndpointWithSecret(organisationId, targetUrl, DEFAULT_SECRET, eventTypes);
    }

    /**
     * Registriert einen Endpunkt mit einem eigenen Secret und liefert dessen ID.
     *
     * <p>Der Aufruf mit eigenem Secret ist dort noetig, wo die <strong>verschiedenen</strong>
     * Secrets zweier Endpunkte Teil der Aussage sind (AC-009-4).
     *
     * @param organisationId der Mandant
     * @param targetUrl      die Ziel-URL
     * @param secret         das Secret des Endpunkts
     * @param eventTypes     die abonnierten Ereignistypen
     * @return die Endpunkt-ID
     * @throws Exception wenn die Registrierung scheitert
     */
    protected String registerEndpointWithSecret(String organisationId, String targetUrl, String secret,
                                                String... eventTypes) throws Exception {
        String body = """
                {
                  "target_url": "%s",
                  "secret": "%s",
                  "event_types": [%s]
                }""".formatted(targetUrl, secret, quoted(eventTypes));

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
