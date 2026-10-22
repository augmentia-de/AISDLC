package com.echobox.event;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.echobox.event.api.EventIntakeRequest;
import com.echobox.event.api.EventIntakeResponse;
import com.echobox.event.api.EventResponse;

/**
 * Der REST-Adapter des Ereignismoduls.
 *
 * <p>Der Adapter bildet <strong>nur</strong> ab: die Pfade, die Statuscodes
 * und die Felder des eingefrorenen Vertrags. Die Fehlerabbildung auf HTTP
 * gehoert <strong>ausschliesslich</strong> dem {@code GlobalExceptionHandler}.
 *
 * <p>Die Annahme ist <strong>asynchron</strong>: die Antwort wird mit HTTP 202
 * gegeben, die Zustellung startet erst danach (NFR-002, D-10).
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    /**
     * Erzeugt den Adapter.
     *
     * @param service die Fachlogik
     */
    public EventController(EventService service) {
        this.service = service;
    }

    /**
     * Liefert ein Ereignis ein — HTTP 202.
     *
     * <p>Der Medientyp ist **festgelegt** auf {@code application/json} (S-28):
     * der Vertrag kennt keinen anderen Koerper. Ein anderer Medientyp — auch
     * {@code application/*+json} — wird mit HTTP 400 {@code malformed_request}
     * abgelehnt, statt vom Message-Converter gelesen zu werden.
     *
     * @param request der Request-Body
     * @return der Annahmelauf
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventIntakeResponse> submit(@RequestBody EventIntakeRequest request) {
        EventService.IntakeResult result = service.submit(request);
        Event event = result.event();
        EventIntakeResponse response = new EventIntakeResponse(
                event.eventId().toString(),
                event.eventType(),
                event.idempotencyKey(),
                event.status(),
                result.idempotentReplay(),
                event.subscribedEndpointCount(),
                event.payloadSizeBytes(),
                event.acceptedAt());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Liest die Metadaten eines Ereignisses — HTTP 200, bei einem fremden
     * Ereignis HTTP 404.
     *
     * @param eventId die Ereignis-ID
     * @return die Metadaten
     */
    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable("eventId") UUID eventId) {
        return EventResponse.of(service.get(eventId));
    }
}
