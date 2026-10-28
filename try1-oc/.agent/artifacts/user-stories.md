# User Stories — lesbare Fassung (Schritt 1)

## E1 — Ticket-Verwaltung (Kern)

### US-001 Ticket anlegen (must-have)
As a support agent, I want to create a ticket with title, description and priority.
- AC-001-1: Given no tickets, When POST with title+priority+description, Then 201 with full ticket
- AC-001-2: Given request, When POST without title, Then 400
- AC-001-3: Given request, When POST with title >200 chars, Then 400
- AC-001-4: Given request, When POST title only, Then 201 priority=medium
- AC-001-5: Given request, When POST title only, Then 201 description null/empty

### US-002 Ticket-Detail abrufen (must-have)
As a support agent, I want to retrieve a single ticket by id.
- AC-002-1: Given ticket exists, When GET by id, Then 200 with details
- AC-002-2: Given unknown id, When GET, Then 404

### US-003 Ticket aktualisieren (must-have)
As a support agent, I want to update title, description, priority.
- AC-003-1: Given ticket exists, When PATCH new title, Then 200 updated
- AC-003-2: Given ticket exists, When PATCH empty title, Then 400
- AC-003-3: Given unknown id, When PATCH, Then 404

### US-004 Status ändern (must-have)
As a support agent, I want to change a ticket's status.
- AC-004-1: Given open ticket, When PATCH status=in_progress, Then 200
- AC-004-2: Given ticket exists, When PATCH status='done', Then 400
- AC-004-3: Given unknown id, When PATCH status, Then 404

## E2 — Ticket-Übersicht & Filter

### US-005 Ticket-Liste abrufen (must-have)
As a support lead, I want to list tickets sorted desc with pagination.
- AC-005-1: Given 3 tickets, When GET, Then 200 list of 3 sorted desc
- AC-005-2: Given no tickets, When GET, Then 200 empty list
- AC-005-3: Given 60 tickets, When GET limit=10&offset=0, Then 200 10 tickets total=60

### US-006 Filter (must-have)
As a support lead, I want to filter by status and priority.
- AC-006-1: Given mixed statuses, When GET ?status=open, Then only open
- AC-006-2: Given mixed priorities, When GET ?priority=high, Then only high
- AC-006-3: Given mixed, When GET ?status=open&priority=high, Then intersection

## E3 — Kommentare

### US-007 Kommentar hinzufügen (must-have)
As a support agent, I want to add a comment to a ticket.
- AC-007-1: Given ticket exists, When POST comment, Then 201 with comment
- AC-007-2: Given ticket exists, When POST empty text, Then 400
- AC-007-3: Given unknown ticket, When POST comment, Then 404
- AC-007-4: Given ticket exists, When POST text >2000 chars, Then 400

### US-008 Kommentare auflisten (must-have)
As a support agent, I want to list comments chronologically.
- AC-008-1: Given ticket with 2 comments, When GET comments, Then 200 list asc
- AC-008-2: Given ticket no comments, When GET, Then 200 empty list
- AC-008-3: Given unknown ticket, When GET comments, Then 404

## E4 — API, Validierung & Betrieb

### US-009 Validierung & Fehlerbehandlung (must-have)
As a system, I want all inputs validated with consistent errors.
- AC-009-1: Given request, When POST priority 'urgent', Then 400 field error
- AC-009-2: Given request, When POST malformed JSON, Then 400
- AC-009-3: Given validation failure, When 4xx, Then body {error,message,details}

### US-010 Persistenz & Datenintegrität (must-have)
As a system, I want durable persistence with UTC timestamps.
- AC-010-1: Given ticket created, When server restarts + GET, Then same ticket
- AC-010-2: Given comment created, When server restarts + GET, Then same comment

### US-011 Health & Betrieb (should-have)
As a system admin, I want health endpoint and documented API.
- AC-011-1: Given server running, When GET /health, Then 200 {status:'ok'}