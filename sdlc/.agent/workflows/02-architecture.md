---
description: Phase 2 — Architecture design for AI SDLC
---

# Architecture Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture` · **Skill:** `aisdlc-openapi-first`, `aisdlc-glossary-adr`, `aisdlc-migration-plan`


##Steps

1. **Evaluate design options** and select appropriate design patterns/architectures.
2. **Specify API interfaces** (request/response schemas, types, status codes) in `docs/api/openapi.yaml`.
3. Design **System and Component Diagrams** (C4 model) in `docs/architecture/diagrams/*.mmd`.
4. **Document ADRs** in `docs/adr/ADR-<ID>-<title>.md` (Nygard schema).

## Output
- Contract to `docs/api/openapi.yaml` — the **only** location of the contract.
  `.agent/knowledge/openapi.yaml` is a reference to it, not a copy.
- Knowledge to `.agent/knowledge/`: `schema.sql` (canonical, byte-identical to
  Copy in `docs/architecture/schema.sql` — mirror rule, ADR-019 §6) and
  `stack.md` (the stack decision from ADR-001).
- Artifacts to `.agent/artifacts/` (architecture.md, folder-structure.md).
- Docs to `docs/architecture/`, `docs/api/`, `docs/adr/`.

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git commit -m "docs(sdlc-2): API-Contract, Domain-Modell und ADRs" \
  -m "Schritt 2 - Konzept & Architektur | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## HARD GATE
**Stop here. Await human approval before any code is written.**

Check: PRD complete, API contract stable, ADRs ok.