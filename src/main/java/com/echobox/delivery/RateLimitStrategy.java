package com.echobox.delivery;

/**
 * Die Strategie des Endpunkt-Limits (FR-033, D-14, ADR-010).
 *
 * <p>Vorgabe (F-01): {@code delay} — bei Ueberschreitung wird der Versuch
 * **verschoben**, nicht verworfen und nicht blockiert.
 */
public enum RateLimitStrategy {

    /** Der Versuch wird verschoben; der Lauf traegt den Zustand {@code delayed}. */
    DELAY,

    /** Der Versuch wird zurueckgestellt, bis das Zeitfenster wieder frei ist. */
    BLOCK;

    /**
     * Liefert den Wert, wie er in der Datenbank steht.
     *
     * @return der Wert in Kleinbuchstaben
     */
    public String wireValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Liest eine Strategie aus ihrem Datenbankwert.
     *
     * @param value der Wert
     * @return die Strategie
     * @throws IllegalArgumentException wenn der Wert unbekannt ist
     */
    public static RateLimitStrategy fromWire(String value) {
        for (RateLimitStrategy strategy : values()) {
            if (strategy.wireValue().equals(value)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unbekannte Rate-Limit-Strategie: " + value);
    }
}
