---
name: aisdlc-test-matrix
description: Phase 5: Map acceptance criteria at test level, file and case.
metadata:
  origin: own framework procedure; Craft from ECC/springboot-tdd + ECC/e2e-testing
---

## When

**Phase 5**, after the contract (steps 2-4) and **before** the first
Implementation line.

## The iron law
```
text
KEIN KRITERIUM OHNE TESTFALL
```
A criterion without a test case is a gap and blocks Gate 1.

## Procedure

1. **Collect criteria** — each acceptance criterion from the stories.
2. **Assign Layer** —

   | level | For | Context |
   |---|---|---|
   | unit | a rule, no infrastructure, no database | without framework context |
   | integration | Persistence, Messaging, HTTP Contract, Transactions | Test container / real dependency |
   | e2e | a user journey across several components | full chain |

3. **Name file** — based on **behavior**, not implementation:
   `test_task_status_transition`, not `test_service`.

   **The test case carries the identifier of its acceptance criterion** — as the method name
   or as a comment directly above:
```
java
   // AC-003-1
   @Test void ac003_1_rotationVerwendetNurS2() { … }
   
```

   
```
typescript
   // AC-003-1
   it('ac003_1 rotation verwendet nur S2', async () => { … })
   
```
The file itself has **no** identifier — it can cover multiple criteria.
   What is important is what is stated **in the test**: the traceability check reads the identifier
   the test text, not the file name. In the end, there is no test without an identifier
   assignable and blocks the gate.
4. **Add negative cases** — for each “the system must refuse” a test that
   the refusal is documented. That will be forgotten.
5. **Invariants** — get state machine, client separation, retention rules
   own cases.
6. **Mark manual** — what cannot be automated becomes `manual` for a reason.

## Output

`.agent/artifacts/test-matrix.md`:

| criterion | level | Test file | case | Status |
|---|---|---|---|---|
| AC-003-1 Task appears in the list | integration | `tests/integration/test_task_create` | `ac003_1_taskAppearsInTheList` | planned |

So machine readable in `docs/requirements/traceability_matrix.json`.

##Rules

- One criterion, at least one case. Without a case it is a gap.
- **Each test case carries the identifier of its acceptance criterion.** Without it, the
  The test cannot be verified at the end - and the receipt is a gate condition.
- Mandatory negative cases for every denial request.
- A test that does not claim anything about behavior is not covered.
- A test that has not been carried out is considered failed.
- Then `aisdlc-traceability-check` — the check compares the matrix against the
  File systems, not against intention.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| Spring Boot test setup, test containers, slice tests | `springboot-tdd` |
| React unit tests | `react-testing` |
| JavaScript/TypeScript Testing Strategies | `javascript-testing-patterns` |
| E2E with Playwright | `e2e-testing` |
| Test inventory, governance | `test-collaboration` |