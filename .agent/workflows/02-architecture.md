---
description: Phase 2 — Architecture design for AI SDLC
---

# Architecture Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- Read `.agent/artifacts/requirements.md` and `.agent/knowledge/stack.md`.
- Bestehende Architektur-Dokumentation: `docs/architecture/`
- Bestehende API-Spezifikation: `docs/api/openapi.yaml`
- ADRs: `docs/adr/`

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-architecture` |
| Instanz (Multi-Instanz-Modus) | `phase2` |
| Skill | `aisdlc-openapi-first` |

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

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.

## Commit

```bash
mkdir -p docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git commit -m "docs(sdlc-2): API-Contract, Domain-Modell und ADRs" \
  -m "Schritt 2 - Konzept & Architektur | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/"
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

## HARD GATE
**Stop here. Await human approval before any code is written.**

Prüfen: PRD vollständig, API-Contract stabil, ADRs ok.