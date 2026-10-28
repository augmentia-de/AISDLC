# Story: US-002 — Ticket-Detail abrufen
# Anforderungen: FR-005, FR-019

Feature: Ticket-Detail abrufen

  Scenario: Existierendes Ticket abrufen  # AC-002-1
    Given ein Ticket existiert mit ID T
    When GET /api/v1/tickets/{T}
    Then Antwort 200 OK mit Ticket-Details

  Scenario: Unbekannte Ticket-ID  # AC-002-2
    Given kein Ticket mit ID X
    When GET /api/v1/tickets/{X}
    Then Antwort 404 Not Found mit strukturierter Fehlermeldung