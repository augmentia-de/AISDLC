---
description: Phase 7 — Code refactoring and clean-up (second simplify pass)
---

# Refactoring Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
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

## Commit

```bash
git add -A .
git diff --cached --name-only -- src/ tests/ .agent/artifacts/ \
  | git commit -m "refactor(sdlc-7): Refactoring und Clean-up" \
             -m "Schritt 7 - Refactoring & Clean-up | Agent: aisdlc-execution | Artefakte: src/, tests/, .agent/artifacts/" \
             --pathspec-from-file=-
git restore --staged .
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das Scope ist technisch erzwungen: committet werden nur Dateien, die im Pfad-Scope
  geaendert oder neu sind — fremde Aenderungen bleiben unangetastet.
- Der Commit-Scope laeuft ueber `--pathspec-from-file`, damit ein leerer oder noch nicht
  existierender Pfad den Commit nicht verhindert (git bricht sonst mit "pathspec did not
  match any files" ab und es wird gar nichts committet).
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Bei unsauberem Code → Rücksprung zu Phase 6.