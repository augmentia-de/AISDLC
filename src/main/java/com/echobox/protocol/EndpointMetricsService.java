package com.echobox.protocol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.echobox.common.ClockPort;
import com.echobox.common.NotFoundException;
import com.echobox.config.EchoBoxProperties;
import com.echobox.delivery.DeliveryRepository;
import com.echobox.endpoint.EndpointRepository;
import com.echobox.protocol.api.EndpointMetricsResponse;
import com.echobox.protocol.api.MetricsWindowResponse;
import com.echobox.protocol.api.RateLimitObservationResponse;

/**
 * Die Kennzahlen eines Endpunkts (FR-032, US-017).
 *
 * <p><strong>Erfolgsquote</strong> = {@code attempts_successful /
 * attempts_total} im Auswertungsfenster; <strong>Erfolg</strong> ist ein
 * Versuch mit Ergebnis {@code success} (Antwort 2xx). Die
 * <strong>Zustelllatenz</strong> ist das p95 der Dauer von der
 * <strong>Annahme</strong> des Ereignisses bis zur <strong>erfolgreichen</strong>
 * Antwort (ADR-011).
 *
 * <p><strong>Ohne Daten im Fenster</strong> gilt {@code data_available:
 * false}; die Erfolgsquote ist dann {@code null} — <strong>nicht</strong> 0 %
 * und <strong>nicht</strong> 100 % (AC-017-3). „Nichts passiert“ und „alles
 * schlug fehl“ sind verschiedene Aussagen.
 *
 * <p><strong>Mandant vor allem anderen:</strong> ein Endpunkt eines
 * <strong>fremden</strong> Mandanten ergibt <strong>404</strong> — auch fuer
 * den {@code service_admin} (D-02, V-27). Die Existenz fremder Endpunkte
 * bleibt verborgen.
 *
 * <p>Der Abschnitt {@code rate_limit} ist <strong>immer</strong> Teil der
 * Antwort: die Zaehlung laeuft unabhaengig vom Grenzwert (AC-018-4, V-42).
 */
@Service
public class EndpointMetricsService {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointMetricsService.class);

    /**
     * Das Vorgabefenster der Kennzahlen: die letzten <strong>7 Tage</strong> bis
     * jetzt (ADR-011, bestaetigt am 2026-10-21). Der Wert ist im Vertrag
     * festgeschrieben und damit <strong>kein</strong> Betriebswert der
     * Konfiguration.
     */
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    /** Die Nachkommastellen der Erfolgsquote. */
    private static final int RATE_SCALE = 4;

    private final DeliveryRepository deliveryRepository;

    private final EndpointRepository endpointRepository;

    private final ClockPort clock;

    private final int defaultLimit;

    private final int defaultWindowSeconds;

    private final String defaultStrategy;

    /**
     * Erzeugt die Kennzahlen.
     *
     * @param deliveryRepository der JDBC-Adapter des Zustellmoduls (Versuche, Limits)
     * @param endpointRepository der JDBC-Adapter des Endpunktmoduls (Mandantpruefung)
     * @param clock              die Uhr des Dienstes
     * @param properties         die Betriebskonfiguration (Vorgaben des Endpunkt-Limits)
     */
    public EndpointMetricsService(DeliveryRepository deliveryRepository,
                                  EndpointRepository endpointRepository,
                                  ClockPort clock,
                                  EchoBoxProperties properties) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.clock = clock;
        this.defaultLimit = properties.getDelivery().getRateLimit().getLimit();
        this.defaultWindowSeconds = properties.getDelivery().getRateLimit().getWindowSeconds();
        this.defaultStrategy = properties.getDelivery().getRateLimit().getStrategy();
    }

    /**
     * Liefert die Kennzahlen eines Endpunkts im Auswertungsfenster.
     *
     * @param organisationId der Mandant aus der Identitaet
     * @param endpointId     der Endpunkt
     * @param from           der Beginn des Fensters (nullable = Vorgabefenster)
     * @param to             das Ende des Fensters (nullable = jetzt)
     * @return die Kennzahlen
     * @throws NotFoundException wenn der Endpunkt im Mandanten nicht existiert
     */
    public EndpointMetricsResponse metrics(String organisationId, UUID endpointId, Instant from, Instant to) {
        endpointRepository.findById(organisationId, endpointId).orElseThrow(() -> {
            LOG.info("Endpunkt {} existiert im Mandanten nicht.", endpointId);
            return new NotFoundException();
        });

        Instant windowTo = to == null ? clock.now() : to;
        Instant windowFrom = from == null ? windowTo.minus(DEFAULT_WINDOW) : from;

        DeliveryRepository.EndpointMetricsRow row =
                deliveryRepository.metrics(organisationId, endpointId, windowFrom, windowTo);
        DeliveryRepository.RateLimitSetting setting =
                deliveryRepository.rateLimitSetting(organisationId, endpointId).orElse(null);
        DeliveryRepository.RateLimitCounters counters =
                deliveryRepository.rateLimitCounters(organisationId, endpointId, windowFrom, windowTo);

        long total = row.attemptsTotal();
        long successful = row.attemptsSuccessful();
        boolean dataAvailable = total > 0;

        return new EndpointMetricsResponse(
                endpointId.toString(),
                new MetricsWindowResponse(windowFrom, windowTo),
                dataAvailable,
                dataAvailable ? total : null,
                dataAvailable ? successful : null,
                dataAvailable ? successRate(successful, total) : null,
                dataAvailable ? row.latencyP95Ms() : null,
                rateLimit(setting, counters));
    }

    /**
     * Die Erfolgsquote — auf vier Nachkommastellen gerundet.
     *
     * @param successful die Zahl der erfolgreichen Versuche
     * @param total      die Zahl aller Versuche
     * @return die Quote oder {@code null}, wenn es keine Versuche gab
     */
    private static Double successRate(long successful, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(successful)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Die Beobachtung des Endpunkt-Limits.
     *
     * <p><strong>{@code limit} ist nie {@code null}</strong>: ohne Override des
     * Endpunkts gilt die Vorgabe der Betriebskonfiguration (F-01), ein
     * Override von {@code 0} schaltet die Begrenzung ab (ADR-010 §3).
     *
     * @param setting  die Einstellung des Endpunkts (nullable = keine Zeile)
     * @param counters die Zaehlung im Fenster
     * @return die Beobachtung
     */
    private RateLimitObservationResponse rateLimit(DeliveryRepository.RateLimitSetting setting,
                                                   DeliveryRepository.RateLimitCounters counters) {
        int limit = setting != null && setting.limitValue() != null
                ? setting.limitValue()
                : defaultLimit;
        int windowSeconds = setting != null ? setting.windowSeconds() : defaultWindowSeconds;
        String strategy = setting != null && setting.strategy() != null
                ? setting.strategy().wireValue()
                : defaultStrategy;
        return new RateLimitObservationResponse(
                limit,
                limit > 0,
                windowSeconds,
                strategy,
                counters.observedAttempts(),
                counters.delayedAttempts());
    }
}
