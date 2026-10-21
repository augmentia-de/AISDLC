package com.echobox.protocol;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.echobox.common.SqlParameters;
import com.echobox.tenant.CrossTenantScope;

/**
 * Der JDBC-Adapter der Aufbewahrungsdurchsetzung.
 *
 * <p>Der Adapter kapselt <strong>nur</strong> den Aufruf der schmalen
 * Eigentuemerfunktion {@code app.enforce_retention_cross_tenant}. Es gibt
 * <strong>keinen</strong> zweiten mandantenuebergreifenden Leseweg und
 * <strong>kein</strong> {@code BYPASSRLS} (ADR-002 §7).
 *
 * <p>Die Funktion ist <strong>fail-closed</strong>: ohne die Freigabe
 * {@code app.cross_tenant = 'service_admin'} liefert sie <strong>keine</strong>
 * Zeile. Der Aufruf laeuft deshalb <strong>immer</strong> ueber den
 * {@link CrossTenantScope}.
 *
 * <p>Alle Statements sind <strong>vorbereitet</strong> und benutzen
 * <strong>benannte Parameter</strong> — es wird kein SQL zusammengesetzt und
 * kein Wert verkettet.
 */
@Repository
public class RetentionRepository {

    private static final String ENFORCE_RETENTION = """
            SELECT organisation_id, event_retention_days, delivery_retention_days,
                   event_cutoff, delivery_cutoff,
                   marked_events, marked_delivery_runs, marked_attempts,
                   purged_events, purged_delivery_runs, purged_attempts
            FROM app.enforce_retention_cross_tenant(
                CAST(:dryRun AS BOOLEAN), CAST(:eventDays AS INTEGER),
                CAST(:deliveryDays AS INTEGER), CAST(:purgeDelay AS INTEGER))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private final CrossTenantScope crossTenantScope;

    /**
     * Erzeugt den Adapter.
     *
     * @param jdbcTemplate      die JDBC-Vorlage
     * @param crossTenantScope  der mandantenuebergreifende Leseweg
     */
    public RetentionRepository(JdbcTemplate jdbcTemplate, CrossTenantScope crossTenantScope) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.crossTenantScope = crossTenantScope;
    }

    /**
     * Setzt die Aufbewahrung mandantenuebergreifend durch.
     *
     * <p>Die uebergebenen Fristen sind die <strong>Vorgaben</strong>: je Mandant
     * ueberschreibt {@code app.retention_settings} sie. {@code null} bedeutet
     * „keine Vorgabe“ — dann gilt allein der Override des Mandanten.
     *
     * @param dryRun       ob nur gezaehlt wird
     * @param eventDays    die Vorgabefrist fuer Ereignisse in Tagen (nullable)
     * @param deliveryDays die Vorgabefrist fuer Zustellprotokolle in Tagen (nullable)
     * @param purgeDelay   die Karenzzeit in Tagen (nullable = 0 Tage)
     * @return je Mandant die angewandten Fristen, Stichtage und Zaehler
     */
    public List<TenantRetentionRow> enforce(boolean dryRun,
                                             Integer eventDays,
                                             Integer deliveryDays,
                                             Integer purgeDelay) {
        MapSqlParameterSource parameters = SqlParameters.source()
                .addValue("dryRun", dryRun)
                .addValue("eventDays", eventDays)
                .addValue("deliveryDays", deliveryDays)
                .addValue("purgeDelay", purgeDelay);
        return crossTenantScope.asServiceAdmin(() -> jdbc.query(ENFORCE_RETENTION, parameters, ROW_MAPPER));
    }

    /**
     * Eine Zeile der Durchsetzung — die Angaben <strong> eines Mandanten</strong>.
     *
     * @param organisationId        der Mandant
     * @param eventRetentionDays    die angewandte Frist fuer Ereignisse in Tagen (nullable)
     * @param deliveryRetentionDays die angewandte Frist fuer Zustellprotokolle in Tagen (nullable)
     * @param eventCutoff           der Stichtag fuer Ereignisse (nullable)
     * @param deliveryCutoff        der Stichtag fuer Zustelllaeufe und Versuche (nullable)
     * @param markedEvents          die markierten Ereignisse
     * @param markedDeliveryRuns    die markierten Zustelllaeufe
     * @param markedAttempts        die markierten Versuche
     * @param purgedEvents          die physisch geloeschten Ereignisse
     * @param purgedDeliveryRuns    die physisch geloeschten Zustelllaeufe
     * @param purgedAttempts        die physisch geloeschten Versuche
     */
    public record TenantRetentionRow(String organisationId,
                                     Integer eventRetentionDays,
                                     Integer deliveryRetentionDays,
                                     Instant eventCutoff,
                                     Instant deliveryCutoff,
                                     long markedEvents,
                                     long markedDeliveryRuns,
                                     long markedAttempts,
                                     long purgedEvents,
                                     long purgedDeliveryRuns,
                                     long purgedAttempts) {
    }

    private static final org.springframework.jdbc.core.RowMapper<TenantRetentionRow> ROW_MAPPER =
            (rs, rowNum) -> mapRow(rs);

    private static TenantRetentionRow mapRow(ResultSet rs) throws SQLException {
        return new TenantRetentionRow(
                rs.getString("organisation_id"),
                rs.getObject("event_retention_days", Integer.class),
                rs.getObject("delivery_retention_days", Integer.class),
                instant(rs, "event_cutoff"),
                instant(rs, "delivery_cutoff"),
                counter(rs, "marked_events"),
                counter(rs, "marked_delivery_runs"),
                counter(rs, "marked_attempts"),
                counter(rs, "purged_events"),
                counter(rs, "purged_delivery_runs"),
                counter(rs, "purged_attempts"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static long counter(ResultSet rs, String column) throws SQLException {
        Long value = rs.getObject(column, Long.class);
        return value == null ? 0L : value.longValue();
    }
}
