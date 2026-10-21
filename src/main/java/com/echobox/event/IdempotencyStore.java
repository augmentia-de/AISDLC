package com.echobox.event;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.echobox.tenant.TenantTransactionRunner;

/**
 * Der Abgleich der Idempotenzschluessel je (Mandant, Schluessel) (D-05).
 *
 * <p>Der Zugriff auf die Datenbank laeuft ueber den {@link EventRepository} —
 * und damit <strong>innerhalb</strong> der Transaktion, in der der Mandant
 * gesetzt wurde (ADR-002 §3). Der Abgleich selbst passiert <strong>vor</strong>
 * der Persistenz (ADR-007).
 */
@Component
public class IdempotencyStore {

    private final EventRepository repository;

    private final TenantTransactionRunner tenantRunner;

    /**
     * Erzeugt den Abgleich.
     *
     * @param repository   der JDBC-Adapter
     * @param tenantRunner der Mandantentransaktions-Runner
     */
    public IdempotencyStore(EventRepository repository, TenantTransactionRunner tenantRunner) {
        this.repository = repository;
        this.tenantRunner = tenantRunner;
    }

    /**
     * Entscheidet, ob die Einlieferung neu, eine Wiederholung oder ein Konflikt
     * ist.
     *
     * @param organisationId der Mandant
     * @param idempotencyKey der Idempotenzschluessel
     * @param fingerprint    der Fingerprint der eingelieferten Nutzlast
     * @return die Entscheidung
     */
    public IdempotencyDecision decide(String organisationId, String idempotencyKey, byte[] fingerprint) {
        return IdempotencyDecision.of(find(organisationId, idempotencyKey), fingerprint);
    }

    /**
     * Liest das gespeicherte Ereignis zum Idempotenzschluessel.
     *
     * @param organisationId der Mandant
     * @param idempotencyKey der Idempotenzschluessel
     * @return das gespeicherte Ereignis, wenn es existiert
     */
    public Optional<StoredEvent> find(String organisationId, String idempotencyKey) {
        return tenantRunner.execute(organisationId,
                () -> repository.findByIdempotencyKey(organisationId, idempotencyKey)
                        .map(event -> new StoredEvent(event.eventId(), event.organisationId(),
                                event.idempotencyKey(), event.fingerprint())));
    }
}
