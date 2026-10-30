---
description: Phase 3 — Simplify & Reduction (before the stress test)
---

# Simplify Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture` · **Skill:** `aisdlc-simplify`


##Steps

1. **Identify complexity** — which parts of the architecture are oversized?
2. **Reduce** — keep only what is necessary, eliminate over-engineering.
3. **Simplify** — Clarity over completeness.

## Output
`.agent/artifacts/` (updated architecture, reduction report)

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git commit -m "refactor(sdlc-3): Architektur vereinfacht" \
  -m "Schritt 3 - Simplify & Reduktion | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

##Gate
Await human review. If there is too much complexity → return to phase 2.