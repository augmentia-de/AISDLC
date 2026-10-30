# Phases, instances and agents — the mapping

This document is the **Single Source of Truth** for mapping workflow steps,
Orchestrator instances, agents and skills. Previously there were three divergent ones
Phase models in the project (9 workflow steps, 4 agents, 2 prompt families) — that is
now brought together here in one place.

## The three levels

| level | What she describes | Where | Number |
|---|---|---|---|
| **Step** | A concrete work action | `.agent/workflows/` | 9 |
| **Instance** | A permanently running Orchestrator process (REST-enabled) | `.agent/prompts/multi-instance/` | 5 (phase0–4) |
| **Agent** | An OpenCode role that a user chooses directly | `.opencode/agents/` | 7 |

A step is **not** an instance and **not** an agent.

## Step → Instance → Agent → Skill

| step | Workflow file | instance | agent | Skill (Optional) | Gate | MVP | Enterprise | Remark |
|---|---|---|---|---|---|---|---|---|
| 0 legacy analysis | — | `phase0` | — | — | optional | optional | optional | Only if necessary |
| 0a Migration Assessment | `00-migration-assessment.md` | `phase0` | `aisdlc-requirements` | `module-migration`, `strangler-fig` | optional | optional | **yes** (if mode=migration) | Only with mode=migration/brownfield |
| 1 Requirements elicitation | `01-requirements.md` | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress`, `aisdlc-prd-to-stories`, `aisdlc-traceability-check` | — | **yes** | **yes** | Core - PRD is usually sufficient in MVP |
| 1a Change Letter (Delta) | `01a-change-brief.md` | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress` (opt.) | — | **yes** (hotfix/patch) | **yes** | Only for hotfix/patch/small feature |
| 1a Request-Grill-Me | (in 01) | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress` | — | optional | **yes** | Targeted use possible in MVP |
| 2 Concept & Architecture | `02-architecture.md` | `phase2` | `aisdlc-architecture` | `aisdlc-openapi-first`, `aisdlc-glossary-adr`, `aisdlc-migration-plan` | **GATE 1** after | minimal | **yes** | MVP: only minimal architectural overview, no full ADR sets if not necessary |
| 2b Migration Plan | `02b-migration-plan.md` | `phase2` | `aisdlc-architecture` | `aisdlc-migration-plan`, `strangler-fig`, `aisdlc-glossary-adr` | **GATE 1 (Migration)** | optional | **yes** (mode=migration) | Only with mode=migration/brownfield |
| 3 Simplify & Reduce | `03-simplify.md` | `phase2` | `aisdlc-architecture` | `aisdlc-simplify` | — | no | **yes** | Often skippable in MVP |
| 4 Grill-Me stress test | `04-grill-me-stress.md` | `phase2` | `aisdlc-architecture` | `aisdlc-grill-stress` | — | optional | **yes** | Recommended for external/public APIs, otherwise skippable in MVP |
| 5 Test Driven Setup | `05-tdd.md` | `phase3` | `aisdlc-execution` | `aisdlc-tdd`, `aisdlc-test-matrix`, `aisdlc-traceability-check` | — | optional | **yes** | MVP: focused testing, not necessarily a complete testing strategy |
| 5a Delta TDD | `05a-delta-tdd.md` | `phase3` | `aisdlc-execution` |`aisdlc-tdd` | — | **yes** (hotfix/patch) | **yes** | Only affected tests + regressions |
| 6 AI code generation | `06-implementation.md` | `phase3` | `aisdlc-execution` | `aisdlc-feature-scaffold`, `aisdlc-containerize` | — | **yes** | **yes** | core |
| 6a Delta implementation | `06a-delta-impl.md` | `phase3` | `aisdlc-execution` | `aisdlc-feature-scaffold` (opt.), `aisdlc-simplify` | — | **yes** (hotfix/patch) | **yes** | Only affected_paths |
| 7 Refactoring & Cleanup | `07-refactoring.md` | `phase3` | `aisdlc-execution` | `aisdlc-simplify` | — | optional | **yes** | In the MVP often part of step 6 |
| 8 Security & Compliance | `08-security.md` | `phase4` | `aisdlc-qa` | `aisdlc-security-scan` | **GATE 2** after | minimal | **yes** | MVP: at least Secrets/Dependency scan, no full compliance documentation |
| 8a Delta QA | `08a-delta-qa.md` | `phase4` | `aisdlc-qa` | `aisdlc-security-scan` | **GATE 2 (light)** | **yes** (hotfix/patch) | **yes** | Diff based, affected modules |
| 9 Deployment & Operations | `09-deploy.md` | `phase4` | `aisdlc-qa` | `aisdlc-ci-pipeline`, `aisdlc-containerize`, `aisdlc-deploy` | — | **yes** | **yes** | core |

**The skills are procedural steps, not roles.** The `aisdlc-*` skills provide
the **procedure** (when, which artifacts, which tests); the **craft** is coming
from the stack skills (`skills/`, vendorized). Both layers complement each other, they
do not replace each other. You will be contacted by the responsible agent
loaded when the step is pending - this means no additional agents are created and the
Context costs remain at 0 until the step is actually needed.

## Gate definition

| Gate | After step | Condition | Effect |
|---|---|---|---|
| **GATE 1** | 2 (Architecture) | PRD complete, architecture and decisions ok | No application code before human release. Checks the **direction**, not the finished contract. |
| **GATE 2** | 8 (Security) | QA 0 Critical, Security 0 Critical/High, **Traceability 0 Gaps** | No deployment before human release |
| **CUTOVER GATE** | 2b/9 (migration) | Routing ok, migration scripts idempotent, backout tested, monitoring available | No final cutover before human approval |

**The traceability condition** (`python3 .agent/tools/traceability_check.py` → 0 gaps)
belongs to GATE 2 because it can only be tested after step 5/6: one
Requirement with story needs a **proven** test. “Proven” means: the test
carries the acceptance criteria identifier and exists in the project. A filled matrix
is not a receipt — the receipt is the file.

The check runs **before** the gate. The matrix remained in the reference run for 15 hours
incomplete because no one looked until after step 7.

**Contract freezing point: after step 4** (end of stress test), not after
Gate 1. The first full run found 13 contract defects in the stress test — all **after** the
Freezing, and each of them has schema, code and tests invalidated. Before step 4 is
the contract a draft; after step 4 it is frozen. A change after thatrequires a return to step 2 — the contract is never changed in passing.

Gates are located in `AGENTS.md` and are referenced by the workflow files. The
Multi-instance prompts contain the same gates at the same step boundaries.

**Agent change at gate:** The agent of the completed step remains active until the
Human gave the release. Only then does the person switch to the agent of the
next step (“Agent” column at the top). Corrections land after a gate query
so with the same agent with the same write permissions - and the write permissions of the
The next step is only active when it actually begins.

## Commit per step

Each step produces **exactly one** commit. The commit occurs **before** the gate —
the gate checks the finished commit. Without a commit, the step is not completed.

| step | agent | Type | Subject | Paths |
|---|---|---|---|---|
| 0 legacy analysis | `phase0` | docs | `docs(sdlc-0): legacy analysis` | `docs/00_legacy_analysis/`, `.agent/artifacts/` |
| 0a Migration Assessment | `aisdlc-requirements` | docs | `docs(sdlc-0): Migration Assessment and Legacy Inventory` | `docs/migration/`, `.agent/artifacts/` |
| 1 Requirements elicitation | `aisdlc-requirements` | docs | `docs(sdlc-1): PRD, User Stories and Traceability Matrix` | `docs/requirements/`, `.agent/artifacts/` |
| 1a Change Letter (Delta) | `aisdlc-requirements` | docs | `docs(sdlc-fix): Change letter and affected paths` | `docs/execution/`, `.agent/artifacts/` |
| 2 Concept & Architecture | `aisdlc-architecture` | docs | `docs(sdlc-2): API contract, domain model and ADRs` | `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` |
| 2b Migration Plan | `aisdlc-architecture` | docs | `docs(sdlc-2b): Migration plan and cutover strategy` | `docs/migration/`, `docs/adr/`, `.agent/artifacts/` |
| 3 Simplify & Reduce | `aisdlc-architecture` | refactor | `refactor(sdlc-3): Architecture simplified` | `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` |
| 4 Grill-Me stress test | `aisdlc-architecture` | docs | `docs(sdlc-4): Stress test report and open decisions` | `docs/architecture/`, `.agent/artifacts/` |
| 5 Test Driven Setup | `aisdlc-execution` | test | `test(sdlc-5): Test strategy and failing tests` | `tests/`, `.agent/artifacts/` |
| 5a Delta TDD | `aisdlc-execution` | test | `test(sdlc-fix): Delta tests and regression cases` | `tests/`, `.agent/artifacts/` |
| 6 AI code generation | `aisdlc-execution` | feat | `feat(sdlc-6): implementation of the stories` | `src/`, `tests/`, `docs/execution/` |
| 6a Delta implementation | `aisdlc-execution` | feat | `feat(sdlc-fix): Delta implementation` | `src/`, `tests/`, `docs/execution/` |
| 7 Refactoring & Cleanup | `aisdlc-execution` | refactor | `refactor(sdlc-7): Refactoring and clean-up` | `src/`, `tests/`, `.agent/artifacts/`|
| 8 Security & Compliance | `aisdlc-qa` | docs | `docs(sdlc-8): Security Audit and QA Report` | `tests/`, `docs/qa/`, `.agent/artifacts/` |
| 8a Delta QA | `aisdlc-qa`| docs|`docs(sdlc-fix): Delta QA and Security Report` | `tests/`, `docs/qa/`, `.agent/artifacts/` |
| 9 Deployment & Operations | `aisdlc-qa` | build | `build(sdlc-9): deployment pipeline and deployment report` | `docs/execution/`, `infra/`, `.github/`, `.agent/artifacts/` |

**Body format:** `Step <n> - <step name> | Agent: <agent-id> | Artifacts: <paths>`

**Command:**
```
bash
mkdir -p <pfade>
git reset -q
git add -A -- <pfade>
git commit -m "<Subject>" -m "<Body>"
```
The scope is set via the **index**, not via a path specification.
`git reset -q` clears the index, `git add -A -- <paths>` only stages the step,
`git commit` only commits the index.

Two reasons why not `git commit -- <paths>`:
- `git commit -- <paths>` aborts as soon as a scope path does not contain any tracked files
  has ("pathspec did not match any files") - for empty target folders this would be
  This step is not committable.
- The route via `--pathspec-from-file` is broken under the global `rtk` plugin:
  `rtk` rewrites `git diff` and appends a "Changes:" block that appears as
  Path specification in turn terminates with "empty string is not a valid pathspec".

`git reset -q` unstages someone else's work, but doesn't delete it.

Rules:
- Exactly one commit per step. If nothing has changed, do not commit
  (`git commit` then reports "nothing to commit" — that's not an error).
- **Exception: Addition:** If a step corrects the artifacts of an earlier step,
  **an** additional commit is created with the subject of the **corrected** step
  plus `(follow-up)`. The body states the trigger step and the reason. **At most
  one** follow-up per step - a second means: the cause lies in the process, not
  in the artifact. There were two in the EchoBox run, both in step 1.
- Only commit the paths of the step - external changes are never committed.
- No `git push` — the pipeline does not push.
- The raw input of the step (e.g. the request email) belongs in the same commit
  like the artifact created from it - otherwise the traceability will come to nothing.

## Traceability: two halves, two steps

| helped | Content | Originates in | Artifact |
|---|---|---|---|
| **static** | Requirement → Story → Feature | Step 1 | `docs/requirements/traceability_matrix.json` |
| **dynamic** | Requirement → Test → Code | Step 5/6, new at any time | `.agent/artifacts/traceability.md` |

The matrix carries **only** the static half. The dynamic comes from the
File system detected (`traceability_check.py --fill`). It was in the reference run
Matrix over the entire run a framework (0/89 with test reference) and was only
filled after step 7 - because `tests`/`code` only knows step 5/6, the file
but belongs to the requirements agent.

**AC identifiers (E):** Each criterion carries `AC-<story>-<nr>` — in the feature scenario
as a comment, in the story JSON as `id`, in the test case as a method name. Just with that
the assignment is mechanical and not guessed.

## Profiles between the steps

The orchestrator writes `.agent/artifacts/briefing.json` at the end of each step and
commit him. It is the **entry point** of the next step: the
Role agent reads the briefing first, then only the named files. The briefing
is **never** the truth — the files are. No excerpts, no derivations.

## Running log

`.agent/artifacts/run-log.jsonl` — **one JSON line per event**, append-only.
writtenjust from `aisdlc-orchestrator`; he is the only one who has step limits and
Gates sees. Events: `step_start`, `step_end`, `jump_back`, `gate`, `check`,
`retry`. Format and justification: `AGENTS.md` (Rule 14).

##Tools

Phase tools: `.agent/tools/` (e.g. `traceability_check.py`).
Code generators: `src/<modul>/tools/`. Convention: `.agent/tools/README.md`.

## Operating modes

| Fashion | Prompt Family | Usage |
|---|---|---|
| **One-orchestrator** | `.agent/prompts/orchestrator/` | local, interactive, an agent changes steps |
| **Multi-Instance** | `.agent/prompts/multi-instance/` | Server operation, 0-4 parallel phases, REST control |

Both families describe **the same steps** — they just differ in that
Activation and session handling.

## Agents

| agent | ID | role | Write permissions |
|---|---|---|---|
| Requirements Orchestrator | `aisdlc-requirements` | Phase 1 + 1a | `docs/requirements/` |
| Architecture Orchestrator | `aisdlc-architecture` | Phase 2-4 | `docs/architecture/`, `docs/api/`, `docs/adr/` |
| Execution Orchestrator | `aisdlc-execution` | Phase 5-7 | `src/`, `tests/`, `docs/execution/` |
| QA Orchestrator | `aisdlc-qa` | Phase 8-9 | `tests/`, `docs/qa/` |
| Java/Spring Backend | `stack/java/backend` | Implementation | `src/`, `tests/`, `docs/execution/` |
| React/TypeScript Frontend | `stack/react/frontend` | Implementation | `src/`, `tests/`, `docs/execution/` |
| *(Original)* | `build` | Generalist, all rights | everywhere |

**Default agent:** `aisdlc-requirements` (in `.opencode/opencode.jsonc`).

## Stack layer

The framework is stack agnostic. Everything stack specific is in
`opencode.jsonc` in the project root: `skills.paths`, `skill`-Allowlist and
Stack agents at `.opencode/agents/stack/<tech>/`.

**Merge rule (OpenCode):** Root config first, `.opencode/` config after,
Agent front matter last — last hit wins. From this it follows: the
Catch-all-deny is in the **stack layer**, the positive rules of the
Frameworks in `.opencode/opencode.jsonc`.

## Stack levels

Stack knowledge comes directly from the libraries via `skills.paths`** - no copying,
no drift. Because ECC 311 and wshobson 185 have skills, they filter
`skill` permission on stack prefixes.

## Write your own skill (convention)
```
markdown
---
name: aisdlc-<verfahren>              # Präfix aisdlc- → wird in opencode.jsonc erlaubt
description: <Was>. Nutze diesen Skill in Phase <n> (<Schritt>).
metadata:
  origin: adaptiert aus <quellen>     # Provenienz
---
```
Rules: `name` = directory name; `description` tells **when** the skill takes effect (that is
the model selection criterion); Formulas stack-independently; `metadata.origin`
the sources. After creation: `name` in the `skill` allowlist of `opencode.jsonc`
supplement.