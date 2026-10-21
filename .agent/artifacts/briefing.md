# Steckbrief — Schritt 2 (Ruecksprung 3: V-26, ein Vertragswiderspruch)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 2 (Ruecksprung 2) abgeschlossen — Commit `0954ada`; davor Schritt 4 `c9c8038`, Schritt 3 `05bceca`, Schritt 2 (Ruecksprung 1) `9bb9bf4`, Schritt 1 `cc67445` |
| Dieser Schritt | 2 (Konzept & Architektur) erneut — **ein Vertragswiderspruch: V-26** |
| Danach | Schritt 4 (dritte Runde, Delta-Pruefung) — **danach wird der Vertrag eingefroren** |
| Vertrag | 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen; **Entwurf, noch nicht eingefroren** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` — byte-identisch; **hier nicht anfassen** |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `docs/api/openapi.yaml` | der Vertrag — nur die betroffenen Stellen |
| 6 | `.agent/artifacts/ruecksprung-bilanz.md` | Abschnitt 5: **V-26** im Wortlaut |

Bei Bedarf: `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md`, `docs/architecture/architecture.md`.

## Entscheidungen, die gelten

| ID | Entscheidung |
|---|---|
| D-01…D-15, ADR-001…ADR-018, F-01…F-04, E-01…E-11, E-13 | bestaetigt, nicht neu aufrollen |
| **V-26** | **`organisation_id` ist IMMER gefuellt** — das Feld benennt den **besitzenden Mandanten** der Ressource. Es ist kein Sonderfeld fuer `service_admin`. In der mandantenuebergreifenden Liste (nur `service_admin`) zeigt es, welcher Mandant zu welcher Ressource gehoert; im eigenen Mandanten ist es der eigene Mandant. Die fruehere Aussage „nur fuer `service_admin` gefuellt; fuer andere Rollen `null`“ ist **falsch** und wird korrigiert. |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 / E-13 | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich | Gate 2 |
| E-12 | Haertung der mandantenuebergreifenden Sicht (eigene Laufzeitrolle?) | Fachbereich + Plattform | Gate 2 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Kein Schema-Anfassen** — `.agent/knowledge/schema.sql` und `docs/architecture/schema.sql`
  bleiben unveraendert (`diff` muss leer bleiben).
- **Keine Aenderung an `docs/adr/`, `docs/architecture/`, `docs/requirements/`, `docs/Anforderung.md`.**
- **Keine neue Operation, kein neues Feld, keine neue Story.** 17 Operationen, 23 Stories,
  101 AKs, 24 Anforderungen bleiben unveraendert.
- **Den Vertrag noch nicht einfrieren** — der Freeze kommt nach Schritt 4.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel (unveraendert halten) | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
