package com.echobox.delivery;

import java.util.Map;

/**
 * Ein Zustellversuch: was genau gesendet wird.
 *
 * <p>Der Request-Body sind die <strong>Nutzlastbytes des Ereignisses</strong> —
 * keine Huelle, kein Envelope. Alle Metadaten stehen in den Kopfzeilen
 * (eingefrorener Vertrag, {@code x-delivery-protocol.request}).
 *
 * @param targetUrl   die registrierte Ziel-URL
 * @param headers     die Kopfzeilen des Versuchs
 * @param payload     die gesendeten Nutzlastbytes
 * @param contentType der Medientyp der Nutzlast
 */
public record DeliveryRequest(String targetUrl,
                               Map<String, String> headers,
                               byte[] payload,
                               String contentType) {

    /**
     * Erzeugt einen Versuch mit kopierten Feldern.
     *
     * @param targetUrl   die registrierte Ziel-URL
     * @param headers     die Kopfzeilen des Versuchs
     * @param payload     die gesendeten Nutzlastbytes
     * @param contentType der Medientyp der Nutzlast
     */
    public DeliveryRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        payload = payload == null ? new byte[0] : payload.clone();
    }
}
