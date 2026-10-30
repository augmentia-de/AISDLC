---
name: aisdlc-migration-plan
description: Plans schema migrations that are safe to run on a live database - expand/contract, backward compatibility and a rollback path. Use this skill in step 2 for the schema decision and in step 6 before any migration is written.
metadata:
  origin: draft for AISDLC
---

##Purpose

A migration that is wrong on a live database is an incident. The plan has to work with the
**previous** application version still running.

## When

- Step 2, when the schema is designed — decide the migration strategy there.
- Step 6, before writing the migration file.

##Inputs

- `.agent/knowledge/schema.sql`
- `docs/api/openapi.yaml` (breaking changes are API changes too)
- `docs/adr/` (retention, deletion policies)

## Procedure

1. Inventory the schema change and the application code that reads the old shape.
2. Split it into expand / migrate / switch / contract steps.
3. Decide the rollback path for each step.
4. Write one migration file per step, in order.

## Expand / contract

| phase | Action | Safe with old code? |
|---|---|---|
| **Expand** | Add nullable column / new table / new index | yes |
| **Migrate** | Backfill in batches, idempotent | yes |
| **Switch** | Application reads and writes the new shape | only after backfill is complete |
| **Contract** | Drop the old column / add the constraint | no — separate release |

Rules: never rename a column in one step; never add a `NOT NULL` column without a default
to existing rows; index creation on a large table uses the concurrent form; backfills run in
batches with a checkpoint so they can be resumed.

## Rollback

Every migration needs the answer to two questions: what happens if the migration is
irreversible, and what happens if the migration succeeded but the application was rolled
back. A migration without an answer is not ready.

## Output

- Migration plan in `docs/adr/ADR-<ID>-migration-<topic>.md`
- Ordered list of migration files with their phase (expand/migrate/switch/contract)
- Rollback section per migration
- `.agent/artifacts/migration-plan.md`

##Rules

- Every migration is idempotent or has an explicit "run once" marker.
- Backfills are batched, resumable and rate-limited.
- A destructive change ships in its own release, after the code that stops using the old
  shape has been deployed.
- Data loss requires explicit human approval and a recorded backup location.