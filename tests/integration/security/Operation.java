package com.echobox.security;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Die 17 Operationen des eingefrorenen Vertrags mit ihrer Rollenbindung.
 *
 * <p>Grundlage ist die Rollenmatrix in {@code docs/api/openapi.yaml}
 * ({@code info.description}, Abschnitt „Rollen") und {@code x-contract-rules.roles}
 * — normativ ADR-015 §1.
 *
 * <p>Die Platzhalter im Pfad werden vom Test mit den Kennungen der eigenen und
 * der fremden Ressource gefuellt.
 */
enum Operation {

    CREATE_ENDPOINT("createEndpoint", HttpMethod.POST, "/api/v1/endpoints",
            """
            {
              "target_url": "https://rollen-test.example.invalid/hooks/echobox",
              "secret": "s3cr3t-rotationswert",
              "event_types": ["order.changed"]
            }""", false, List.of("receiver_admin", "service_admin")),

    LIST_ENDPOINTS("listEndpoints", HttpMethod.GET, "/api/v1/endpoints",
            "", false, List.of("developer", "receiver_admin", "service_admin")),

    GET_ENDPOINT("getEndpoint", HttpMethod.GET, "/api/v1/endpoints/{ownEndpoint}",
            "", true, List.of("developer", "receiver_admin", "service_admin")),

    UPDATE_ENDPOINT("updateEndpoint", HttpMethod.PATCH, "/api/v1/endpoints/{ownEndpoint}",
            "{\"target_url\":\"https://rollen-test.example.invalid/hooks/echobox-v2\"}",
            true, List.of("receiver_admin", "service_admin")),

    PAUSE_ENDPOINT("pauseEndpoint", HttpMethod.POST, "/api/v1/endpoints/{ownEndpoint}/pause",
            "", true, List.of("receiver_admin", "service_admin")),

    RESUME_ENDPOINT("resumeEndpoint", HttpMethod.POST, "/api/v1/endpoints/{ownEndpoint}/resume",
            "", true, List.of("receiver_admin", "service_admin")),

    ROTATE_SECRET("rotateEndpointSecret", HttpMethod.POST, "/api/v1/endpoints/{ownEndpoint}/secret-rotation",
            "{\"secret\":\"neues-s3cr3t-rotationswert\"}",
            true, List.of("receiver_admin")),

    SUBMIT_EVENT("submitEvent", HttpMethod.POST, "/api/v1/events",
            """
            {
              "event_type": "order.changed",
              "idempotency_key": "rollen-test-laufend",
              "payload": {"order_id": 4711}
            }""", false, List.of("developer")),

    GET_EVENT("getEvent", HttpMethod.GET, "/api/v1/events/{ownEvent}",
            "", true, List.of("developer", "receiver_admin", "service_admin")),

    LIST_DELIVERIES("listDeliveries", HttpMethod.GET, "/api/v1/deliveries",
            "", false, List.of("developer", "receiver_admin", "service_admin")),

    GET_DELIVERY_RUN("getDeliveryRun", HttpMethod.GET, "/api/v1/deliveries/{ownRun}",
            "", true, List.of("developer", "receiver_admin", "service_admin")),

    LIST_DEAD_DELIVERIES("listDeadDeliveries", HttpMethod.GET, "/api/v1/dead-deliveries",
            "", false, List.of("service_admin")),

    REPLAY("replayDelivery", HttpMethod.POST, "/api/v1/deliveries/{ownRun}/replay",
            "{\"reason\":\"Rollen-Test\"}",
            true, List.of("receiver_admin")),

    ENDPOINT_METRICS("getEndpointMetrics", HttpMethod.GET, "/api/v1/endpoints/{ownEndpoint}/metrics",
            "", true, List.of("developer", "receiver_admin", "service_admin")),

    ENFORCE_RETENTION("enforceRetention", HttpMethod.POST, "/api/v1/retention/enforcement",
            "{\"dry_run\":true}", false, List.of("service_admin")),

    HEALTH("getHealth", HttpMethod.GET, "/health",
            "", false, List.of("developer", "receiver_admin", "service_admin")),

    READINESS("getReadiness", HttpMethod.GET, "/health/ready",
            "", false, List.of("developer", "receiver_admin", "service_admin"));

    private final String operationId;
    private final HttpMethod method;
    private final String path;
    private final String body;
    private final boolean resourceBound;
    private final List<String> allowedRoles;

    Operation(String operationId, HttpMethod method, String path, String body, boolean resourceBound,
            List<String> allowedRoles) {
        this.operationId = operationId;
        this.method = method;
        this.path = path;
        this.body = body;
        this.resourceBound = resourceBound;
        this.allowedRoles = allowedRoles;
    }

    String operationId() {
        return operationId;
    }

    String path() {
        return path;
    }

    String body() {
        return body;
    }

    boolean resourceBound() {
        return resourceBound;
    }

    List<String> allowedRoles() {
        return allowedRoles;
    }

    /**
     * Baut den HTTP-Aufruf fuer die Operation.
     *
     * @param resolvedPath der Pfad mit gefuellten Platzhaltern
     * @return der Request-Builder
     */
    MockHttpServletRequestBuilder request(String resolvedPath) {
        if (HttpMethod.GET.equals(method)) {
            return get(resolvedPath);
        }
        if (HttpMethod.PATCH.equals(method)) {
            return patch(resolvedPath);
        }
        return post(resolvedPath);
    }
}
