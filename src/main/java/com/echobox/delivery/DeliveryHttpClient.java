package com.echobox.delivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der HTTP-Adapter des Zustellports.
 *
 * <p>Der Dienst sendet <strong>genau einmal</strong> an die registrierte
 * Ziel-URL: eine Umleitung (301, 302, 307, 308 …) wird <strong>nicht
 * gefolgt</strong> — die Ziel-URL ist Teil der Signier-Eingabe, ein Folgeort
 * wuerde die signierte URL aufweichen (D-08, I-10).
 *
 * <p>Die Timeouts stammen aus der Betriebskonfiguration
 * ({@code echobox.delivery.http.connect-timeout},
 * {@code echobox.delivery.http.read-timeout} — ADR-008: 5 s / 10 s). Sie
 * werden **hier nicht verdrahtet**: die Bean entsteht in
 * {@link DeliveryConfiguration} aus {@code EchoBoxProperties}.
 *
 * <p>TLS ist Standard: es wird <strong>kein</strong> Trust-all betrieben.
 */
public class DeliveryHttpClient implements DeliveryPort {

    private final HttpClient httpClient;

    private final Duration readTimeout;

    /**
     * Erzeugt den HTTP-Adapter.
     *
     * @param connectTimeout der Verbindungs-Timeout
     * @param readTimeout    der Lese-Timeout
     */
    public DeliveryHttpClient(Duration connectTimeout, Duration readTimeout) {
        this.readTimeout = readTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public DeliveryResponse deliver(DeliveryRequest request) {
        HttpRequest httpRequest = buildRequest(request);
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return new DeliveryResponse(response.statusCode(), response.body(), headersOf(response),
                    DeliveryResponse.TransportFailure.NONE);
        } catch (HttpTimeoutException e) {
            return transportFailure(DeliveryResponse.TransportFailure.TIMEOUT);
        } catch (IOException e) {
            return transportFailure(DeliveryResponse.TransportFailure.NETWORK_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return transportFailure(DeliveryResponse.TransportFailure.NETWORK_ERROR);
        }
    }

    private HttpRequest buildRequest(DeliveryRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.targetUrl()))
                .timeout(readTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.payload()));
        request.headers().forEach(builder::header);
        if (request.contentType() != null && !request.contentType().isBlank()) {
            builder.setHeader("Content-Type", request.contentType());
        }
        return builder.build();
    }

    private static DeliveryResponse transportFailure(DeliveryResponse.TransportFailure failure) {
        return new DeliveryResponse(null, "", Map.of(), failure);
    }

    private static Map<String, String> headersOf(HttpResponse<String> response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return headers;
    }
}
