package com.echobox.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sucht eine Klartextmarkierung in der gesamten Datenbank.
 *
 * <p>AC-007-1 verlangt: „die Datenbank (Tabellen, Indizes, Journal) wird nach der
 * Markierung durchsucht — es wird kein Treffer erzielt". Diese Klasse deckt die
 * drei Orte ab:
 * <ul>
 *   <li><strong>Tabellen</strong> — jede Spalte jeder Tabelle im Schema {@code app},
 *       einschliesslich {@code bytea} (Chiffre) und JSON;</li>
 *   <li><strong>Indizes</strong> — die Definitionen aus {@code pg_indexes};</li>
 *   <li><strong>Journal</strong> — der Server-Log des Containers und der vollstaendige
 *       Datenbank-Dump ({@code pg_dump}), der jede Zeile in INSERT-Form enthaelt.</li>
 * </ul>
 *
 * <p>Ein Treffer wird als Fundstelle benannt (Tabelle, Spalte) — nicht nur als
 * Ja/Nein. Das ist der Unterschied zwischen einem Beleg und einer Behauptung.
 */
public final class PlaintextScanner {

    private PlaintextScanner() {
        // statische Helferklasse
    }

    /**
     * Durchsucht alle Spalten aller Tabellen im Schema {@code app}.
     *
     * @param dataSource die Datenbankverbindung
     * @param marker     die Klartextmarkierung
     * @return die Fundstellen — leer, wenn die Markierung nirgends steht
     * @throws SQLException wenn die Abfrage scheitert
     */
    public static List<String> findInTables(DataSource dataSource, String marker) throws SQLException {
        List<String> hits = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            List<String[]> columns = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT table_name, column_name, data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'app'
                      AND data_type IN ('text', 'character varying', 'character', 'json', 'jsonb',
                                        'bytea', 'uuid', 'integer', 'bigint', 'boolean', 'numeric',
                                        'date', 'timestamp with time zone', 'ARRAY')
                    ORDER BY table_name, ordinal_position""")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        columns.add(new String[]{
                                resultSet.getString("table_name"),
                                resultSet.getString("column_name"),
                                resultSet.getString("data_type")});
                    }
                }
            }

            for (String[] column : columns) {
                String expression = "bytea".equals(column[2])
                        ? "encode(\"" + column[1] + "\", 'escape')"
                        : "\"" + column[1] + "\"::text";
                String sql = "SELECT count(*) FROM app.\"" + column[0] + "\" WHERE " + expression + " LIKE ?";

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, "%" + marker + "%");
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next() && resultSet.getLong(1) > 0L) {
                            hits.add("app." + column[0] + "." + column[1]);
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * Durchsucht die Indexdefinitionen.
     *
     * @param dataSource die Datenbankverbindung
     * @param marker     die Klartextmarkierung
     * @return die Fundstellen — leer, wenn die Markierung in keinem Index steht
     * @throws SQLException wenn die Abfrage scheitert
     */
    public static List<String> findInIndexes(DataSource dataSource, String marker) throws SQLException {
        List<String> hits = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'app'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (resultSet.getString("indexdef").contains(marker)) {
                        hits.add("index:" + resultSet.getString("indexname"));
                    }
                }
            }
        }
        return hits;
    }

    /**
     * Durchsucht den vollstaendigen Datenbank-Dump (alle Zeilen in INSERT-Form).
     *
     * @param container der PostgreSQL-Container
     * @param marker    die Klartextmarkierung
     * @return die Fundstellen — leer, wenn die Markierung nicht im Dump steht
     * @throws Exception wenn der Dump fehlschlaegt
     */
    public static List<String> findInDump(PostgreSQLContainer<?> container, String marker) throws Exception {
        String dump = container.execInContainer("pg_dump", "-U", container.getUsername(), "-d", container.getDatabaseName(),
                "--inserts", "--no-owner").getStdout();
        return dump.contains(marker) ? List.of("pg_dump") : List.of();
    }

    /**
     * Durchsucht das Server-Protokoll des Containers.
     *
     * @param container der PostgreSQL-Container
     * @param marker    die Klartextmarkierung
     * @return die Fundstellen — leer, wenn die Markierung nicht im Protokoll steht
     */
    public static List<String> findInServerLog(PostgreSQLContainer<?> container, String marker) {
        String log = container.getLogs();
        return log.contains(marker) ? List.of("server-log") : List.of();
    }
}
