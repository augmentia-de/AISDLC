package com.echobox.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


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
 * Integrationsebene — die als „immer vorhanden" zugesagten Antwortfelder
 * (Befund V-46).
 *
 * <p>Der Vertrag sagt an mehreren Stellen ausdruecklich, dass ein Feld
 * <em>immer</em> gefuellt ist: {@code organisation_id} in Endpunkt, Zustelllauf
 * und toter Zustellung, der Abschnitt {@code rate_limit} in den Kennzahlen und
 * {@code abandoned_at} in der Totenliste. Dieser Test prueft die
 * <strong>Laufzeitzusage</strong> — der Vertragstest
 * {@code tests/contract/AlwaysPresentFieldsContractTest.java} prueft die
 * Vertragszusage.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code components.schemas.Endpoint}, {@code DeliveryRun}, {@code DeadDelivery},
 * {@code EndpointMetrics}.
 */
class AlwaysPresentFieldsIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://immer-vorhanden.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.max-attempts", () -> "1");
        registry.add("echobox.delivery.backoff.base-delay", () -> "1s");
    }

    @Test
    @DisplayName("V-46 (AC-004-1): organisation_id ist in der Endpunktliste immer gefuellt")
    void organisation_id_is_always_filled_in_the_endpoint_list() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.not(Matchers.isEmptyOrNullString()))))
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.everyItem(Matchers.is("m-1"))));
    }

    @Test
    @DisplayName("V-46 (AC-004-3): organisation_id ist im einzelnen Endpunkt immer gefuellt")
    void organisation_id_is_always_filled_in_the_single_endpoint() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId)
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisation_id").value("m-1"));
    }

    @Test
    @DisplayName("V-46 (AC-004-2): organisation_id ist in der mandantenuebergreifenden Sicht der besitzende Mandant")
    void organisation_id_names_the_owner_in_the_cross_tenant_view() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        registerEndpoint("m-2", "https://immer-fremd.example.invalid/hook", "order.changed");

        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id", Matchers.hasItems("m-1", "m-2")));
    }

    @Test
    @DisplayName("V-46 (AC-015-1): organisation_id ist im Zustellprotokoll immer gefuellt")
    void organisation_id_is_always_filled_in_the_delivery_protocol() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        submitEvent("m-1", "order.changed", "immer-1", "{\"order_id\":1}");

        mockMvc.perform(get("/api/v1/deliveries")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organisation_id",
                        Matchers.everyItem(Matchers.is("m-1"))));
    }

    @Test
    @DisplayName("V-46 (AC-016-1): abandoned_at und organisation_id sind in der Totenliste immer gefuellt")
    void the_dead_delivery_always_names_its_owner_and_its_abandonment() throws Exception {
        registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 500);
        submitEvent("m-1", "order.changed", "immer-2", "{\"order_id\":2}");

        for (int attempt = 1; attempt <= 3; attempt++) {
            worker.runOnce();
        }

        mockMvc.perform(get("/api/v1/dead-deliveries")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].organisation_id").value("m-1"))
                .andExpect(jsonPath("$.items[0].abandoned_at").isNotEmpty());
    }

    @Test
    @DisplayName("V-46 (AC-018-4): der Abschnitt rate_limit ist in den Kennzahlen immer Teil der Antwort")
    void the_rate_limit_section_is_always_part_of_the_metrics() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId + "/metrics")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate_limit").isNotEmpty())
                .andExpect(jsonPath("$.rate_limit.limit").isNumber())
                .andExpect(jsonPath("$.rate_limit.limited").isBoolean())
                .andExpect(jsonPath("$.data_available").isBoolean());
    }
}
