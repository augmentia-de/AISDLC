package com.echobox.delivery;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.echobox.config.EchoBoxProperties;

/**
 * Die Beans der Zustellung.
 *
 * <p><strong>Kein Zahlwert ist im Code verdrahtet</strong> (D-09, F-04): der
 * Backoff kommt aus der Betriebskonfiguration. Die Streuung nutzt eine
 * thread-sichere Zufallsquelle — der Backoff wird von mehreren Worker-Threads
 * gleichzeitig benutzt.
 */
@Configuration
public class DeliveryConfiguration {

    /**
     * Die Wartezeit zwischen zwei Versuchen (FR-022, ADR-008).
     *
     * @param properties die Betriebskonfiguration
     * @return der Backoff
     */
    @Bean
    public BackoffPolicy backoffPolicy(EchoBoxProperties properties) {
        EchoBoxProperties.Delivery.Backoff backoff = properties.getDelivery().getBackoff();
        return new BackoffPolicy(backoff.getBaseDelay(), backoff.getMultiplier(), backoff.getMaxDelay(),
                backoff.getJitter(), ThreadLocalRandom.current());
    }

    /**
     * Der HTTP-Adapter des Zustellports.
     *
     * <p><strong>Die Timeouts kommen aus der Betriebskonfiguration</strong>
     * ({@code echobox.delivery.http.connect-timeout},
     * {@code echobox.delivery.http.read-timeout} — ADR-008) und **nicht** aus
     * einem im Code verdrahteten Vorgabewert: {@link EchoBoxProperties} ist
     * die einzige Quelle, aus der alle Zahlenwerte der Zustellung stammen
     * (D-09).
     *
     * @param properties die Betriebskonfiguration
     * @return der HTTP-Adapter
     */
    @Bean
    public DeliveryHttpClient deliveryHttpClient(EchoBoxProperties properties) {
        EchoBoxProperties.Delivery.Http http = properties.getDelivery().getHttp();
        return new DeliveryHttpClient(http.getConnectTimeout(), http.getReadTimeout());
    }
}
