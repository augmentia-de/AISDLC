# Steckbrief — Schritt 2 (Konzept & Architektur)

| Feld | Wert |
|---|---|
| Stand | Schritt 2 abgeschlossen, Commit pending, GATE 1 ausstehend |
| Nächster Schritt | 3 (Simplify & Reduktion), Agent aisdlc-architecture (erst nach Freigabe GATE 1) |
| Vertrag | docs/api/openapi.yaml (Entwurf, Einfrierung nach Schritt 4) |
| Schema | .agent/knowledge/schema.sql (kanonisch) + docs/architecture/schema.sql (Spiegel) |
| Stack | .agent/knowledge/stack.md (Java 21 / Spring Boot 3) |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate |
| 3 | `.agent/workflows/03-simplify.md` | nächster Schritt (nach GATE 1) |
| 4 | `docs/requirements/PRD.md` | Anforderungen |
| 5 | `docs/api/openapi.yaml` | Vertrag |
| 6 | `docs/architecture/architecture.md` | kondensierte Review-Ansicht |
| 7 | `docs/architecture/contract-matrix.md` | Klassifikation der Endpunkte |

## Entscheidungen, die gelten
- ADR-001..ADR-009 (Tech-Stack, Persistenz, Status, Auth, ID, Pagination, CORS, API-first, Fehlerformat)
- CON-001..CON-005
- FR-001..FR-021, NFR-001..NFR-006

## Offene Punkte
- O-01 Profil-Konflikt mvp vs. enterprise (config.json sagt mvp) — bleibt offen
- O-13 Retention/DSGVO — dokumentiert, keine Auto-Loeschung im MVP
- (O-02, O-04, O-06, O-09, O-11, O-12, O-16 entschieden — siehe ADRs)

## Was du in diesem Schritt nicht tust
- Den Vertrag nach Schritt 4 aendern (bis dahin Entwurf; Aenderungen mit Impact-Analyse).
- `src/` oder `tests/` anfassen — Read-Only.
- Keine Implementierung vor GATE 1-Freigabe.