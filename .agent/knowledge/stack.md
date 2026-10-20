# Tech Stack — AI SDLC Project

## Backend
**Default:** FastAPI (Python 3.11+) oder Spring Boot (Java 21+)
**Why**: Async-native, automatic OpenAPI doc generation, Pydantic/Spring für Datenvalidierung,
excellent für AI/ML-Integrationen.

**Alternativen:**
- Node.js/Express oder NestJS (TypeScript)
- Go (Gin/Fiber)
- .NET (ASP.NET Core)

## Frontend
**Default:** Next.js 15 (TypeScript, App Router) oder React 19 (Vite)
**Why**: Server-side rendering für SEO, App Router für moderne Layout-Patterns,
TypeScript strict mode für Typsicherheit, große Ökosystem.

## Database
**Default:** PostgreSQL 16 (local: raw via docker-compose, prod: Supabase/Cloud SQL)
**Why**: Relationale Datenbank mit JSONB-Support, gute Performance, GDPR-konform (EU-hosted).

## Auth
**Default:** Custom JWT (bcrypt + python-jose / Spring Security)
**Why**: Keine externe Abhängigkeit für lokale Entwicklung. organisation_id-scoping für Multi-Tenancy.

## Deployment
**Default:** Docker + GitHub Actions CI/CD → Cloud Run (europe-west3) oder Docker Compose
**Why**: Reproduzierbare Builds, automatisierte Deployments, EU-Hosting für GDPR.

## LLM (optional)
**Default:** Gemini 2.0 Flash oder OpenAI
**Why**: Gute Balance zwischen Kosten und Qualität. Konfigurierbar über Umgebungsvariablen.

## GDPR Compliance
- EU-hosted (europe-west3 Frankfurt)
- 90-day retention policy
- Right to erasure (DELETE endpoint)
- Bilingual privacy notice (DE/EN)
- No data leaves the EU

## Stack-Entscheidung

Die Stack-Entscheidung wird in Phase 2 (Architektur) getroffen und in
`docs/adr/ADR-001-tech-stack.md` dokumentiert. Die obige Liste ist der Default,
der bei Bedarf angepasst werden kann.