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
        @JsonProperty("page") PageResponse page) {

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
}
