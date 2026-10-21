package com.echobox.tenant;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fuehrt fachliche Aktionen in der Transaktion aus, in der **zuvor** der
 * Mandant gesetzt wurde (ADR-002 §3).
 *
 * <p>Der Mandant wird ueber {@code SELECT app.set_tenant(?)} gesetzt. Die Funktion
 * nutzt {@code set_config(..., is_local = true)}: der Wert gilt **nur innerhalb
 * der Transaktion**, die ihn gesetzt hat, und wird beim COMMIT zurueckgesetzt.
 *
 * <p>Der Kontext ist damit **transaktionsgebunden**, nicht verbindungsgebunden:
 * ein Connection-Pool aendert daran nichts. Ein Aufruf im Servlet-Filter oder
 * beim Verbindungsaufbau waere wirkungslos — der Mandant waere beim ersten
 * Query zurueckgesetzt (Autocommit) bzw. auf einer anderen Connection gesetzt;
 * {@code current_setting('app.organisation_id', true)} lieferte dann {@code NULL},
 * die RLS-Policy griff **fail-closed** und **jede** Abfrage lieferte 0 Zeilen.
 *
 * <p>Grundsaetze:
 * <ul>
 *   <li>kein {@code REQUIRES_NEW} nach dem Setzen des Mandanten,</li>
 *   <li>kein Autocommit,</li>
 *   <li>kein Zugriff auf {@code app.*} ausserhalb einer solchen Transaktion,</li>
 *   <li>jede Abfrage traegt den Mandanten zusaetzlich **explizit** — die
 *       Durchsetzung auf Datenbankebene (RLS) und der Filter der Anwendung sind
 *       zwei unabhaengige Schichten (NFR-003).</li>
 * </ul>
 */
@Component
public class TenantTransactionRunner {

    /** Die transaktionslokale Bindung des Mandanten. */
    public static final String SET_TENANT_SQL = "SELECT app.set_tenant(?)";

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    /**
     * Erzeugt den Runner.
     *
     * @param jdbcTemplate     der JDBC-Adapter
     * @param transactionManager der Transaktionsverwalter
     */
    public TenantTransactionRunner(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Fuehrt die Aktion in der Mandantentransaktion aus.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param action         die Aktion
     * @param <T>            der Ergebnistyp
     * @return das Ergebnis der Aktion
     */
    public <T> T execute(String organisationId, Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            setTenant(organisationId);
            return action.get();
        });
    }

    /**
     * Fuehrt die Aktion in der Mandantentransaktion aus.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param action         die Aktion
     */
    public void execute(String organisationId, Runnable action) {
        transactionTemplate.executeWithoutResult(status -> {
            setTenant(organisationId);
            action.run();
        });
    }

    /**
     * Setzt den Mandanten der aktuellen Transaktion.
     *
     * @param organisationId der Mandant
     */
    public void setTenant(String organisationId) {
        if (organisationId == null || organisationId.isBlank()) {
            throw new IllegalArgumentException("Der Mandant darf nicht leer sein (fail-closed).");
        }
        jdbcTemplate.query(SET_TENANT_SQL,
                statement -> statement.setString(1, organisationId),
                resultSet -> {
                    resultSet.next();
                    return null;
                });
    }

    /**
     * Liefert die Argumente fuer einen benannten Mandanten.
     *
     * @param organisationId der Mandant
     * @return die Parameter
     */
    public static Map<String, Object> tenantParameter(String organisationId) {
        return Map.of("organisationId", organisationId);
    }
}
