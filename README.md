# AI SDLC – AI agent-assisted software development cycle

An SDLC template in which an **Orchestrator Agent** controls the process and **OpenCode** executes the development. Depending on the SDLC phase, the orchestrator changes the system prompt (`.agent/prompts/`) and regulates who is allowed to write or only read in which directory.

## Process (iteration loops)

The orchestrator goes through four feedback loops per feature and escalates each other:
```
[1. Requirements] ──> [2. Architecture] ──> [3. Implementation] ──> [4. Testing/QA] ──> PR & Merge
       ^                    ^                       |                       |
       └── REQUIREMENT_GAP ─┘                       └── Blocker/Fehler ────┘
                              [ARCHITECTURAL_BLOCKER ──> Redesign, Git-Reset, Re-Entry]
```
1. **Planning/Architecture Iteration** – Check feasibility, analyze affected files (`impact_analysis.json`).
2. **Build & Lint Iteration** – Code compiled & style requirements met; Errors are returned to OpenCode as a log (`lint_build_results.json`).
3. **Test Self-Correction Loop** – Test Runner is running; Failures are parsed and passed to OpenCode for correction (`test_results.json`).
4. **Quality Gate Loop** – Overall acceptance (syntax, tests, security, coverage) before the merge (`gate_evaluation.json`).

**Escalation:** If OpenCode detects an incomplete requirement (`REQUIREMENT_GAP`) or an infeasible design (`ARCHITECTURAL_BLOCKER`) during implementation, it stops immediately and reports to the orchestrator with structured JSON. This supplements the requirements (seamless further construction) or revises the architecture/API, resets affected files via Git and restarts the implementation.

## Flows (execution modes)

The orchestrator chooses the path based on `.agent/config.json` (`mode`, `change_flow`, `profile`) and the `change` block in the brief. Three modes are provided:

### A) Greenfield flow (full run, steps 1–9)

For new projects or larger features. Complete phases, both gates.

| # | step | Workflow | agent | Gate |
|---|---|---|---|---|
| 1 | Requirements gathering | `01-requirements.md` | `aisdlc-requirements` | — |
| 1a | Request Grill Me | (in 01) | `aisdlc-requirements` | — |
| 2 | Concept & Architecture | `02-architecture.md` | `aisdlc-architecture` | **GATE 1** |
| 3 | Simplify & Reduction | `03-simplify.md` | `aisdlc-architecture` | — |
| 4 | Grill-Me stress test | `04-grill-me-stress.md` | `aisdlc-architecture` | Contract freeze |
| 5 | Test Driven Setup | `05-tdd.md` | `aisdlc-execution` | — |
| 6 | AI code generation | `06-implementation.md` | `aisdlc-execution` | — |
| 7 | Refactoring & Cleanup | `07-refactoring.md` | `aisdlc-execution` | — |
| 8 | Security & Compliance | `08-security.md` | `aisdlc-qa` | **GATE 2** (Traceability 0 gaps) |
| 9 | Deployment & Operations | `09-deploy.md` | `aisdlc-qa` | — |

### B) Migration flow (`mode: migration` / `brownfield`)

For existing systems. Strangler fig/module migration, parallel operation, cutover gate.

| # | step | Workflow | agent | Gate |
|---|---|---|---|---|
| 0a | Migration assessment | `00-migration-assessment.md` | `aisdlc-requirements` | Scope release |
| 1 | Requirements gathering | `01-requirements.md` | `aisdlc-requirements` | — |
| 2 | Concept & Architecture | `02-architecture.md` | `aisdlc-architecture` | GATE 1 |
| 2b | Migration plan | `02b-migration-plan.md` | `aisdlc-architecture` | **GATE 1 (Migration)** |
| 3-4 | Simplify / stress test | `03/04-*.md` | `aisdlc-architecture` | Contract freeze (new external/public only) |
| 5-7 | TDD/Impl/Refactor | `05/06/07-*.md` | `aisdlc-execution` | — |
| 8 | Security & Compliance | `08-security.md` | `aisdlc-qa` | GATE 2 |
| 9 | Deployment/Cutover| `09-deploy.md` | `aisdlc-qa` | **CUTOVER GATE** |

Special features: Adapters/ACL between old and new are excluded from the contract freeze. Migration scripts must be idempotent; Backout plan mandatory.

### C) Delta Flow (Hotfix/Patch/Small Feature)

For small changes without full PRD/architecture. Requirement: `change.type in {hotfix, patch}`, `contract_impact in {none, internal}`, `breaking == false`.

| # | step | Workflow | agent |
|---|---|---|---|
| 1a | Change letter | `01a-change-brief.md` | `aisdlc-requirements` |
| 5a | Delta TDD | `05a-delta-tdd.md` | `aisdlc-execution` |
| 6a | Delta implementation | `06a-delta-impl.md` | `aisdlc-execution` |
| 8a | Delta QA | `08a-delta-qa.md` | `aisdlc-qa` |

GATE 1 is skipped; GATE 2 as **light** (affected ACs only). For `contract_impact in {external, public}` or change to `openapi.yaml` → **never** delta flow.

### Routing rules (short version)

- `mode == migration/brownfield` → assessment (0a) + migration plan (2b) + cutover gate.
- `change.type in {hotfix, patch}` + internal + non-breaking → delta flow.
- Otherwise → Greenfield full run.
- Fast-Track (MVP) only with `allow_fast_track: true` and the above delta condition.

## Directory structure
```
AISDLC/
├── README.md                                # Dieses Dokument: Struktur, Ablauf, Rollen
├── dis.txt                                  # Quelle/Verlauf des Konzepts (Anforderung & Analyse)
├── docs/
│   ├── sdlc_ergebnisse.md                   # Ergebnistabelle: jede Phase -> Output, Format, Verwendung
│   ├── requirements/                 (✍️ Requirements Orchestrator)
│   │   ├── PRD.md                           # Product Requirement Document: Single Source of Truth
│   │   ├── traceability_matrix.json         # Mapping Requirement-ID -> Test -> Code (Compliance)
│   │   ├── features/*.feature               # BDD-Szenarien in Gherkin (Given/When/Then)
│   │   └── stories/*.json                   # User Stories als tickerartige Aufgaben für OpenCode
│   ├── architecture/                 (✍️ Architecture Orchestrator)
│   │   ├── domain_model.md                  # Systemkontext & Domain-Modell
│   │   └── diagrams/*.mmd                   # C4-Diagramme als Code (Mermaid.js)
│   ├── api/                           (✍️ Architecture Orchestrator)
│   │   └── openapi.yaml                     # API-Vertrag 3.0+, Grundlage für Stubs & Tests
│   ├── adr/                           (✍️ Architecture Orchestrator)
│   │   └── ADR-*.md                         # Architecture Decision Records (Nygard-Schema)
│   ├── execution/                     (✍️ Execution Orchestrator)
│   │   ├── pull_requests/PR_*.md            # Generierte PR-Beschreibungen pro Task
│   │   └── CHANGELOG.md                     # Versionshistorie unter [Unreleased]
│   ├── qa/                           (✍️ QA Orchestrator)
│   │   └── bugs/BUG-*.json                  # Strukturierte Bug-Reports (Pfad, Zeile, Ursache)
├── opencode.jsonc                   # STACK-SCHICHT (projektspezifisch, wird ersetzt)
│                                        skills.paths + skill-Allowlist des Stacks
├── .agent/                          # Agent Control Layer (ASDF-Stil)
│   ├── PHASES.md                    # Single Source of Truth: Schritt → Agent → Skill → Gate
│   ├── prompts/                     # System-Prompts für Orchestrator & OpenCode
│   │   ├── orchestrator/            # Ein-Orchestrator-Modus (1 Agent, wechselt Phasen)
│   │   │   ├── 00_orchestrator_analysis.md
│   │   │   ├── 01_orchestrator_requirements.md
│   │   │   ├── 02_orchestrator_architecture.md
│   │   │   ├── 03_orchestrator_implementation.md
│   │   │   └── 04_orchestrator_testing.md
│   │   └── multi-instance/         # Multi-Instanz-Modus (jede Phase als eigene Instanz)
│   │       ├── sdlc_phase0_legacy.md
│   │       ├── sdlc_phase1_requirements.md
│   │       ├── sdlc_phase2_architecture.md
│   │       ├── sdlc_phase3_implementation.md
│   │       └── sdlc_phase4_testing.md
│   ├── workflows/                   # Workflow-Definitionen pro Schritt (9 Dateien)
│   ├── tools/                       # Stackunabhängige Werkzeuge (traceability_check.py)
│   ├── knowledge/                   # Stackwissen des PROJEKTS: stack.md, schema.sql, openapi.yaml
│   └── artifacts/                   # Ergebnisse jeder Schritt + briefing.md (Steckbrief)
├── .opencode/                       # FRAMEWORK-SCHICHT (stackagnostisch)
│   ├── opencode.jsonc               # Framework-Regeln: Skills, Subagenten, Shell
│   ├── agents/                      # 5 Rollen: 4 Orchestrator + 1 Pipeline-Orchestrator
│   │   └── stack/                   # leer im Template — die Stack-Agenten kommen vom Projekt
│   └── skills/                      # aisdlc-* (Verfahrens-Schritte) + 3 eigene
├── src/                             (✍️ OpenCode Build-Modus)
│   └── ...                                  # Produktiver Quellcode (nur vom Orchestrator gelesen)
├── tests/                           (✍️ OpenCode QA-Modus)
│   ├── unit/                                # Unit-Tests
│   ├── integration/                         # Integrationstests
│   └── e2e/                                 # End-to-End-Tests
├── coverage/                        (✍️ CI / Test-Runner)
│   └── coverage-summary.json                # Coverage-Report als Qualitätsschranke (Quality Gate)
├── example/                         # Referenz-Anwendung als Formatvorbild
├── lessons-learned.md               # Beobachtungen aus dem Referenzlauf
└── nohitl.md                        # Betriebsmodus ohne Human-in-the-Loop
```
**Two layers.** The framework (`.opencode/`, `.agent/`, `AGENTS.md`) is
stack-agnostic — it knows no library paths and no stack. The
**Stack layer** is located in `opencode.jsonc` in the project root and is used by everyone
Stack change replaced; the framework remains untouched. Merge rule: root config
first, `.opencode/`-Config after, Agent-Frontmatter last — last hit
wins. Consequence: **Catch-all denies belong in the stack layer**, which
positive rules in the framework layer.

## Rights matrix (own writer, others only read)

| Directory | Writing Agent (Exclusive) | Reading Agents (Read-Only) |
| :--- | :--- | :--- |
| `docs/requirements/` | Requirements Orchestrator | Architecture, Execution, QA, OpenCode |
| `docs/architecture/`, `docs/api/`, `docs/adr/` | Architecture Orchestrator | Execution, QA, OpenCode |
| `docs/execution/` | Execution Orchestrator (Tech Lead) | QA, OpenCode, human. Reviewer |
| `docs/qa/` | QA Orchestrator | Execution, OpenCode (Self-Correction) |
| `src/` | OpenCode (build phase) | Requirements, Architecture, QA |
| `tests/` | OpenCode (test phase) | QA, Execution |
| `coverage/` | CI/Test Runner | QA, Execution |

## Usage

1. Orchestrator starts with `.agent/prompts/orchestrator/01_orchestrator_requirements.md`, reads the requirement and creates PRD, stories, `.feature` files and the traceability matrix.
2. Phase 2 (`02_orchestrator_architecture.md`, plan mode): Architecture diagrams, `openapi.yaml`, ADRs are created.
3. Phase 3 (`03_orchestrator_implementation.md`, build mode): OpenCode gets atomic tasks, the orchestrator validates linter/build and prepares commits & PRs.
4. Phase 4 (`04_orchestrator_testing.md`): Write and execute test suites; in case of errors, the orchestrator passes `{{TEST_RESULTS_JSON}}` to `opencode_task_self_correction.md` (self-correction loop).
5. Escalation for `REQUIREMENT_GAP`/`ARCHITECTURAL_BLOCKER` via structured JSON - then re-entry into the respective phase; with Blocker Git reset of the affected `src/` files.

### Multi-instance operation (0-4 parallel phase services)

In addition to the one-orchestrator mode, each phase can run as its own `ai-agent-service` instance (see discord-channel `start-sdlc.sh`, Observer as supervisor):
```
observer-service (18100, Supervisor) ── start/cancel/active ──► phase1..phase4
phase0 (18082) → phase1 (18083) → phase2 (18084) → phase3 (18085) → phase4 (18086)
     ↑ REST-Handoff pro Phase (send_message → nächste Instanz /api/messages)
```
- Each instance carries its own system prompt (`.agent/prompts/sdlc_phaseN_*.md`) via `agent.prompt.path`.
- A change remains without `docs/00_legacy_analysis/` (Phase 0 optional, entry into Phase 1 possible directly).
- The common SQLite `trace.db` makes the phase change visible in the observer as an "active phase".
---

## Upgrade: ASDF style (Immune System, Hard Gates, Knowledge Base)

This project has been expanded to include concepts from the [Agentic Software Delivery Framework](https://github.com/kasampra/Agentic-Software-Delivery-Framework):

### New files and directories

| File/Directory | Description |
|---|---|
| `AGENTS.md` | Immune System — Behavior Rules, Hard Gates, Folder Ownership, Secrets. Every agent reads this file first. |
| `.agent/knowledge/` | Central knowledge base: `openapi.yaml`, `schema.sql`, `stack.md` |
| `.agent/artifacts/` | Central artifact storage for all phase results |
| `.agent/workflows/` | 15 workflow files (00-09 + 0a/1a/2b/5a/6a/8a) with gates and output definitions |
| `.agent/PHASES.md` | **Single Source of Truth**: Step → Instance → Agent → Skill → Gate |

### Hard gates

- **GATE 1** (after Step 2 — Architecture): Human approval before code generation
- **GATE 2** (after step 8 — Security): Human approval before deployment

### Parallel Build Phases (Phase 6)

Backend, frontend and infrastructure are built simultaneously (3 parallel streams), synchronized via the `openapi.yaml` contract from phase 2.

### Reference app: TaskFlow

The project serves as a template for a task app with the following components:
- Backend: FastAPI or Spring Boot (JWT-Auth, CRUD, status workflow)
- Frontend: Next.js or React (task list, filter, sorting)
- Infra: Docker Compose, GitHub Actions CI/CD
- Tests: Unit, Integration, E2E, Coverage ≥ 80%
- Security: OWASP Top 10 Audit

### Use of the new structure

1. Read `AGENTS.md` — contains all rules and gates.
2. `.agent/knowledge/` — Tech stack decisions and API spec.
3. `.agent/workflows/` — Workflow instructions per phase.
4. `.agent/artifacts/` — Results of each phase end up here.

### OpenCode Agents

Six primary agents are defined in `.opencode/agents/`:

**Phase agents (stack agnostic)**

| agent | Steps | Writes in |
|---|---|---|
| `aisdlc-requirements` | 1 + 1a | `docs/requirements/` |
| `aisdlc-architecture` | 2–4, 2b | `docs/architecture/`, `docs/api/`, `docs/adr/`, `docs/migration/` |
| `aisdlc-execution` | 5–7, 5a, 6a | `src/`, `tests/`, `docs/execution/` |
| `aisdlc-qa` | 8–9, 8a | `tests/`, `docs/qa/` |

**Stack agents (interchangeable)**

| agent | stack | Writes in |
|---|---|---|
| `stack/<tech>/<role>` | Stack specific (Java / Spring Boot, React / TypeScript, …) | `src/`, `tests/`, `docs/execution/` |

> The stack agents are **not** part of the framework. The template delivers
> `.opencode/agents/stack/` empty; the project populates it and enters the agents
> free of its stack layer.

**Skills (procedural steps)**| Skill | Steps |
|---|---|
| `aisdlc-grill-stress` | 1a + 4 |
| `aisdlc-simplify` | 3 + 7 |
| `aisdlc-tdd` | 5 |
| `aisdlc-security-scan` | 8 |
| `aisdlc-deploy` | 9 |
| `strangler-fig`, `module-migration` | 0a, 2b (migration) |
| `test-self-correction` | as required |

Each agent has folder-scoped write permissions and its own system prompt.
Details: `how-to/how-to.md`, mapping: `.agent/PHASES.md`.

### Stack knowledge from the libraries (without copies)

Stack skills come directly from the cloned libraries via `skills.paths` - no copying,
no drift. The `skill` permission in `opencode.jsonc` filters for stack prefixes, so not
all 496 library skills are loaded into the context (Java+Spring: 342 tokens, React/TS: 1,029).