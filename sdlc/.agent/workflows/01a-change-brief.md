---
description: Phase 1a — Change brief (delta flow for hotfix/patch/small feature/refactor)
---

# Change letter workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- `.agent/config.json` has `change_flow.enforce_classification: true`.

## Agent & Skill

- **Agent:** `aisdlc-requirements` (or `aisdlc-execution` for pure technical fixes)
- **Skill:** (optional) `aisdlc-grill-stress` in case of uncertainty

## Change types

| Type | Meaning | Acceptance |
|---|---|---|
| `hotfix` | broken behaviour | new ACs (minimum) |
| `patch` | small correction | new ACs (minimum) |
| `feature` | small extension | new ACs (minimum) |
| `refactor` | behaviour-preserving restructure | **no new ACs** — the existing tests are the acceptance |

`refactor` is the only type without new acceptance criteria: it changes structure,
naming and dead code, never behaviour. It requires `contract_impact: none`,
`breaking: false` and a green baseline.

## Steps

1. **Problem** — What is wrong/done? Reproduction or requirement.
2. **Actual/Target** — Short, not a full PRD.
3. **Acceptance Criteria (Minimum)** — 1-5 ACs, `AC-<id>-<no>`.
   For `refactor`: **none** — instead record the baseline (commit hash, test suite green)
   and the behaviour contract (public API, contract and schema unchanged).
4. **Affected Files** — `affected_paths` in the briefing.
5. **Regression Risk** — Which existing scenarios could break.
6. **Test Plan** — Which tests must be green.

## Output

- `docs/execution/change-brief-<id>.md`
- Briefing updated with `change` block.

## Rules

- No full PRD, no story JSONs, no traceability matrix change (except new ACs).
- If `contract_impact` `external/public` or `openapi.yaml` affected → **no delta flow**, full phases 1-9.
- If `breaking: true` → full phases 1–9.
- `refactor` with `contract_impact` other than `none` or `breaking: true` → **no delta flow**.

## Commit
```
bash
mkdir -p docs/execution/ .agent/artifacts/
git reset -q
git add -A -- docs/execution/ .agent/artifacts/
git commit -m "docs(sdlc-fix): Change-Brief und betroffene Pfade" \
  -m "Schritt 1a - Change-Brief | Agent: aisdlc-requirements | Artefakte: docs/execution/, .agent/artifacts/"
```
- If nothing has changed: do not create a commit (no error).