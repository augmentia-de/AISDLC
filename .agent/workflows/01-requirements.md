---
description: Phase 1 — Requirements gathering for AI SDLC
---

# Requirements Workflow

## Prerequisites
- Read `AGENTS.md` in project root first.
- Read the orchestrator prompt: `docs/prompts/01_orchestrator_requirements.md`.

## Steps

1. **Analyse der Anforderung** auf Unklarheiten, Edge Cases und Zielkonflikte.
2. **Zerlegung** in fachliche Epics, User Stories und BDD-Szenarien.
3. **Akzeptanzkriterien** für jede User Story definieren.
4. **Requirement-IDs** vergeben für Rückverfolgbarkeit.

## Output
All artifacts written to `.agent/artifacts/` and `docs/requirements/`.

- `requirements.md` — PRD & Feature Spec
- `user-stories.md` — Epics → Stories → Acceptance Criteria
- `risk-register.md` — Top 5 Risiken
- `docs/requirements/PRD.md` — Product Requirement Document
- `docs/requirements/features/*.feature` — BDD-Szenarien (Gherkin)
- `docs/requirements/stories/*.json` — User Stories als Tasks
- `docs/requirements/traceability_matrix.json` — Requirement → Test → Code

## Gate
Await human review of all artifacts before proceeding to Phase 2.