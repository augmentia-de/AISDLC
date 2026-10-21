package com.echobox.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.delivery.DeliveryHeaders;

/**
 * Vertragsebene — Ablehnungssignal (US-014, AC-014-2, FR-025).
 *
 * <p>AC-014-2 verlangt: <em>„ist das Signal dort beschrieben und ein
 * Vertragstest schlaegt fehl, wenn es fehlt."</em>
 *
 * <p>Geprueft werden der Statuscode 410, die Pflicht-Kopfzeile
 * {@code X-EchoBox-Rejection: permanent} und das Koerperformat
 * {@code DeliveryRejection} — gegen den eingefrorenen Vertrag
 * {@code docs/api/openapi.yaml} und gegen die Spezifikation
 * {@code tests/contract/openapi_rejection_contract_test.yaml}.
 */
class RejectionContractTest {

    private static Map<String, Object> contract;
    private static Map<String, Object> spec;

    @BeforeAll
    static void readContract() {
        contract = ContractFixture.contract();
        spec = ContractFixture.spec("openapi_rejection_contract_test.yaml");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> outcomes() {
        return ContractFixture.listAt(contract, "x-delivery-protocol", "response", "outcomes").stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rejectionOutcome() {
        Object statusCode = ContractFixture.mapAt(spec, "expect").get("status_code");
        return outcomes().stream()
                .filter(outcome -> statusCode.equals(outcome.get("status_code")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Der Vertrag beschreibt kein Ablehnungssignal mit dem Statuscode " + statusCode + "."));
    }

    @Test
    @DisplayName("AC-014-2: der Vertrag beschreibt das Ablehnungssignal mit dem Statuscode 410")
    void the_contract_describes_the_rejection_status_code() {
        Object statusCode = ContractFixture.mapAt(spec, "expect").get("status_code");

        assertThat(outcomes())
                .as("der Vertrag muss das Ablehnungssignal (410) beschreiben")
                .anyMatch(outcome -> statusCode.equals(outcome.get("status_code")));
    }

    @Test
    @DisplayName("AC-014-2: der Vertrag nennt die Pflicht-Kopfzeile des Ablehnungssignals")
    void the_contract_names_the_mandatory_rejection_header() {
        Map<String, Object> expectedHeader = ContractFixture.mapAt(spec, "expect", "required_header");

        Object requiredHeaders = rejectionOutcome().get("required_headers");

        assertThat(requiredHeaders)
                .as("das Signal muss die Pflicht-Kopfzeile benennen")
                .isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> headers = ((List<Map<String, Object>>) requiredHeaders).stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .toList();

        assertThat(headers)
                .as("die Kopfzeile %s: %s muss im Vertrag stehen",
                        expectedHeader.get("name"), expectedHeader.get("value"))
                .anyMatch(header -> expectedHeader.get("name").equals(header.get("name"))
                        && expectedHeader.get("value").equals(header.get("value")));
    }

    @Test
    @DisplayName("AC-014-2: der Vertrag beschreibt das Koerperformat des Ablehnungssignals")
    void the_contract_describes_the_rejection_body_schema() {
        Object expectedSchema = ContractFixture.mapAt(spec, "expect").get("body_schema");

        Object body = rejectionOutcome().get("body");

        assertThat(body).as("der Koerper des Ablehnungssignals muss im Vertrag beschrieben sein").isNotNull();

        Object schemaRef = ((Map<?, ?>) body).get("schema");

        assertThat(schemaRef).isNotNull();
        assertThat(String.valueOf(schemaRef)).contains(String.valueOf(expectedSchema));
    }

    @Test
    @DisplayName("AC-014-2: das Schema DeliveryRejection verlangt die Felder rejection und reason")
    void the_rejection_schema_requires_rejection_and_reason() {
        Object required = ContractFixture.at(contract, "components", "schemas", "DeliveryRejection", "required");

        assertThat(required).isNotNull();
        assertThat(asList(required))
                .containsAll(ContractFixture.listAt(spec, "expect", "body_required_fields"));
    }

    @Test
    @DisplayName("AC-014-2: die Implementierung kennt den Ablehnungs-Header des Vertrags")
    void the_implementation_knows_the_rejection_header() {
        Object expectedName = ContractFixture.mapAt(spec, "expect", "required_header").get("name");

        assertThat(DeliveryHeaders.REJECTION).isEqualTo(expectedName);
    }

    @Test
    @DisplayName("AC-014-2: die Ablehnung kennt genau den einen Wert 'permanent'")
    void the_rejection_knows_exactly_one_value() {
        Object enumValues = ContractFixture.at(contract, "components", "schemas", "DeliveryRejection",
                "properties", "rejection", "enum");

        assertThat(enumValues).isNotNull();
        assertThat(asList(enumValues))
                .containsExactlyElementsOf(ContractFixture.listAt(spec, "expect", "body_enum_rejection"));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
