# Architektur-Review-Ansicht — mini-ticket (Schritt 2)

## Stil
Modularer Monolith, API-first, REST/JSON, intern (CON-001..003). Kein Frontend im
MVP (ADR-008). Layered: Controller -> Service -> Repository -> DB.

## Komponenten
- API-Server (Spring Boot 3 / Java 21): HealthController, TicketController,
  CommentController, Services, Repositories, DTO-Mapper, GlobalExceptionHandler,
  SecurityConfig (Basic Auth optional).
- Datenbank: Postgres (prod) / SQLite (dev) — Tickets, Comments.

## Endpunkte (8 Operationen)
- GET /health (public), GET /api/v1/health (alias)
- POST/GET /api/v1/tickets
- GET/PATCH /api/v1/tickets/{id}
- POST/GET /api/v1/tickets/{id}/comments

## Datenmodell
Ticket (id, title, description, priority, status, created_at, updated_at);
Comment (id, ticket_id, text, author, created_at). Siehe domain_model.md.

## Querschnitt
- Validierung am Rand (Bean Validation), Geschaeftsregeln im Service, Invarianten im
  Modell/Schema.
- Fehlerformat {error, message, details} (ADR-009).
- Auth optional umgebungs-gesteuert (ADR-004).
- CORS nur konfigurierte Origins (ADR-007).
- Pagination limit 50/max 200, offset, total (ADR-006).

## ADRs
ADR-001 Tech-Stack, ADR-002 Persistenz, ADR-003 Status-Workflow, ADR-004 Auth,
ADR-005 ID-Generierung, ADR-006 Pagination/Sortierung, ADR-007 CORS, ADR-008 API-first,
ADR-009 Fehlerformat.

## Offene Punkte (O-xx) — Status
- O-01 Profil-Konflikt: bleibt offen (config.json sagt mvp, PRD/briefing enterprise);
  Architektur wurde fuer enterprise ausgelegt, Scope bleibt klein.
- O-02 Auth: entschieden (ADR-004, Basic Auth optional).
- O-04 Status-Transition: entschieden (ADR-003, keine Validierung).
- O-06 Kommentar-Autor: Default anonymous (A-04), immutable.
- O-09 ID-Generierung: entschieden (ADR-005, UUID v4).
- O-11 UI: API-first (ADR-008).
- O-12 Persistenz: entschieden (ADR-002).
- O-13 Retention/DSGVO: dokumentiert, keine Auto-Loeschung im MVP.
- O-16 CORS: entschieden (ADR-007).

## Risiken
R-01..R-05 siehe risk-register.md; Auth-Risiko teilweise gemindert (ADR-004).