package com.echobox.protocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.ClockPort;
import com.echobox.common.ForbiddenException;
import com.echobox.config.EchoBoxProperties;
import com.echobox.protocol.api.RetentionEnforcementResponse;
import com.echobox.protocol.api.TenantRetentionResponse;
import com.echobox.tenant.TenantContext;

/**
 * Die Durchsetzung der Aufbewahrung (FR-031, AC-016-5, US-016).
 *
 * <p><strong>Geltungsbereich: alle Mandanten.</strong> Der Vorgang ist
 * mandantenuebergreifend; je Mandant gilt die fuer ihn konfigurierte Frist.
 * Die mandantenuebergreifende Sicht und die Durchsetzung werden ueber die
 * schmale Eigentuemerfunktion des Schemas hergestellt
 * ({@code app.enforce_retention_cross_tenant}, ADR-002 §7) — die einzige
 * <strong>schreibende</strong> mandantenuebergreifende Funktion: sie schreibt
 * ausschliesslich {@code deleted_at} und loescht im Purge.
 *
 * <p><strong>Mechanik</strong> (ADR-012):
 * <ul>
 *   <li><strong>Anker je Datensatz:</strong> der letzte Zustellversuch
 *       ({@code last_attempt_at}); fuer Ereignisse ohne Versuch der
 *       Annahmezeitpunkt ({@code accepted_at}).</li>
 *   <li><strong>Zwei Stufen:</strong> Markieren ({@code deleted_at} gesetzt —
 *       sofort unsichtbar) und Purge (physische Loeschung nach der
 *       Karenzzeit).</li>
 *   <li><strong>Kaskade:</strong> das Loeschen eines Ereignisses nimmt seine
 *       Zustelllaeufe und Versuche mit.</li>
 *   <li><strong>Zwei Fristen, getrennt:</strong> je Datenart eine Frist, der
 *       Vorgabewert ist 30 Tage (F-02); je Mandant ueberschreibbar.</li>
 * </ul>
 *
 * <p><strong>Die Quelle der Frist</strong> (je Datenart) wird mit einem
 * <strong>zweiten, reinen Leseaufruf</strong> der Eigentuemerfunktion
 * bestimmt: mit {@code null} als Vorgabe liefert die Funktion je Mandant den
 * <strong>eigenen Override</strong> ({@code null}, wenn keiner gesetzt ist).
 * Damit laesst sich „Mandant hat selbst gesetzt“ ({@code tenant}) von „es gilt
 * die Vorgabe“ ({@code default}) unterscheiden.
 *
 * <p><strong>Nur {@code service_admin}</strong> darf die Aufbewahrung
 * erzwingen; ein {@code receiver_admin} erhaelt HTTP 403 (AC-016-5).
 */
@Service
public class RetentionService {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);

    /** Die Rolle, die die Aufbewahrung erzwingen darf. */
    public static final String ROLE_SERVICE_ADMIN = "service_admin";

    /** Der Geltungsbereich der Durchsetzung — alle Mandanten. */
    private static final String SCOPE_ALL = "all";

    /** Der Anker je Datensatz — der letzte Zustellversuch. */
    private static final String ANCHOR_LAST_ATTEMPT_AT = "last_attempt_at";

    /** Die Frist stammt aus dem Override des Mandanten. */
    private static final String SOURCE_TENANT = "tenant";

    /** Die Frist stammt aus der Vorgabe der Betriebskonfiguration. */
    private static final String SOURCE_DEFAULT = "default";

    private final RetentionRepository repository;

    private final ClockPort clock;

    private final TenantContext tenantContext;

    private final int eventDays;

    private final int deliveryDays;

    /**
     * Die Karenzzeit zwischen Markieren und Purge in Tagen — {@code null}, wenn
     * die Betriebskonfiguration <strong>keinen</strong> Wert setzt.
     *
     * <p><strong>Es gibt keinen Vorgabewert</strong> fuer die Karenzzeit (R-01 /
     * E-13, Gate 2). Ohne gesetzten Wert rechnet das Schema mit <strong>0
     * Tagen</strong> — der Purge erfolgt dann im naechsten Durchlauf. Das ist
     * das dokumentierte Verhalten <strong>ohne</strong> Wert, <strong>kein</strong>
     * beschlossener Vorgabewert.
     */
    private final Integer purgeDelayDays;

    /**
     * Erzeugt die Durchsetzung.
     *
     * @param repository    der JDBC-Adapter der Aufbewahrung
     * @param clock         die Uhr des Dienstes
     * @param tenantContext der Mandant und die Rollen aus der Identitaet
     * @param properties    die Betriebskonfiguration (Fristen, Karenzzeit)
     */
    public RetentionService(RetentionRepository repository,
                            ClockPort clock,
                            TenantContext tenantContext,
                            EchoBoxProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.tenantContext = tenantContext;
        this.eventDays = properties.getRetention().getEventDays();
        this.deliveryDays = properties.getRetention().getDeliveryDays();
        this.purgeDelayDays = properties.getRetention().getPurgeDelay();
    }

    /**
     * Setzt die Aufbewahrung <strong>aller Mandanten</strong> durch.
     *
     * @param dryRun ob nur gezaehlt wird ({@code true}) oder tatsaechlich
     *               geloescht wird ({@code false})
     * @return das Ergebnis der Durchsetzung
     * @throws ForbiddenException wenn die Rolle die Aufbewahrung nicht erzwingen darf
     */
    public RetentionEnforcementResponse enforce(boolean dryRun) {
        requireServiceAdmin();

        List<RetentionRepository.TenantRetentionRow> rows =
                repository.enforce(dryRun, eventDays, deliveryDays, purgeDelayDays);
        Map<String, RetentionRepository.TenantRetentionRow> overrides = ownOverrides();

        List<TenantRetentionResponse> tenants = new ArrayList<>();
        long markedEvents = 0L;
        long markedDeliveryRuns = 0L;
        long markedAttempts = 0L;
        long purgedEvents = 0L;
        long purgedDeliveryRuns = 0L;
        long purgedAttempts = 0L;
        RetentionRepository.TenantRetentionRow shortestEvent = null;
        RetentionRepository.TenantRetentionRow shortestDelivery = null;

        for (RetentionRepository.TenantRetentionRow row : rows) {
            RetentionRepository.TenantRetentionRow override = overrides.get(row.organisationId());
            tenants.add(new TenantRetentionResponse(
                    row.organisationId(),
                    row.eventRetentionDays(),
                    source(row.eventRetentionDays(),
                            override == null ? null : override.eventRetentionDays()),
                    row.deliveryRetentionDays(),
                    source(row.deliveryRetentionDays(),
                            override == null ? null : override.deliveryRetentionDays()),
                    row.eventCutoff(),
                    row.deliveryCutoff(),
                    row.markedEvents(),
                    row.markedDeliveryRuns(),
                    row.markedAttempts(),
                    row.purgedEvents(),
                    row.purgedDeliveryRuns(),
                    row.purgedAttempts()));

            markedEvents += row.markedEvents();
            markedDeliveryRuns += row.markedDeliveryRuns();
            markedAttempts += row.markedAttempts();
            purgedEvents += row.purgedEvents();
            purgedDeliveryRuns += row.purgedDeliveryRuns();
            purgedAttempts += row.purgedAttempts();

            shortestEvent = shorter(shortestEvent, row, true);
            shortestDelivery = shorter(shortestDelivery, row, false);
        }

        return new RetentionEnforcementResponse(
                dryRun,
                SCOPE_ALL,
                ANCHOR_LAST_ATTEMPT_AT,
                purgeDelayDays,
                shortestEvent == null ? null : shortestEvent.eventRetentionDays(),
                shortestDelivery == null ? null : shortestDelivery.deliveryRetentionDays(),
                shortestEvent == null ? null : shortestEvent.eventCutoff(),
                shortestDelivery == null ? null : shortestDelivery.deliveryCutoff(),
                markedEvents,
                markedDeliveryRuns,
                markedAttempts,
                purgedEvents,
                purgedDeliveryRuns,
                purgedAttempts,
                tenants,
                clock.now());
    }

    /**
     * Die eigenen Overrides der Mandanten — ein <strong>reiner Leseaufruf</strong>.
     *
     * <p>Mit {@code null} als Vorgabe liefert die Eigentuemerfunktion je Mandant
     * den eigenen Override ({@code null}, wenn keiner gesetzt ist): damit
     * laesst sich die Quelle der angewandten Frist bestimmen. Der Aufruf
     * aendert nichts — {@code dry_run} ist {@code true}, und ohne Vorgabefrist
     * wird ohnehin nichts markiert.
     *
     * @return je Mandant der eigene Override
     */
    private Map<String, RetentionRepository.TenantRetentionRow> ownOverrides() {
        List<RetentionRepository.TenantRetentionRow> rows =
                repository.enforce(true, null, null, purgeDelayDays);
        Map<String, RetentionRepository.TenantRetentionRow> overrides = new HashMap<>();
        for (RetentionRepository.TenantRetentionRow row : rows) {
            overrides.put(row.organisationId(), row);
        }
        return overrides;
    }

    /**
     * Die Quelle der angewandten Frist.
     *
     * @param applied die angewandte Frist (nullable = keine Frist wirksam)
     * @param own     der eigene Override des Mandanten (nullable = keiner)
     * @return {@code tenant}, {@code default} oder {@code null}
     */
    private static String source(Integer applied, Integer own) {
        if (applied == null) {
            return null;
        }
        return own == null ? SOURCE_DEFAULT : SOURCE_TENANT;
    }

    /**
     * Der Mandant mit der <strong>kuerzesten</strong> angewandten Frist.
     *
     * @param current  der bisher kuerzeste Mandant (nullable)
     * @param candidate der Mandant, der geprueft wird
     * @param events   ob die Frist fuer Ereignisse verglichen wird
     * @return der kuerzeste Mandant (nullable, wenn keine Frist wirksam ist)
     */
    private static RetentionRepository.TenantRetentionRow shorter(
            RetentionRepository.TenantRetentionRow current,
            RetentionRepository.TenantRetentionRow candidate,
            boolean events) {
        Integer candidateDays = events ? candidate.eventRetentionDays() : candidate.deliveryRetentionDays();
        if (candidateDays == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        Integer currentDays = events ? current.eventRetentionDays() : current.deliveryRetentionDays();
        if (currentDays == null) {
            return candidate;
        }
        return candidateDays < currentDays ? candidate : current;
    }

    /**
     * Die Aufbewahrung erzwingen darf <strong>nur</strong> der Service-Admin —
     * der Empfaenger-Admin erhaelt HTTP 403 (AC-016-5).
     */
    private void requireServiceAdmin() {
        if (!tenantContext.hasRole(ROLE_SERVICE_ADMIN)) {
            LOG.info("Die Aufbewahrung wurde von einer Rolle ohne '{}-Recht angefordert.",
                    ROLE_SERVICE_ADMIN);
            throw new ForbiddenException();
        }
    }
}
