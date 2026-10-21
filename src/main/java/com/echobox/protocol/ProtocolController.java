package com.echobox.protocol;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.echobox.common.ValidationException;
import com.echobox.delivery.DeliveryStatus;
import com.echobox.protocol.api.DeadDeliveryListResponse;
import com.echobox.protocol.api.DeliveryListResponse;
import com.echobox.protocol.api.DeliveryRunDetailResponse;
import com.echobox.protocol.api.RetentionEnforcementRequest;
import com.echobox.protocol.api.RetentionEnforcementResponse;
import com.echobox.tenant.TenantContext;

/**
 * Der REST-Adapter des Protokollmoduls.
 *
 * <p>Der Adapter bildet <strong>nur</strong> ab: die Pfade, die Statuscodes
 * und die Felder des eingefrorenen Vertrags. Die Fehlerabbildung auf HTTP
 * gehoert <strong>ausschliesslich</strong> dem {@code GlobalExceptionHandler} —
 * die Fachlogik wirft die Ausnahmen aus {@code com.echobox.common}.
 *
 * <p><strong>Der Mandant kommt nie aus dem Request</strong>, sondern
 * ausschliesslich aus der Identitaet ({@link TenantContext}) — ein
 * Mandantenparameter wird zentral abgelehnt (D-01).
 *
 * <p>Die Operationen dieses Adapters:
 * <ul>
 *   <li>{@code GET /api/v1/deliveries} — das Zustellprotokoll (FR-030),</li>
 *   <li>{@code GET /api/v1/deliveries/{deliveryRunId}} — ein Zustelllauf mit
 *       allen Versuchen,</li>
 *   <li>{@code GET /api/v1/dead-deliveries} — die Totenliste (FR-031),</li>
 *   <li>{@code POST /api/v1/retention/enforcement} — die Aufbewahrung
 *       (FR-031).</li>
 * </ul>
 *
 * <p>Der <strong>Replay</strong> gehoert nicht zu diesem Adapter: er liegt im
 * Zustellmodul (Traceability-Matrix, Schritt 1).
 */
@RestController
@RequestMapping("/api/v1")
public class ProtocolController {

    private final ProtocolQueryService queryService;

    private final DeadDeliveryQueryService deadDeliveryQueryService;

    private final RetentionService retentionService;

    private final TenantContext tenantContext;

    /**
     * Erzeugt den Adapter.
     *
     * @param queryService            die Lesezugriffe des Zustellprotokolls
     * @param deadDeliveryQueryService die Lesezugriffe der Totenliste
     * @param retentionService        die Durchsetzung der Aufbewahrung
     * @param tenantContext           der Mandant aus der Identitaet
     */
    public ProtocolController(ProtocolQueryService queryService,
                              DeadDeliveryQueryService deadDeliveryQueryService,
                              RetentionService retentionService,
                              TenantContext tenantContext) {
        this.queryService = queryService;
        this.deadDeliveryQueryService = deadDeliveryQueryService;
        this.retentionService = retentionService;
        this.tenantContext = tenantContext;
    }

    /**
     * Liest das Zustellprotokoll — mandantengefiltert und cursor-basiert
     * paginiert.
     *
     * @param endpointId der Filter auf den Endpunkt (optional)
     * @param eventId    der Filter auf die Ereignis-ID (optional)
     * @param eventType  der Filter auf den Ereignistyp (optional)
     * @param status     der Filter auf den Zustand, kommasepariert (optional)
     * @param from       der Beginn des Zeitraums auf {@code created_at} (optional)
     * @param to         das Ende des Zeitraums auf {@code created_at} (optional)
     * @param limit      die Zahl der Eintraege je Seite (optional)
     * @param cursor     der Cursor der vorherigen Seite (optional)
     * @return die Seite des Zustellprotokolls
     */
    @GetMapping("/deliveries")
    public DeliveryListResponse listDeliveries(@RequestParam(name = "endpoint_id", required = false) UUID endpointId,
                                               @RequestParam(name = "event_id", required = false) UUID eventId,
                                               @RequestParam(name = "event_type", required = false) String eventType,
                                               @RequestParam(name = "status", required = false) String status,
                                               @RequestParam(name = "from", required = false) String from,
                                               @RequestParam(name = "to", required = false) String to,
                                               @RequestParam(name = "limit", required = false) Integer limit,
                                               @RequestParam(name = "cursor", required = false) String cursor) {
        return queryService.list(tenantContext.organisationId(), endpointId, eventId, eventType,
                deliveryStatuses(status),
                ProtocolParameters.instant(from, "from"),
                ProtocolParameters.instant(to, "to"),
                ProtocolParameters.limit(limit),
                cursor);
    }

    /**
     * Liest einen Zustelllauf <strong>mit allen Versuchen</strong> — bei einem
     * Lauf eines fremden Mandanten HTTP 404 (D-02, V-27).
     *
     * @param deliveryRunId die Zustelllauf-ID
     * @return der Zustelllauf mit seinen Versuchen
     */
    @GetMapping("/deliveries/{deliveryRunId}")
    public DeliveryRunDetailResponse getDeliveryRun(@PathVariable("deliveryRunId") UUID deliveryRunId) {
        return queryService.get(tenantContext.organisationId(), deliveryRunId);
    }

    /**
     * Liest die toten Zustellungen — mandantenuebergreifend, aber nur fuer den
     * {@code service_admin} (AC-016-1, AC-016-2).
     *
     * @param endpointId der Filter auf den Endpunkt (optional)
     * @param eventType  der Filter auf den Ereignistyp (optional)
     * @param status     der Filter auf den Zustand — erlaubt ist nur
     *                   {@code abandoned} (optional)
     * @param from       der Beginn des Zeitraums auf {@code abandoned_at} (optional)
     * @param to         das Ende des Zeitraums auf {@code abandoned_at} (optional)
     * @param limit      die Zahl der Eintraege je Seite (optional)
     * @param cursor     der Cursor der vorherigen Seite (optional)
     * @return die Seite der Totenliste
     */
    @GetMapping("/dead-deliveries")
    public DeadDeliveryListResponse listDeadDeliveries(
            @RequestParam(name = "endpoint_id", required = false) UUID endpointId,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return deadDeliveryQueryService.list(endpointId, eventType, ProtocolParameters.values(status),
                ProtocolParameters.instant(from, "from"),
                ProtocolParameters.instant(to, "to"),
                ProtocolParameters.limit(limit),
                cursor);
    }

    /**
     * Setzt die Aufbewahrung durch — HTTP 200.
     *
     * <p>Der Body ist <strong>optional</strong>: ohne Body gilt
     * {@code dry_run = true} — ein versehentlicher Aufruf verliert keine Daten.
     *
     * @param request der Request-Body (optional)
     * @return das Ergebnis der Durchsetzung
     */
    @PostMapping("/retention/enforcement")
    public RetentionEnforcementResponse enforceRetention(
            @RequestBody(required = false) RetentionEnforcementRequest request) {
        if (request != null) {
            request.validate();
        }
        boolean dryRun = request == null || request.dryRunEnabled();
        return retentionService.enforce(dryRun);
    }

    /**
     * Die Zustaende des Zustellprotokolls — ein unbekannter Wert ist ein
     * Verstoss gegen den Vertrag (HTTP 422).
     *
     * @param status der mitgeschickte Wert (kommasepariert)
     * @return die Zustaende
     */
    private static List<String> deliveryStatuses(String status) {
        List<String> statuses = ProtocolParameters.values(status);
        for (String value : statuses) {
            try {
                DeliveryStatus.fromWire(value);
            } catch (IllegalArgumentException exception) {
                throw new ValidationException("status", "status_invalid",
                        "Unbekannter Zustellstatus: " + value);
            }
        }
        return statuses;
    }
}
