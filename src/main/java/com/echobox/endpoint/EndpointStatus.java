package com.echobox.endpoint;

import java.util.Locale;

/**
 * Der Zustand eines Endpunkts.
 *
 * <p>Der Vertrag kennt genau zwei Zustaende: {@code active} und
 * {@code paused}. Ein pausierter Endpunkt erhaelt <strong>keine</strong>
 * Zustellung — fuer Ereignisse, die waehrend der Pause eintreffen, entsteht
 * kein Zustellvorgang und keine Warteschlangeneintragung (D-03).
 */
public enum EndpointStatus {

    /** Der Endpunkt ist aktiv — er erhaelt Zustellungen. */
    ACTIVE,

    /** Der Endpunkt ist pausiert — er erhaelt keine Zustellung. */
    PAUSED;

    /**
     * Der Wert im Vertrag — klein geschrieben, wie im Schema.
     *
     * @return der Drahtwert
     */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Liest den Zustand aus dem Datenbankwert.
     *
     * @param value der Datenbankwert
     * @return der Zustand
     */
    public static EndpointStatus fromWire(String value) {
        for (EndpointStatus status : values()) {
            if (status.wireValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unbekannter Endpunktstatus: " + value);
    }
}
