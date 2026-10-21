# Story: US-018 — Rate-Limit je Endpunkt
# Quelle: docs/Anforderung.md — Abschnitt 9 (FR-033), Abschnitt 13 (Rate-Limit)
# Anforderungen: FR-033
# Offene Punkte: O-01

@US-018 @FR-033
Feature: Rate-Limit je Endpunkt
  Als Service-Admin möchte ich, dass ein Endpunkt gegen zu viele Zustellungen in kurzer Zeit geschützt ist, damit ein schwacher Empfänger nicht die Zustellung für alle anderen blockiert.

  Scenario: Normalfall — Limit greift je Endpunkt  # Normalfall
    Given ein Endpunkt mit konfiguriertem Grenzwert
    When die Versuche im Zeitfenster den Grenzwert überschreiten
    Then greift die konfigurierte Strategie und es geht kein Ereignis verloren
    And die betroffene Zustellung ist im Protokoll erkennbar

  Scenario: Verweigerungsfall — kein Ereignisverlust  # Verweigerungsfall
    Given ein Endpunkt mit aktivem Limit
    When mehrere Zustellungen laufen in das Limit hinein
    Then wird kein Ereignis verworfen und kein Zustellvorgang endet unprotokolliert

  Scenario: Grenzfall — Grenze wirkt nicht mandantenübergreifend  # Grenzfall
    Given zwei Endpunkte, von denen nur einer das Limit überschreitet
    When beide Endpunkte werden zugestellt
    Then bleibt der andere Endpunkt unverzögert
