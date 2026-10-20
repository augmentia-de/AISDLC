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

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-architecture` |
| Instanz (Multi-Instanz-Modus) | `phase2` |
| Skill | — (kein Skill nötig) |

## Steps

1. **Design-Optionen bewerten** und passende Entwurfsmuster/Architekturen auswählen.
2. **API-Schnittstellen spezifizieren** (Request/Response-Schemas, Typen, Statuscodes) in `docs/api/openapi.yaml`.
3. **System- und Komponentendiagramme** entwerfen (C4-Modell) in `docs/architecture/diagrams/*.mmd`.
4. **ADRs dokumentieren** in `docs/adr/ADR-<ID>-<titel>.md` (Nygard-Schema).

## Output
- Knowledge files to `.agent/knowledge/` (openapi.yaml, schema.sql, stack.md).
- Artifacts to `.agent/artifacts/` (architecture.md, folder-structure.md).
- Docs to `docs/architecture/`, `docs/api/`, `docs/adr/`.

## Commit

```bash
git add -A .
git diff --cached --name-only -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/ \
  | git commit -m "docs(sdlc-2): API-Contract, Domain-Modell und ADRs" \
             -m "Schritt 2 - Konzept & Architektur | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/" \
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

## HARD GATE
**Stop here. Await human approval before any code is written.**

Prüfen: PRD vollständig, API-Contract stabil, ADRs ok.