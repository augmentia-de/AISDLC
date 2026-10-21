package com.echobox.protocol.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.echobox.common.PageResponse;

/**
 * Die Totenliste — die Projektion des Vertrags
 * {@code components.schemas.DeadDeliveryList}.
 *
 * <p>Die Liste ist <strong>mandantenuebergreifend</strong> und nur dem
 * {@code service_admin} zugaenglich (AC-016-1). Eine leere Trefferliste ist ein
 * HTTP 200 mit leerem {@code items}-Array, kein Fehler (AC-016-3).
 *
 * @param items die toten Zustellungen der Seite
 * @param page  die Angaben zur Seite (Limit, weitere Seite, Cursor)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeadDeliveryListResponse(
        @JsonProperty("items") List<DeadDeliveryResponse> items,
        @JsonProperty("page") Page page) {

    /**
     * Erzeugt eine Totenliste. Die Eintragsliste ist unabhaengig und
     * unveraenderlich.
     *
     * @param items die toten Zustellungen der Seite
     * @param page  die Angaben zur Seite
     */
    public DeadDeliveryListResponse {
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
