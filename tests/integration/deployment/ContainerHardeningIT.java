package com.echobox.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Integrationsebene — die Haertung des Container-Builds und der
 * Bereitstellung (S-26, S-27) sowie das Coverage-Gate des Builds (A-1).
 *
 * <p>Geprueft werden **Konfiguration und Dateien**, nicht das Laufzeitverhalten:
 * <ul>
 *   <li><strong>S-26</strong> — die {@code apt-get}-Pakete stehen mit Version
 *       fest; das Image ist damit reproduzierbar.</li>
 *   <li><strong>S-27</strong> — es gibt ein eigenes Produktionsprofil, in dem
 *       der Verschluesselungsschluessel Pflicht ist, und das Image ist nicht
 *       fest auf das Staging-Profil gepinnt.</li>
 *   <li><strong>A-1</strong> — der Build erzwingt die Coverage-Schwelle von
 *       80 % (JaCoCo).</li>
 * </ul>
 */
class ContainerHardeningIT {

    private static final Path DOCKERFILE = Path.of("Dockerfile");

    private static final Path POM = Path.of("pom.xml");

    private static final Path APPLICATION = Path.of("src", "main", "resources", "application.yml");

    private static final Path APPLICATION_PRODUCTION =
            Path.of("src", "main", "resources", "application-production.yml");

    /** Das Muster eines {@code apt-get install}-Aufrufs. */
    private static final Pattern APT_INSTALL = Pattern.compile("apt-get install[^\\n]*");

    /** Das Muster der gesetzten Spring-Profile (ENV-Zeile). */
    private static final Pattern PROFILE =
            Pattern.compile("SPRING_PROFILES_ACTIVE=([A-Za-z0-9_-]+)");

    @Test
    @DisplayName("S-26: die apt-get-Pakete sind gepinnt")
    void the_apt_packages_are_pinned() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE, StandardCharsets.UTF_8);

        String install = aptInstallCommand(dockerfile);
        assertThat(install)
                .as("das Image muss Pakete ueber apt-get installieren")
                .isNotEmpty();
        for (String packageName : List.of("curl", "tini")) {
            assertThat(install)
                    .as("'%s' muss mit einer Version gepinnt sein (<paket>=<version>)", packageName)
                    .contains(packageName + "=");
        }
    }

    /**
     * Liefert den {@code apt-get install}-Aufruf des Image-Builds — er
     * laeuft ueber mehrere Zeilen (Zeilenfortsetzung mit {@code \}).
     *
     * @param dockerfile der Inhalt des Dockerfiles
     * @return der Aufruf oder {@code ""}, wenn es keinen gibt
     */
    private static String aptInstallCommand(String dockerfile) {
        Matcher matcher = APT_INSTALL.matcher(dockerfile);
        if (!matcher.find()) {
            return "";
        }
        StringBuilder command = new StringBuilder(matcher.group());
        int index = dockerfile.indexOf('\n', matcher.end());
        if (index < 0) {
            return command.toString();
        }
        index++;
        while (index < dockerfile.length()) {
            int lineEnd = dockerfile.indexOf('\n', index);
            if (lineEnd < 0) {
                lineEnd = dockerfile.length();
            }
            String line = dockerfile.substring(index, lineEnd).trim();
            command.append(' ').append(line);
            if (!line.endsWith("\\")) {
                break;
            }
            index = lineEnd + 1;
        }
        return command.toString();
    }

    @Test
    @DisplayName("S-26: die gepinnten Versionen haben einen Vorgabewert")
    void the_pinned_versions_have_a_default() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE, StandardCharsets.UTF_8);

        assertThat(dockerfile)
                .as("ohne Vorgabewert waere der Build nicht reproduzierbar")
                .containsPattern("ARG CURL_VERSION=[0-9][^\\s]*")
                .containsPattern("ARG TINI_VERSION=[0-9][^\\s]*");

        String install = aptInstallCommand(dockerfile);
        assertThat(install)
                .as("die Pakete muessen die gepinnten Variablen verwenden")
                .contains("curl=${CURL_VERSION}")
                .contains("tini=${TINI_VERSION}");
    }

    @Test
    @DisplayName("S-27: es gibt ein eigenes Produktionsprofil mit Pflicht-Schluessel")
    void there_is_a_production_profile_with_a_mandatory_key() throws IOException {
        assertThat(APPLICATION_PRODUCTION)
                .as("ohne eigenes Produktionsprofil greift das Default-Profil — "
                        + "der Schluessel waere nicht Pflicht")
                .exists();

        String configuration = Files.readString(APPLICATION_PRODUCTION, StandardCharsets.UTF_8);
        assertThat(configuration)
                .as("das Produktionsprofil muss aktiviert sein")
                .contains("on-profile: production");
        assertThat(configuration)
                .as("im Produktivbetrieb ist der Verschluesselungsschluessel Pflicht")
                .contains("key-required: true");
        assertThat(configuration)
                .as("der Schluessel kommt aus der Umgebung")
                .contains("${ECHobox_CRYPTO_KEY:");
    }

    @Test
    @DisplayName("S-27: das Image ist nicht fest auf das Staging-Profil gepinnt")
    void the_image_is_not_pinned_to_the_staging_profile() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE, StandardCharsets.UTF_8);

        String profile = profileOf(dockerfile);
        assertThat(profile)
                .as("das Image ist das Produktionsartefakt — der Vorgabewert ist 'production'")
                .isEqualTo("production");
    }

    /**
     * Liefert das im Image gesetzte Spring-Profil.
     *
     * @param dockerfile der Inhalt des Dockerfiles
     * @return das Profil oder {@code ""}, wenn keines gesetzt ist
     */
    private static String profileOf(String dockerfile) {
        Matcher matcher = PROFILE.matcher(dockerfile);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    @Test
    @DisplayName("S-27: die Bereitstellung legt das Profil je Umgebung fest")
    void the_deployment_sets_the_profile_per_environment() throws IOException {
        Path descriptor = Path.of(".github", "workflows", "deploy.yml");
        assertThat(descriptor).exists();

        String content = Files.readString(descriptor, StandardCharsets.UTF_8);

        assertThat(content)
                .as("Staging laeuft im Staging-Profil")
                .contains("SPRING_PROFILES_ACTIVE: staging");
        assertThat(content)
                .as("Produktion laeuft im Produktionsprofil")
                .contains("SPRING_PROFILES_ACTIVE: production");
    }

    @Test
    @DisplayName("A-1: der Build erzwingt die Coverage-Schwelle von 80 %")
    void the_build_enforces_the_coverage_threshold() throws IOException {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);

        assertThat(pom)
                .as("der Build braucht das JaCoCo-Plugin")
                .contains("jacoco-maven-plugin");
        assertThat(pom)
                .as("das Gate prueft die Zeilenabdeckung")
                .contains("<counter>LINE</counter>")
                .contains("<value>COVEREDRATIO</value>");
        assertThat(pom)
                .as("die Schwelle liegt bei 80 %")
                .contains("<minimum>0.80</minimum>");
    }

    @Test
    @DisplayName("S-19: die Build-Datei hebt die Version des eingebetteten Containers an")
    void the_build_pins_the_embedded_container() throws IOException {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);

        assertThat(pom)
                .as("der eingebettete Container braucht mindestens 10.1.55 (S-19)")
                .contains("<tomcat.version>10.1.55</tomcat.version>");
        assertThat(pom)
                .as("Spring Boot 3.5.16 bringt Tomcat 10.1.55 mit")
                .contains("<version>3.5.16</version>");
    }

    @Test
    @DisplayName("S-27: die Grundkonfiguration laesst den Schluessel im Default-Profil optional")
    void the_default_profile_keeps_the_key_optional() throws IOException {
        Object loaded = new Yaml().load(Files.readString(APPLICATION, StandardCharsets.UTF_8));
        assertThat(loaded).isInstanceOf(java.util.Map.class);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> root = (java.util.Map<String, Object>) loaded;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> echobox = (java.util.Map<String, Object>) root.get("echobox");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> crypto = (java.util.Map<String, Object>) echobox.get("crypto");

        assertThat(crypto.get("key-required"))
                .as("im Default-Profil (lokale Entwicklung) bleibt der Schluessel optional")
                .isEqualTo(false);
    }
}
