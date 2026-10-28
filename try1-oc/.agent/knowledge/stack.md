# Tech Stack — mini-ticket

## Runtime und Sprache
**Entschieden:** Java 21 (LTS)
**Warum:** Enterprise-Konsistenz, Typsicherheit, vorhandener Stack-Agent `stack/java/backend`.
**Ausgeschlossen:** Node/Python/Go (keine vorhandene Stack-Schicht / geringerer Enterprise-Fit).

## Framework und Bibliotheken
**Entschieden:** Spring Boot 3.x (Spring Web, Validation, Data JPA, Security)
**Warum:** Ausgereiftes Validierungs-/Security-/Persistenz-Oekosystem. Siehe ADR-001.

## Datenbank
**Entschieden:** PostgreSQL (prod) >= 14; SQLite (dev/test).
**Warum:** A-08, NFR-003, R-04. Siehe ADR-002.

## Persistenz
**Entschieden:** Spring Data JPA (Hibernate), parametrisierte Queries; Flyway-Migrationen.
**Warum:** SEC-002, Portabilitaet ueber Dialekte.

## Auth
**Entschieden:** HTTP Basic Auth, umgebungs-gesteuert (Default off in Dev/Test). Siehe ADR-004.

## Frontend
**Entschieden:** Kein Frontend im MVP (API-first). Siehe ADR-008.

## Tests
**Entschieden:** JUnit 5, Spring Boot Test, MockMvc, Testcontainers (Postgres), AssertJ.
**Warum:** Integrations-Tests gegen echte DB.

## Deployment
**Entschieden:** Docker (Multi-Stage, non-root) + Compose (Dev). EU-Hosting vorgesehen.

## Datenschutz
- EU-Hosting vorgesehen (Region noch zu entscheiden, O-13).
- Aufbewahrung: keine Auto-Loeschung im MVP (DP-002 Won't-Have).
- Recht auf Loeschung: nicht implementiert im MVP (dokumentiert).
- Keine PII ausser optionalem Kommentar-Autor (DP-001); Default "anonymous".
- Keine personenbezogenen Daten in Logs (NFR-006).

## Schema
`schema.sql` in diesem Verzeichnis ist kanonisch. Kopie in `docs/architecture/schema.sql`
ist byte-identisch (SchemaMirrorIT). Siehe ADR-002.

## Vertrag
`openapi.yaml` in diesem Verzeichnis ist ein Verweis auf `docs/api/openapi.yaml`,
keine Kopie. Der Vertrag wird an genau einer Stelle gepflegt.