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
git add src/ tests/ .agent/artifacts/
git commit -m "refactor(sdlc-7): Refactoring und Clean-up" \
  -m "Schritt 7 - Refactoring & Clean-up | Agent: aisdlc-execution | Artefakte: src/, tests/, .agent/artifacts/" \
  -- src/ tests/ .agent/artifacts/
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das `-- <pfade>`-Scope ist technisch erzwungen: selbst bei `git add -A` committet
  nur der angegebene Pfad.
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Bei unsauberem Code → Rücksprung zu Phase 6.