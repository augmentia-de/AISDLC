---
description: Phase 9 — Deployment and operations
---

# Deploy workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/04_orchestrator_testing.md`.

## Agent & Skill

- **Agent:** `aisdlc-qa` · **Skill:** `aisdlc-ci-pipeline`, `aisdlc-containerize`, `aisdlc-deploy`


## Steps

1. **Validate reports**: Confirm no Critical issues in QA and Security reports.
2. **Local Docker deployment — mandatory first target.**
   - `docker compose -f infra/docker-compose.yml build`
   - `docker compose -f infra/docker-compose.yml up -d`
   - Wait for the health check, then run the smoke tests against `http://127.0.0.1:8080`.
   - Verify the file logs: `chat.log` and `error.log` exist, rotate at 2 MB
     (step 6, "File logging"), and contain no payload.
   - Only a green local run justifies the next stage. No cloud credentials and
     no external environment are required for this stage.
3. **Smoke tests** (local, then optionally staging): health endpoint, create a
   ticket, add a comment, change the status, read the thread.
4. **Staging / production** — only if an environment is configured: same image
   digest, promotion after green smoke tests. If no environment exists, write
   "not configured" in the deploy report; never invent URLs or digests.
5. **Produce deploy report** (`.agent/artifacts/deploy-report.md`):
   - local Docker status: build, up, health check, smoke tests (pass/fail)
   - log verification: files, size, rotation, no payload
   - staging/production URL or "not configured"

## Output
Project README at root (`README.md`) updated with quickstart/deployment reference.
`.agent/artifacts/deploy-report.md`

## Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p docs/execution/ infra/ .github/ .agent/artifacts/
git reset -q
git add -A -- docs/execution/ infra/ .github/ .agent/artifacts/
git commit -m "build(sdlc-9): Deployment-Pipeline und Deploy-Report" \
  -m "Schritt 9 - Deployment & Betrieb | Agent: aisdlc-qa | Artefakte: docs/execution/, infra/, .github/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## Traceability as a gate requirement

The check runs in front of the gate - **0 gaps** are a condition:
```
bash
python3 .agent/tools/traceability_check.py
```
A gap means: a requirement with a story does not have a documented test. The check
compares the artifacts with the file system - a filled matrix is not evidence,
the existing file is it.

## Gate
For production incidents → return to phase 2.