# Story: US-023 — Containerbetrieb, Mandanteninstanzen und horizontale Skalierung
# Quelle: docs/Anforderung.md — Abschnitt 10 (NFR-006), Abschnitt 11 (Rahmenbedingungen)
# Anforderungen: NFR-006

@US-023 @NFR-006
Feature: Containerbetrieb, Mandanteninstanzen und horizontale Skalierung
  Als Service-Admin möchte ich den Dienst als Container betreiben, pro Mandanten konfigurierbar und horizontal skalierbar, damit Ausfälle und Lastspitzen beherrschbar sind.

  Scenario: Normalfall — Betrieb im Container  # Normalfall
    Given das gebaute Image
    When der Dienst daraus gestartet wird
    Then startet er, meldet der Gesundheitsendpunkt „gesund“, und das Image enthält keine Secrets

  Scenario: Verweigerungsfall — keine fest verdrahteten Zugangsdaten  # Verweigerungsfall
    Given das Image und die Konfigurationsdateien
    When sie nach Zugangsdaten und fest verdrahteten Passwörtern durchsucht werden
    Then wird kein Treffer gefunden

  Scenario: Grenzfall — Absturz während der Zustellung  # Grenzfall
    Given eine Instanz mit einem laufenden Zustellversuch
    When die Instanz abstürzt und neu startet
    Then wird der Zustellvorgang fortgesetzt, ohne das Ereignis zu verlieren
