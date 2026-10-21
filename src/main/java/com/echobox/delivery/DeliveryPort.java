package com.echobox.delivery;

/**
 * Der Port zum ausgehenden Zustellzugriff.
 *
 * <p>Die Anwendung spricht den Empfaenger <strong>ausschliesslich</strong> ueber
 * dieses Port: nur so ist die Zustelllogik ohne Netz pruefbar — und nur so
 * ersetzt ein Testdouble ({@code RecordingDeliveryPort}) die HTTP-Implementierung.
 *
 * <p>Die HTTP-Mechanik selbst prueft {@code tests/integration/delivery/
 * SignedDeliveryIT.java} gegen einen echten Server.
 */
public interface DeliveryPort {

    /**
     * Liefert genau einen Zustellversuch an den Empfaenger.
     *
     * @param request der Versuch (Ziel-URL, Kopfzeilen, Nutzlast, Medientyp)
     * @return die Antwort des Empfaengers oder der Transportfehler
     */
    DeliveryResponse deliver(DeliveryRequest request);
}
