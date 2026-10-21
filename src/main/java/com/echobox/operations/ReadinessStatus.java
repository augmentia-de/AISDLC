package com.echobox.operations;

import java.time.Instant;

/**
 * Die Bereitschafts-Antwort des **bereiten** Zustands (Vertrag:
 * {@code components.schemas.ReadinessStatus}, AC-023-1).
 *
 * <p>Der **nicht bereite** Zustand wird **nicht** als Readiness-Bericht
 * geliefert, sondern als **HTTP 503** mit dem Fehlerschema {@code Problem}
 * ({@code code: service_unavailable}) — fuer alle Fehlerantworten gilt **ein**
 * Schema (ADR-015 §4, V-41).
 *
 * @param status     immer {@code ready}
 * @param checks     die Einzelpruefungen
 * @param checked_at der Zeitpunkt der Pruefung
 */
public record ReadinessStatus(String status, Checks checks, Instant checked_at) {

    /**
     * Die Einzelpruefungen — im HTTP 200 sind sie alle {@code ok}.
     *
     * @param database       die Datenbank ist erreichbar
     * @param delivery_worker der Zustell-Hintergrundprozess ist verfuegbar
     * @param tenant_context der Mandantenkontext ist verfuegbar
     */
    public record Checks(String database, String delivery_worker, String tenant_context) {
    }
}
