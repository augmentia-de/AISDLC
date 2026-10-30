---
description: Phase 0 — Migration assessment & legacy inventory (brownfield/migration mode)
---

# Migration Assessment Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/00_orchestrator_analysis.md`.
- `.agent/config.json` must have `mode: migration` or `mode: brownfield`.

## Agent & Skill

- **Agent:** `aisdlc-requirements` (or `aisdlc-architecture` for tech-heavy systems)
- **Skill:** `module-migration`, `strangler-fig`

## Steps

1. **Inventory** — Module, Routen, Jobs, Integrationen, Datenbanken, Queues, Cron, Config-Dateien.
2. **Contract discovery** — Bestehende API-Contracts (OpenAPI/GraphQL/WSDL), implizite DTOs, Fehlerformate.
3. **Data model extraction** — Tabellen, Relationen, Migrationsskripte, Seed-Daten.
4. **Dependency map** — Interne/externe Abhängigkeiten, verdeckte Kopplungen.
5. **Risk & debt register** — Technische Schulden, fehlende Tests, unbekannte Owner.
6. **Scope proposal** — Was wird migriert, was bleibt legacy, was wird deprecated.

## Output

- `docs/migration/inventory.md`
- `docs/migration/dependency-map.md`
- `docs/migration/risk-register.md`
- `docs/migration/scope.md`
- `.agent/artifacts/migration-assessment.md`

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren (nur für **neue** externe/public Verträge).
Für Adapter zwischen Alt/Neu gilt **kein** Contract-Freeze.

## Commit

```bash
mkdir -p docs/migration/ .agent/artifacts/
git reset -q
git add -A -- docs/migration/ .agent/artifacts/
git commit -m "docs(sdlc-0): Migration-Assessment und Legacy-Inventar" \
  -m "Schritt 0 - Migration-Assessment | Agent: aisdlc-requirements | Artefakte: docs/migration/, .agent/artifacts/"
```

- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Gate

Optional (MVP) / Pflicht (Enterprise): Menschliche Freigabe des Scope, bevor Migration-Plan erstellt wird.