package com.echobox.deployment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Mandanteninstanz (US-023, AC-023-2, NFR-006).
 *
 * <p>Eine Instanz kann an einen Mandanten gebunden werden
 * ({@code echobox.tenant.id}). Eine solche Instanz bedient <em>nur</em> diesen
 * Mandanten — das Mandanten-Sharding ist damit vorbereitet.
 *
 * <p>Eigener Spring-Kontext: die Bindung an einen Mandanten ist eine
 * Konfiguration der Instanz, keine Eigenschaft des Aufrufs.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; `x-requirement-coverage` (NFR-006);
 * ADR-013.
 */
class TenantInstanceIT extends AbstractApplicationIT {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.tenant.id", () -> "m-1");
    }

    @Test
    @DisplayName("AC-023-2: die an m-1 gebundene Instanz bedient m-1")
    void the_bound_instance_serves_its_own_tenant() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-023-2: die an m-1 gebundene Instanz bedient m-2 nicht")
    void the_bound_instance_does_not_serve_another_tenant() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-2", "tester")))
                .andExpect(status().isForbidden());
    }
}
