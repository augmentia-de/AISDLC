package com.echobox.event;

import java.security.MessageDigest;
import java.util.Optional;

/**
 * Die Entscheidung des Idempotenzvergleichs (ADR-007, D-05).
 *
 * <p>Der Geltungsbereich des Idempotenzschluessels ist das Paar
 * (Mandant, Schluessel). Eine Wiederholung mit gleicher Nutzlast ist
 * <strong>erfolgreich</strong> und liefert die Kennung des ersten Ereignisses;
 * eine Wiederholung mit abweichender Nutzlast ist ein <strong>Konflikt</strong>
 * (HTTP 409).
 */
public enum IdempotencyDecision {

    /** Der Schluessel ist unbekannt — es entsteht ein neues Ereignis. */
    NEW,

    /** Der Schluessel ist bekannt und die Nutzlast gleich — Wiederholung. */
    REPLAYED,

    /** Der Schluessel ist bekannt, die Nutzlast weicht ab — Konflikt. */
    CONFLICT;

    /**
     * Entscheidet anhand des gespeicherten Ereignisses und des Fingerprints
     * der eingelieferten Nutzlast.
     *
     * <p>Der Vergleich der Fingerprints ist <strong>zeitkonstant</strong>
     * ({@link MessageDigest#isEqual(byte[], byte[])}).
     *
     * @param existing    das gespeicherte Ereignis (leer, wenn es keines gibt)
     * @param fingerprint der Fingerprint der eingelieferten Nutzlast
     * @return die Entscheidung
     */
    public static IdempotencyDecision of(Optional<StoredEvent> existing, byte[] fingerprint) {
        if (existing == null || existing.isEmpty()) {
            return NEW;
        }
        byte[] stored = existing.get().fingerprint();
        if (stored != null && MessageDigest.isEqual(stored, fingerprint)) {
            return REPLAYED;
        }
        return CONFLICT;
    }
}
