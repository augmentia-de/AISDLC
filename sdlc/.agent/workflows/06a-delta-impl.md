---
description: Phase 6a — Delta implementation (slim, focused on affected paths)
---

# Delta Implementation Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- `docs/execution/change-brief-<id>.md` exists.

## Agent & Skill

- **Agent:** `aisdlc-execution`
- **Skill:** `aisdlc-feature-scaffold` (only if needed), `aisdlc-simplify`

##Steps

1. **Implement** only the files mentioned in `affected_paths`.
2. **No parallel streams** — focused single stream.
3. **Lint/Build** after each step.
4. **No contract change** — return to phase 2 if necessary.
5. **README** not mandatory (only for user-relevant changes).

## Output

- Code in `src/`, `tests/`.
- `.agent/artifacts/` (Delta report).

##Rules

- Change only `affected_paths`; everything else read-only.
- No refactoring outside of the fix scope (that's step 7).
- No `openapi.yaml` change without jump-back.

## Commit
```
bash
mkdir -p src/ tests/ docs/execution/
git reset -q
git add -A -- src/ tests/ docs/execution/
git commit -m "feat(sdlc-fix): Delta-Implementierung" \
  -m "Schritt 6a - Delta-Implementierung | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/"
```
- If nothing has changed: do not create a commit (no error).