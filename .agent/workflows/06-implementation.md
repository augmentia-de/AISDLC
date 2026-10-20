---
description: Phase 6 — Implementation and coding (parallel build streams)
---

# Implementation Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/knowledge/openapi.yaml`, `schema.sql`, `stack.md`.
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
| Skill | — (kein Skill nötig) |

## Output
Working application code in `src/`, `tests/`, `infra/`.
Inline documentation (JSDoc/Docstrings).
Commits (Conventional Commits) and PR descriptions.

## Commit

```bash
git add -A .
git diff --cached --name-only -- src/ tests/ docs/execution/ \
  | git commit -m "feat(sdlc-6): Implementierung der Stories" \
             -m "Schritt 6 - KI-Code-Generation | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/" \
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
Bei fehlenden Tests → Rücksprung zu Phase 5.