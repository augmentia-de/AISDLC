---
name: aisdlc-tdd
description: Phase 5: RED/GREEN/REFACTOR, failure paths, workflow invariants.
metadata:
  origin: adapted from superpowers/test-driven-development + ECC/tdd-workflow + nnennandukwe/failure-path-testing + nnennandukwe/workflow-invariants
---

## When

Before every code generation (Phase 5) and every refactoring (Phase 7).

## The iron law
```
text
KEIN PRODUKTIONSCODE OHNE ZUERST FAILING TEST
```
Code before testing? **Delete.** Reimplementation. Exceptions (only with explicit
Human consent): disposable prototypes, generated code, configuration files.

## RED - Write a failing test

One test, **one** behavior, clear name, real code (no mocks unless unavoidable).
```
text
GUT:  "wiederholt fehlgeschlagene Operationen 3x"
      -> testet echtes Verhalten, eine Sache
SCHLECHT: "Retry funktioniert"
      -> unspezifisch, testet den Mock
```
**Verify RED - the test must fail, not fail:**
```
bash
<test-runner> <pfad/zum/test>
```
- Test **fails** (not errored)? correctly.
- Error message is the expected one? correctly.
- Test **green**? You test existing behavior. Correct test.
- Test **errored**? Fix the error, run again until it fails correctly.

## GREEN - Minimal code

The simplest code that makes the test green. No anticipation of features that are not yet available
test describes.

## REFACTOR

Keep tests green, then clean up: duplicates, overly long methods, bad names -
while constantly executing the tests.

## Choose the test level consciously

| level | For | Context |
|---|---|---|
| Unit | pure logic, a rule | without framework context |
| Integration | Interfaces, Persistence, Messaging | Test container / real dependency |
| E2E | User journey across multiple components | full chain |

Only load context what is needed. The test generates test data itself - not shared data
Database, no order dependency.

## Failure Path Testing (Negative Paths)

Prioritize tests that prove the system **refuses** unsafe progress.

1. Determine critical gates where progress must stop.
2. Define blocked state fixtures and expected recovery actions.
3. Write integration tests that execute realistic processes.
4. Check state transitions **and** the user recovery text.
5. One test by historical regression.
6. Covers a bug parsing, persistence and output, all three levels in one path.

**Assertions that always make sense:**
- No forbidden artifact written
- Blocked state with diagnostic metadata persisted
- Recovery instructions are explicit and executable
- Downstream commands fail deterministically
- Error message tells **what** to do, not just what went wrong

**Coverage heuristics per new workflow feature:**
1. at least one happy path test
2. at least one blocked path test
3. a message clarity assertion for recovery
4. the function writes files or changes state: regression at the failure point **before** the change

## Workflow invariants

Model behavior as explicit states and permitted transitions.
```
text
state: awaiting_review
allowed: approve -> awaiting_resume
blocked: publish, deploy
error: "Entwurf wartet auf Freigabe."
recovery: "Run approve + resume."
```
**Implementation:**
1. Transition logic in **one** place.
2. Structured error payloads for invalid transitions.
3. Recovery statements in **all** blocked path errors.
4. Remove obsolete artifacts when entering blocked states.
5. Emit an event for every transition (traceability).

**Invariant Checklist:**
- A failed run never becomes an active run.
- Downstream flows are blocked as long as releases are missing.
- Final artifacts do not exist until release is complete.
- Help text and guidance use the same transition names.

## Safety rules

- Don't delete or comment out tests to go green.
- No configuration change to pass tests (coverage gate, linter).
- Tests that produce different results without changing the code are an error.
- A test that has not been carried out is considered failed.

## Output

- Test strategy (levels, test cases, mandatory cases)
- Newly added negative paths and regressions
- Verification proof: exact command and result
- Gate conditions not covered