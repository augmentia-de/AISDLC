package com.echobox.delivery;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.echobox.common.ClockPort;
import com.echobox.config.EchoBoxProperties;

/**
 * Der Hintergrundprozess der Zustellung.
 *
 * <p>Der Worker ist <strong>immer</strong> als Bean vorhanden — auch wenn der
 * Zeitplan deaktiviert ist. Der Zeitplan stoesst den Worker
 * <strong>nur</strong> an, wenn {@code echobox.delivery.worker.enabled} gesetzt
 * ist; die Tests treiben die Zustellung selbst, damit die Reihenfolge der
 * Versuche eindeutig ist.
 *
 * <p><strong>Zeitplan:</strong> der Takt kommt aus der Betriebskonfiguration
 * ({@code echobox.delivery.worker.poll-interval}). Er wird ueber einen eigenen
 * {@link ScheduledExecutorService} getrieben und <strong>nicht</strong> ueber
 * {@code @Scheduled}: die Annotationsform von Spring kann nur eine Zahl in
 * Millisekunden oder eine ISO-8601-Dauer lesen, der Konfigurationswert
 * {@code 1s} ist jedoch keine ISO-8601-Dauer ({@code PT1S}) — ein
 * {@code @Scheduled} wuerde den Dienst deshalb beim Start scheitern lassen.
 *
 * <p><strong>Der Zyklus arbeitet mandantenweise</strong> (ADR-006 §3):
 * <pre>
 * Scheduler -&gt; app.tenant_ids_for_worker() -&gt; je Mandant:
 *   beanspruchen (FOR UPDATE SKIP LOCKED + Lease) -&gt; versenden -&gt; protokollieren
 * </pre>
 * Ohne die Mandantenermittlung und ohne {@code app.set_tenant} waere die
 * Beanspruchung mandantenlos — und lieferte unter RLS 0 Zeilen: es wuerde
 * <strong>nichts</strong> zugestellt, ohne Fehler und ohne Logeintrag
 * (AC-023-3, AC-023-4).
 *
 * <p><strong>Die Beanspruchung und der Versuch sind zwei Schritte</strong>:
 * die Beanspruchung (mit Lease) endet mit ihrem Commit, der HTTP-Versuch
 * laeuft ausserhalb jeder Transaktion. Stirbt die Instanz mitten im Versuch,
 * hinterlaesst sie eine abgelaufene Lease — ein anderer Worker nimmt den Lauf
 * danach wieder auf, <strong>ohne</strong> dass die Versuchsanzahl sprunghaft
 * steigt (AC-023-4, I-6).
 */
@Component
public class DeliveryWorker {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorker.class);

    private final boolean enabled;

    private final Duration pollInterval;

    private final String instanceId;

    private final ScheduledExecutorService scheduler;

    private final DeliveryRepository repository;

    private final DeliveryDispatcher dispatcher;

    private final ClockPort clock;

    private final int leaseSeconds;

    private final int batchSize;

    /**
     * Erzeugt den Worker.
     *
     * @param properties die Betriebskonfiguration (Zeitplan, Mandanteninstanz)
     * @param repository  der JDBC-Adapter des Zustellmoduls
     * @param dispatcher  der Faecher des Zustellzyklus
     * @param clock       die Uhr des Dienstes
     */
    public DeliveryWorker(EchoBoxProperties properties,
                          DeliveryRepository repository,
                          DeliveryDispatcher dispatcher,
                          ClockPort clock) {
        this.enabled = properties.getDelivery().getWorker().isEnabled();
        this.pollInterval = properties.getDelivery().getWorker().getPollInterval();
        this.instanceId = buildInstanceId(properties.getTenant().getId());
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.leaseSeconds = (int) Math.max(1L, properties.getDelivery().getWorker().getLeaseDuration().toSeconds());
        this.batchSize = Math.max(1, properties.getDelivery().getWorker().getBatchSize());
        this.scheduler = enabled
                ? Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "echobox-delivery-worker");
                    thread.setDaemon(true);
                    return thread;
                })
                : null;
    }

    /**
     * Liefert die Instanz-ID dieser Worker-Instanz — sie wandert in die Spalte
     * {@code lease_owner} der Zustelllaeufe (AC-023-3).
     *
     * @return die Instanz-ID
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * Startet den Zeitplan — nur wenn er aktiviert ist.
     */
    @PostConstruct
    void startSchedule() {
        if (!enabled) {
            LOG.info("Der Zustell-Zeitplan ist deaktiviert (echobox.delivery.worker.enabled=false).");
            return;
        }
        long periodMillis = Math.max(1L, pollInterval.toMillis());
        scheduler.scheduleWithFixedDelay(this::runScheduled, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        LOG.info("Der Zustell-Zeitplan laeuft mit einem Takt von {} ms.", periodMillis);
    }

    /**
     * Beendet den Zeitplan.
     */
    @PreDestroy
    void stopSchedule() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Fuehrt einen Durchlauf des Workers mit der Instanz-ID dieser Bean aus.
     */
    public void runOnce() {
        runOnce(instanceId);
    }

    /**
     * Fuehrt einen Durchlauf des Workers aus.
     *
     * <p>Je Mandant werden die faelligen Laeufe beansprucht und versendet. Ein
     * Fehler in einem Mandanten darf die anderen Mandanten nicht stoppen — der
     * naechste Durchlauf versucht es erneut.
     *
     * @param instanceId die ID der beanspruchenden Instanz
     */
    public void runOnce(String instanceId) {
        List<String> tenants = repository.tenantIdsForWorker();
        for (String organisationId : tenants) {
            try {
                List<DeliveryRun> claimed = repository.claimDueRuns(organisationId, instanceId, clock.now(),
                        leaseSeconds, batchSize);
                for (DeliveryRun run : claimed) {
                    dispatcher.dispatch(run, instanceId);
                }
            } catch (RuntimeException exception) {
                LOG.warn("Der Zustell-Durchlauf fuer den Mandanten {} ist fehlgeschlagen: {}",
                        organisationId, exception.getMessage());
            }
        }
    }

    private void runScheduled() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            // Der Zeitplan darf wegen eines einzelnen Fehlschlags nicht stehenbleiben.
            LOG.warn("Der Zustell-Durchlauf ist fehlgeschlagen: {}", e.getMessage());
        }
    }

    private static String buildInstanceId(String tenantId) {
        String suffix = UUID.randomUUID().toString();
        return tenantId == null || tenantId.isBlank() ? suffix : tenantId + "/" + suffix;
    }
}
