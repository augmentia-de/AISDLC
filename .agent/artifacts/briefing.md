# Steckbrief — Schritt 2 (Rücksprung: Vertragsdefekte und Schema korrigieren)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Rücksprung aus Schritt 4 — Commit `bdb03d3` `docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen`; Schritt 1 nachgezogen in `cc67445` |
| Dieser Schritt | 2 (Konzept & Architektur) erneut — **22 Vertragsdefekte, 1 Architekturbruch, 2 Schema-Befunde** |
| Nächster Schritt danach | 3 (Simplify) und 4 (Stresstest) — **erst nach Schritt 4 wird der Vertrag eingefroren** |
| Vertrag | `docs/api/openapi.yaml` — 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen; **Entwurf, noch nicht eingefroren** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` — **beide byte-identisch ändern** |
| Stack | `.agent/knowledge/stack.md` — entschieden (ADR-001) |
| Codebasis | `src/` existiert nicht — Neuaufbau |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/02-architecture.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `.agent/artifacts/stress-test.md` | **der Stresstest** — Abschnitte 2 (Befunde N-01…N-12), 3 (S-01…S-23), 5 (V-01…V-22), 8 (E-01…E-09) |
| 6 | `docs/api/openapi.yaml` | der Vertrag, der korrigiert wird |
| 7 | `docs/architecture/` | architecture.md, domain_model.md, diagrams/, GLOSSARY.md, folder-structure.md, schema.sql |
| 8 | `docs/adr/ADR-*.md` | die Entscheidungen, die nachgezogen werden |
| 9 | `.agent/knowledge/schema.sql`, `.agent/knowledge/stack.md` | kanonisches Schema, Stack |

Bei Bedarf: `docs/requirements/PRD.md`, `docs/requirements/stories/US-0xx.json`.

## Entscheidungen, die gelten

| ID | Entscheidung | Wirkung in diesem Schritt |
|---|---|---|
| D-01…D-15 | Festlegungen der Anforderungserhebung | bestaetigt, nicht neu aufrollen |
| ADR-001…ADR-018 | Architekturentscheidungen | bestaetigt, nicht neu aufrollen |
| F-01 | O-01 Rate-Limit: 60 Versuche je Endpunkt und Minute, Strategie `delay` | V-03, E-02 |
| F-02 | O-02 Aufbewahrung: 30 Tage, getrennt fuer Ereignisse und Protokolle, ab letztem Versuch | V-02, E-04 |
| F-03 | O-03 Replay: nur Empfaenger-Admin | V-14, V-18 |
| F-04 | O-06 Wiederholung: 8 Versuche, 1 min, ×2, 60 min Deckel, ±20 % | V-16, E-03 |
| **E-01** | Mandantenuebergreifende Sicht des Service-Admins ueber **SECURITY DEFINER-Funktionen** (kein BYPASSRLS, keine Mandantenliste) | S-01, ADR-002, Schema, Vertrag |
| **E-02** | Wirksamer Rate-Limit-Grenzwert: **zwei Zustaende** — `limit_value IS NULL` = kein Override (60/min gilt), `0` = abgeschaltet. Die Spalte `enabled` **entfaellt** | S-04, N-08, ADR-010, Schema, Vertrag |
| **E-03** | ADR-008: Gesamtfenster **≈ 2 Stunden** (nicht 4), Fenster in den Vertrag | V-16, ADR-008, Vertrag |
| **E-04** | **Zwei** Aufbewahrungsfristen; das Loeschen eines Ereignisses **nimmt seine Laeufe und Versuche mit** — im Vertrag benennen | V-02, ADR-012, Vertrag |
| **E-08** | 3xx: **kein Wiederholen**, Status `client_error`; Redirects werden **nicht** gefolgt | V-12, ADR-009, Vertrag |
| **E-09** | Replay auf denselben aufgegebenen Lauf **einmal**, danach **409** | V-18, ADR-006/ADR-015, Schema (z. B. UNIQUE), Vertrag |

E-05 (Klassenname bleibt, Semantik im Kommentar), E-06 (PRD nachgezogen — erledigt in
`cc67445`), E-07 (Testmatrix aus den 101 AKs in Schritt 5) sind entschieden und brauchen
keine Arbeit in diesem Schritt.

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Den Vertrag noch nicht einfrieren** — der Freeze kommt nach Schritt 4.
- **Keine Änderung an `docs/requirements/`** (Ergebnisbereich Schritt 1, in `cc67445` erledigt).
- **Keine Änderung an `docs/Anforderung.md`.**
- **Keine Schemadatei ohne die andere.** Jede Änderung an `.agent/knowledge/schema.sql`
  braucht die byte-identische Änderung an `docs/architecture/schema.sql` **im selben Schritt**
  (ADR-016, Spiegel-Regel).
- **Keine Erweiterung des Umfangs.** 17 Operationen, 23 Stories, 101 AKs, 24 Anforderungen —
  du **behebst** Defekte, du fuegst nichts hinzu.

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
| Schema anwenden und belegen | PostgreSQL 16 (Testcontainers oder lokal) — RLS-Verhalten und neue Objekte nachweisen |
