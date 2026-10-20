---
description: Phase 6 — Implementation and coding (parallel build streams)
---

# Implementation Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/knowledge/openapi.yaml`, `schema.sql`, `stack.md`.
- Confirm Phase 2 HARD GATE has been passed (human-approved).

## Parallel Build Streams

1. **Backend Stream** (`src/`):
   - Scaffold app with entry point
   - Implement all endpoints from `openapi.yaml`
   - Set up database with `DATABASE_URL` env var
   - Add JWT auth middleware (scoped by `organisation_id`)
   - Integrate external AI/LLM service
   - Implement export functionality
   - Write tests in `tests/`

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

## Synchronisation
- All streams work against the same `openapi.yaml` (Phase 2 Output).
- No stream may modify `openapi.yaml` — return to Phase 2 if needed.
- Merge point: all streams must be green before Phase 7 begins.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-execution` |
| Instanz (Multi-Instanz-Modus) | `phase3` |
| Skill | — (kein Skill nötig) |

## Output
Working application code in `src/`, `tests/`, `infra/`.
Inline documentation (JSDoc/Docstrings).
Commits (Conventional Commits) and PR descriptions.

## Gate
Bei fehlenden Tests → Rücksprung zu Phase 5.