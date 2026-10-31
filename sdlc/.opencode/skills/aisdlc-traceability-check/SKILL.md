---
name: aisdlc-traceability-check
description: Phase 1/5: Traceability chain Requirement->Story->Feature->Test->Check code.
metadata:
  origin: tool .agent/tools/traceability_check.py; derived from the inline consistency checks of the requirements run
---

## When

- **Phase 5**, after setting up the tests — where the chain can be tested for the first time
- **Phase 6**, post-implementation — the code now exists
- **Before GATE 2** — mandatory, not as an indication

## Two halves - don't mix them up

| helped | Content | Originates in | Where |
|---|---|---|---|
| **static** | Requirement → Story → Feature | Step 1 | `docs/requirements/traceability_matrix.json` |
| **dynamic** | Requirement → Test → Code | Step 5/6 | `.agent/artifacts/traceability.md` |

The **Matrix** only supports the static half. It will happen **once** in step 1
written and then **not** touched again. That's the point: `tests` and
`code` are values that only step 5/6 knows about — a field that is an earlier step
cannot fill, creates either a gap or an invented path.

**Why this is so:** In the reference run, the matrix was on for the entire run
Scaffolding — 0 of 89 requirements with test reference. It was only filled afterwards
Step 7, with two additional commits in step 1. The check that
would have revealed, never ran because it only existed after the run.

## The receipt
```
bash
python3 .agent/tools/traceability_check.py            # pruefen, nur melden
python3 .agent/tools/traceability_check.py --fill     # pruefen und Beleg schreiben
`
```
--fill` writes `.agent/artifacts/traceability.md` (the receipt) and
`.agent/artifacts/traceability-fill.md` (what was documented, what was not and why).

**The basic rule of the tool:** It **never** writes a path that it doesn't occupy
can. A made-up path is worse than a gap — it looks occupied and
it is not.

## How the assignment is proven

**Test → Requirement** via the acceptance criteria identifier `AC-<story>-<nr>`:

| Where | shape |
|---|---|
| Story JSON | `"id": "AC-003-1"` |
| Feature scenario | `Scenario: Rotation # AC-003-1` |
| Test case | Method name `ac003_1_rotationUsesOnlyS2` or a comment `// AC-003-1` |

The identifier is in **all three** locations. Therefore the assignment is mechanical: that
Tool reads the identifier from the test, finds the story via the story JSON and
their request IDs. Nothing is advised.

**Code → Requirement**: A test that **names** a production class substantiates it
Class — if it exists in the project. More than that cannot be proven and will not happen
registered.

## What is being checked

| # | exam | Heavy | Meaning |
|---|---|---|---|
| 1 | Requirement in the PRD without story | gap | except deliberately led without a story (post-MVP) |
| 2 | Story references unknown requirement | gap | the story doesn't depend on anything |
| 3 | Story without feature file | gap | |
| 4 | Story without reference to requirements | gap | |
| 5 | Acceptance criterion without matrix entry | gap | Criteria without a matrix are invisible |
| 6 | Test with unknown AC identifier | gap | the test does not depend on any requirement |
| 7 | Requirement with story without documented test | gap | **the core condition** |
| 8 | Requirement with story without documented code reference | gap | |
| 9 | Production class without test reference | Grade | with dependency injection normal |
| 10 | Test file without AC identifier | Grade | |

**Gap** blocks the gate. **Note** reports, not blocks. The exit code
is 1 for gaps, 0 for pure clues.

## Log result

The gap list moves to `.agent/artifacts/traceability.md` (human readable)
and as `gaps` in the traceability matrix. **Not a percentage.** A matrix that corresponds to
87% complete is less useful than the list of 13 missing criteria.

Every gap gets a decision, not just a finding:

| Type of gap | decision |
|---|---|
| Request without story | Add story **or** Mark requirement as post MVP |
| Requirement without a documented test | Add test **or** justify coverage as `manual` |
| Acceptance criterion without entry | Add entry — criteria without a matrix are invisible |
| Test without AC identifier | Add AC identifier (method or comment) — otherwise the test is blank |

## Who fills

- **Step 1** writes the static matrix — the requirements agent, once.
- **Step 5/6** generate tests and code. The **Requirements Agent** then lets
  Run the tool (`--fill`) and commit the document. This is a **own one
  Order**, no return: the agent that owns `docs/requirements/` is the same
  as in Scsstep 1.

**Why this way and not the other way:** The execution agent is not allowed to use `docs/requirements/`
write. Giving him the right to exactly one file would mean folder ownership
soften. The order to the requirements agent costs a change of agent and
keeps the separation clean.

**Principle:** A follow-up is **a** commit with the subject of the corrected one
Steps plus `(follow-up)`. At most one per step — a second means that
the cause lies in the process.

## Rules

- **Always check after** the matrix update, never before - otherwise you will measure it
  old status.
- The check is stack-independent: it reads JSON, Markdown, Gherkin and source code.
- **Never** delete gaps by removing matrix entries. That just disappears.
- The receipt is the **end** status, not the desired status. What doesn't exist
  is there as a gap.