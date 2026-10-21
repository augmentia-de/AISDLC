# Story: US-011 — Wiederholung mit Backoff und Streuung
# Quelle: docs/Anforderung.md — Abschnitt 8 (FR-022)
# Anforderungen: FR-022
# Offene Punkte: O-06, O-07

@US-011 @FR-022
Feature: Wiederholung mit Backoff und Streuung
  Als Service-Admin möchte ich, dass fehlgeschlagene Zustellungen mit wachsendem Abstand erneut versucht werden und die Versuche über die Endpunkte gestreut sind, damit ein Ausfall nicht zu einem Laststurm wird.

  Scenario: Normalfall — Backoff mit wachsendem Abstand  # Normalfall
    Given ein Endpunkt, der mit 5xx antwortet
    When mehrere Zustellversuche geplant werden
    Then wächst der Abstand zwischen den Versuchen von Versuch zu Versuch
    And die Versuche laufen im Hintergrundprozess

  Scenario: Verweigerungsfall — Erfolg beendet die Wiederholungen  # Verweigerungsfall
    Given ein Empfänger, der mit 2xx antwortet
    When die Wiederholungsplanung läuft
    Then wird kein weiterer Versuch geplant

  Scenario: Grenzfall — Streuung über viele Endpunkte  # Grenzfall
    Given 100 Endpunkte, die gleichzeitig fehlschlagen
    When die Zeitpunkte der nächsten Versuche verglichen werden
    Then sind die Zeitpunkte nicht alle identisch
