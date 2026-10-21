# Story: US-002 — Endpunkt pausieren und reaktivieren
# Quelle: docs/Anforderung.md — Abschnitt 6 (FR-002)
# Anforderungen: FR-002

@US-002 @FR-002
Feature: Endpunkt pausieren und reaktivieren
  Als Empfänger-Admin möchte ich einen Endpunkt pausieren und wieder aktivieren, damit ich die Zustellung kontrolliert aussetze, ohne dass Ereignisse nachgeliefert werden.

  Scenario: Endpunkt wird pausiert  # Normalfall
    Given ein aktiver Endpunkt mit einem offenen Zustellvorgang
    When der Empfänger-Admin pausiert den Endpunkt
    Then der Endpunkt steht auf „pausiert“
    And für den Endpunkt wird kein neuer Zustellversuch gestartet
    And ein bereits geplanter Wiederholungsvorgang wird abgebrochen

  Scenario: fremder Endpunkt  # Verweigerungsfall
    Given ein Empfänger-Admin des Mandanten M1
    When er pausiert einen Endpunkt des Mandanten M2
    Then der Dienst antwortet mit HTTP 404 und der fremde Endpunkt bleibt unverändert

  Scenario: Ereignisse während der Pause  # Grenzfall
    Given ein pausierter Endpunkt
    When während der Pause wird ein Ereignis eingeliefert und danach wird der Endpunkt reaktiviert
    Then das Ereignis wird nicht zugestellt und nicht nachgeliefert
    And nach der Reaktivierung existiert für dieses Ereignis kein Zustellversuch
