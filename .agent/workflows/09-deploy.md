---
description: Phase 9 — Deployment and operations
---

# Deploy Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/test-report.md` — must show zero Critical issues.
- Read `.agent/artifacts/security-audit.md` — must show zero Critical issues.
- Phase 8 HARD GATE must have been passed (human-approved).

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-qa` |
| Instanz (Multi-Instanz-Modus) | `phase4` |
| Skill | `aisdlc-deploy` |

## Steps

1. **Validate reports**: Confirm no Critical issues in QA and Security reports.
2. **Deploy to staging**: Use CI/CD pipeline.
3. **Smoke tests on staging**: Health endpoint, submit test brief, verify PDF download.
4. **Promote to production**: If smoke tests pass.
5. **Produce deploy report** (`.agent/artifacts/deploy-report.md`):
   - Staging URL, Production URL, Smoke test results (pass/fail)

## Output
`.agent/artifacts/deploy-report.md`

## Commit

```bash
git add docs/execution/ infra/ .github/ .agent/artifacts/
git commit -m "build(sdlc-9): Deployment-Pipeline und Deploy-Report" \
  -m "Schritt 9 - Deployment & Betrieb | Agent: aisdlc-qa | Artefakte: docs/execution/, infra/, .github/, .agent/artifacts/" \
  -- docs/execution/ infra/ .github/ .agent/artifacts/
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das `-- <pfade>`-Scope ist technisch erzwungen: selbst bei `git add -A` committet
  nur der angegebene Pfad.
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Bei Produktions-Incidents → Rücksprung zu Phase 2.