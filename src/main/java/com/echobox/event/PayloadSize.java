package com.echobox.event;

/**
 * Die Groessengrenzen der Einlieferung (NFR-001, D-13).
 *
 * <p>Die Grenze betrifft die <strong>Nutzlastbytes</strong> — bei
 * vorverschluesselter Einlieferung also die Chiffre — und <strong>nicht</strong>
 * den Request-Body: bei Einlieferung als {@code payload_base64} ist der Body
 * base64-kodiert und damit rund 1,4-mal so gross.
 *
 * <p>Ein Byte ueber der Grenze ergibt HTTP 413 {@code payload_too_large};
 * es wird <strong>kein</strong> Ereignis angelegt.
 */
public final class PayloadSize {

    /** Die Grenze der Nutzlastbytes: 1.048.576 Byte (1 MiB). */
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;

    /** Die Grenze des Request-Bodys: 2 MiB (base64-kodierte Nutzlast). */
    public static final int MAX_REQUEST_BODY_BYTES = 2 * 1024 * 1024;

    private PayloadSize() {
        // statische Konstanten — keine Instanz
    }

    /**
     * Prueft, ob die Nutzlast die Grenze ueberschreitet.
     *
     * @param size die Zahl der Nutzlastbytes
     * @return {@code true}, wenn die Grenze ueberschritten ist
     */
    public static boolean exceedsPayloadLimit(long size) {
        return size > MAX_PAYLOAD_BYTES;
    }

    /**
     * Prueft, ob der Request-Body die Grenze ueberschreitet.
     *
     * @param size die Zahl der Body-Bytes
     * @return {@code true}, wenn die Grenze ueberschritten ist
     */
    public static boolean exceedsRequestBodyLimit(long size) {
        return size > MAX_REQUEST_BODY_BYTES;
    }
}
