# Story: US-010 — Zustellkennungen und at-least-once
# Quelle: docs/Anforderung.md — Abschnitt 8 (FR-021), Abschnitt 4 (Fachbegriffe)
# Anforderungen: FR-021

@US-010 @FR-021
Feature: Zustellkennungen und at-least-once
  Als Empfänger-Admin möchte ich, dass jede Zustellung eigene Kennungen trägt, damit mein Empfänger doppelte Zustellungen erkennen und selbst entscheiden kann.

  Scenario: Normalfall — Kennungen am Zustelllauf  # Normalfall
    Given ein Ereignis E an einen Endpunkt
    When die Zustellung erfolgt
    Then trägt der Request die Ereignis-ID, eine Zustelllauf-ID und eine Versuchs-ID

  Scenario: Verweigerungsfall — Erfolg beendet die Kette  # Verweigerungsfall
    Given ein Empfänger, der mit 2xx antwortet
    When der Zustellvorgang abgeschlossen wird
    Then erfolgt kein weiterer Versuch für dieses Ereignis an diesen Endpunkt

  Scenario: Grenzfall — Dublette nach Timeout  # Grenzfall
    Given ein Empfänger, der wegen eines Timeouts nicht geantwortet hat
    When eine Wiederholung eingeht
    Then tragen beide Anfragen dieselbe Zustelllauf-ID und unterschiedliche Versuchs-IDs
