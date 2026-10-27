---
description: Phase 2 — Architecture design for AI SDLC
---

# Architecture Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture`  ·  **Skill:** `aisdlc-openapi-first`, `aisdlc-glossary-adr`, `aisdlc-migration-plan`


## Steps

1. **Design-Optionen bewerten** und passende Entwurfsmuster/Architekturen auswählen.
2. **API-Schnittstellen spezifizieren** (Request/Response-Schemas, Typen, Statuscodes) in `docs/api/openapi.yaml`.
3. **System- und Komponentendiagramme** entwerfen (C4-Modell) in `docs/architecture/diagrams/*.mmd`.
4. **ADRs dokumentieren** in `docs/adr/ADR-<ID>-<titel>.md` (Nygard-Schema).

## Output
- Contract to `docs/api/openapi.yaml` — der **einzige** Ort des Vertrags.
  `.agent/knowledge/openapi.yaml` ist ein Verweis darauf, keine Kopie.
- Knowledge to `.agent/knowledge/`: `schema.sql` (kanonisch, byte-identisch zur
  Kopie in `docs/architecture/schema.sql` — Spiegel-Regel, ADR-019 §6) und
  `stack.md` (die Stack-Entscheidung aus ADR-001).
- Artifacts to `.agent/artifacts/` (architecture.md, folder-structure.md).
- Docs to `docs/architecture/`, `docs/api/`, `docs/adr/`.

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git commit -m "docs(sdlc-2): API-Contract, Domain-Modell und ADRs" \
  -m "Schritt 2 - Konzept & Architektur | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## HARD GATE
**Stop here. Await human approval before any code is written.**

Prüfen: PRD vollständig, API-Contract stabil, ADRs ok.