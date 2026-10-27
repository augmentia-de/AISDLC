---
description: Phase 3 — Simplify & Reduction (vor dem Stresstest)
---

# Simplify Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.

## Agent & Skill

- **Agent:** `aisdlc-architecture`  ·  **Skill:** `aisdlc-simplify`


## Steps

1. **Komplexität identifizieren** — welche Teile der Architektur sind überdimensioniert?
2. **Reduzieren** — nur das Nötigste behalten, Over-Engineering eliminieren.
3. **Vereinfachen** — Klarheit vor Vollständigkeit.

## Output
`.agent/artifacts/` (aktualisierte Architektur, Reduktionsbericht)

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/
git commit -m "refactor(sdlc-3): Architektur vereinfacht" \
  -m "Schritt 3 - Simplify & Reduktion | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Gate
Await human review. Bei zu viel Komplexität → Rücksprung zu Phase 2.