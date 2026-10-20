---
description: Phase 4 — Grill-Me stress test for architecture validation
---

# Grill-Me Stress Test Workflow

## Prerequisites
- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/architecture.md` and `.agent/knowledge/openapi.yaml`.

## Steps

1. **Architekturfehler früh finden** — bevor implementiert wird.
2. **Schonungslose Befragung** — Designbaum aufbauen, von der Wurzel nach unten.
3. **Review gegen Standards und Spec** als zwei unabhaengige Achsen.
4. **Developer-Interface prüfen** über 9 Dimensionen.

## Output
`.agent/artifacts/` (Stresstest-Bericht, gefundenen Fehler, offene Fragen)

## Gate
Await human review. Bei Architekturfehlern → Rücksprung zu Phase 2.