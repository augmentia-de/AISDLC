---
description: Phase 6 — Implementation and coding (parallel build streams)
---

# Implementation Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/03_orchestrator_implementation.md`.

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

- **Agent:** `aisdlc-execution`  ·  **Skill:** `aisdlc-feature-scaffold`, `aisdlc-containerize`


## Output
Working application code in `src/`, `tests/`, `infra/`.
Inline documentation (JSDoc/Docstrings).
Commits (Conventional Commits) and PR descriptions.

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p src/ tests/ docs/execution/
git reset -q
git add -A -- src/ tests/ docs/execution/
git commit -m "feat(sdlc-6): Implementierung der Stories" \
  -m "Schritt 6 - KI-Code-Generation | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Logging

Die Anwendung loggt **strukturiert**: eine Zeile je Ereignis mit Korrelations-ID,
Ereignistyp, Mandant, Ergebnis und Dauer. **Keine** Nutzdaten, keine Secrets, keine
Tokens, keine vollstaendigen Personendatensaetze.

Die Konvention wird in `.agent/artifacts/logging.md` festgeschrieben — der
Security-Scan prueft spaeter **gegen diese Datei**, nicht gegen eine eigene
Erwartung. Sie entsteht im selben Schritt wie der Code, weil sie aus dem Code
heraus folgt.

## Gate
Bei fehlenden Tests → Rücksprung zu Phase 5.