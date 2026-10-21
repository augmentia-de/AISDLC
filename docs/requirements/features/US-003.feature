# Story: US-003 — Secret rotieren
# Quelle: docs/Anforderung.md — Abschnitt 6 (FR-003)
# Anforderungen: FR-003

@US-003 @FR-003
Feature: Secret rotieren
  Als Empfänger-Admin möchte ich das Secret meines Endpunkts rotieren, damit ein verdächtiges Secret ausgetauscht werden kann, ohne den Endpunkt neu anzulegen.

  Scenario: Rotation  # Normalfall
    Given ein Endpunkt mit Secret S1
    When der Empfänger-Admin rotiert auf Secret S2
    Then die nächste Zustellung trägt eine Signatur, die mit S2 prüfbar ist
    And das alte Secret wird nicht mehr verwendet

  Scenario: unberechtigte Rolle  # Verweigerungsfall
    Given ein Entwickler desselben Mandanten
    When er die Rotation auslöst
    Then der Dienst antwortet mit HTTP 403 und das Secret bleibt unverändert

  Scenario: laufende Zustellung  # Grenzfall
    Given eine Zustellung, die bereits mit S1 signiert wurde
    When im Anschluss wird auf S2 rotiert
    Then die bereits signierte Zustellung bleibt mit S1 signiert, alle folgenden mit S2
