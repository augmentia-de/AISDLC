---
description: Phase 0 — Migration assessment & legacy inventory (brownfield/migration mode)
---

# Migration Assessment Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/00_orchestrator_analysis.md`.
- `.agent/config.json` must have `mode: migration` or `mode: brownfield`.

## Agent & Skill

- **Agent:** `aisdlc-requirements` (or `aisdlc-architecture` for tech-heavy systems)
- **Skill:** `module-migration`, `strangler-fig`

##Steps

1. **Inventory** — Modules, Routes, Jobs, Integrations, Databases, Queues, Cron, Config files.
2. **Contract discovery** — Existing API contracts (OpenAPI/GraphQL/WSDL), implicit DTOs, error formats.
3. **Data model extraction** — tables, relations, migration scripts, seed data.
4. **Dependency map** — Internal/external dependencies, hidden couplings.
5. **Risk & debt register** — Technical debt, missing tests, unknown owners.
6. **Scope proposal** — What will be migrated, what will remain legacy, what will be deprecated.

## Output

- `docs/migration/inventory.md`
- `docs/migration/dependency-map.md`
- `docs/migration/risk-register.md`
- `docs/migration/scope.md`
- `.agent/artifacts/migration-assessment.md`

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4 (only for **new** external/public contracts).
**No** contract freeze applies to adapters between old/new.

## Commit
```
bash
mkdir -p docs/migration/ .agent/artifacts/
git reset -q
git add -A -- docs/migration/ .agent/artifacts/
git commit -m "docs(sdlc-0): Migration-Assessment und Legacy-Inventar" \
  -m "Schritt 0 - Migration-Assessment | Agent: aisdlc-requirements | Artefakte: docs/migration/, .agent/artifacts/"
```
- If nothing has changed: do not create a commit (no error).

##Gate

Optional (MVP) / Mandatory (Enterprise): Human release of scope before migration plan is created.