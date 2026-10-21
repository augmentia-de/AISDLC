package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Endpunkte nach Rolle und Mandant sehen (US-004, FR-004).
 *
 * <p>Drei Sichtbarkeitsregeln werden hier belegt: die mandantengefilterte
 * Liste, die mandantenuebergreifende Sicht des Service-Admins und der
 * Einzelabruf einer fremden Ressource (404 — die Existenz bleibt verborgen).
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code listEndpoints} und
 * {@code getEndpoint}; Rollenmatrix in {@code info.description}.
 */
class EndpointVisibilityIT extends AbstractApplicationIT {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-004-1: die Endpunktliste eines Empfaenger-Admins zeigt nur seinen Mandanten")
    void the_list_of_a_receiver_admin_is_tenant_scoped() throws Exception {
        String own = registerEndpoint("m-1", "https://own.example.invalid/hook", "order.changed");
        String foreign = registerEndpoint("m-2", "https://foreign.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].endpoint_id", Matchers.hasItem(own)))
                // Der Endpunkt des **fremden** Mandanten darf nicht in der Liste
                // stehen — die Liste zeigt nur den Mandanten aus der Identitaet.
                .andExpect(jsonPath("$.items[*].endpoint_id", Matchers.not(Matchers.hasItem(foreign))))
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.is("m-1"))));
    }

    @Test
    @DisplayName("AC-004-1: die Liste eines Entwicklers zeigt ebenfalls nur seinen Mandanten")
    void the_list_of_a_developer_is_tenant_scoped() throws Exception {
        registerEndpoint("m-1", "https://own.example.invalid/hook", "order.changed");
        registerEndpoint("m-2", "https://foreign.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.developer("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.is("m-1"))));
    }

    @Test
    @DisplayName("AC-004-2: der Service-Admin sieht die Endpunkte aller Mandanten")
    void the_service_admin_sees_all_tenants() throws Exception {
        String first = registerEndpoint("m-1", "https://first.example.invalid/hook", "order.changed");
        String second = registerEndpoint("m-2", "https://second.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].endpoint_id", Matchers.hasItems(first, second)))
                .andExpect(jsonPath("$.items[*].organisation_id",
                        Matchers.hasItems("m-1", "m-2")));
    }

    @Test
    @DisplayName("AC-004-2: jeder Datensatz der mandantenuebergreifenden Liste traegt den besitzenden Mandanten")
    void every_cross_tenant_record_names_its_owner() throws Exception {
        registerEndpoint("m-1", "https://first.example.invalid/hook", "order.changed");
        registerEndpoint("m-2", "https://second.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.not(Matchers.isEmptyOrNullString()))));
    }

    @Test
    @DisplayName("AC-004-3: der Einzelabruf eines fremden Endpunkts ergibt 404 ohne Daten")
    void reading_a_foreign_endpoint_yields_not_found() throws Exception {
        String foreign = registerEndpoint("m-2", "https://foreign.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + foreign)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.target_url").doesNotExist())
                .andExpect(jsonPath("$.endpoint_id").doesNotExist());
    }

    @Test
    @DisplayName("AC-004-3: auch der Service-Admin bekommt beim Einzelabruf 404")
    void even_the_service_admin_gets_not_found_for_a_single_foreign_endpoint() throws Exception {
        String foreign = registerEndpoint("m-2", "https://foreign.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + foreign)
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-004-4: ein Mandantenparameter im Query wird mit 422 abgelehnt")
    void a_tenant_parameter_in_the_query_is_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints")
                        .param("organisation_id", "m-2")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("tenant_parameter_rejected"));
    }

    @Test
    @DisplayName("AC-004-4: der Mandant der Antwort ist immer der Mandant der Identitaet")
    void the_tenant_of_the_answer_is_the_tenant_of_the_identity() throws Exception {
        registerEndpoint("m-1", "https://own.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.is("m-1"))));
    }

    @Test
    @DisplayName("AC-004-1: die Liste ist mandantengefiltert — die Datenbank behauptet nichts anderes")
    void the_database_still_holds_both_tenants() throws Exception {
        registerEndpoint("m-1", "https://own.example.invalid/hook", "order.changed");
        registerEndpoint("m-2", "https://foreign.example.invalid/hook", "order.changed");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT organisation_id FROM app.endpoints ORDER BY organisation_id");

        assertThat(rows).hasSize(2);
    }
}
