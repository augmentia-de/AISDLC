---
name: aisdlc-grill-stress
description: Phase 1a/4: Query design tree, find deepening, check standards and spec axis.
metadata:
  origin: adapted from mattpocock/grilling + mattpocock/improve-codebase-architecture + mattpocock/codebase-design + wshobson/architecture-patterns
---

## When

After phase 2 (architecture), **before** implementation. Goal: Find architectural errors,
as long as they are still cheap to correct.

## 1. Query design tree

Intention is the root. Every decision depends on the decisions below.

**Frontier** = all decisions whose requirements have already been clarified - the questions
which you can **now** ask without inventing answers. Question the whole frontier
a round, number the questions and give your recommendation for each. Then wait for them
Answer before the next round begins.
```
text
❓ **F1** - <Fragetitel>: <Frage, ggf. mit Antwortoptionen>

➡️ <deine Empfehlung>

---

❓ **F2** - <Fragetitel>: <Frage>

➡️ <deine Empfehlung>
```
Phrase each question so that “yes” accepts your recommendation. A question whose answer is from
depends on a question that is still open in this round, belongs in a **later** round.

**Facts are your job, not the user's.** Does a Frontier question need one
Environmental fact (file system, tooling, existing code), then determine it yourself or
delegate it to a subagent - don't ask the user about it.

The session is finished when the frontier is empty: every branch attended, nothing silent
accepted. Only act when the user confirms the shared understanding.

## 2. Find deepening opportunities

Goal: convert flat modules into deep ones - more testable and AI-navigable.

**Deletion test:** Would deleting the module bundle the complexity or just shift it?
"Yes, it's bundling" is the signal you're looking for.

**Vocabulary** (from `codebase-design`): *Module*, *Interface*, *Depth*, *Seam*, *Adapter*,
*Leverage*, *Locality*. Use these terms exactly, not in "component",
"Service", "API" or "Boundary" drift.

**Check two axes separately** (Standards and specification must not mask each other):

| axis | Source | Finding |
|---|---|---|
| Standards | Repo conventions, linter, structure, naming, test coverage | |
| Specification | `docs/api/openapi.yaml`, `docs/requirements/features/*.feature`, ADRs | |

**Read sources first:** `GLOSSARY.md` (domain terms), `docs/adr/` (decisions that
not to be discussed again), `docs/architecture/` (current design).

**Friction signals to watch out for:**
- Understanding a concept requires jumping between many small modules
- Modules are **flat**: the interface is almost as complex as the implementation
- Pure functions were only extracted for testability, but the bugs lie in the interaction
- Tight coupling survives the seam
- Untested or difficult to test parts

**ADR conflicts:** If a proposal contradicts an existing ADR, only propose it
when the friction is real. Mark clearly in the report.

## 3. Result

**Report begins with a table**: Findings, Severity, Status, Where Resolved. The
The derivation is below. The reference run produced 67 KB of prose about the
Stress test – can be found, but cannot be evaluated. The table is the introduction
Prose is the evidence.

- Findings separated according to standards axis and specification axis
- Per finding: file: line, problem, impact, solution, recommendation strength
- Open decisions that people have to make
- No implementation until the common understanding is confirmed