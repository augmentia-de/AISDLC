package com.echobox.protocol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Tote Zustellungen abholen (US-016, FR-031).
 *
 * <p>Die Totenliste ist mandantenuebergreifend und nur fuer den Service-Admin
 * zugaenglich. Sie enthaelt ausschliesslich aufgegebene Zustellungen und ist
 * filterbar und seitenweise abrufbar.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code listDeadDeliveries}.
 */
class DeadDeliveryListIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://deadlist.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
        registry.add("echobox.delivery.max-attempts", () -> "1");
    }

    @Test
    @DisplayName("AC-016-1: der Service-Admin erhaelt die toten Zustellungen aller Mandanten")
    void the_service_admin_sees_the_dead_deliveries_of_all_tenants() throws Exception {
        createAbandonedRun("m-1", "deadlist-1");
        createAbandonedRun("m-2", "deadlist-2");

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.hasItems("m-1", "m-2")));
    }

    @Test
    @DisplayName("AC-016-1: der Eintrag nennt Ereignis-ID, Endpunkt, Ereignistyp, Versuche, Antwortcode und Aufgabezeitpunkt")
    void the_entry_names_all_required_fields() throws Exception {
        createAbandonedRun("m-1", "deadlist-3");

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].event_id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].endpoint_id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].event_type").value("order.changed"))
                .andExpect(jsonPath("$.items[0].attempt_count").value(1))
                .andExpect(jsonPath("$.items[0].last_response_status").value(500))
                .andExpect(jsonPath("$.items[0].abandoned_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].status").value("abandoned"));
    }

    @Test
    @DisplayName("AC-016-2: ein Empfaenger-Admin erhaelt 403")
    void a_receiver_admin_is_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("AC-016-3: eine leere Trefferliste ist HTTP 200 mit leerem items-Array")
    void an_empty_result_is_a_200_with_an_empty_array() throws Exception {
        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.has_more").value(false));
    }

    @Test
    @DisplayName("AC-016-4: die Filter sind kombinierbar und die Liste ist seitenweise abrufbar")
    void the_filters_are_combinable_and_the_list_is_paged() throws Exception {
        String endpointId = createAbandonedRun("m-1", "deadlist-4");

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .param("endpoint_id", endpointId)
                        .param("status", "abandoned")
                        .param("event_type", "order.changed")
                        .param("limit", "1")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page.limit").value(1));
    }

    @Test
    @DisplayName("AC-016-4: nur aufgegebene Zustellungen stehen in der Totenliste")
    void only_abandoned_runs_are_listed() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "deadlist-5", "{\"order_id\":5}");
        worker.runOnce();

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    /**
     * Legt eine aufgegebene Zustellung an und liefert die Endpunkt-ID — sie
     * ist der Filter der Totenliste.
     *
     * @param organisationId der Mandant
     * @param idempotencyKey der Idempotenzschluessel des Ereignisses
     * @return die Endpunkt-ID
     * @throws Exception wenn das Anlegen scheitert
     */
    private String createAbandonedRun(String organisationId, String idempotencyKey) throws Exception {
        String endpointId = registerEndpoint(organisationId, TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent(organisationId, "order.changed", idempotencyKey, "{\"order_id\":1}");

        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.runOnce();
            clock.advance(Duration.ofMinutes(1));
        }
        return endpointId;
    }
}
