# Story: US-001 — Ticket anlegen
# Anforderungen: FR-001, FR-002, FR-003, FR-004, FR-017

Feature: Ticket anlegen

  Scenario: Ticket mit allen Feldern anlegen  # AC-001-1
    Given keine Tickets existieren
    When POST /api/v1/tickets mit Titel "Login failed", Prioritaet "high" und Beschreibung "cannot log in"
    Then Antwort 201 Created mit Ticket (id, title, description, priority=high, status=open, created_at, updated_at)

  Scenario: Fehlender Titel  # AC-001-2
    Given eine Create-Anfrage
    When POST /api/v1/tickets ohne Titel
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung

  Scenario: Titel zu lang  # AC-001-3
    Given eine Create-Anfrage
    When POST /api/v1/tickets mit Titel laenger als 200 Zeichen
    Then Antwort 400 Bad Request mit strukturierter Fehlermeldung

  Scenario: Default-Prioritaet medium  # AC-001-4
    Given eine Create-Anfrage
    When POST /api/v1/tickets nur mit Titel (ohne Prioritaet)
    Then Antwort 201 Created mit priority=medium

  Scenario: Fehlende Beschreibung  # AC-001-5
    Given eine Create-Anfrage
    When POST /api/v1/tickets nur mit Titel (ohne Beschreibung)
    Then Antwort 201 Created mit description null oder leer