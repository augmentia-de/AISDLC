package com.echobox.delivery;

import java.util.Locale;

/**
 * Der Zustand eines Zustelllaufs.
 *
 * <p>Die Werte entsprechen exakt den Zustaenden des Schemas
 * ({@code app.delivery_runs.status}) und den Werten des Vertrags
 * ({@code components.schemas.DeliveryStatus}).
 *
 * <p><strong>Endgueltige Zustaende</strong> sind {@code sent},
 * {@code abandoned}, {@code rejected}, {@code cancelled} und
 * {@code no_subscriber}: sie tragen <strong>keinen</strong> naechsten
 * Versuchszeitpunkt (DB-Constraint
 * {@code delivery_runs_terminal_no_next_attempt_ck}).
 */
public enum DeliveryStatus {

    /** Angelegt, noch kein Versuch ausgefuehrt. */
    PENDING,

    /** Ein Versuch laeuft gerade oder ist terminiert. */
    SCHEDULED,

    /** Der Versuch wurde durch das Endpunkt-Limit verschoben (FR-033). */
    DELAYED,

    /** Erfolgreich zugestellt (2xx). */
    SENT,

    /** Der letzte Versuch ist fehlgeschlagen, ein weiterer ist geplant. */
    FAILED,

    /** Die Versuchsgrenze ist erreicht — eine tote Zustellung (FR-023). */
    ABANDONED,

    /** Vom Empfaenger endgueltig abgelehnt (FR-025, D-08). */
    REJECTED,

    /** Durch die Pause des Endpunkts abgebrochen (D-03). */
    CANCELLED,

    /** Ereignis ohne Abnehmer (D-04) — es gab nie einen Versuch. */
    NO_SUBSCRIBER;

    /**
     * Liefert, ob der Zustand endgueltig ist.
     *
     * @return {@code true}, wenn der Zustand endgueltig ist
     */
    public boolean isTerminal() {
        return this == SENT || this == ABANDONED || this == REJECTED
                || this == CANCELLED || this == NO_SUBSCRIBER;
    }

    /**
     * Liefert den Wert, wie er in der Datenbank steht.
     *
     * @return der Wert in Kleinbuchstaben
     */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Liest einen Zustand aus seinem Datenbankwert.
     *
     * @param value der Wert
     * @return der Zustand
     * @throws IllegalArgumentException wenn der Wert unbekannt ist
     */
    public static DeliveryStatus fromWire(String value) {
        for (DeliveryStatus status : values()) {
            if (status.wireValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unbekannter Zustellstatus: " + value);
    }
}
