# Phases, Instances and Agents — Mapping

Single source of truth for step → instance → agent → skill → gate. Three levels: **Step** (action, `.agent/workflows/`), **Instance** (long-running orchestrator, `.agent/prompts/multi-instance/`), **Agent** (OpenCode role, `.opencode/agents/`).

## Step → Instance → Agent → Skill

| Step | Workflow | Instance | Agent | Skill | Gate | MVP | Enterprise |
|---|---|---|---|---|---|---|---|
| 0 Legacy analysis | — | phase0 | — | — | optional | optional | optional |
| 0a Migration assessment | 00-migration-assessment.md | phase0 | aisdlc-requirements | module-migration, strangler-fig | optional | optional | yes (migration) |
| 1 Requirements | 01-requirements.md | phase1 | aisdlc-requirements | grill-stress, prd-to-stories, traceability-check | — | yes | yes |
| 1a Change brief (delta) | 01a-change-brief.md | phase1 | aisdlc-requirements | grill-stress (opt.) | — | yes (delta) | yes |
| 2 Architecture | 02-architecture.md | phase2 | aisdlc-architecture | openapi-first, glossary-adr, migration-plan | GATE 1 | minimal | yes |
| 2b Migration plan | 02b-migration-plan.md | phase2 | aisdlc-architecture | migration-plan, strangler-fig, glossary-adr | GATE 1 (migration) | optional | yes (migration) |
| 3 Simplify | 03-simplify.md | phase2 | aisdlc-architecture | simplify | — | no | yes |
| 4 Grill-Me stress | 04-grill-me-stress.md | phase2 | aisdlc-architecture | grill-stress | contract freeze | optional | yes |
| 5 TDD setup | 05-tdd.md | phase3 | aisdlc-execution | tdd, test-matrix, traceability-check | — | optional | yes |
| 5a Delta TDD | 05a-delta-tdd.md | phase3 | aisdlc-execution | tdd | — | yes (delta) | yes |
| 6 Implementation | 06-implementation.md | phase3 | aisdlc-execution | feature-scaffold, containerize | — | yes | yes |
| 6a Delta impl | 06a-delta-impl.md | phase3 | aisdlc-execution | feature-scaffold (opt.), simplify | — | yes (delta) | yes |
| 7 Refactoring | 07-refactoring.md | phase3 | aisdlc-execution | simplify | — | optional | yes |
| 8 Security | 08-security.md | phase4 | aisdlc-qa | security-scan | GATE 2 | minimal | yes |
| 8a Delta QA | 08a-delta-qa.md | phase4 | aisdlc-qa | security-scan | GATE 2 (light) | yes (delta) | yes |
| 9 Deploy | 09-deploy.md | phase4 | aisdlc-qa | ci-pipeline, containerize, deploy | — | yes | yes |

Skills are procedures, not roles. `aisdlc-*` = process; stack skills = craft. Loaded on demand.

## Gates

- **GATE 1** (after 2): direction review (architecture, decisions, ADRs). Contract is still a draft.
- **GATE 2** (after 8): QA 0 Critical, Security 0 Critical/High, Traceability 0 gaps. No deploy before approval.
- **CUTOVER-GATE** (migration, before final switch): routing, idempotent migrations, tested backout, monitoring.
- Contract freeze after Step 4 (stress test), not Gate 1. Changes after freeze: jump back to Step 2 for `external/public`; `internal` uses justified change + impact analysis.

## Commit per Step

One commit per step, before the gate. Empty step → no commit.

| Step | Agent | Type | Subject | Paths |
|---|---|---|---|---|
| 0 | phase0 | docs | docs(sdlc-0): Legacy-Analyse | docs/00_legacy_analysis/, .agent/artifacts/ |
| 0a | aisdlc-requirements | docs | docs(sdlc-0): Migration-Assessment und Legacy-Inventar | docs/migration/, .agent/artifacts/ |
| 1 | aisdlc-requirements | docs | docs(sdlc-1): PRD, User Stories und Traceability-Matrix | docs/requirements/, .agent/artifacts/ |
| 1a | aisdlc-requirements | docs | docs(sdlc-fix): Change-Brief und betroffene Pfade | docs/execution/, .agent/artifacts/ |
| 2 | aisdlc-architecture | docs | docs(sdlc-2): API-Contract, Domain-Modell und ADRs | docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/ |
| 2b | aisdlc-architecture | docs | docs(sdlc-2b): Migration-Plan und Cutover-Strategie | docs/migration/, docs/adr/, .agent/artifacts/ |
| 3 | aisdlc-architecture | refactor | refactor(sdlc-3): Architektur vereinfacht | docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/ |
| 4 | aisdlc-architecture | docs | docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen | docs/architecture/, .agent/artifacts/ |
| 5 | aisdlc-execution | test | test(sdlc-5): Teststrategie und fehlschlagende Tests | tests/, .agent/artifacts/ |
| 5a | aisdlc-execution | test | test(sdlc-fix): Delta-Tests und Regressionsfälle | tests/, .agent/artifacts/ |
| 6 | aisdlc-execution | feat | feat(sdlc-6): Implementierung der Stories | src/, tests/, docs/execution/ |
| 6a | aisdlc-execution | feat | feat(sdlc-fix): Delta-Implementierung | src/, tests/, docs/execution/ |
| 7 | aisdlc-execution | refactor | refactor(sdlc-7): Refactoring und Clean-up | src/, tests/, .agent/artifacts/ |
| 8 | aisdlc-qa | docs | docs(sdlc-8): Security-Audit und QA-Bericht | tests/, docs/qa/, .agent/artifacts/ |
| 8a | aisdlc-qa | docs | docs(sdlc-fix): Delta-QA und Security-Bericht | tests/, docs/qa/, .agent/artifacts/ |
| 9 | aisdlc-qa | build | build(sdlc-9): Deployment-Pipeline und Deploy-Report | docs/execution/, infra/, .github/, .agent/artifacts/ |

Command: `mkdir -p <paths> && git reset -q && git add -A -- <paths> && git commit -m "<subject>" -m "<body>"`. Body: `Schritt <n> - <name> | Agent: <id> | Artefakte: <paths>`. Jump-back corrects an earlier step's artifacts (max one). No push.

## Traceability

Static half (Requirement → Story → Feature) in Step 1: `docs/requirements/traceability_matrix.json`. Dynamic half (Requirement → Test → Code) from filesystem via `traceability_check.py`. AC IDs: `AC-<story>-<nr>`.

## Briefing

`.agent/artifacts/briefing.json` (template `.agent/templates/briefing.json`): entry point for next step, never the truth.

## Run Log

`.agent/artifacts/run-log.jsonl`: one JSON line per event (`step_start`, `step_end`, `gate`, `check`, `jump_back`, `retry`, `skill_start`, `skill_end`).

## Modes

- `greenfield`: steps 1–9.
- `migration`/`brownfield`: 0a + 2b + CUTOVER-GATE.
- Delta (hotfix/patch/feature/refactor, internal, non-breaking, module scope): 1a → 5a → 6a → 8a.

## Agents

| Agent | Steps | Writes |
|---|---|---|
| aisdlc-requirements | 1, 1a | docs/requirements/ |
| aisdlc-architecture | 2–4, 2b | docs/architecture/, docs/api/, docs/adr/, docs/migration/ |
| aisdlc-execution | 5–7, 5a, 6a | src/, tests/, docs/execution/ |
| aisdlc-qa | 8–9, 8a | tests/, docs/qa/ |
| stack/* | impl | src/, tests/, docs/execution/ |

Default agent: `aisdlc-requirements` (`.opencode/opencode.jsonc`). Stack agents live in `.opencode/agents/stack/<tech>/`.