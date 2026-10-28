# Story: US-005 — Ticket-Liste abrufen
# Anforderungen: FR-008, FR-012, FR-013, NFR-001

Feature: Ticket-Liste abrufen

  Scenario: Alle Tickets auflisten  # AC-005-1
    Given 3 Tickets existieren
    When GET /api/v1/tickets
    Then Antwort 200 OK mit Liste von 3 Tickets sortiert nach created_at absteigend

  Scenario: Leere Liste  # AC-005-2
    Given keine Tickets existieren
    When GET /api/v1/tickets
    Then Antwort 200 OK mit leerer Liste

  Scenario: Pagination  # AC-005-3
    Given 60 Tickets existieren
    When GET /api/v1/tickets?limit=10&offset=0
    Then Antwort 200 OK mit 10 Tickets und total=60