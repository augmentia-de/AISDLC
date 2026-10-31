---
name: aisdlc-migration-plan
description: Phase 2/6: Plan schema migrations (Expand/Contract, Rollback).
metadata:
  origin: own framework procedure; Craft from ECC/database-migrations + ECC/postgresql-table-design
---

## When

- **Phase 2**, in the schema decision — the strategy is determined there.
- **Phase 6**, before writing a migration file.

## The iron law
```
text
EINE MIGRATION, DIE AUF EINER LIVE-DATENBANK FALSCH IST, IST EIN INCIDENT
```
The plan must run with the **previous** application version.

## Expand/Contract

| phase | Action | Safe with old code? |
|---|---|---|
| **Expand** | Column nullable / new table / new index | yes |
| **Migrate** | Backfill in batches, idempotent | yes |
| **Switch** | Application reads and writes the new form | only after complete backfill |
| **Contract** | old column gone / constraint in | no — own release |

Rules: no renaming in one step; no `NOT NULL` column without default
for old lines; `CREATE INDEX CONCURRENTLY` on large tables; Backfills in
Checkpoint batches to make them resumable.

## Rollback

Every migration answers two questions: What happens if it **is not reversed**
is to be done? And what happens if the migration was **successful**?
But the application is rolled back? A migration without an answer to this is not complete.

## Output

- Migration plan as ADR: `docs/adr/ADR-<ID>-migration-<topic>.md`
- ordered list of migration files with their phase (expand/migrate/switch/contract)
- Rollback section per migration
- `.agent/artifacts/migration-plan.md`

## Rules

- Every migration is idempotent or carries an explicit "once" marker.
- Backfills are batched, resumable and rate limited.
- A destructive change goes into its own release, **after** the release of the
  Codes that no longer need the old form.
- Data loss requires explicit human approval and a
  recorded backup location.
- If the schema belongs to the Architecture Orchestrator, reports to the Execution
  Orchestrator considers the change request as a **finding** - he doesn't write it himself.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| Expand/Contract, Concurrent Index, Batch Backfill | `database-migrations` |
| Table design, columns, constraints | `postgresql-table-design` |
| PostgreSQL patterns, indexes, JSONB | `postgres-patterns` |