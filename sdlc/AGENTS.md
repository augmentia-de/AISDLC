# AGENTS.md — AI SDLC Orchestrator Framework

## Project Overview

**Two layers.** Framework (`.opencode/`, `.agent/`, `AGENTS.md`) is stack agnostic.
Stack layer in `opencode.jsonc` (root) is replaced per stack; the framework remains
untouched. Details: `how-to.md` → “stack layer”.

AI SDLC: AI agent-assisted software development cycle. The orchestrator controls it
Process, OpenCode executes. Write rights per agent, phase assignment in
`.agent/PHASES.md` — SSoT, in case of objection it applies.

## Behavior Rules

1. Always read this file before starting any task. Read the briefing first (rule 12). **Read profile first:** load `.agent/config.json` (profile `mvp` or `enterprise`). The orchestrator is the gatekeeper for profile-based behavior.
2. Write all output artifacts to the designated directories (see Folder Ownership).
3. Never modify files outside your assigned folder (see Folder Ownership).
4. Never delete files without explicit human approval.
5. Write atomic commits: **exactly one commit per SDLC step** (see Commit Convention).
6. Flag any security risk with `[SECURITY]` prefix in your response.
7. Always re-read this file after context compaction or session reset.
8. Load the phase skill when the step calls for it (see Phase Skills) - do not improvise.
9. **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no
    result. Use `read`, `glob`, `grep`. In the first full run, 591 of 1,094 shells
    calls (54%) were of this kind.
10. **Read the briefing first.** `.agent/artifacts/briefing.json` (Template: `.agent/templates/briefing.json`) names the files this
    step needs. Read only those, not the repository. The briefing is the entry point -
    never the truth (see briefing).
11. **Log structured, never payload.** One event per line: correlation ID, event type,
    tenant, outcome, duration. Never payload data, secrets, tokens, PII. convention
    in `.agent/artifacts/logging.md`; the security scan checks against it.
12. **Write the run log.** Every step start/end, gate, check and jump-back as one JSON
    line in `.agent/artifacts/run-log.jsonl`. Only the orchestrator writes it.
12a. **Log skill invocations (debug).** Every call to an `aisdlc-*` skill is logged as
    `skill_start`/`skill_end` event logged in `run-log.jsonl` (Helper:
    `.agent/tools/skill_log.py <skill> <agent> <step> [start|end]`). So it is understandable
    which skills really worked.
13. **Big artifacts start with a table.** Artefact > ~20 KB: table on top (finding,
    severity, status, where fixed). Prose stays below. Table is entry point, prose
    is proof.

## HARD GATES - Agent Must Stop and Await Human Approval

- **After Step 2** (Architecture complete) - before any application code is written.
  The agent must produce all artifacts, present a summary, and wait for human approval.
  This gate reviews the **direction**: architecture, decisions, ADR set. The contract
  there is still a draft here.
- **After Step 8**(Security & Compliance complete) - before Deploy runs.
  The agent must present QA and Security findings and wait for human approval.
- **CUTOVER-GATE** (only with mode=migration/brownfield, before final switching).
  Routing, idempotent migration scripts, tested backout and monitoring must
  be detectable. No final cutover without human approval.

Without approval, the pipeline does not advance. No exceptions.

**Profile-aware Gates:** Respect `.agent/config.json`. In `profile: mvp`, gates can be presented in shortened form, but **Folder-Ownership** and **atomic commits** remain mandatory. In `profile: enterprise`, full governance applies.

**Traceability is a gate condition, not a footnote.** The check
`python3 .agent/tools/traceability_check.py` must report **zero gaps** before
GATE 2 in `traceability: full` with `gates.traceability_hard_fail: true`. A gap means: a requirement with a story has no provable test, or a test
that proves nothing. The check compares the artifacts against the file system - a
filled matrix is not a proof, the existing file is. The check runs **before** the
gate. 

**Soft/Hard Fail:** In `traceability: light` or when not all gates are hard-failing, the orchestrator must present a traceability report with gaps and allow human approval ("accept gaps & proceed") when appropriate. Only hard-fail for `external/public` contracts with full traceability.

**Contract freeze.** The contract `docs/api/openapi.yaml` is **frozen after step 4**
(the stress test), not after gate 1. Consult `docs/architecture/contract-matrix.md` for classification (`internal|external|public`). Changes after freeze require a jump back to step 2 **only for `external/public`**; for `internal` use a justified change with impact analysis. 13 contract defects were found in the stress test, every one of them after the freeze.

**Agent change at the gate:** the agent of the finished step remains active until the human
approves. Only then does the human switch to the agent of the next step.

## Briefing - the profile between the steps

The orchestrator writes `.agent/artifacts/briefing.json` (template: `.agent/templates/briefing.json`) at the end of each step and
commit him. It is the **entry point** of the next step:

| field | Content |
|---|---|
| Stand | last step, commit, next step, agent |
| What you need to read | the files — and nothing else |
| Decisions that apply | F-x / ADR-x that bind the step |
| Open points | O-xx with Owner |
| What you don't do | e.g. E.g. “the contract is frozen” |

Rules: Role agent reads the briefing **first**, then only the named files.
The letter is **never** the truth — the files are. No excerpts.


## Commit Convention

**After every SDLC step exactly one commit.** A step is not finished until its commit
exists. The commit is the checkpoint the human reviews at the gate.

| Field | Rule |
|---|---|
| Subject | `<type>(sdlc-<n>): <Short description exercise>` |
| Bodysuit | `Step <n> - <step name> \| Agent: <agent-id> \| Artifacts: <paths>` |
| Scope | index-based: `git reset -q` → `git add -A -- <paths>` → `git commit`. Only the step scope is staged. |
| Order | commit **before** the gate — the gate reviews the finished commit |
| Jump back | a step that corrects an earlier step's artifacts produces **one** extra commit with the subject of the **corrected** step plus `(Nachzug)`. Maximum one per step. |
| Empty | if nothing changed, no commit — no empty commits |
| Push | the pipeline never pushes — `git push` is not part of the pipeline |
```
bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```
Types: `docs` Requirements/Architecture/Security · `refactor` Simplify/Refactoring ·
`test` TDD · `feat` Implementation · `build` Deploy.

Subject, paths and command per step: `.agent/PHASES.md` -> "Commit per step".

## Phase Skills

Procedural steps are loaded as skills, not as their own agents:

| Step | Skill | Loading from |
|---|---|---|
| 1a | `aisdlc-grill-stress` | `aisdlc-requirements` |
| 3 | `aisdlc-simplify` | `aisdlc-architecture` |
| 4 | `aisdlc-grill-stress` | `aisdlc-architecture` |
| 5 | `aisdlc-tdd` | `aisdlc-execution` |
| 7 | `aisdlc-simplify` | `aisdlc-execution` |
| 8 | `aisdlc-security-scan` | `aisdlc-qa` |
| 9 | `aisdlc-deploy` | `aisdlc-qa` |

Additionally available: `strangler-fig`, `module-migration`, `test-self-correction`.

## Folder Ownership

| Folders | Owner (exclusive write) | Reading Agents (Read-Only) |
|---|---|---|
| `docs/requirements/` | Requirements Orchestrator | Architecture, Execution, QA, OpenCode |
| `docs/architecture/`, `docs/api/`, `docs/adr/` | Architecture Orchestrator | Execution, QA, OpenCode |
| `src/` | OpenCode (build phase) | Requirements, Architecture, QA |
| `tests/` | OpenCode (test phase) | QA, Execution |
| `docs/execution/` | Execution Orchestrator | QA, OpenCode, humane. Reviewer |
| `docs/qa/` | QA Orchestrator | Execution, OpenCode (Self-Correction) |
| `coverage/` | CI/Test Runner | QA, Execution |
| `.agent/artifacts/` | responsible role agent of the step | All (Read Only) |
| `.agent/artifacts/run-log.jsonl` | `aisdlc-orchestrator` | All (Read Only) |
| `.agent/knowledge/` | Architecture Orchestrator | All (Read Only) |
| `how-to/` | Human Reviewer | All (Read Only) |
| `.opencode/` | OpenCode system | All (Read Only) |

**`.agent/artifacts/`** is the canonical destination of the step artifacts
(`requirements.md`, `user-stories.md`, `risk-register.md`, `architecture.md`,
`folder-structure.md`, `test-report.md`, `security-audit.md`, `deploy-report.md`,
`briefing.json`, `logging.md`, `traceability.md`, `traceability-fill.md`,
`run-log.jsonl`). Each step writes its own files; are allowed to write there
the four role agents. A role agent whose workflow names this as a target,
but doesn't have the right to write, otherwise writes to a place that no one knows
finds.

**Mirror rule for the database schema:** `.agent/knowledge/schema.sql` is
canonical, `docs/architecture/schema.sql` the versioned copy — both
byte-identical, enforced by `SchemaMirrorIT` (T-SCHEMA-MIRROR-01). To agent,
who changes the schema changes **both** files in the same step (ADR-019 §6,
O-13/S-26).

**Reference rule for the API contract:** `docs/api/openapi.yaml` is the only one
contract. `.agent/knowledge/openapi.yaml` is a reference to it, not a copy.

## Escalation

- `REQUIREMENT_GAP`: Requirement incomplete → return to phase 1.
- `ARCHITECTURAL_BLOCKER`: design unfeasible → return to phase 2.
- Git reset of the affected `src/` files in ARCHITECTURAL_BLOCKER.
- **Fast Track/Patch (Delta Flow):** For `profile:mvp` and `allow_fast_track: true` as well as `change_flow.allow_delta_for_hotfix_patch: true`, hotfix/patch/feature (type in {hotfix, patch, feature}, contract_impact in {none, internal}, scope in {local, module}, breaking=false) may be used via the delta path (1a → 5a → 6a → 8a) run. **Never** for `contract_impact in {external, public}` or changes to `docs/api/openapi.yaml`. Contract-Impact is first checked against `docs/architecture/contract-matrix.md`. In `profile: enterprise`, delta flow is only allowed if explicitly enabled.

**Migration (mode=migration/brownfield):** Contract freeze does not apply to adapters/ACL between old and new. Only new external/public contracts are frozen. Migration requires CUTOVER-GATE (routing, idempotent migration scripts, tested backout, monitoring) before final switchover.

##Secrets

- All environment variables in `.env` (never hardcoded). `.env` is in `.gitignore`.
- Secret scan in CI/CD (`gitleaks` or `trufflehog`). No credentials in
  Source code, config files, logs, Dockerfiles or pipelines.
- In `profile: mvp` enforce at least a minimal secrets/dependency scan (diff-based). In `profile: enterprise` full security requirements apply.

## Output

- Each task ends with a summary artifact in the associated directory.
- Large artifacts (> ~20 KB) start with a table (see rule 15).

## Tech Stack

See `.agent/knowledge/stack.md` — the decision is made in phase 2 and
documented in `docs/adr/ADR-001-tech-stack.md`. The default in this file is
Framework, not the decision.

##Rights matrix

See **Folder Ownership** above — a single source, not a second copy.
The technical enforcement is in the 'permissions' of the Agent-Frontmatter
(`.opencode/agents/`) and in `.opencode/opencode.jsonc`.