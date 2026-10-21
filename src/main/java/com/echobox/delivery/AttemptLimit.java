package com.echobox.delivery;

/**
 * Die Versuchsgrenze eines Zustelllaufs (FR-023, F-04).
 *
 * <p>Nach dem letzten erlaubten Versuch endet der Lauf als
 * {@code abandoned} — eine **tote Zustellung** (AC-012-1). Der Zustand traegt
 * dann keinen naechsten Versuchszeitpunkt mehr
 * (DB-Constraint {@code delivery_runs_terminal_no_next_attempt_ck}).
 *
 * <p>Vorgabe F-04: 8 Versuche. Der Wert ist **konfigurierbar** und wird beim
 * Anlegen des Laufs festgeschrieben.
 */
public class AttemptLimit {

    private final int maxAttempts;

    /**
     * Erzeugt die Grenze.
     *
     * @param maxAttempts die maximale Zahl der Versuche (mindestens 1)
     */
    public AttemptLimit(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Die Versuchsgrenze muss mindestens 1 betragen.");
        }
        this.maxAttempts = maxAttempts;
    }

    /**
     * Liefert die maximale Zahl der Versuche.
     *
     * @return die Versuchsgrenze
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Prueft, ob die Versuchsgrenze erreicht ist.
     *
     * @param attemptCount die Zahl der bereits ausgefuehrten Versuche
     * @return {@code true}, wenn kein weiterer Versuch erfolgt
     */
    public boolean isExhausted(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    /**
     * Der Zustand, in den der Lauf bei erreichter Grenze wechselt.
     *
     * @return {@code ABANDONED}
     */
    public DeliveryStatus statusWhenExhausted() {
        return DeliveryStatus.ABANDONED;
    }
}
