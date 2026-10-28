# ADR-001: Tech-Stack

## Kontext
Das Projekt mini-ticket benoetigt einen Backend-Stack fuer eine kleine, robuste
REST-API (Ticket-CRUD, Filter, Kommentare) mit dauerhafter Persistenz. Kein UI im
MVP (API-first, A-07). Enterprise-Profil (CON-004), aber kleiner Scope (1-2 Tage).

## Entscheidung
- Runtime/Sprache: Java 21 (LTS).
- Framework: Spring Boot 3.x (Spring Web, Spring Validation, Spring Data JPA, Spring Security).
- Persistenz: PostgreSQL (prod), SQLite (dev/test); ORM Spring Data JPA (Hibernate).
- Migrationen: Flyway.
- Build: Maven (oder Gradle) — Maven als Default.
- Tests: JUnit 5, Testcontainers (Postgres), MockMvc, AssertJ.
- Serialisierung: Jackson (JSON), Feldnamen snake_case fuer Zeitstempel gem. Vertrag.
- ID-Generierung: UUID v4 (siehe ADR-005).
- Deployment: Docker-Image (Multi-Stage, non-root), Compose fuer Dev.

## Konsequenzen
- Positiv: Ausgereiftes Validierungs-/Security-/Persistenz-Oekosystem; Typsicherheit;
  passt zum vorhandenen Stack-Agenten `stack/java/backend`.
- Negativ: Hoeherer Footprint/Startzeit als Node; fuer 1-2-Tage-Scope mehr Boilerplate.
- Build-Zeiten laenger; JDK 21 erforderlich.

## Alternativen
- Node.js/TypeScript + Fastify/Prisma: schnellerer Start, weniger Boilerplate, aber
  weniger konsistent mit vorhandenem Stack-Agenten und Framework-Defaults; verworfen
  wegen Enterprise-Konsistenz und vorhandener Java-Expertise.
- Python/FastAPI: verworfen (keine vorhandene Stack-Schicht).
- Go: verworfen (kein vorhandener Stack-Agent).

## Status
Angenommen