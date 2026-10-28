# Story: US-009 — Eingabevalidierung und Fehlerbehandlung
# Anforderungen: FR-020, NFR-002, SEC-002

Feature: Eingabevalidierung und Fehlerbehandlung

  Scenario: Ungueltige Prioritaet  # AC-009-1
    Given eine Create-Anfrage
    When POST /api/v1/tickets mit priority='urgent'
    Then Antwort 400 Bad Request mit Feldfehler fuer priority

  Scenario: Ungueltiges JSON  # AC-009-2
    Given eine Create-Anfrage
    When POST /api/v1/tickets mit ungueltigem JSON-Body
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung

  Scenario: Einheitliches Fehler-Schema  # AC-009-3
    Given ein Validierungsfehler
    When die API mit 4xx antwortet
    Then Response-Body folgt dem Schema {error, message, details}