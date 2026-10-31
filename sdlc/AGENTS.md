# AGENTS.md — AI SDLC Orchestrator Framework

## Overview

Two layers. Framework (`.opencode/`, `.agent/`, `AGENTS.md`) is stack-agnostic. Stack layer in root `opencode.jsonc` is replaced per stack; framework stays untouched. Details: `docs/how-to.md`.

AI SDLC: orchestrator steers the process, OpenCode executes. Writes are folder-scoped (see Folder Ownership). Phase mapping: `.agent/PHASES.md` (SSoT).

## Behaviour Rules

1. Read this file and the briefing first. Read `.agent/config.json` (`mvp`/`enterprise`). Orchestrator is gatekeeper.
2. Write artifacts only to designated dirs.
3. Never modify files outside your assigned folder.
4. Never delete without human approval.
5. One commit per SDLC step.
6. Flag security risks with `[SECURITY]`.
7. Re-read after compaction/reset.
8. Load the phase skill when the step calls for it.
9. No output commands (`echo`/`printf`/`pwd`/`cat`). Use `read`/`glob`/`grep`.
10. Read briefing first (`.agent/artifacts/briefing.json`).
11. Log structured, never payload. Convention: `.agent/artifacts/logging.md`.
12. Run log: one JSON line per event in `.agent/artifacts/run-log.jsonl`. Only orchestrator writes.
12a. Log skill invocations via `.agent/tools/skill_log.py`.
13. Artefacts > ~20 KB start with a table.

## Hard Gates

- **After Step 2** (Architecture): human approval before code.
- **After Step 8** (Security): human approval before deploy.
- **CUTOVER-GATE** (migration/brownfield): routing, idempotent migrations, tested backout, monitoring.
- **Traceability**: `traceability_check.py` → 0 gaps before GATE 2 when `traceability: full` & `traceability_hard_fail: true`.
- **Contract freeze** after Step 4 (stress test). Later changes: jump back to Step 2 for `external/public`; `internal` uses justified change + impact analysis.

## Briefing

`.agent/artifacts/briefing.json` (template `.agent/templates/briefing.json`) is written by the orchestrator at the end of each step and committed. Entry point for the next step; never the truth (files are).

## Commit Convention

One commit per step, before the gate.

- Subject: `<type>(sdlc-<n>): <description>`
- Body: `Schritt <n> - <name> | Agent: <id> | Artefakte: <paths>`
- Scope: `git reset -q` → `git add -A -- <paths>` → `git commit`
- Jump-back: one extra commit with the corrected step's subject + `(Nachzug)` (max one).
- No empty commits, no push.

## Phase Skills

Skills are procedures, not roles. Verfahren from `aisdlc-*`, craft from stack skills. Mapping and loading: `.agent/PHASES.md`.

## Folder Ownership

| Folder | Owner (exclusive write) | Readers (read-only) |
|---|---|---|
| `docs/requirements/` | Requirements Orchestrator | Architecture, Execution, QA, OpenCode |
| `docs/architecture/`, `docs/api/`, `docs/adr/` | Architecture Orchestrator | Execution, QA, OpenCode |
| `src/` | OpenCode (Build) | Requirements, Architecture, QA |
| `tests/` | OpenCode (Test) | QA, Execution |
| `docs/execution/` | Execution Orchestrator | QA, OpenCode, human reviewer |
| `docs/qa/` | QA Orchestrator | Execution, OpenCode (self-correction) |
| `coverage/` | CI/Test-Runner | QA, Execution |
| `.agent/artifacts/` | step's role agent | all (read-only) |
| `.agent/artifacts/run-log.jsonl` | orchestrator | all (read-only) |
| `.agent/knowledge/` | Architecture Orchestrator | all (read-only) |
| `how-to/` / `docs/how-to.md` | human reviewer | all (read-only) |
| `.opencode/` | OpenCode system | all (read-only) |

`.agent/artifacts/` is the canonical target for step artifacts. `schema.sql` is canonical in `.agent/knowledge/`, mirrored byte-identically in `docs/architecture/schema.sql` (enforced by `SchemaMirrorIT`). `docs/api/openapi.yaml` is the only contract; `.agent/knowledge/openapi.yaml` is a reference, not a copy.

## Escalation

- `REQUIREMENT_GAP` → jump to Phase 1.
- `ARCHITECTURAL_BLOCKER` → jump to Phase 2 + git reset affected `src/`.
- **Fast-Track/Delta**: MVP + `allow_fast_track` + `change_flow`; `hotfix`/`patch`/`feature`/`refactor` with `contract_impact` in {none, internal}, `scope` in {local, module}, `breaking=false` → Delta-Flow (1a→5a→6a→8a). `refactor` is always `contract_impact: none` and `breaking: false`. Never for `external/public` or `openapi.yaml` changes.
- **Migration** (`mode=migration`/`brownfield`): contract freeze does not apply to adapters/ACL; CUTOVER-GATE required.

## Secrets

Env vars in `.env` (gitignored). Secret scan in CI. No credentials in code, config, logs, Dockerfiles or pipelines. MVP: at least minimal scan.

## Output

Summary artifact per task in the assigned dir. Large artifacts start with a table.

## Tech Stack

See `.agent/knowledge/stack.md` and `docs/adr/ADR-001-tech-stack.md`.

## Rights

Technical enforcement in agent frontmatter (`.opencode/agents/`) and `.opencode/opencode.jsonc`. Folder Ownership above is the single source.