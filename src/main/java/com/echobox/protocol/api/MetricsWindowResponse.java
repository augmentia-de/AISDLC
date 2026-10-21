package com.echobox.protocol.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Das Auswertungsfenster der Kennzahlen — die Projektion des Vertrags
 * {@code components.schemas.MetricsWindow}.
 *
 * <p>Ohne {@code from}/{@code to} gilt das Fenster der letzten <strong>7
 * Tage</strong> bis jetzt (ADR-011, bestaetigt am 2026-10-21).
 *
 * @param from der Beginn des Fensters (inklusive)
 * @param to   das Ende des Fensters (exklusive)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricsWindowResponse(
        @JsonProperty("from") Instant from,
        @JsonProperty("to") Instant to) {
}
