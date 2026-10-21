# Story: US-008 — Bereits verschlüsselte Einlieferung
# Quelle: docs/Anforderung.md — Abschnitt 7 (FR-013)
# Anforderungen: FR-013

@US-008 @FR-013
Feature: Bereits verschlüsselte Einlieferung
  Als Entwickler möchte ich ein bereits verschlüsseltes Ereignis einliefern können, damit der Dienst die Nutzlast nicht entschlüsseln muss, bevor er zustellt.

  Scenario: Normalfall — Durchreichen ohne Entschlüsseln  # Normalfall
    Given ein Entwickler mit einem bereits verschlüsselten Ereignis
    When er es mit dem Kennzeichen „bereits verschlüsselt“ einliefert
    Then der Dienst speichert und liefert die Bytes unverändert aus
    And vor der Zustellung wird nicht entschlüsselt

  Scenario: Verweigerungsfall — Kennzeichen passt nicht zur Nutzlast  # Verweigerungsfall
    Given ein Entwickler
    When er das Kennzeichen „bereits verschlüsselt“ mit einer Klartextnutzlast kombiniert
    Then der Dienst antwortet mit HTTP 422

  Scenario: Grenzfall — Größengrenze bei verschlüsselter Nutzlast  # Grenzfall
    Given ein Entwickler
    When er ein verschlüsseltes Ereignis mit mehr als 1 MB einliefert
    Then der Dienst antwortet mit HTTP 413
