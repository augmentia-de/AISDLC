---
name: aisdlc-ci-pipeline
description: Phase 9: Create CI pipeline with test, coverage, security and deploy gates.
metadata:
  origin: own framework procedure; Craft from ECC/deployment-patterns + ECC/github-actions-templates
---

## When

**Phase 9**, before the first deployment — and whenever the stack changes.

## The iron law
```
text
DIE QUALITAETSGATES GEHOEREN IN DIE PIPELINE, NICHT IN DIE KONVENTION
```
A convention that is forgotten under time pressure is not a security. This
Coverage gate, security scan and release before deployment are included
the build, not into a file that you read.

## Jobs

1. **Test Job** — Install dependencies, run tests, coverage
   publish.
2. **Coverage Gate** — the job fails below the project threshold. The value
   comes from the test report, not recommended.
3. **Static Analysis** — Linter and, if configured, a security scan
   (vulnerabilities in dependencies, secret detection).
4. **Build job** — Container image tagged with commit SHA **and** a movable
   day.
5. **Deploy job** — only on `main`, only after all gates. staging before production,
   with smoke test in between.
6. **Release Gate** — a protected environment that human release from
   Production deployment required. The gate in `AGENTS.md` and the gate in the
   pipeline are **the same** gate; the pipeline is where it is enforced.
7. **Secrets** — exclusively from the secret store, never in a log or a
   File in the repo.

## Output

- `.github/workflows/<pipeline>.yml`
- `.agent/artifacts/ci-pipeline.md` — jobs, gates, thresholds, secret names,
  Rollback trigger
- `docs/execution/CHANGELOG.md` on a release

## Rules

- Every job has an explicit pass or fail criteria. None
  "continue on error" in a gate job.
- The coverage threshold comes from the test report.
- The deploy job cannot be reached without a release gate.
- The pipeline fails if the security scan reports a critical finding.
- No secret in a command that ends up in the log.
- An image tag without SHA is not a release.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| Pipeline patterns, deployment strategies, rollback | `deployment-patterns` |
| GitHub Actions Workflows | `github-actions-templates` |
| Staging/production separation | `deployment-pipeline-design` |
| Secrets in CI | `secrets-management` |
| SAST configuration | `sast-configuration` |