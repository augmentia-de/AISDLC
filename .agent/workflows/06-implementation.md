---
description: Phase 6 — Implementation and coding (parallel build streams)
---

# Implementation Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- Read the contract `docs/api/openapi.yaml` (`.agent/knowledge/openapi.yaml` ist nur ein
  Verweis darauf), `.agent/knowledge/schema.sql` und `.agent/knowledge/stack.md`.
- Confirm Phase 2 HARD GATE has been passed (human-approved).

## Parallel Build Streams

1. **Backend Stream** (`src/`):
   - Scaffold app with entry point
   - Implement all endpoints from `openapi.yaml`
   - Set up database with `DATABASE_URL` env var
   - Add JWT auth middleware (scoped by `organisation_id`)
   - Integrate external AI/LLM service
   - Implement export functionality
   - Write tests in `tests/`

2. **Frontend Stream** (`frontend/` or `src/frontend/`):
   - Scaffold app with App Router
   - Build submission form with privacy notice
   - Build results display page
   - Build export button
   - All API calls must match `openapi.yaml`

3. **Infra Stream** (`infra/`):
   - Create Dockerfiles (multi-stage, non-root)
   - Create docker-compose.yml (local dev)
   - Create CI/CD pipeline (GitHub Actions)

## Synchronisation
- All streams work against the same `openapi.yaml` (Phase 2 Output).
- No stream may modify `openapi.yaml` — return to Phase 2 if needed.
- Merge point: all streams must be green before Phase 7 begins.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-execution` |
| Instanz (Multi-Instanz-Modus) | `phase3` |
| Skill | `aisdlc-feature-scaffold`, `aisdlc-containerize` |

## Output
Working application code in `src/`, `tests/`, `infra/`.
Inline documentation (JSDoc/Docstrings).
Commits (Conventional Commits) and PR descriptions.

## Regeln

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.
- **The contract `docs/api/openapi.yaml` is frozen** (since step 4). A contract defect is a finding for a jump back to step 2, not an edit in passing.

## Commit

```bash
mkdir -p src/ tests/ docs/execution/
git reset -q
git add -A -- src/ tests/ docs/execution/
git commit -m "feat(sdlc-6): Implementierung der Stories" \
  -m "Schritt 6 - KI-Code-Generation | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/"
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
Bei fehlenden Tests → Rücksprung zu Phase 5.