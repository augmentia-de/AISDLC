---
name: aisdlc-prd-to-stories
description: Phase 1: Generate PRD, stories, features and traceability matrix from an order.
metadata:
  origin: own framework procedure; Draft from example/skills-to-create/aisdlc-prd-to-stories
---

## When

Phase 1, **after** the grill meal. If the requirement is still ambiguous, first
`aisdlc-grill-stress` — this skill has a *clear* requirement.

## The iron law
```
text
KEINE STORY OHNE AKZEPTANZKITERIUM
```
An acceptance criterion must be **observable**. “The system is fast” is not one
criterion. "The list renders in under 2 s (p95)" is one.

## Procedure

1. **Extract Skills** — group the job into epics. An epic = one
   coherent goal.
2. **Write stories** — *As a <role> I want <skill> so that <benefit>.*
   A story so small that it can be built and tested in one go.
3. **Acceptance criteria** — at least one per story, observable and measurable.
4. **BDD Features** — one `.feature` per story with `Given/When/Then`:
   the normal case, the refusal case, the borderline case.
   **Each scenario has the identifier of its acceptance criterion** as a comment:
```
gherkin
   # Story: US-003 — Secret rotieren
   # Anforderungen: FR-003

   Feature: Secret rotieren

     Scenario: Rotation  # AC-003-1
       Given ein Endpunkt mit Secret S1
       When der Empfänger-Admin rotiert auf Secret S2
       Then verwendet der Dienst ab dem nächsten Zustellversuch ausschließlich S2
   
```
The identifier `AC-<story>-<no>` is therefore in **three** places: in the story JSON
   ("id"`), in the feature scenario (comment) and later in the test case. That's just how it is
   Assignment test → requirement **mechanical** — the traceability check reads the
   Identifier from the test and finds the requirement via the story. Without them you have to
   assigned, and that is advised.
5. **Non-Functional Requirements** — Performance, Security, Privacy,
   Accessibility, availability. One **measurable** target size for each.
6. **Risks** — only risks that change a **decision**. A counterweight each, that
   is anchored in a requirement.
7. **Traceability Seed** — each story names the requirement IDs it covers.

## Output
```
docs/requirements/PRD.md                    # Vision, Personas, Ziele, KPIs, MoSCoW
docs/requirements/stories/<story_id>.json    # eine Datei je Story
docs/requirements/features/<story_id>.feature  # Gherkin
docs/requirements/traceability_matrix.json   # Anforderung -> Story -> Test
.agent/artifacts/requirements.md             # kompakter Katalog
.agent/artifacts/user-stories.md             # lesbare Fassung
.agent/artifacts/risk-register.md            # Top-Risiken
```
Story scheme:
```
json
{
  "id": "US-003",
  "epic": "E1",
  "title": "Token-Lebenszyklus",
  "description": "As a developer, I want ...",
  "requirement_ids": ["FR-007"],
  "acceptance_criteria": [
    { "id": "AC-003-1", "given": "...", "when": "...", "then": "..." }
  ],
  "priority": "must-have",
  "status": "todo"
}
```
##Rules

- **Each** acceptance criteria has an identifier `AC-<story>-<nr>`, and **each**
  Feature scenario carries them. In the end, a scenario without an identifier cannot be verified.
- Every story has at least one acceptance criterion; every criterion is observable.
- A story doesn't hang on to another unfinished story without saying so.
- Anything that doesn't belong in the scope ends up in an explicit **"won't have"** —
  not in the backlog.
- If a decision is missing, it is **escalated**, not invented.
- After writing, `aisdlc-traceability-check` applies.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| Clarify ambiguous requirement | `intent-driven-development` |
| Story Lifecycle, Epic to Sprint | `story-lifecycle` |