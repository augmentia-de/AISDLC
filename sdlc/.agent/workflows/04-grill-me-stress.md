---
description: Phase 4 — Grill-Me stress test for architecture validation
---

# Grill-Me Stress Test Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture`  ·  **Skill:** `aisdlc-grill-stress`


## Steps

1. **Architekturfehler früh finden** — bevor implementiert wird.
2. **Schonungslose Befragung** — Designbaum aufbauen, von der Wurzel nach unten.
3. **Review gegen Standards und Spec** als zwei unabhaengige Achsen.
4. **Developer-Interface prüfen** über 9 Dimensionen.

## Output
`.agent/artifacts/` (Stresstest-Bericht, gefundenen Fehler, offene Fragen)

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p docs/architecture/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ .agent/artifacts/
git commit -m "docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen" \
  -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Gate
Await human review. Bei Architekturfehlern → Rücksprung zu Phase 2.