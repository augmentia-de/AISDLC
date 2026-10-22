package com.echobox.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integrationsebene — der SCA-Gate ist **kalibriert** (S-32).
 *
 * <p>Ohne Baseline schlug der SCA-Schritt der Pipeline auf <strong>jedem</strong>
 * Lauf an: der Gate war vorhanden, unterschied aber nicht zwischen „neu" und
 * „seit langem bekannt". Die Baseline {@code osv-scanner.toml} listet die
 * bekannten Advisories — je mit Begruendung und Ablaufdatum. Der Scan laesst
 * damit nur noch <strong>neue</strong> Advisories scheitern.
 *
 * <p>Geprueft werden <strong>Datei und Konfiguration</strong>, nicht das
 * Laufzeitverhalten (der Scan braucht einen Runner):
 * <ul>
 *   <li>die Baseline existiert und traegt je Eintrag eine ID, eine Begruendung
 *       und ein Ablaufdatum;</li>
 *   <li>kein Eintrag ist abgelaufen — ein abgelaufener Eintrag waere ein
 *       stillschweigend verschwundener Advisory;</li>
 *   <li>die Pipeline uebergibt die Baseline an den Scanner;</li>
 *   <li>die Baseline enthaelt keine unbekannten Schluessel — der Scanner
 *       lehnt die Datei sonst ab und der Gate schlaegt wieder auf jedem Lauf
 *       an.</li>
 * </ul>
 */
class ScaBaselineIT {

    private static final Path BASELINE = Path.of("osv-scanner.toml");

    private static final Path WORKFLOW = Path.of(".github", "workflows", "deploy.yml");

    /** Die zulaessigen Schluessel eines Eintrags — der Scanner lehnt andere ab. */
    private static final List<String> ALLOWED_KEYS = List.of("id", "ignoreuntil", "reason");

    @Test
    @DisplayName("S-32: die Baseline des SCA-Scans existiert und ist gueltig")
    void the_baseline_exists_and_is_valid() throws IOException {
        assertThat(BASELINE)
                .as("ohne Baseline schlaegt der SCA-Schritt auf jedem Lauf an")
                .exists();

        String content = Files.readString(BASELINE, StandardCharsets.UTF_8);
        List<Entry> entries = entries(content);

        assertThat(entries)
                .as("die Baseline muss die bekannten Advisories auffuehren")
                .isNotEmpty();
        for (Entry entry : entries) {
            assertThat(entry.id())
                    .as("jeder Eintrag braucht die ID des Advisory")
                    .isNotBlank();
            assertThat(entry.reason())
                    .as("%s: ohne Begruendung ist die Ausnahme nicht nachvollziehbar", entry.id())
                    .isNotBlank();
            assertThat(entry.ignoreUntil())
                    .as("%s: ohne Ablaufdatum bleibt die Ausnahme unbefristet", entry.id())
                    .isNotNull();
            assertThat(entry.ignoreUntil())
                    .as("%s: der Eintrag ist abgelaufen — der Scanner meldet das Advisory "
                            + "wieder und der Gate schlaegt an", entry.id())
                    .isAfter(LocalDate.now());
        }
    }

    @Test
    @DisplayName("S-32: die Baseline enthaelt keine unbekannten Schluessel")
    void the_baseline_contains_no_unknown_keys() throws IOException {
        String content = Files.readString(BASELINE, StandardCharsets.UTF_8);

        for (String key : keys(content)) {
            assertThat(ALLOWED_KEYS)
                    .as("der Scanner lehnt die Baseline ab, wenn sie einen unbekannten Schluessel "
                            + "enthaelt — der Gate wuerde dann wieder auf jedem Lauf anschlagen")
                    .contains(key);
        }
    }

    @Test
    @DisplayName("S-32: die Pipeline uebergibt die Baseline an den Scanner")
    void the_workflow_uses_the_baseline() throws IOException {
        String workflow = Files.readString(WORKFLOW, StandardCharsets.UTF_8);

        assertThat(workflow)
                .as("der SCA-Schritt muss die Baseline verwenden, damit nur neue Advisories "
                        + "den Lauf scheitern lassen")
                .contains("--config=osv-scanner.toml");
        assertThat(workflow)
                .as("der SCA-Scan bleibt im Gate erhalten")
                .contains("osv-scanner-action@v2.7.0");
    }

    /**
     * Liest die Eintraege der Baseline.
     *
     * @param content der Inhalt der Datei
     * @return die Eintraege
     */
    private static List<Entry> entries(String content) {
        List<Entry> entries = new ArrayList<>();
        Entry current = null;
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            if (line.equals("[[IgnoredVulns]]")) {
                current = new Entry();
                entries.add(current);
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0 || current == null) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.startsWith("\"")) {
                value = value.substring(1, value.lastIndexOf('"'));
            }
            switch (key) {
                case "id" -> current.id = value;
                case "reason" -> current.reason = value;
                case "ignoreUntil" -> current.ignoreUntil = LocalDate.parse(value);
                default -> {
                    // unbekannte Schluessel werden im eigenen Test beanstandet
                }
            }
        }
        return entries;
    }

    /**
     * Liest alle Schluessel der Datei (ohne Kommentarzeilen).
     *
     * @param content der Inhalt der Datei
     * @return die Schluessel in Kleinschreibung
     */
    private static List<String> keys(String content) {
        List<String> keys = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty() || line.startsWith("[")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator > 0) {
                keys.add(line.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return keys;
    }

    /** Ein Eintrag der Baseline. */
    private static final class Entry {

        private String id;

        private String reason;

        private LocalDate ignoreUntil;

        private String id() {
            return id;
        }

        private String reason() {
            return reason;
        }

        private LocalDate ignoreUntil() {
            return ignoreUntil;
        }
    }
}
