package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Fremde Ressource (Befund V-47 und V-45).
 *
 * <p>V-47: fuer jede endpunktbezogene Operation gibt es einen Test „fremde
 * Ressource ⇒ 404". V-45: beim Replay trennt der Test klar zwischen
 * „fremder Mandant ⇒ 404" und „eigener Mandant, falsche Rolle ⇒ 403".
 *
 * <p>Die Reihenfolge der Pruefungen ist fest: <strong>erst der Mandant, dann
 * die Rolle</strong> (V-39). Ein fremder Mandant ergibt 404 — fuer alle
 * Rollen, auch fuer den Service-Admin.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code info.description}
 * (Abschnitt „Mandant" und „Rollen"), `x-contract-rules.tenant`.
 */
class ForeignResourceIsolationIT extends AbstractApplicationIT {

    private static final String OWN_URL = "https://own.example.invalid/hooks/echobox";
    private static final String FOREIGN_URL = "https://foreign.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    private String ownEndpointId;
    private String foreignEndpointId;
    private String ownRunId;
    private String foreignRunId;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.max-attempts", () -> "1");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @BeforeEach
    void prepareResources() throws Exception {
        ownEndpointId = registerEndpoint("m-1", OWN_URL, "order.changed");
        foreignEndpointId = registerEndpoint("m-2", FOREIGN_URL, "order.changed");
        ownRunId = createAbandonedRun("m-1", "foreign-isolation-own");
        foreignRunId = createAbandonedRun("m-2", "foreign-isolation-foreign");
    }

    @Test
    @DisplayName("V-47 (AC-004-3): das Lesen eines fremden Endpunkts ergibt 404")
    void reading_a_foreign_endpoint_yields_not_found() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/" + foreignEndpointId)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V-47 (AC-001-2): das Aktualisieren eines fremden Endpunkts ergibt 404")
    void updating_a_foreign_endpoint_yields_not_found() throws Exception {
        mockMvc.perform(patch("/api/v1/endpoints/" + foreignEndpointId)
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_url\":\"https://changed.example.invalid/hook\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V-47 (AC-002-3): das Pausieren eines fremden Endpunkts ergibt 404")
    void pausing_a_foreign_endpoint_yields_not_found() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints/" + foreignEndpointId + "/pause")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V-47 (AC-002-2): das Reaktivieren eines fremden Endpunkts ergibt 404")
    void resuming_a_foreign_endpoint_yields_not_found() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints/" + foreignEndpointId + "/resume")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V-47 (AC-003-3): die Secret-Rotation eines fremden Endpunkts ergibt 404")
    void rotating_a_foreign_endpoint_yields_not_found() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints/" + foreignEndpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"neues-s3cr3t-rotationswert\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V-45 (AC-013-3): der Replay eines fremden Zustelllaufs ergibt 404 — auch fuer den Entwickler")
    void replaying_a_foreign_run_yields_not_found_for_a_developer() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/" + foreignRunId + "/replay")
                        .with(Auth.developer("m-1", "tester")))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("V-45 (AC-013-3): der Replay eines fremden Zustelllaufs ergibt 404 — auch fuer den Service-Admin")
    void replaying_a_foreign_run_yields_not_found_for_a_service_admin() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/" + foreignRunId + "/replay")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V-45 (AC-013-3): der Replay im eigenen Mandanten mit der falschen Rolle ergibt 403")
    void replaying_an_own_run_with_the_wrong_role_yields_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/" + ownRunId + "/replay")
                        .with(Auth.developer("m-1", "tester")))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("V-45 (AC-013-3): der Replay im eigenen Mandanten mit der falschen Rolle ergibt 403 — auch fuer den Service-Admin")
    void replaying_an_own_run_with_the_wrong_role_yields_forbidden_for_a_service_admin() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/" + ownRunId + "/replay")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("V-45 (AC-013-1): der Replay im eigenen Mandanten mit der berechtigten Rolle wird angenommen")
    void replaying_an_own_run_with_the_permitted_role_is_accepted() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/" + ownRunId + "/replay")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("V-39 (AC-022-4): die Existenz einer fremden Ressource wird nicht preisgegeben")
    void the_existence_of_a_foreign_resource_is_not_revealed() throws Exception {
        String unknownId = "99999999-9999-4999-8999-999999999999";

        MvcResult foreign = mockMvc.perform(get("/api/v1/endpoints/" + foreignEndpointId)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andReturn();
        MvcResult unknown = mockMvc.perform(get("/api/v1/endpoints/" + unknownId)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andReturn();

        assertThat(foreign.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus());
        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
    }

    private String createAbandonedRun(String organisationId, String idempotencyKey) throws Exception {
        String url = organisationId.equals("m-1") ? OWN_URL : FOREIGN_URL;
        registerEndpoint(organisationId, url, "order.changed");
        deliveryPort.respondWith(url, 500);
        submitEvent(organisationId, "order.changed", idempotencyKey, "{\"order_id\":1}");

        for (int attempt = 1; attempt <= 3; attempt++) {
            worker.runOnce();
            clock.advance(Duration.ofMinutes(1));
        }

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                "SELECT delivery_run_id FROM app.delivery_runs WHERE status = 'abandoned' "
                        + "AND organisation_id = ? ORDER BY created_at DESC LIMIT 1", organisationId);

        return (String) runs.get(0).get("delivery_run_id");
    }
}
