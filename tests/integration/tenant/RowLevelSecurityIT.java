package com.echobox.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.echobox.testsupport.PostgresTestContainer;

/**
 * Integrationsebene — Mandanttrennung auf Datenbankebene (US-022, NFR-003).
 *
 * <p>Dieser Test stellt die Forderung von NFR-003 direkt an die Datenbank: die
 * Mandantentrennung wird <em>auf Datenbankebene</em> erzwiesen, nicht in der
 * Anwendung. Ein Anwendungsfehler, der den Filter vergisst, darf kein fremdes
 * Ereignis zeigen — das prueft der Test gegen eine echte PostgreSQL-Instanz.
 *
 * <p>Das Schema wird aus der Migration des Dienstes gespielt
 * ({@code src/main/resources/db/migration/V1__initial_schema.sql}) — nicht aus
 * einer Kopie im Test. So prueft der Test zugleich, dass die Migration das
 * kanonische Schema traegt.
 *
 * <p>Testorakel: `.agent/knowledge/schema.sql` (kanonisch) und
 * `docs/architecture/domain_model.md`.
 */
class RowLevelSecurityIT {

    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V1__initial_schema.sql");

    @BeforeAll
    static void applySchema() throws Exception {
        assertThat(MIGRATION)
                .as("die Migration des Dienstes muss das kanonische Schema anlegen")
                .exists();

        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), "postgres", "postgres");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    @DisplayName("AC-022-1: jede mandantenfuehrende Tabelle erzwingt die Mandantentrennung")
    void every_tenant_table_enforces_row_level_security() throws SQLException {
        List<String> withoutRls = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), "postgres", "postgres");
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT c.relname
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'app' AND c.relkind = 'r' AND NOT c.relrowsecurity
                     ORDER BY c.relname""");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                withoutRls.add(resultSet.getString("relname"));
            }
        }

        assertThat(withoutRls)
                .as("keine mandantenfuehrende Tabelle darf ohne Row-Level-Security auskommen")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-022-2: eine Abfrage ohne Mandantenfilter zeigt nur die Zeilen des gesetzten Mandanten")
    void a_query_without_tenant_filter_shows_only_the_tenants_rows() throws SQLException {
        seedEvent("m-1", "11111111-1111-4111-8111-111111111111");
        seedEvent("m-2", "22222222-2222-4222-8222-222222222222");

        List<String> visible = asApplicationRole("m-1",
                "SELECT event_id::text FROM app.events ORDER BY event_id");

        assertThat(visible).containsExactly("11111111-1111-4111-8111-111111111111");
    }

    @Test
    @DisplayName("AC-022-2: ohne gesetzten Mandanten liefert die Abfrage keine Zeile (fail-closed)")
    void without_a_tenant_the_query_returns_no_rows() throws SQLException {
        seedEvent("m-1", "33333333-3333-4333-8333-333333333333");

        List<String> visible = asApplicationRole(null,
                "SELECT event_id::text FROM app.events");

        assertThat(visible)
                .as("current_setting('app.organisation_id', true) liefert NULL — die Policy greift fail-closed")
                .isEmpty();
    }

    @Test
    @DisplayName("AC-022-2: ein fremder Mandant sieht die Zeilen des eigenen Mandanten nicht")
    void a_tenant_never_sees_the_rows_of_another_tenant() throws SQLException {
        seedEvent("m-1", "44444444-4444-4444-8444-444444444444");
        seedEvent("m-2", "55555555-5555-4555-8555-555555555555");

        List<String> fromM2 = asApplicationRole("m-2",
                "SELECT event_id::text FROM app.events WHERE event_id = '44444444-4444-4444-8444-444444444444'");

        assertThat(fromM2).isEmpty();
    }

    private void seedEvent(String organisationId, String eventId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), "postgres", "postgres");
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO app.events (event_id, organisation_id, event_type, idempotency_key,
                                             payload_ciphertext, payload_nonce, payload_fingerprint,
                                             payload_size_bytes)
                     VALUES (?::uuid, ?, 'order.changed', ?, decode('00', 'hex'), decode('000000000000000000000000', 'hex'),
                             decode(repeat('ab', 32), 'hex'), 2)
                     ON CONFLICT (event_id) DO NOTHING""")) {
            statement.setString(1, eventId);
            statement.setString(2, organisationId);
            statement.setString(3, "idem-" + eventId);
            statement.executeUpdate();
        }
    }

    private List<String> asApplicationRole(String organisationId, String query) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), "postgres", "postgres")) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE echobox_app");
                if (organisationId != null) {
                    statement.execute("SELECT app.set_tenant('" + organisationId + "')");
                }
                try (ResultSet resultSet = statement.executeQuery(query)) {
                    while (resultSet.next()) {
                        result.add(resultSet.getString(1));
                    }
                }
            }
            connection.commit();
        }
        return result;
    }
}
