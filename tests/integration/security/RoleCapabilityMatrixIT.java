package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Rollenmatrix (Befunde V-50, V-51, V-52).
 *
 * <p>Der Vertrag nennt eine <em>vollstaendige</em> Rollenmatrix: jede Rolle mit
 * jedem Recht. Dieser Test macht sie pruefbar:
 * <ul>
 *   <li><strong>V-50</strong> — je (Rolle, Faehigkeit) ein Testfall, auch fuer
 *       den Service-Admin im <em>eigenen</em> Mandanten;</li>
 *   <li><strong>V-51</strong> — fuer jede der 17 Operationen: welche Rolle darf
 *       sie, und welcher Statuscode ergibt sich bei fehlender Berechtigung
 *       beziehungsweise bei einem fremden Mandanten;</li>
 *   <li><strong>V-52</strong> — Rollenrechte, die kein eigenes Akzeptanzkriterium
 *       haben, sind hier einem Test zugewiesen (Anker: die Rollenmatrix des
 *       Vertrags, ADR-015 §1).</li>
 * </ul>
 *
 * <p>Die Reihenfolge der Pruefungen gilt ueberall: <strong>erst der Mandant,
 * dann die Rolle</strong> (V-39). Ein fremder Mandant ergibt 404 — fuer alle
 * Rollen, auch fuer den Service-Admin.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code info.description}
 * (Abschnitt „Rollen"), {@code x-contract-rules.roles};
 * ADR-015 §1 (normative Rollenmatrix).
 */
class RoleCapabilityMatrixIT extends AbstractApplicationIT {

    private static final String OWN_URL = "https://roles-own.example.invalid/hooks/echobox";
    private static final String FOREIGN_URL = "https://roles-foreign.example.invalid/hooks/echobox";

    private static final List<String> ALL_ROLES = List.of("developer", "receiver_admin", "service_admin");

    @Autowired
    private DeliveryWorker worker;

    private String ownEndpointId;
    private String foreignEndpointId;
    private String ownEventId;
    private String foreignEventId;
    private String ownRunId;
    private String foreignRunId;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.max-attempts", () -> "1");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    // ------------------------------------------------------------------ V-50

    /**
     * Die 24 Kombinationen aus Rolle und Faehigkeit.
     *
     * @return die Testfaelle
     */
    static Stream<Arguments> capabilityMatrix() {
        return Stream.of(
                // developer
                Arguments.of("developer", "endpunkte_verwalten", false),
                Arguments.of("developer", "ereignisse_einliefern", true),
                Arguments.of("developer", "secret_rotieren", false),
                Arguments.of("developer", "zustellprotokoll_lesen", true),
                Arguments.of("developer", "replay_ausloesen", false),
                Arguments.of("developer", "totenliste", false),
                Arguments.of("developer", "kennzahlen", true),
                Arguments.of("developer", "aufbewahrung", false),
                // receiver_admin
                Arguments.of("receiver_admin", "endpunkte_verwalten", true),
                Arguments.of("receiver_admin", "ereignisse_einliefern", false),
                Arguments.of("receiver_admin", "secret_rotieren", true),
                Arguments.of("receiver_admin", "zustellprotokoll_lesen", true),
                Arguments.of("receiver_admin", "replay_ausloesen", true),
                Arguments.of("receiver_admin", "totenliste", false),
                Arguments.of("receiver_admin", "kennzahlen", true),
                Arguments.of("receiver_admin", "aufbewahrung", false),
                // service_admin
                Arguments.of("service_admin", "endpunkte_verwalten", true),
                Arguments.of("service_admin", "ereignisse_einliefern", false),
                Arguments.of("service_admin", "secret_rotieren", false),
                Arguments.of("service_admin", "zustellprotokoll_lesen", true),
                Arguments.of("service_admin", "replay_ausloesen", false),
                Arguments.of("service_admin", "totenliste", true),
                Arguments.of("service_admin", "kennzahlen", true),
                Arguments.of("service_admin", "aufbewahrung", true));
    }

    @ParameterizedTest(name = "V-50: {0} darf {1} = {2}")
    @MethodSource("capabilityMatrix")
    @DisplayName("V-50 (AC-004-2): die Rollenmatrix gilt je (Rolle, Faehigkeit) — auch fuer den Service-Admin im eigenen Mandanten")
    void the_capability_matrix_is_enforced(String role, String capability, boolean allowed) throws Exception {
        int status = performCapability(capability, role);

        if (allowed) {
            assertThat(status)
                    .as("%s darf %s", role, capability)
                    .isNotIn(401, 403);
        } else {
            assertThat(status)
                    .as("%s darf %s nicht", role, capability)
                    .isEqualTo(403);
        }
    }

    // ------------------------------------------------------------------ V-51

    /**
     * Die 17 Operationen des Vertrags mit ihrer Rollenbindung.
     *
     * @return die Testfaelle
     */
    static Stream<Arguments> operationMatrix() {
        List<Arguments> cases = new ArrayList<>();
        for (Operation operation : Operation.values()) {
            for (String role : ALL_ROLES) {
                cases.add(Arguments.of(operation, role));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "V-51: {0} mit der Rolle {1}")
    @MethodSource("operationMatrix")
    @DisplayName("V-51 (AC-013-3, AC-022-4): je Operation ist die Rolle und der Statuscode bei fehlender Berechtigung festgeschrieben")
    void every_operation_has_a_role_and_a_status_code(Operation operation, String role) throws Exception {
        int status = perform(operation, role, false);

        if (operation.allowedRoles().contains(role)) {
            assertThat(status)
                    .as("%s darf %s (eigener Mandant)", role, operation.operationId())
                    .isNotIn(401, 403);
        } else {
            assertThat(status)
                    .as("%s darf %s nicht (eigener Mandant)", role, operation.operationId())
                    .isEqualTo(403);
        }

        if (operation.resourceBound()) {
            int foreignStatus = perform(operation, role, true);
            assertThat(foreignStatus)
                    .as("%s: ein fremder Mandant ergibt 404 — erst der Mandant, dann die Rolle", operation.operationId())
                    .isEqualTo(404);
        }
    }

    // ------------------------------------------------------------------ V-52

    @Test
    @DisplayName("V-52 (AC-004-2): der Service-Admin verwaltet Endpunkte im eigenen Mandanten (im Vertrag zugesagt, ohne eigenes AK)")
    void the_service_admin_manages_endpoints_in_the_own_tenant() throws Exception {
        assertThat(perform(Operation.CREATE_ENDPOINT, "service_admin", false))
                .as("der Service-Admin verwaltet Endpunkte im eigenen Mandanten")
                .isEqualTo(201);
    }

    @Test
    @DisplayName("V-52 (AC-005-1): der Service-Admin liefert keine Ereignisse ein")
    void the_service_admin_does_not_submit_events() throws Exception {
        assertThat(perform(Operation.SUBMIT_EVENT, "service_admin", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-52 (AC-001-1): der Entwickler verwaltet keine Endpunkte")
    void the_developer_does_not_manage_endpoints() throws Exception {
        assertThat(perform(Operation.CREATE_ENDPOINT, "developer", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-52 (AC-016-2): der Entwickler liest die Totenliste nicht")
    void the_developer_does_not_read_the_dead_letter_list() throws Exception {
        assertThat(perform(Operation.LIST_DEAD_DELIVERIES, "developer", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-52 (AC-016-5): der Empfaenger-Admin erzwingt die Aufbewahrung nicht")
    void the_receiver_admin_does_not_enforce_retention() throws Exception {
        assertThat(perform(Operation.ENFORCE_RETENTION, "receiver_admin", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-52 (AC-016-5): der Entwickler erzwingt die Aufbewahrung nicht")
    void the_developer_does_not_enforce_retention() throws Exception {
        assertThat(perform(Operation.ENFORCE_RETENTION, "developer", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-52 (AC-003-3): der Service-Admin rotiert kein Secret")
    void the_service_admin_does_not_rotate_secrets() throws Exception {
        assertThat(perform(Operation.ROTATE_SECRET, "service_admin", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-52 (AC-003-3): der Entwickler rotiert kein Secret")
    void the_developer_does_not_rotate_secrets() throws Exception {
        assertThat(perform(Operation.ROTATE_SECRET, "developer", false)).isEqualTo(403);
    }

    @Test
    @DisplayName("V-51 (AC-022-4): der Mandant kommt vor der Rolle — alle Rollen ergeben bei einem fremden Mandanten 404")
    void the_tenant_is_checked_before_the_role_for_every_role() throws Exception {
        for (String role : ALL_ROLES) {
            assertThat(perform(Operation.GET_ENDPOINT, role, true))
                    .as("%s: fremder Mandant ergibt 404, nicht 403", role)
                    .isEqualTo(404);
        }
    }

    // ------------------------------------------------------------------ Vorbereitung

    @BeforeEach
    void prepareResources() throws Exception {
        ownEndpointId = registerEndpoint("m-1", OWN_URL, "order.changed");
        foreignEndpointId = registerEndpoint("m-2", FOREIGN_URL, "order.changed");
        ownEventId = submitEvent("m-1", "order.changed", "roles-own", "{\"order_id\":1}");
        foreignEventId = submitEvent("m-2", "order.changed", "roles-foreign", "{\"order_id\":2}");
        ownRunId = createAbandonedRun("m-1", OWN_URL, "roles-own-run");
        foreignRunId = createAbandonedRun("m-2", FOREIGN_URL, "roles-foreign-run");
    }

    private int performCapability(String capability, String role) throws Exception {
        return switch (capability) {
            case "endpunkte_verwalten" -> perform(Operation.CREATE_ENDPOINT, role, false);
            case "ereignisse_einliefern" -> perform(Operation.SUBMIT_EVENT, role, false);
            case "secret_rotieren" -> perform(Operation.ROTATE_SECRET, role, false);
            case "zustellprotokoll_lesen" -> perform(Operation.LIST_DELIVERIES, role, false);
            case "replay_ausloesen" -> perform(Operation.REPLAY, role, false);
            case "totenliste" -> perform(Operation.LIST_DEAD_DELIVERIES, role, false);
            case "kennzahlen" -> perform(Operation.ENDPOINT_METRICS, role, false);
            case "aufbewahrung" -> perform(Operation.ENFORCE_RETENTION, role, false);
            default -> throw new IllegalArgumentException("Unbekannte Faehigkeit: " + capability);
        };
    }

    private int perform(Operation operation, String role, boolean foreign) throws Exception {
        MockHttpServletRequestBuilder request = operation.request(resolve(operation, foreign));

        return mockMvc.perform(request
                        .with(Auth.as("m-1", "rollen-test", role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operation.body()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String resolve(Operation operation, boolean foreign) {
        return operation.path()
                .replace("{ownEndpoint}", foreign ? foreignEndpointId : ownEndpointId)
                .replace("{foreignEndpoint}", foreignEndpointId)
                .replace("{ownEvent}", foreign ? foreignEventId : ownEventId)
                .replace("{foreignEvent}", foreignEventId)
                .replace("{ownRun}", foreign ? foreignRunId : ownRunId)
                .replace("{foreignRun}", foreignRunId);
    }

    private String createAbandonedRun(String organisationId, String url, String idempotencyKey) throws Exception {
        deliveryPort.respondWith(url, 500);
        submitEvent(organisationId, "order.changed", idempotencyKey, "{\"order_id\":3}");

        for (int attempt = 1; attempt <= 3; attempt++) {
            worker.runOnce();
            clock.advance(Duration.ofMinutes(1));
        }

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT delivery_run_id FROM app.delivery_runs WHERE status = 'abandoned' "
                        + "AND organisation_id = ? ORDER BY created_at DESC LIMIT 1", organisationId);

        // Der Treiber liefert eine UUID-Spalte als UUID-Objekt — die
        // Umwandlung in die Zeichenkette gehoert zum Test.
        return String.valueOf(runs.get(0).get("delivery_run_id"));
    }
}
