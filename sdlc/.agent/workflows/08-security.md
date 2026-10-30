---
description: Phase 8 — Security and compliance scan
---

# Security Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/04_orchestrator_testing.md`.

## Checks

1. **Authentication**: All routes require valid JWT. Test with missing auth header → 401.
2. **Secrets exposure**: Grep for API keys, tokens, passwords in source. Verify `.env` in `.gitignore`.
3. **SQL injection**: Verify parameterized queries (ORM). No string concatenation in SQL.
4. **XSS**: Verify frontend sanitizes input. Check for `dangerouslySetInnerHTML`.
5. **Docker security**: Containers run as non-root, specific base image tags, no unnecessary ports.
6. **OWASP Top 10** general scan.

## Agent & Skill

- **Agent:** `aisdlc-qa` · **Skill:** `aisdlc-security-scan`


## Output
`.agent/artifacts/security-audit.md` with findings rated: Critical / High / Medium / Low.

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht" \
  -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
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

## HARD GATE
**After Security is complete, stop and await human approval before Deploy Agent runs.**

Check: 0 Critical, 0 High. For vulnerabilities → return to phase 6.