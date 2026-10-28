# Glossar — mini-ticket

## Ticket

Eine Support-Anfrage mit Titel, Beschreibung, Prioritaet und Status.

- **Auspraegung:** Datensatz in `tickets` mit UUID, Titel, optionaler Beschreibung, Prioritaet, Status, Zeitstempeln.
- **Nicht:** ein Kommentar (Anmerkung zu einem Ticket) oder ein Benutzer.
- **Quelle:** PRD §4, FR-001.

## Status

Lebenszyklus-Zustand eines Tickets: open, in_progress, resolved, closed.

- **Auspraegung:** Enum-Wert, beliebig setzbar ohne Transition-Validierung (A-02).
- **Nicht:** ein Workflow mit erlaubten Uebergaengen.
- **Quelle:** PRD §4, FR-007, A-02, A-10.

## Prioritaet

Dringlichkeit eines Tickets: low, medium, high.

- **Auspraegung:** Enum-Wert, Default medium (FR-004), nachtraeglich aenderbar (A-03).
- **Nicht:** eine SLA oder ein Ranking-Algorithmus.
- **Quelle:** PRD §4, FR-004.

## Kommentar

Freitext-Anmerkung zu einem Ticket mit Zeitstempel.

- **Auspraegung:** Datensatz in `comments` mit UUID, Text, Autor (Default anonymous), Zeitstempel; immutable.
- **Nicht:** eine Statusaenderung oder Historie.
- **Quelle:** PRD §4, FR-014, A-04.

## Autor

Verfasser eines Kommentars.

- **Auspraegung:** optionaler String; Default "anonymous" ohne Auth (A-04, DP-001).
- **Nicht:** ein Benutzerkonto oder eine Identitaet mit Berechtigungen (kein User-Management im MVP).
- **Quelle:** O-06, A-04, DP-001.

## Tenant

Nicht vorhanden (Single-Tenant, A-01).

- **Auspraegung:** keine Mandantentrennung.
- **Nicht:** Multi-Tenant-Scoping.
- **Quelle:** A-01.

## Health-Check

Endpoint zur Betriebspruefung.

- **Auspraegung:** GET /health (und Alias /api/v1/health) -> {status: "ok"}.
- **Nicht:** ein fachlicher Endpoint.
- **Quelle:** FR-021, NFR-005.