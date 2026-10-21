---
description: Phase 5 — Test-Driven Setup (TDD)
---

# TDD Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- Read `.agent/artifacts/architecture.md` and the contract `docs/api/openapi.yaml`
  (`.agent/knowledge/openapi.yaml` ist nur ein Verweis darauf).

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-execution` |
| Instanz (Multi-Instanz-Modus) | `phase3` |
| Skill | `aisdlc-tdd` |

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

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.
- **The contract `docs/api/openapi.yaml` is frozen** (since step 4). A contract defect is a finding for a jump back to step 2, not an edit in passing.

## Commit

```bash
mkdir -p tests/ .agent/artifacts/
git reset -q
git add -A -- tests/ .agent/artifacts/
git commit -m "test(sdlc-5): Teststrategie und fehlschlagende Tests" \
  -m "Schritt 5 - Test-Driven Setup | Agent: aisdlc-execution | Artefakte: tests/, .agent/artifacts/"
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das Scope ist technisch erzwungen: es wird nur der Schritt-Scope gestaged, alles
  andere bleibt unangetastet.
- Das Scope wird ueber den **Index** gesetzt, nicht ueber eine Pfadspezifikation:
  `git reset -q` leert den Index, `git add -A -- <pfade>` staged nur den Schritt,
  `git commit` committet nur den Index.
  Grund: `git commit -- <pfade>` bricht ab, sobald ein Scope-Pfad keine getrackten
  Dateien hat ("pathspec did not match any files") — und der Weg ueber
  `--pathspec-from-file` ist unter dem globalen `rtk`-Plugin kaputt: `rtk` rewritet
  `git diff` und haengt einen "Changes:"-Block an, der als Pfadspezifikation
  wiederum mit "empty string is not a valid pathspec" abbricht.
- `git reset -q` unstaged fremde Arbeit, loescht sie aber nicht.
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Bei unklarem Interface → Rücksprung zu Phase 3 (Simplify).