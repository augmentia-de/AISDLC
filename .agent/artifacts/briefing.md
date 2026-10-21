# Steckbrief — Schritt 4 (Grill-Me-Stresstest)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 3 (Simplify & Reduktion) abgeschlossen — **dieser Commit**; Grundlage: Schritt 2 (Ruecksprung) in `9bb9bf4`, Schritt 1 nachgezogen in `cc67445`, Stresstest in `bdb03d3` |
| Nächster Schritt | 5 (TDD-Setup) — der Vertrag wird **nach diesem Schritt eingefroren** |
| Vertrag | `docs/api/openapi.yaml` — 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen; **Entwurf** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` — byte-identisch |
| Stack | `.agent/knowledge/stack.md` — entschieden (ADR-001) |
| Codebasis | `src/` existiert nicht — Neuaufbau |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/04-grill-me-stress.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `.agent/artifacts/architecture.md` | kompakter Architektur-Katalog (Stand nach Schritt 3) |
| 6 | `docs/api/openapi.yaml` | **der Vertrag** — er wird nach diesem Schritt eingefroren |
| 7 | `.agent/artifacts/simplify-report.md` | **Reduktionsbericht (Schritt 3)** — was verändert wurde und warum; R-01…R-05 |
| 8 | `.agent/artifacts/ruecksprung-bilanz.md` | Nachweis des Ruecksprungs — **Vorgangsprotokoll**, kein Wahrheitsort |
| 9 | `docs/architecture/architecture.md`, `docs/architecture/domain_model.md` | Volltext |
| 10 | `docs/adr/README.md` + `docs/adr/ADR-*.md` | Entscheidungen, die **nicht** neu diskutiert werden |

Bei Bedarf: `.agent/artifacts/stress-test.md` (der **erste** Stresstest — seine
22 Vertragsdefekte und 9 Entscheidungen sind behoben), `docs/architecture/diagrams/*.mmd`,
`docs/requirements/PRD.md`.

## Entscheidungen, die gelten — alle bestaetigt, nicht neu aufrollen

| ID | Entscheidung |
|---|---|
| D-01…D-15 | Festlegungen der Anforderungserhebung |
| ADR-001…ADR-018 | Architekturentscheidungen |
| F-01 | Rate-Limit 60 Versuche/Endpunkt/Minute, Strategie `delay` |
| F-02 | Aufbewahrung 30 Tage, getrennt fuer Ereignisse und Protokolle, ab letztem Versuch |
| F-03 | Replay nur durch den Empfaenger-Admin |
| F-04 | 8 Versuche, 1 min Basis, ×2, 60 min Deckel, ±20 % |
| E-01 | Mandantenuebergreifende Sicht ueber SECURITY DEFINER-Funktionen (kein BYPASSRLS) |
| E-02 | Zwei Zustaende fuer den wirksamen Rate-Limit-Grenzwert, Spalte `enabled` entfaellt |
| E-03 | Gesamtfenster der Wiederholung ≈ 2 Stunden |
| E-04 | Zwei Fristen; das Loeschen eines Ereignisses nimmt seine Laeufe und Versuche mit |
| E-08 | 3xx: kein Wiederholen, `client_error`; Redirects werden nicht gefolgt |
| E-09 | Replay einmal, danach 409; `UNIQUE (replay_of)` erzwingt es |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 | **Karenzzeit** der Aufbewahrung (Markieren → Purge) — **kein** beschlossener Wert. Der Vertrag behauptet **keinen** (Feld nullable, Beispiel `null`, ADR-012 §6). **F-02 bleibt unveraendert** (30 Tage je Datenart, Anker letzter Versuch, Kaskade) | Fachbereich | Gate 2 |

R-03, R-04 und R-05 sind in Schritt 3 **behoben** (rein mechanisch, ohne neue
Festlegung) — siehe `.agent/artifacts/simplify-report.md` §2.

## Was du in diesem Schritt **nicht** tust
- **Kein Anwendungscode.** `src/` bleibt unberuehrt.
- **Keine Änderung am Schema.** `.agent/knowledge/schema.sql` und `docs/architecture/schema.sql`
  liegen ausserhalb des Commit-Scopes dieses Schritts. Braucht eine Vereinfachung eine
  Schemaänderung, dokumentierst du sie als **Befund** — du aenderst sie nicht.
- **Keine Änderung an `docs/requirements/`**, **keine an `docs/Anforderung.md`**.
- **Keine Aufweichung** der bestaetigten Entscheidungen oben.
- **Keine Ausweitung von R-01**: die Karenzzeit bleibt **ohne Wert** — der
  Stresstest darf sie **nicht** festlegen, sondern nur ihre Vertretbarkeit pruefen.
- **Keine Erweiterung des Umfangs:** 17 Operationen, 23 Stories, 101 AKs, 24 Anforderungen.
- **Kein `git push`. Keine Ausgabe-Kommandos** (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel (unveraendert halten) | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
