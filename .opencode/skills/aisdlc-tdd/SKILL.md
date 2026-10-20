---
name: aisdlc-tdd
description: Testgetriebene Entwicklung - Test zuerst, rot sehen, minimal gruen machen, refactoren. Mit Failure-Path-Tests und Workflow-Invarianten. Nutze diesen Skill in Phase 5 (TDD-Setup) vor der Code-Generierung und waehrend der Implementierung.
metadata:
  origin: adaptiert aus superpowers/test-driven-development + ECC/tdd-workflow + nnennandukwe/failure-path-testing + nnennandukwe/workflow-invariants
---

## Wann

Vor jeder Code-Generierung (Phase 5) und bei jedem Refactoring (Phase 7).

## Das eiserne Gesetz

```text
KEIN PRODUKTIONSCODE OHNE ZUERST FAILING TEST
```

Code vor dem Test? **Loeschen.** Neu implementieren. Ausnahmen (nur mit ausdruecklicher
Zustimmung des Menschen): Wegwerf-Prototypen, generierter Code, Konfigurationsdateien.

## RED - Failing Test schreiben

Ein Test, **ein** Verhalten, klarer Name, echter Code (keine Mocks ausser unvermeidbar).

```text
GUT:  "wiederholt fehlgeschlagene Operationen 3x"
      -> testet echtes Verhalten, eine Sache
SCHLECHT: "Retry funktioniert"
      -> unspezifisch, testet den Mock
```

**Verify RED - der Test muss fehlschlagen, nicht fehlerhagen:**

```bash
<test-runner> <pfad/zum/test>
```

- Test **fehlschlaegt** (nicht errored)? korrekt.
- Fehlermeldung ist die erwartete? korrekt.
- Test **gruen**? Du testest bestehendes Verhalten. Test korrigieren.
- Test **errored**? Fehler beheben, erneut laufen lassen bis er korrekt fehlschlaegt.

## GREEN - Minimaler Code

Der einfachste Code, der den Test gruen macht. Kein Vorgriff auf Features, die noch kein
Test beschreibt.

## REFACTOR

Tests gruen halten, dann aufraeumen: Duplikate, ueberlange Methoden, schlechte Namen -
unter staendigem Ausfuehren der Tests.

## Testebene bewusst waehlen

| Ebene | Fuer | Kontext |
|---|---|---|
| Unit | reine Logik, eine Regel | ohne Framework-Kontext |
| Integration | Schnittstellen, Persistenz, Messaging | Testcontainer / echte Abhaengigkeit |
| E2E | User-Journey ueber mehrere Komponenten | volle Kette |

Kontext nur laden, was gebraucht wird. Testdaten erzeugt der Test selbst - keine geteilte
Datenbank, keine Reihenfolgeabhaengigkeit.

## Failure-Path-Testing (Negative Pfade)

Priorisiere Tests, die beweisen, dass das System unsichere Fortschritte **verweigert**.

1. Kritische Gates bestimmen, an denen der Fortschritt stoppen muss.
2. Blockierte Zustands-Fixtures und erwartete Recovery-Aktionen definieren.
3. Integrationstests schreiben, die realistische Ablaeufe ausfuehren.
4. Zustandsuebergaenge **und** den nutzerseitigen Recovery-Text pruefen.
5. Ein Test pro historischem Regression.
6. Deckt ein Bug Parsing, Persistenz und Ausgabe ab, alle drei Ebenen in einem Pfad.

**Assertions, die immer sinnvoll sind:**
- Kein verbotenes Artefakt geschrieben
- Blockierter Zustand mit Diagnose-Metadaten persistiert
- Recovery-Hinweise sind explizit und ausfuehrbar
- Downstream-Kommandos scheitern deterministisch
- Fehlermeldung sagt, **was** zu tun ist, nicht nur, was schiefging

**Coverage-Heuristik pro neue Workflow-Funktion:**
1. mindestens ein Happy-Path-Test
2. mindestens ein Blocked-Path-Test
3. eine Nachrichten-Klarheits-Assertion fuer die Recovery
4. schreibt die Funktion Dateien oder wechselt Zustand: Regression am Fehlerpunkt **vor** dem Wechsel

## Workflow-Invarianten

Verhalten als explizite Zustaende und erlaubte Uebergaenge modellieren.

```text
state: awaiting_review
allowed: approve -> awaiting_resume
blocked: publish, deploy
error: "Entwurf wartet auf Freigabe."
recovery: "Run approve + resume."
```

**Implementierung:**
1. Uebergangslogik an **einer** Stelle.
2. Strukturierte Fehlerpayloads bei ungueltigen Uebergaengen.
3. Recovery-Anweisungen in **allen** Blocked-Path-Fehlern.
4. Veraltete Artefakte beim Wechsel in blockierte Zustaende entfernen.
5. Bei jedem Uebergang ein Event emittieren (Nachvollziehbarkeit).

**Invarianten-Checkliste:**
- Ein fehlgeschlagener Lauf wird nie zum aktiven Lauf.
- Downstream-Flows sind blockiert, solange Freigaben fehlen.
- Finale Artefakte existieren nicht vor abgeschlossener Freigabe.
- Hilfe-Text und Guidance verwenden dieselben Uebergangsnamen.

## Sicherheitsregeln

- Keine Tests loeschen oder auskommentieren, um gruen zu werden.
- Keine Konfigurationsaenderung, um Tests bestehen zu lassen (Coverage-Gate, Linter).
- Tests, die ohne Codeaenderung unterschiedlich ausgehen, sind ein Fehler.
- Ein Test, der nicht ausgefuehrt wurde, gilt als nicht bestanden.

## Output

- Teststrategie (Ebenen, Testfälle, Pflichtfaelle)
- Neu hinzugefuegte negative Pfade und Regressionen
- Verifikationsnachweis: exakter Befehl und Ergebnis
- Nicht abgedeckte Gate-Bedingungen
