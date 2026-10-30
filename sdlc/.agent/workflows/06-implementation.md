---
description: Phase 6 — Implementation and coding (parallel build streams)
---

# Implementation workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/03_orchestrator_implementation.md`.

## Parallel build streams

1. **Backend Stream** (`src/`):
   - Scaffold app with entry point
   - Implement all endpoints from `openapi.yaml`
   - Set up database with `DATABASE_URL` env var
   - Add JWT auth middleware (scoped by `organization_id`)
   - Integrate external AI/LLM service
   - Implement export functionality
   - Write tests to `tests/`

2. **Frontend Stream** (`frontend/` or `src/frontend/`):
   - Scaffold app with App Router
   - Build submission form with privacy notice
   - Build results display page
   - Build export button
   - All API calls must match `openapi.yaml`

3. **Infra Stream** (`infra/`):
   - Create Dockerfiles (multi-stage, non-root)
   - Create docker-compose.yml (local dev)
   - Create CI/CD pipeline (GitHub Actions)

## Synchronization
- All streams work against the same `openapi.yaml` (Phase 2 Output).
- No stream may modify `openapi.yaml` — return to Phase 2 if needed.
- Merge point: all streams must be green before Phase 7 begins.

## Agent & Skill

- **Agent:** `aisdlc-execution` · **Skill:** `aisdlc-feature-scaffold`, `aisdlc-containerize`


## Output
Working application code in `src/`, `tests/`, `infra/`.
Inline documentation (JSDoc/Docstrings).
Project README at root (`README.md`) with quickstart, build/test/run and structure.
Commits (Conventional Commits) and PR descriptions.

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p src/ tests/ docs/execution/
git reset -q
git add -A -- src/ tests/ docs/execution/ README.md
git commit -m "feat(sdlc-6): Implementierung der Stories" \
  -m "Schritt 6 - KI-Code-Generation | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## Logging

The application logs **structured**: one line per event with correlation ID,
Event type, tenant, outcome and duration. **No** payload, no secrets, none
Tokens, not complete personal data sets.

The convention is enshrined in `.agent/artifacts/logging.md` — the
Security Scan later checks **against this file**, not against its own
Expectation. It is created in the same step as the code because it comes from the code
follows out.

##Gate
If tests are missing → return to phase 5.