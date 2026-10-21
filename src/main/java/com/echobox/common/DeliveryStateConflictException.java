package com.echobox.common;

/**
 * Der Zustelllauf ist nicht replaybar (HTTP 409).
 *
 * <p>Der Fehler entsteht, wenn ein Zustelllauf nicht aufgegeben ist, der
 * Endpunkt pausiert wurde oder der Lauf bereits replayed wurde (I-9,
 * ADR-006 §6, F-03).
 *
 * <p>HTTP 409 - Code {@code delivery_state_conflict}.
 */
public class DeliveryStateConflictException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public DeliveryStateConflictException() {
        super(ProblemFactory.DELIVERY_STATE_CONFLICT, 409, "Delivery state conflict",
                "Der Zustelllauf ist nicht replaybar: er ist nicht aufgegeben, der Endpunkt ist pausiert "
                        + "oder der Zustelllauf wurde bereits einmal replayed.");
    }
}
