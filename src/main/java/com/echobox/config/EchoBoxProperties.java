package com.echobox.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Die gesamte Betriebskonfiguration des Dienstes.
 *
 * <p>Grundsaetze (ADR-009, ADR-015, F-01…F-04):
 * <ul>
 *   <li><strong>Kein Zahlwert ist im Code verdrahtet.</strong> Backoff,
 *       Versuchsgrenze, Timeouts, Rate-Limit, Aufbewahrung und Mandantenbezug
 *       kommen ausschliesslich aus der Konfiguration.</li>
 *   <li><strong>Keine Geheimnisse in der Konfiguration.</strong> Die Schluessel
 *       kommen aus der Umgebung (Praefix {@code ECHobox_}); in der Datei stehen
 *       nur die Variablennamen und Betriebswerte.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "echobox")
public class EchoBoxProperties {

    private final Crypto crypto = new Crypto();

    private final Security security = new Security();

    private final Tenant tenant = new Tenant();

    private final Delivery delivery = new Delivery();

    private final Retention retention = new Retention();

    private final ApiRateLimit rateLimit = new ApiRateLimit();

    /**
     * Liefert die Einstellungen des API-Anfragelimits (S-24).
     *
     * @return die Einstellungen des Anfragelimits
     */
    public ApiRateLimit getRateLimit() {
        return rateLimit;
    }

    /**
     * Liefert die Kryptografie-Einstellungen.
     *
     * @return die Kryptografie-Einstellungen
     */
    public Crypto getCrypto() {
        return crypto;
    }

    /**
     * Liefert die Sicherheitseinstellungen.
     *
     * @return die Sicherheitseinstellungen
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * Liefert die Mandanteneinstellungen.
     *
     * @return die Mandanteneinstellungen
     */
    public Tenant getTenant() {
        return tenant;
    }

    /**
     * Liefert die Zustelleinstellungen.
     *
     * @return die Zustelleinstellungen
     */
    public Delivery getDelivery() {
        return delivery;
    }

    /**
     * Liefert die Aufbewahrungseinstellungen.
     *
     * @return die Aufbewahrungseinstellungen
     */
    public Retention getRetention() {
        return retention;
    }

    /** Die Verschluesselung im Ruhezustand (ADR-003). */
    public static class Crypto {

        private String key = "";

        private boolean keyRequired;

        /**
         * Liefert der Verschluesselungsschluessel (Base64 oder Hex).
         *
         * @return der Schluessel
         */
        public String getKey() {
            return key;
        }

        /**
         * Setzt den Verschluesselungsschluessel.
         *
         * @param key der Schluessel
         */
        public void setKey(String key) {
            this.key = key;
        }

        /**
         * Liefert, ob der Schluessel **Pflicht** ist (Staging und Produktion).
         *
         * @return {@code true}, wenn der Dienst ohne Schluessel nicht starten darf
         */
        public boolean isKeyRequired() {
            return keyRequired;
        }

        /**
         * Setzt, ob der Schluessel Pflicht ist.
         *
         * @param keyRequired die Pflicht
         */
        public void setKeyRequired(boolean keyRequired) {
            this.keyRequired = keyRequired;
        }
    }

    /** Die Anmeldung und die Cursor. */
    public static class Security {

        private String jwkSetUri = "";

        private String cursorKey = "";

        /**
         * Die Zielgruppe ({@code aud}), die ein Token tragen muss (S-21).
         *
         * <p>Leer: die Zielgruppe wird nicht geprueft (nur fuer Betriebe ohne
         * Identity Provider mit eigener Audience-Politik).
         */
        private String audience = "echobox";

        /**
         * Liefert die JWKS-Adresse des Identity Providers.
         *
         * @return die JWKS-Adresse
         */
        public String getJwkSetUri() {
            return jwkSetUri;
        }

        /**
         * Setzt die JWKS-Adresse.
         *
         * @param jwkSetUri die Adresse
         */
        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        /**
         * Liefert die erwartete Zielgruppe der Token.
         *
         * @return die Zielgruppe
         */
        public String getAudience() {
            return audience;
        }

        /**
         * Setzt die erwartete Zielgruppe der Token.
         *
         * @param audience die Zielgruppe
         */
        public void setAudience(String audience) {
            this.audience = audience;
        }

        /**
         * Liefert der Signaturschluessel der opaken Cursor.
         *
         * @return der Schluessel
         */
        public String getCursorKey() {
            return cursorKey;
        }

        /**
         * Setzt den Signaturschluessel der opaken Cursor.
         *
         * @param cursorKey der Schluessel
         */
        public void setCursorKey(String cursorKey) {
            this.cursorKey = cursorKey;
        }
    }

    /** Die Mandanteninstanz (ADR-013). */
    public static class Tenant {

        private String id = "";

        /**
         * Liefert die Kennung der Mandanteninstanz (leer = geteilter Betrieb).
         *
         * @return die Kennung
         */
        public String getId() {
            return id;
        }

        /**
         * Setzt die Kennung der Mandanteninstanz.
         *
         * @param id die Kennung
         */
        public void setId(String id) {
            this.id = id;
        }
    }

    /** Die Zustellung: Backoff, Versuchsgrenze, Timeouts, Rate-Limit. */
    public static class Delivery {

        private final Worker worker = new Worker();

        private final Backoff backoff = new Backoff();

        private final RateLimit rateLimit = new RateLimit();

        private final Http http = new Http();

        private final Replay replay = new Replay();

        private int maxAttempts = 8;

        /**
         * Liefert die Einstellungen des Hintergrundprozesses.
         *
         * @return die Einstellungen des Hintergrundprozesses
         */
        public Worker getWorker() {
            return worker;
        }

        /**
         * Liefert die Backoff-Einstellungen.
         *
         * @return die Backoff-Einstellungen
         */
        public Backoff getBackoff() {
            return backoff;
        }

        /**
         * Liefert die Rate-Limit-Einstellungen.
         *
         * @return die Rate-Limit-Einstellungen
         */
        public RateLimit getRateLimit() {
            return rateLimit;
        }

        /**
         * Liefert die HTTP-Einstellungen der Zustellung.
         *
         * @return die HTTP-Einstellungen
         */
        public Http getHttp() {
            return http;
        }

        /**
         * Liefert die Einstellungen des Replays.
         *
         * @return die Replay-Einstellungen
         */
        public Replay getReplay() {
            return replay;
        }

        /**
         * Liefert die Versuchsgrenze (Vorgabe F-04: 8 Versuche).
         *
         * @return die Versuchsgrenze
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * Setzt die Versuchsgrenze.
         *
         * @param maxAttempts die Versuchsgrenze
         */
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        /**
         * Die Zeitlimits der Replay-Transaktion (ADR-006 §6, N-20).
         *
         * <p>Beide Grenzen sind <strong>kuerzer</strong> als das HTTP-Timeout:
         * der verlierende Replay antwortet damit mit HTTP 409
         * ({@code delivery_state_conflict}) statt mit einem Timeout.
         */
        public static class Replay {

            private Duration lockTimeout = Duration.ofSeconds(2);

            private Duration statementTimeout = Duration.ofSeconds(5);

            /**
             * Liefert die Sperrenwartezeit der Replay-Transaktion.
             *
             * @return die Sperrenwartezeit
             */
            public Duration getLockTimeout() {
                return lockTimeout;
            }

            /**
             * Setzt die Sperrenwartezeit der Replay-Transaktion.
             *
             * @param lockTimeout die Sperrenwartezeit
             */
            public void setLockTimeout(Duration lockTimeout) {
                this.lockTimeout = lockTimeout;
            }

            /**
             * Liefert das Statement-Timeout der Replay-Transaktion.
             *
             * @return das Statement-Timeout
             */
            public Duration getStatementTimeout() {
                return statementTimeout;
            }

            /**
             * Setzt das Statement-Timeout der Replay-Transaktion.
             *
             * @param statementTimeout das Statement-Timeout
             */
            public void setStatementTimeout(Duration statementTimeout) {
                this.statementTimeout = statementTimeout;
            }
        }

        /** Der Hintergrundprozess der Zustellung. */
        public static class Worker {

            private boolean enabled = true;

            private Duration pollInterval = Duration.ofSeconds(1);

            private Duration leaseDuration = Duration.ofMinutes(1);

            private int batchSize = 50;

            /**
             * Liefert, ob der Hintergrundprozess laeuft.
             *
             * @return {@code true}, wenn der Worker laeuft
             */
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * Setzt, ob der Hintergrundprozess laeuft.
             *
             * @param enabled die Schalterstellung
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * Liefert das Polling-Intervall.
             *
             * @return das Intervall
             */
            public Duration getPollInterval() {
                return pollInterval;
            }

            /**
             * Setzt das Polling-Intervall.
             *
             * @param pollInterval das Intervall
             */
            public void setPollInterval(Duration pollInterval) {
                this.pollInterval = pollInterval;
            }

            /**
             * Liefert die Dauer der Beanspruchung (Lease).
             *
             * @return die Lease-Dauer
             */
            public Duration getLeaseDuration() {
                return leaseDuration;
            }

            /**
             * Setzt die Dauer der Beanspruchung.
             *
             * @param leaseDuration die Dauer
             */
            public void setLeaseDuration(Duration leaseDuration) {
                this.leaseDuration = leaseDuration;
            }

            /**
             * Liefert die Zahl der Zustelllaeufe, die je Mandant und Durchlauf
             * beansprucht werden.
             *
             * @return die Stapelgroesse
             */
            public int getBatchSize() {
                return batchSize;
            }

            /**
             * Setzt die Zahl der beanspruchten Zustelllaeufe je Durchlauf.
             *
             * @param batchSize die Stapelgroesse
             */
            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }
        }

        /** Der Backoff: Verdopplung, Deckel, Streuung (F-04). */
        public static class Backoff {

            private Duration baseDelay = Duration.ofMinutes(1);

            private double multiplier = 2.0;

            private Duration maxDelay = Duration.ofMinutes(60);

            private double jitter = 0.2;

            /**
             * Liefert den Basisabstand.
             *
             * @return der Basisabstand
             */
            public Duration getBaseDelay() {
                return baseDelay;
            }

            /**
             * Setzt den Basisabstand.
             *
             * @param baseDelay der Basisabstand
             */
            public void setBaseDelay(Duration baseDelay) {
                this.baseDelay = baseDelay;
            }

            /**
             * Liefert den Verdopplungsfaktor.
             *
             * @return der Faktor
             */
            public double getMultiplier() {
                return multiplier;
            }

            /**
             * Setzt den Verdopplungsfaktor.
             *
             * @param multiplier der Faktor
             */
            public void setMultiplier(double multiplier) {
                this.multiplier = multiplier;
            }

            /**
             * Liefert den Deckel.
             *
             * @return der Deckel
             */
            public Duration getMaxDelay() {
                return maxDelay;
            }

            /**
             * Setzt den Deckel.
             *
             * @param maxDelay der Deckel
             */
            public void setMaxDelay(Duration maxDelay) {
                this.maxDelay = maxDelay;
            }

            /**
             * Liefert die Streuung (0.2 = +/- 20 %).
             *
             * @return die Streuung
             */
            public double getJitter() {
                return jitter;
            }

            /**
             * Setzt die Streuung.
             *
             * @param jitter die Streuung
             */
            public void setJitter(double jitter) {
                this.jitter = jitter;
            }
        }

        /** Das Rate-Limit je Endpunkt (F-01). */
        public static class RateLimit {

            private int limit = 60;

            private int windowSeconds = 60;

            private String strategy = "delay";

            /**
             * Liefert den Grenzwert (Vorgabe F-01: 60 je Endpunkt und Minute).
             *
             * @return der Grenzwert
             */
            public int getLimit() {
                return limit;
            }

            /**
             * Setzt den Grenzwert.
             *
             * @param limit der Grenzwert
             */
            public void setLimit(int limit) {
                this.limit = limit;
            }

            /**
             * Liefert die Laenge des Zaehlfensters in Sekunden.
             *
             * @return die Fensterlaenge
             */
            public int getWindowSeconds() {
                return windowSeconds;
            }

            /**
             * Setzt die Laenge des Zaehlfensters.
             *
             * @param windowSeconds die Fensterlaenge
             */
            public void setWindowSeconds(int windowSeconds) {
                this.windowSeconds = windowSeconds;
            }

            /**
             * Liefert die Strategie ({@code delay} oder {@code block}).
             *
             * @return die Strategie
             */
            public String getStrategy() {
                return strategy;
            }

            /**
             * Setzt die Strategie.
             *
             * @param strategy die Strategie
             */
            public void setStrategy(String strategy) {
                this.strategy = strategy;
            }
        }

        /** Die Timeouts der ausgehenden Zustellung (ADR-008). */
        public static class Http {

            private Duration connectTimeout = Duration.ofSeconds(5);

            private Duration readTimeout = Duration.ofSeconds(10);

            /** Die Hoechstgroesse des gelesenen Antwortkoerpers (S-23). */
            private int maxResponseBodyBytes = 1024 * 1024;

            /**
             * Liefert den Verbindungs-Timeout.
             *
             * @return der Verbindungs-Timeout
             */
            public Duration getConnectTimeout() {
                return connectTimeout;
            }

            /**
             * Setzt den Verbindungs-Timeout.
             *
             * @param connectTimeout der Verbindungs-Timeout
             */
            public void setConnectTimeout(Duration connectTimeout) {
                this.connectTimeout = connectTimeout;
            }

            /**
             * Liefert den Lese-Timeout.
             *
             * @return der Lese-Timeout
             */
            public Duration getReadTimeout() {
                return readTimeout;
            }

            /**
             * Setzt den Lese-Timeout.
             *
             * @param readTimeout der Lese-Timeout
             */
            public void setReadTimeout(Duration readTimeout) {
                this.readTimeout = readTimeout;
            }

            /**
             * Liefert die Hoechstgroesse des gelesenen Antwortkoerpers.
             *
             * <p>Der Antwortkoerper eines Empfaengers ist vertragsseitig
             * bedeutungslos — nur Statuscode und Ablehnungssignal zaehlen. Die
             * Grenze schuetzt den Zustell-Worker vor einem boesartigen
             * Empfaenger (S-23).
             *
             * @return die Grenze in Byte
             */
            public int getMaxResponseBodyBytes() {
                return maxResponseBodyBytes;
            }

            /**
             * Setzt die Hoechstgroesse des gelesenen Antwortkoerpers.
             *
             * @param maxResponseBodyBytes die Grenze in Byte
             */
            public void setMaxResponseBodyBytes(int maxResponseBodyBytes) {
                this.maxResponseBodyBytes = maxResponseBodyBytes;
            }
        }
    }

    /** Die Aufbewahrung (F-02). */
    public static class Retention {
        private int eventDays = 30;

        private int deliveryDays = 30;

        private Integer purgeDelay;

        /**
         * Liefert die Frist fuer Ereignisse in Tagen (Vorgabe F-02: 30).
         *
         * @return die Frist
         */
        public int getEventDays() {
            return eventDays;
        }

        /**
         * Setzt die Frist fuer Ereignisse.
         *
         * @param eventDays die Frist
         */
        public void setEventDays(int eventDays) {
            this.eventDays = eventDays;
        }

        /**
         * Liefert die Frist fuer Zustellprotokolle in Tagen (Vorgabe F-02: 30).
         *
         * @return die Frist
         */
        public int getDeliveryDays() {
            return deliveryDays;
        }

        /**
         * Setzt die Frist fuer Zustellprotokolle.
         *
         * @param deliveryDays die Frist
         */
        public void setDeliveryDays(int deliveryDays) {
            this.deliveryDays = deliveryDays;
        }

        /**
         * Liefert die Karenzzeit zwischen Markieren und Purge in Tagen.
         *
         * <p><strong>Es gibt keinen Vorgabewert</strong> (R-01, E-13): ohne
         * gesetzten Wert rechnet der Dienst mit 0 Tagen — der Purge erfolgt
         * dann im naechsten Durchlauf. Das ist das dokumentierte Verhalten
         * <strong>ohne</strong> Wert, kein beschlossener Vorgabewert.
         *
         * @return die Karenzzeit oder {@code null}, wenn keine gesetzt ist
         */
        public Integer getPurgeDelay() {
            return purgeDelay;
        }

        /**
         * Setzt die Karenzzeit.
         *
         * @param purgeDelay die Karenzzeit
         */
        public void setPurgeDelay(Integer purgeDelay) {
            this.purgeDelay = purgeDelay;
        }
    }

    /**
     * Das Anfragelimit der API (S-24) — die Zusage des eingefrorenen Vertrags
     * (HTTP 429 {@code rate_limit_exceeded} mit {@code Retry-After}).
     *
     * <p>Der Zaehler gilt je Mandant. Das Endpunkt-Limit aus FR-033 steht
     * weiterhin unter {@code echobox.delivery.rate-limit}.
     */
    public static class ApiRateLimit {

        private int limit = 240;

        private int windowSeconds = 60;

        /**
         * Liefert die Zahl der erlaubten Anfragen je Mandant und Fenster.
         *
         * @return die Grenze
         */
        public int getLimit() {
            return limit;
        }

        /**
         * Setzt die Zahl der erlaubten Anfragen je Mandant und Fenster.
         *
         * @param limit die Grenze
         */
        public void setLimit(int limit) {
            this.limit = limit;
        }

        /**
         * Liefert die Laenge des Fensters in Sekunden.
         *
         * @return die Fensterlaenge
         */
        public int getWindowSeconds() {
            return windowSeconds;
        }

        /**
         * Setzt die Laenge des Fensters in Sekunden.
         *
         * @param windowSeconds die Fensterlaenge
         */
        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
