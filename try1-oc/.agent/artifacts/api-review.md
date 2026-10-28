# API-Review — mini-ticket (Schritt 2)

## Schema-Abgleich (Vertrag vs. DB-Schema)
- Alle Ticket-/Comment-Felder des Vertrags existieren in schema.sql mit gleichem Typ/Nullbarkeit.
- id als TEXT (UUID), Zeitstempel als TEXT (ISO-8601). Konsistent.
- Keine Abweichungen.

## Story -> Operation Zuordnung
| Story | Operation(s) |
|---|---|
| US-001 | createTicket |
| US-002 | getTicket |
| US-003 | updateTicket |
| US-004 | updateTicket (status) |
| US-005 | listTickets |
| US-006 | listTickets (Filter) |
| US-007 | createComment |
| US-008 | listComments |
| US-009 | alle (Validierung/Fehlerformat) |
| US-010 | Persistenz (alle) |
| US-011 | getHealth/getHealthV1 |

## Offene Fragen
- O-01 Profil-Konflikt bleibt offen (config.json mvp vs. PRD enterprise).
- Auth in Dev deaktiviert; Prod aktiviert (ADR-004).