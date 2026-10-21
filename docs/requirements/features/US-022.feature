# Story: US-022 — Mandanttrennung auf Datenbankebene
# Quelle: docs/Anforderung.md — Abschnitt 10 (NFR-003), Abschnitt 3
# Anforderungen: NFR-003

@US-022 @NFR-003
Feature: Mandanttrennung auf Datenbankebene
  Als Service-Admin möchte ich, dass die Mandantentrennung auf Datenbankebene erzwungen wird, damit ein Fehler in der Anwendung niemals ein fremdes Ereignis zeigt.

  Scenario: Normalfall — Trennung wird erzwungen  # Normalfall
    Given zwei Mandanten mit je einem Ereignis in einer echten PostgreSQL-Instanz
    When eine Abfrage ohne Mandantenfilter läuft
    Then liefert die Abfrage nur die Zeilen des gesetzten Mandanten

  Scenario: Verweigerungsfall — Zugriff auf fremde Ressource  # Verweigerungsfall
    Given ein Benutzer des Mandanten M1
    When er eine Ressource von M2 per ID abruft
    Then antwortet der Dienst mit HTTP 404

  Scenario: Grenzfall — Anwendungsfehler ohne Filter  # Grenzfall
    Given zwei Mandanten mit Daten
    When der Anwendungscode setzt versehentlich keinen Mandantenfilter
    Then bleibt die Datenmenge auf den Mandanten der Verbindung begrenzt
