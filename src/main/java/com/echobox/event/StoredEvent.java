package com.echobox.event;

import java.util.UUID;

/**
 * Das bereits gespeicherte Ereignis, soweit es fuer den Idempotenzvergleich
 * gebraucht wird.
 *
 * <p>Der Idempotenzvergleich laeuft ueber den <strong>Fingerprint</strong> der
 * Nutzlast — ein gezielter HMAC-SHA256 ueber die kanonischen Nutzlastbytes
 * (ADR-007). Der Fingerprint enthaelt keinen Klartextanteil und taugt
 * ausschliesslich zum Vergleich.
 *
 * @param eventId        die Ereignis-ID
 * @param organisationId der besitzende Mandant
 * @param idempotencyKey der Idempotenzschluessel
 * @param fingerprint    der Fingerprint der Nutzlast
 */
public record StoredEvent(UUID eventId,
                          String organisationId,
                          String idempotencyKey,
                          byte[] fingerprint) {

    /**
     * Erzeugt einen Eintrag mit kopiertem Fingerprint.
     *
     * @param eventId        die Ereignis-ID
     * @param organisationId der besitzende Mandant
     * @param idempotencyKey der Idempotenzschluessel
     * @param fingerprint    der Fingerprint der Nutzlast
     */
    public StoredEvent {
        fingerprint = fingerprint == null ? new byte[0] : fingerprint.clone();
    }
}
