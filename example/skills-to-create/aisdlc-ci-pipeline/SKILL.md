---
name: aisdlc-ci-pipeline
description: Generates the CI pipeline - tests, coverage gate, static analysis, image build and deployment with approval gates. Use this skill in step 9 to create the pipeline before the first deployment.
metadata:
  origin: draft for AISDLC
---

## Purpose

Make the pipeline the place where the quality gates are actually enforced, not a convention
that is forgotten under time pressure.

## When

Step 9, before the first deployment, and whenever the stack changes.

## Inputs

- `.agent/knowledge/stack.md` (runtime, test command, lint command, build command)
- `.agent/artifacts/test-report.md` (coverage target, test layout)
- `docs/qa/security-audit.md` (findings that must be gated)

## Procedure

1. **Test job** — install dependencies, run the test suite, publish the coverage report.
2. **Coverage gate** — fail the job below the project threshold. Use the value from the test
   report, not a guessed number.
3. **Static analysis** — the project's linter and, if configured, a security scan
   (dependency vulnerabilities, secret detection).
4. **Build job** — container image, tagged with the commit SHA **and** a moving tag.
5. **Deploy job** — only on the main branch, after all gates passed. Staging first, then
   production, each with a smoke test in between.
6. **Approval gate** — a protected environment that requires human approval before the
   production deploy. The gate in `AGENTS.md` and the gate in the pipeline are the same
   gate; the pipeline is where it is enforced.
7. **Secrets** — only from the CI secret store, never written to a log or a file in the repo.

## Output

- `.github/workflows/<pipeline>.yml`
- `.agent/artifacts/ci-pipeline.md` — jobs, gates, thresholds, secret names, rollback trigger
- Updated `docs/execution/CHANGELOG.md` when a release is produced

## Rules

- Every job has an explicit pass/fail criterion. No "continue on error" on a gate job.
- The coverage gate value comes from the test report.
- The deployment job is unreachable without the approval gate.
- The pipeline fails if the security scan reports a critical finding.
- No secret in a command line that ends up in the log.
