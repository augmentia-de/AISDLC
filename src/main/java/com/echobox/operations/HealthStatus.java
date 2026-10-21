package com.echobox.operations;

import java.time.Instant;

/**
 * Die Liveness-Antwort des Dienstes (Vertrag: {@code components.schemas.
 * HealthStatus}, AC-023-1).
 *
 * @param status     immer {@code up} — die Antwort existiert nur im laufenden Zustand
 * @param service    der Name des Dienstes
 * @param version    die Version des Dienstes
 * @param checked_at der Zeitpunkt der Pruefung
 */
public record HealthStatus(String status, String service, String version, Instant checked_at) {
}
