# Story: US-016 — Tote Zustellungen abholen und Aufbewahrung durchsetzen
# Quelle: docs/Anforderung.md — Abschnitt 9 (FR-031), Abschnitt 3 (Rollen), Abschnitt 13 (Aufbewahrung)
# Anforderungen: FR-031
# Offene Punkte: O-02

@US-016 @FR-031
Feature: Tote Zustellungen abholen und Aufbewahrung durchsetzen
  Als Service-Admin möchte ich die toten Zustellungen aller Mandanten abholen und die Aufbewahrung der Daten durchsetzen, damit ich den Betrieb überblicke und die Daten nicht unbegrenzt wachsen.

  Scenario: Normalfall — Service-Admin holt die Totenliste  # Normalfall
    Given eine tote Zustellung in zwei Mandanten
    When ein Service-Admin die Liste abruft
    Then erhält er die toten Zustellungen beider Mandanten mit allen geforderten Feldern

  Scenario: Verweigerungsfall — Empfänger-Admin  # Verweigerungsfall
    Given ein Empfänger-Admin
    When er die mandantenübergreifende Totenliste abruft
    Then der Dienst antwortet mit HTTP 403

  Scenario: Grenzfall — leere Trefferliste  # Grenzfall
    Given ein Mandant ohne tote Zustellungen
    When die Liste abgerufen wird
    Then liefert der Dienst eine leere Liste ohne Fehler
