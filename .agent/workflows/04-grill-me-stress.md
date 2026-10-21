---
description: Phase 4 — Grill-Me stress test for architecture validation
---

# Grill-Me Stress Test Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- Read `.agent/artifacts/architecture.md` and the contract `docs/api/openapi.yaml`
  (`.agent/knowledge/openapi.yaml` ist nur ein Verweis darauf).

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-architecture` |
| Instanz (Multi-Instanz-Modus) | `phase2` |
| Skill | `aisdlc-grill-stress` |

## Steps

1. **Architekturfehler früh finden** — bevor implementiert wird.
2. **Schonungslose Befragung** — Designbaum aufbauen, von der Wurzel nach unten.
3. **Review gegen Standards und Spec** als zwei unabhaengige Achsen.
4. **Developer-Interface prüfen** über 9 Dimensionen.

## Output
`.agent/artifacts/` (Stresstest-Bericht, gefundenen Fehler, offene Fragen)

## Regeln

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.

## Commit

```bash
mkdir -p docs/architecture/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ .agent/artifacts/
git commit -m "docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen" \
  -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/"
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
Await human review. Bei Architekturfehlern → Rücksprung zu Phase 2.