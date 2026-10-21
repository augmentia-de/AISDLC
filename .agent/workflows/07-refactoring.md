---
description: Phase 7 — Code refactoring and clean-up (second simplify pass)
---

# Refactoring Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- All build phases (6) must be complete.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-execution` |
| Instanz (Multi-Instanz-Modus) | `phase3` |
| Skill | `aisdlc-simplify` |

## Steps

1. **Vereinfachen** — Code vereinfachen, verhaltenserhaltend (wie Phase 3, aber auf fertigen Code).
2. **Dead Code entfernen** — mit Test-Verifikation nach jedem Schritt.
3. **Technische Schulden abbauen** — Debt-Inventar, Priorisierung, Aufwandsschätzung.
4. **Verification** — sicherstellen, dass es wirklich fertig ist.

## Output
Refactored code in `src/`, `tests/`.

## Regeln

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.
- **The contract `docs/api/openapi.yaml` is frozen** (since step 4). A contract defect is a finding for a jump back to step 2, not an edit in passing.

## Commit

```bash
mkdir -p src/ tests/ .agent/artifacts/
git reset -q
git add -A -- src/ tests/ .agent/artifacts/
git commit -m "refactor(sdlc-7): Refactoring und Clean-up" \
  -m "Schritt 7 - Refactoring & Clean-up | Agent: aisdlc-execution | Artefakte: src/, tests/, .agent/artifacts/"
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
Bei unsauberem Code → Rücksprung zu Phase 6.