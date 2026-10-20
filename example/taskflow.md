# TaskFlow — the AISDLC reference application

## What it is

TaskFlow is a small task-management web application. It is deliberately modest in size and
deliberately complete in quality: every layer that a production application needs is
present — authentication, multi-user data isolation, a state machine, tests above 80 %
coverage, a CI pipeline, and a documented rollback path. The point is not the feature list;
it is that the **entire AISDLC pipeline runs on a real project** and leaves an auditable
trail of artifacts behind.

## Users

| Persona | Need |
|---|---|
| **Anna, freelance designer** | Capture a client task in seconds, find it again later, know what is due |
| **Team Leo** (design, dev, QA, product) | One shared task list, clear ownership, visible progress |
| **Stefan, student** | A simple checklist that works on the phone, without setup |

## Scope

### In scope (MVP)

- **Registration and login** — e-mail + password, session-based, HTTP-only secure cookies
- **Task list** — all tasks of the signed-in organisation, filterable by status, sortable by
  due date and priority
- **Task CRUD** — title (required), description, priority, due date, status
- **Status workflow** — `Open → In Progress → Done`, forward-only, no skipping
- **Organisation isolation** — every query is scoped by `organisation_id`; cross-tenant access
  returns 403, never data
- **Right to erasure** — `DELETE /tasks/{id}` removes the task and its history (GDPR)
- **Export** — task list as PDF
- **Privacy notice** — bilingual (EN/DE)

### Out of scope (explicitly)

- Assignment of tasks to users, push/email notifications, file attachments, dark mode,
  voice input

## Data model

```
Organisation 1──n User
Organisation 1──n Task
Task 1──n TaskHistory      # status transitions, for the 90-day retention audit
```

- `Task`: id, title, description, priority, status, due_date, organisation_id, created_by,
  created_at, updated_at
- `TaskHistory`: id, task_id, from_status, to_status, changed_at, changed_by
- Retention: 90 days, enforced by a scheduled job, plus right to erasure on request

## Non-functional requirements

| Area | Target |
|---|---|
| Performance | First view < 2 s (p95), 100 concurrent users |
| Availability | 99.9 % (Cloud Run, min 0 / max 10 instances) |
| Security | JWT (bcrypt), OWASP Top 10 clean, 0 Critical findings, no secrets in repo |
| Privacy | EU-hosted (europe-west3), 90-day retention, right to erasure, bilingual notice |
| Accessibility | WCAG 2.1 AA — keyboard navigation, screen-reader support, contrast |
| Observability | Health endpoint, structured logs with correlation ID, SLO 99.9 % availability |
| CI/CD | Test + coverage gate + SAST on every PR, container image built on main, staged deploy with smoke tests, documented rollback |

## Stack

| Layer | Choice |
|---|---|
| Backend | FastAPI (Python 3.11+) with SQLAlchemy async, Pydantic validation |
| Frontend | Next.js 15 (TypeScript, App Router), Tailwind CSS |
| Database | PostgreSQL 16 — schema in `.agent/knowledge/schema.sql` |
| Auth | Custom JWT (`organisation_id`-scoped), bcrypt + python-jose |
| Export | ReportLab (PDF) |
| Deployment | Docker (multi-stage, non-root) → Cloud Run, europe-west3 |
| CI/CD | GitHub Actions → Cloud Run |
| Local dev | Docker Compose (`infra/docker-compose.yml`) |

The stack is **not** baked into the framework. It is a decision recorded in
`.agent/knowledge/stack.md` and an ADR, and the reference app simply happens to be a
TypeScript-on-the-frontend, Python-on-the-backend project. The pipeline works the same for
Spring Boot plus React.

## Why this app is the right reference

- It is small enough to finish in one pipeline run, but not trivial: it has a state machine,
  tenant isolation, privacy obligations and a real deployment.
- Every artifact of the SDLC is meaningful — a PRD, BDD features, an OpenAPI contract, a
  schema, ADRs, a traceability matrix, a test report, a security audit and a deploy report.
- It exercises every failure path the framework claims to handle: requirement gaps
  (ambiguous status transitions), architecture blockers (deletion vs. retention policy),
  failing tests, and security findings (the erasure endpoint).
