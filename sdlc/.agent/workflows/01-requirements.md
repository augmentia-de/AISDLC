---
description: Phase 1 — Requirements gathering for AI SDLC
---

# Requirements Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/01_orchestrator_requirements.md`.

## Agent & Skill

- **Agent:** `aisdlc-requirements`  ·  **Skill:** `aisdlc-grill-stress`, `aisdlc-prd-to-stories`, `aisdlc-traceability-check`


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
- `docs/requirements/features/*.feature` — BDD-Szenarien (Gherkin).
  **Jedes Szenario trägt die Kennung seines Akzeptanzkriteriums** (`# AC-003-1`) —
  ohne sie ist der zugehörige Test am Ende nicht belegbar.
- `docs/requirements/stories/*.json` — User Stories als Tasks
- `docs/requirements/traceability_matrix.json` — Anforderung → Story → Feature
  (**nur die statische Hälfte**; die Test- und Code-Referenzen kommen später aus dem
  Dateisystem, siehe `aisdlc-traceability-check`)

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Gate
Await human review of all artifacts before proceeding to Phase 2.