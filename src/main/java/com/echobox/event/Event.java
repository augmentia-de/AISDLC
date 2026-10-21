package com.echobox.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Das Fachmodell eines Ereignisses — <strong>ohne</strong> die Nutzlast.
 *
 * <p>Die Nutzlast liegt im Ruhezustand verschluesselt in der Datenbank
 * (FR-012) und wird <strong>nie</strong> ausgegeben (D-11). Das Modell traegt
 * deshalb nur Metadaten und den Fingerprint, ueber den die Idempotenz
 * entschieden wird (ADR-007).
 *
 * @param eventId                die Ereignis-ID
 * @param organisationId         der besitzende Mandant
 * @param eventType              der Ereignistyp
 * @param idempotencyKey         der Idempotenzschluessel
 * @param fingerprint            der Fingerprint der Nutzlast
 * @param payloadEncoding        die Kodierung der Nutzlast
 * @param payloadMediaType       der Medientyp der Nutzlast
 * @param payloadSizeBytes       die Groesse der Nutzlastbytes
 * @param status                 der Zustand ({@code accepted} oder {@code no_subscriber})
 * @param subscribedEndpointCount die Zahl der Abonnenten des Ereignistyps
 * @param acceptedAt             der Annahmezeitpunkt
 * @param createdAt              der Anlegezeitpunkt
 * @param updatedAt              der Zeitpunkt der letzten Aenderung
 */
public record Event(UUID eventId,
                    String organisationId,
                    String eventType,
                    String idempotencyKey,
                    byte[] fingerprint,
                    PayloadEncoding payloadEncoding,
                    String payloadMediaType,
                    long payloadSizeBytes,
                    String status,
                    int subscribedEndpointCount,
                    Instant acceptedAt,
                    Instant createdAt,
                    Instant updatedAt) {

    /** Der Zustand eines Ereignisses mit mindestens einem Abnehmer. */
    public static final String STATUS_ACCEPTED = "accepted";

    /** Der Zustand eines Ereignisses <strong>ohne</strong> Abnehmer (D-04). */
    public static final String STATUS_NO_SUBSCRIBER = "no_subscriber";

    /**
     * Erzeugt ein Ereignis mit kopiertem Fingerprint.
     *
     * @param eventId                die Ereignis-ID
     * @param organisationId         der besitzende Mandant
     * @param eventType              der Ereignistyp
     * @param idempotencyKey         der Idempotenzschluessel
     * @param fingerprint            der Fingerprint der Nutzlast
     * @param payloadEncoding        die Kodierung der Nutzlast
     * @param payloadMediaType       der Medientyp der Nutzlast
     * @param payloadSizeBytes       die Groesse der Nutzlastbytes
     * @param status                 der Zustand
     * @param subscribedEndpointCount die Zahl der Abonnenten
     * @param acceptedAt             der Annahmezeitpunkt
     * @param createdAt              der Anlegezeitpunkt
     * @param updatedAt              der Zeitpunkt der letzten Aenderung
     */
    public Event {
        fingerprint = fingerprint == null ? new byte[0] : fingerprint.clone();
    }

    /**
     * Der Eintrag, den der Idempotenzvergleich braucht.
     *
     * @return der Eintrag
     */
    public StoredEvent toStoredEvent() {
        return new StoredEvent(eventId, organisationId, idempotencyKey, fingerprint);
    }
}
