# Story: US-017 — Erfolgsquote und Zustelllatenz je Endpunkt
# Quelle: docs/Anforderung.md — Abschnitt 9 (FR-032)
# Anforderungen: FR-032

@US-017 @FR-032
Feature: Erfolgsquote und Zustelllatenz je Endpunkt
  Als Service-Admin möchte ich je Endpunkt Erfolgsquote und Zustelllatenz ausgewiesen bekommen, damit ich im Betrieb erkennen kann, wo die Zustellung schwächelt.

  Scenario: Normalfall — Kennzahlen je Endpunkt  # Normalfall
    Given ein Endpunkt mit gemischten Versuchsergebnissen
    When die Kennzahlen für das Auswertungsfenster abgerufen werden
    Then werden Erfolgsquote und Zustelllatenz (p95) ausgewiesen

  Scenario: Verweigerungsfall — fremder Endpunkt  # Verweigerungsfall
    Given ein Empfänger-Admin von M1
    When er die Kennzahlen eines Endpunkts von M2 abruft
    Then der Dienst antwortet mit HTTP 404

  Scenario: Grenzfall — Endpunkt ohne Zustellvorgänge  # Grenzfall
    Given ein Endpunkt ohne Zustellvorgänge im Fenster
    When die Kennzahlen abgerufen werden
    Then wird „keine Daten“ ausgewiesen
