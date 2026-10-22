package com.echobox.endpoint.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.PageResponse;

/**
 * Die Endpunktliste — die Projektion des Vertrags
 * {@code components.schemas.EndpointList}.
 *
 * @param items die Endpunkte der Seite
 * @param page  die Angaben zur Seite (Limit, weitere Seite, Cursor)
 */
public record EndpointListResponse(
        @JsonProperty("items") List<EndpointResponse> items,
        @JsonProperty("page") PageResponse page) {

    /**
     * Erzeugt eine Endpunktliste.
     *
     * @param items die Endpunkte der Seite
     * @param page  die Angaben zur Seite
     */
    public EndpointListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
