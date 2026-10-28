# Risk Register — Top 5 (Schritt 1)

| # | Risiko | Schwere | Wahrsch. | Gegenmass (in Anforderung verankert) | Owner |
|---|---|---|---|---|---|
| R-01 | Auth unklar (O-02): API ohne Schutz im internen Netz, Missbrauch/PII-Exposition | High | Medium | SEC-003 (offen), O-02 vor Schritt 2 entscheiden; Basic Auth Default (A-01) | Security/Architektur |
| R-02 | Scope-Creep durch UI-Wunsch (O-11): 1-2-Tage-Rahmen gesprengt | Medium | High | Won't-Have-Liste (PRD §2), API-first (A-07), CON-003 | Product Owner |
| R-03 | Status-Semantik uneinheitlich (O-04/O-15): Inkonsistenzen in Reports | Medium | Medium | A-02 (keine Engine), A-10 (resolved vs. closed), FR-007 Enum | Product Owner |
| R-04 | Persistenz-Wahl verzögert Deployment (O-12): SQLite/Postgres-Wechsel spät | Medium | Medium | A-08 (Postgres prod/SQLite dev), NFR-003, CON-005 (Stack in Schritt 2) | Architektur |
| R-05 | Validierungslücken → Datenqualität (FR-020, NFR-002): schlechte Eingaben persisted | High | Medium | FR-002/003/004/015 Längen, US-009, SEC-002 (parametrisierte Queries) | Execution/QA |

## Weitere beobachtete Risiken (nicht Top 5)

- R-06: Profil-Widerspruch mvp vs. enterprise (O-01) — geklärt via config.json SSoT.
- R-07: Kommentar-Autor/PII (O-06, DP-001) — Default "anonymous".
- R-08: Pagination-Defaults (O-07) — A-05 (limit 50, max 200).