package com.echobox.delivery;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.echobox.delivery.api.ReplayRequest;
import com.echobox.delivery.api.ReplayResponse;

/**
 * Der REST-Adapter des Zustellmoduls — der Replay (US-013, FR-024).
 *
 * <p>Der Adapter bildet <strong>nur</strong> ab: den Pfad, den Statuscode und
 * die Felder des eingefrorenen Vertrags. Die Fehlerabbildung auf HTTP gehoert
 * <strong>ausschliesslich</strong> dem {@code GlobalExceptionHandler} — die
 * Fachlogik wirft die Ausnahmen aus {@code com.echobox.common}.
 *
 * <p>Der Mandant kommt nie aus dem Request, sondern ausschliesslich aus der
 * Identitaet ({@code TenantContext}) — ein Mandantenparameter wird zentral
 * abgelehnt (D-01).
 */
@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    /**
     * Erzeugt den Adapter.
     *
     * @param service die Fachlogik
     */
    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    /**
     * Loest eine aufgegebene Zustellung erneut aus — HTTP 202.
     *
     * <p>Der Request-Body ist optional: ohne Begruendung wird der Lauf ohne
     * Audit-Begruendung replayed.
     *
     * @param deliveryRunId die Zustelllauf-ID der aufgegebenen Zustellung
     * @param request       der Request-Body (optional)
     * @return der neue Zustelllauf
     */
    @PostMapping("/{deliveryRunId}/replay")
    public ResponseEntity<ReplayResponse> replay(@PathVariable("deliveryRunId") UUID deliveryRunId,
                                                  @RequestBody(required = false) ReplayRequest request) {
        if (request != null) {
            request.validate();
        }
        ReplayResponse response = service.replay(deliveryRunId, request == null ? null : request.reason());
        return ResponseEntity.accepted().body(response);
    }
}
