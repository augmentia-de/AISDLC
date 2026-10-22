package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — die <strong>Abwehr</strong> der Oberflaeche, geprueft
 * gegen die <em>laufende</em> Anwendung.
 *
 * <p>Der Test belegt die Eigenschaften, die der Sicherheits-Audit in den
 * Pruefungen 1 (Authentifizierung), 5 (Ausgabe) und 7 (Ressourcen) als
 * "kein Fund" festgehalten hat:
 * <ul>
 *   <li>eine geschuetzte Route verlangt ein Token und antwortet ohne Token mit
 *       <strong>401</strong> — nicht 403, nicht 500 (S-HARD-1);</li>
 *   <li>die Sicherheitskopfzeilen der Kette sind gesetzt (S-HARD-2);</li>
 *   <li>die 1-MiB-Grenze der Nutzlast wird durchgesetzt (S-HARD-3);</li>
 *   <li>die 2-MiB-Grenze des Request-Bodies wird durchgesetzt (S-HARD-4);</li>
 *   <li>die Fehlerantwort gibt keine internen Details preis (S-HARD-5);</li>
 *   <li>ein Mandantenparameter wird abgelehnt (S-HARD-6);</li>
 *   <li>nur die drei Betriebsrouten sind ohne Token erreichbar (S-HARD-7).</li>
 * </ul>
 */
class SecurityHardeningIT extends AbstractApplicationIT {

    @Test
    @DisplayName("S-HARD-1: eine geschuetzte Route antwortet ohne Token mit 401 und dem Problem-Schema")
    void a_protected_route_answers_without_token_with_401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/endpoints"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("ohne Token ist 401 gefordert — nicht 403 (angemeldet, aber unberechtigt) "
                        + "und nicht 500 (Serverfehler)")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("S-HARD-2: die Antwort traegt die Sicherheitskopfzeilen der Kette")
    void the_response_carries_the_security_headers() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints").with(Auth.developer("m-1", "tester")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("S-HARD-3: eine Nutzlast ueber 1 MiB wird mit 413 abgelehnt")
    void a_payload_above_one_mebibyte_is_rejected_with_413() throws Exception {
        String body = """
                {
                  "event_type": "order.changed",
                  "idempotency_key": "zu-gross-1",
                  "payload": { "data": "%s" }
                }""".formatted("x".repeat(1_100_000));

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    @DisplayName("S-HARD-4: ein Request-Body ueber 2 MiB wird mit 413 abgelehnt")
    void a_request_body_above_two_mebibytes_is_rejected_with_413() throws Exception {
        String body = "{\"event_type\": " + " ".repeat(2 * 1024 * 1024 + 64)
                + "\"idempotency_key\": \"zu-gross-2\", \"payload\": {\"a\": 1}}";

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    @DisplayName("S-HARD-5: die Fehlerantwort gibt keine internen Details preis")
    void the_error_answer_reveals_no_internal_details() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ kaputt"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .as("die Fehlerantwort nennt weder Stacktrace noch SQL noch Pfade")
                .doesNotContain("com.echobox")
                .doesNotContain("org.springframework")
                .doesNotContain("Exception")
                .doesNotContain("/home/")
                .doesNotContain("SELECT");
    }

    @Test
    @DisplayName("S-HARD-6: ein Mandantenparameter im Request-Body wird mit 422 abgelehnt")
    void a_tenant_parameter_in_the_body_is_rejected_with_422() throws Exception {
        String body = """
                {
                  "event_type": "order.changed",
                  "idempotency_key": "mit-mandant-1",
                  "organisation_id": "m-2",
                  "payload": { "a": 1 }
                }""";

        mockMvc.perform(post("/api/v1/events")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("tenant_parameter_rejected"));
    }

    @Test
    @DisplayName("S-HARD-7: nur die Betriebsrouten sind ohne Token erreichbar")
    void only_the_operational_routes_are_reachable_without_a_token() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/health/ready")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/endpoints")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/deliveries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }
}
