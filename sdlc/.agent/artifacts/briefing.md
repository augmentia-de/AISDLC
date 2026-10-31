# Profiles - <Step N> (<Step Name>)

> **Template.** Orchestrator rewrites this file at the end of each step
> and commits it with the step. The next step role agent reads
> them **first** and then only the files mentioned here.

| field | Value |
|---|---|
| Stand | Step <N> completed, commit <hash> |
| Next step | <N+1> (<step name>), agent <agent-id> |
| contract | `docs/api/openapi.yaml`, revision <x> — **frozen since step 4** |
| Scheme | `.agent/knowledge/schema.sql` (canonical) |
| stack | `.agent/knowledge/stack.md` |

## What you have to read - and nothing else

| # | File | Why |
|---|---|---|
| 1 | `AGENTS.md` | Rules, Gates, Folder Ownership |
| 2 | `.agent/PHASES.md` | Step → Instance → Agent → Skill → Gate |
| 3 | `<workflow file>` | this step |
| 4 | <additional file> | <why> |

## Decisions that apply

| ID | decision | Binds |
|---|---|---|
| <F-x / ADR-x> | <short version> | <what> |

## Open points

| ID | Topic | Owner |
|---|---|---|
| <O-xx> | <short> | <who> |

## What you **don't** do in this step

- <e.g. E.g. "the contract is frozen" - a contract defect is a finding for
  a jump back to step 2, no change in passing.>

## Tools

| Purpose | Command |
|---|---|
| Consistency of the traceability chain | `python3 .agent/tools/traceability_check.py` |
| <more> | <command> |