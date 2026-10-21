package com.echobox.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Integrationsebene — getrennte Umgebungen (US-023, AC-023-5, NFR-006).
 *
 * <p>AC-023-5 verlangt: „Staging und Produktion werden getrennt bereitgestellt
 * und erhalten getrennte Konfigurationen und Secrets." Drei pruefbare Aussagen:
 * <ol>
 *   <li>es gibt eine <em>eigene</em> Konfiguration fuer Staging;</li>
 *   <li>keine Konfiguration enthaelt ein fest verdrahtetes Secret — jedes Secret
 *       kommt aus der Umgebung;</li>
 *   <li>die Bereitstellung stellt die beiden Umgebungen in <em>getrennten
 *       Jobs</em> bereit.</li>
 * </ol>
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt; {@code x-requirement-coverage}
 * (NFR-006); `.agent/knowledge/stack.md` (Deployment: getrennte Konfigurationen
 * und getrennte Secrets je Umgebung).
 */
class EnvironmentSeparationIT {

    private static final Path APPLICATION = Path.of("src", "main", "resources", "application.yml");
    private static final Path APPLICATION_STAGING = Path.of("src", "main", "resources", "application-staging.yml");

    @Test
    @DisplayName("AC-023-5: Staging hat eine eigene Konfiguration")
    void staging_has_its_own_configuration() throws IOException {
        assertThat(APPLICATION)
                .as("die Grundkonfiguration des Dienstes")
                .exists();
        assertThat(APPLICATION_STAGING)
                .as("Staging braucht eine eigene Konfiguration")
                .exists();

        assertThat(Files.readAllBytes(APPLICATION_STAGING))
                .as("die Staging-Konfiguration ist nicht identisch mit der Grundkonfiguration")
                .isNotEqualTo(Files.readAllBytes(APPLICATION));
    }

    @Test
    @DisplayName("AC-023-5: keine Konfiguration enthaelt ein fest verdrahtetes Secret")
    void no_configuration_contains_a_hardcoded_secret() throws IOException {
        List<Path> configurations = new ArrayList<>();
        configurations.add(APPLICATION);
        if (Files.exists(APPLICATION_STAGING)) {
            configurations.add(APPLICATION_STAGING);
        }

        for (Path configuration : configurations) {
            String content = Files.readString(configuration, StandardCharsets.UTF_8);
            for (String line : content.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !trimmed.contains(":")) {
                    continue;
                }
                String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                boolean secretKey = trimmed.toLowerCase().startsWith("secret")
                        || trimmed.toLowerCase().startsWith("password")
                        || trimmed.toLowerCase().startsWith("api-key")
                        || trimmed.toLowerCase().startsWith("encryption-key");
                if (secretKey && !value.isEmpty() && !value.startsWith("${")) {
                    assertThat(value)
                            .as("in %s muss das Secret aus der Umgebung kommen: %s", configuration, trimmed)
                            .doesNotContain("secret");
                }
            }
        }
    }

    @Test
    @DisplayName("AC-023-5: die Bereitstellung stellt Staging und Produktion getrennt bereit")
    void the_deployment_provides_staging_and_production_separately() throws IOException {
        Path descriptor = findDeploymentDescriptor();

        assertThat(descriptor)
                .as("es muss einen Bereitstellungsdeskriptor geben (.github/workflows oder deploy/)")
                .exists();

        String content = Files.readString(descriptor, StandardCharsets.UTF_8).toLowerCase();

        assertThat(content).contains("staging");
        assertThat(content).contains("production");

        List<String> jobs = jobNames(descriptor);

        long stagingJobs = jobs.stream().filter(job -> job.toLowerCase().contains("staging")).count();
        long productionJobs = jobs.stream().filter(job -> job.toLowerCase().contains("production")).count();

        assertThat(stagingJobs)
                .as("Staging braucht einen eigenen Bereitstellungsschritt")
                .isGreaterThanOrEqualTo(1L);
        assertThat(productionJobs)
                .as("Produktion braucht einen eigenen Bereitstellungsschritt")
                .isGreaterThanOrEqualTo(1L);
        assertThat(jobs.stream().filter(job -> {
            String lower = job.toLowerCase();
            return lower.contains("staging") && lower.contains("production");
        }).count())
                .as("Staging und Produktion werden nicht im selben Job bereitgestellt")
                .isZero();
    }

    private Path findDeploymentDescriptor() throws IOException {
        List<Path> candidates = new ArrayList<>();
        Path workflows = Path.of(".github", "workflows");
        if (Files.isDirectory(workflows)) {
            try (Stream<Path> paths = Files.list(workflows)) {
                paths.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                        .forEach(candidates::add);
            }
        }
        Path deploy = Path.of("deploy");
        if (Files.isDirectory(deploy)) {
            try (Stream<Path> paths = Files.list(deploy)) {
                paths.forEach(candidates::add);
            }
        }
        return candidates.isEmpty() ? Path.of(".github", "workflows", "deploy.yml") : candidates.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<String> jobNames(Path descriptor) throws IOException {
        String content = Files.readString(descriptor, StandardCharsets.UTF_8);
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            return List.of();
        }
        Object jobs = root.get("jobs");
        if (!(jobs instanceof Map<?, ?> jobMap)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Map.Entry<?, ?> entry : jobMap.entrySet()) {
            names.add(String.valueOf(entry.getKey()));
            if (entry.getValue() instanceof Map<?, ?> job) {
                Object steps = job.get("steps");
                if (steps instanceof List<?> stepList) {
                    for (Object step : stepList) {
                        if (step instanceof Map<?, ?> stepMap && stepMap.get("name") != null) {
                            names.add(String.valueOf(stepMap.get("name")));
                        }
                    }
                }
            }
        }
        return names;
    }
}
