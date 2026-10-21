package com.echobox.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.echobox.testsupport.AbstractApplicationIT;

/**
 * Integrationsebene — Containerbetrieb (US-023, NFR-006).
 *
 * <p>AC-023-1 verlangt: das Container-Image startet mit einem
 * Gesundheitsendpunkt, enthaelt keine Secrets und laeuft als nicht privilegierter
 * Benutzer. Der erste Teil wird am laufenden Dienst geprueft (Health und
 * Readiness antworten **ohne** Authentifizierung), der zweite am Image-Build
 * (Dockerfile).
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code getHealth} und
 * {@code getReadiness}.
 */
class ContainerStartupIT extends AbstractApplicationIT {

    private static final Path DOCKERFILE = Path.of("Dockerfile");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        disableDeliveryWorker(registry);
    }

    @Test
    @DisplayName("AC-023-1: der Gesundheitsendpunkt antwortet ohne Authentifizierung")
    void the_health_endpoint_answers_without_authentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.service").value("echobox"))
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.checked_at").isNotEmpty());
    }

    @Test
    @DisplayName("AC-023-1: der Readiness-Endpunkt meldet die Bereitschaft")
    void the_readiness_endpoint_reports_readiness() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.checks.database").value("ok"))
                .andExpect(jsonPath("$.checks.delivery_worker").value("ok"))
                .andExpect(jsonPath("$.checks.tenant_context").value("ok"));
    }

    @Test
    @DisplayName("AC-023-1: das Image laeuft als nicht privilegierter Benutzer und enthaelt keine Secrets")
    void the_image_runs_as_non_root_and_contains_no_secrets() throws IOException {
        assertThat(DOCKERFILE)
                .as("das Image-Build ist Teil von Schritt 6")
                .exists();

        String dockerfile = Files.readString(DOCKERFILE, StandardCharsets.UTF_8);

        assertThat(dockerfile.toLowerCase())
                .as("das Image setzt einen nicht privilegierten Benutzer")
                .contains("user ");

        assertThat(dockerfile.toLowerCase())
                .as("das Image enthaelt keine fest verdrahteten Zugangsdaten")
                .doesNotContain("password=")
                .doesNotContain("secret_key")
                .doesNotContain("begin private key");
    }

    @Test
    @DisplayName("AC-023-1: im Projekt ist kein fest verdrahtetes Secret hinterlegt")
    void the_project_contains_no_hardcoded_secret() throws IOException {
        List<Path> candidates;
        try (Stream<Path> paths = Files.walk(Path.of("src"))) {
            candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(ContainerStartupIT::isSourceArtifact)
                    .toList();
        }

        for (Path candidate : candidates) {
            // ISO-8859-1 bildet jedes Byte auf ein Zeichen ab — der Nachweis
            // gilt damit auch fuer binaere Dateien, und der Test bricht nicht
            // an einem Build-Artefakt mit ungueltiger UTF-8-Folge ab.
            String content = new String(Files.readAllBytes(candidate), StandardCharsets.ISO_8859_1);
            assertThat(content)
                    .as("in %s darf kein Schluessel stehen", candidate)
                    .doesNotContain("BEGIN PRIVATE KEY")
                    .doesNotContain("AES_KEY=");
        }
    }

    /**
     * Prueft, ob eine Datei ein Quellartefakt ist.
     *
     * <p>Erzeugte Verzeichnisse ({@code node_modules}, {@code .next},
     * {@code target}) sind keine Projektartefakte im Sinne der Anforderung —
     * sie werden aus dem Build erzeugt, stehen nicht im Versionsverwaltung
     * und enthalten keinen Schluessel.
     *
     * @param path die Datei
     * @return true, wenn die Datei geprueft werden soll
     */
    private static boolean isSourceArtifact(Path path) {
        for (Path segment : path) {
            String name = segment.toString();
            if ("node_modules".equals(name) || ".next".equals(name) || "target".equals(name)) {
                return false;
            }
        }
        return true;
    }
}
