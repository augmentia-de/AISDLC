# Migrationsplan — mini-ticket

## Strategie: Expand/Contract (ADR-002, aisdlc-migration-plan)

Initiales Schema (V1): tickets, comments (schema.sql). Keine destruktiven Aenderungen.

### V1__init.sql
- Expand: CREATE TABLE tickets, comments + Indizes (IF NOT EXISTS).
- Idempotent: ja (IF NOT EXISTS).
- Backfill: nicht noetig (initiales Schema).
- Rollback: DROP TABLE comments; DROP TABLE tickets; (nur in Dev; Prod: manuell,
  da Datenverlust -> menschliche Freigabe).

### Zukuenftige Aenderungen (Beispiel)
- Neue nullable Spalte: Expand (nullable + Default) -> Migrate (Backfill batched) ->
  Switch (Code nutzt neue Spalte) -> Contract (NOT NULL/old column drop) in eigenem Release.

### Rollback-Regeln
- Vorwaerts-Migrationen sind idempotent oder tragen "einmal"-Marker.
- Backfills batched, fortsetzbar, ratenbegrenzt.
- Destruktive Aenderungen in eigenem Release nach Code-Release.
- Datenverlust braucht menschliche Freigabe + Backup-Ort.