---
description: Phase 4 — Grill-Me stress test for architecture validation
---

# Grill-Me Stress Test Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture` · **Skill:** `aisdlc-grill-stress`


##Steps

1. **Find architectural errors early** — before implementing.
2. **Ruthless Interrogation** — Build a design tree, from the root down.
3. **Review against Standards and Spec** as two independent axes.
4. **Check developer interface** across 9 dimensions.

## Output
`.agent/artifacts/` (stress test report, found bugs, open questions)

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p docs/architecture/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ .agent/artifacts/
git commit -m "docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen" \
  -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

##Gate
Await human review. In case of architectural errors → return to phase 2.