---
name: aisdlc-migration-plan
description: Phase 2/6: Schema-Migrationen planen (Expand/Contract, Rollback).
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/database-migrations + ECC/postgresql-table-design
---

## Wann

- **Phase 2**, bei der Schema-Entscheidung — die Strategie wird dort festgelegt.
- **Phase 6**, vor dem Schreiben einer Migrationsdatei.

## Das eiserne Gesetz

```text
EINE MIGRATION, DIE AUF EINER LIVE-DATENBANK FALSCH IST, IST EIN INCIDENT
```

Der Plan muss mit der **vorherigen** Anwendungsversion laufen.

## Expand / Contract

| Phase | Aktion | Mit altem Code sicher? |
|---|---|---|
| **Expand** | Spalte nullable / neue Tabelle / neuer Index | ja |
| **Migrate** | Backfill in Batches, idempotent | ja |
| **Switch** | Anwendung liest und schreibt die neue Form | erst nach vollstaendigem Backfill |
| **Contract** | alte Spalte weg / Constraint rein | nein — eigenes Release |

Regeln: keine Umbenennung in einem Schritt; keine `NOT NULL`-Spalte ohne Default
fuer Altzeilen; `CREATE INDEX CONCURRENTLY` auf grossen Tabellen; Backfills in
Batches mit Checkpoint, damit sie fortsetzbar sind.

## Rollback

Jede Migration beantwortet zwei Fragen: Was passiert, wenn sie **nicht rueckgaengig**
zu machen ist? Und was passiert, wenn die Migration **erfolgreich** war, die
Anwendung aber zurueckgerollt wird? Eine Migration ohne Antwort darauf ist nicht fertig.

## Output

- Migrationsplan als ADR: `docs/adr/ADR-<ID>-migration-<thema>.md`
- geordnete Liste der Migrationsdateien mit ihrer Phase (expand/migrate/switch/contract)
- Rollback-Abschnitt je Migration
- `.agent/artifacts/migration-plan.md`

## Regeln

- Jede Migration ist idempotent oder traegt einen ausdruecklichen "einmal"-Marker.
- Backfills sind batched, fortsetzbar und ratenbegrenzt.
- Eine destruktive Aenderung geht in ein eigenes Release, **nach** dem Release des
  Codes, der die alte Form nicht mehr braucht.
- Datenverlust braucht ausdrueckliche menschliche Freigabe und einen
  festgehaltenen Backup-Ort.
- Gehoert das Schema dem Architecture Orchestrator, meldet der Execution
  Orchestrator den Aenderungswunsch als **Befund** — er schreibt nicht selbst.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| Expand/Contract, Concurrent-Index, Batch-Backfill | `database-migrations` |
| Tabellen-Entwurf, Spalten, Constraints | `postgresql-table-design` |
| PostgreSQL-Muster, Indizes, JSONB | `postgres-patterns` |
