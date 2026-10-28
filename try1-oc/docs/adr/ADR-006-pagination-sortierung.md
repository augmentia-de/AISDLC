# ADR-006: Pagination und Sortierung

## Kontext
FR-012/FR-013: Liste mit Sortierung und Pagination. O-07: Defaults. NFR-001: p95 < 2s
bei <= 1000 Tickets.

## Entscheidung
- Sortierung: created_at DESC (neueste zuerst), fest (kein Sort-Parameter im MVP).
- Pagination: limit/offset als Query-Parameter.
  - limit: Default 50, Min 1, Max 200 (A-05).
  - offset: Default 0, Min 0.
- Antwort enthaelt total (Gesamtanzahl ohne Pagination), limit, offset, items.
- Ungueltige Werte -> 400.
- Indizes auf created_at DESC, status, priority (schema.sql).

## Konsequenzen
- Positiv: Einfach, deterministisch, ausreichend fuer <= 1000 Tickets.
- Negativ: Offset-basierte Pagination ineffizient bei grossen Offsets (nicht relevant
  fuer MVP-Scope).
- Kein Cursor-Paging im MVP.

## Alternativen
- Cursor/Keyset-Pagination: verworfen (Overkill fuer <= 1000 Tickets).
- Sortierparameter exponieren: verworfen (Scope, keine Anforderung).

## Status
Angenommen