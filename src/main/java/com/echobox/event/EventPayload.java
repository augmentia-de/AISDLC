package com.echobox.event;

/**
 * Die Nutzlast eines Ereignisses — Kodierung und Bytes.
 *
 * <p>Bei {@link PayloadEncoding#PRE_ENCRYPTED} sind die Bytes bereits
 * verschluesselt: sie werden <strong>unveraendert</strong> gespeichert und
 * <strong>unveraendert</strong> zugestellt. Der Dienst entschluesselt sie
 * <strong>nicht</strong> (FR-013, AC-008-1).
 *
 * @param encoding die Kodierung
 * @param bytes    die Nutzlastbytes
 */
public record EventPayload(PayloadEncoding encoding, byte[] bytes) {

    /**
     * Erzeugt eine Nutzlast mit kopierten Bytes.
     *
     * @param encoding die Kodierung
     * @param bytes    die Nutzlastbytes
     */
    public EventPayload {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    /**
     * Klartextnutzlast — der Dienst verschluesselt sie selbst.
     *
     * @param bytes die Klartextbytes
     * @return die Nutzlast
     */
    public static EventPayload plaintext(byte[] bytes) {
        return new EventPayload(PayloadEncoding.PLAINTEXT, bytes);
    }

    /**
     * Bereits verschluesselte Nutzlast — die Bytes werden unveraendert
     * uebernommen.
     *
     * @param bytes die Chiffre
     * @return die Nutzlast
     */
    public static EventPayload preEncrypted(byte[] bytes) {
        return new EventPayload(PayloadEncoding.PRE_ENCRYPTED, bytes);
    }

    /**
     * Ist die Nutzlast bereits verschluesselt?
     *
     * @return {@code true}, wenn die Bytes unveraendert weitergereicht werden
     */
    public boolean isPreEncrypted() {
        return encoding == PayloadEncoding.PRE_ENCRYPTED;
    }

    /**
     * Die Zahl der Nutzlastbytes — genau diese Bytes werden zugestellt.
     *
     * @return die Groesse der Nutzlast in Byte
     */
    public int sizeBytes() {
        return bytes.length;
    }
}
