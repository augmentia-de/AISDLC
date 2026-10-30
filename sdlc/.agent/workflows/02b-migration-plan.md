---
description: Phase 2b — Migration plan (strangler-fig / module-migration)
---

# Migration plan workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read `docs/migration/*` (Assessment output).
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture`
- **Skill:** `aisdlc-migration-plan`, `strangler-fig`, `aisdlc-glossary-adr`

##Steps

1. **Target image** — Target architecture, module boundaries, new contracts (only where necessary).
2. **Mapping** — Old → New (Module, Route, Table, Job). File: `docs/migration/mapping.md` (or `.json`).
3. **Anti-Corruption Layer** — Define adapter/ACL between old and new.
4. **Cutover strategy** — Gradual switching (routing, feature flags, canary).
5. **Data Migration** — Scripts, Idempotence, Rollback, Validation.
6. **Backout Plan** — How to get back (without losing data).
7. **ADRs** — `migration/strategy`, `cutover`, `rollback`, `data-migration`.

## Output

- `docs/migration/mapping.md`
- `docs/migration/cutover-checklist.md`
- `docs/migration/rollback-plan.md`
- `docs/migration/data-migration.md`
- `docs/adr/ADR-xxx-migration-*.md`
- `.agent/artifacts/migration-plan.md`

##Rules

- Contract freeze **does not** apply to adapters/ACLs. Only new external/public contracts are frozen.
- No big bang switching without cutover gate.
- Data migration scripts must be idempotent and rollbacked.

## Commit
```
bash
mkdir -p docs/migration/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/migration/ docs/adr/ .agent/artifacts/
git commit -m "docs(sdlc-2b): Migration-Plan und Cutover-Strategie" \
  -m "Schritt 2b - Migration-Plan | Agent: aisdlc-architecture | Artefakte: docs/migration/, docs/adr/, .agent/artifacts/"
```
## HARD GATE

**GATE 1 (Migration):** Human approval of target image, mapping and cutover strategy before implementation.