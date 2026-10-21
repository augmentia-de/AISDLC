# Story: US-021 — Barrierefreiheit der Oberfläche
# Quelle: docs/Anforderung.md — Abschnitt 10 (NFR-005), Abschnitt 12 (Nr. 5)
# Anforderungen: NFR-005

@US-021 @NFR-005
Feature: Barrierefreiheit der Oberfläche
  Als Nutzer mit Hilfsmitteln möchte ich die Oberfläche ohne Maus und mit Screenreader bedienen können, damit ich Endpunkte, Protokoll und Totenliste gleichwertig nutzen kann.

  Scenario: Normalfall — Bedienung mit der Tastatur  # Normalfall
    Given die Seiten Endpunkte, Protokoll und tote Zustellungen
    When sie ausschließlich mit der Tastatur bedient werden
    Then sind alle Funktionen erreichbar und auslösbar
    And die Fokussierung ist durchgehend sichtbar

  Scenario: Verweigerungsfall — automatischer WCAG-Scan  # Verweigerungsfall
    Given alle Seiten der Oberfläche
    When ein axe-Scan in der Qualitätssicherung läuft
    Then meldet der Scan keinen Verstoß der Klasse „kritisch“ oder „schwer“

  Scenario: Grenzfall — Kontrast der Bedienelemente  # Grenzfall
    Given Bedienelemente der Oberfläche
    When die Kontrastverhältnisse gemessen werden
    Then erreichen die Bedienelemente mindestens 3:1 und die Texte mindestens 4,5:1
