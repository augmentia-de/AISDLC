package com.echobox.event;

import java.util.Locale;

/**
 * Die Kodierung der eingelieferten Nutzlast (FR-012, FR-013).
 *
 * <p>{@link #PLAINTEXT}: der Dienst verschluesselt die Nutzlast selbst.
 * {@link #PRE_ENCRYPTED}: die Bytes sind bereits verschluesselt und werden
 * <strong>unveraendert</strong> gespeichert und zugestellt — vor der Zustellung
 * findet <strong>kein</strong> Entschlüsseln statt.
 */
public enum PayloadEncoding {

    /** Klartext — der Dienst verschluesselt die Nutzlast selbst. */
    PLAINTEXT,

    /** Bereits verschluesselt — die Bytes werden unveraendert uebernommen. */
    PRE_ENCRYPTED;

    /**
     * Der Wert im Vertrag — klein geschrieben, wie im Schema.
     *
     * @return der Drahtwert
     */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Liest die Kodierung aus dem Request-Body.
     *
     * @param value der Wert aus dem Request
     * @return die Kodierung
     * @throws IllegalArgumentException wenn der Wert unbekannt ist
     */
    public static PayloadEncoding fromWire(String value) {
        for (PayloadEncoding encoding : values()) {
            if (encoding.wireValue().equals(value)) {
                return encoding;
            }
        }
        throw new IllegalArgumentException("Unbekannte Nutzlast-Kodierung: " + value);
    }
}
