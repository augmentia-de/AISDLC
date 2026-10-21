# Story: US-019 — Endpunktverwaltung in der Oberfläche
# Quelle: docs/Anforderung.md — Abschnitt 12 (Nr. 1 und 5)
# Anforderungen: FR-001, FR-002, FR-003

@US-019 @FR-001 @FR-002 @FR-003
Feature: Endpunktverwaltung in der Oberfläche
  Als Empfänger-Admin möchte ich Endpunkte in einer Oberfläche registrieren, pausieren und rotieren, damit ich die Verwaltung ohne API-Aufrufe erledigen kann.

  Scenario: Normalfall — Endpunktliste  # Normalfall
    Given ein Empfänger-Admin mit zwei Endpunkten
    When er die Endpunktseite öffnet
    Then sieht er beide Endpunkte mit Status, URL und Ereignistypen

  Scenario: Verweigerungsfall — ungültige URL  # Verweigerungsfall
    Given ein Empfänger-Admin
    When er das Registrierungsformular mit einer http-URL abschickt
    Then erscheint eine Fehlermeldung am Feld und es wird kein Endpunkt angelegt

  Scenario: Grenzfall — Statuswechsel beim Pausieren  # Grenzfall
    Given ein aktiver Endpunkt
    When er ihn pausiert
    Then zeigt die Liste sofort den Status „pausiert“ und bietet „reaktivieren“ an
