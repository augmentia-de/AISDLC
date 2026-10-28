# ADR-002: Persistenz (Postgres prod / SQLite dev)

## Kontext
NFR-003 verlangt dauerhafte Persistenz ueber Neustarts. O-12/O-08 empfehlen Postgres
prod, SQLite dev/test (A-08). R-04: Persistenz-Wahl soll Deployment nicht verzoegern.

## Entscheidung
- Produktion: PostgreSQL (>= 14).
- Entwicklung/Test: SQLite (Datei) — ueber Profil/Umgebungsvariable waehlbar.
- ORM: Spring Data JPA/Hibernate mit portablen Typen (IDs/Timestamps als TEXT/UUID-as-String),
  damit ein Schema (schema.sql) in beiden Dialekten laeuft.
- Migrationen: Flyway, idempotent, Expand/Contract (siehe migration-plan.md).
- Queries: ausschliesslich parametrisierte Queries/Repository-Methoden (SEC-002).
- Verbindung ueber Umgebungsvariablen (SEC-001): DB_URL, DB_USER, DB_PASSWORD.

## Konsequenzen
- Positiv: Eine Schema-Definition fuer beide Dialekte; Testcontainers fuer Integrations-
  tests gegen Postgres moeglich.
- Negativ: SQLite-Abweichungen (Concurrency, Typsystem) koennen in Prod anders verhalten;
  Integrations-Tests sollten gegen Postgres laufen.
- Kein optimistisches Locking (Out-of-Scope).

## Alternativen
- Nur Postgres (auch Dev): verworfen, weil lokales Setup ohne DB-Server erschwert.
- Nur SQLite: verworfen, weil Prod-Anforderungen (Concurrency, Backups) fehlen.
- NoSQL: verworfen (relational, Filter/Sortierung).

## Status
Angenommen