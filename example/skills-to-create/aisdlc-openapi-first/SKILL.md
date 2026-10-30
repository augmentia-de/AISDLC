---
name: aisdlc-openapi-first
description: Drives contract-first work - from an OpenAPI specification to generated stubs, DTOs, routes and contract tests. Use this skill in step 2 to review the contract and in step 6 to implement against it without changing it.
metadata:
  origin: draft for AISDLC
---

##Purpose

Keep the API contract the single source of truth. Implementation follows the contract; the
contract only changes through an explicit revision step.

## When

- Step 2 — review and complete `docs/api/openapi.yaml` before it is frozen.
- Step 6 — implement endpoints from the contract, never from memory.

##Inputs

- `docs/api/openapi.yaml`
- `.agent/knowledge/schema.sql` (field types must agree with the schema)
- `docs/adr/` (constraints that apply to the API)

## Procedure

1. **Review the contract** — every operation has: id, summary, request, response, status
   codes, error shape, auth requirement. Missing error responses are the most common defect.
2. **Align with the schema** — every field in a request/response exists in the database
   schema, with the same type and nullability. Report mismatches; do not fix the schema
   silently.
3. **Derive the implementation order** — group operations by dependency: shared schemas
   first, then leaf endpoints, then the aggregating ones.
4. **Implement per operation** — route, validation, service call, error mapping, then the test.
5. **Contract tests** — assert status code, required fields, and the error shape. A reply
   with extra undocumented fields is a failure.
6. **Never edit the contract during implementation.** If something cannot be expressed,
   stop and go back to step 2.

## Error format

Use one shape everywhere:
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
## Output

- Revised `docs/api/openapi.yaml` (step 2)
- Implemented endpoints in `src/` (step 6)
- Contract tests under `tests/`
- `.agent/artifacts/api-review.md` — mismatches and open questions

##Rules

- The contract is frozen at gate 1. Deviations need a new revision of the file, not an
  implicit change in the code.
- Every operation states its authorization requirement explicitly.
- Pagination, filtering and sorting parameters are part of the contract, not an
  implementation detail.