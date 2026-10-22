package com.echobox.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Mandanttrennung (US-022, NFR-003, FR-004).
 *
 * <p>Vier Forderungen werden geprueft: jede mandantenfuehrende Tabelle steht
 * unter RLS, eine Abfrage zeigt nur den eigenen Mandanten, der Mandant stammt
 * aus der Identitaet, und eine fremde Ressource per ID ergibt 404.
 *
 * <p>Der reine Datenbank-Nachweis steht in
 * {@code tests/integration/tenant/RowLevelSecurityIT.java}.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-contract-rules.tenant};
 * ADR-002.
 */
class TenantIsolationIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://tenant.example.invalid/hooks/echobox";

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-022-1: jede mandantenfuehrende Tabelle steht unter Row-Level-Security")
    void every_tenant_table_enforces_row_level_security() {
        List<String> withoutRls = jdbcTemplate.queryForList("""
                SELECT c.relname AS table_name
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relkind = 'r' AND NOT c.relrowsecurity
                ORDER BY c.relname""", String.class);

        assertThat(withoutRls)
                .as("keine mandantenfuehrende Tabelle darf ohne Row-Level-Security auskommen")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-022-1: jede mandantenfuehrende Tabelle traegt organisation_id")
    void every_tenant_table_carries_the_tenant_column() {
        List<String> withoutTenant = jdbcTemplate.queryForList("""
                SELECT c.relname AS table_name
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relkind = 'r'
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_attribute a
                      WHERE a.attrelid = c.oid AND a.attname = 'organisation_id' AND a.attnum > 0
                        AND NOT a.attisdropped)
                ORDER BY c.relname""", String.class);

        assertThat(withoutTenant).isEmpty();
    }

    @Test
    @DisplayName("AC-022-2: die Anwendung zeigt nur Daten des eigenen Mandanten")
    void the_application_shows_only_the_own_tenants_data() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        registerEndpoint("m-2", "https://other.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.is("m-1"))));
    }

    @Test
    @DisplayName("AC-022-3: der Mandant stammt aus der Identitaet — ein Parameter aus dem Body wird nicht uebernommen")
    void the_tenant_comes_from_the_identity_and_not_from_the_body() throws Exception {
        // Eigene Ziel-URL: der Test legt seine Daten selbst an (Teststrategie §2)
        // und ist damit unabhaengig von den Endpunkten der anderen Testfaelle.
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target_url": "https://tenant-abgelehnt.example.invalid/hooks/echobox",
                                  "secret": "s3cr3t-rotationswert",
                                  "event_types": ["order.changed"],
                                  "organisation_id": "m-2"
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("tenant_parameter_rejected"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT organisation_id FROM app.endpoints WHERE target_url = ?",
                "https://tenant-abgelehnt.example.invalid/hooks/echobox");

        // Der abgelehnte Aufruf hat **keinen** Endpunkt angelegt — der Mandant
        // aus dem Body wurde nicht uebernommen (D-01, AC-022-3).
        assertThat(rows)
                .as("der abgelehnte Aufruf hat keinen Endpunkt angelegt")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-022-4: eine fremde Ressource per ID ergibt 404")
    void a_foreign_resource_yields_not_found() throws Exception {
        String foreign = registerEndpoint("m-2", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + foreign)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("AC-022-4: auch der Service-Admin erhaelt 404 beim Einzelabruf einer fremden Ressource")
    void even_the_service_admin_gets_not_found_for_a_foreign_resource() throws Exception {
        String foreign = registerEndpoint("m-2", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + foreign)
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isNotFound());
    }
}
