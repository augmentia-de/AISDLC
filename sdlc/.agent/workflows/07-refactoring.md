---
description: Phase 7 — Code refactoring and clean-up (second simplify pass)
---

# Refactoring workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/03_orchestrator_implementation.md`.

## Agent & Skill

- **Agent:** `aisdlc-execution` · **Skill:** `aisdlc-simplify`


## Steps

1. **Simplify** — Simplify code, preserving behavior (like phase 3, but on finished code).
2. **Remove Dead Code** — with test verification after each step.
3. **Reduce technical debt** — Debt inventory, prioritization, effort estimation.
4. **Verification** — make sure it's really finished.

## Output
Refactored code in `src/`, `tests/`.

## Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p src/ tests/ .agent/artifacts/
git reset -q
git add -A -- src/ tests/ .agent/artifacts/
git commit -m "refactor(sdlc-7): Refactoring und Clean-up" \
  -m "Schritt 7 - Refactoring & Clean-up | Agent: aisdlc-execution | Artefakte: src/, tests/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## Gate
If the code is unclean → return to phase 6.