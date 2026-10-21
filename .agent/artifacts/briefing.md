# Steckbrief — Schritt 2 (Ruecksprung 2: letzte Vertrags- und Schemakorrektur)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 4 (zweite Runde) abgeschlossen — Commit `c9c8038`; davor Schritt 3 `05bceca`, Schritt 2 (Ruecksprung 1) `9bb9bf4`, Schritt 1 `cc67445` |
| Dieser Schritt | 2 (Konzept & Architektur) erneut — **3 Vertragssaetze + 3 Schema-Befunde (alle „muss“) + 6 „sollte“-Befunde** |
| Danach | Schritt 4 (dritte Runde, Delta-Pruefung) — **danach wird der Vertrag eingefroren** |
| Vertrag | 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen; **Entwurf, noch nicht eingefroren** |
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
| 5 | `.agent/artifacts/stress-test.md` | **der zweite Stresstest** — Abschnitte 1 (Nachpruefung), 3/4 (Befunde N-13…N-21), 6 (V-23…V-25), 7 (E-10…E-13) |
| 6 | `docs/api/openapi.yaml` | der Vertrag |
| 7 | `.agent/knowledge/schema.sql` | das kanonische Schema |
| 8 | `docs/adr/ADR-002-mandantentrennung-rls.md`, `ADR-006-zustellprozess-hintergrundprozess.md`, `ADR-012-aufbewahrung.md`, `ADR-015-fehlersemantik-tenant-rollen.md` | die nachzuziehenden Entscheidungen |

Bei Bedarf: `docs/architecture/architecture.md`, `docs/architecture/domain_model.md`,
`docs/architecture/diagrams/*.mmd`, `docs/requirements/PRD.md`.

## Entscheidungen, die gelten — alle bestaetigt, nicht neu aufrollen

| ID | Entscheidung |
|---|---|
| D-01…D-15 | Festlegungen der Anforderungserhebung |
| ADR-001…ADR-018 | Architekturentscheidungen |
| F-01 | Rate-Limit 60 Versuche/Endpunkt/Minute, Strategie `delay` |
| F-02 | Aufbewahrung 30 Tage, getrennt fuer Ereignisse und Protokolle, ab letztem Versuch; Kaskade |
| F-03 | Replay nur durch den Empfaenger-Admin |
| F-04 | 8 Versuche, 1 min Basis, ×2, 60 min Deckel, ±20 %; Gesamtfenster ≈ 2 Stunden |
| E-01 | Mandantenuebergreifende Sicht ueber drei SECURITY DEFINER-Funktionen (kein BYPASSRLS, kein Klartext, keine Secrets) |
| E-02 | Zwei Zustaende fuer den wirksamen Rate-Limit-Grenzwert; `enabled` entfaellt |
| E-03 | Gesamtfenster ≈ 2 Stunden |
| E-04 | Zwei Fristen + benannte Kaskade |
| E-08 | 3xx: kein Wiederholen, `client_error`, keine Redirects |
| E-09 | Replay einmal, danach 409; `UNIQUE (replay_of)` |
| **E-10** | Das **Zustellprotokoll** bleibt **mandantengefiltert** — die mandantenuebergreifende Sicht gilt **nur** fuer Endpunktliste, Totenliste und Aufbewahrung. Kein vierter Eigentuemer, keine Vertragsaenderung. |
| **E-11** | Die **Replay-Historie ueberlebt den Purge**: `replay_of` ist ein **historischer Verweis** ohne Fremdschlüssel |
| **E-13** | Die **Karenzzeit** der Aufbewahrung bekommt **keinen** Vorgabewert. Der Vertrag bleibt bei „kein Wert festgelegt“; das Schema-Verhalten (Purge im naechsten Durchlauf) wird **explizit** gemacht, nicht stillschweigend. |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht | Gate 2 |
| R-01 (= E-13) | Karenzzeit der Aufbewahrung — kein Wert, bewusst so | Fachbereich | Gate 2 |
| E-12 | Soll die mandantenuebergreifende Sicht **haerter** abgesichert werden (eigene Laufzeitrolle `echobox_ops`)? Empfehlung: jetzt benennen, Härtung als Risiko vormerken | Fachbereich + Plattform | Gate 2 |

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode.** `src/` bleibt unberuehrt.
- **Den Vertrag noch nicht einfrieren** — der Freeze kommt nach Schritt 4.
- **Keine Aenderung an `docs/requirements/`** und **keine an `docs/Anforderung.md`**.
- **Keine Erweiterung des Umfangs:** 17 Operationen, 23 Stories, 101 AKs, 24 Anforderungen.
  Du **behebst** Defekte, du fuegst nichts hinzu.
- **Keine neue Festlegung** der bestaetigten Entscheidungen oben.

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
| Schema anwenden und belegen | PostgreSQL 16 — RLS-Verhalten, Eigentuemerfunktionen, Constraints, Indizes nachweisen |
