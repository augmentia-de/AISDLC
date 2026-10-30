---
name: aisdlc-prd-to-stories
description: Turns a PRD or raw request into user stories with acceptance criteria, Gherkin feature files and a risk register. Use this skill in step 1 to produce docs/requirements/stories/*.json, docs/requirements/features/*.feature and .agent/artifacts/risk-register.md.
metadata:
  origin: draft for AISDLC
---

##Purpose

Turn a request into a specification that later agents can execute without guessing. The
output must be machine-readable (stories as JSON) **and** human-readable (Gherkin).

## When

Step 1, after the requirement has been grilled. If requirements are still ambiguous, do not
use this skill — run `aisdlc-grill-stress` first.

##Inputs

- User request or `docs/requirements/PRD.md`
- Existing `.agent/knowledge/stack.md` (for technical constraints)
- Domain glossary if one exists (`docs/architecture/domain_model.md`)

## Procedure

1. **Extract capabilities** — group the request into epics. One epic = one cohesive goal.
2. **Write stories** — one user story per capability, in the form
   *As a <role>, I want <capability>, so that <benefit>*. Keep one story small
   enough to implement and test in one sitting.
3. **Acceptance criteria** — concrete, observable, testable. "The system is fast" is not a
   criterion. "The list renders in under 2 s at p95" is.
4. **BDD features** — one `.feature` per story, with `Given/When/Then` scenarios covering
   the happy path, the rejection path and the edge case.
5. **Non-functional requirements** — performance, security, privacy, accessibility,
   availability. Give each a measurable target.
6. **Risk register** — top risks with likelihood, impact, mitigation. Only risks that change
   a decision.
7. **Traceability seed** — every story references the requirement ids it satisfies.

## Output
```
docs/requirements/PRD.md                    # updated or created
docs/requirements/stories/<story_id>.json    # one per story
docs/requirements/features/<feature>.feature  # Gherkin, one per story
docs/requirements/traceability_matrix.json    # requirement -> story -> criteria
.agent/artifacts/risk-register.md
.agent/artifacts/user-stories.md
```
Story file schema:
```
json
{
  "id": "US-003",
  "epic": "Task management",
  "title": "Create a task",
  "description": "As a user, I want to create a task, so that I can track work.",
  "requirement_ids": ["FR-012"],
  "acceptance_criteria": [
    { "id": "AC-003-1", "given": "a signed-in user", "when": "they submit a valid task", "then": "the task appears in the list" }
  ],
  "priority": "must-have",
  "status": "todo"
}
```
##Rules

- Every story has at least one acceptance criterion; every criterion is observable.
- No story may depend on another unfinished story without saying so.
- Out-of-scope items go into an explicit "won't have" section, not into the backlog.
- If a story cannot be written because a decision is missing, escalate — do not invent the
  decision.