---
description: Phase 9 — Deployment and operations
---

# Deploy Workflow

## Prerequisites
- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/test-report.md` — must show zero Critical issues.
- Read `.agent/artifacts/security-audit.md` — must show zero Critical issues.
- Phase 8 HARD GATE must have been passed (human-approved).

## Steps

1. **Validate reports**: Confirm no Critical issues in QA and Security reports.
2. **Deploy to staging**: Use CI/CD pipeline.
3. **Smoke tests on staging**: Health endpoint, submit test brief, verify PDF download.
4. **Promote to production**: If smoke tests pass.
5. **Produce deploy report** (`.agent/artifacts/deploy-report.md`):
   - Staging URL, Production URL, Smoke test results (pass/fail)

## Output
`.agent/artifacts/deploy-report.md`

## Gate
Bei Produktions-Incidents → Rücksprung zu Phase 2.