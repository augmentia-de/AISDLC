package com.echobox.tenant;

import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Der mandantenuebergreifende Leseweg des Service-Admins (ADR-002 §7).
 *
 * <p>Der Plattform-Betrieb sieht die Endpunkte **aller** Mandanten. Diese Sicht
 * wird ueber die **drei** schmalen Eigentuemerfunktionen des Schemas hergestellt:
 * {@code app.list_endpoints_cross_tenant},
 * {@code app.list_dead_deliveries_cross_tenant} und
 * {@code app.enforce_retention_cross_tenant}. Es gibt **keinen** vierten
 * mandantenuebergreifenden Leseweg und **kein** {@code BYPASSRLS}.
 *
 * <p>Die Funktionen sind **fail-closed**: ohne die Freigabe
 * {@code app.cross_tenant = 'service_admin'} liefern sie **keine** Zeile. Diese
 * Freigabe wird hier **transaktionslokal** gesetzt — und **erst**, nachdem die
 * JWT-Rolle geprueft wurde. Der Aufrufer dieses Scopes muss also **zuvor** die
 * Rolle {@code service_admin} geprueft haben.
 */
@Component
public class CrossTenantScope {

    /** Die transaktionslokale Freigabe. */
    public static final String CROSS_TENANT_SQL =
            "SELECT set_config('app.cross_tenant', 'service_admin', true)";

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    /**
     * Erzeugt den Scope.
     *
     * @param jdbcTemplate      der JDBC-Adapter
     * @param transactionManager der Transaktionsverwalter
     */
    public CrossTenantScope(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Fuehrt die Aktion mit der mandantenuebergreifenden Freigabe aus.
     *
     * @param action die Aktion
     * @param <T>    der Ergebnistyp
     * @return das Ergebnis der Aktion
     */
    public <T> T asServiceAdmin(Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            jdbcTemplate.query(CROSS_TENANT_SQL, resultSet -> {
                resultSet.next();
                return null;
            });
            return action.get();
        });
    }
}
