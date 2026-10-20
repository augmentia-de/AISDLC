---
description: Phase 4 — Grill-Me stress test for architecture validation
---

# Grill-Me Stress Test Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/architecture.md` and `.agent/knowledge/openapi.yaml`.

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

## Commit

```bash
git add -A .
git diff --cached --name-only -- docs/architecture/ .agent/artifacts/ \
  | git commit -m "docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen" \
             -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/" \
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
Await human review. Bei Architekturfehlern → Rücksprung zu Phase 2.