package com.echobox.protocol;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.echobox.protocol.api.EndpointMetricsResponse;
import com.echobox.tenant.TenantContext;

/**
 * Der REST-Adapter der Kennzahlen (FR-032, ADR-011 §4).
 *
 * <p>Der Adapter bildet <strong>nur</strong> ab: den Pfad, die Query-Parameter
 * und die Felder des eingefrorenen Vertrags. Die Fehlerabbildung auf HTTP
 * gehoert <strong>ausschliesslich</strong> dem {@code GlobalExceptionHandler}.
 *
 * <p><strong>Der Mandant kommt nie aus dem Request</strong>, sondern
 * ausschliesslich aus der Identitaet ({@link TenantContext}) — ein
 * Mandantenparameter wird zentral abgelehnt (D-01).
 *
 * <p>Der Adapter hat <strong>keine eigene Ressource {@code /metrics}</strong>:
 * die maschinelle Betriebsueberwachung laeuft ueber das Actuator
 * ({@code /actuator/prometheus}) — eine eigene Ressource wuerde sie
 * duplizieren (ADR-011 §4). Ebenso gehoert die Bereitschaftsanzeige
 * ({@code /health}, {@code /health/ready}) in das Betriebsmodul — dieser
 * Adapter bildet sie <strong>nicht</strong> ab.
 */
@RestController
@RequestMapping("/api/v1/endpoints")
public class MetricsController {

    private final EndpointMetricsService metricsService;

    private final TenantContext tenantContext;

    /**
     * Erzeugt den Adapter.
     *
     * @param metricsService die Kennzahlen des Endpunkts
     * @param tenantContext  der Mandant aus der Identitaet
     */
    public MetricsController(EndpointMetricsService metricsService, TenantContext tenantContext) {
        this.metricsService = metricsService;
        this.tenantContext = tenantContext;
    }

    /**
     * Liest die Kennzahlen eines Endpunkts — bei einem Endpunkt eines fremden
     * Mandanten HTTP 404 (D-02, AC-017-4).
     *
     * @param endpointId der Endpunkt
     * @param from       der Beginn des Auswertungsfensters (optional)
     * @param to         das Ende des Auswertungsfensters (optional)
     * @return die Kennzahlen
     */
    @GetMapping("/{endpointId}/metrics")
    public EndpointMetricsResponse metrics(@PathVariable("endpointId") UUID endpointId,
                                           @RequestParam(name = "from", required = false) String from,
                                           @RequestParam(name = "to", required = false) String to) {
        return metricsService.metrics(tenantContext.organisationId(), endpointId,
                ProtocolParameters.instant(from, "from"),
                ProtocolParameters.instant(to, "to"));
    }
}
