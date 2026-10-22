package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.echobox.config.EchoBoxProperties;
import com.echobox.config.SecurityConfiguration;

/**
 * Unit-Ebene — die Zielgruppen-Pruefung ist ueber **alle Konfigurationswege**
 * fail-closed (S-29, dritte Audit-Runde).
 *
 * <p>Die Abwehr hat zwei Seiten: der Decoder lehnt **jedes** Token ab, wenn
 * keine Zielgruppe konfiguriert ist, und der Dienst **startet nicht**, wenn die
 * Zielgruppe leer ist. Die zweite Seite ist die, die eine Fehlkonfiguration
 * sichtbar macht — ein stilles Fail-open waere die schlimmere Variante.
 *
 * <p>Geprueft werden deshalb die Wege, ueber die eine leere Zielgruppe in den
 * Dienst gelangen kann:
 * <ol>
 *   <li>die **Produktions-Bee-Fabrik** der Bean-Methode (der Kontext kann nicht
 *       entstehen, wenn sie eine leere Zielgruppe bekommt);</li>
 *   <li>die **Aufloesung der Umgebungsvariable** — ein leerer Secret-Wert
 *       darf <strong>nicht</strong> auf den Vorgabewert zurueckfallen (sonst
 *       waere die Fehlkonfiguration unsichtbar);</li>
 *   <li>die **ausgelieferten Profile** — keines davon darf die Zielgruppe
 *       leeren;</li>
 *   <li>die **Pipeline** — beide Umgebungen uebergeben die Zielgruppe aus dem
 *       Secret-Store.</li>
 * </ol>
 */
class AudienceFailClosedPathsTest {

    /** Die Konfigurationsdatei des Default-Profils. */
    private static final Path APPLICATION = Path.of("src", "main", "resources", "application.yml");

    /** Die Konfigurationsdatei des Staging-Profils. */
    private static final Path STAGING = Path.of("src", "main", "resources", "application-staging.yml");

    /** Die Konfigurationsdatei des Produktions-Profils. */
    private static final Path PRODUCTION = Path.of("src", "main", "resources", "application-production.yml");

    /** Die Pipeline. */
    private static final Path WORKFLOW = Path.of(".github", "workflows", "deploy.yml");

    /** Der Platzhalter der Zielgruppe in der Betriebskonfiguration. */
    private static final String PLACEHOLDER = "${ECHobox_SECURITY_AUDIENCE:echobox}";

    @Test
    @DisplayName("S-29: die Produktions-Bee-Fabrik verweigert den Start bei leerer Zielgruppe")
    void the_production_bean_factory_refuses_an_empty_audience() {
        assertThatThrownBy(() -> new SecurityConfiguration().audiencePolicy(propertiesWith("")))
                .as("die Bean-Methode der Produktionskonfiguration muss eine leere Zielgruppe "
                        + "ablehnen — der Kontext kann dann nicht entstehen und der Dienst "
                        + "startet nicht (fail-closed)")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Zielgruppe");

        assertThatThrownBy(() -> new SecurityConfiguration().audiencePolicy(propertiesWith("   ")))
                .as("auch eine Zielgruppe aus Leerzeichen ist leer")
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new SecurityConfiguration().audiencePolicy(propertiesWith(null)))
                .as("auch ein fehlender Wert ist eine leere Zielgruppe")
                .isInstanceOf(IllegalStateException.class);

        assertThat(new SecurityConfiguration().audiencePolicy(propertiesWith("echobox")).audience())
                .as("die konfigurierte Zielgruppe bleibt wirksam")
                .isEqualTo("echobox");
    }

    @Test
    @DisplayName("S-29: ein leerer Secret-Wert faellt nicht auf den Vorgabewert zurueck")
    void an_empty_environment_variable_does_not_fall_back_to_the_default() {
        assertThat(resolve(PLACEHOLDER, null))
                .as("ohne gesetzte Variable gilt der Vorgabewert — der Dienst startet")
                .isEqualTo("echobox");

        assertThat(resolve(PLACEHOLDER, ""))
                .as("ein **leerer** Secret-Wert ergibt einen leeren Wert und nicht den Vorgabewert — "
                        + "sonst waere die Fehlkonfiguration unsichtbar und die Zielgruppe wuerde "
                        + "doch nicht geprueft (fail-open)")
                .isEmpty();
    }

    @Test
    @DisplayName("S-29: keines der ausgelieferten Profile leert die Zielgruppe")
    void no_shipped_profile_empties_the_audience() throws IOException {
        for (Path profile : new Path[] {APPLICATION, STAGING, PRODUCTION}) {
            List<String> values = audienceValues(Files.readString(profile, StandardCharsets.UTF_8));

            for (String value : values) {
                assertThat(value)
                        .as("%s: der Wert der Zielgruppe darf nicht leer sein — der Dienst "
                                + "wuerde nicht starten", profile)
                        .isNotBlank();
            }
        }

        assertThat(audienceValues(Files.readString(APPLICATION, StandardCharsets.UTF_8)))
                .as("die Betriebskonfiguration bindet die Zielgruppe an die Umgebungsvariable")
                .contains(PLACEHOLDER);
    }

    @Test
    @DisplayName("S-29: die Pipeline uebergibt die Zielgruppe aus dem Secret-Store beider Umgebungen")
    void the_pipeline_passes_the_audience_from_the_environment_secrets() throws IOException {
        String workflow = Files.readString(WORKFLOW, StandardCharsets.UTF_8);

        assertThat(workflow)
                .as("ohne die Zielgruppe startet der Dienst nicht — die Pipeline muss sie setzen")
                .contains("ECHobox_SECURITY_AUDIENCE: ${{ secrets.STAGING_SECURITY_AUDIENCE }}")
                .contains("ECHobox_SECURITY_AUDIENCE: ${{ secrets.PRODUCTION_SECURITY_AUDIENCE }}");
    }

    /**
     * Erzeugt die Betriebskonfiguration mit einer bestimmten Zielgruppe.
     *
     * @param audience die Zielgruppe
     * @return die Konfiguration
     */
    private static EchoBoxProperties propertiesWith(String audience) {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getSecurity().setAudience(audience);
        return properties;
    }

    /**
     * Loest einen Platzhalter in einer Umgebung auf — mit und ohne gesetzte
     * Variable. Die Umgebung ist bewusst **isoliert** von der Systemumgebung:
     * der Test darf nicht von der Maschine abhaengen, auf der er laeuft.
     *
     * @param placeholder der Platzhalter
     * @param value       der Wert der Variablen ({@code null} = nicht gesetzt)
     * @return der aufgeloeste Wert
     */
    private static String resolve(String placeholder, String value) {
        MockEnvironment environment = new MockEnvironment();
        if (value != null) {
            environment.setProperty("ECHobox_SECURITY_AUDIENCE", value);
        }
        return environment.resolvePlaceholders(placeholder);
    }

    /**
     * Liest alle Werte der Zielgruppe aus einer Konfigurationsdatei.
     *
     * @param content der Inhalt der Datei
     * @return die Werte
     */
    private static List<String> audienceValues(String content) {
        List<String> values = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#") || !line.startsWith("audience:")) {
                continue;
            }
            values.add(line.substring("audience:".length()).trim());
        }
        return values;
    }
}
