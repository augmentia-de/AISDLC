---
name: aisdlc-glossary-adr
description: Writes and maintains the domain glossary and architecture decision records. Use this skill in step 2 to record decisions, and whenever a term is coined or sharpened during any step.
metadata:
  origin: draft for AISDLC
---

##Purpose

Two artifacts carry the reasoning that would otherwise be lost:

- **GLOSSARY.md** — the domain language, so that code, docs and tests use the same words.
- **ADRs** — why a decision was made, so that no later agent re-litigates it.

## When

- Step 2, for every decision that has consequences.
- Any time a term is introduced, narrowed or renamed.
- After a grill-me session that settled a decision.

##Inputs

- `docs/requirements/PRD.md` and `docs/requirements/stories/*.json` (the terms in use)
- `docs/architecture/domain_model.md` (entities and invariants)
- `docs/api/openapi.yaml` (the terms the contract uses)
- The code base (the terms the implementation uses)
- Existing `docs/adr/` (to avoid contradicting or duplicating a decision)

## Procedure

1. Collect the terms of the requirement and the code actually use.
2. Write the glossary entries (definition + what it is not + source).
3. Write one ADR per decision that has consequences.

## Glossary

Keep it small and precise. One term per entry:
```
markdown
## Task

A unit of work owned by one organisation.

- **Status**: exactly one of `Open`, `In Progress`, `Done`. Forward-only.
- **Not**: a subtask, a reminder or a recurring job.
- **Source**: `docs/requirements/features/task-crud.feature`
```
Rules: define what a term is **and** what it is not; link the requirement it comes from;
delete terms that no longer appear in the code or the requirements.

## ADR format
```
markdown
# ADR-002: Keep task history instead of deleting it

## Context
Erasure (GDPR) and 90-day retention both apply to the task history table.
## Decision
History rows are deleted with the task. The retention job only prunes orphaned rows.
## Consequences
Audit trail is lost on erasure. Simpler compliance story, no separate erasure flow.
## Alternatives
- Keep history, anonymise on erasure: more complex, keeps the audit trail.
- Soft delete: violates the right to erasure.
```
Rules: record the decision, not the discussion; always name the rejected alternatives;
never rewrite an accepted ADR — supersede it with a new one.

## Output

- `docs/architecture/GLOSSARY.md`
- `docs/adr/ADR-<ID>-<title>.md`
- `.agent/artifacts/adr-index.md` — one-line summary per ADR for the gate review

##Rules

- One decision per ADR.
- If an agent cannot find the rationale for a decision, the ADR is missing.
- Terms used in code must appear in the glossary; flags it as a review finding otherwise.