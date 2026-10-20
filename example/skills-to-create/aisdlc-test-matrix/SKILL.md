---
name: aisdlc-test-matrix
description: Maps every acceptance criterion to the test level and the test file that covers it, and finds the criteria nobody tested. Use this skill in step 5 to build the test plan before implementation.
metadata:
  origin: draft for AISDLC
---

## Purpose

Turn acceptance criteria into a concrete test plan. Without the mapping, coverage is a
number without a meaning, and the criteria nobody thought about are exactly the ones that
break in production.

## When

Step 5, after the API contract is frozen and before the first implementation task.

## Inputs

- `docs/requirements/stories/*.json` (acceptance criteria)
- `docs/requirements/features/*.feature`
- `docs/api/openapi.yaml`
- `docs/architecture/domain_model.md` (invariants that need tests)

## Procedure

1. **List the criteria** — every acceptance criterion, grouped by story.
2. **Assign a level** —

   | Level | Use for |
   |---|---|
   | unit | one rule, no infrastructure, no database |
   | integration | persistence, messaging, HTTP contract, transactions |
   | e2e | a user journey across several components |

3. **Assign the file** — name it after the behaviour, not the implementation:
   `test_task_status_transition.py`, not `test_service.py`.
4. **Add the negative cases** — for every "the system must refuse" criterion, a test that
   proves the refusal. This is the part that is forgotten.
5. **Add the invariant tests** — the state machine, tenant isolation, and the retention
   rules from the domain model get their own cases.
6. **Mark the manual ones** — a criterion that cannot be automated is marked `manual` with a
   reason, never silently dropped.

## Output

`.agent/artifacts/test-matrix.md`:

| Criterion | Level | Test file | Case | Status |
|---|---|---|---|---|
| AC-003-1 task appears in list | integration | `tests/integration/test_task_create.py` | `test_create_task` | planned |
| AC-009-2 cross-tenant read is refused | integration | `tests/integration/test_tenant_isolation.py` | `test_other_org_task_forbidden` | planned |
| AC-014-1 history pruned after 90 days | integration | `tests/integration/test_retention.py` | `test_prune_expired_history` | planned |

Plus the machine-readable version in `docs/requirements/traceability_matrix.json`.

## Rules

- One criterion, at least one case. A criterion without a case is a gap and blocks gate 1.
- Mandatory negative cases for every refusal criterion.
- A test that asserts nothing about the behaviour is not coverage.
