---
description: Phase 3 — Simplify & Reduction (vor dem Stresstest)
---

# Simplify Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/architecture.md` and `.agent/knowledge/stack.md`.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-architecture` |
| Instanz (Multi-Instanz-Modus) | `phase2` |
| Skill | `aisdlc-simplify` |

## Steps

1. **Komplexität identifizieren** — welche Teile der Architektur sind überdimensioniert?
2. **Reduzieren** — nur das Nötigste behalten, Over-Engineering eliminieren.
3. **Vereinfachen** — Klarheit vor Vollständigkeit.

## Output
`.agent/artifacts/` (aktualisierte Architektur, Reduktionsbericht)

## Gate
Await human review. Bei zu viel Komplexität → Rücksprung zu Phase 2.