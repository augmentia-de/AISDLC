# Steckbrief — Schritt 4 (Grill-Me-Stresstest, zweite Runde)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 3 abgeschlossen — Commit `05bceca`; davor Schritt 2 (Ruecksprung) `9bb9bf4`, Schritt 1 `cc67445`, erster Stresstest `bdb03d3` |
| Dieser Schritt | 4 (Grill-Me-Stresstest) — **zweite Runde**: Nachpruefung der Korrekturen + Suche nach neuen Defekten |
| Danach | **Einfrieren des Vertrags** `docs/api/openapi.yaml` |
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
| 5 | `.agent/artifacts/stress-test.md` | **der erste Stresstest** — was gefunden wurde, was seitdem passiert ist |
| 6 | `.agent/artifacts/ruecksprung-bilanz.md` | **die Korrekturen** — Defektbilanz V-01…V-22 und die Umsetzung von E-01…E-09, N-01, N-02, N-08 |
| 7 | `.agent/artifacts/simplify-report.md` | die Reduktion danach |
| 8 | `docs/api/openapi.yaml` | **der Vertrag** — nach der Korrektur |
| 9 | `docs/architecture/` | architecture.md, domain_model.md, diagrams/, GLOSSARY.md, folder-structure.md |
| 10 | `docs/adr/ADR-*.md` | die nachgezogenen Entscheidungen |
| 11 | `.agent/knowledge/schema.sql` | das kanonische Schema mit den neuen Objekten |

Bei Bedarf: `docs/requirements/PRD.md`, `docs/requirements/stories/US-0xx.json`.

## Entscheidungen, die gelten — alle bestaetigt, nicht neu aufrollen

| ID | Entscheidung |
|---|---|
| D-01…D-15 | Festlegungen der Anforderungserhebung |
| ADR-001…ADR-018 | Architekturentscheidungen |
| F-01 | Rate-Limit 60 Versuche/Endpunkt/Minute, Strategie `delay` |
| F-02 | Aufbewahrung 30 Tage, getrennt fuer Ereignisse und Protokolle, ab letztem Versuch; Kaskade: Loeschen eines Ereignisses nimmt seine Laeufe und Versuche mit |
| F-03 | Replay nur durch den Empfaenger-Admin |
| F-04 | 8 Versuche, 1 min Basis, ×2, 60 min Deckel, ±20 %; Gesamtfenster ≈ 2 Stunden |
| E-01 | Mandantenuebergreifende Sicht ueber SECURITY DEFINER-Funktionen (kein BYPASSRLS, kein Klartext, keine Secrets) |
| E-02 | Zwei Zustaende fuer den wirksamen Rate-Limit-Grenzwert; `enabled` entfaellt |
| E-03 | Gesamtfenster ≈ 2 Stunden |
| E-04 | Zwei Fristen + benannte Kaskade |
| E-08 | 3xx: kein Wiederholen, `client_error`, keine Redirects |
| E-09 | Replay einmal, danach 409; `UNIQUE (replay_of)` |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 | Karenzzeit der Aufbewahrung (Markieren → Purge) — **kein beschlossener Wert**, Vertrag behauptet keinen; Default: sofort | Fachbereich | Gate 2 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Den Vertrag nicht umbauen.** Er wird **nach** diesem Schritt eingefroren. Findest du einen
  Vertragsdefekt: **dokumentiere** ihn exakt (Pfad:Zeile, erwartet vs. tatsaechlich, Auswirkung auf
  Story/AK/Anforderung, konkrete Korrektur, Prioritaet) im Bericht — **aendere
  `docs/api/openapi.yaml` nicht**. Der Vertrag hat genau einen Eigentuemer (ADR-016, Schritt 2).
- **Keine Änderung an `docs/api/`, `docs/adr/`, `.agent/knowledge/`.**
- **Keine Änderung an `docs/requirements/`** und **keine an `docs/Anforderung.md`**.
- **Keine neue Festlegung** der bestaetigten Entscheidungen.
- **Kein `git push`. Keine Ausgabe-Kommandos** (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Ergebnisse
- **`.agent/artifacts/stress-test.md`** — **ersetzen** (der erste Bericht beschreibt einen
  ueberholten Stand). Struktur:
  1. **Nachpruefung**: jede der 22 V-Defekte, S-01, N-01, N-02, N-08 — **behoben / teilweise /
     nicht behoben**, mit Fundstelle im aktuellen Vertrag bzw. Schema. Eine Korrektur, die nicht
     nachweisbar ist, ist kein Fix.
  2. **Neue Runde**: Designbaum erneut, beide Achsen (Standards / Spezifikation) getrennt,
     9 Dimensionen des Developer-Interface — mit Schwerpunkt auf dem, was **neu** entstanden ist:
     die drei `SECURITY DEFINER`-Funktionen, `UNIQUE (replay_of)`, die Aufbewahrungs-Kaskade,
     das 3xx-Verhalten, die Replay-einmal-Regel, der entfernte `enabled`-Zustand.
  3. **Vertragsdefekte** (falls welche da sind) — im eigenen Abschnitt, wie beim ersten Lauf.
  4. **Freeze-Empfehlung**: ausdruecklich, ob der Vertrag **eingefroren** werden kann
     (keine offenen „muss"-Befunde) oder nicht — mit Begründung.
  5. Was **nicht** zu beanstanden war (Bestand).
- Korrekturen an `docs/architecture/` (nur echte Fehler dort).

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p docs/architecture/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ .agent/artifacts/
git commit -m "docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen" \
  -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/"
```
Achtung: der Scope dieses Schritts ist **bewusst ohne `docs/api/` und `docs/adr/`**.

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Artefaktpfade, Pruefergebnis). In der zweiten optionalen
Zeile bitte nennen: **Anzahl Nachpruefungen behoben / offen**, **Anzahl neuer Vertragsdefekte**,
**Freeze-Empfehlung**.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit O-xx/R-xx> | -
```
