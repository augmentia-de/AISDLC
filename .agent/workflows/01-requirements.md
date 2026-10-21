---
description: Phase 1 — Requirements gathering for AI SDLC
---

# Requirements Workflow

## Prerequisites

- Read `AGENTS.md` in project root first.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- Read the orchestrator prompt: `.agent/prompts/01_orchestrator_requirements.md`.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-requirements` |
| Instanz (Multi-Instanz-Modus) | `phase1` |
| Skill | `aisdlc-grill-stress`, `aisdlc-prd-to-stories`, `aisdlc-traceability-check` |

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

## Regeln

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.

## Commit

```bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das Scope ist technisch erzwungen: es wird nur der Schritt-Scope gestaged, alles
  andere bleibt unangetastet.
- Das Scope wird ueber den **Index** gesetzt, nicht ueber eine Pfadspezifikation:
  `git reset -q` leert den Index, `git add -A -- <pfade>` staged nur den Schritt,
  `git commit` committet nur den Index.
  Grund: `git commit -- <pfade>` bricht ab, sobald ein Scope-Pfad keine getrackten
  Dateien hat ("pathspec did not match any files") — und der Weg ueber
  `--pathspec-from-file` ist unter dem globalen `rtk`-Plugin kaputt: `rtk` rewritet
  `git diff` und haengt einen "Changes:"-Block an, der als Pfadspezifikation
  wiederum mit "empty string is not a valid pathspec" abbricht.
- `git reset -q` unstaged fremde Arbeit, loescht sie aber nicht.
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Await human review of all artifacts before proceeding to Phase 2.