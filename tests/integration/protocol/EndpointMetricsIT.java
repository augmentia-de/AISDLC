package com.echobox.protocol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.delivery.DeliveryWorker;
import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Erfolgsquote und Zustelllatenz je Endpunkt (US-017, FR-032).
 *
 * <p>Die Kennzahlen werden je Endpunkt im Auswertungsfenster ausgewiesen. Ohne
 * Daten im Fenster wird <em>keine Daten</em> ausgewiesen — nicht 0 % und nicht
 * 100 %.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code getEndpointMetrics}
 * (GET /api/v1/endpoints/{endpointId}/metrics) und
 * {@code components.schemas.EndpointMetrics}.
 */
class EndpointMetricsIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://metrics.example.invalid/hooks/echobox";

    @Autowired
    private DeliveryWorker worker;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
        registry.add("echobox.delivery.backoff.jitter", () -> "0.0");
    }

    @Test
    @DisplayName("AC-017-1: die Kennzahlen nennen Erfolgsquote und p95-Latenz")
    void the_metrics_name_success_rate_and_latency() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        deliveryPort.respondWith(TARGET_URL, 200);
        submitEvent("m-1", "order.changed", "metrics-1", "{\"order_id\":1}");

        worker.runOnce();

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId + "/metrics")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint_id").value(endpointId))
                .andExpect(jsonPath("$.data_available").value(true))
                .andExpect(jsonPath("$.attempts_total").value(1))
                .andExpect(jsonPath("$.attempts_successful").value(1))
                .andExpect(jsonPath("$.success_rate").value(1.0))
                .andExpect(jsonPath("$.delivery_latency_p95_ms").isNumber())
                .andExpect(jsonPath("$.window.from").isNotEmpty())
                .andExpect(jsonPath("$.window.to").isNotEmpty());
    }

    @Test
    @DisplayName("AC-017-2: die Kennzahlen sind ueber eine dokumentierte Schnittstelle abrufbar")
    void the_metrics_are_available_over_a_documented_interface() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId + "/metrics")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate_limit.limit").isNumber())
                .andExpect(jsonPath("$.rate_limit.limited").isBoolean())
                .andExpect(jsonPath("$.rate_limit.window_seconds").isNumber())
                .andExpect(jsonPath("$.rate_limit.strategy").isNotEmpty())
                .andExpect(jsonPath("$.rate_limit.observed_attempts").isNumber())
                .andExpect(jsonPath("$.rate_limit.delayed_attempts").isNumber());
    }

    @Test
    @DisplayName("AC-017-3: ohne Daten im Fenster wird 'keine Daten' ausgewiesen")
    void without_data_the_metrics_report_no_data() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId + "/metrics")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data_available").value(false))
                .andExpect(jsonPath("$.success_rate").doesNotExist())
                .andExpect(jsonPath("$.delivery_latency_p95_ms").doesNotExist());
    }

    @Test
    @DisplayName("AC-017-4: die Kennzahlen eines fremden Endpunkts ergeben 404")
    void the_metrics_of_a_foreign_endpoint_yield_not_found() throws Exception {
        String foreign = registerEndpoint("m-2", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + foreign + "/metrics")
                        .with(Auth.receiverAdmin("m-1", "tester")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("AC-018-4: der Abschnitt rate_limit ist immer Teil der Antwort")
    void the_rate_limit_section_is_always_part_of_the_answer() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(get("/api/v1/endpoints/" + endpointId + "/metrics")
                        .with(Auth.serviceAdmin("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate_limit").isNotEmpty())
                .andExpect(jsonPath("$.rate_limit.limit").value(60))
                .andExpect(jsonPath("$.rate_limit.limited").value(true));
    }
}
