package com.echobox.common;

/**
 * Die Seitennavigation des Vertrags ({@code components.schemas.Page}).
 *
 * <p>Alle Listen sind **cursor-basiert**: der Cursor traegt den Sortierschluessel
 * der Liste, nicht einen Offset. Zwischen zwei Seiten geaenderte Datensaetze
 * werden deshalb weder doppelt noch uebersprungen. {@code total} wird bewusst
 * **nicht** ausgewiesen.
 *
 * @param limit      die maximale Zahl der Eintraege je Seite
 * @param hasMore    ob es eine weitere Seite gibt
 * @param nextCursor der Cursor der naechsten Seite ({@code null} bei der letzten Seite)
 */
public record PageResponse(int limit, boolean hasMore, String nextCursor) {
}
