package com.echobox.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vertragsebene — die als „immer vorhanden" beschriebenen Antwortfelder
 * (Befund V-46, US-004/US-016/US-017, FR-004, FR-031, FR-032).
 *
 * <p>Mehrere Stellen des Vertrags sagen ausdruecklich, dass ein Feld
 * <em>immer</em> gefuellt ist:
 * <ul>
 *   <li>{@code organisation_id} in {@code Endpoint}, {@code DeliveryRun} und
 *       {@code DeadDelivery} — „immer gefuellt" (V-26);</li>
 *   <li>{@code rate_limit} in {@code EndpointMetrics} — „immer Teil der Antwort" (V-42);</li>
 *   <li>{@code abandoned_at} in {@code DeadDelivery} — „immer gefuellt";</li>
 *   <li>{@code data_available} in {@code EndpointMetrics} — die Kennzahlen ohne
 *       Daten werden als {@code false} ausgewiesen, nicht als 0 %.</li>
 * </ul>
 *
 * <p>Dieser Test haelt fest, dass der Vertrag diese Felder als <em>required</em>
 * fuehrt — ein Vertragstest, der fehlschlaegt, wenn die Zusage fehlt. Die
 * Laufzeitpruefung steht in
 * {@code tests/integration/security/AlwaysPresentFieldsIT.java}.
 */
class AlwaysPresentFieldsContractTest {

    private static Map<String, Object> contract;

    @BeforeAll
    static void readContract() {
        contract = ContractFixture.contract();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> required(String schema) {
        Object value = ContractFixture.at(contract, "components", "schemas", schema, "required");
        assertThat(value).as("das Schema %s muss eine required-Liste fuehren", schema).isNotNull();
        return (List<Object>) value;
    }

    @Test
    @DisplayName("V-46 (AC-004-2): organisation_id ist in Endpoint, DeliveryRun und DeadDelivery pflicht")
    void organisation_id_is_required_wherever_it_is_promised() {
        assertThat(required("Endpoint")).contains("organisation_id");
        assertThat(required("DeliveryRun")).contains("organisation_id");
        assertThat(required("DeadDelivery")).contains("organisation_id");
    }

    @Test
    @DisplayName("V-46 (AC-004-2): organisation_id wird im Vertrag als 'immer gefuellt' beschrieben")
    void organisation_id_is_described_as_always_filled() {
        List<String> descriptions = List.of(
                String.valueOf(ContractFixture.at(contract, "components", "schemas", "Endpoint",
                        "properties", "organisation_id", "description")),
                String.valueOf(ContractFixture.at(contract, "components", "schemas", "DeliveryRun",
                        "properties", "organisation_id", "description")),
                String.valueOf(ContractFixture.at(contract, "components", "schemas", "DeadDelivery",
                        "properties", "organisation_id", "description")));

        assertThat(descriptions)
                .as("der Vertrag sagt ausdruecklich 'immer gefuellt'")
                .allMatch(description -> description.contains("immer"));
    }

    @Test
    @DisplayName("V-46 (AC-018-4): der Abschnitt rate_limit ist in EndpointMetrics pflicht")
    void rate_limit_is_required_in_the_metrics() {
        assertThat(required("EndpointMetrics")).contains("rate_limit");
    }

    @Test
    @DisplayName("V-46 (AC-018-4): rate_limit.limit ist nie null")
    void the_effective_rate_limit_is_never_null() {
        assertThat(required("RateLimitObservation")).contains("limit");
        assertThat(String.valueOf(ContractFixture.at(contract, "components", "schemas", "RateLimitObservation",
                "properties", "limit", "description")))
                .contains("nie");
    }

    @Test
    @DisplayName("V-46 (AC-016-1): abandoned_at ist in der toten Zustellung pflicht")
    void abandoned_at_is_required_in_a_dead_delivery() {
        assertThat(required("DeadDelivery")).contains("abandoned_at");
    }

    @Test
    @DisplayName("V-46 (AC-017-3): data_available ist in EndpointMetrics pflicht — 'keine Daten' ist ein eigener Wert")
    void data_available_is_required_in_the_metrics() {
        assertThat(required("EndpointMetrics")).contains("data_available");
    }

    @Test
    @DisplayName("V-46 (AC-015-2): next_attempt_at ist in endgueltigen Zustaenden leer, aber nicht verboten")
    void next_attempt_at_may_be_empty_but_is_not_forbidden() {
        Object nullable = ContractFixture.at(contract, "components", "schemas", "DeliveryRun",
                "properties", "next_attempt_at", "nullable");

        assertThat(nullable)
                .as("next_attempt_at ist nullable — bei endgueltigen Zustaenden leer (AC-015-2)")
                .isEqualTo(true);
    }
}
