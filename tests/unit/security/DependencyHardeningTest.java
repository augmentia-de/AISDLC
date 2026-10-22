package com.echobox.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — der eingebettete Container traegt keine der drei
 * <strong>erreichbaren</strong> Advisories (S-19).
 *
 * <p>Der Nachweis ist der <strong>Abhaengigkeitsbaum</strong>, nicht ein
 * Laufzeitverhalten: geprueft wird die Version des Containers, die der Build
 * tatsaechlich aufloest — also das {@code tomcat-embed-core}-Jar im
 * Klassenpfad des Tests.
 *
 * <p>Die drei erreichbaren Advisories des HTTP/1.1-Pfades:
 * <ul>
 *   <li>CVE-2026-24880 — Request/Response-Smuggling — behoben in 10.1.52,</li>
 *   <li>CVE-2025-48988 — Multipart-DoS — behoben in 10.1.42,</li>
 *   <li>CVE-2025-48989 — „made you reset" — behoben in 10.1.44.</li>
 * </ul>
 * Die geforderte Untergrenze ist <strong>10.1.55</strong> — sie deckt alle
 * drei ab und ist die Version, die Spring Boot 3.5.16 mitbringt.
 */
class DependencyHardeningTest {

    /** Die Mindestversion des eingebetteten Containers (S-19). */
    private static final String REQUIRED_TOMCAT_VERSION = "10.1.55";

    @Test
    @DisplayName("S-19: der eingebettete Container ist mindestens 10.1.55")
    void the_embedded_container_is_new_enough() {
        String version = resolvedTomcatVersion();

        assertThat(version)
                .as("die Version des aufgeloesten tomcat-embed-core muss aus dem Klassenpfad "
                        + "ablesbar sein")
                .isNotNull();
        assertThat(isAtLeast(version, REQUIRED_TOMCAT_VERSION))
                .as("tomcat-embed-core %s ist aelter als %s — damit sind CVE-2026-24880 "
                        + "(Request/Response-Smuggling), CVE-2025-48988 (Multipart-DoS) und "
                        + "CVE-2025-48989 erreichbar.", version, REQUIRED_TOMCAT_VERSION)
                .isTrue();
    }

    @Test
    @DisplayName("S-19: die gehobene Version deckt alle drei erreichbaren Advisories ab")
    void the_version_covers_every_reachable_advisory() {
        String version = resolvedTomcatVersion();

        assertThat(isAtLeast(version, "10.1.52"))
                .as("CVE-2026-24880 (Request/Response-Smuggling) ist erst ab 10.1.52 behoben")
                .isTrue();
        assertThat(isAtLeast(version, "10.1.42"))
                .as("CVE-2025-48988 (Multipart-DoS) ist erst ab 10.1.42 behoben")
                .isTrue();
        assertThat(isAtLeast(version, "10.1.44"))
                .as("CVE-2025-48989 („made you reset\") ist erst ab 10.1.44 behoben")
                .isTrue();
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
