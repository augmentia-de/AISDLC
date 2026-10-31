---
name: aisdlc-openapi-first
description: Phase 2/6: Design and implement API contract against it.
metadata:
  origin: own framework procedure; Craft from ECC/api-design + ECC/api-design-principles
---

## When

- **Phase 2**, when drafting the contract
- **Phase 6**, when building routes, DTOs and handlers against the contract

Do not change the contract in phase 6. What cannot be expressed goes back
after phase 2.

## Two layers - don't confuse them

| layer | Responsible | Delivers |
|---|---|---|
| **Procedure** (this skill) | the framework | **when**, **which artifacts**, **which tests** |
| **Craft** | `api-design`, `api-design-principles` (vendored) | **how** to build REST designs, status codes, error bodies and versioning |

This skill does not replace crafting. He tells the agent **which one**
Stack skill he loads for **which** part of the contract, and records what at the end
must be present as an artifact.

## The iron law
```
text
KEIN ENDPOINT OHNE VOLLSTAENDIGE OPERATION IM VERTRAG
```
An endpoint with no documented error response, no authorization information and no
Description is not a contract, but a guess.

## The contract

`docs/api/openapi.yaml` is the single source of truth. Everything else follows from this:
Schemas, DTOs, routes, client calls, tests.

| field | Rule |
|---|---|
| `operationId` | stable, camelCase, clear throughout the contract |
| `summary` | one line what the operation does |
| `description` | Boundary conditions, side effects, idempotence |
| `parameters` | including validation (min/max/format/enum) and origin (path/query/header) |
| `requestBody` | Required fields, formats, examples |
| `responses` | at least success **and** any error response |
| `security` | what role that needs — or explicitly `none` |
| `tags` | Feature area |

**The craft for this:** `api-design` (resource naming, status codes, pagination,
Error bodies, versioning, rate limiting) and `api-design-principles` (the
general REST principles).

## Procedure

### 1. Extract requirements

From `docs/requirements/PRD.md`, `.agent/artifacts/user-stories.md` and
`docs/requirements/features/*.feature` map each story to its operation. One
Story without surgery and surgery without story are both findings.

### 2. Check against the schema

Every field in request and response must exist in the database schema, with the same
Type and equal nullability. **Report discrepancies, the scheme is not tacit
change.** If the pattern is incorrect, that is a separate finding with its own ADR.

**The craft for this:** `database-migrations`, `postgresql-table-design`.

### 3. Determine the order

Sort by dependency: shared schemas first, then endpoints without
Dependency, then the aggregating ones. This means there is no half-finished interim result.

### 4. Write contract tests

Per operation: status code, mandatory fields, error format. **Answer with
undocumented fields is an error** — the test then fails until contract
and implementation match.

### 5. Implement

Route, validation, service call, error mapping, then the test. In this one
Order. Validation at the edge (request), business rules in the service, invariants
in the model.

## Error format — one form for everyone
```
json
{
  "type": "https://errors.example.com/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "title must not be empty",
  "instance": "/tasks"
}
```
| Status | When |
|---|---|
| 400 | syntactically incorrect request |
| 401 | not logged in |
| 403 | logged in but not authorized |
| 404 | Resource does not exist **or** is not visible to this organization |
| 409 | Conflict with current state |
| 422 | semantically invalid (validation) |
| 429 | Rate limit exceeded |
| 500 | unexpected error — no stack trace, no internal detail |

The detail message tells **what** to do, not just what went wrong.

## Comparison with traceability

After writing the contract and after writing the tests comes the reconciliation
the chain Requirement → Story → Feature → Test → Code:
```
bash
python3 .agent/tools/traceability_check.py
```
**A filled matrix is not evidence** — an entry that refers to a test,
that no one has written counts as a gap. See `aisdlc-traceability-check`.

## Rules

- Pagination, filtering and sorting are **part of the contract**, none
  Implementation details. Without them in the list, the list is not repairable.
- Client separation is part of the contract: each read and write path carries
  `organization_id` scope. If it is missing, that is a finding, not a detail.
- The contract will be frozen **after step 4** (end of the stress test), not after
  Gate 1. Gate 1 after step 2 checks the *direction* — the contract is still there
  Draft. In the first full run, the stress test found 13 contract defects, all **after**
  of freezing, and each of them has invalidated schema, code and tests.
  A deviation after the freeze requires a **return to step 2** - none
  silent change in the code, no new revision in passing.
- A field without a purpose and an operation without a purpose are removed, not documented.
- Error answers are mandatory. A contract without an error format is incomplete.

## Output

- `docs/api/openapi.yaml` — the frozen contract
- `.agent/artifacts/api-review.md` — Schema discrepancies and open questions
- Assignment Story → Operation, with gaps as a list (not as a percentage)

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| REST design, status codes, error bodies, versioning | `api-design` |
| REST principles, GraphQL vs REST | `api-design-principles` |
| Database types, migrations | `database-migrations`, `postgresql-table-design` |
| Contract as a source for both sides | `contract-first` |