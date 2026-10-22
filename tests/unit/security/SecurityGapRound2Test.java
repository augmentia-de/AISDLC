package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.echobox.config.EchoBoxProperties;
import com.echobox.config.SecurityConfiguration;

/**
 * Unit-Ebene — die <strong>belegten Luecken der zweiten Audit-Runde</strong>.
 *
 * <p><strong>Diese Tests schlagen fehl.</strong> Sie belegen zwei Luecken:
 *
 * <table>
 *   <caption>Die belegten Luecken der zweiten Runde</caption>
 *   <tr><th>Test</th><th>Befund</th><th>Schwere</th></tr>
 *   <tr><td>S-GAP-29</td><td>Die Zielgruppen-Pruefung faellt bei leerer Konfiguration
 *       still auf "nicht geprueft" zurueck (fail-open)</td><td>Medium</td></tr>
 *   <tr><td>S-GAP-31</td><td>Der gepinnte eingebettete Container (10.1.55) liegt unter der
 *       Fix-Linie (10.1.58) von drei aktuell veroeffentlichten Tomcat-Advisories</td>
 *       <td>Medium</td></tr>
 * </table>
 */
class SecurityGapRound2Test {

    /**
     * Die Version des eingebetteten Containers, in der die drei aktuell
     * veroeffentlichten Advisories behoben sind. 10.1.58 wurde nie
     * veroeffentlicht — die erste verfuegbare Version darueber ist 10.1.59.
     */
    private static final String TOMCAT_VERSION_WITH_THE_CURRENT_FIXES = "10.1.59";

    private JwtTestKeys keys;

    @BeforeEach
    void setUp() throws Exception {
        keys = new JwtTestKeys();
    }

    @AfterEach
    void tearDown() {
        if (keys != null) {
            keys.close();
        }
    }

    @Test
    @DisplayName("LUECKE (Befund S-29): ohne konfigurierte Zielgruppe darf die Zielgruppe "
            + "nicht ungeprueft bleiben")
    void without_a_configured_audience_the_audience_must_still_be_checked() throws Exception {
        JwtDecoder decoder = decoderWith(keys.jwkSetUri(), "");
        String token = keys.signRs256(JwtTestKeys.claims("m-1", List.of("developer"),
                Instant.now().plusSeconds(300).getEpochSecond(), "einen-anderen-client"));

        assertThatThrownBy(() -> decoder.decode(token))
                .as("Die Zielgruppen-Pruefung wird uebergangen, sobald die Audience leer "
                        + "konfiguriert ist (SecurityConfiguration.validators). Ein leerer "
                        + "Secret-Wert (ECHobox_SECURITY_AUDIENCE=) schaltet die Pruefung still "
                        + "ab — der Dienst nimmt dann Token eines anderen Clients desselben "
                        + "Identity Providers an.")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("LUECKE (Befund S-31): der eingebettete Container muss mindestens 10.1.59 sein")
    void the_embedded_container_must_carry_the_current_fixes() {
        String version = resolvedTomcatVersion();

        assertThat(version)
                .as("die Version des aufgeloesten tomcat-embed-core muss aus dem Klassenpfad "
                        + "ablesbar sein")
                .isNotNull();
        assertThat(isAtLeast(version, TOMCAT_VERSION_WITH_THE_CURRENT_FIXES))
                .as("tomcat-embed-core %s liegt unter der Fix-Linie %s der drei aktuell "
                        + "veroeffentlichten Advisories GHSA-9xv2-5v5q-p794, "
                        + "GHSA-gcx9-497g-6cp6 und GHSA-h3x4-894j-xpx5.",
                        version, TOMCAT_VERSION_WITH_THE_CURRENT_FIXES)
                .isTrue();
    }

    private static JwtDecoder decoderWith(String jwkSetUri, String audience) {
        EchoBoxProperties properties = new EchoBoxProperties();
        properties.getSecurity().setJwkSetUri(jwkSetUri);
        properties.getSecurity().setAudience(audience);
        return new SecurityConfiguration().jwtDecoder(properties);
    }

    /**
     * Liefert die Version des aufgeloesten {@code tomcat-embed-core}-Jars.
     *
     * @return die Version oder {@code null}, wenn sie nicht lesbar ist
     */
    private static String resolvedTomcatVersion() {
        ProtectionDomain domain = org.apache.catalina.startup.Tomcat.class.getProtectionDomain();
        CodeSource codeSource = domain.getCodeSource();
        if (codeSource == null) {
            return null;
        }
        URL location = codeSource.getLocation();
        String name = new File(location.getPath()).getName();
        if (!name.startsWith("tomcat-embed-core-") || !name.endsWith(".jar")) {
            return null;
        }
        return name.substring("tomcat-embed-core-".length(), name.length() - ".jar".length());
    }

    /**
     * Vergleicht zwei Versionsangaben numerisch (nicht lexikografisch — sonst
     * waere „10.1.9" groesser als „10.1.55").
     *
     * @param actual   die gepruefte Version
     * @param required die geforderte Version
     * @return {@code true}, wenn {@code actual} mindestens {@code required} ist
     */
    private static boolean isAtLeast(String actual, String required) {
        if (actual == null) {
            return false;
        }
        String[] actualParts = actual.split("[.\\-]");
        String[] requiredParts = required.split("[.\\-]");
        int length = Math.max(actualParts.length, requiredParts.length);
        for (int index = 0; index < length; index++) {
            int actualPart = number(actualParts, index);
            int requiredPart = number(requiredParts, index);
            if (actualPart != requiredPart) {
                return actualPart > requiredPart;
            }
        }
        return true;
    }

    private static int number(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
