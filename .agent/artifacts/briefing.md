# Steckbrief — Schritt 2 (Ruecksprung 6: V-48 und V-49, reine Formulierung)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 4 (dritte Runde) abgeschlossen — Commit `68ffa4e`; davor Ruecksprung 5 `1a3d6a8`, Ruecksprung 4 `5a1c421`, Ruecksprung 3 `d6c4e85`, Ruecksprung 2 `0954ada`, Schritt 3 `05bceca`, Ruecksprung 1 `9bb9bf4`, Schritt 1 `cc67445` |
| Dieser Schritt | 2 (Konzept & Architektur) erneut — **zwei Formulierungsluecken: V-48, V-49** |
| Vertrag | war ab `68ffa4e` eingefroren; dieser Ruescksprung **oeffnet ihn fuer genau diese zwei Aenderungen** und **friert ihn danach wieder ein** (Einfrierpunkt: der Commit dieses Schritts) |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` — byte-identisch; **hier nicht anfassen** |
| Codebasis | `src/` existiert nicht — Neuaufbau |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `.agent/artifacts/stress-test.md` | **Abschnitt 2.1** (die Nachweistabelle mit V-48/V-49) |
| 6 | `docs/adr/ADR-002-mandantentrennung-rls.md` §7 | die Formulierung, die zu korrigieren ist (V-48) |
| 7 | `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md` §1 | die Rollenmatrix — **die normative Quelle** fuer V-49 |
| 8 | `docs/api/openapi.yaml` `info.description` § „Rollen“ | die Tabelle, die zu vervollstaendigen ist (V-49) |

## Entscheidungen, die gelten

D-01…D-15 · ADR-001…ADR-018 · F-01…F-04 · E-01…E-11 · E-13 · V-26 · V-27 · V-39 · V-40 · V-41 ·
V-42 · V-43 · V-44 — alle bestaetigt. Die Semantik ist an allen Vertrags- und ADR-Stellen gleich;
V-48 und V-49 betreffen **Formulierungen, nicht Semantik**.

| ID | Korrektur |
|---|---|
| **V-48** | **ADR-002 §7**: die schwächere Fassung („In den mandantenuebergreifenden Antworten ist `organisation_id` im Datensatz gefüllt“) auf **V-26** umstellen: `organisation_id` ist **immer** gefüllt und benennt den **besitzenden Mandanten** — in der mandantenuebergreifenden Liste zeigt es, welcher Mandant zu welcher Ressource gehört. |
| **V-49** | **`docs/api/openapi.yaml` `info.description` § „Rollen“**: die Rollentabelle **vollstaendig** gegen **ADR-015 §1** abgleichen — jede Rolle, jede Faehigkeit, insbesondere die mandantenuebergreifende Sicht des `service_admin` **nur** fuer Endpunktliste, Totenliste und Aufbewahrung sowie der Umstand, dass der **Einzelabruf** einer fremden Ressource auch fuer `service_admin` **404** ergibt (V-27). |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 / E-13 | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich | Gate 2 |
| E-12 | Haertung der mandantenuebergreifenden Sicht | Fachbereich + Plattform | Gate 2 |
| V-45 | AC-013-3 nennt 403 ohne Mandantbezug | Architektur | Schritt 5 |
| V-46 | optionale, aber als „immer vorhanden“ beschriebene Antwortfelder | Architektur | Schritt 5 |
| V-47 | vier Endpunkt-Operationen nennen den Fremdmandanten-404 nicht im Text | Architektur | Schritt 5 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Kein Schema-Anfassen.** `diff` der beiden Schemadateien muss leer bleiben.
- **Keine Aenderung an `docs/architecture/`, `docs/requirements/`, `docs/Anforderung.md`.**
- **Keine neue Operation, kein neues Feld, keine neue Story, kein neues Akzeptanzkriterium.**
  Umfang bleibt 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen.
- **Keine neue Festlegung.** Alles, was sich nicht aus den bestaetigten Entscheidungen ergibt:
  **dokumentieren**, nicht entscheiden. **Dieser Durchgang ist der letzte vor dem Freeze** —
  dokumentierte Befunde gehen als offene Punkte in Schritt 5/8 und blockieren den Freeze nicht.
- **Kein `git push`. Keine Ausgabe-Kommandos** (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Nach dem Commit
Der Vertrag gilt **ab dem Commit dieses Schritts** wieder als **eingefroren**. Halte das in
`.agent/artifacts/architecture.md` fest (Abschnitt „Vertrag“): Vertrag, Einfrierpunkt, und dass
V-48/V-49 die einzigen nach dem Freeze vorgenommenen Aenderungen waren.

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel (unveraendert halten) | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
