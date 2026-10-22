package com.echobox.endpoint;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.echobox.common.ValidationException;
import com.echobox.endpoint.api.EndpointCreateRequest;
import com.echobox.endpoint.api.EndpointListResponse;
import com.echobox.endpoint.api.EndpointResponse;
import com.echobox.endpoint.api.EndpointUpdateRequest;
import com.echobox.endpoint.api.SecretRotationRequest;
import com.echobox.endpoint.api.SecretRotationResponse;
import com.echobox.tenant.TenantContext;

/**
 * Der REST-Adapter des Endpunktmoduls.
 *
 * <p>Der Adapter bildet <strong>nur</strong> ab: die Pfade, die Statuscodes
 * und die Felder des eingefrorenen Vertrags. Die Fehlerabbildung auf HTTP
 * gehoert <strong>ausschliesslich</strong> dem {@code GlobalExceptionHandler} —
 * die Fachlogik wirft die Ausnahmen aus {@code com.echobox.common}.
 *
 * <p><strong>Der Mandant kommt nie aus dem Request</strong>, sondern
 * ausschliesslich aus der Identitaet ({@link TenantContext}) — ein
 * Mandantenparameter wird zentral abgelehnt (D-01).
 */
@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointController {

    /** Der Basispfad der Endpunkte — traegt den {@code Location}-Header. */
    private static final String BASE_PATH = "/api/v1/endpoints";

    /** Die Vorgabezahl der Eintraege je Seite. */
    private static final int DEFAULT_LIMIT = 50;

    private final EndpointService service;

    private final EndpointQueryService queryService;

    private final TenantContext tenantContext;

    /**
     * Erzeugt den Adapter.
     *
     * @param service       die Fachlogik
     * @param queryService  die Lesezugriffe
     * @param tenantContext der Mandant aus der Identitaet
     */
    public EndpointController(EndpointService service,
                              EndpointQueryService queryService,
                              TenantContext tenantContext) {
        this.service = service;
        this.queryService = queryService;
        this.tenantContext = tenantContext;
    }

    /**
     * Registriert einen Endpunkt — HTTP 201 mit dem {@code Location}-Header.
     *
     * @param request der Request-Body
     * @return der angelegte Endpunkt
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EndpointResponse> create(@RequestBody EndpointCreateRequest request) {
        Endpoint endpoint = service.register(request);
        return ResponseEntity
                .created(URI.create(BASE_PATH + "/" + endpoint.endpointId()))
                .body(EndpointResponse.of(endpoint));
    }

    /**
     * Listet die Endpunkte — mandantengefiltert, fuer den {@code service_admin}
     * mandantenuebergreifend.
     *
     * @param status der Statusfilter (kommasepariert, optional)
     * @param limit  die Zahl der Eintraege je Seite
     * @param cursor der Cursor der vorherigen Seite (optional)
     * @return die Seite
     */
    @GetMapping
    public EndpointListResponse list(@RequestParam(name = "status", required = false) String status,
                                     @RequestParam(name = "limit", required = false) Integer limit,
                                     @RequestParam(name = "cursor", required = false) String cursor) {
        List<String> statuses = parseStatuses(status);
        int effectiveLimit = validateLimit(limit);
        if (tenantContext.hasRole(EndpointService.ROLE_SERVICE_ADMIN)) {
            return queryService.listCrossTenant(statuses, effectiveLimit, cursor);
        }
        return queryService.list(tenantContext.organisationId(), statuses, effectiveLimit, cursor);
    }

    /**
     * Liest einen Endpunkt — HTTP 200, bei einem fremden Endpunkt HTTP 404.
     *
     * @param endpointId die Endpunkt-ID
     * @return der Endpunkt
     */
    @GetMapping("/{endpointId}")
    public EndpointResponse get(@PathVariable("endpointId") UUID endpointId) {
        return EndpointResponse.of(queryService.get(tenantContext.organisationId(), endpointId));
    }

    /**
     * Aendert einen Endpunkt — nur die mitgeschickten Felder.
     *
     * @param endpointId die Endpunkt-ID
     * @param request    der Request-Body
     * @return der geaenderte Endpunkt
     */
    @PatchMapping(value = "/{endpointId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EndpointResponse update(@PathVariable("endpointId") UUID endpointId,
                                   @RequestBody EndpointUpdateRequest request) {
        return EndpointResponse.of(service.update(endpointId, request));
    }

    /**
     * Pausiert einen Endpunkt.
     *
     * @param endpointId die Endpunkt-ID
     * @return der pausierte Endpunkt
     */
    @PostMapping("/{endpointId}/pause")
    public EndpointResponse pause(@PathVariable("endpointId") UUID endpointId) {
        return EndpointResponse.of(service.pause(endpointId));
    }

    /**
     * Reaktiviert einen Endpunkt — es wird nichts nachgeliefert.
     *
     * @param endpointId die Endpunkt-ID
     * @return der reaktivierte Endpunkt
     */
    @PostMapping("/{endpointId}/resume")
    public EndpointResponse resume(@PathVariable("endpointId") UUID endpointId) {
        return EndpointResponse.of(service.resume(endpointId));
    }

    /**
     * Rotiert das Secret eines Endpunkts.
     *
     * @param endpointId die Endpunkt-ID
     * @param request    der Request-Body
     * @return der Stand der Rotation
     */
    @PostMapping(value = "/{endpointId}/secret-rotation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SecretRotationResponse rotateSecret(@PathVariable("endpointId") UUID endpointId,
                                                @RequestBody SecretRotationRequest request) {
        request.validate();
        return service.rotateSecret(endpointId, request.secret());
    }

    private static List<String> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        List<String> statuses = Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        for (String value : statuses) {
            EndpointStatus.fromWire(value);
        }
        return statuses;
    }

    private static int validateLimit(Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > EndpointQueryService.MAX_LIMIT) {
            throw new ValidationException("limit", "limit_out_of_range",
                    "Die Zahl der Eintraege je Seite muss zwischen 1 und "
                            + EndpointQueryService.MAX_LIMIT + " liegen.");
        }
        return effective;
    }
}
