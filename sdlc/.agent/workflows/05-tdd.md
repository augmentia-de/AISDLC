---
description: Phase 5 — Test-Driven Setup (TDD)
---

# TDD workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/03_orchestrator_implementation.md`.

## Agent & Skill

- **Agent:** `aisdlc-execution` · **Skill:** `aisdlc-tdd`, `aisdlc-test-matrix`, `aisdlc-traceability-check`


##Steps

1. **Tests before code** — write a failing test, then implement it.
2. **Choose level consciously** — Unit tests for pure logic, integration tests for interfaces.
3. **Load context sparingly** — only what is really needed.
4. **Test data locally** — each test generates its own data.
5. **Mandatory cases** — Success case, Double call (idempotency), Invalid input,
   Lack of permission, dependency responds slowly or with errors.

## Output
`.agent/artifacts/` (test strategy, test cases)

##Rules

See `AGENTS.md`: no output commands, contract frozen after step 4.

## Commit
```
bash
mkdir -p tests/ .agent/artifacts/
git reset -q
git add -A -- tests/ .agent/artifacts/
git commit -m "test(sdlc-5): Teststrategie und fehlschlagende Tests" \
  -m "Schritt 5 - Test-Driven Setup | Agent: aisdlc-execution | Artefakte: tests/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## Traceability

After the tests, the chain can be verified for the first time. The check runs here:
```
bash
python3 .agent/tools/traceability_check.py
```
**Each test case carries the identifier of its acceptance criterion** (method name or
comment). Without it, the test remains unproven.

The **document** (`.agent/artifacts/traceability.md`) is sent from a separate order
creates the requirements agent — `--fill`, see `aisdlc-traceability-check`. distress
in this step: the execution agent is not allowed to write `docs/requirements/`.

##Gate
If the interface is unclear → return to phase 3 (Simplify).