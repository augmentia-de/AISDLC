# Story: US-013 — Tote Zustellung erneut auslösen
# Quelle: docs/Anforderung.md — Abschnitt 8 (FR-024), Abschnitt 13 (Replay-Berechtigung)
# Anforderungen: FR-024
# Offene Punkte: O-03

@US-013 @FR-024
Feature: Tote Zustellung erneut auslösen
  Als Empfänger-Admin möchte ich eine aufgegebene Zustellung erneut auslösen können, damit ich ein nach einem Ausfall verpasstes Ereignis doch noch verarbeiten kann.

  Scenario: Normalfall — Replay startet neuen Zustelllauf  # Normalfall
    Given eine tote Zustellung zu einem aktiven Endpunkt
    When der Empfänger-Admin löst sie erneut aus
    Then ein neuer Zustelllauf startet mit Versuchsanzahl 1
    And der ursprüngliche Datensatz bleibt im Protokoll erhalten

  Scenario: Verweigerungsfall — unberechtigte Rolle  # Verweigerungsfall
    Given eine tote Zustellung
    When eine nicht berechtigte Rolle sie erneut auslöst
    Then der Dienst antwortet mit HTTP 403 und es startet kein Zustelllauf

  Scenario: Grenzfall — Replay bei pausiertem Endpunkt  # Grenzfall
    Given eine tote Zustellung zu einem pausierten Endpunkt
    When sie erneut ausgelöst wird
    Then wird kein Versuch gestartet
