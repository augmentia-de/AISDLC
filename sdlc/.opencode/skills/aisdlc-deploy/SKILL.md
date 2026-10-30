---
name: aisdlc-deploy
description: Phase 9: Deployment pipeline, rollout, smoke tests, runbook, rollback.
metadata:
  origin: adapted from wshobson/deployment-pipeline-design + wshobson/deployment-strategies + wshobson/deployment-validation + wshobson/slo-implementation + wshobson/incident-runbook-templates + ECC/deployment-patterns + ECC/docker-patterns
---

## Requirements (Gate)

Check before deploying and start **only if the conditions are met**:

| Condition | Proof |
|---|---|
| QA Report: 0 Critical | `docs/qa/` test report |
| Security Report: 0 Critical, 0 High | `docs/qa/security-audit.md` |
| Coverage >= project threshold | Coverage report |
| Human approval after phase 8 | confirmed |
| Migration plan declining | documented (if schema change) |

## 1. Pipeline stages
```
text
Build -> Test -> Security-Scan -> Package -> Staging -> Smoke -> Approval -> Prod
```
Each stage has: **Entry criterion, an executable check, a result, a
Termination criterion.** No step without a clear result - "green" or "red", not "looks good".

**Approval gates** where a mistake would be expensive: before staging (after security scan) and before
Production (after smoke tests).

## 2. Rollout strategies

| Strategy | Failure effect | Use |
|---|---|---|
| **Recreate** | Downtime | only maintenance window, migration-free |
| **Rolling** | short inconsistent | Default for compatible changes |
| **Blue/Green** | instant, double costs | Database Compatible Releases |
| **Canary** | exponentially small first | high risk, finest control |

**Migration compatibility** (Expand/Contract) must match the strategy: expanding
Scheme allows rolling, contract only after complete rollout.

## 3. Validation before deployment (pre-flight)

- Configuration complete and valid - **fail closed**, does not fall back to default.
- Secrets/variables exist in the target environment.
- Health and Readiness checks pass **before** traffic switching.
- Resources (CPU, memory, replicas) match the load profile.
- Rollback path specifically: which image, which command, how long.
- Versioned artifact (digest/tag), no `latest`.

## 4. Smoke testing for staging

1. Health endpoint responds.
2. A representative end-to-end flavor runs through (create, read, change, delete).
3. Authentication and authorization take effect (401/403 as expected).
4. Dependencies accessible (DB, queue, external API).
5. Logs do not show unexpected errors.

## 5. Promotion and rollback

- **Promotion** only after green smoke tests and approval.
- **Rollback** is part of the deployment, not the emergency plan afterwards: command, order,
  Data status handling and authorization must be determined in advance.
- For Canary: Set abort criteria (error rate, latency, SLO burn rate) **before** deployment.

## 6. Observability and Operations (SLIs/SLOs)

| level | Content |
|---|---|
| **SLI** | measurable user signals (success rate, latency, availability) |
| **SLO** | Target value via SLI (e.g. 99.9% success in 30 days) |
| **Error Budget** | remaining tolerance; when consumed: Feature Freeze |
| **Alerting** | Symptom-based (user effect), not cause-based |
| **Dashboards** | via SLI, with the relevant dimensions |

Alerts need a **Runbook** reference: detection, diagnosis, mitigation, recovery,
Escalation, responsible person.

## 7. Runbook structure (incident)

1. **Detection** - Alert, severity, first 5 minutes
2. **Triage** - what's broken, blast radius, user impact
3. **Mitigation** - concrete action with an order
4. **Recovery** - verification, monitoring, dismantling
5. **Communication** - who reports what to whom
6. **Postmortem** - Cause, Contributors, Prevention, with date

A runbook is written for a person at 3 a.m.: not an anamnesis, but
Commands.

## 8. Rollback plan

| step |Statement |
|---|---|
| Triggers | exact alarm or error value |
| command | exact deploy/rollback command |
| Order | Migration back? Data first or code? |
| Duration | realistic estimated duration |
| Communication | who informs whom |
| Follow-up inspection | which test confirms the recovery?

## Output

`docs/execution/deploy-report.md`:
- Environments and versions (digest/tag)
- Staging and production URL
- Smoke test results (pass/fail per check)
- Rollback plan and triggers
- SLO/Alerting status