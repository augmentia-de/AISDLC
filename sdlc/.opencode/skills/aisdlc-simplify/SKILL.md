---
name: aisdlc-simplify
description: Phase 3/7: Simplify code to preserve behavior, remove dead code.
metadata:
  origin: adapted from ECC/code-simplifier + ECC/refactor-cleaner + wshobson/refactor-clean + wshobson/tech-debt
---

## When

- **Phase 3 (Simplify & Reduction):** before the Grill-Me stress test, so that only essential things are tested.
- **Phase 7 (Refactoring & Clean-up):** second pass on the finished code - you can see there
  defensive code that was replaced by tests and abstractions that didn't work.

## Principles

1. Clarity over cleverness.
2. Consistency with repo style, not personal style.
3. Preserve behavior exactly - the simplification must not change anything.
4. Only simplify where the result is **provably** easier to maintain.

## Simplify goals

**Structure**
- extract deeply nested logic into named functions
- replace complex conditions with early returns where this is clearer
- resolve chained callbacks/then chains
- remove dead code and unused imports

**Readability**
- descriptive names instead of abbreviations
- no nested ternaries
- break down long chains into intermediate variables
- Destructuring if it makes access clearer

**Quality**
- Remove debug output
- remove commented code
- merge duplicate logic
- resolve over-abstracted single-use helpers

## Dead code detection (stack agnostic)

Use static analysis of the project, e.g. E.g. `knip`, `depcheck`, `ts-prune`, `vulture`,
`jdeps`, `cargo-machete` or the analysis tool of the respective language. If none
is present: import and reference search (`grep`) as a replacement.

Classify results by risk:

| Risk | Meaning |
|---|---|
| **SAFE** | unused exports, imports, dependencies, files |
| **CAREFUL** | dynamic imports, reflection, registration via string |
| **RISKY** | Part of a public API, referenced from outside |

## Process

1. **Analyze** - Run detection tools in parallel, classify results.
2. **Verify** - search for all references for each candidate (including dynamic ones),
   Check public API, view Git history for context.
3. **Safely Remove** - SAFE first only, one category per pass
   (Dependencies → Exports → Files → Duplicates), run tests after each batch.
4. **Merge Duplicates** - keep the best implementation (most complete,
   best tested), adjust imports, delete excess, check tests.

## Technical Debt

- Take inventory, rate each debt item according to impact, prioritized list
  generate estimated effort.
- Don't fix what lies outside the current order - document it instead of leaving it lying around.

## Safety rules

- No change in behavior, not even an “obvious” improvement in the technical logic.
- Delete nothing, without evidence: no reference in the code, no reflection, none
  Configuration, no external use.
- No configuration or formatting changes as a side effect.
- Do not remove a dependency without proving usage.
- Run tests after each step and report the result.

## Output

- List of simplifications with file:line and reason
- List of removed dead code with receipt (no reference found)
- Proof of verification (tests, coverage before/after)
- Remaining technical debt with priority