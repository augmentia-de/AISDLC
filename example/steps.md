# TaskFlow step by step — inputs, artifacts, skills

Every step lists: what it consumes, what it produces, which skills already exist, and which
skills are still missing. Missing skills have a ready-to-copy draft in `skills-to-create/`.

Legend: **[present]** = exists in AISDLC · **[missing]** = draft in `skills-to-create/`

---

## Step 0 — Legacy analysis (optional)

**Purpose:** Understand an existing codebase before changing it. For TaskFlow (greenfield)
this step is skipped — that is exactly what the optional step is for.

**Consumes:** legacy source tree
**Produces:** `.agent/artifacts/folder-structure.md`, architecture inventory
**Skills:** **[missing]** `aisdlc-legacy-inventory` (only for brownfield projects)

---

## Step 1 — Requirements gathering

**Purpose:** Turn "an app where I can track my tasks" into a specification that later
agents can execute without guessing.

**Consumes:** user request, `.agent/artifacts/requirements.md`
**Produces:**

| Artifact | Path | Content |
|---|---|---|
| PRD | `docs/requirements/PRD.md` | vision, personas, goals, KPIs, MoSCoW scope, functional + non-functional requirements |
| User stories | `docs/requirements/stories/*.json` | one file per story: id, epic, title, acceptance criteria, requirement ids |
| BDD features | `docs/requirements/features/*.feature` | Gherkin scenarios per story |
| Traceability matrix | `docs/requirements/traceability_matrix.json` | requirement id → story → acceptance criteria |
| Risk register | `.agent/artifacts/risk-register.md` | top risks with likelihood, impact, mitigation |
| Requirements summary | `.agent/artifacts/requirements.md` | condensed view for the review gate |
| User stories (readable) | `.agent/artifacts/user-stories.md` | epic → story → criteria |

**Skills:** **[available]** `aisdlc-grill-stress` (step 1a) · **[missing]** `aisdlc-prd-to-stories`

**Gate:** human reviews PRD, stories, acceptance criteria before step 2.

**TaskFlow specifics:** the ambiguous point is the status workflow — "Done" without "In
"Progress"? — exactly what the grill must settle.

---

## Step 2 — Concept & architecture

**Purpose:** Freeze the interfaces before any code exists.

**Consumes:** `docs/requirements/*`, `.agent/knowledge/stack.md`
**Produces:**

| Artifact | Path | Content |
|---|---|---|
| API contracts | `docs/api/openapi.yaml` | OAS 3.0+, all endpoints, schemas, status codes, error format |
| Domain model | `docs/architecture/domain_model.md` | entities, relationships, invariants, lifecycle |
| Diagrams | `docs/architecture/diagrams/*.mmd` | C4 context/container/component as Mermaid |
| ADRs | `docs/adr/ADR-<ID>-<title>.md` | context, decision, consequences, alternatives |
| Architecture summary | `.agent/artifacts/architecture.md` | review view for the gate |
| Folder plan | `.agent/artifacts/folder-structure.md` | exact target layout of the app |
| DB schema | `.agent/knowledge/schema.sql` | tables, indexes, retention policy |

**Skills:** **[available]** `aisdlc-simplify`, `aisdlc-grill-stress` ·
**[missing]** `aisdlc-openapi-first`, `aisdlc-glossary-adr`

**🚧 Gate 1** — human approves the contract and the schema before any application code.

**TaskFlow specifics:** ADR-001 records the stack, ADR-002 the 90-day retention vs. right-to-erasure
tension, ADR-003 the forward-only status workflow.

---

## Step 3 — Simplify & reduce

**Purpose:** Cut over-engineering before the stress test — otherwise the test reviews
complexity that should not exist.

**Consumes:** `docs/architecture/*`, `.agent/artifacts/architecture.md`
**Produces:** simplified design (edited in place), `.agent/artifacts/architecture.md` (updated)

**Skills:** **[available]** `aisdlc-simplify` · **[missing]** — (covered)

**TaskFlow specifics:** the generic `Task` with tags, comments and attachments is cut — the PRD
puts them out of scope.

---

## Step 4 — Grill-me stress test

**Purpose:** Find the architectural errors that are still cheap to fix.

**Consumes:** `docs/api/openapi.yaml`, `docs/adr/`, `docs/architecture/`
**Produces:** findings per axis (standards / spec), open decisions, `.agent/artifacts/architecture.md`

**Skills:** **[available]** `aisdlc-grill-stress` · **[missing]** — (covered)

**TaskFlow specifics:** catches that "erase task" and "90-day retention" contradict each other
for the history table — resolved by an ADR before coding.

---

## Step 5 — Test-driven setup

**Purpose:** Decide what is tested where, before the first line of implementation.

**Consumes:** `docs/requirements/features/*.feature`, `docs/api/openapi.yaml`
**Produces:**

| Artifact | Path | Content |
|---|---|---|
| Test strategy | `.agent/artifacts/test-report.md` (plan section) | levels, fixtures, mandatory cases |
| Test matrix | `.agent/artifacts/` + `docs/requirements/` | acceptance criterion → test level → test file |
| Failures first | `tests/unit/`, `tests/integration/`, `tests/e2e/` | failing tests that drive the implementation |

**Skills:** **[available]** `aisdlc-tdd` · **[missing]** `aisdlc-test-matrix`

**TaskFlow specifics:** the tenant-isolation test is an integration test with a real database,
not a unit test — the matrix must say so explicitly.

---

## Step 6 — Code generation

**Purpose:** Build the application in parallel streams against the frozen contract.

**Consumes:** `docs/api/openapi.yaml`, `.agent/knowledge/schema.sql`, `.agent/knowledge/stack.md`
**Produces:**

| Artifact | Path | Content |
|---|---|---|
| Backend | `src/` | models, repositories, services, routers, auth middleware, PDF export |
| Frontend | `src/` (or `frontend/`) | forms, list, details, login/register, privacy notice |
| Infrastructure | `infra/` | `backend.Dockerfile`, `frontend.Dockerfile`, `docker-compose.yml` |
| Tests | `tests/` | unit, integration, e2e |
| PR descriptions | `docs/execution/pull_requests/PR_*.md` | per task |
| Changelog | `docs/execution/CHANGELOG.md` | under `[Unreleased]` |

**Skills:** **[available]** `aisdlc-tdd` · **[missing]** `aisdlc-feature scaffold`,
`aisdlc-containerize`

**Streams run in parallel** (backend / frontend / infra), synchronized only through
`openapi.yaml` — no stream may change the contract.

**TaskFlow specifics:** three streams, one contract, the erasure endpoint implemented in the
backend stream and surfaced in the frontend stream.

---

## Step 7 — Refactoring & clean-up

**Purpose:** Second simplify pass on code that now exists.

**Consumes:** `src/`, `tests/`
**Produces:** cleaned `src/`, updated `tests/`, `.agent/artifacts/architecture.md` (updated)

**Skills:** **[available]** `aisdlc-simplify` · **[missing]** — (covered)

**TaskFlow specifics:** the first pass of the PDF service is more general than needed
(supports arbitrary templates) — the second pass narrows it to one report.

---

## Step 8 — Security & compliance scan

**Purpose:** Read-only audit before anything is deployed.

**Consumes:** `src/`, `tests/`, `docs/api/openapi.yaml`
**Produces:**

| Artifact | Path | Content |
|---|---|---|
| Security audit | `docs/qa/security-audit.md` | findings Critical/High/Medium/Low, evidence, rating |
| Test report | `.agent/artifacts/test-report.md` | pass/fail summary, coverage |

**Skills:** **[available]** `aisdlc-security-scan` · **[missing]** — (covered)

**TaskFlow specifics:** the mandatory checks are tenant isolation on every route, the erasure
endpoint actually deleting, no secrets in the image, and the retention job being idempotent.

**🚧 Gate 2** — 0 Critical and 0 High before deployment.

---

## Step 9 — Deployment & operations

**Purpose:** Ship it, prove it works, and be able to undo it.

**Consumes:** `docs/qa/security-audit.md`, `.agent/artifacts/test-report.md`
**Produces:**

| Artifact | Path | Content |
|---|---|---|
| Deploy report | `docs/execution/deploy-report.md` | environments, versions, smoke results, rollback |
| Deploy report (summary) | `.agent/artifacts/deploy-report.md` | short version for the review |
| Runbook | `docs/execution/` | detection, triage, mitigation, recovery, escalation |
| CI/CD | `.github/workflows/deploy.yml` | test + coverage gate + SAST + build + deploy |
| Infrastructure | `infra/` | Dockerfiles (non-root, pinned tags), compose, CI |

**Skills:** **[existing]** `aisdlc-deploy` · **[missing]** `aisdlc-ci-pipeline`

**TaskFlow specifics:** smoke tests hit the health endpoint, create a task, change its status
and download the PDF — the four steps that would have broken in production.

---

## Summary: skill gaps

| Missing skill | Step | Why it is missing |
|---|---|---|
| `aisdlc-prd-to-stories` | 1 | The framework has no skill that turns a PRD into stories, Gherkin features and acceptance criteria |
| `aisdlc-traceability` | 1, 5 | The matrix is ​​required by the SDLC but nothing builds or checks it |
| `aisdlc-openapi-first` | 2 | No skill drives contract-first work (spec → stubs → DTOs → routes) |
| `aisdlc-glossary-adr` | 2 | ADRs and the glossary are required artifacts; no skill writes them |
| `aisdlc-migration plan` | 2, 6 | Schema changes need expand/contract planning with a rollback path |
| `aisdlc-test-matrix` | 5 | No skill maps acceptance criteria to test levels and files |
| `aisdlc-feature-scaffold` | 6 | No skill creates a feature slice end to end consistently |
| `aisdlc-containerize` | 6, 9 | No skill produces a non-root multi-stage image plus compose |
| `aisdlc-ci-pipeline` | 9 | No skill generates the pipeline with the coverage gate and SAST |

All nine have a draft in `skills-to-create/`.