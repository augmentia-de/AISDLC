# Story: US-006 — Idempotente Einlieferung
# Quelle: docs/Anforderung.md — Abschnitt 7 (FR-011)
# Anforderungen: FR-011

@US-006 @FR-011
Feature: Idempotente Einlieferung
  Als Entwickler möchte ich, dass eine wiederholte Einlieferung mit demselben Idempotenzschlüssel kein zweites Ereignis erzeugt, damit ein Retry meines Clients keine Doppelzustellung auslöst.

  Scenario: Normalfall — Wiederholung liefert die erste Kennung  # Normalfall
    Given ein bereits eingeliefertes Ereignis E mit Idempotenzschlüssel K
    When der Entwickler liefert dasselbe Ereignis erneut mit K ein
    Then die Antwort ist erfolgreich und nennt die ID von E
    And es existiert genau ein Ereignis mit K

  Scenario: Verweigerungsfall — gleicher Schlüssel, andere Nutzlast  # Verweigerungsfall
    Given ein Ereignis E mit Idempotenzschlüssel K
    When der Entwickler liefert mit K eine abweichende Nutzlast ein
    Then der Dienst antwortet mit HTTP 409 und E bleibt unverändert

  Scenario: Grenzfall — gleicher Schlüssel in einem anderen Mandanten  # Grenzfall
    Given ein Ereignis mit K im Mandanten M1
    When der Mandant M2 liefert mit demselben K ein Ereignis ein
    Then M2 erhält ein eigenes Ereignis mit eigener Kennung
    And die Idempotenz ist je Mandant abgegrenzt
