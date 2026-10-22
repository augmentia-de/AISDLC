package com.echobox.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Die Seitennavigation des Vertrags ({@code components.schemas.Page}).
 *
 * <p>Alle Listen sind **cursor-basiert**: der Cursor traegt den Sortierschluessel
 * der Liste, nicht einen Offset. Zwischen zwei Seiten geaenderte Datensaetze
 * werden deshalb weder doppelt noch uebersprungen. {@code total} wird bewusst
 * **nicht** ausgewiesen.
 *
 * <p>Die Feldnamen sind <strong>explizit</strong> gesetzt: der Vertrag fuehrt
 * sie snake_case, unabhaengig von der Benennungskonvention der JSON-Bindung.
 * Es gibt <strong>eine</strong> Seitennavigation fuer alle Listen — die
 * Endpunktliste, das Zustellprotokoll und die Totenliste.
 *
 * @param limit      die maximale Zahl der Eintraege je Seite
 * @param hasMore    ob es eine weitere Seite gibt
 * @param nextCursor der Cursor der naechsten Seite ({@code null} bei der letzten Seite)
 */
public record PageResponse(
        @JsonProperty("limit") int limit,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("next_cursor") String nextCursor) {
}
