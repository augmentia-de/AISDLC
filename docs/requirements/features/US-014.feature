# Story: US-014 — Endgültige Ablehnung des Empfängers
# Quelle: docs/Anforderung.md — Abschnitt 8 (FR-025)
# Anforderungen: FR-025

@US-014 @FR-025
Feature: Endgültige Ablehnung des Empfängers
  Als Empfänger-Admin möchte ich dem Dienst signalisieren können, dass er ein Ereignis dauerhaft nicht möchte, damit er sofort aufhört statt weiter zu versuchen.

  Scenario: Normalfall — dauerhafte Ablehnung beendet die Kette  # Normalfall
    Given ein Empfänger, der ein Ereignis dauerhaft nicht möchte
    When er dies im Antwortsignal angibt
    Then wird die Zustellung sofort aufgegeben
    And das Protokoll zeigt den Status „abgelehnt“

  Scenario: Verweigerungsfall — Serverfehler ist keine Ablehnung  # Verweigerungsfall
    Given ein Empfänger, der mit 5xx antwortet
    When die Zustellung bearbeitet wird
    Then wird die Zustellung erneut versucht statt aufgegeben

  Scenario: Grenzfall — anderer 4xx  # Grenzfall
    Given ein Empfänger, der mit 4xx ohne das dokumentierte Ablehnungssignal antwortet
    When die Zustellung bearbeitet wird
    Then wird die Zustellung ohne weitere Versuche aufgegeben
