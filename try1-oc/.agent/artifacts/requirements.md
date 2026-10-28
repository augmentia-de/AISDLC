# Requirements — kondensierter Katalog (Schritt 1)

**Projekt:** mini-ticket | **Profil:** enterprise | **Quelle:** try1/docs/spec.md

## Epics

| Epic | Titel | Stories |
|---|---|---|
| E1 | Ticket-Verwaltung (Kern) | US-001..US-004 |
| E2 | Ticket-Übersicht & Filter | US-005, US-006 |
| E3 | Kommentare | US-007, US-008 |
| E4 | API, Validierung & Betrieb | US-009, US-010, US-011 |

## Anforderungs-IDs (38)

- FR-001..FR-021 (21 funktional)
- NFR-001..NFR-006 (6 nicht-funktional)
- SEC-001..SEC-004 (4 sicherheit)
- DP-001, DP-002 (2 datenschutz)
- CON-001..CON-005 (5 constraints)

## Stories (11) — siehe docs/requirements/stories/*.json

US-001 Ticket anlegen (must) — FR-001,FR-002,FR-003,FR-004,FR-017 — AC-001-1..5
US-002 Ticket-Detail (must) — FR-005,FR-019 — AC-002-1,2
US-003 Ticket aktualisieren (must) — FR-006,FR-019,FR-020 — AC-003-1..3
US-004 Status ändern (must) — FR-007,FR-018,FR-019,FR-020 — AC-004-1..3
US-005 Ticket-Liste (must) — FR-008,FR-012,FR-013,NFR-001 — AC-005-1..3
US-006 Filter (must) — FR-009,FR-010,FR-011 — AC-006-1..3
US-007 Kommentar hinzufügen (must) — FR-014,FR-015,FR-019,FR-020,DP-001 — AC-007-1..4
US-008 Kommentare auflisten (must) — FR-016,FR-019 — AC-008-1..3
US-009 Validierung & Fehler (must) — FR-020,NFR-002,SEC-002 — AC-009-1..3
US-010 Persistenz (must) — NFR-003,NFR-004 — AC-010-1,2
US-011 Health & Betrieb (should) — FR-021,NFR-005,SEC-004,CON-001,CON-002,CON-003 — AC-011-1

## Won't-Have (MVP)

Ticket-Löschung, Anhänge, Volltext-Suche, Benachrichtigungen, Concurrency-Control,
Audit-Historie, i18n, vollständiges UI, Mandantentrennung, Retention/Löschung (DP-002).

## Offene Punkte

O-01..O-16 (siehe PRD §8). Kritisch: O-02 (Auth), O-12 (Persistenz).

## Grill-Me Befunde

1. Profil-Widerspruch (O-01).
2. Auth nicht entschieden (O-02).
3. Status-Transition-Regeln fehlen (O-04, A-02).
4. Keine Pflichtfeld-/Längen-Definitionen → FR-002/003/004/015.
5. Keine Fehler-/HTTP-Semantik → FR-019/FR-020.
6. Keine Pagination/Sortierung → FR-012/FR-013.
7. Won't-Have explizit (Scope-Creep-Schutz).
8. Kommentar-Autor/Editierbarkeit unklar (O-06, A-04).

---
Referenzen: docs/requirements/PRD.md, docs/requirements/stories/*.json, docs/requirements/features/*.feature, docs/requirements/traceability_matrix.json