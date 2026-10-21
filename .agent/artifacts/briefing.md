# Steckbrief — Schritt 2 (Ruecksprung 5: letzter Korrekturdurchgang vor dem Freeze)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 2 (Ruecksprung 4) abgeschlossen — Commit `5a1c421`; davor Ruecksprung 3 `d6c4e85`, Ruecksprung 2 `0954ada`, Schritt 4 `c9c8038`, Schritt 3 `05bceca`, Ruecksprung 1 `9bb9bf4`, Schritt 1 `cc67445` |
| Dieser Schritt | 2 (Konzept & Architektur) erneut — **letzter Korrekturdurchgang: V-39…V-44** |
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
| 5 | `docs/api/openapi.yaml` | der Vertrag |
| 6 | `.agent/artifacts/ruecksprung-bilanz.md` | Abschnitt 10 (**V-39…V-44**) |
| 7 | `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md` | die zu korrigierende Rollenmatrix (§1, §2) |
| 8 | `docs/adr/ADR-017-protokollsuche-metadaten.md` | Sortierschluessel des Protokolls |

Bei Bedarf: `docs/architecture/architecture.md`, `docs/requirements/PRD.md`.

## Entscheidungen, die gelten

| ID | Entscheidung |
|---|---|
| D-01…D-15, ADR-001…ADR-018, F-01…F-04, E-01…E-11, E-13 | bestaetigt, nicht neu aufrollen |
| D-02 | Fremde Ressourcen ⇒ **404** (Existenz bleibt verborgen) |
| E-01 | Mandantenuebergreifende Sicht **nur** ueber die drei Eigentuemerfunktionen (Endpunktliste, Totenliste, Aufbewahrung) |
| V-26 | `organisation_id` **immer** gefuellt, benennt den besitzenden Mandanten |
| V-27 | Einzelabruf einer fremden Ressource ⇒ **auch fuer `service_admin` 404** |
| **V-39** | **Mandantenpruefung vor Rollenpruefung**: ein Zustelllauf eines **fremden** Mandanten ⇒ **404** (auch fuer `service_admin` und `developer`); ein Lauf im **eigenen** Mandant, aber ohne die Rolle (F-03: nicht `receiver_admin`) ⇒ **403**. Genau so steht es bereits in ADR-015 §3 („403 = dein Mandant, deine Rolle; 404 = nicht dein Mandant“) — der Vertrag folgt ihm. |
| **V-40** | Der Zeitraumfilter `from`/`to` des **Zustellprotokolls** bezieht sich auf **`created_at`** (der Sortierschluessel, ADR-017 §4). Die Totenliste bezieht ihren Zeitraum weiterhin auf `abandoned_at` — beide Aussagen bleiben nebeneinander stehen. |
| **V-41** | `ReadinessStatus` wird auf die **beobachtbaren** Werte geschaerft (`ready`, `ok`); der nicht bereite Zustand kommt als **503 mit dem Fehlerschema `Problem`** (ADR-015 §4). |
| **V-42** | `EndpointMetrics.rate_limit` ist **immer** Teil der Antwort (die Zaehlung laeuft unabhaengig vom Grenzwert, AC-018-4) → in `required` aufnehmen. |
| **V-43** | `Event.deleted_at` wird **entfernt** — ein markiertes Ereignis ist unsichtbar; die uebrigen Modelle fuehren den Marker nicht. Konsistenz der Antwortform. |
| **V-44** | **ADR-015** wird an V-26 und V-27 angeglichen (§1 Rollenmatrix: die mandantenuebergreifende Sicht gilt nur fuer die drei Listen-/Betriebsoperationen, der Einzelabruf ergibt 404; §2: `organisation_id` ist **immer** gefuellt). |

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
- **Keine Aenderung an `docs/architecture/`, `docs/requirements/`, `docs/Anforderung.md`.**
- **Keine neue Operation, kein neues Feld (ausser der Entfernung von `Event.deleted_at`),
  keine neue Story, kein neues Akzeptanzkriterium.**
- **Keine neue Festlegung.** Was sich nicht aus den oben bestaetigten Entscheidungen ergibt:
  **dokumentieren**, nicht entscheiden.
- **Den Vertrag noch nicht einfrieren** — der Freeze kommt nach Schritt 4.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel (unveraendert halten) | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
