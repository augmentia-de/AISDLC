# Story: US-004 — Endpunkte nach Rolle und Mandant sehen
# Quelle: docs/Anforderung.md — Abschnitt 6 (FR-004), Abschnitt 3 (Rollen)
# Anforderungen: FR-004

@US-004 @FR-004
Feature: Endpunkte nach Rolle und Mandant sehen
  Als Empfänger-Admin möchte ich nur die Endpunkte meines Mandanten sehen und als Service-Admin alle Endpunkte aller Mandanten, damit die Mandantentrennung in der Oberfläche genauso gilt wie in der Schnittstelle.

  Scenario: Empfänger-Admin sieht eigene Endpunkte  # Normalfall
    Given zwei Mandanten mit je einem Endpunkt
    When ein Empfänger-Admin von M1 ruft die Endpunktliste ab
    Then die Liste enthält nur den Endpunkt von M1

  Scenario: mandantenübergreifende Liste  # Verweigerungsfall
    Given ein Empfänger-Admin von M1
    When er die mandantenübergreifende Endpunktliste anfordert
    Then der Dienst antwortet mit HTTP 403

  Scenario: direkter Zugriff auf fremde ID  # Grenzfall
    Given ein Empfänger-Admin von M1
    When er die Endpunkt-ID von M2 direkt abruft
    Then der Dienst antwortet mit HTTP 404 ohne Daten des fremden Endpunkts
