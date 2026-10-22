package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.EchoBoxApplication;
import com.echobox.testsupport.PostgresTestContainer;
import com.echobox.testsupport.TestDeliveryConfiguration;

/**
 * Integrationsebene — eine **nicht unterstuetzte HTTP-Methode** (S-36).
 *
 * <p><strong>Der Fund.</strong> Die Fehlerabbildung des Dienstes kennt eigene
 * Faelle fuer einen nicht unterstuetzten Medientyp (S-30, HTTP 400) und fuer
 * einen Multipart-Body (HTTP 400). Die <strong> Methode</strong> hat keinen
 * eigenen Fall: {@code HttpRequestMethodNotSupportedException} faellt in den
 * allgemeinen Handler, der HTTP <strong>500</strong> {@code internal_error}
 * antwortet und den **Stacktrace** ins Protokoll schreibt.
 *
 * <p>Der Pfad ist <strong>ohne Anmeldung</strong> erreichbar: die
 * Gesundheitspfade sind {@code permitAll} — ein {@code POST /health} genuegt.
 *
 * <p><strong>Was dieser Test festnagelt:</strong> die
 * <em>sicherheitsrelevante</em> Eigenschaft der Antwort — sie gibt
 * <strong>kein internes Detail</strong> preis (kein Stacktrace, keine Klasse,
 * kein Pfad). Der <em>Statuscode</em> ist der offene Teil des Befunds: er
 * sollte ein 4xx sein (400 {@code malformed_request}) statt 500 — der
 * eingefrorene Vertrag kennt fuer einen Fehler des Aufrufers nur 4xx. Der
 * Abnahmetest fuer diese Korrektur steht in
 * {@code docs/qa/bugs/BUG-S-36.json}; bis dahin belegt dieser Test, dass die
 * Antwort **keinen** internen Detail preisgibt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class UnsupportedMethodErrorIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerDataSource(registry);
        registry.add("echobox.delivery.worker.enabled", () -> "false");
    }

    @Test
    @DisplayName("S-36: eine nicht unterstuetzte Methode gibt kein internes Detail preis")
    void an_unsupported_method_leaks_no_internal_detail() throws Exception {
        HttpResponse<String> response = send("POST", "/health");

        assertThat(response.statusCode())
                .as("eine nicht unterstuetzte Methode ist ein Fehler des Aufrufers — er darf "
                        + "kein 2xx sein")
                .isGreaterThanOrEqualTo(300);
        assertThat(response.body())
                .as("die Antwort traegt weder Stacktrace noch Klassen- oder Pfadnamen — der "
                        + "Fehler darf keine internen Details preisgeben")
                .doesNotContain("org.springframework")
                .doesNotContain("com.echobox")
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("Request method");
    }

    @Test
    @DisplayName("S-36: die Antwort traegt das Fehlerschema des Vertrags")
    void the_answer_carries_the_problem_schema() throws Exception {
        HttpResponse<String> response = send("POST", "/health");

        assertThat(response.body())
                .as("jede Fehlerantwort traegt das Problem-Schema des Vertrags")
                .contains("\"code\"")
                .contains("\"status\"")
                .contains("\"title\"");
        assertThat(response.statusCode())
                .as("der Status der Antwort entspricht dem Status im Fehlerschema")
                .isEqualTo(Integer.parseInt(response.body().replaceAll("(?s).*\"status\"\\s*:\\s*(\\d+).*", "$1")));
    }

    /**
     * Sendet eine Anfrage mit der angegebenen Methode.
     *
     * @param method die HTTP-Methode
     * @param path   der Pfad
     * @return die Antwort
     * @throws Exception wenn der Versand scheitert
     */
    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
