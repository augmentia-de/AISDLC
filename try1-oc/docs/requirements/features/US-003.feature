# Story: US-003 — Ticket aktualisieren
# Anforderungen: FR-006, FR-019, FR-020

Feature: Ticket aktualisieren

  Scenario: Titel aktualisieren  # AC-003-1
    Given ein Ticket existiert
    When PATCH /api/v1/tickets/{id} mit neuem Titel
    Then Antwort 200 OK mit aktualisiertem Ticket und geaendertem updated_at

  Scenario: Ungueltiger Titel  # AC-003-2
    Given ein Ticket existiert
    When PATCH /api/v1/tickets/{id} mit leerem Titel
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung

  Scenario: Unbekannte Ticket-ID beim Update  # AC-003-3
    Given kein Ticket mit ID X
    When PATCH /api/v1/tickets/{X}
    Then Antwort 404 Not Found mit strukturierter Fehlermeldung