package com.echobox.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.delivery.DeliveryHeaders;

/**
 * Vertragsebene — Signaturverfahren (US-009, AC-009-3, FR-020).
 *
 * <p>AC-009-3 verlangt: <em>„sind Verfahren und Header-Name der Signatur dort
 * benannt; ein Vertragstest schlaegt fehl, wenn eines von beiden fehlt."</em>
 *
 * <p>Der Test liest den eingefrorenen Vertrag {@code docs/api/openapi.yaml} und
 * vergleicht ihn mit der maschinenlesbaren Spezifikation
 * {@code tests/contract/openapi_signature_contract_test.yaml}. Zusaetzlich wird
 * geprueft, dass die Implementierung denselben Header-Name benutzt — der Vertrag
 * ist die Quelle, die Implementierung muss ihm folgen.
 *
 * <p>Testorakel: {@code docs/api/openapi.yaml} -&gt; {@code x-delivery-protocol.signature}
 * (eingefroren).
 */
class SignatureContractTest {

    private static Map<String, Object> contract;
    private static Map<String, Object> spec;

    @BeforeAll
    static void readContract() {
        contract = ContractFixture.contract();
        spec = ContractFixture.spec("openapi_signature_contract_test.yaml");
    }

    @Test
    @DisplayName("AC-009-3: der Vertrag nennt das Signaturverfahren")
    void the_contract_names_the_signature_algorithm() {
        Map<String, Object> expect = ContractFixture.mapAt(spec, "expect");

        Object algorithm = ContractFixture.at(contract, "x-delivery-protocol", "signature", "algorithm");

        assertThat(algorithm)
                .as("der Vertrag muss das Verfahren nennen — sonst schlaegt der Vertragstest fehl")
                .isNotNull();
        assertThat(algorithm).isEqualTo(expect.get("algorithm"));
    }

    @Test
    @DisplayName("AC-009-3: der Vertrag nennt den Header-Namen der Signatur")
    void the_contract_names_the_signature_header() {
        Map<String, Object> expect = ContractFixture.mapAt(spec, "expect");

        Object headerName = ContractFixture.at(contract, "x-delivery-protocol", "signature", "header_name");

        assertThat(headerName)
                .as("der Vertrag muss den Header-Namen nennen — sonst schlaegt der Vertragstest fehl")
                .isNotNull();
        assertThat(headerName).isEqualTo(expect.get("header_name"));
    }

    @Test
    @DisplayName("AC-009-3: die Signier-Eingabe ist festgeschrieben")
    void the_contract_names_the_signing_input() {
        Map<String, Object> expect = ContractFixture.mapAt(spec, "expect");

        Object signingInput = ContractFixture.at(contract, "x-delivery-protocol", "signature", "signing_input");

        assertThat(signingInput).isNotNull();
        assertThat(signingInput).isEqualTo(expect.get("signing_input"));
    }

    @Test
    @DisplayName("AC-009-3: die Implementierung benutzt den Header-Namen des Vertrags")
    void the_implementation_uses_the_contract_header_name() {
        Map<String, Object> expect = ContractFixture.mapAt(spec, "expect");

        assertThat(DeliveryHeaders.SIGNATURE)
                .as("die ausgehende Zustellung muss den eingefrorenen Header-Namen benutzen")
                .isEqualTo(expect.get("header_name"));
    }

    @Test
    @DisplayName("AC-009-3: der ausgehende Zustellvertrag ist als eingefroren markiert")
    void the_outgoing_delivery_contract_is_marked_frozen() {
        Object status = ContractFixture.at(contract, "x-delivery-protocol", "status");

        assertThat(status).isEqualTo("eingefroren");
    }

    @Test
    @DisplayName("AC-009-3: die Signatur-Kopfzeile ist im ausgehenden Vertrag als Pflicht gefuehrt")
    void the_signature_header_is_mandatory_in_the_outgoing_contract() {
        Object headers = ContractFixture.at(contract, "x-delivery-protocol", "request", "headers");

        assertThat(headers)
                .as("die Kopfzeilen des ausgehenden Vertrags muessen im Vertrag stehen")
                .isNotNull();

        boolean found = ContractFixture.listAt(contract, "x-delivery-protocol", "request", "headers").stream()
                .filter(item -> item instanceof Map)
                .map(item -> (Map<?, ?>) item)
                .anyMatch(header -> "X-EchoBox-Signature".equals(header.get("name")));

        assertThat(found).isTrue();
    }
}
