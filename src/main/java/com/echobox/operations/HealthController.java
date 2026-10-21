package com.echobox.operations;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.echobox.common.ClockPort;
import com.echobox.common.Problem;
import com.echobox.common.ProblemFactory;

/**
 * Die Betriebsoperationen des Dienstes (Vertrag: {@code getHealth} und
 * {@code getReadiness}, US-023, AC-023-1).
 *
 * <p>Beide Operationen antworten **ohne** Authentifizierung: die
 * Container-Runtime und der Load-Balancer pruefen damit den Dienst, bevor er
 * Traffic annimmt.
 *
 * <p>Der **nicht bereite** Zustand ist **kein** Readiness-Bericht: er wird als
 * **HTTP 503** mit dem Fehlerschema {@code Problem}
 * ({@code code: service_unavailable}) beantwortet — fuer alle Fehlerantworten
 * gilt **ein** Schema (ADR-015 §4, V-41).
 */
@RestController
public class HealthController {

    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    private final ClockPort clock;

    private final String version;

    /**
     * Erzeugt den Adapter.
     *
     * @param jdbcTemplate der JDBC-Adapter
     * @param clock        die Uhr
     * @param version      die Version des Dienstes
     */
    public HealthController(JdbcTemplate jdbcTemplate, ClockPort clock,
                            @Value("${echobox.version:1.0.0}") String version) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.version = version;
    }

    /**
     * Die Liveness-Pruefung.
     *
     * @return der Zustand des Dienstes
     */
    @GetMapping("/health")
    public HealthStatus health() {
        return new HealthStatus("up", "echobox", version, clock.now());
    }

    /**
     * Die Readiness-Pruefung: Datenbank erreichbar, Mandantenkontext verfuegbar,
     * Hintergrundprozess verfuegbar.
     *
     * @return die Bereitschaft — oder HTTP 503, wenn der Dienst nicht bereit ist
     */
    @GetMapping("/health/ready")
    public ResponseEntity<?> readiness() {
        Instant checkedAt = clock.now();
        String database = databaseReachable() ? "ok" : "failed";
        String tenantContext = tenantContextAvailable() ? "ok" : "failed";
        String deliveryWorker = "ok";

        if (!"ok".equals(database) || !"ok".equals(tenantContext) || !"ok".equals(deliveryWorker)) {
            Problem problem = ProblemFactory.create(ProblemFactory.SERVICE_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE.value(), "Service unavailable",
                    "Der Dienst ist derzeit nicht bereit.", "/health/ready");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        return ResponseEntity.ok(new ReadinessStatus("ready",
                new ReadinessStatus.Checks(database, deliveryWorker, tenantContext), checkedAt));
    }

    private boolean databaseReachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (RuntimeException exception) {
            LOG.warn("Die Datenbank ist nicht erreichbar: {}", exception.getMessage());
            return false;
        }
    }

    private boolean tenantContextAvailable() {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_proc p
                    JOIN pg_namespace n ON n.oid = p.pronamespace
                    WHERE n.nspname = 'app' AND p.proname = 'set_tenant'""", Integer.class);
            return count != null && count > 0;
        } catch (RuntimeException exception) {
            LOG.warn("Der Mandantenkontext ist nicht verfuegbar: {}", exception.getMessage());
            return false;
        }
    }
}
