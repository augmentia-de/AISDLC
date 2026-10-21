package com.echobox.protocol.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.PageResponse;

/**
 * Das Zustellprotokoll — die Projektion des Vertrags
 * {@code components.schemas.DeliveryList}.
 *
 * <p>Die Liste ist <strong>mandantengefiltert</strong>: sie zeigt die Laeufe des
 * Mandanten aus der Identitaet — auch dem {@code service_admin} (E-10).
 *
 * @param items die Zustelllaeufe der Seite
 * @param page  die Angaben zur Seite (Limit, weitere Seite, Cursor)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryListResponse(
        @JsonProperty("items") List<DeliveryRunResponse> items,
        @JsonProperty("page") Page page) {

    /**
     * Erzeugt eine Protokollliste. Die Eintragsliste ist unabhaengig und
     * unveraenderlich.
     *
     * @param items die Zustelllaeufe der Seite
     * @param page  die Angaben zur Seite
     */
    public DeliveryListResponse {
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
