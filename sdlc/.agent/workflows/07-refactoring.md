---
description: Phase 7 — Code refactoring and clean-up (second simplify pass)
---

# Refactoring Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/03_orchestrator_implementation.md`.

## Agent & Skill

- **Agent:** `aisdlc-execution`  ·  **Skill:** `aisdlc-simplify`


## Steps

1. **Vereinfachen** — Code vereinfachen, verhaltenserhaltend (wie Phase 3, aber auf fertigen Code).
2. **Dead Code entfernen** — mit Test-Verifikation nach jedem Schritt.
3. **Technische Schulden abbauen** — Debt-Inventar, Priorisierung, Aufwandsschätzung.
4. **Verification** — sicherstellen, dass es wirklich fertig ist.

## Output
Refactored code in `src/`, `tests/`.

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p src/ tests/ .agent/artifacts/
git reset -q
git add -A -- src/ tests/ .agent/artifacts/
git commit -m "refactor(sdlc-7): Refactoring und Clean-up" \
  -m "Schritt 7 - Refactoring & Clean-up | Agent: aisdlc-execution | Artefakte: src/, tests/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Gate
Bei unsauberem Code → Rücksprung zu Phase 6.