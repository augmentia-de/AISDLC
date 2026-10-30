---
description: Phase 5a — Delta TDD (only affected tests + regression cases)
---

# Delta TDD workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- `docs/execution/change-brief-<id>.md` exists.
- `affected_paths` set in briefing.

## Agent & Skill

- **Agent:** `aisdlc-execution`
- **Skill:** `aisdlc-tdd`

##Steps

1. **Identify affected tests** — grep for callers/imports of changed files.
2. **Regression testing** — at least one existing scenario that was potentially affected.
3. **New Tests** — for the new ACs from the change letter.
4. **Failing tests first** — Write tests, then adapt code (TDD).
5. **Traceability (light)** — only changed ACs must have evidence.

## Output

- Tests in `tests/` (only affected modules + new ones).
- `.agent/artifacts/test-report.md` (Delta scope).

##Rules

- No full test strategy, no complete test matrix.
- No changing `openapi.yaml` or external/public contracts.
- Regression testing against at least one existing scenario is mandatory.

## Commit
```
bash
mkdir -p tests/ .agent/artifacts/
git reset -q
git add -A -- tests/ .agent/artifacts/
git commit -m "test(sdlc-fix): Delta-Tests und Regressionsfälle" \
  -m "Schritt 5a - Delta TDD | Agent: aisdlc-execution | Artefakte: tests/, .agent/artifacts/"
```
- If nothing has changed: do not create a commit (no error).