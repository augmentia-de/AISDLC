---
description: Phase 5 — Test-Driven Setup (TDD)
---

# TDD Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/architecture.md` and `.agent/knowledge/openapi.yaml`.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-execution` |
| Instanz (Multi-Instanz-Modus) | `phase3` |
| Skill | `aisdlc-tdd` |

## Steps

1. **Tests vor dem Code** — fehlschlagenden Test schreiben, dann implementieren.
2. **Ebene bewusst wählen** — Unit-Tests für reine Logik, Integrationstests für Schnittstellen.
3. **Kontext sparsam laden** — nur was wirklich gebraucht wird.
4. **Testdaten lokal** — jeder Test erzeugt seine Daten selbst.
5. **Pflichtfälle** — Erfolgsfall, Doppelter Aufruf (Idempotenz), Ungültige Eingabe,
   Fehlende Berechtigung, Abhängigkeit antwortet langsam oder mit Fehler.

## Output
`.agent/artifacts/` (Test-Strategie, Testfälle)

## Gate
Bei unklarem Interface → Rücksprung zu Phase 3 (Simplify).