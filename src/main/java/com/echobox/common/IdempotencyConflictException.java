package com.echobox.common;

/**
 * Gleicher Idempotenzschluessel mit abweichender Nutzlast (HTTP 409, D-05).
 *
 * <p>HTTP 409 - Code {@code CODE}.
 */
public class IdempotencyConflictException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public IdempotencyConflictException() {
        super(ProblemFactory.IDEMPOTENCY_CONFLICT, 409, "Idempotency conflict", "Der Idempotenzschluessel wurde bereits mit einer abweichenden Nutzlast verwendet.");
    }
}
