---
description: Phase 1 — Requirements gathering for AI SDLC
---

# Requirements Workflow

## Prerequisites

- Read `AGENTS.md` in project root first.
- Read the orchestrator prompt: `.agent/prompts/01_orchestrator_requirements.md`.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-requirements` |
| Instanz (Multi-Instanz-Modus) | `phase1` |
| Skill | `aisdlc-grill-stress` |

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

## Commit

```bash
git add -A .
git diff --cached --name-only -- docs/requirements/ .agent/artifacts/ \
  | git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
             -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/" \
             --pathspec-from-file=-
git restore --staged .
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das Scope ist technisch erzwungen: committet werden nur Dateien, die im Pfad-Scope
  geaendert oder neu sind — fremde Aenderungen bleiben unangetastet.
- Der Commit-Scope laeuft ueber `--pathspec-from-file`, damit ein leerer oder noch nicht
  existierender Pfad den Commit nicht verhindert (git bricht sonst mit "pathspec did not
  match any files" ab und es wird gar nichts committet).
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Await human review of all artifacts before proceeding to Phase 2.