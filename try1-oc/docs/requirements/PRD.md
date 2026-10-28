# PRD — Mini-Ticket-System (mini-ticket)

**Projekt:** mini-ticket
**Version:** 1.0 (Schritt 1)
**Datum:** 2026-10-28
**Status:** Draft — zur menschlichen Freigabe (GATE 1 nach Schritt 2)
**Profil:** enterprise (`.agent/config.json`) — vollständiger Zyklus
**Roh-Input:** `try1/docs/spec.md` (Kundenanforderung, Profil dort: mvp — siehe O-01)
**Owner (Requirements):** aisdlc-requirements

---

## 1. Vision & Ziel

Das Support-Team soll einfache Support-Anfragen als **Tickets** erfassen, bearbeiten,
kommentieren und schließen können. Fokus auf schnelle, korrekte Umsetzung einer
minimalen, aber robusten REST-API. Keine komplexe Workflow-Engine, keine externen
Integrationen.

**Ziel (messbar):** Ein Support-Mitarbeiter kann ein Ticket in unter 30 s anlegen
und den Status in einem Request ändern; die Liste rendert in unter 2 s (p95) bei
bis zu 1 000 Tickets.

## 2. Scope

### In-Scope (Must/Should)
- Ticket anlegen, anzeigen, aktualisieren, Status ändern
- Ticket-Liste mit Filter nach Status und Priorität, Sortierung, Pagination
- Einfache Kommentare pro Ticket
- REST-API (JSON), intern
- Persistenz (dauerhaft, Neustart-robust)
- Eingabevalidierung und konsistente Fehlerantworten
- Health-Check-Endpoint
- OpenAPI-Vertrag (Schritt 2)

### Out-of-Scope / Won't-Have (MVP)
- Keine Ticket-Löschung (DP-002, O-10)
- Keine Datei-Anhänge
- Keine Volltext-Suche
- Keine Benachrichtigungen/E-Mail
- Kein optimistisches Locking / Concurrency-Control
- Keine Historie/Audit-Log über Statuswechsel (nur Kommentare)
- Keine Internationalisierung (nur englische Feldnamen/API)
- Kein vollständiges UI (API-first; minimales UI ist Could, O-11)
- Keine Mandantentrennung (Single-Tenant, A-01)

## 3. Personas

| Persona | Beschreibung | Primäre Ziele |
|---|---|---|
| Support-Mitarbeiter | Erfasst und bearbeitet Tickets täglich | Schnelles Anlegen, Statuswechsel, Kommentare |
| Support-Lead | Übersicht über offene Tickets, Priorisierung | Filter, Sortierung, Übersicht |
| System-Admin | Betreibt das System | Health-Check, Persistenz, keine Secrets im Code |

## 4. Glossar (Kurz)

| Begriff | Bedeutung |
|---|---|
| Ticket | Eine Support-Anfrage mit Titel, Beschreibung, Priorität, Status |
| Status | Lebenszyklus-Zustand: open, in_progress, resolved, closed |
| Priorität | Dringlichkeit: low, medium, high |
| Kommentar | Freitext-Anmerkung zu einem Ticket mit Zeitstempel |

## 5. Funktionale Anforderungen

| ID | Anforderung | Priorität |
|---|---|---|
| FR-001 | Ein Ticket kann mit Titel, Beschreibung und Priorität angelegt werden. | must-have |
| FR-002 | Der Titel ist Pflichtfeld, Länge 1–200 Zeichen. | must-have |
| FR-003 | Die Beschreibung ist optional, maximal 4 000 Zeichen. | must-have |
| FR-004 | Die Priorität ist eines von low/medium/high; Default medium, falls nicht angegeben. | must-have |
| FR-005 | Ein Ticket kann per ID abgerufen werden (Detailansicht). | must-have |
| FR-006 | Titel, Beschreibung und Priorität eines Tickets können aktualisiert werden. | must-have |
| FR-007 | Der Status eines Tickets kann auf open/in_progress/resolved/closed gesetzt werden. | must-have |
| FR-008 | Alle Tickets können als Liste abgerufen werden. | must-have |
| FR-009 | Die Liste kann nach Status gefiltert werden. | must-have |
| FR-010 | Die Liste kann nach Priorität gefiltert werden. | must-have |
| FR-011 | Status- und Prioritätsfilter sind kombinierbar (AND). | should-have |
| FR-012 | Die Liste wird standardmäßig nach created_at absteigend sortiert. | should-have |
| FR-013 | Die Liste unterstützt Pagination (limit/offset) mit Gesamtanzahl. | should-have |
| FR-014 | Zu einem Ticket kann ein Kommentar (Text) hinzugefügt werden. | must-have |
| FR-015 | Der Kommentartext ist Pflichtfeld, maximal 2 000 Zeichen. | must-have |
| FR-016 | Die Kommentare eines Tickets können aufgelistet werden (chronologisch aufsteigend). | must-have |
| FR-017 | Jedes Ticket erhält eine eindeutige ID. | must-have |
| FR-018 | Ungültige Statuswerte werden abgelehnt (400). | must-have |
| FR-019 | Zugriff auf unbekannte Ticket-ID liefert 404. | must-have |
| FR-020 | Ungültige Eingaben liefern 400 mit strukturierter Fehlermeldung. | must-have |
| FR-021 | Ein Health-Check-Endpoint steht zur Verfügung. | should-have |

## 6. Nicht-funktionale Anforderungen

### 6.1 Performance / Verfügbarkeit

| ID | Anforderung | Messgröße |
|---|---|---|
| NFR-001 | Liste rendert in unter 2 s (p95) bei ≤ 1 000 Tickets. | p95 < 2 s |
| NFR-002 | Eingabevalidierung erfolgt serverseitig für alle Felder. | 100 % der Felder |
| NFR-003 | Daten überleben einen Server-Neustart (dauerhafte Persistenz). | 0 Datenverlust |
| NFR-004 | Zeitstempel (created_at, updated_at) in UTC, ISO-8601. | Format-Check |
| NFR-005 | Health-Endpoint antwortet in unter 1 s. | p95 < 1 s |
| NFR-006 | Keine personenbezogenen Daten in Logs. | Audit/Scan |

### 6.2 Sicherheit

| ID | Anforderung | Status |
|---|---|---|
| SEC-001 | Keine Secrets im Code; Konfiguration über Umgebungsvariablen. | Prozess (CI-Scan) |
| SEC-002 | Eingaben werden gegen Injection (SQLi/XSS) geschützt (parametrisierte Queries, Output-Encoding). | must-have |
| SEC-003 | Authentifizierungsstrategie (überspringen vs. Basic vs. JWT). | **OFFEN (O-02)** |
| SEC-004 | CORS nur für konfigurierte Origins (falls UI). | should-have |

### 6.3 Datenschutz

| ID | Anforderung | Status |
|---|---|---|
| DP-001 | Keine PII außer optionalem Kommentar-Autor; minimaler Datenbestand. | must-have |
| DP-002 | Recht auf Löschung / Retention-Policy. | Won't-Have MVP (O-13) |

### 6.4 Constraints

| ID | Constraint |
|---|---|
| CON-001 | REST-API, JSON, intern (keine externen Integrationen). |
| CON-002 | API-Prefix `/api/v1`. |
| CON-003 | Keine externen Integrationen erforderlich. |
| CON-004 | Profil enterprise: vollständiger SDLC-Zyklus, Gates. |
| CON-005 | Tech-Stack-Entscheidung in Schritt 2 (ADR-001). |

## 7. Epics & User Stories (Übersicht)

| Epic | Titel | Stories |
|---|---|---|
| E1 | Ticket-Verwaltung (Kern) | US-001, US-002, US-003, US-004 |
| E2 | Ticket-Übersicht & Filter | US-005, US-006 |
| E3 | Kommentare | US-007, US-008 |
| E4 | API, Validierung & Betrieb | US-009, US-010, US-011 |

Details: `docs/requirements/stories/*.json`, `docs/requirements/features/*.feature`.

## 8. Offene Punkte (O-xx)

| ID | Thema | Owner | Empfehlung |
|---|---|---|---|
| O-01 | Profil-Konflikt: Spec sagt mvp, config.json sagt enterprise. | Product Owner | Enterprise-Profil gilt (Framework-SSoT); Spec-Scope bleibt klein. |
| O-02 | Authentifizierungsstrategie (überspringen / Basic / JWT). | Security/Architektur | Basic Auth + Session für internes Tool; Entscheidung vor Schritt 2. |
| O-03 | Multi-Tenant vs. Single-Tenant. | Product Owner | Single-Tenant (A-01); keine Mandantentrennung im MVP. |
| O-04 | Status-Transition-Regeln (welche Übergänge erlaubt). | Product Owner | Keine Engine; beliebige Werte aus Enum (A-02). |
| O-05 | Priorität nachträglich änderbar? | Product Owner | Ja, über Update (A-03). |
| O-06 | Kommentar-Autor & Editierbarkeit. | Product Owner | Autor optional (aus Auth oder "anonymous"), immutable (A-04). |
| O-07 | Pagination-Defaults und Sortierung. | Architektur | limit=50 Default, offset=0, max 200 (A-05). |
| O-08 | Pflichtfelder und Defaults (Titel/Beschreibung/Priorität). | Product Owner | Siehe FR-002/003/004 (A-06). |
| O-09 | ID-Generierung (UUID vs. auto-increment). | Architektur | Schritt 2. |
| O-10 | Ticket-Löschung. | Product Owner | Won't-Have MVP (DP-002). |
| O-11 | UI vs. API-first. | Product Owner | API-first; UI Could (A-07). |
| O-12 | Persistenz (SQLite vs. Postgres). | Architektur | Postgres prod, SQLite dev/test (A-08). |
| O-13 | Retention / Löschung (DSGVO). | Datenschutz/Architektur | Keine Auto-Löschung im MVP; dokumentiert. |
| O-14 | API-Sprache (Feldnamen/Fehler). | Product Owner | Englisch (A-09). |
| O-15 | Semantik resolved vs. closed. | Product Owner | resolved = gelöst, closed = abgeschlossen (A-10). |
| O-16 | CORS-Konfiguration. | Architektur | Nur konfigurierte Origins (SEC-004). |

## 9. Annahmen (A-xx)

| ID | Annahme |
|---|---|
| A-01 | Single-Tenant: ein Support-Team, keine Mandantentrennung. |
| A-02 | Keine Status-Transition-Validierung; jeder Wert aus der Enum ist setzbar. |
| A-03 | Priorität ist nachträglich änderbar. |
| A-04 | Kommentare sind immutable; Autor optional (Default "anonymous" ohne Auth). |
| A-05 | Pagination: limit Default 50, max 200, offset Default 0. |
| A-06 | Titel required (1–200), Beschreibung optional (≤4000), Priorität Default medium. |
| A-07 | API-first; UI ist Could-Have. |
| A-08 | Postgres in Produktion, SQLite für Dev/Test. |
| A-09 | API-Feldnamen und Fehlermeldungen in Englisch. |
| A-10 | resolved = Lösung vorgeschlagen; closed = abgeschlossen/akzeptiert. |

## 10. Risiken

Siehe `.agent/artifacts/risk-register.md` (Top 5). Zusammenfassung:
- R-01: Auth unklar → API ohne Schutz im internen Netz (SEC-003, O-02).
- R-02: Scope-Creep durch UI-Wunsch (O-11).
- R-03: Status-Semantik uneinheitlich → Inkonsistenzen (O-04, O-15).
- R-04: Persistenz-Wahl verzögert Deployment (O-12).
- R-05: Validierungslücken → Datenqualität (FR-020, NFR-002).

## 11. Traceability

Statische Hälfte: `docs/requirements/traceability_matrix.json`.
Dynamische Hälfte (Tests/Code): wird in Schritt 5/6 belegt (`traceability_check.py --fill`).

## 12. Grill-Me Befunde (Zusammenfassung)

Die Anforderung (try1/docs/spec.md) ist bewusst knapp ("1-2 Tage", "mvp").
Für das Enterprise-Profil wurden folgende Lücken geschlossen bzw. als O-xx eskaliert:
1. Profil-Widerspruch (O-01).
2. Auth nicht entschieden (O-02) — blockiert SEC-003.
3. Status-Transition-Regeln fehlen (O-04) — Annahme A-02.
4. Keine Pflichtfeld-/Längen-Definitionen — in FR-002/003/004/015 konkretisiert.
5. Keine Fehler-/HTTP-Semantik — in FR-019/FR-020 + CON-002 ergänzt.
6. Keine Pagination/Sortierung — in FR-012/FR-013 ergänzt.
7. Won't-Have-Liste explizit gemacht (Abschnitt 2), um Scope-Creep zu verhindern.
8. Kommentar-Autor/Editierbarkeit unklar (O-06) — Annahme A-04.

---
*Ende PRD v1.0*