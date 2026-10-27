---
description: Phase 5 — Test-Driven Setup (TDD)
---

# TDD Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/03_orchestrator_implementation.md`.

## Agent & Skill

- **Agent:** `aisdlc-execution`  ·  **Skill:** `aisdlc-tdd`, `aisdlc-test-matrix`, `aisdlc-traceability-check`


## Steps

1. **Tests vor dem Code** — fehlschlagenden Test schreiben, dann implementieren.
2. **Ebene bewusst wählen** — Unit-Tests für reine Logik, Integrationstests für Schnittstellen.
3. **Kontext sparsam laden** — nur was wirklich gebraucht wird.
4. **Testdaten lokal** — jeder Test erzeugt seine Daten selbst.
5. **Pflichtfälle** — Erfolgsfall, Doppelter Aufruf (Idempotenz), Ungültige Eingabe,
   Fehlende Berechtigung, Abhängigkeit antwortet langsam oder mit Fehler.

## Output
`.agent/artifacts/` (Test-Strategie, Testfälle)

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p tests/ .agent/artifacts/
git reset -q
git add -A -- tests/ .agent/artifacts/
git commit -m "test(sdlc-5): Teststrategie und fehlschlagende Tests" \
  -m "Schritt 5 - Test-Driven Setup | Agent: aisdlc-execution | Artefakte: tests/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Traceability

Nach den Tests ist die Kette erstmals belegbar. Der Check laeuft hier:

```bash
python3 .agent/tools/traceability_check.py
```

**Jeder Testfall traegt die Kennung seines Akzeptanzkriteriums** (Methodenname oder
Kommentar). Ohne sie bleibt der Test unbelegt.

Der **Beleg** (`.agent/artifacts/traceability.md`) wird von einem eigenen Auftrag an
den Requirements-Agenten erzeugt — `--fill`, siehe `aisdlc-traceability-check`. Nicht
in diesem Schritt: der Execution-Agent darf `docs/requirements/` nicht schreiben.

## Gate
Bei unklarem Interface → Rücksprung zu Phase 3 (Simplify).