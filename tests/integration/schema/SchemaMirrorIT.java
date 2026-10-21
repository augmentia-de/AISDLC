package com.echobox.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integrationsebene — Schema-Spiegel (ADR-016, N-13-Befund O-13/S-26).
 *
 * <p>Das kanonische Schema liegt in {@code .agent/knowledge/schema.sql}. Die
 * versionierte Kopie unter {@code docs/architecture/schema.sql} muss
 * byte-identisch sein, und die Migration des Dienstes muss dasselbe Schema
 * anlegen — sonst laeuft die Produktion gegen ein anderes Schema als das
 * gepruefte.
 *
 * <p>Testorakel: `.agent/knowledge/stack.md` (Schema-Abschnitt) und ADR-016.
 */
class SchemaMirrorIT {

    private static final Path CANONICAL = Path.of(".agent", "knowledge", "schema.sql");
    private static final Path MIRROR = Path.of("docs", "architecture", "schema.sql");
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V1__initial_schema.sql");

    @Test
    @DisplayName("ADR-016 (AC-022-1): der Spiegel in docs/architecture ist byte-identisch mit dem kanonischen Schema")
    void the_mirror_is_byte_identical() throws Exception {
        assertThat(Files.readAllBytes(MIRROR))
                .as("%s muss byte-identisch mit %s sein", MIRROR, CANONICAL)
                .isEqualTo(Files.readAllBytes(CANONICAL));
    }

    @Test
    @DisplayName("ADR-016 (AC-022-1): die Migration des Dienstes legt das kanonische Schema an")
    void the_migration_carries_the_canonical_schema() throws Exception {
        assertThat(MIGRATION)
                .as("die Flyway-Migration muss das kanonische Schema anlegen")
                .exists();

        String canonical = Files.readString(CANONICAL, StandardCharsets.UTF_8);
        String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        List<String> requiredStatements = List.of(
                "CREATE TABLE IF NOT EXISTS app.endpoints",
                "CREATE TABLE IF NOT EXISTS app.events",
                "CREATE TABLE IF NOT EXISTS app.delivery_runs",
                "CREATE TABLE IF NOT EXISTS app.delivery_attempts",
                "ALTER TABLE app.events ENABLE ROW LEVEL SECURITY",
                "CREATE OR REPLACE FUNCTION app.set_tenant",
                "CREATE POLICY events_tenant_isolation ON app.events",
                "UNIQUE (replay_of)");

        for (String statement : requiredStatements) {
            assertThat(migration)
                    .as("die Migration muss enthalten: %s", statement)
                    .contains(statement);
        }

        assertThat(migration.length())
                .as("die Migration ist eine Kopie des kanonischen Schemas")
                .isGreaterThan(canonical.length() / 2);
    }
}
