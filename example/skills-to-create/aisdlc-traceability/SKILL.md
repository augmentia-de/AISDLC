---
name: aisdlc-traceability
description: Builds and checks the requirement-to-test-to-code traceability matrix. Use this skill at the end of step 1 to seed the matrix and in step 5 to verify that every requirement is covered by a test.
metadata:
  origin: draft for AISDLC
---

##Purpose

Make coverage provable: every requirement must be traceable to a test, and every test to a
piece of code. Without the matrix, "we tested it" is a claim, not a fact.

## When

- End of step 1 — seed the matrix from the stories.
- Step 5 — verify coverage before implementation starts.
- Before gate 1 — the reviewer needs the gaps list.

##Inputs

- `docs/requirements/stories/*.json`
- `docs/requirements/features/*.feature`
- `docs/api/openapi.yaml`
- Test files under `tests/`

## Procedure

1. **Collect the requirement ids** — every id that appears in a story or a feature.
2. **Map to tests** — for each acceptance criterion, find the test that covers it. A
   criterion with no test is a gap; a test with no criterion is dead weight.
3. **Map to code** — for each test, the module under test. A test that cannot be attributed
   to a module is reported as unattributed.
4. **Check the inverse** — every test file must be reachable from at least one criterion.
   Orphaned tests are usually the residue of a removed feature.
5. **Report gaps** — as a list, not as a percentage. A matrix that is 87% complete
   less useful than a list of the 13 missing criteria.

## Output

`docs/requirements/traceability_matrix.json`:
```
json
{
  "generated_at": "2026-01-01T00:00:00Z",
  "entries": [
    {
      "requirement_id": "FR-012",
      "story_id": "US-003",
      "acceptance_criteria": ["AC-003-1"],
      "features": ["docs/requirements/features/task-crud.feature"],
      "tests": ["tests/integration/test_task_create.py::test_create_task"],
      "code": ["src/tasks/service.py", "src/tasks/router.py"]
    }
  ],
  "gaps": [
    { "requirement_id": "NFR-004", "reason": "no test covers the 90-day retention job" }
  ]
}
```
Plus `.agent/artifacts/traceability.md` — the human-readable gap list for the gate review.

##Rules

- Only reference tests that exist. Do not plan coverage in the matrix.
- A criterion that cannot be automated is marked `manual`, not silently dropped.
- Gaps are blockers for gate 1, not a footnote.