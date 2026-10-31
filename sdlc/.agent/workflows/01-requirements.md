---
description: Phase 1 — Requirements gathering for AI SDLC
---

# Requirements Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/01_orchestrator_requirements.md`.

## Agent & Skill

- **Agent:** `aisdlc-requirements` · **Skill:** `aisdlc-grill-stress`, `aisdlc-prd-to-stories`, `aisdlc-traceability-check`


## Steps

1. **Analysis of the requirement** for ambiguities, edge cases and conflicting goals.
2. **Decomposition** into technical epics, user stories and BDD scenarios.
3. Define **acceptance criteria** for each user story.
4. **Requirement IDs** assigned for traceability.

## Output
All artifacts written to `.agent/artifacts/` and `docs/requirements/`.

- `requirements.md` — PRD & Feature Spec
- `user-stories.md` — Epics → Stories → Acceptance Criteria
- `risk-register.md` — Top 5 risks
- `docs/requirements/PRD.md` — Product Requirement Document
- `docs/requirements/features/*.feature` — BDD scenarios (Gherkin).
  **Each scenario carries the identifier of its acceptance criterion** (`# AC-003-1`) —
  Without it, the associated test ultimately cannot be verified.
- `docs/requirements/stories/*.json` — User stories as tasks
- `docs/requirements/traceability_matrix.json` — Requirement → Story → Feature
  (**only the static half**; the test and code references come later from the
  file system, see `aisdlc-traceability-check`)

## Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## Gate
Await human review of all artifacts before proceeding to Phase 2.