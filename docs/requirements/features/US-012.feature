# Story: US-012 — Aufgabe nach erschöpften Versuchen
# Quelle: docs/Anforderung.md — Abschnitt 8 (FR-023)
# Anforderungen: FR-023
# Offene Punkte: O-06

@US-012 @FR-023
Feature: Aufgabe nach erschöpften Versuchen
  Als Service-Admin möchte ich, dass eine Zustellung nach einer festen Anzahl erfolgloser Versuche aufgegeben wird und sichtbar in der Liste der toten Zustellungen landet, damit kein Ereignis still verschwindet.

  Scenario: Normalfall — Zustellung wird aufgegeben  # Normalfall
    Given ein Endpunkt, der dauerhaft mit 5xx antwortet
    When die konfigurierte Anzahl der Versuche erreicht ist
    Then die Zustellung erscheint in der Liste der toten Zustellungen
    And ein weiterer Versuch findet nicht statt

  Scenario: Verweigerungsfall — kein automatischer Neuversuch  # Verweigerungsfall
    Given eine aufgegebene Zustellung
    When das Zeitfenster des nächsten Versuchs verstreicht
    Then bleibt die Versuchsanzahl unverändert

  Scenario: Grenzfall — Versuchsgrenze 1  # Grenzfall
    Given eine Konfiguration mit der Versuchsgrenze 1
    When ein Zustellversuch fehlschlägt
    Then die Zustellung endet nach genau einem Versuch als „aufgegeben“
