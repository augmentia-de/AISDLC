package com.echobox.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Die Parameterbindung der JDBC-Adapter.
 *
 * <p>Der PostgreSQL-Treiber kann einen {@link Instant} ueber {@code setObject}
 * **nicht** ableiten ("der Datentyp kann nicht abgeleitet werden"). Alle
 * Zeitpunkte werden deshalb als {@link OffsetDateTime} in UTC gebunden — der
 * Zeitpunkt bleibt damit unabhaengig von der Zeitzone des Betriebssystems
 * exakt erhalten.
 *
 * <p>Die Umwandlung passiert an **einer** Stelle: die Adapter erzeugen ihre
 * Parameter ueber {@link #source()} und muessen sich nicht um die Typbindung
 * kuemmern.
 */
public final class SqlParameters {

    private SqlParameters() {
        // statische Helferklasse
    }

    /**
     * Erzeugt eine Parameterquelle, die Zeitpunkte in UTC bindet.
     *
     * @return die Parameterquelle
     */
    public static MapSqlParameterSource source() {
        return new MapSqlParameterSource() {
            @Override
            public MapSqlParameterSource addValue(String parameterName, Object value) {
                return super.addValue(parameterName, convert(value));
            }
        };
    }

    /**
     * Wandelt einen Zeitpunkt in eine bindbare Form um.
     *
     * @param value der Wert
     * @return der bindbare Wert
     */
    public static Object convert(Object value) {
        if (value instanceof Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        return value;
    }
}
