package com.echobox.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Integrationsebene — Secret-Rotation (US-003, FR-003).
 *
 * <p>Die Rotation ist der sicherheitskritischste Vorgang des Endpunktmoduls:
 * sie muss wirksam werden, darf das alte Secret aber nirgends offenlegen.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code rotateEndpointSecret}
 * (POST /api/v1/endpoints/{endpointId}/secret-rotation) und
 * {@code components.schemas.SecretRotationResponse}.
 */
class SecretRotationIT extends AbstractApplicationIT {

    private static final String TARGET_URL = "https://rotation.example.invalid/hooks/echobox";
    private static final String NEW_SECRET = "neues-s3cr3t-rotationswert";

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-003-1: die Rotation erhoeht die Secret-Version")
    void rotation_increases_the_secret_version() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint_id").value(endpointId))
                .andExpect(jsonPath("$.secret_version").value(2))
                .andExpect(jsonPath("$.rotated_at").isNotEmpty())
                .andExpect(jsonPath("$.rotated_by").isNotEmpty());
    }

    @Test
    @DisplayName("AC-003-2: die Rotationshistorie vermerkt Zeitpunkt und Identitaet")
    void the_rotation_history_records_time_and_actor() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "rotator-42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT secret_version, rotated_by, rotated_at FROM app.endpoint_secret_history WHERE endpoint_id = ?",
                endpointId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("rotated_by")).isEqualTo("rotator-42");
        assertThat(history.get(0).get("secret_version")).isEqualTo(2);
        assertThat(history.get(0).get("rotated_at")).isNotNull();
    }

    @Test
    @DisplayName("AC-003-2: das alte Secret wird nirgends im Klartext ausgegeben")
    void the_old_secret_is_never_returned_in_plaintext() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");
        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT encode(secret_ciphertext, 'escape') AS ciphertext FROM app.endpoints WHERE endpoint_id = ?",
                endpointId);
        assertThat(String.valueOf(rows.get(0).get("ciphertext"))).doesNotContain(NEW_SECRET);
    }

    @Test
    @DisplayName("AC-003-2: eine zweite Rotation erzeugt einen zweiten Eintrag in der Historie")
    void a_second_rotation_creates_a_second_history_entry() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "rotator-42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret_version").value(2));

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "rotator-42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"drittes-s3cr3t-rotationswert-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret_version").value(3));

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT secret_version FROM app.endpoint_secret_history WHERE endpoint_id = ? ORDER BY secret_version",
                endpointId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("secret_version")).isEqualTo(2);
        assertThat(history.get(1).get("secret_version")).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-003-3: ein Entwickler darf nicht rotieren (403) — das Secret bleibt unveraendert")
    void a_developer_may_not_rotate() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.developer("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        Integer version = jdbcTemplate.queryForObject(
                "SELECT secret_version FROM app.endpoints WHERE endpoint_id = ?", Integer.class, endpointId);
        assertThat(version).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-003-1: nach der Rotation gilt nur das neue Secret")
    void after_rotation_only_the_new_secret_is_stored() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT encode(secret_ciphertext, 'escape') AS ciphertext FROM app.endpoints WHERE endpoint_id = ?",
                endpointId);

        assertThat(String.valueOf(rows.get(0).get("ciphertext"))).doesNotContain("s3cr3t-rotationswert");
    }

    @Test
    @DisplayName("AC-003-3: ein fremder Endpunkt ergibt 404 — auch fuer den Service-Admin")
    void a_foreign_endpoint_yields_not_found() throws Exception {
        String endpointId = registerEndpoint("m-2", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.serviceAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + NEW_SECRET + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("AC-003-1: ein zu kurzes Secret wird mit 422 abgelehnt")
    void a_too_short_secret_is_rejected() throws Exception {
        String endpointId = registerEndpoint("m-1", TARGET_URL, "order.changed");

        mockMvc.perform(post("/api/v1/endpoints/" + endpointId + "/secret-rotation")
                        .with(Auth.receiverAdmin("m-1", "tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"kurz\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }
}
