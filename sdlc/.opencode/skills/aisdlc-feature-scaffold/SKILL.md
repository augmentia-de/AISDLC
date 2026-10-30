---
name: aisdlc-feature-scaffold
description: Phase 6: a story as a vertical slice (model, repo, service, route, test).
metadata:
  origin: own framework procedure; Craft from ECC/contract-first + stack skills
---

## When

**Phase 6**, per story. One call = one story.

## The iron law
```
text
LIES ZUERST DEN VORHANDENEN CODE, DANN SCHREIBE
```
The skill does not insert a second architecture. He finds the most similar thing available
Feature and reflects its structure.

## Procedure

1. **Read before writing** — find the nearest feature and its
   Adopt structure: module layout, naming, error handling, test style, logging.
2. **Vertical section, not layered** — Model, Repository, Service, Route,
   Test for **this** story. Don't do half the modeling shift and then stop.
3. **contract first** — the route form comes from `docs/api/openapi.yaml`, never from the
   Imagination.
4. **Validation at the edge** — input validation in the request layer,
   Business rules in the service, invariants in the model.
5. **Tests with the code** — the cut is not finished without its tests.
6. **Traceability** — Story ID and acceptance criteria in the test name or commit body.
7. **Report** — changed files, criteria covered, test result.

## Output

- Code under `src/`
- Tests under `tests/`
- Short report: story ID, criteria covered, files, test result
- Update of the traceability matrix for the criteria covered

##Rules

-One story per view. No second story in the same run.
- No schema change without migration plan (`aisdlc-migration-plan`).
- No new dependencies without checking whether the project already uses them.
- Does the story need a decision that is neither in the PRD nor in the contract:
  **escalate**, don't invent.
- The contract is not touched - a contract defect is a finding
  Step2.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| Frontend and backend from one contract | `contract-first` |
| Java/Spring backend structure | `springboot-patterns` |
| Java conventions | `java-coding-standards` |
| React components | `react-patterns` |
| Next.js App Router | `nextjs-app-router-patterns` |
| Types | `typescript-advanced-types` |