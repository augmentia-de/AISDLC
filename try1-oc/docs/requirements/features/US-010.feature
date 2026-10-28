# Story: US-010 — Persistenz und Datenintegritaet
# Anforderungen: NFR-003, NFR-004

Feature: Persistenz und Datenintegritaet

  Scenario: Ticket ueberlebt Neustart  # AC-010-1
    Given ein Ticket wurde angelegt
    When der Server neu startet und GET /api/v1/tickets/{id} aufgerufen wird
    Then Antwort 200 OK mit demselben Ticket

  Scenario: Kommentar ueberlebt Neustart  # AC-010-2
    Given ein Kommentar wurde angelegt
    When der Server neu startet und GET /api/v1/tickets/{id}/comments aufgerufen wird
    Then Antwort 200 OK mit demselben Kommentar