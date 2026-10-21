package com.echobox.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.echobox.common.SqlParameters;
import com.echobox.tenant.CrossTenantScope;
import com.echobox.tenant.TenantTransactionRunner;

/**
 * Der JDBC-Adapter des Zustellmoduls — {@code delivery_runs} und
 * {@code delivery_attempts}.
 *
 * <p><strong>Mandant vor allem anderen:</strong> jeder mandantenbezogene
 * Zugriff laeuft ueber {@link TenantTransactionRunner} — der Mandant wird
 * <strong>innerhalb</strong> der Transaktion gesetzt, die auch liest
 * (ADR-002 §3). Zusaetzlich traegt <strong>jede</strong> Abfrage den Mandanten
 * <strong>explizit</strong>: die Datenbank erzwingt die Trennung durch RLS,
 * die Anwendung filtert <strong>zusaetzlich</strong> (NFR-003).
 *
 * <p><strong>Die Uhr der Beanspruchung ist die Datenbankuhr</strong>
 * ({@code now()}): die Lease ist ein Wettlauf zwischen Instanzen — alle
 * Instanzen muessen dieselbe Zeitbasis benutzen. Die fachlichen Zeitpunkte
 * (Annahme, Versuch, Backoff) kommen dagegen aus der Uhr der Anwendung
 * ({@code ClockPort}).
 *
 * <p>Alle Statements sind <strong>vorbereitet</strong> und benutzen
 * <strong>benannte Parameter</strong> — es wird kein SQL zusammengesetzt und
 * kein Wert verkettet.
 */
@Repository
public class DeliveryRepository {

    /** Die Mandanten, fuer die es Arbeit gibt — die Mandantenermittlung. */
    private static final String SELECT_TENANT_IDS = """
            SELECT organisation_id FROM app.tenant_ids_for_worker()
            """;

    /** Die Spalten eines Zustelllaufs — an einer Stelle, damit die Abbildung einheitlich bleibt. */
    private static final String RUN_COLUMNS = """
            delivery_run_id, organisation_id, event_id, endpoint_id, event_type, status,
            attempt_count, max_attempts, next_attempt_at, last_attempt_at, last_response_status,
            last_error_code, rejection_reason, abandoned_at, replay_of, lease_owner,
            lease_expires_at, created_at, updated_at
            """;

    /**
     * Die Beanspruchung: faellige Laeufe des Mandanten, die **nicht** von einer
     * anderen Instanz beansprucht sind (ADR-006 §3).
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} sorgt dafuer, dass konkurrierende
     * Instanzen sich nicht blockieren, sondern aneinander vorbeigehen — und
     * damit genau eine Instanz einen Lauf beansprucht (AC-023-3).
     */
    private static final String SELECT_DUE_RUNS = """
            SELECT %s
            FROM app.delivery_runs
            WHERE organisation_id = :organisationId
              AND status IN ('pending', 'scheduled', 'failed', 'delayed')
              AND next_attempt_at IS NOT NULL
              AND next_attempt_at <= :now
              AND (lease_expires_at IS NULL OR lease_expires_at <= now())
            ORDER BY next_attempt_at, delivery_run_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """.formatted(RUN_COLUMNS);

    /** Die Beanspruchung: Lease setzen und den Lauf als laufend kennzeichnen. */
    private static final String CLAIM_RUN = """
            UPDATE app.delivery_runs
            SET lease_owner = :instanceId,
                lease_expires_at = now() + make_interval(secs => :leaseSeconds),
                status = :status
            WHERE delivery_run_id = :deliveryRunId
              AND organisation_id = :organisationId
            """;

    /** Der Versuch — die Zeile des Protokolls. */
    private static final String INSERT_ATTEMPT = """
            INSERT INTO app.delivery_attempts
                (attempt_id, organisation_id, delivery_run_id, event_id, endpoint_id, event_accepted_at,
                 attempt_number, started_at, finished_at, duration_ms, response_status, outcome,
                 error_code, next_attempt_at, created_at)
            VALUES
                (:attemptId, :organisationId, :deliveryRunId, :eventId, :endpointId, :eventAcceptedAt,
                 :attemptNumber, :startedAt, :finishedAt, :durationMs, :responseStatus, :outcome,
                 :errorCode, :nextAttemptAt, :createdAt)
            """;

    /** Der Zustand des Laufs nach dem Versuch. */
    private static final String UPDATE_RUN_AFTER_ATTEMPT = """
            UPDATE app.delivery_runs
            SET status = :status,
                attempt_count = :attemptCount,
                next_attempt_at = :nextAttemptAt,
                last_attempt_at = :lastAttemptAt,
                last_response_status = :lastResponseStatus,
                last_error_code = :lastErrorCode,
                rejection_reason = :rejectionReason,
                abandoned_at = :abandonedAt,
                lease_owner = :leaseOwner,
                lease_expires_at = NULL
            WHERE delivery_run_id = :deliveryRunId
              AND organisation_id = :organisationId
            """;

    /** Die Freigabe der Lease ohne Versuch (verschobener oder abgebrochener Lauf). */
    private static final String RELEASE_RUN = """
            UPDATE app.delivery_runs
            SET status = :status,
                next_attempt_at = :nextAttemptAt,
                lease_owner = :leaseOwner,
                lease_expires_at = NULL
            WHERE delivery_run_id = :deliveryRunId
              AND organisation_id = :organisationId
            """;

    /** Ein Zustelllauf im Mandanten. */
    private static final String SELECT_RUN = """
            SELECT %s
            FROM app.delivery_runs
            WHERE organisation_id = :organisationId
              AND delivery_run_id = :deliveryRunId
              AND deleted_at IS NULL
            """.formatted(RUN_COLUMNS);

    /** Die Versuche eines Laufs — chronologisch. */
    private static final String SELECT_ATTEMPTS = """
            SELECT attempt_id, organisation_id, delivery_run_id, event_id, endpoint_id, event_accepted_at,
                   attempt_number, started_at, finished_at, duration_ms, response_status, outcome,
                   error_code, next_attempt_at
            FROM app.delivery_attempts
            WHERE organisation_id = :organisationId
              AND delivery_run_id = :deliveryRunId
            ORDER BY attempt_number
            """;

    /** Das Zustellprotokoll — mandantengefiltert, ueber Metadaten (ADR-017). */
    private static final String SELECT_RUNS = """
            SELECT %s
            FROM app.delivery_runs
            WHERE organisation_id = :organisationId
              AND deleted_at IS NULL
              AND (CAST(:endpointId AS UUID) IS NULL OR endpoint_id = CAST(:endpointId AS UUID))
              AND (CAST(:eventId AS UUID) IS NULL OR event_id = CAST(:eventId AS UUID))
              AND (CAST(:eventType AS TEXT) IS NULL OR event_type = CAST(:eventType AS TEXT))
              AND (CAST(:statuses AS TEXT) IS NULL
                   OR status = ANY(string_to_array(CAST(:statuses AS TEXT), ',')))
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR created_at < CAST(:to AS TIMESTAMPTZ))
              AND (CAST(:afterCreatedAt AS TIMESTAMPTZ) IS NULL
                   OR (created_at, delivery_run_id)
                      < (CAST(:afterCreatedAt AS TIMESTAMPTZ), CAST(:afterDeliveryRunId AS UUID)))
            ORDER BY created_at DESC, delivery_run_id DESC
            LIMIT :limit
            """.formatted(RUN_COLUMNS);

    /** Die Totenliste mandantenuebergreifend — ueber die schmale Eigentuemerfunktion. */
    private static final String SELECT_DEAD_DELIVERIES_CROSS_TENANT = """
            SELECT delivery_run_id, organisation_id, event_id, endpoint_id, endpoint_url, event_type,
                   status, attempt_count, last_response_status, last_attempt_at, abandoned_at, created_at
            FROM app.list_dead_deliveries_cross_tenant(
                CAST(:endpointId AS UUID), CAST(:eventType AS TEXT), :limit,
                CAST(:from AS TIMESTAMPTZ), CAST(:to AS TIMESTAMPTZ),
                CAST(:afterCreatedAt AS TIMESTAMPTZ), CAST(:afterDeliveryRunId AS UUID))
            """;

    /** Die Kennzahlen eines Endpunkts im Auswertungsfenster (ADR-011). */
    private static final String SELECT_METRICS = """
            SELECT count(*) AS attempts_total,
                   count(*) FILTER (WHERE a.outcome = 'success') AS attempts_successful,
                   percentile_cont(0.95) WITHIN GROUP (
                       ORDER BY EXTRACT(EPOCH FROM (a.finished_at - a.event_accepted_at)) * 1000
                   ) FILTER (WHERE a.outcome = 'success') AS latency_p95_ms
            FROM app.delivery_attempts a
            JOIN app.delivery_runs dr ON dr.delivery_run_id = a.delivery_run_id
            WHERE a.organisation_id = :organisationId
              AND a.endpoint_id = CAST(:endpointId AS UUID)
              AND a.started_at >= CAST(:from AS TIMESTAMPTZ)
              AND a.started_at <= CAST(:to AS TIMESTAMPTZ)
              AND dr.deleted_at IS NULL
            """;

    /** Der konfigurierte Grenzwert eines Endpunkts (nullable = kein Override). */
    private static final String SELECT_RATE_LIMIT_SETTING = """
            SELECT limit_value, window_seconds, strategy
            FROM app.endpoint_rate_limits
            WHERE organisation_id = :organisationId
              AND endpoint_id = CAST(:endpointId AS UUID)
            """;

    /** Die Zaehlung der Zustellversuche je Endpunkt und Zeitfenster. */
    private static final String SELECT_RATE_LIMIT_COUNTERS = """
            SELECT COALESCE(sum(attempt_count), 0) AS observed_attempts,
                   COALESCE(sum(delayed_count), 0) AS delayed_attempts
            FROM app.endpoint_rate_limit_counters
            WHERE organisation_id = :organisationId
              AND endpoint_id = CAST(:endpointId AS UUID)
              AND window_start >= CAST(:from AS TIMESTAMPTZ)
              AND window_start <= CAST(:to AS TIMESTAMPTZ)
            """;

    /** Das aktuelle Zaehlfenster eines Endpunkts. */
    private static final String SELECT_RATE_LIMIT_WINDOW = """
            SELECT window_start, attempt_count, delayed_count
            FROM app.endpoint_rate_limit_counters
            WHERE organisation_id = :organisationId
              AND endpoint_id = CAST(:endpointId AS UUID)
              AND window_start > (CAST(:now AS TIMESTAMPTZ) - make_interval(secs => :windowSeconds))
            ORDER BY window_start DESC
            LIMIT 1
            """;

    /** Die Zaehlung fortschreiben — der Zaehler laeuft immer (ADR-010 §1). */
    private static final String UPSERT_RATE_LIMIT_COUNTER = """
            INSERT INTO app.endpoint_rate_limit_counters
                (endpoint_id, organisation_id, window_start, attempt_count, delayed_count)
            VALUES
                (CAST(:endpointId AS UUID), :organisationId, CAST(:windowStart AS TIMESTAMPTZ), 1, :delayed)
            ON CONFLICT (endpoint_id, window_start) DO UPDATE
            SET attempt_count = app.endpoint_rate_limit_counters.attempt_count + 1,
                delayed_count = app.endpoint_rate_limit_counters.delayed_count + EXCLUDED.delayed_count
            """;

    /** Ein neuer Zustelllauf — der Replay (ADR-006 §6). */
    private static final String INSERT_RUN = """
            INSERT INTO app.delivery_runs
                (delivery_run_id, organisation_id, event_id, endpoint_id, event_type, status,
                 attempt_count, max_attempts, next_attempt_at, replay_of, created_at, updated_at)
            VALUES
                (:deliveryRunId, :organisationId, :eventId, :endpointId, :eventType, :status,
                 0, :maxAttempts, :nextAttemptAt, :replayOf, :createdAt, :createdAt)
            """;

    /** Der Replay eines bereits replayten Laufs — die Einmal-Regel (I-9). */
    private static final String SELECT_REPLAY = """
            SELECT delivery_run_id
            FROM app.delivery_runs
            WHERE organisation_id = :organisationId
              AND replay_of = CAST(:replayOf AS UUID)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    private final TenantTransactionRunner tenantRunner;

    private final CrossTenantScope crossTenantScope;

    /**
     * Erzeugt den Adapter.
     *
     * @param jdbcTemplate      die JDBC-Vorlage
     * @param tenantRunner      der Mandantentransaktions-Runner
     * @param crossTenantScope  der mandantenuebergreifende Leseweg
     */
    public DeliveryRepository(JdbcTemplate jdbcTemplate,
                              TenantTransactionRunner tenantRunner,
                              CrossTenantScope crossTenantScope) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.tenantRunner = tenantRunner;
        this.crossTenantScope = crossTenantScope;
    }

    /**
     * Liefert die Mandanten, fuer die es Arbeit gibt — die Mandantenermittlung
     * des Workers (ADR-006 §3).
     *
     * <p>Die Funktion ist schmal zugeschnitten: sie liefert nur
     * Mandantenkennungen, keine Nutzlast und keine Mandantendaten.
     *
     * @return die Mandantenkennungen
     */
    public List<String> tenantIdsForWorker() {
        return jdbc.query(SELECT_TENANT_IDS, SqlParameters.source(), (rs, rowNum) -> rs.getString("organisation_id"));
    }

    /**
     * Beansprucht die faelligen Zustelllaeufe des Mandanten.
     *
     * <p>Die Beanspruchung geschieht in <strong>einer</strong> Transaktion: das
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} nimmt die Zeilen, das
     * {@code UPDATE} setzt die Lease. Nach dem Commit ist der Lauf von genau
     * einer Instanz beansprucht; stirbt die Instanz, laeuft die Lease ab und
     * ein anderer Worker nimmt den Lauf wieder auf (AC-023-4).
     *
     * @param organisationId der Mandant
     * @param instanceId     die beanspruchende Instanz
     * @param now            der Bezugszeitpunkt (die Uhr der Anwendung)
     * @param leaseSeconds   die Dauer der Lease in Sekunden
     * @param limit          die hoechste Zahl der beanspruchten Laeufe
     * @return die beanspruchten Laeufe
     */
    public List<DeliveryRun> claimDueRuns(String organisationId,
                                           String instanceId,
                                           Instant now,
                                           int leaseSeconds,
                                           int limit) {
        return tenantRunner.execute(organisationId, () -> {
            List<DeliveryRun> candidates = jdbc.query(SELECT_DUE_RUNS, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("now", now)
                    .addValue("limit", limit), RUN_MAPPER);
            for (DeliveryRun candidate : candidates) {
                jdbc.update(CLAIM_RUN, SqlParameters.source()
                        .addValue("instanceId", instanceId)
                        .addValue("leaseSeconds", leaseSeconds)
                        .addValue("status", DeliveryStateMachine.onClaimed(candidate.status()).wireValue())
                        .addValue("deliveryRunId", candidate.deliveryRunId())
                        .addValue("organisationId", organisationId));
            }
            return candidates.stream()
                    .map(run -> new DeliveryRun(run.deliveryRunId(), run.organisationId(), run.eventId(),
                            run.endpointId(), run.eventType(), DeliveryStateMachine.onClaimed(run.status()),
                            run.attemptCount(), run.maxAttempts(), run.nextAttemptAt(), run.lastAttemptAt(),
                            run.lastResponseStatus(), run.lastErrorCode(), run.rejectionReason(),
                            run.abandonedAt(), run.replayOf(), instanceId, null,
                            run.createdAt(), run.updatedAt()))
                    .toList();
        });
    }

    /**
     * Schreibt den Versuch und den neuen Zustand des Laufs — in <strong>einer</strong>
     * Transaktion (das Protokoll stimmt mit dem Zustand ueberein).
     *
     * @param organisationId  der Mandant
     * @param attempt         der Versuch
     * @param status          der neue Zustand des Laufs
     * @param attemptCount    die Zahl der Versuche nach diesem Versuch
     * @param nextAttemptAt   der naechste Versuchszeitpunkt (nullable)
     * @param lastAttemptAt   der Zeitpunkt des letzten Versuchs
     * @param responseStatus  der Antwortcode (nullable)
     * @param errorCode       der Fehlercode (nullable)
     * @param rejectionReason der Grund der Ablehnung (nullable)
     * @param abandonedAt     der Zeitpunkt der Aufgabe (nullable)
     * @param leaseOwner      die Instanz, die den Versuch gefuehrt hat (nullable bei Freigabe)
     */
    public void recordAttempt(String organisationId,
                               DeliveryAttempt attempt,
                               DeliveryStatus status,
                               int attemptCount,
                               Instant nextAttemptAt,
                               Instant lastAttemptAt,
                               Integer responseStatus,
                               String errorCode,
                               String rejectionReason,
                               Instant abandonedAt,
                               String leaseOwner) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(INSERT_ATTEMPT, SqlParameters.source()
                    .addValue("attemptId", attempt.attemptId())
                    .addValue("organisationId", organisationId)
                    .addValue("deliveryRunId", attempt.deliveryRunId())
                    .addValue("eventId", attempt.eventId())
                    .addValue("endpointId", attempt.endpointId())
                    .addValue("eventAcceptedAt", attempt.eventAcceptedAt())
                    .addValue("attemptNumber", attempt.attemptNumber())
                    .addValue("startedAt", attempt.startedAt())
                    .addValue("finishedAt", attempt.finishedAt())
                    .addValue("durationMs", attempt.durationMs())
                    .addValue("responseStatus", attempt.responseStatus())
                    .addValue("outcome", attempt.outcome().wireValue())
                    .addValue("errorCode", attempt.errorCode())
                    .addValue("nextAttemptAt", attempt.nextAttemptAt())
                    .addValue("createdAt", attempt.startedAt()));
            jdbc.update(UPDATE_RUN_AFTER_ATTEMPT, SqlParameters.source()
                    .addValue("status", status.wireValue())
                    .addValue("attemptCount", attemptCount)
                    .addValue("nextAttemptAt", nextAttemptAt)
                    .addValue("lastAttemptAt", lastAttemptAt)
                    .addValue("lastResponseStatus", responseStatus)
                    .addValue("lastErrorCode", errorCode)
                    .addValue("rejectionReason", rejectionReason)
                    .addValue("abandonedAt", abandonedAt)
                    .addValue("leaseOwner", leaseOwner)
                    .addValue("deliveryRunId", attempt.deliveryRunId())
                    .addValue("organisationId", organisationId));
            return null;
        });
    }

    /**
     * Gibt die Lease eines Laufs frei, ohne einen Versuch zu schreiben — der
     * verschobene (Endpunkt-Limit) und der abgebrochene (Pause) Lauf.
     *
     * @param organisationId der Mandant
     * @param deliveryRunId  der Zustelllauf
     * @param status         der neue Zustand
     * @param nextAttemptAt  der naechste Versuchszeitpunkt (nullable)
     * @param leaseOwner     die Instanz, die den Lauf freigibt (nullable)
     */
    public void releaseRun(String organisationId,
                           UUID deliveryRunId,
                           DeliveryStatus status,
                           Instant nextAttemptAt,
                           String leaseOwner) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(RELEASE_RUN, SqlParameters.source()
                    .addValue("status", status.wireValue())
                    .addValue("nextAttemptAt", nextAttemptAt)
                    .addValue("leaseOwner", leaseOwner)
                    .addValue("deliveryRunId", deliveryRunId)
                    .addValue("organisationId", organisationId));
            return null;
        });
    }

    /**
     * Liest einen Zustelllauf im Mandanten.
     *
     * @param organisationId der Mandant
     * @param deliveryRunId  die Zustelllauf-ID
     * @return der Zustelllauf, wenn er im Mandanten existiert
     */
    public Optional<DeliveryRun> findById(String organisationId, UUID deliveryRunId) {
        return tenantRunner.execute(organisationId, () -> {
            List<DeliveryRun> rows = jdbc.query(SELECT_RUN, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("deliveryRunId", deliveryRunId), RUN_MAPPER);
            return rows.stream().findFirst();
        });
    }

    /**
     * Liest die Versuche eines Zustelllaufs — chronologisch.
     *
     * @param organisationId der Mandant
     * @param deliveryRunId  die Zustelllauf-ID
     * @return die Versuche
     */
    public List<DeliveryAttempt> attempts(String organisationId, UUID deliveryRunId) {
        return tenantRunner.execute(organisationId, () -> jdbc.query(SELECT_ATTEMPTS, SqlParameters.source()
                .addValue("organisationId", organisationId)
                .addValue("deliveryRunId", deliveryRunId), ATTEMPT_MAPPER));
    }

    /**
     * Listet das Zustellprotokoll des Mandanten — mandantengefiltert und
     * ausschliesslich ueber Metadaten (ADR-017).
     *
     * @param organisationId    der Mandant
     * @param endpointId        der Filter auf den Endpunkt (nullable)
     * @param eventId           der Filter auf die Ereignis-ID (nullable)
     * @param eventType         der Filter auf den Ereignistyp (nullable)
     * @param statuses          die gewuenschten Zustaende (leer = alle)
     * @param from              der Beginn des Zeitraums auf {@code created_at} (nullable)
     * @param to                das Ende des Zeitraums auf {@code created_at} (nullable, exklusiv)
     * @param limit             die Zahl der Zeilen (die Abfrage liefert eine mehr)
     * @param afterCreatedAt    der Sortierschluessel des Cursors (nullable)
     * @param afterDeliveryRunId der zweite Teil des Sortierschluessels (nullable)
     * @return die Zustelllaeufe
     */
    public List<DeliveryRun> list(String organisationId,
                                   UUID endpointId,
                                   UUID eventId,
                                   String eventType,
                                   List<String> statuses,
                                   Instant from,
                                   Instant to,
                                   int limit,
                                   Instant afterCreatedAt,
                                   UUID afterDeliveryRunId) {
        return tenantRunner.execute(organisationId, () -> jdbc.query(SELECT_RUNS, SqlParameters.source()
                .addValue("organisationId", organisationId)
                .addValue("endpointId", endpointId)
                .addValue("eventId", eventId)
                .addValue("eventType", eventType)
                .addValue("statuses", statuses == null || statuses.isEmpty() ? null : String.join(",", statuses))
                .addValue("from", from)
                .addValue("to", to)
                .addValue("afterCreatedAt", afterCreatedAt)
                .addValue("afterDeliveryRunId", afterDeliveryRunId)
                .addValue("limit", limit), RUN_MAPPER));
    }

    /**
     * Listet die toten Zustellungen <strong>aller</strong> Mandanten — ueber
     * die schmale Eigentuemerfunktion des Schemas (ADR-002 §7).
     *
     * <p>Der Status ist in der Funktion <strong>fest verdrahtet</strong>: sie
     * liefert ausschliesslich aufgegebene Zustaende. Die Funktion ist
     * fail-closed — ohne die Freigabe {@code app.cross_tenant} liefert sie
     * keine Zeile.
     *
     * @param endpointId         der Filter auf den Endpunkt (nullable)
     * @param eventType          der Filter auf den Ereignistyp (nullable)
     * @param limit              die Zahl der Zeilen (die Funktion liefert eine mehr)
     * @param from               der Beginn des Zeitraums auf {@code abandoned_at} (nullable)
     * @param to                 das Ende des Zeitraums auf {@code abandoned_at} (nullable, exklusiv)
     * @param afterCreatedAt     der Sortierschluessel des Cursors (nullable)
     * @param afterDeliveryRunId der zweite Teil des Sortierschluessels (nullable)
     * @return die toten Zustellungen
     */
    public List<DeadDelivery> listDeadDeliveriesCrossTenant(UUID endpointId,
                                                             String eventType,
                                                             int limit,
                                                             Instant from,
                                                             Instant to,
                                                             Instant afterCreatedAt,
                                                             UUID afterDeliveryRunId) {
        return crossTenantScope.asServiceAdmin(() -> jdbc.query(SELECT_DEAD_DELIVERIES_CROSS_TENANT,
                SqlParameters.source()
                        .addValue("endpointId", endpointId)
                        .addValue("eventType", eventType)
                        .addValue("from", from)
                        .addValue("to", to)
                        .addValue("afterCreatedAt", afterCreatedAt)
                        .addValue("afterDeliveryRunId", afterDeliveryRunId)
                        .addValue("limit", limit), DEAD_DELIVERY_MAPPER));
    }

    /**
     * Berechnet die Kennzahlen eines Endpunkts im Auswertungsfenster.
     *
     * <p><strong>Markierte Versuche zaehlen nicht mit:</strong> ein markierter
     * Zustelllauf macht seine Versuche unsichtbar (N-13, ADR-012 §3) — die
     * Bedingung wird hier <strong>zusaetzlich</strong> zur Policy der
     * Datenbank geprueft, damit die Kennzahlen auch dann stimmen, wenn die
     * Anwendung mit einer Rolle ohne RLS arbeitet.
     *
     * @param organisationId der Mandant
     * @param endpointId     der Endpunkt
     * @param from           der Beginn des Auswertungsfensters
     * @param to             das Ende des Auswertungsfensters (einschliesslich)
     * @return die Kennzahlen
     */
    public EndpointMetricsRow metrics(String organisationId, UUID endpointId, Instant from, Instant to) {
        return tenantRunner.execute(organisationId, () -> jdbc.queryForObject(SELECT_METRICS,
                SqlParameters.source()
                        .addValue("organisationId", organisationId)
                        .addValue("endpointId", endpointId)
                        .addValue("from", from)
                        .addValue("to", to), (rs, rowNum) -> new EndpointMetricsRow(
                                rs.getLong("attempts_total"),
                                rs.getLong("attempts_successful"),
                                rounded(rs.getObject("latency_p95_ms")))));
    }

    /**
     * Rundet eine Kennzahl auf ganze Millisekunden.
     *
     * <p>{@code percentile_cont} liefert eine {@code DOUBLE PRECISION} — die
     * p95-Latenz wird als ganze Millisekunde ausgewiesen.
     *
     * @param value der Wert (nullable)
     * @return der gerundete Wert oder {@code null}
     */
    private static Long rounded(Object value) {
        return value == null ? null : Math.round(((Number) value).doubleValue());
    }

    /**
     * Liest den konfigurierten Grenzwert eines Endpunkts.
     *
     * @param organisationId der Mandant
     * @param endpointId     der Endpunkt
     * @return der Grenzwert, wenn der Endpunkt einen hat
     */
    public Optional<RateLimitSetting> rateLimitSetting(String organisationId, UUID endpointId) {
        return tenantRunner.execute(organisationId, () -> {
            List<RateLimitSetting> rows = jdbc.query(SELECT_RATE_LIMIT_SETTING, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("endpointId", endpointId), (rs, rowNum) -> new RateLimitSetting(
                    (Integer) rs.getObject("limit_value"),
                    rs.getInt("window_seconds"),
                    RateLimitStrategy.fromWire(rs.getString("strategy"))));
            return rows.stream().findFirst();
        });
    }

    /**
     * Liest die Zaehlung der Zustellversuche eines Endpunkts im Zeitraum.
     *
     * @param organisationId der Mandant
     * @param endpointId     der Endpunkt
     * @param from           der Beginn des Zeitraums
     * @param to             das Ende des Zeitraums (einschliesslich)
     * @return die Zaehlung
     */
    public RateLimitCounters rateLimitCounters(String organisationId, UUID endpointId, Instant from, Instant to) {
        return tenantRunner.execute(organisationId, () -> jdbc.queryForObject(SELECT_RATE_LIMIT_COUNTERS,
                SqlParameters.source()
                        .addValue("organisationId", organisationId)
                        .addValue("endpointId", endpointId)
                        .addValue("from", from)
                        .addValue("to", to), (rs, rowNum) -> new RateLimitCounters(
                        rs.getLong("observed_attempts"),
                        rs.getLong("delayed_attempts"))));
    }

    /**
     * Liest das aktuelle Zaehlfenster eines Endpunkts.
     *
     * @param organisationId der Mandant
     * @param endpointId     der Endpunkt
     * @param now            der Bezugszeitpunkt
     * @param windowSeconds  die Laenge des Fensters in Sekunden
     * @return das Fenster, wenn es eines gibt
     */
    public Optional<RateLimitWindow> rateLimitWindow(String organisationId,
                                                      UUID endpointId,
                                                      Instant now,
                                                      int windowSeconds) {
        return tenantRunner.execute(organisationId, () -> {
            List<RateLimitWindow> rows = jdbc.query(SELECT_RATE_LIMIT_WINDOW, SqlParameters.source()
                    .addValue("organisationId", organisationId)
                    .addValue("endpointId", endpointId)
                    .addValue("now", now)
                    .addValue("windowSeconds", windowSeconds), (rs, rowNum) -> new RateLimitWindow(
                    rs.getTimestamp("window_start").toInstant(),
                    rs.getLong("attempt_count"),
                    rs.getLong("delayed_count")));
            return rows.stream().findFirst();
        });
    }

    /**
     * Schreibt die Zaehlung eines Zustellversuchs fort — der Zaehler laeuft
     * <strong>immer</strong>, unabhaengig vom Grenzwert (ADR-010 §1).
     *
     * @param organisationId der Mandant
     * @param endpointId     der Endpunkt
     * @param windowStart    der Beginn des Zaehlfensters
     * @param delayed        ob der Versuch durch das Limit verschoben wurde
     */
    public void countRateLimitAttempt(String organisationId,
                                      UUID endpointId,
                                      Instant windowStart,
                                      boolean delayed) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(UPSERT_RATE_LIMIT_COUNTER, SqlParameters.source()
                    .addValue("endpointId", endpointId)
                    .addValue("organisationId", organisationId)
                    .addValue("windowStart", windowStart)
                    .addValue("delayed", delayed ? 1 : 0));
            return null;
        });
    }

    /**
     * Legt einen neuen Zustelllauf an — der Replay (ADR-006 §6).
     *
     * @param organisationId der Mandant
     * @param deliveryRunId  die neue Zustelllauf-ID
     * @param eventId        die Ereignis-ID des Vorgängers
     * @param endpointId     der Endpunkt
     * @param eventType      der Ereignistyp
     * @param maxAttempts    die Versuchsgrenze
     * @param nextAttemptAt  der naechste Versuchszeitpunkt
     * @param replayOf       der Vorgängerlauf
     * @param now            der Zeitpunkt der Anlage
     */
    public void insertRun(String organisationId,
                          UUID deliveryRunId,
                          UUID eventId,
                          UUID endpointId,
                          String eventType,
                          DeliveryStatus status,
                          Integer maxAttempts,
                          Instant nextAttemptAt,
                          UUID replayOf,
                          Instant now) {
        tenantRunner.execute(organisationId, () -> {
            jdbc.update(INSERT_RUN, SqlParameters.source()
                    .addValue("deliveryRunId", deliveryRunId)
                    .addValue("organisationId", organisationId)
                    .addValue("eventId", eventId)
                    .addValue("endpointId", endpointId)
                    .addValue("eventType", eventType)
                    .addValue("status", status.wireValue())
                    .addValue("maxAttempts", maxAttempts)
                    .addValue("nextAttemptAt", nextAttemptAt)
                    .addValue("replayOf", replayOf)
                    .addValue("createdAt", now)
                    .addValue("updatedAt", now));
            return null;
        });
    }

    /**
     * Prueft, ob ein Zustelllauf bereits replayed wurde — die Einmal-Regel
     * (I-9, ADR-006 §6).
     *
     * @param organisationId der Mandant
     * @param replayOf       der Vorgängerlauf
     * @return {@code true}, wenn der Replay bereits existiert
     */
    public boolean replayExists(String organisationId, UUID replayOf) {
        return tenantRunner.execute(organisationId, () -> !jdbc.query(SELECT_REPLAY, SqlParameters.source()
                .addValue("organisationId", organisationId)
                .addValue("replayOf", replayOf), (rs, rowNum) -> rs.getObject("delivery_run_id", UUID.class)).isEmpty());
    }

    private static final org.springframework.jdbc.core.RowMapper<DeliveryRun> RUN_MAPPER =
            (rs, rowNum) -> mapRun(rs);

    private static final org.springframework.jdbc.core.RowMapper<DeliveryAttempt> ATTEMPT_MAPPER =
            (rs, rowNum) -> mapAttempt(rs);

    private static final org.springframework.jdbc.core.RowMapper<DeadDelivery> DEAD_DELIVERY_MAPPER =
            (rs, rowNum) -> mapDeadDelivery(rs);

    private static DeliveryRun mapRun(ResultSet rs) throws SQLException {
        return new DeliveryRun(
                rs.getObject("delivery_run_id", UUID.class),
                rs.getString("organisation_id"),
                rs.getObject("event_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getString("event_type"),
                DeliveryStatus.fromWire(rs.getString("status")),
                rs.getInt("attempt_count"),
                (Integer) rs.getObject("max_attempts"),
                instant(rs, "next_attempt_at"),
                instant(rs, "last_attempt_at"),
                (Integer) rs.getObject("last_response_status"),
                rs.getString("last_error_code"),
                rs.getString("rejection_reason"),
                instant(rs, "abandoned_at"),
                rs.getObject("replay_of", UUID.class),
                rs.getString("lease_owner"),
                instant(rs, "lease_expires_at"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static DeliveryAttempt mapAttempt(ResultSet rs) throws SQLException {
        return new DeliveryAttempt(
                rs.getObject("attempt_id", UUID.class),
                rs.getString("organisation_id"),
                rs.getObject("delivery_run_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getTimestamp("event_accepted_at").toInstant(),
                rs.getInt("attempt_number"),
                rs.getTimestamp("started_at").toInstant(),
                instant(rs, "finished_at"),
                longValue(rs, "duration_ms"),
                (Integer) rs.getObject("response_status"),
                DeliveryOutcome.fromWire(rs.getString("outcome")),
                rs.getString("error_code"),
                instant(rs, "next_attempt_at"));
    }

    private static DeadDelivery mapDeadDelivery(ResultSet rs) throws SQLException {
        return new DeadDelivery(
                rs.getObject("delivery_run_id", UUID.class),
                rs.getString("organisation_id"),
                rs.getObject("event_id", UUID.class),
                rs.getObject("endpoint_id", UUID.class),
                rs.getString("endpoint_url"),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                (Integer) rs.getObject("last_response_status"),
                instant(rs, "last_attempt_at"),
                instant(rs, "abandoned_at"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /**
     * Liest eine Zahl unabhängig von ihrem SQL-Typ.
     *
     * <p>Die Spalten der Datenbank sind teils {@code INTEGER}, teils
     * {@code BIGINT}, und {@code percentile_cont} liefert {@code DOUBLE
     * PRECISION} — der Treiber gibt aber jeweils den Java-Typ der Spalte
     * zurueck. Die Umwandlung laeuft deshalb ueber {@link Number}.
     *
     * @param rs     die Ergebnismenge
     * @param column die Spalte
     * @return der Wert oder {@code null}, wenn die Spalte leer ist
     */
    private static Long longValue(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    /**
     * Eine tote Zustellung — die Projektion des Vertrags
     * {@code components.schemas.DeadDelivery}.
     *
     * @param deliveryRunId      die Zustelllauf-ID
     * @param organisationId     der besitzende Mandant
     * @param eventId            die Ereignis-ID
     * @param endpointId         der Endpunkt
     * @param endpointUrl        die Ziel-URL des Endpunkts (nullable)
     * @param eventType          der Ereignistyp
     * @param status             immer {@code abandoned}
     * @param attemptCount       die Zahl der Versuche
     * @param lastResponseStatus der Antwortcode des letzten Versuchs (nullable)
     * @param lastAttemptAt      der Zeitpunkt des letzten Versuchs (nullable)
     * @param abandonedAt        der Zeitpunkt der Aufgabe — immer gefuellt
     * @param createdAt          der Anlegezeitpunkt
     */
    public record DeadDelivery(UUID deliveryRunId,
                               String organisationId,
                               UUID eventId,
                               UUID endpointId,
                               String endpointUrl,
                               String eventType,
                               String status,
                               int attemptCount,
                               Integer lastResponseStatus,
                               Instant lastAttemptAt,
                               Instant abandonedAt,
                               Instant createdAt) {
    }

    /**
     * Die Kennzahlen eines Endpunkts im Auswertungsfenster.
     *
     * @param attemptsTotal       die Zahl aller Versuche
     * @param attemptsSuccessful  die Zahl der erfolgreichen Versuche
     * @param latencyP95Ms        die p95-Zustelllatenz in Millisekunden (nullable)
     */
    public record EndpointMetricsRow(long attemptsTotal,
                                     long attemptsSuccessful,
                                     Long latencyP95Ms) {
    }

    /**
     * Der konfigurierte Grenzwert eines Endpunkts (ADR-010 §1).
     *
     * @param limitValue    der Override des Grenzwerts ({@code null} = kein Override,
     *                      {@code 0} = abgeschaltet)
     * @param windowSeconds die Laenge des Zaehlfensters
     * @param strategy      die Strategie bei Ueberschreitung
     */
    public record RateLimitSetting(Integer limitValue,
                                    int windowSeconds,
                                    RateLimitStrategy strategy) {
    }

    /**
     * Die Zaehlung der Zustellversuche eines Endpunkts.
     *
     * @param observedAttempts die Zahl der beobachteten Versuche
     * @param delayedAttempts  die Zahl der durch das Limit verschobenen Versuche
     */
    public record RateLimitCounters(long observedAttempts,
                                    long delayedAttempts) {
    }

    /**
     * Das aktuelle Zaehlfenster eines Endpunkts.
     *
     * @param windowStart  der Beginn des Fensters
     * @param attempts     die Zahl der Versuche im Fenster
     * @param delayed      die Zahl der verschobenen Versuche im Fenster
     */
    public record RateLimitWindow(Instant windowStart,
                                   long attempts,
                                   long delayed) {
    }
}
