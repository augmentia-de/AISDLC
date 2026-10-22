package com.echobox.common;

import java.time.Instant;
import java.util.UUID;

/**
 * Der Sortierschluessel, den ein Cursor traegt.
 *
 * <p>Ein Cursor haengt **nicht** an einem Offset, sondern an der Position des
 * letzten Eintrags der Seite: dem Sortierschluessel der Liste (der Anlege- oder
 * Aktualisierungszeitpunkt) und der Kennung dieses Eintrags. Zwischen zwei
 * Seiten geaenderte Datensaetze werden dadurch weder doppelt noch
 * uebersprungen (FR-030).
 *
 * <p>Der Sortierschluessel traegt die **Reihenfolge**, die Kennung macht sie
 * **total** — zwei Eintraege mit demselben Zeitpunkt stehen in einer
 * eindeutigen Reihenfolge.
 *
 * @param sortKey der Sortierschluessel der Liste (nullable = Anfang der Liste)
 * @param id      die Kennung des letzten Eintrags der Seite (nullable = Anfang der Liste)
 */
public record CursorPosition(Instant sortKey, UUID id) {

    /** Der Anfang der Liste — kein Cursor. */
    public static final CursorPosition START = new CursorPosition(null, null);
}
