package com.echobox.endpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Das Fachmodell eines Endpunkts.
 *
 * <p>Der Endpunkt traegt die Ziel-URL, den Zustand, die abonnierten
 * Ereignistypen und den Stand der Secret-Rotation. Das Secret selbst ist
 * <strong>nicht</strong> Teil des Modells: es liegt verschluesselt in der
 * Datenbank und wird <strong>nie</strong> im Klartext ausgegeben (AC-001-4).
 *
 * @param endpointId      die Endpunkt-ID
 * @param organisationId  der besitzende Mandant
 * @param targetUrl       die Ziel-URL
 * @param status          der Zustand (aktiv oder pausiert)
 * @param eventTypes      die abonnierten Ereignistypen
 * @param secretVersion   die laufende Secret-Generation (Startwert 1)
 * @param secretRotatedAt der Zeitpunkt der letzten Rotation (nullable)
 * @param pausedAt        der Zeitpunkt der Pause (nullable)
 * @param createdAt       der Anlegezeitpunkt
 * @param updatedAt       der Zeitpunkt der letzten Aenderung
 */
public record Endpoint(UUID endpointId,
                       String organisationId,
                       EndpointTargetUrl targetUrl,
                       EndpointStatus status,
                       List<String> eventTypes,
                       int secretVersion,
                       Instant secretRotatedAt,
                       Instant pausedAt,
                       Instant createdAt,
                       Instant updatedAt) {

    /**
     * Erzeugt einen Endpunkt mit unabhaengiger Ereignistyp-Liste.
     *
     * @param endpointId      die Endpunkt-ID
     * @param organisationId  der besitzende Mandant
     * @param targetUrl       die Ziel-URL
     * @param status          der Zustand
     * @param eventTypes      die abonnierten Ereignistypen
     * @param secretVersion   die laufende Secret-Generation
     * @param secretRotatedAt der Zeitpunkt der letzten Rotation
     * @param pausedAt        der Zeitpunkt der Pause
     * @param createdAt       der Anlegezeitpunkt
     * @param updatedAt       der Zeitpunkt der letzten Aenderung
     */
    public Endpoint {
        eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
    }
}
