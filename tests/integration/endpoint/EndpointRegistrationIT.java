package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;
import com.echobox.testsupport.Auth;

/**
 * Integrationsebene — Registrierung eines Endpunkts (US-001, FR-001).
 *
 * <p>Geprueft wird der HTTP-Vertrag: Statuscode, Pflichtfelder, Fehlerformat
 * ({@code Problem}) und das Fehlen des Secrets in der Antwort.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code createEndpoint}
 * (POST /api/v1/endpoints) und {@code components.schemas.EndpointCreateRequest}.
 */
class EndpointRegistrationIT extends AbstractApplicationIT {

    private static final String VALID_BODY = """
            {
              "target_url": "https://orders.example.invalid/hooks/echobox",
              "secret": "s3cr3t-rotationswert",
              "event_types": ["order.changed"]
            }""";

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-001-1: die Registrierung liefert 201, Status 'aktiv' und die ID des Endpunkts")
    void registration_creates_an_active_endpoint() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endpoint_id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.event_types[0]").value("order.changed"))
                .andExpect(jsonPath("$.secret_version").value(1));
    }

    @Test
    @DisplayName("AC-001-1: der Mandant stammt aus der Identitaet, nicht aus der Anfrage")
    void the_tenant_comes_from_the_identity() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organisation_id").value("m-1"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT organisation_id FROM app.endpoints ORDER BY created_at DESC LIMIT 1");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("organisation_id")).isEqualTo("m-1");
    }

    @Test
    @DisplayName("AC-001-2: eine Ziel-URL ohne https wird mit 422 abgelehnt und legt keinen Endpunkt an")
    void a_non_https_target_url_is_rejected() throws Exception {
        long before = countEndpoints();

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target_url": "http://orders.example.invalid/hooks/echobox",
                                  "secret": "s3cr3t-rotationswert",
                                  "event_types": ["order.changed"]
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations[0].field").value("target_url"));

        assertThat(countEndpoints()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-001-3: eingebettete Zugangsdaten werden mit 422 abgelehnt")
    void embedded_credentials_are_rejected() throws Exception {
        long before = countEndpoints();

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target_url": "https://user:pass@orders.example.invalid/hooks/echobox",
                                  "secret": "s3cr3t-rotationswert",
                                  "event_types": ["order.changed"]
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations[0].field").value("target_url"));

        assertThat(countEndpoints()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-001-4: die Antwort enthaelt das Secret nicht im Klartext")
    void the_response_never_contains_the_plaintext_secret() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.secret_ciphertext").doesNotExist());
    }

    @Test
    @DisplayName("AC-001-4: das Secret wird verschluesselt abgelegt")
    void the_secret_is_stored_encrypted() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT encode(secret_ciphertext, 'escape') AS ciphertext FROM app.endpoints "
                        + "WHERE organisation_id = 'm-1' ORDER BY created_at DESC LIMIT 1");

        assertThat(rows).hasSize(1);
        assertThat(String.valueOf(rows.get(0).get("ciphertext")))
                .doesNotContain("s3cr3t-rotationswert");
    }

    @Test
    @DisplayName("AC-001-5: ein Mandantenparameter im Body wird mit 422 abgelehnt")
    void a_tenant_parameter_in_the_body_is_rejected() throws Exception {
        long before = countEndpoints();

        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target_url": "https://orders.example.invalid/hooks/echobox",
                                  "secret": "s3cr3t-rotationswert",
                                  "event_types": ["order.changed"],
                                  "organisation_id": "m-2"
                                }"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("tenant_parameter_rejected"));

        assertThat(countEndpoints()).isEqualTo(before);
    }

    @Test
    @DisplayName("AC-001-1: die Antwort traegt den Location-Header des neuen Endpunkts")
    void the_response_carries_the_location_header() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/endpoints/")));
    }

    @Test
    @DisplayName("AC-001-1: ein Entwickler darf keinen Endpunkt registrieren (403)")
    void a_developer_may_not_register_an_endpoint() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.developer("m-1", "user-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("AC-001-1: ohne Token antwortet der Dienst mit 401")
    void an_unauthenticated_request_is_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    @DisplayName("AC-001-1: ein syntaktisch falscher Body ergibt 400, kein 422")
    void a_malformed_body_is_a_bad_request() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .with(Auth.receiverAdmin("m-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ kein json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    private long countEndpoints() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM app.endpoints", Long.class);
        return count == null ? 0L : count;
    }
}
