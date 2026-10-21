# Steckbrief — Schritt 2 (Ruecksprung 4: V-27 und Abschluss-Sweep des Vertrags)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 2 (Ruecksprung 3) abgeschlossen — Commit `d6c4e85`; davor Ruecksprung 2 `0954ada`, Schritt 4 `c9c8038`, Schritt 3 `05bceca`, Schritt 2 (Ruecksprung 1) `9bb9bf4`, Schritt 1 `cc67445` |
| Dieser Schritt | 2 (Konzept & Architektur) erneut — **V-27 + vollstaendiger Abschluss-Sweep des Vertrags** |
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
| 5 | `docs/api/openapi.yaml` | **der Vertrag — im ganzen lesen, nicht nur die Fundstellen** |
| 6 | `.agent/artifacts/ruecksprung-bilanz.md` | Abschnitt 9.5 (**V-27**) und die bisherigen Bilanzen |
| 7 | `docs/adr/ADR-002-mandantentrennung-rls.md` §7, `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md` | die mandantenuebergreifende Sicht und die Rollenmatrix |
| 8 | `docs/requirements/PRD.md` | Rollen und Rechte (FR-004) |

## Entscheidungen, die gelten

| ID | Entscheidung |
|---|---|
| D-01…D-15, ADR-001…ADR-018, F-01…F-04, E-01…E-11, E-13 | bestaetigt, nicht neu aufrollen |
| D-02 | Fremde Ressourcen werden mit **HTTP 404** beantwortet (Existenz bleibt verborgen) |
| E-01 | Mandantenuebergreifende Sicht ueber **genau drei** `SECURITY DEFINER`-Eigentuemerfunktionen: Endpunktliste, Totenliste, Aufbewahrung. **Kein** mandantenuebergreifender Einzelabruf. |
| **V-26** | `organisation_id` ist **immer** gefuellt und benennt den **besitzenden Mandanten** |
| **V-27** | Die mandantenuebergreifende Sicht gilt **nur fuer die drei Listen-/Betriebsoperationen**. Der **Einzelabruf** einer fremden Ressource ergibt **auch fuer `service_admin` HTTP 404** (D-02). Ein vierter mandantenuebergreifender Leseweg wird **nicht** gebaut. Der Vertrag **sagt das ausdruecklich**. |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 / E-13 | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich | Gate 2 |
| E-12 | Haertung der mandantenuebergreifenden Sicht (eigene Laufzeitrolle?) | Fachbereich + Plattform | Gate 2 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Kein Schema-Anfassen.** `diff` der beiden Schemadateien muss leer bleiben.
- **Keine Aenderung an `docs/adr/`, `docs/architecture/`, `docs/requirements/`, `docs/Anforderung.md`.**
- **Keine neue Operation, kein neues Feld, keine neue Story, kein neues Akzeptanzkriterium.**
  Umfang bleibt 17 Operationen, 23 Stories, 101 AKs, 24 Anforderungen.
- **Keine neue Festlegung.** Wenn eine Stelle im Vertrag nur durch eine **neue** Entscheidung
  aufloesbar waere: **dokumentiere** sie, entscheide sie nicht.
- **Den Vertrag noch nicht einfrieren** — der Freeze kommt nach Schritt 4.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel (unveraendert halten) | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
