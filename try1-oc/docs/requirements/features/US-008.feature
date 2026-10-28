# Story: US-008 — Kommentare auflisten
# Anforderungen: FR-016, FR-019

Feature: Kommentare auflisten

  Scenario: Kommentare eines Tickets  # AC-008-1
    Given ein Ticket mit 2 Kommentaren
    When GET /api/v1/tickets/{id}/comments
    Then Antwort 200 OK mit 2 Kommentaren sortiert nach created_at aufsteigend

  Scenario: Keine Kommentare  # AC-008-2
    Given ein Ticket ohne Kommentare
    When GET /api/v1/tickets/{id}/comments
    Then Antwort 200 OK mit leerer Liste

  Scenario: Kommentare zu unbekanntem Ticket  # AC-008-3
    Given kein Ticket mit ID X
    When GET /api/v1/tickets/{X}/comments
    Then Antwort 404 Not Found mit strukturierter Fehlermeldung