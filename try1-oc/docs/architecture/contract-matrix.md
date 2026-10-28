# Contract Matrix — mini-ticket

Klassifikation der Endpunkte (internal / external / public). Gilt fuer Aenderungen
nach dem Freeze (Schritt 4): Aenderungen an `external`/`public` Endpunkten erfordern
einen Rücksprung zu Schritt 2; `internal` Endpunkte dürfen mit begruendeter
Impact-Analyse geaendert werden.

| Pfad | Methode | operationId | Klassifikation | Auth (prod) | Begruendung |
|---|---|---|---|---|---|
| /health | GET | getHealth | public | nein | Betriebsprobe (Load-Balancer/Orchestrierung), keine Daten |
| /api/v1/health | GET | getHealthV1 | public | nein | Alias von /health |
| /api/v1/tickets | POST | createTicket | internal | Basic Auth | Kern-CRUD, internes Support-Team |
| /api/v1/tickets | GET | listTickets | internal | Basic Auth | Kern-Liste, internes Support-Team |
| /api/v1/tickets/{id} | GET | getTicket | internal | Basic Auth | Kern-Detail |
| /api/v1/tickets/{id} | PATCH | updateTicket | internal | Basic Auth | Kern-Update |
| /api/v1/tickets/{id}/comments | POST | createComment | internal | Basic Auth | Kern-Kommentar |
| /api/v1/tickets/{id}/comments | GET | listComments | internal | Basic Auth | Kern-Kommentarliste |

Hinweis: Alle fachlichen Endpunkte sind `internal` (CON-001, Single-Tenant A-01).
Keine `external`/`public` fachlichen Endpunkte im MVP. Auth in Dev/Test standardmaessig
deaktiviert (ADR-004).