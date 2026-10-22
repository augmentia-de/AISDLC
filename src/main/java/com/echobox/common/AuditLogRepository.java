package com.echobox.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Der JDBC-Adapter des Audit-Protokolls ({@code app.audit_log}).
 *
 * <p>Das Audit-Protokoll haelt nachvollziehbare Aktionen mit der Identitaet
 * fest — <strong>ohne Nutzlastinhalt</strong> (FR-003, FR-024). Der Mandant
 * kommt aus der Identitaet; der Eintrag entsteht in derselben Transaktion wie
 * die Aktion, die er belegt.
 *
 * <p>Die Rolle wird als Datenbankrolle gefuehrt: {@code developer},
 * {@code receiver_admin}, {@code service_admin} oder {@code system}.
 */
@Repository
public class AuditLogRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INSERT_AUDIT = """
            INSERT INTO app.audit_log
                (audit_id, organisation_id, actor_id, actor_role, action, resource_type,
                 resource_id, details, occurred_at)
            VALUES
                (:auditId, :organisationId, :actorId, :actorRole, :action, :resourceType,
                 :resourceId, CAST(:details AS JSONB), :occurredAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Erzeugt den Adapter.
     *
     * @param jdbcTemplate die JDBC-Vorlage
     */
    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Schreibt einen Audit-Eintrag — ohne Nutzlastinhalt.
     *
     * @param organisationId der Mandant
     * @param actorId        die ausloesende Identitaet
     * @param actorRole      die Rolle der Identitaet
     * @param action         die Aktion
     * @param resourceType   die Art der Ressource
     * @param resourceId     die Kennung der Ressource (nullable)
     * @param details        die Einzelheiten der Aktion (nullable)
     * @param occurredAt     der Zeitpunkt der Aktion
     */
    public void insert(String organisationId,
                       String actorId,
                       String actorRole,
                       String action,
                       String resourceType,
                       UUID resourceId,
                       Map<String, Object> details,
                       java.time.Instant occurredAt) {
        Map<String, Object> payload = details == null ? Map.of() : details;
        jdbc.update(INSERT_AUDIT, SqlParameters.source()
                .addValue("auditId", UUID.randomUUID())
                .addValue("organisationId", organisationId)
                .addValue("actorId", actorId == null ? "" : actorId)
                .addValue("actorRole", actorRole)
                .addValue("action", action)
                .addValue("resourceType", resourceType)
                .addValue("resourceId", resourceId)
                .addValue("details", toJson(payload))
                .addValue("occurredAt", occurredAt));
    }

    /**
     * Baut eine Karte der Einzelheiten — die Reihenfolge der Eintraege bleibt
     * erhalten, damit der Eintrag lesbar bleibt.
     *
     * @param details die Einzelheiten
     * @return die Karte
     */
    public static Map<String, Object> details(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValuePairs.length; index += 2) {
            map.put(String.valueOf(keyValuePairs[index]), keyValuePairs[index + 1]);
        }
        return map;
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Der Audit-Eintrag konnte nicht serialisiert werden.", exception);
        }
    }
}
