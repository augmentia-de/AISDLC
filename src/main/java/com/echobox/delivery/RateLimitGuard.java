package com.echobox.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.echobox.config.EchoBoxProperties;

/**
 * Der Schutz eines Endpunkts gegen zu viele Zustellversuche in kurzer Zeit
 * (FR-033, D-14, ADR-010).
 *
 * <p><strong>Der Zaehler liegt in der Datenbank</strong>
 * ({@code app.endpoint_rate_limit_counters}): er ueberlebt einen Neustart,
 * er ist mandantenfest und ein zweiter Worker sieht dieselbe Zahl. Ein Zaehler
 * im Arbeitsspeicher waere eine zweite, abweichende Wahrheit (ADR-010,
 * Alternativen).
 *
 * <p><strong>Zwei Zustaende, keine drei</strong> (ADR-010 §1, E-02):
 * <ul>
 *   <li>{@code limit_value IS NULL} — <strong>kein Override</strong>: es gilt die
 *       Vorgabe aus der Betriebskonfiguration (60 Versuche je Endpunkt und
 *       Minute, F-01),</li>
 *   <li>{@code limit_value = 0} — <strong>abgeschaltet</strong>: es gibt keinen
 *       wirksamen Grenzwert; der Versuch wird <strong>nicht</strong>
 *       verschoben.</li>
 * </ul>
 *
 * <p><strong>Die Zaehlung laeuft immer</strong>, unabhaengig vom Grenzwert
 * (AC-018-4) — sie ist die Grundlage der Kennzahlen im Vertrag.
 */
@Component
public class RateLimitGuard {

    private final DeliveryRepository repository;

    private final EchoBoxProperties properties;

    /**
     * Erzeugt den Schutz.
     *
     * @param repository  der JDBC-Adapter
     * @param properties  die Betriebskonfiguration (Vorgabewerte)
     */
    public RateLimitGuard(DeliveryRepository repository, EchoBoxProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Registriert einen Versuch und liefert die Entscheidung.
     *
     * <p>Die Zaehlung wird **vor** der Entscheidung fortgeschrieben — der
     * Zaehler laeuft immer, auch fuer den abgeschalteten Grenzwert.
     *
     * @param organisationId der Mandant
     * @param endpointId     der Endpunkt
     * @param now            der Bezugszeitpunkt
     * @return die Entscheidung
     */
    public RateLimitDecision acquire(String organisationId, UUID endpointId, Instant now) {
        EchoBoxProperties.Delivery.RateLimit defaults = properties.getDelivery().getRateLimit();
        Optional<DeliveryRepository.RateLimitSetting> setting =
                repository.rateLimitSetting(organisationId, endpointId);

        int windowSeconds = setting.map(DeliveryRepository.RateLimitSetting::windowSeconds)
                .orElseGet(defaults::getWindowSeconds);
        int limit = setting.map(DeliveryRepository.RateLimitSetting::limitValue)
                .map(value -> value == null ? defaults.getLimit() : value)
                .orElseGet(defaults::getLimit);

        Optional<DeliveryRepository.RateLimitWindow> window =
                repository.rateLimitWindow(organisationId, endpointId, now, windowSeconds);
        Instant windowStart = window.map(DeliveryRepository.RateLimitWindow::windowStart).orElse(now);
        long observed = window.map(DeliveryRepository.RateLimitWindow::attempts).orElse(0L) + 1L;

        boolean exceeding = limit > 0 && observed > limit;
        repository.countRateLimitAttempt(organisationId, endpointId, windowStart, exceeding);

        return EndpointRateLimiter.decide(observed, limit, Duration.ofSeconds(windowSeconds), windowStart, now);
    }
}
