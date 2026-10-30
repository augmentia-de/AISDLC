---
name: aisdlc-feature-scaffold
description: Creates one complete feature slice - model, repository, service, route, validation and test - following the conventions already present in the codebase. Use this skill in step 6 when implementing a story from docs/requirements/stories.
metadata:
  origin: draft for AISDLC
---

##Purpose

Make every feature look like the ones that already exist. Consistency beats cleverness when
several agents write code in parallel.

## When

Step 6, per story. One invocation = one story.

##Inputs

- The story file `docs/requirements/stories/<story_id>.json`
- `docs/api/openapi.yaml` (the endpoints this story needs)
- The existing code base (read it first — this skill adds nothing new architecturally)
- `docs/requirements/traceability_matrix.json` (the criteria of this slice must satisfy)

## Procedure

1. **Read before writing** — find the closest existing feature and mirror its structure:
   module layout, naming, error handling, test style, logging. Do not introduce a second
   pattern.
2. **Vertical slice, not layers** — model, repository, service, route, test for *this* story.
   Do not build half of the model layer and stop.
3. **Contract first** — the route shape comes from `openapi.yaml`, never from imagination.
4. **Validation at the edge** — input validation in the request layer, business rules in the
   service, invariants in the domain.
5. **Tests with the code** — the slice is not done without its tests.
6. **Traceability** — reference the story id and the acceptance criteria in the test names or
   the commit message.
7. **Report** — files touched, criteria satisfied, test result.

## Output

- Code under `src/`
- Tests under `tests/`
- A short report: story id, criteria covered, files, test result
- Update of `docs/requirements/traceability_matrix.json` for the covered criteria

##Rules

- One story per invocation. Do not start a second story in the same run.
- No schema change without a migration plan (`aisdlc-migration-plan`).
- No new dependency without checking that the project already uses it.
- If the story needs a decision that is not in the PRD or the contract, escalate — do not
  invent it.