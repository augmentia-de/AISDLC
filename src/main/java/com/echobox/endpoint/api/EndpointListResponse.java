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
        @JsonProperty("page") Page page) {

    /**
     * Erzeugt eine Endpunktliste.
     *
     * @param items die Endpunkte der Seite
     * @param page  die Angaben zur Seite
     */
    public EndpointListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Die Angaben zur Seite — die Projektion des Vertrags
     * {@code components.schemas.Page}.
     *
     * <p>Die Feldnamen sind <strong>explizit</strong> gesetzt: der Vertrag
     * fuehrt sie snake_case, unabhaengig von der Benennungskonvention der
     * JSON-Bindung.
     *
     * @param limit      die maximale Zahl der Eintraege je Seite
     * @param hasMore    ob es eine weitere Seite gibt
     * @param nextCursor der Cursor der naechsten Seite (nullable)
     */
    public record Page(
            @JsonProperty("limit") int limit,
            @JsonProperty("has_more") boolean hasMore,
            @JsonProperty("next_cursor") String nextCursor) {

        /**
         * Bildet die Seitenangaben aus dem Traeger des Fachmodells.
         *
         * @param response der Traeger
         * @return die Seitenangaben
         */
        public static Page of(PageResponse response) {
            return new Page(response.limit(), response.hasMore(), response.nextCursor());
        }
    }
}
