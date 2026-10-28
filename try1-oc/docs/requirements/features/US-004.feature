# Story: US-004 — Status aendern
# Anforderungen: FR-007, FR-018, FR-019, FR-020

Feature: Status aendern

  Scenario: Status auf in_progress setzen  # AC-004-1
    Given ein Ticket mit Status open
    When PATCH /api/v1/tickets/{id} mit status=in_progress
    Then Antwort 200 OK mit status=in_progress

  Scenario: Ungueltiger Statuswert  # AC-004-2
    Given ein Ticket existiert
    When PATCH /api/v1/tickets/{id} mit status='done'
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung

  Scenario: Unbekannte Ticket-ID beim Statuswechsel  # AC-004-3
    Given kein Ticket mit ID X
    When PATCH /api/v1/tickets/{X} mit status=closed
    Then Antwort 404 Not Found mit strukturierter Fehlermeldung