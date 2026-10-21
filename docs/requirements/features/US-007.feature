# Story: US-007 — Verschlüsselung im Ruhezustand
# Quelle: docs/Anforderung.md — Abschnitt 7 (FR-012), Abschnitt 10 (NFR-004)
# Anforderungen: FR-012, NFR-004
# Offene Punkte: O-04

@US-007 @FR-012 @NFR-004
Feature: Verschlüsselung im Ruhezustand
  Als Entwickler möchte ich, dass meine Nutzlast verschlüsselt gespeichert wird, damit sie weder in der Datenbank noch in einem Log im Klartext lesbar ist.

  Scenario: Normalfall — Nutzlast im Ruhezustand verschlüsselt  # Normalfall
    Given ein eingeliefertes Ereignis mit der Klartextmarkierung „TOP-SECRET-PAYLOAD“
    When die Datenbank und die Protokolle nach dieser Markierung durchsucht werden
    Then es gibt keinen Treffer im Klartext
    And beim Zustellen werden die ursprünglichen Klartextbytes geliefert

  Scenario: Verweigerungsfall — Schlüssel darf nicht im Projekt liegen  # Verweigerungsfall
    Given der Verschlüsselungsschlüssel
    When Schema, Image und Konfiguration werden geprüft
    Then ist der Schlüssel in keinem Artefakt enthalten
    And der Schlüssel kommt aus einer Secret-Verwaltung

  Scenario: Grenzfall — Suche im Zustellprotokoll  # Grenzfall
    Given ein verschlüsselt gespeichertes Ereignis
    When im Zustellprotokoll nach Ereignis-ID und Typ gefiltert wird
    Then der Treffer wird gefunden
    And eine Suche nach Nutzlastinhalt ist nicht vorhanden
