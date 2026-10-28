# ADR-005: ID-Generierung (UUID v4)

## Kontext
FR-017: Jedes Ticket erhält eine eindeutige ID. O-09 offen (UUID vs. auto-increment).

## Entscheidung
- IDs sind UUID v4 (String-Form, 36 Zeichen mit Bindestrichen).
- Erzeugung serverseitig (Java UUID.randomUUID()).
- Speicherung als TEXT (portabel ueber Postgres/SQLite).
- Gilt fuer Ticket und Comment.

## Konsequenzen
- Positiv: Keine Enumeration, keine zentrale Sequenz, verteilt sicher, einfach.
- Negativ: Laengere IDs in URLs/Logs; leicht hoehere Speicherkosten.
- Sortierung nach ID nicht sinnvoll -> Sortierung nach created_at (FR-012).

## Alternativen
- Auto-increment (BIGINT): verworfen (Enumeration, Kopplung an DB-Sequence, Migration).
- ULID: verworfen (kein Bedarf fuer sortierbare IDs).

## Status
Angenommen