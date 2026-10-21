package com.echobox.common;

/**
 * Ein Mandantenparameter wurde im Body, im Query oder im Header mitgeschickt (HTTP 422, D-01).
 *
 * <p>HTTP 422 - Code {@code CODE}.
 */
public class TenantParameterException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Erzeugt den Fehler.
     */
    public TenantParameterException() {
        super(ProblemFactory.TENANT_PARAMETER_REJECTED, 422, "Tenant parameter rejected", "Der Mandant stammt aus der Identitaet; ein Mandantenparameter wird nicht akzeptiert.");
    }
}
