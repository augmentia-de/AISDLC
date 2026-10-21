package com.echobox.delivery;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.echobox.common.AuditLogRepository;
import com.echobox.common.ClockPort;
import com.echobox.common.DeliveryStateConflictException;
import com.echobox.common.ForbiddenException;
import com.echobox.common.IdGenerator;
import com.echobox.common.NotFoundException;
import com.echobox.config.EchoBoxProperties;
import com.echobox.delivery.api.ReplayResponse;
import com.echobox.endpoint.Endpoint;
import com.echobox.endpoint.EndpointRepository;
import com.echobox.endpoint.EndpointStatus;
import com.echobox.tenant.TenantContext;
import com.echobox.tenant.TenantTransactionRunner;

/**
 * Die Fachlogik des Replays (FR-024, ADR-006 §6, F-03).
 *
 * <p><strong>Wer darf replayen?</strong> Ausschliesslich der
 * <strong>Empfaenger-Admin des betroffenen Endpunkts</strong> (D-15, F-03).
 * {@code developer} und {@code service_admin} erhalten HTTP 403.
 *
 * <p><strong>Die Reihenfolge der Pruefungen</strong> (D-02, ADR-015 §3,
 * V-39): <strong>erst der Mandant</strong> — ein Zustelllauf eines fremden
 * Mandanten ergibt HTTP 404, auch fuer {@code developer} und
 * {@code service_admin}; <strong>dann die Rolle</strong> — im eigenen Mandanten
 * ergibt die falsche Rolle HTTP 403.
 *
 * <p><strong>Einmal:</strong> ein aufgegebener Zustelllauf kann
 * <strong>einmal</strong> replayed werden. Die Datenbank erzwingt das
 * ({@code UNIQUE (replay_of)}); der zweite Replay verletzt die Constraint und
 * ergibt HTTP 409 {@code delivery_state_conflict} — es entsteht <strong>kein</strong>
 * weiterer Zustelllauf (AC-013-1, AC-013-5, I-9).
 *
 * <p><strong>Der Vorgängerlauf bleibt unveraendert</strong> (AC-013-2): der
 * Replay ist ein <strong>neuer</strong> Zustelllauf mit neuer ID und
 * zurueckgesetzter Versuchsanzahl.
 *
 * <p><strong>Die Zeitlimits</strong> (ADR-006 §6, N-20): die Replay-Transaktion
 * setzt vor dem {@code INSERT} eine Sperrenwartezeit und ein
 * Statement-Timeout, die <strong>kuerzer</strong> sind als das HTTP-Timeout —
 * der verlierende Replay antwortet damit mit 409 statt mit einem Timeout.
 */
@Service
public class DeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryService.class);

    /** Die Rolle, die einen Zustelllauf erneut ausloesen darf. */
    public static final String ROLE_RECEIVER_ADMIN = "receiver_admin";

    /** Die Aktion im Audit-Protokoll. */
    public static final String AUDIT_ACTION = "delivery_replayed";

    private final DeliveryRepository repository;

    private final EndpointRepository endpointRepository;

    private final AuditLogRepository auditLogRepository;

    private final TenantContext tenantContext;

    private final TenantTransactionRunner tenantRunner;

    private final JdbcTemplate jdbcTemplate;

    private final IdGenerator idGenerator;

    private final ClockPort clock;

    private final int maxAttempts;

    private final EchoBoxProperties.Delivery.Replay replayLimits;

    /**
     * Erzeugt die Fachlogik.
     *
     * @param repository         der JDBC-Adapter des Zustellmoduls
     * @param endpointRepository der JDBC-Adapter des Endpunktmoduls
     * @param auditLogRepository der JDBC-Adapter des Audit-Protokolls
     * @param tenantContext      der Mandant und die Rollen aus der Identitaet
     * @param tenantRunner       der Mandantentransaktions-Runner
     * @param jdbcTemplate       die JDBC-Vorlage (fuer die Zeitlimits)
     * @param idGenerator        die Erzeugung der Kennungen (UUID-v7)
     * @param clock              die Uhr des Dienstes
     * @param properties         die Betriebskonfiguration
     */
    public DeliveryService(DeliveryRepository repository,
                           EndpointRepository endpointRepository,
                           AuditLogRepository auditLogRepository,
                           TenantContext tenantContext,
                           TenantTransactionRunner tenantRunner,
                           JdbcTemplate jdbcTemplate,
                           IdGenerator idGenerator,
                           ClockPort clock,
                           EchoBoxProperties properties) {
        this.repository = repository;
        this.endpointRepository = endpointRepository;
        this.auditLogRepository = auditLogRepository;
        this.tenantContext = tenantContext;
        this.tenantRunner = tenantRunner;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.maxAttempts = properties.getDelivery().getMaxAttempts();
        this.replayLimits = properties.getDelivery().getReplay();
    }

    /**
     * Loest eine aufgegebene Zustellung erneut aus.
     *
     * @param deliveryRunId die Zustelllauf-ID der aufgegebenen Zustellung
     * @param reason        die Begruendung fuer das Audit-Protokoll (nullable)
     * @return die Antwort des Vertrags
     */
    public ReplayResponse replay(UUID deliveryRunId, String reason) {
        String organisationId = tenantContext.organisationId();
        return tenantRunner.execute(organisationId, () -> {
            DeliveryRun previous = repository.findById(organisationId, deliveryRunId)
                    .orElseThrow(() -> {
                        LOG.info("Der Zustelllauf {} existiert im Mandanten nicht.", deliveryRunId);
                        return new NotFoundException();
                    });
            requireReceiverAdmin();
            requireReplayable(previous, organisationId);

            Endpoint endpoint = endpointRepository.findById(organisationId, previous.endpointId())
                    .orElseThrow(() -> new NotFoundException());
            if (endpoint.status() == EndpointStatus.PAUSED) {
                // Ein pausierter Endpunkt startet keinen Versuch (D-03, AC-013-4).
                LOG.info("Der Replay des Zustelllaufs {} wurde abgelehnt — der Endpunkt ist pausiert.",
                        deliveryRunId);
                throw new DeliveryStateConflictException();
            }

            applyTimeouts();

            Instant now = clock.now();
            UUID newRunId = idGenerator.newId();
            try {
                repository.insertRun(organisationId, newRunId, previous.eventId(), previous.endpointId(),
                        previous.eventType(), DeliveryStatus.PENDING, maxAttempts, now, deliveryRunId, now);
            } catch (DuplicateKeyException exception) {
                // UNIQUE (replay_of) — der Lauf wurde bereits replayed (I-9).
                LOG.info("Der Zustelllauf {} wurde bereits einmal replayed.", deliveryRunId);
                throw new DeliveryStateConflictException();
            }

            auditLogRepository.insert(organisationId, tenantContext.subject(), ROLE_RECEIVER_ADMIN,
                    AUDIT_ACTION, "delivery_run", newRunId,
                    AuditLogRepository.details(
                            "previous_delivery_run_id", deliveryRunId.toString(),
                            "event_id", previous.eventId().toString(),
                            "reason", reason == null ? "" : reason),
                    now);

            LOG.info("Der Zustelllauf {} wurde replayed — der neue Lauf ist {}.", deliveryRunId, newRunId);
            return new ReplayResponse(deliveryRunId.toString(), newRunId.toString(),
                    previous.eventId().toString(), DeliveryStatus.PENDING.wireValue(), 0, now, now);
        });
    }

    /**
     * Der Vorgängerlauf muss aufgegeben und noch nicht replayed sein.
     *
     * @param previous       der Vorgängerlauf
     * @param organisationId der Mandant
     */
    private void requireReplayable(DeliveryRun previous, String organisationId) {
        if (previous.status() != DeliveryStatus.ABANDONED) {
            LOG.info("Der Zustelllauf {} ist nicht aufgegeben — ein Replay ist nicht moeglich.", previous.deliveryRunId());
            throw new DeliveryStateConflictException();
        }
        if (repository.replayExists(organisationId, previous.deliveryRunId())) {
            LOG.info("Der Zustelllauf {} wurde bereits einmal replayed.", previous.deliveryRunId());
            throw new DeliveryStateConflictException();
        }
    }

    /**
     * Setzt die Zeitlimits der Replay-Transaktion (ADR-006 §6, N-20).
     *
     * <p>Beide Grenzen sind <strong>kuerzer</strong> als das HTTP-Timeout: der
     * verlierende Replay wartet hoechstens so lange und antwortet dann mit
     * HTTP 409 statt mit einem Timeout.
     */
    private void applyTimeouts() {
        setLocalConfig("lock_timeout", replayLimits.getLockTimeout().toMillis() + "ms");
        setLocalConfig("statement_timeout", replayLimits.getStatementTimeout().toMillis() + "ms");
    }

    private void setLocalConfig(String name, String value) {
        jdbcTemplate.query("SELECT set_config(?, ?, true)", statement -> {
            statement.setString(1, name);
            statement.setString(2, value);
        }, resultSet -> {
            resultSet.next();
            return null;
        });
    }

    /**
     * Nur der Empfaenger-Admin darf einen Zustelllauf erneut ausloesen
     * (D-15, F-03).
     */
    private void requireReceiverAdmin() {
        if (!tenantContext.hasRole(ROLE_RECEIVER_ADMIN)) {
            throw new ForbiddenException();
        }
    }
}
