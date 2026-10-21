# Story: US-005 — Ereignis einliefern
# Quelle: docs/Anforderung.md — Abschnitt 7 (FR-010), Abschnitt 10 (NFR-001, NFR-002), Abschnitt 11
# Anforderungen: FR-010, NFR-001, NFR-002

@US-005 @FR-010 @NFR-001 @NFR-002
Feature: Ereignis einliefern
  Als Entwickler möchte ich ein Ereignis mit Typ, Nutzlast und Idempotenzschlüssel einliefern, damit es von unserem zentralen Dienst an die abonnierten Endpunkte zugestellt wird, ohne dass mein Aufruf wartet.

  Scenario: Normalfall — Annahme und asynchrone Zustellung  # Normalfall
    Given ein berechtigter Entwickler des Mandanten M1
    When er ein Ereignis mit Typ, Nutzlast und Idempotenzschlüssel einliefert
    Then der Dienst nimmt das Ereignis an und liefert die Ereignis-ID zurück
    And die Zustellung startet im Hintergrundprozess, nicht im Request-Thread
    And die Annahmeantwort liegt unter 200 ms (p95)

  Scenario: Verweigerungsfall — unvollständige Einlieferung  # Verweigerungsfall
    Given ein berechtigter Entwickler
    When er ein Ereignis ohne Idempotenzschlüssel einliefert
    Then der Dienst antwortet mit HTTP 422 und legt kein Ereignis an

  Scenario: Grenzfall — Nutzlast an der 1-MB-Grenze  # Grenzfall
    Given ein berechtigter Entwickler
    When er eine Nutzlast von genau 1.048.576 Byte einliefert
    Then der Dienst nimmt das Ereignis an
    And eine Nutzlast von 1.048.577 Byte wird mit HTTP 413 abgelehnt
