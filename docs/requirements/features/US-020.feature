# Story: US-020 — Oberfläche für Zustellprotokoll und tote Zustellungen
# Quelle: docs/Anforderung.md — Abschnitt 12 (Nr. 5)
# Anforderungen: FR-030, FR-031

@US-020 @FR-030 @FR-031
Feature: Oberfläche für Zustellprotokoll und tote Zustellungen
  Als Entwickler und als Service-Admin möchte ich Zustellprotokoll und tote Zustellungen in der Oberfläche sehen, damit ich Zustellfragen ohne Datenbankzugriff klären kann.

  Scenario: Normalfall — Protokollansicht  # Normalfall
    Given ein Entwickler mit Zustellvorgängen
    When er die Protokollseite öffnet
    Then sieht er die Zustellvorgänge mit allen geforderten Feldern und Filtern

  Scenario: Verweigerungsfall — fremder Mandant per Direktaufruf  # Verweigerungsfall
    Given ein Entwickler von M1
    When er die Detailseite eines Ereignisses von M2 direkt aufruft
    Then werden keine fremden Daten angezeigt

  Scenario: Grenzfall — Leerzustand  # Grenzfall
    Given ein Entwickler ohne Zustellvorgänge
    When er die Protokollseite öffnet
    Then wird ein verständlicher Leerzustand angezeigt
