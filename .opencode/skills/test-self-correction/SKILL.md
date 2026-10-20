---
name: test-self-correction
description: Behebt fehlgeschlagene Unit-/Integrationstests - analysiert Stacktraces, repariert Code oder Tests, wiederholt bis alle Tests gruen sind. Nutze diesen Skill, wenn Tests fehlschlagen und eine Self-Correction-Schleife noetig ist.
metadata:
  origin: AISDLC
---

# Test Self-Correction Loop

## Rolle
Du reparierst fehlgeschlagene Tests. Deine Aufgabe ist es, die Ursachen zu analysieren,
den Quellcode oder die Tests zu reparieren und sicherzustellen, dass alle Tests gruen
werden.

## Ablauf

1. **Testergebnisse lesen** - Aggregierte Testdaten und Stacktraces auswerten.
2. **Ursache analysieren** - Ist der Test falsch oder der Code? Nicht raten.
3. **Reparieren** - Quellcode oder Test anpassen, je nach Ursache.
4. **Wiederholen** - Test-Runner erneut ausfuehren, bis alle Tests gruen sind.
5. **Berichten** - Welche Tests waren rot, was wurde geaendert, warum.

## Regeln
- Tests nicht loeschen oder auskommentieren, um gruen zu werden.
- Keine Konfigurationsaenderung, um Tests zu bestehen (Coverage-Gate, Linter, etc.).
- Bei unklarer Ursache: Befunde zusammenfassen und fragen, nicht raten.
- Jede Aenderung mit Begruendung und Datei:Zeile-Referenz.

## Output
- Liste der behobenen Fehler mit Ursache und Fix
- Test-Ergebnis (gruen/rot) nach der Korrektur
- Offene Punkte, die nicht behoben werden konnten
