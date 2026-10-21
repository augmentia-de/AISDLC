package com.echobox.testsupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.echobox.delivery.DeliveryPort;
import com.echobox.delivery.DeliveryRequest;
import com.echobox.delivery.DeliveryResponse;

/**
 * Aufzeichnender Testdouble fuer den ausgehenden Zustellport.
 *
 * <p>Die Architektur legt fest: „Alle nach aussen gerichteten Zugriffe laufen
 * ueber Ports" — die Zustellung spricht den Empfaenger ueber
 * {@link DeliveryPort} an, die HTTP-Implementierung ist ein Adapter. Die
 * Zustelllogik (Backoff, Klassifikation, Zustandsautomat) wird deshalb gegen
 * diesen Port geprueft; die HTTP-Mechanik selbst in
 * {@code tests/integration/delivery/SignedDeliveryIT.java} gegen einen echten
 * Server.
 *
 * <p>Der Testdouble haelt fest, was der Dienst gesendet hat, und liefert die
 * Antworten, die der Test vorgegeben hat — inklusive Timeout und Netzwerkfehler
 * (der Pflichtfall „Abhaengigkeit antwortet langsam oder mit Fehler").
 */
public final class RecordingDeliveryPort implements DeliveryPort {

    private final List<DeliveryRequest> requests = new CopyOnWriteArrayList<>();
    private final List<DeliveryResponse> scripted = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> statusPerUrl = new LinkedHashMap<>();
    private Duration responseDelay = Duration.ZERO;

    @Override
    public DeliveryResponse deliver(DeliveryRequest request) {
        requests.add(request);
        if (!responseDelay.isZero()) {
            try {
                Thread.sleep(responseDelay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new DeliveryResponse(null, "", Map.of(), DeliveryResponse.TransportFailure.TIMEOUT);
            }
        }
        if (!scripted.isEmpty()) {
            return scripted.remove(0);
        }
        Integer status = statusPerUrl.get(request.targetUrl());
        return new DeliveryResponse(status == null ? 200 : status, "", Map.of(), DeliveryResponse.TransportFailure.NONE);
    }

    /**
     * Legt die Antworten fest, die der Port in der Reihenfolge der Aufrufe
     * liefert.
     *
     * @param responses die Antworten
     * @return dieser Testdouble
     */
    public RecordingDeliveryPort script(DeliveryResponse... responses) {
        scripted.addAll(List.of(responses));
        return this;
    }

    /**
     * Legt fest, mit welchem Statuscode ein Endpunkt antwortet.
     *
     * @param targetUrl die Ziel-URL
     * @param status    der Statuscode
     * @return dieser Testdouble
     */
    public RecordingDeliveryPort respondWith(String targetUrl, int status) {
        statusPerUrl.put(targetUrl, status);
        return this;
    }

    /**
     * Legt fest, wie lange der Empfaenger antwortet (der Pflichtfall
     * „Abhaengigkeit antwortet langsam").
     *
     * @param delay die Antwortzeit
     * @return dieser Testdouble
     */
    public RecordingDeliveryPort respondAfter(Duration delay) {
        this.responseDelay = delay;
        return this;
    }

    /**
     * Liefert alle gesendeten Anfragen.
     *
     * @return die Anfragen in der Reihenfolge des Absendens
     */
    public List<DeliveryRequest> requests() {
        return new ArrayList<>(requests);
    }

    /**
     * Liefert die Anfrage mit der angegebenen Nummer (1-basiert).
     *
     * @param number die Nummer des Versuchs
     * @return die Anfrage
     */
    public DeliveryRequest request(int number) {
        return requests.get(number - 1);
    }

    /**
     * Liefert die Anzahl der gesendeten Anfragen.
     *
     * @return die Anzahl der Versuche
     */
    public int requestCount() {
        return requests.size();
    }

    /**
     * Setzt alle Zaehler zurueck.
     */
    public void reset() {
        requests.clear();
        scripted.clear();
        responseDelay = Duration.ZERO;
    }
}
