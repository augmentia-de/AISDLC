package com.echobox.common;

import java.util.UUID;

/**
 * Port fuer die Erzeugung der fachlichen Kennungen.
 *
 * <p>Die Kennungen sind **UUID-v7**: die Zeitpraefix macht sie zeitlich
 * sortierbar. Das ist kein Zufall — die Protokollsuche sortiert nach Zeit und
 * UUID; zeitlich sortierbare Kennungen halten die Indexierung stabil.
 */
public interface IdGenerator {

    /**
     * Erzeugt eine neue Kennung.
     *
     * @return die Kennung
     */
    UUID newId();
}
