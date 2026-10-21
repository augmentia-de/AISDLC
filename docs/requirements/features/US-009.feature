# Story: US-009 — Signierte Zustellung
# Quelle: docs/Anforderung.md — Abschnitt 8 (FR-020)
# Anforderungen: FR-020

@US-009 @FR-020
Feature: Signierte Zustellung
  Als Empfänger-Admin möchte ich, dass jede Zustellung über Zeitstempel und Nutzlast signiert ist, damit mein Empfänger die Echtheit des Ereignisses prüfen kann.

  Scenario: Normalfall — Signatur prüft beim Empfänger  # Normalfall
    Given ein Endpunkt mit Secret S
    When eine Zustellung mit Nutzlast und Zeitstempel erfolgt
    Then der Empfänger kann die Signatur mit S bestätigen

  Scenario: Verweigerungsfall — manipulierte Nutzlast  # Verweigerungsfall
    Given eine signierte Zustellung
    When die Nutzlast vor der Prüfung verändert wird
    Then schlägt die Signaturprüfung fehl
    And die Zustellung gilt nicht als erfolgreich zugestellt

  Scenario: Grenzfall — gleiche Nutzlast, verschiedene Endpunkte  # Grenzfall
    Given zwei Endpunkte mit unterschiedlichen Secrets
    When beide erhalten dasselbe Ereignis
    Then tragen die beiden Zustellungen verschiedene Signaturen
