# How-To: When which agent and which skill should be active

## Overview

The project has **6 primary agents** and **7 skills**. The agents are *roles*, the skills
are *procedural steps*. Per step, **exactly one** agent is the main agent; Skills
are additionally loaded if the step needs them.

| level | Scope | Location |
|---|---|---|
| **Step** | 9 work steps (01-09) | `.agent/workflows/` |
| **Instance** | 5 Orchestrator processes (phase0-4) | `.agent/prompts/multi-instance/` |
| **Agent** | 6 rolls | `.opencode/agents/` |
| **Skill** | 7 procedural steps | `.opencode/skills/` |

**Single Source of Truth:** `.agent/PHASES.md` (Step → Instance → Agent → Skill → Gate).

## The 6 Agents

| Agent ID | role | Default? | Writes in |
|---|---|---|---|
| `aisdlc-requirements` | Requirements Orchestrator (Step 1 + 1a) | **yes** | `docs/requirements/` |
| `aisdlc-architecture` | Architecture Orchestrator (Step 2-4) | no | `docs/architecture/`, `docs/api/`, `docs/adr/` |
| `aisdlc-execution` | Execution Orchestrator (Step 5-7) | no | `src/`, `tests/`, `docs/execution/` |
| `aisdlc-qa` | QA & Compliance Orchestrator (Step 8-9) | no | `tests/`, `docs/qa/` |
| `stack/java/backend` | Java/Spring backend implementation | no | `src/`, `tests/`, `docs/execution/` |
| `stack/react/frontend` | React/TypeScript frontend implementation | no | `src/`, `tests/`, `docs/execution/` |
| *(Original)* `build` | Generalist | no | everywhere |

**Default agent:** `aisdlc-requirements` — a new session starts in step 1.

## The 7 skills

| Skill | When | Origin |
|---|---|---|
| `aisdlc-grill-stress` | Step 1a + 4 — Design Tree, Deepening, Standards/Spec as Two Axes | mattpocock (grilling, codebase design), wshobson |
| `aisdlc-simplify` | Step 3 + 7 — simplify behavior, dead code, debt | ECC (code simplifier, refactor cleaner), wshobson |
| `aisdlc-tdd` | Step 5 — RED/GREEN/REFACTOR, failure paths, workflow invariants | superpowers, ECC, nnennandukwe |
| `aisdlc-security-scan` | Step 8 — read-only audit, OWASP Top 10 | ECC, wshobson |
| `aisdlc-deploy` | Step 9 — Pipeline, Rollout, Validation, Smoke, SLO, Runbook | wshobson, ECC |
| `strangler-fig` | Design legacy module migration | own |
| `module-migration` | Implement legacy module (characterization tests) | own |
| `test-self-correction` | Fix failed tests | own |

**Why skills instead of your own agents:** Simplify, TDD, Security and Deploy are *procedures*,
no roles. As a skill, the step costs 0 tokens until it is needed, and the
The responsible agent remains the only writer.

---

## Step-by-step

### Step 1 — Requirements Gathering

**Agent:** `aisdlc-requirements` (Default) · **Instance:** `phase1` · **Skill:** `aisdlc-grill-stress` (1a)

Created PRD, User Stories, `.feature` files, Traceability Matrix, Risk Register.

**Go to Step 2** if PRD, Stories and Acceptance criteria are complete and checked.
**Escalation:** `REQUIREMENT_GAP` → back to step 1.

### Step 2 — Concept & Architectural Design

**Agent:** `aisdlc-architecture` · **Instance:** `phase2` · **Skill:** none

Created `docs/api/openapi.yaml`, C4 diagrams, ADRs, domain model.

**🚧 GATE 1** — then pause, show summary, **wait for human approval**.

### Step 3 — Simplify & Reduce

**Agent:** `aisdlc-architecture` · **Instance:** `phase2` · **Skill:** `aisdlc-simplify`

Reduce oversized parts of the architecture **before** running the stress test.

### Step 4 — Grill-Me Stress Test

**Agent:** `aisdlc-architecture` · **Instance:** `phase2` · **Skill:** `aisdlc-grill-stress`

Stress-testing architecture: querying the design tree, finding deepening opportunities,
Check standards and spec axles separately.

**Escalation:** Architectural error → back to step 2.

### Step 5 — Test-Driven Setup

**Agent:** `aisdlc-execution` · **Instance:** `phase3` · **Skill:** `aisdlc-tdd`

Test strategy, test levels, failure paths, workflow invariants.

### Step 6 — AI code generation

**Agent:** `aisdlc-execution` · **Instance:** `phase3` · **Skill:** none

Parallel build streams (backend/frontend/infra), synchronized via `openapi.yaml`.

**Escalation:** missing tests → back to step 5.

### Step 7 — Code Refactoring & Clean-up

**Agent:** `aisdlc-execution` · **Instance:** `phase3` · **Skill:** `aisdlc-simplify`

**Second Simplify pass** on the finished code: now you see defensive code,
that has been replaced by tests and abstractions that have not proven successful.

**Escalation:** dirty code → back to step 6.

### Step 8 — Security & Compliance Scan

**Agent:** `aisdlc-qa` · **Instance:** `phase4` · **Skill:** `aisdlc-security-scan`

Read-only Audit: AuthN/AuthZ, Secrets, Injection, XSS, Containers, Dependencies, OWASP Top 10.

**🚧 GATE 2** — then stop, show findings, **wait for human approval**.

### Step 9 — Deployment & Operations

**Agent:** `aisdlc-qa` · **Instance:** `phase4` · **Skill:** `aisdlc-deploy`

Pipeline, rollout strategy, pre-flight, smoke testing, promotion, rollback, SLOs, runbook.

**Escalation:** Production incident → back to step 2.

---

## Commit per step

**Each step produces exactly one commit.** A step is not complete until its
Commit exists. The commit is created **before** the gate — the gate checks the finished commit.
```
bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```
| Rule | Meaning |
|---|---|
| Subject | `<type>(sdlc-<n>): <Short description>` |
| Bodysuit | `Step <n> - <step name> \| Agent: <agent-id> \| Artifacts: <paths>` |
| Scope | only the step scope is staged — technically enforced |
| Order | Commit before the gate or before the handoff |
| Empty | nothing changed → no commit, no empty commits |
| Push | the pipeline never pushes — `git push` remains a human decision |

The scope is technically enforced: the index is emptied, then only the step scope
staged. `git reset -q` unstages someone else's work, but doesn't delete it.

Two reasons why not `git commit -- <paths>`: the pathspec aborts as soon as a
Scope path has no tracked files - and the route is via `--pathspec-from-file`
broken under the global `rtk` plugin (`rtk` rewrites `git diff` and hangs a
"Changes:" block, which in turn terminates with "empty string is not a valid pathspec").

Subject and paths per step: `.agent/PHASES.md` → "Commit per step".

---

## Agent change rules

### 1. Follow the sequence of steps

The steps build on each other. Do not start step 6 until step 2 is enabled.

### 2. Do not mix agents

**Exactly one** agent is the main agent per step. Two agents with different
Write permissions active at the same time lead to conflicting permissions.

### 3. Switch without rebooting
```
bash
Tab                               # nächster Agent (vorwärts)
Shift+Tab                         # vorheriger Agent (rückwärts)
Ctrl+X, dann a                   # Agentenliste (oder /agents)
opencode run --agent <agent-id> "Auftrag"
opencode reload                  # Config ohne Neustart übernehmen
`
```
default_agent` in `.opencode/opencode.jsonc` works **only for new sessions** — one
existing session retains its saved agent.

### 3a. When to change? — after approval, before the next step
```
Schritt N läuft  →  Agent committet  →  GATE: Artefakte vorstellen
                                                    │
                                     Freigabe?  ──────┴────── nein
                                        ja                      │
                                        ▼                       ▼
                              Agent auf Schritt N+1        Korrekturen im
                              wechseln, dann Schritt N+1    selben Agenten
                              starten                       (bleibt aktiv)
```
**Change after release, not before.** Reason:

- The gate checkpoint belongs to the step just completed. The agent who
  Step has delivered, remains active until you have decided - questions and
  Corrections end up with the same agent with the same writing permission.
- Switching before release takes away the right of the requirements agent
  `docs/requirements/`. A correction to the PRD would then be blocked.
- Only the agent of the next step may be active when this step begins —
  `aisdlc-architecture` is only allowed to write `docs/architecture/`, `docs/api/`, `docs/adr/`.

**Context:** There is no automatic context transfer between steps — that
is wanted. The new agent reads the artifacts from the file system (PRD, `openapi.yaml`,
`.agent/artifacts/`). That's why the commit **before** the gate is important: the new agent
works on a versioned, clean basis.

### 4. Reload skills, don't improvise

If a step has a skill, load that skill instead of improvising the procedure:
```
text
Nutze das Skill aisdlc-security-scan für den Security-Audit.
```
### 5. Delegate subagents when necessary
```
text
Nutze den stack/java/backend-Subagenten, um die Persistenzschicht zu prüfen.
```
Subagents run in their own child session with a fresh context - the result comes as
Summary back.

---

## Stack layer

The framework is **stack agnostic**. It doesn't know any library paths, none
stack-specific skills and no stack agent. Everything that depends on the project
is located in **`opencode.jsonc` in the project root**.

| level | File | Content | Will be replaced at |
|---|---|---|---|
| Framework | `.opencode/opencode.jsonc` | `default_agent`, `experimental`, framework skills, subagents, shell | never |
| stack | `opencode.jsonc` (Root) | `skills.paths`, `skill` allowlist, stack subagents | every stack change |
| Stack knowledge | `.agent/knowledge/` | `stack.md`, `schema.sql`, `openapi.yaml` | Phase 2 of the project |
| Stack Agents | `.opencode/agents/stack/<tech>/` | the stack-specific roles | every stack change |

**Why two config files:** OpenCode merges config files additively — `skills` paths
are **combined**, `permissions` rules **appended**, none are replaced. The
The order is fixed: root config first, `.opencode/` config after, agent front matter
last; last; last hit wins.

This leads to exactly one rule:

> **The catch-all deny belongs in the stack layer, the positive rules in the
> Framework layer.**

Otherwise the framework rule (`skill: *` → deny, further back) overrides it
Stack permissions (`springboot-*` → allow, above) again - that's exactly what happened
in the reference run the error caused by the agent front matter.

**Example stack layer for Spring Boot + React:**
```
jsonc
{
  "skills": { "paths": [ "<pfad>/ECC/skills", "<pfad>/wshobson-agents/plugins/backend-development/skills" ] },
  "permissions": [
    { "action": "skill", "resource": "*", "effect": "deny" },
    { "action": "skill", "resource": "aisdlc-*", "effect": "allow" },
    { "action": "skill", "resource": "springboot-*", "effect": "allow" },
    { "action": "skill", "resource": "java-*", "effect": "allow" },
    { "action": "skill", "resource": "react-*", "effect": "allow" },
    { "action": "skill", "resource": "typescript-*", "effect": "allow" },
    { "action": "subagent", "resource": "*", "effect": "deny" },
    { "action": "subagent", "resource": "stack/java/backend", "effect": "allow" },
    { "action": "subagent", "resource": "stack/react/frontend", "effect": "allow" }
  ]
}
```
**Paths are machine dependent.** Absolute paths break when moving. Two ways:
`skills.paths` relative to the working directory (`./skills` if the libraries
are vendored there) or a vendor step that brings the library skills into the
Project copied.

## Stack level

Stack knowledge **doesn't** come from your own files, but directly from them via `skills.paths`
Libraries. No copying, no drift:
```
jsonc
"skills": { "paths": ["/…/ECC/skills", "/…/wshobson-agents/plugins/backend-development/skills", …] }
```
| stack | Available via | ≈ Token Descriptions |
|---|---|---|
| Java + Spring | `stack/java/backend` + `springboot-*`, `java-*` | 342 |
| React/TypeScript | `stack/react/frontend` + `react-*`, `typescript-*` | 1,029 |
| Python, Go, Rust, .NET | Create stack agent + `python-*` etc. | ≈ 2,281 total |

**Create your own stack agent:**
```
bash
mkdir -p .opencode/agents/stack/python
# .opencode/agents/stack/python/backend.md anlegen  →  ID: stack/python/backend
```
Then in `opencode.jsonc` allow: `{ "action": "subagent", "resource": "stack/*", "effect": "allow" }`
(the `stack/*` entry covers all stacks).

**Change stack:** Convert `default_agent` to `.opencode/opencode.jsonc`, e.g. B. from
`aisdlc-requirements` on `stack/java/backend`, and run `opencode reload`.

---

## Permissions (who can do what)

| agent | Read | Write | Shell |
|---|---|---|---|
| `aisdlc-requirements` | everything | `docs/requirements/` | ask |
| `aisdlc-architecture` | everything | `docs/architecture/`, `docs/api/`, `docs/adr/` | ask |
| `aisdlc-execution` | everything | `src/`, `tests/`, `docs/execution/` | ask |
| `aisdlc-qa` | everything | `tests/`, `docs/qa/` | ask |
| `stack/java/backend` | everything | `src/`, `tests/`, `docs/execution/` | ask |
| `stack/react/frontend` | everything | `src/`, `tests/`, `docs/execution/` | ask |
| `build` | everything | everything | ask (except git status/diff/log) |

**Important:** An agent is only allowed to write to its assigned directories. Try
outside are blocked. `build` intentionally remains omnipotent.

---

## Escalation and rebound

| Situation | Signal | Return to |
|---|---|---|
| Unclear or incomplete requirement | `REQUIREMENT_GAP` | Step 1 |
| Unfeasible design | `ARCHITECTURAL_BLOCKER` | Step 2 |
| Missing tests during implementation | (implicit) | Step 5 |
| Dirty code | (implicit) | Step 7 |
| Security vulnerability | (implicit) | Step 6 |
| Production Incident | (implicit) | Step 2 |

---

## Typical errors

| Error | Cause | Solution |
|---|---|---|
| Agent modifies files outside its scope | Wrong agent active | `Shift+Tab` to switch to the correct agent |
| Agent does not write to the expected directory | Permissions too restrictive | Check `opencode debug agents`, check permissions in `opencode.jsonc` |
| Stack skill not found | `skills.paths` filtered too narrowly | Expand `skill`-Allowlist in `opencode.jsonc` with the prefix |
| Agent does not know the API spec | `docs/api/openapi.yaml` is missing | Complete step 2 first |
| Tests fail, agent gives up | No self-correction loop | `aisdlc-qa` uses `{{TEST_RESULTS_JSON}}` — when run manually: return error log to OpenCode |
| Agent forgets context from previous step | No automatic context transfer | Artifacts (PRD, openapi.yaml) must be present — the agent reads them at startup |
| Wrong workflow file selected | Two Prompt Families | Use `.agent/PHASES.md` as a single source of truth |

---

## Select model

Currently, **every** agent runs on the session's model — it is not a `model:` field
set, so each subagent inherits the session model. That was in the first full run
`minimax-m3.1-flash-free`, a free flash model. The election is on **three**
Positions configurable; the precedence is: **Call > Agent > Session**.

| level | Where | Effect |
|---|---|---|
| **Session** | `opencode run --model provider/model` | Model of the session. All agents **without** their own `model:` inherit it — this includes the role agents and the orchestrator. |
| **Agent** | `model: provider/model` in Frontmatter | Fixed model binding for this exact agent. Overwrites the session. |
| **Subagent call** | `model:` parameter when starting subagent | Only applies to this one call. Overwrites agent and session. |

Examples:
```
yaml
# .opencode/agents/aisdlc-architecture.md  — Designarbeit braucht das starke Modell
---
description: ...
mode: all
model: provider/model
permissions: ...
---
```

```
bash
# Ein Lauf mit einem anderen Modell für die ganze Pipeline
opencode run --model provider/model --agent aisdlc-orchestrator "Starte die Pipeline …"
```
**Why there is a separate level per agent:** the steps cost a lot
different. Step 2 (Contract, Scheme, 20 ADRs) and Step 6 (Implementation)
are expensive and prone to errors; Step 3 (Simplify) and Step 7 (Refactoring)
are more mechanical. With one `model:` per agent you only pay where it counts.
Without this tier, **every** step inherits the session model — even the expensive ones.

**Important:** `model:` in frontmatter overrides the session selection. Who at the start
If you choose a model, you will only receive it from agents who do not have their own `model:`.
An agent with `model:` in front matter **always** uses this model — even in a
Session in which another was elected.

The variant suffix (`#high`, `#reasoning`) is attached to the model ID:
`model: provider/model#variant`.

## Quick Reference
```
bash
# Phase starten (Default ist aisdlc-requirements)
opencode
opencode run --agent aisdlc-architecture "Entwirf die API für [Feature]"
opencode run --agent stack/java/backend "Implementiere [Task] gemäß openapi.yaml"
opencode run --agent stack/react/frontend "Baue [Komponente]"

# Agentenliste
/agents
Shift+Tab

# Skill manuell laden
Nutze das Skill aisdlc-deploy für das Deployment.

# Config neu laden
opencode reload

# Nachsehen, was geladen ist
opencode debug agents
```
| File | Content |
|---|---|
| `AGENTS.md` | Rules, Gates, Folder Ownership, Phase Skills |
| `.agent/PHASES.md` | Step → Instance → Agent → Skill → Gate |
| `.agent/workflows/` | 9 workflow files, per step |
| `.agent/prompts/orchestrator/` | Single Orchestrator Mode Prompts |
| `.agent/prompts/multi-instance/` | Prompts for server operation (0-4 instances) |
| `.opencode/agents/` | 6 primary agents |
| `.opencode/skills/` | 7 Skills |
| `how-to.md` | this document |