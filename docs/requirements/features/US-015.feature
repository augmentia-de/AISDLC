# Story: US-015 — Zustellprotokoll einsehen
# Quelle: docs/Anforderung.md — Abschnitt 9 (FR-030)
# Anforderungen: FR-030

@US-015 @FR-030
Feature: Zustellprotokoll einsehen
  Als Entwickler möchte ich je Zustellung Zustand, Versuche, Zeitpunkte und den Antwortcode des Empfängers einsehen, damit ich die Frage „ist das angekommen?“ selbst beantworten kann.

  Scenario: Normalfall — Protokolleintrag mit allen Feldern  # Normalfall
    Given ein Zustellvorgang mit zwei Versuchen
    When das Zustellprotokoll gelesen wird
    Then zeigt der Eintrag Zustand, Versuchsanzahl, letzten Versuchszeitpunkt, Antwortcode und nächsten Versuchszeitpunkt

  Scenario: Verweigerungsfall — fremdes Ereignis  # Verweigerungsfall
    Given ein Entwickler von M1
    When er ein Protokoll eines Ereignisses von M2 abruft
    Then der Dienst antwortet mit HTTP 404 ohne fremde Daten

  Scenario: Grenzfall — tote Zustellung ohne nächsten Termin  # Grenzfall
    Given eine tote Zustellung
    When das Protokoll gelesen wird
    Then ist der nächste Versuchszeitpunkt leer
