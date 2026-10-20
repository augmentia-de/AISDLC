---
description: Phase 2 — Architecture design for AI SDLC
---

# Architecture Workflow

## Prerequisites
- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/requirements.md` and `.agent/knowledge/stack.md`.
- Bestehende Architektur-Dokumentation: `docs/architecture/`
- Bestehende API-Spezifikation: `docs/api/openapi.yaml`
- ADRs: `docs/adr/`

## Steps

1. **Design-Optionen bewerten** und passende Entwurfsmuster/Architekturen auswählen.
2. **API-Schnittstellen spezifizieren** (Request/Response-Schemas, Typen, Statuscodes) in `docs/api/openapi.yaml`.
3. **System- und Komponentendiagramme** entwerfen (C4-Modell) in `docs/architecture/diagrams/*.mmd`.
4. **ADRs dokumentieren** in `docs/adr/ADR-<ID>-<titel>.md` (Nygard-Schema).

## Output
- Knowledge files to `.agent/knowledge/` (openapi.yaml, schema.sql, stack.md).
- Artifacts to `.agent/artifacts/` (architecture.md, folder-structure.md).
- Docs to `docs/architecture/`, `docs/api/`, `docs/adr/`.

## HARD GATE
**Stop here. Await human approval before any code is written.**

Prüfen: PRD vollständig, API-Contract stabil, ADRs ok.