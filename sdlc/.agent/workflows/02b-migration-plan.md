---
description: Phase 2b — Migration plan (strangler-fig / module-migration)
---

# Migration Plan Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read `docs/migration/*` (Assessment output).
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture`
- **Skill:** `aisdlc-migration-plan`, `strangler-fig`, `aisdlc-glossary-adr`

## Steps

1. **Zielbild** — Zielarchitektur, Modulgrenzen, neue Verträge (nur wo nötig).
2. **Mapping** — Alt → Neu (Modul, Route, Tabelle, Job). Datei: `docs/migration/mapping.md` (oder `.json`).
3. **Anti-Corruption Layer** — Adapter/ACL zwischen Alt und Neu definieren.
4. **Cutover-Strategie** — Schrittweise Umschaltung (Routing, Feature-Flags, Canary).
5. **Datenmigration** — Skripte, Idempotenz, Rollback, Validierung.
6. **Backout-Plan** — Wie man zurückkommt (ohne Datenverlust).
7. **ADRs** — `migration/strategy`, `cutover`, `rollback`, `data-migration`.

## Output

- `docs/migration/mapping.md`
- `docs/migration/cutover-checklist.md`
- `docs/migration/rollback-plan.md`
- `docs/migration/data-migration.md`
- `docs/adr/ADR-xxx-migration-*.md`
- `.agent/artifacts/migration-plan.md`

## Regeln

- Contract-Freeze gilt **nicht** für Adapter/ACL. Nur neue externe/public Verträge sind eingefroren.
- Keine Big-Bang-Umschaltung ohne Cutover-Gate.
- Datenmigrationsskripte müssen idempotent und mit Rollback versehen sein.

## Commit

```bash
mkdir -p docs/migration/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/migration/ docs/adr/ .agent/artifacts/
git commit -m "docs(sdlc-2b): Migration-Plan und Cutover-Strategie" \
  -m "Schritt 2b - Migration-Plan | Agent: aisdlc-architecture | Artefakte: docs/migration/, docs/adr/, .agent/artifacts/"
```

## HARD GATE

**GATE 1 (Migration):** Menschliche Freigabe von Zielbild, Mapping und Cutover-Strategie vor Implementierung.