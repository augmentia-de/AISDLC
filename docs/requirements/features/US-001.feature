# Story: US-001 — Endpunkt registrieren
# Quelle: docs/Anforderung.md — Abschnitt 6 (FR-001)
# Anforderungen: FR-001

@US-001 @FR-001
Feature: Endpunkt registrieren
  Als Empfänger-Admin möchte ich einen Endpunkt mit Ziel-URL, Secret und gewünschten Ereignistypen registrieren, damit Ereignisse meines Systems an eine kontrollierte, mandanteneigene URL zugestellt werden.

  Scenario: Endpunkt wird angelegt  # Normalfall
    Given ein Empfänger-Admin des Mandanten M1
    When er registriert einen Endpunkt mit https-URL, Secret und Ereignistypen
    Then der Endpunkt existiert mit dem Status „aktiv“
    And der Mandant M1 stammt aus der Identität, nicht aus dem Request
    And die Antwort enthält die Endpunkt-ID, aber kein Secret im Klartext

  Scenario: Ziel-URL ohne https  # Verweigerungsfall
    Given ein Empfänger-Admin des Mandanten M1
    When er registriert einen Endpunkt mit einer http-URL
    Then der Dienst antwortet mit HTTP 422
    And es wird kein Endpunkt angelegt

  Scenario: URL mit eingebetteten Zugangsdaten  # Grenzfall
    Given ein Empfänger-Admin des Mandanten M1
    When er registriert einen Endpunkt mit https://user:pass@host/path
    Then der Dienst antwortet mit HTTP 422
    And Zugangsdaten im URL werden abgelehnt; nur das Secret trägt die Authentifizierung
