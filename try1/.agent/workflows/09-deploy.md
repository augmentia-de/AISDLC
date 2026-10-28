---
description: Phase 9 — Deployment and operations
---

# Deploy Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/04_orchestrator_testing.md`.

## Agent & Skill

- **Agent:** `aisdlc-qa`  ·  **Skill:** `aisdlc-ci-pipeline`, `aisdlc-containerize`, `aisdlc-deploy`


## Steps

1. **Validate reports**: Confirm no Critical issues in QA and Security reports.
2. **Deploy to staging**: Use CI/CD pipeline.
3. **Smoke tests on staging**: Health endpoint, submit test brief, verify PDF download.
4. **Promote to production**: If smoke tests pass.
5. **Produce deploy report** (`.agent/artifacts/deploy-report.md`):
   - Staging URL, Production URL, Smoke test results (pass/fail)

## Output
`.agent/artifacts/deploy-report.md`

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p docs/execution/ infra/ .github/ .agent/artifacts/
git reset -q
git add -A -- docs/execution/ infra/ .github/ .agent/artifacts/
git commit -m "build(sdlc-9): Deployment-Pipeline und Deploy-Report" \
  -m "Schritt 9 - Deployment & Betrieb | Agent: aisdlc-qa | Artefakte: docs/execution/, infra/, .github/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

## Traceability als Gate-Voraussetzung

Vor dem Gate laeuft der Check — **0 Luecken** sind Bedingung:

```bash
python3 .agent/tools/traceability_check.py
```

Eine Luecke heisst: eine Anforderung mit Story hat keinen belegten Test. Der Check
vergleicht die Artefakte mit dem Dateisystem — eine gefuellte Matrix ist kein Beleg,
die existierende Datei ist es.

## Gate
Bei Produktions-Incidents → Rücksprung zu Phase 2.