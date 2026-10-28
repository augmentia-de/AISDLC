# Domain-Modell — mini-ticket

## Entities

### Ticket
- id: UUID (PK)
- title: string (1–200, Pflicht)
- description: string | null (≤ 4000)
- priority: enum {low, medium, high} (Default medium)
- status: enum {open, in_progress, resolved, closed} (Default open)
- created_at: UTC ISO-8601 (readOnly)
- updated_at: UTC ISO-8601 (readOnly)

### Comment
- id: UUID (PK)
- ticket_id: UUID (FK -> Ticket, NOT NULL)
- text: string (1–2000, Pflicht)
- author: string (Default "anonymous")
- created_at: UTC ISO-8601 (readOnly)

## Beziehungen
- Ticket 1..* Comment (Komposition: Kommentare gehoeren zu einem Ticket; Loeschung des Tickets entfernt Kommentare — in MVP kein Ticket-Delete, daher latent).
- Comment *..1 Ticket.

## Invarianten
- I-01: title nicht leer, Länge 1–200.
- I-02: description null oder Länge ≤ 4000.
- I-03: priority ∈ {low, medium, high}.
- I-04: status ∈ {open, in_progress, resolved, closed}.
- I-05: created_at/updated_at werden serverseitig gesetzt (UTC, ISO-8601); Client kann sie nicht setzen.
- I-06: updated_at wird bei jeder Aenderung aktualisiert.
- I-07: text nicht leer, Länge 1–2000.
- I-08: author Default "anonymous", falls keine Auth/kein Autor angegeben.
- I-09: Statuswechsel ohne Transition-Validierung (A-02): jeder Enum-Wert ist setzbar.
- I-10: Kommentare sind immutable (kein Update/Delete im MVP).

## Lifecycle

### Ticket
1. Erzeugt (POST) mit status=open, priority Default medium.
2. Aktualisierbar (PATCH) — title/description/priority/status.
3. Statuswechsel beliebig (open -> in_progress -> resolved -> closed, aber auch Rueckwege erlaubt).
4. Kein Delete (Won't-Have MVP, DP-002).

### Comment
1. Erzeugt (POST) zu einem existierenden Ticket.
2. Nur lesbar (GET), keine Aenderung/Loeschung.

## Enum-Semantik
- open: neu/offen
- in_progress: in Bearbeitung
- resolved: Loesung vorgeschlagen (A-10)
- closed: abgeschlossen/akzeptiert (A-10)