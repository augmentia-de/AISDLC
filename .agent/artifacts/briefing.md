# Steckbrief — Schritt 4 (Grill-Me-Stresstest, dritte Runde — Delta-Pruefung, Einfrierpunkt)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 2 (Ruecksprung 5) abgeschlossen — Commit `1a3d6a8`; davor Ruecksprung 4 `5a1c421`, Ruecksprung 3 `d6c4e85`, Ruecksprung 2 `0954ada`, Schritt 4 (2. Runde) `c9c8038`, Schritt 3 `05bceca`, Ruecksprung 1 `9bb9bf4`, Schritt 1 `cc67445` |
| Dieser Schritt | 4 (Grill-Me-Stresstest) — **dritte Runde: Delta-Pruefung + Freeze-Empfehlung** |
| **Einfrierpunkt** | **Nach diesem Schritt wird `docs/api/openapi.yaml` eingefroren.** Ab dann gilt: jeder Vertragsdefekt ist ein Befund fuer einen **Ruescksprung zu Schritt 2** — keine Aenderung im Vorbeigehen. |
| Vertrag | 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` — byte-identisch |
| Codebasis | `src/` existiert nicht — Neuaufbau |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/04-grill-me-stress.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `.agent/artifacts/ruecksprung-bilanz.md` | **alle Korrekturen** — Abschnitte 1 (V-01…V-22), 5 (V-26), 9.5 (V-27), 10 (V-28…V-44), 11 (V-39…V-44, V-45…V-47) |
| 6 | `.agent/artifacts/stress-test.md` | der zweite Stresstest — was offen war |
| 7 | `docs/api/openapi.yaml` | **der Vertrag** |
| 8 | `.agent/knowledge/schema.sql` | das kanonische Schema |
| 9 | `docs/adr/ADR-*.md` | die nachgezogenen Entscheidungen (002, 006, 008, 009, 010, 012, 015, 016, 017) |
| 10 | `docs/architecture/` | architecture.md, domain_model.md, diagrams/, GLOSSARY.md, folder-structure.md |

## Entscheidungen, die gelten — alle bestaetigt

D-01…D-15 · ADR-001…ADR-018 · F-01…F-04 · E-01…E-11 · E-13 · V-26 (`organisation_id` immer
gefuellt) · V-27 (mandantenuebergreifende Sicht nur fuer die drei Listen-/Betriebsoperationen,
Einzelabruf 404) · V-39 (Mandantenpruefung vor Rollenpruefung) · V-40 (`from`/`to` auf
`created_at`) · V-41 (Readiness-Enum geschaerft, 503 mit `Problem`) · V-42 (`rate_limit` immer
vorhanden) · V-43 (`Event.deleted_at` entfernt) · V-44 (ADR-015 angeglichen)

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 / E-13 | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich | Gate 2 |
| E-12 | Haertung der mandantenuebergreifenden Sicht (eigene Laufzeitrolle?) | Fachbereich + Plattform | Gate 2 |
| V-45 | AC-013-3 nennt 403 ohne Mandantbezug — **Testmatrix-Schritt 5** | Architektur | Schritt 5 |
| V-46 | optionale, aber als „immer vorhanden“ beschriebene Antwortfelder | Architektur | Schritt 5 |
| V-47 | vier Endpunkt-Operationen nennen den Fremdmandanten-404 nicht im Text | Architektur | Schritt 5 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Den Vertrag nicht umbauen.** Er wird **nach** diesem Schritt eingefroren. Vertragsdefekte:
  **dokumentieren** (Pfad:Zeile, erwartet vs. tatsaechlich, Auswirkung, konkrete Korrektur,
  Prioritaet), **nicht** beheben. Ein Eigentuemer, eine Wahrheit (ADR-016).
- **Keine Aenderung an `docs/api/`, `docs/adr/`, `.agent/knowledge/`, `docs/requirements/`,
  `docs/Anforderung.md`.**
- **Keine neue Festlegung** der bestaetigten Entscheidungen.
- **Kein `git push`. Keine Ausgabe-Kommandos** (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Ergebnisse
- **`.agent/artifacts/stress-test.md`** — **ersetzen**. Gliederung:
  1. **Nachpruefung der Korrekturen**: V-01…V-22, V-23…V-27, V-28…V-44 — je Zeile
     **behoben / teilweise / nicht behoben** mit Fundstelle im **aktuellen** Stand.
     Eine Korrektur ohne Nachweis ist kein Fix.
  2. **Delta-Runde**: was ist durch die letzten drei Rueckspruenge **neu** entstanden?
     Schwerpunkt: die Rollen-/Mandantensemantik (V-26/V-27/V-39), die Zeitraumfilter (V-40),
     die Antwortformen (V-41/V-42/V-43), ADR-015/ADR-017, und die **3 neuen Befunde
     V-45/V-46/V-47** — sind sie echte Widersprueche gegen bestaetigte Entscheidungen oder nur
     Dokumentationsluecken fuer Schritt 5?
  3. **Vertragsdefekte** (falls welche) — eigener Abschnitt, wie beim ersten Lauf.
  4. **Freeze-Empfehlung** — ausdruecklich: kann `docs/api/openapi.yaml` **eingefroren** werden,
     und **ab welchem Commit**? Begruende mit Zahlen (Nachpruefungen, neue Befunde).
  5. Was **nicht** zu beanstanden war — der Bestand.
  6. Offene Entscheidungen fuer den Menschen (mit Empfehlung), soweit welche entstanden sind.
- Korrekturen an `docs/architecture/` (nur echte Fehler dort, mit Vermerk).

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p docs/architecture/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ .agent/artifacts/
git commit -m "docs(sdlc-4): Stresstest-Bericht, Nachpruefung und Einfrierempfehlung" \
  -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/"
```
Achtung: der Scope dieses Schritts ist **bewusst ohne `docs/api/` und `docs/adr/`**.

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Artefaktpfade, Pruefergebnis). In der zweiten optionalen
Zeile bitte nennen: **Nachpruefungen behoben/offen**, **neue Vertragsdefekte**, **Freeze-Empfehlung
mit Commit**.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit O-xx/E-xx/V-xx> | -
```
