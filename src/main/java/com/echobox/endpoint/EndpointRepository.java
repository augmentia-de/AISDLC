package com.echobox.endpoint;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.echobox.common.IdGenerator;
import com.echobox.common.SqlParameters;
import com.echobox.delivery.DeliveryStateMachine;
import com.echobox.delivery.DeliveryStatus;
import com.echobox.tenant.CrossTenantScope;
import com.echobox.tenant.TenantTransactionRunner;

/**
 * Der JDBC-Adapter des Endpunktmoduls.
 *
 * <p><strong>Mandant vor allem anderen:</strong> jeder Zugriff laeuft ueber
 * {@link TenantTransactionRunner} — der Mandant wird <strong>innerhalb</strong>
 * der Transaktion gesetzt, die auch liest (ADR-002 §3). Zusaetzlich traegt
 * <strong>jede</strong> Abfrage den Mandanten <strong>explizit</strong>
 * ({@code organisation_id = :organisationId}): die Datenbank erzwingt die
 * Trennung durch RLS, die Anwendung filtert <strong>zusaetzlich</strong> — die
 * zweite, unabhaengige Schicht (NFR-003).
 *
 * <p>Alle Statements sind <strong>vorbereitet</strong> und benutzen
 * <strong>benannte Parameter</strong> — es wird kein SQL zusammengesetzt und
 * kein Wert verkettet.
 */
@Repository
public class EndpointRepository {

    private static final String INSERT_ENDPOINT = """
            INSERT INTO app.endpoints
                (endpoint_id, organisation_id, target_url, target_host, secret_ciphertext, secret_nonce,
                 secret_key_version, secret_version, status, created_at, updated_at)
            VALUES
                (:endpointId, :organisationId, :targetUrl, :targetHost, :secretCiphertext, :secretNonce,
                 :secretKeyVersion, :secretVersion, :status, :createdAt, :updatedAt)
            """;

    private static final String INSERT_EVENT_TYPE = """
            INSERT INTO app.endpoint_event_types (endpoint_id, organisation_id, event_type, created_at)
            VALUES (:endpointId, :organisationId, :eventType, :createdAt)
            """;

    private static final String DELETE_EVENT_TYPES = """
            DELETE FROM app.endpoint_event_types
            WHERE endpoint_id = :endpointId AND organisation_id = :organisationId
            """;

    private static final String SELECT_ENDPOINT = """
            SELECT e.endpoint_id, e.organisation_id, e.target_url, e.status, e.secret_version,
                   e.secret_rotated_at, e.paused_at, e.created_at, e.updated_at,
                   COALESCE(t.event_types, ARRAY[]::TEXT[]) AS event_types
            FROM app.endpoints e
            LEFT JOIN LATERAL (
                SELECT array_agg(et.event_type ORDER BY et.event_type) AS event_types
                FROM app.endpoint_event_types et
                WHERE et.endpoint_id = e.endpoint_id AND et.organisation_id = :organisationId
            ) t ON TRUE
            WHERE e.endpoint_id = :endpointId
              AND e.organisation_id = :organisationId
              AND e.deleted_at IS NULL
            """;

    private static final String SELECT_ENDPOINTS = """
            SELECT e.endpoint_id, e.organisation_id, e.target_url, e.status, e.secret_version,
                   e.secret_rotated_at, e.paused_at, e.created_at, e.updated_at,
                   COALESCE(t.event_types, ARRAY[]::TEXT[]) AS event_types
            FROM app.endpoints e
            LEFT JOIN LATERAL (
                SELECT array_agg(et.event_type ORDER BY et.event_type) AS event_types
                FROM app.endpoint_event_types et
                WHERE et.endpoint_id = e.endpoint_id AND et.organisation_id = :organisationId
            ) t ON TRUE
            WHERE e.organisation_id = :organisationId
              AND e.deleted_at IS NULL
              AND (CAST(:statuses AS TEXT) IS NULL
                   OR e.status = ANY(string_to_array(CAST(:statuses AS TEXT), ',')))
              AND (CAST(:afterUpdatedAt AS TIMESTAMPTZ) IS NULL
                   OR (e.updated_at, e.endpoint_id)
                      < (CAST(:afterUpdatedAt AS TIMESTAMPTZ), CAST(:afterEndpointId AS UUID)))
            ORDER BY e.updated_at DESC, e.endpoint_id DESC
            LIMIT :limit
            """;

    private static final String SELECT_ENDPOINTS_CROSS_TENANT = """
            SELECT endpoint_id, organisation_id, target_url, status, event_types, secret_version,
                   secret_rotated_at, paused_at, created_at, updated_at
            FROM app.list_endpoints_cross_tenant(
                CAST(:status AS TEXT), :limit,
                CAST(:afterUpdatedAt AS TIMESTAMPTZ), CAST(:afterEndpointId AS UUID))
            """;

    private static final String UPDATE_TARGET = """
            UPDATE app.endpoints
            SET target_url = :targetUrl, target_host = :targetHost
            WHERE endpoint_id = :endpointId AND organisation_id = :organisationId
            """;

    private static final String UPDATE_STATUS = """
            UPDATE app.endpoints
            SET status = :status, paused_at = :pausedAt
            WHERE endpoint_id = :endpointId AND organisation_id = :organisationId
            """;

    private static final String UPDATE_SECRET = """
            UPDATE app.endpoints
            SET secret_ciphertext = :secretCiphertext, secret_nonce = :secretNonce,
                secret_version = :secretVersion, secret_rotated_at = :rotatedAt
            WHERE endpoint_id = :endpointId AND organisation_id = :organisationId
            """;

    private static final String INSERT_SECRET_HISTORY = """
            INSERT INTO app.endpoint_secret_history
                (rotation_id, organisation_id, endpoint_id, secret_version, rotated_at, rotated_by)
            VALUES (:rotationId, :organisationId, :endpointId, :secretVersion, :rotatedAt, :rotatedBy)
            """;

    private static final String CANCEL_OPEN_RUNS = """
            UPDATE app.delivery_runs
            SET status = 'cancelled', next_attempt_at = NULL
            WHERE organisation_id = :organisationId
              AND endpoint_id = :endpointId
              AND status IN (:statuses)
            """;

    private static final String SELECT_SUBSCRIBERS = """
            SELECT e.endpoint_id, e.status
            FROM app.endpoints e
            JOIN app.endpoint_event_types t
              ON t.endpoint_id = e.endpoint_id AND t.organisation_id = e.organisation_id
            WHERE e.organisation_id = :organisationId
              AND t.event_type = :eventType
              AND e.deleted_at IS NULL
            ORDER BY e.endpoint_id
            """;

    /**
     * Das Zustellziel eines Endpunkts: die Ziel-URL, der Zustand und das
     * verschluesselte Secret.
     *
     * <p>Das Secret wird nur hier gelesen und nur fuer die Signatur der
     * Zustellung entschluesselt — es erscheint in keiner Antwort und in
     * keinem Protokoll (AC-001-4).
     */
    private static final String SELECT_DELIVERY_TARGET = """
            SELECT endpoint_id, organisation_id, target_url, status, secret_ciphertext, secret_nonce
            FROM app.endpoints
            WHERE organisation_id = :organisationId
              AND endpoint_id = :endpointId
              AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private final TenantTransactionRunner tenantRunner;

    private final CrossTenantScope crossTenantScope;

    private final IdGenerator idGenerator;

    /**
     * Erzeugt den Adapter.
     *
     * @param jdbcTemplate     die JDBC-Vorlage
     * @param tenantRunner     der Mandantentransaktions-Runner
     * @param crossTenantScope der mandantenuebergreifende Leseweg
     * @param idGenerator      die Erzeugung der Kennungen (UUID-v7)
     */
    public EndpointRepository(JdbcTemplate jdbcTemplate,
                              TenantTransactionRunner tenantRunner,
                              CrossTenantScope crossTenantScope,
                              IdGenerator idGenerator) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.tenantRunner = tenantRunner;
        this.crossTenantScope = crossTenantScope;
        this.idGenerator = idGenerator;
    }

    /**
     * Legt einen Endpunkt an.
     *
     * @param organisationId  der besitzende Mandant
     * @param targetUrl       die Ziel-URL
     * @param secretCiphertext die Chiffre des Secrets
     * @param secretNonce     die Nonce der Chiffre
     * @param eventTypes      die abonnierten Ereignistypen
     * @param now             der Zeitpunkt der Anlage
     * @return der angelegte Endpunkt
     */
    public Endpoint insert(String organisationId,
                           EndpointTargetUrl targetUrl,
                           byte[] secretCiphertext,
                           byte[] secretNonce,
                           List<String> eventTypes,
                           Instant now) {
        return tenantRunner.execute(organisationId, () -> {
            UUID endpointId = idGenerator.newId();
            MapSqlParameterSource parameters = SqlParameters.source()
                    .addValue("endpointId", endpointId)
                    .addValue("organisationId", organisationId)
                    .addValue("targetUrl", targetUrl.value())
                    .addValue("targetHost", targetUrl.host())
                    .addValue("secretCiphertext", secretCiphertext)
                    .addValue("secretNonce", secretNonce)
                    .addValue("secretKeyVersion", 1)
                    .addValue("secretVersion", 1)
                    .addValue("status", EndpointStatus.ACTIVE.wireValue())
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now);
            jdbc.update(INSERT_ENDPOINT, parameters);

            for (String eventType : eventTypes) {
                jdbc.update(INSERT_EVENT_TYPE, SqlParameters.source()
                        .addValue("endpointId", endpointId)
                        .addValue("organisationId", organisationId)
                        .addValue("eventType", eventType)
                        .addValue("createdAt", now));
            }
            return new Endpoint(endpointId, organisationId, targetUrl, EndpointStatus.ACTIVE,
                    eventTypes, 1, null, null, now, now);
        });
    }

    /**
     * Liest einen Endpunkt im Mandanten.
     *
     * @param organisationId der Mandant
     * @param endpointId     die Endpunkt-ID
     * @return der Endpunkt, wenn er im Mandanten existiert
     */
    public Optional<Endpoint> findById(String organisationId, UUID endpointId) {
        return tenantRunner.execute(organisationId, () -> {
            List<Endpoint> rows = jdbc.query(SELECT_ENDPOINT, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("endpointId", endpointId), ENDPOINT_MAPPER);
            return rows.stream().findFirst();
        });
    }

    /**
     * Listet die Endpunkte des Mandanten — sortiert nach {@code updated_at DESC,
     * endpoint_id DESC}.
     *
     * @param organisationId der Mandant
     * @param statuses       die gewuenschten Status (leer = alle)
     * @param limit          die Zahl der Zeilen (die Abfrage liefert eine mehr)
     * @param afterUpdatedAt der Sortierschluessel des Cursors (nullable)
     * @param afterEndpointId der zweite Teil des Sortierschluessels (nullable)
     * @return die Endpunkte
     */
    public List<Endpoint> list(String organisationId,
                               List<String> statuses,
                               int limit,
                               Instant afterUpdatedAt,
                               UUID afterEndpointId) {
        return tenantRunner.execute(organisationId, () -> jdbc.query(SELECT_ENDPOINTS, SqlParameters.source()
                .addValue("organisationId", organisationId)
                .addValue("statuses", statuses == null || statuses.isEmpty() ? null : String.join(",", statuses))
                .addValue("afterUpdatedAt", afterUpdatedAt)
                .addValue("afterEndpointId", afterEndpointId)
                .addValue("limit", limit), ENDPOINT_MAPPER));
    }

    /**
     * Listet die Endpunkte <strong>aller</strong> Mandanten ueber die schmale
     * Eigentuemerfunktion des Schemas (ADR-002 §7).
     *
     * @param status          der gewuenschte Status (nullable)
     * @param limit           die Zahl der Zeilen (die Funktion liefert eine mehr)
     * @param afterUpdatedAt  der Sortierschluessel des Cursors (nullable)
     * @param afterEndpointId der zweite Teil des Sortierschluessels (nullable)
     * @return die Endpunkte aller Mandanten
     */
    public List<Endpoint> listCrossTenant(String status,
                                          int limit,
                                          Instant afterUpdatedAt,
                                          UUID afterEndpointId) {
        return crossTenantScope.asServiceAdmin(() -> jdbc.query(SELECT_ENDPOINTS_CROSS_TENANT,
                SqlParameters.source()
                        .addValue("status", status)
                        .addValue("afterUpdatedAt", afterUpdatedAt)
                        .addValue("afterEndpointId", afterEndpointId)
                        .addValue("limit", limit), ENDPOINT_MAPPER));
    }

    /**
     * Aendert Ziel-URL und Ereignistypen.
     *
     * @param organisationId der Mandant
     * @param endpointId     die Endpunkt-ID
     * @param targetUrl      die neue Ziel-URL (nullable = unveraendert)
     * @param eventTypes     die neuen Ereignistypen (nullable = unveraendert)
     * @param now            der Zeitpunkt der Aenderung
     */
    public void update(String organisationId,
                       UUID endpointId,
                       EndpointTargetUrl targetUrl,
                       List<String> eventTypes,
                       Instant now) {
        tenantRunner.execute(organisationId, () -> {
            if (targetUrl != null) {
                jdbc.update(UPDATE_TARGET, SqlParameters.source()
                        .addValue("endpointId", endpointId)
                        .addValue("organisationId", organisationId)
                        .addValue("targetUrl", targetUrl.value())
                        .addValue("targetHost", targetUrl.host()));
            }
            if (eventTypes != null) {
                jdbc.update(DELETE_EVENT_TYPES, SqlParameters.source()
                        .addValue("endpointId", endpointId)
                        .addValue("organisationId", organisationId));
                for (String eventType : eventTypes) {
                    jdbc.update(INSERT_EVENT_TYPE, SqlParameters.source()
                            .addValue("endpointId", endpointId)
                            .addValue("organisationId", organisationId)
                            .addValue("eventType", eventType)
                            .addValue("createdAt", now));
                }
            }
            return null;
        });
    }

    /**
     * Setzt den Zustand des Endpunkts (Pause oder Reaktivierung).
     *
     * @param organisationId der Mandant
     * @param endpointId     die Endpunkt-ID
     * @param status         der neue Zustand
     * @param pausedAt       der Zeitpunkt der Pause (nullable = aktiv)
     */
    public void updateStatus(String organisationId, UUID endpointId, EndpointStatus status, Instant pausedAt) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(UPDATE_STATUS, SqlParameters.source()
                    .addValue("endpointId", endpointId)
                    .addValue("organisationId", organisationId)
                    .addValue("status", status.wireValue())
                    .addValue("pausedAt", pausedAt));
            return null;
        });
    }

    /**
     * Pausiert den Endpunkt und bricht die offenen Zustelllaeufe ab — in
     * <strong>einer</strong> Transaktion (D-03).
     *
     * <p>Welche Laeufe abgebrochen werden, entscheidet der Zustandsautomat
     * ({@link DeliveryStateMachine#onEndpointPaused(DeliveryStatus)}): ein
     * offener Lauf endet mit {@code cancelled}, ein endgueltiger bleibt
     * unveraendert.
     *
     * <p>Ein abgebrochener Lauf ist endgueltig: er bekommt <strong>keinen</strong>
     * naechsten Versuchszeitpunkt (DB-Constraint
     * {@code delivery_runs_terminal_no_next_attempt_ck}).
     *
     * @param organisationId der Mandant
     * @param endpointId     die Endpunkt-ID
     * @param pausedAt       der Zeitpunkt der Pause
     * @return die Zahl der abgebrochenen Laeufe
     */
    public int pause(String organisationId, UUID endpointId, Instant pausedAt) {
        List<String> cancellable = Arrays.stream(DeliveryStatus.values())
                .filter(status -> DeliveryStateMachine.onEndpointPaused(status) == DeliveryStatus.CANCELLED)
                .map(DeliveryStatus::wireValue)
                .toList();
        return tenantRunner.execute(organisationId, () -> {
            jdbc.update(UPDATE_STATUS, SqlParameters.source()
                    .addValue("endpointId", endpointId)
                    .addValue("organisationId", organisationId)
                    .addValue("status", EndpointStatus.PAUSED.wireValue())
                    .addValue("pausedAt", pausedAt));
            return jdbc.update(CANCEL_OPEN_RUNS, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("endpointId", endpointId)
                    .addValue("statuses", cancellable));
        });
    }

    /**
     * Schreibt das neue Secret und vermerkt die Rotation in der Historie.
     *
     * @param organisationId  der Mandant
     * @param endpointId      die Endpunkt-ID
     * @param secretCiphertext die Chiffre des neuen Secrets
     * @param secretNonce     die Nonce der Chiffre
     * @param secretVersion   die neue Secret-Generation
     * @param rotatedAt       der Zeitpunkt der Rotation
     * @param rotatedBy       die ausloesende Identitaet
     */
    public void rotateSecret(String organisationId,
                             UUID endpointId,
                             byte[] secretCiphertext,
                             byte[] secretNonce,
                             int secretVersion,
                             Instant rotatedAt,
                             String rotatedBy) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(UPDATE_SECRET, SqlParameters.source()
                    .addValue("endpointId", endpointId)
                    .addValue("organisationId", organisationId)
                    .addValue("secretCiphertext", secretCiphertext)
                    .addValue("secretNonce", secretNonce)
                    .addValue("secretVersion", secretVersion)
                    .addValue("rotatedAt", rotatedAt));
            jdbc.update(INSERT_SECRET_HISTORY, SqlParameters.source()
                    .addValue("rotationId", idGenerator.newId())
                    .addValue("organisationId", organisationId)
                    .addValue("endpointId", endpointId)
                    .addValue("secretVersion", secretVersion)
                    .addValue("rotatedAt", rotatedAt)
                    .addValue("rotatedBy", rotatedBy));
            return null;
        });
    }

    /**
     * Liest die Endpunkte, die den Ereignistyp abonnieren — <strong>mit</strong>
     * ihrem Zustand.
     *
     * @param organisationId der Mandant
     * @param eventType     der Ereignistyp
     * @return die Abonnenten
     */
    public List<Subscriber> subscribers(String organisationId, String eventType) {
        return tenantRunner.execute(organisationId, () -> jdbc.query(SELECT_SUBSCRIBERS, SqlParameters.source()
                .addValue("organisationId", organisationId)
                .addValue("eventType", eventType), (rs, rowNum) -> new Subscriber(
                        rs.getObject("endpoint_id", UUID.class),
                        EndpointStatus.fromWire(rs.getString("status")))));
    }

    /**
     * Liest das Zustellziel eines Endpunkts — die Ziel-URL, der Zustand und
     * das verschluesselte Secret.
     *
     * <p>Der Zustellzyklus braucht das Secret, um die Zustellung zu
     * signieren (ADR-004). Es wird hier **nicht** entschluesselt — das
     * entschluesselt der Zustellzyklus, unmittelbar vor der Signatur, und
     * gibt es nie aus.
     *
     * @param organisationId der Mandant
     * @param endpointId     die Endpunkt-ID
     * @return das Zustellziel, wenn der Endpunkt im Mandanten existiert
     */
    public Optional<DeliveryTarget> findDeliveryTarget(String organisationId, UUID endpointId) {
        return tenantRunner.execute(organisationId, () -> {
            List<DeliveryTarget> rows = jdbc.query(SELECT_DELIVERY_TARGET, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("endpointId", endpointId), (rs, rowNum) -> new DeliveryTarget(
                    rs.getObject("endpoint_id", UUID.class),
                    rs.getString("organisation_id"),
                    rs.getString("target_url"),
                    EndpointStatus.fromWire(rs.getString("status")),
                    rs.getBytes("secret_ciphertext"),
                    rs.getBytes("secret_nonce")));
            return rows.stream().findFirst();
        });
    }

    private static final org.springframework.jdbc.core.RowMapper<Endpoint> ENDPOINT_MAPPER =
            (rs, rowNum) -> mapEndpoint(rs);

    private static Endpoint mapEndpoint(ResultSet rs) throws SQLException {
        return new Endpoint(
                rs.getObject("endpoint_id", UUID.class),
                rs.getString("organisation_id"),
                storedTargetUrl(rs.getString("target_url")),
                EndpointStatus.fromWire(rs.getString("status")),
                readTextArray(rs.getArray("event_types")),
                rs.getInt("secret_version"),
                rs.getTimestamp("secret_rotated_at") == null
                        ? null : rs.getTimestamp("secret_rotated_at").toInstant(),
                rs.getTimestamp("paused_at") == null ? null : rs.getTimestamp("paused_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    /**
     * Liest die gespeicherte Ziel-URL. Ein Verstoß gegen das Schema ist ein
     * <strong>Datenfehler</strong> (kein Anfragefehler) — die Datenbank
     * verhindert ihn durch ihre Constraints.
     */
    private static EndpointTargetUrl storedTargetUrl(String raw) {
        try {
            return EndpointTargetUrl.of(raw);
        } catch (com.echobox.common.ValidationException e) {
            throw new IllegalStateException("Die gespeicherte Ziel-URL verletzt das Schema.", e);
        }
    }

    private static List<String> readTextArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        try {
            Object raw = array.getArray();
            List<String> values = new ArrayList<>();
            if (raw instanceof Object[] elements) {
                for (Object element : elements) {
                    values.add(element == null ? null : element.toString());
                }
            }
            return List.copyOf(values);
        } finally {
            array.free();
        }
    }

    /**
     * Ein Abonnent des Ereignistyps — der Endpunkt und sein Zustand.
     *
     * @param endpointId die Endpunkt-ID
     * @param status     der Zustand des Endpunkts
     */
    public record Subscriber(UUID endpointId, EndpointStatus status) {
    }

    /**
     * Das Zustellziel eines Endpunkts: Ziel-URL, Zustand und das
     * verschluesselte Secret.
     *
     * <p>Das Secret bleibt **verschluesselt** — die Entschluesselung
     * gehoert in den Zustellzyklus und wird dort unmittelbar verwendet.
     *
     * @param endpointId       die Endpunkt-ID
     * @param organisationId   der besitzende Mandant
     * @param targetUrl        die registrierte Ziel-URL
     * @param status           der Zustand des Endpunkts
     * @param secretCiphertext die Chiffre des Secrets
     * @param secretNonce      die Nonce der Chiffre
     */
    public record DeliveryTarget(UUID endpointId,
                                  String organisationId,
                                  String targetUrl,
                                  EndpointStatus status,
                                  byte[] secretCiphertext,
                                  byte[] secretNonce) {

        /**
         * Erzeugt ein Zustellziel mit kopierten Feldern.
         *
         * @param endpointId       die Endpunkt-ID
         * @param organisationId   der besitzende Mandant
         * @param targetUrl        die registrierte Ziel-URL
         * @param status           der Zustand des Endpunkts
         * @param secretCiphertext die Chiffre des Secrets
         * @param secretNonce      die Nonce der Chiffre
         */
        public DeliveryTarget {
            secretCiphertext = secretCiphertext == null ? new byte[0] : secretCiphertext.clone();
            secretNonce = secretNonce == null ? new byte[0] : secretNonce.clone();
        }
    }
}
