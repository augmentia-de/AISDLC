# Steckbrief — Schritt 1 (Anforderungserhebung)

| Feld | Wert |
|---|---|
| Stand | Schritt 1 abgeschlossen, Commit pending |
| Nächster Schritt | 2 (Konzept & Architektur), Agent aisdlc-architecture |
| Vertrag | noch nicht erstellt (Schritt 2) |
| Schema | noch nicht erstellt (Schritt 2) |
| Stack | `.agent/knowledge/stack.md` (Template, in Schritt 2 zu füllen) |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt |
| 4 | `docs/requirements/PRD.md` | Anforderungen, Offene Punkte, Annahmen |
| 5 | `docs/requirements/traceability_matrix.json` | statische Traceability |
| 6 | `.agent/artifacts/risk-register.md` | Top 5 Risiken |

## Entscheidungen, die gelten

| ID | Entscheidung | Bindet |
|---|---|---|
| CON-004 | Profil enterprise (config.json SSoT) | vollständiger Zyklus, Gates |
| FR-001..FR-021 | Funktionaler Scope (Ticket-CRUD, Liste, Filter, Kommentare) | Architektur, Implementierung |
| A-01 | Single-Tenant | keine Mandantentrennung |
| A-02 | Keine Status-Transition-Validierung | beliebige Enum-Werte setzbar |
| A-08 | Postgres prod / SQLite dev | Persistenz-Schicht |
| A-09 | API-Feldnamen/Fehler in Englisch | Vertrag |

## Offene Punkte

| ID | Thema | Owner |
|---|---|---|
| O-01 | Profil-Konflikt mvp vs. enterprise | Product Owner |
| O-02 | Auth-Strategie (SEC-003) | Security/Architektur |
| O-04 | Status-Transition-Regeln | Product Owner |
| O-06 | Kommentar-Autor/Editierbarkeit | Product Owner |
| O-09 | ID-Generierung | Architektur |
| O-11 | UI vs. API-first | Product Owner |
| O-12 | Persistenz (SQLite vs. Postgres) | Architektur |
| O-13 | Retention/DSGVO | Datenschutz/Architektur |
| O-16 | CORS | Architektur |

## Was du in diesem Schritt **nicht** tust

- Den Vertrag (OpenAPI) ändern — er existiert noch nicht (Schritt 2 erstellt ihn).
- `src/` oder `tests/` anfassen — Read-Only für Requirements.
- Die Traceability-Matrix dynamisch füllen (Tests/Code gibt es noch nicht).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Mirror (Schritt 2+) | `SchemaMirrorIT` |