package com.echobox.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

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
 *
 * <p><strong>Die Antwort wird begrenzt gelesen (S-23).</strong> Der
 * Antwortkoerper eines Empfaengers ist vertragsseitig bedeutungslos — nur
 * Statuscode und Ablehnungssignal zaehlen. Ohne Grenze wuerde ein boesartiger
 * Empfaenger den Koerper aber vollstaendig in den Speicher des Zustell-Workers
 * laden. Der Koerper wird deshalb bis zur Grenze gelesen und der Rest
 * verworfen; die Verbindung wird weiterhin vollstaendig konsumiert (der
 * Request-Timeout bleibt damit wirksam und die Verbindung bleibt
 * wiederverwendbar).
 */
public class DeliveryHttpClient implements DeliveryPort {

    /**
     * Die Hoechstgroesse des Antwortkoerpers, der in den Speicher geladen wird:
     * 1 MiB (S-23). Groessere Antworten werden nach dieser Grenze verworfen.
     */
    public static final int DEFAULT_MAX_RESPONSE_BODY_BYTES = 1024 * 1024;

    private final HttpClient httpClient;

    private final Duration readTimeout;

    private final int maxResponseBodyBytes;

    /**
     * Erzeugt den HTTP-Adapter.
     *
     * @param connectTimeout der Verbindungs-Timeout
     * @param readTimeout    der Lese-Timeout
     */
    public DeliveryHttpClient(Duration connectTimeout, Duration readTimeout) {
        this(connectTimeout, readTimeout, DEFAULT_MAX_RESPONSE_BODY_BYTES);
    }

    /**
     * Erzeugt den HTTP-Adapter mit eigener Antwortgrenze.
     *
     * @param connectTimeout       der Verbindungs-Timeout
     * @param readTimeout          der Lese-Timeout
     * @param maxResponseBodyBytes die Hoechstgroesse des gelesenen Antwortkoerpers
     */
    public DeliveryHttpClient(Duration connectTimeout, Duration readTimeout, int maxResponseBodyBytes) {
        this.readTimeout = readTimeout;
        this.maxResponseBodyBytes = maxResponseBodyBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public DeliveryResponse deliver(DeliveryRequest request) {
        HttpRequest httpRequest = buildRequest(request);
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    responseInfo -> new LimitedBodySubscriber(maxResponseBodyBytes));
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

    /**
     * Der Abnehmer, der den Antwortkoerper bis zu einer Grenze liest und den
     * Rest verwirft (S-23).
     *
     * <p>Der Koerper wird <strong>vollstaendig konsumiert</strong> — nur die
     * Speicherung ist begrenzt. Das haelt den Request-Timeout wirksam und
     * laesst die Verbindung weiterhin wiederverwendbar.
     */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<String> {

        private final int limit;

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private final CompletableFuture<String> result = new CompletableFuture<>();

        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int limit) {
            this.limit = Math.max(0, limit);
        }

        @Override
        public CompletableFuture<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                int remaining = limit - buffer.size();
                if (remaining <= 0) {
                    // Ueber der Grenze: der Rest wird verworfen, aber weiterhin
                    // konsumiert — die Verbindung bleibt intakt.
                    item.position(item.limit());
                    continue;
                }
                int count = Math.min(remaining, item.remaining());
                byte[] chunk = new byte[count];
                item.get(chunk);
                buffer.write(chunk, 0, count);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
