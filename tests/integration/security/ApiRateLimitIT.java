package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echobox.EchoBoxApplication;
import com.echobox.testsupport.Auth;
import com.echobox.testsupport.MutableClock;
import com.echobox.testsupport.PostgresTestContainer;
import com.echobox.testsupport.TestDeliveryConfiguration;

/**
 * Integrationsebene — das <strong>Anfragelimit der API</strong> (S-24).
 *
 * <p>Der eingefrorene Vertrag sagt fuer <em>jede</em> Operation HTTP
 * <strong>429</strong> {@code rate_limit_exceeded} mit dem Header
 * {@code Retry-After} zu („API-seitiges Anfragelimit, nicht das Endpunkt-Limit
 * aus FR-033"). Dieser Test beweist, dass die Zusage eingeloest wird:
 * <ul>
 *   <li>eine Anfrage ueber dem Limit ergibt 429 mit {@code Retry-After} und
 *       dem Fehlerschema des Vertrags,</li>
 *   <li>ein <strong>anderer</strong> Mandant ist nicht betroffen,</li>
 *   <li>der Gesundheitsendpunkt ist nicht begrenzt,</li>
 *   <li>die Betriebskonfiguration nennt ein Limit — die Zusage ist also auch
 *       im Auslieferungsstand aktiv.</li>
 * </ul>
 */
@SpringBootTest(classes = EchoBoxApplication.class)
@AutoConfigureMockMvc
@Import(TestDeliveryConfiguration.class)
class ApiRateLimitIT {

    /** Das Limit dieses Tests — bewusst klein, damit die Zusage sichtbar wird. */
    private static final int LIMIT = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerDataSource(registry);
        registry.add("echobox.delivery.worker.enabled", () -> "false");
        registry.add("echobox.rate-limit.limit", () -> String.valueOf(LIMIT));
        registry.add("echobox.rate-limit.window-seconds", () -> "60");
    }

    /**
     * Beginnt jeden Testfall in einem **neuen** Zeitfenster — der Zaehler des
     * Anfragelimits zaehlt je Fenster, nicht je Testklasse.
     */
    @BeforeEach
    void newWindow() {
        clock.advance(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("S-24: eine Anfrage ueber dem Limit ergibt 429 mit 'Retry-After'")
    void a_request_above_the_limit_is_answered_with_429() throws Exception {
        MvcResult last = null;
        for (int attempt = 0; attempt <= LIMIT; attempt++) {
            last = mockMvc.perform(get("/api/v1/endpoints")
                    .with(Auth.developer("m-1", "tester"))).andReturn();
        }

        assertThat(last).isNotNull();
        assertThat(last.getResponse().getStatus())
                .as("die %d. Anfrage ueberschreitet das Limit von %d", LIMIT + 1, LIMIT)
                .isEqualTo(429);
        assertThat(last.getResponse().getHeader("Retry-After"))
                .as("die 429-Antwort muss den Header 'Retry-After' tragen")
                .isNotBlank();
        assertThat(Integer.parseInt(last.getResponse().getHeader("Retry-After")))
                .as("'Retry-After' ist die Wartezeit in Sekunden (mindestens 1)")
                .isGreaterThanOrEqualTo(1);
        assertThat(last.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .as("die Ablehnung traegt das Fehlerschema des Vertrags")
                .contains("rate_limit_exceeded")
                .contains("\"status\":429");
    }

    @Test
    @DisplayName("S-24: ein anderer Mandant ist von dem Limit nicht betroffen")
    void another_tenant_is_not_affected() throws Exception {
        for (int attempt = 0; attempt <= LIMIT; attempt++) {
            mockMvc.perform(get("/api/v1/endpoints")
                    .with(Auth.developer("m-1", "tester"))).andReturn();
        }

        MvcResult other = mockMvc.perform(get("/api/v1/endpoints")
                .with(Auth.developer("m-2", "tester"))).andReturn();

        assertThat(other.getResponse().getStatus())
                .as("das Anfragelimit gilt je Mandant")
                .isNotEqualTo(429);
    }

    @Test
    @DisplayName("S-24: der Gesundheitsendpunkt ist nicht begrenzt")
    void the_health_endpoint_is_not_rate_limited() throws Exception {
        for (int attempt = 0; attempt <= LIMIT * 3; attempt++) {
            mockMvc.perform(get("/health")).andReturn();
        }

        MvcResult health = mockMvc.perform(get("/health")).andReturn();

        assertThat(health.getResponse().getStatus())
                .as("der Gesundheitsendpunkt muss immer erreichbar bleiben")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("S-24: die Betriebskonfiguration nennt ein Limit — die Zusage ist im Auslieferungsstand aktiv")
    void the_configuration_defines_a_limit() throws IOException {
        String configuration = Files.readString(
                Path.of("src", "main", "resources", "application.yml"), StandardCharsets.UTF_8);

        assertThat(configuration)
                .as("das Anfragelimit kommt aus der Konfiguration (ADR-009)")
                .contains("rate-limit:")
                .contains("ECHobox_RATE_LIMIT");
    }
}
