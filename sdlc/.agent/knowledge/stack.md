# Tech Stack — <project name>

> **This file is a template.** It will be used in Phase 2 (Concept & Architecture)
> filled with the **decision** of this project. The decision will be made in
> `docs/adr/ADR-001-tech-stack.md` documented; this file is the entry point
> for agents who need the project's stack.
>
> **Don't** just leave the default list from `AGENTS.md` - that's exactly what it was
> in the reference run the reason why the architecture agent built FastAPI, although
> the customer requested a JVM library.

## Runtime and language

**Decided:** <e.g. B. Java 21 (LTS)>
**Why:** <the decisive reason — usually a requirement from the input>

**Excluded:**
- <Stack that was out of the question and why>

## Framework and libraries

**Decided:** <e.g. B. Spring Boot 3.x (Web, Security, Validation, Persistence)>
**Why:** <Reason>

## Database

**Decided:** <e.g. B. PostgreSQL 16>
**Why:** <Reason — Relationality, RLS, GDPR>

## Persistence

**Decided:** <e.g. B. Spring Data JPA/JDBC, never dynamically composed SQL>

## Auth

**Decided:** <e.g. B. Custom JWT, `organization_id`-Scope>
**Why:** <Reason>

## Frontend

**Decided:** <e.g. B. React 19 in Next.js 15 (App Router), TypeScript strict>
**Why:** <Reason>

## Tests

**Decided:** <e.g. B. JUnit 5, Testcontainers, Vitest, Playwright>
**Why:** <Reason - often: the client separation can only be done against a real one
Database, not against a mock>

## Deployment

**Decided:** <e.g. B. Docker + GitHub Actions → Cloud Run (europe-west3)>
**Why:** <Justification — reproducible builds, EU hosting>

## LLM (optional)

**Decided:** <Model(s) and Configuration>

## Data protection

- EU hosting (<Region>)
- Retention periods: <specification>
- Right to erase: <mechanism>
- No data leaves the EU

## Scheme

`schema.sql` in this directory is **canonical**. A copy in the commit scope of the
Architecture phase is byte-identical to the canonical path — forcing a mirror test
that. See the scheme decision in the associated ADR.

## Contract

`openapi.yaml` in this directory is a **reference** to `docs/api/openapi.yaml`,
no copy. The contract is maintained in exactly one place.