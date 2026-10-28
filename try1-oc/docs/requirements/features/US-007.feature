# Story: US-007 — Kommentar hinzufuegen
# Anforderungen: FR-014, FR-015, FR-019, FR-020, DP-001

Feature: Kommentar hinzufuegen

  Scenario: Kommentar anlegen  # AC-007-1
    Given ein Ticket existiert
    When POST /api/v1/tickets/{id}/comments mit Text "reproduced"
    Then Antwort 201 Created mit Kommentar (id, text, author, created_at)

  Scenario: Leerer Kommentartext  # AC-007-2
    Given ein Ticket existiert
    When POST /api/v1/tickets/{id}/comments mit leerem Text
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung

  Scenario: Kommentar zu unbekanntem Ticket  # AC-007-3
    Given kein Ticket mit ID X
    When POST /api/v1/tickets/{X}/comments
    Then Antwort 404 Not Found mit strukturierter Fehlermeldung

  Scenario: Kommentartext zu lang  # AC-007-4
    Given ein Ticket existiert
    When POST /api/v1/tickets/{id}/comments mit Text laenger als 2000 Zeichen
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung