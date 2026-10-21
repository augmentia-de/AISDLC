# Steckbrief — Schritt 4 (Grill-Me-Stresstest)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. Keine Auszüge, keine Herleitungen. **Die Dateien sind die Wahrheit,
> nicht dieser Steckbrief.** Bei Widerspruch gewinnen die Dateien; der Steckbrief wird
> im nächsten Commit korrigiert.

| Feld | Wert |
|---|---|
| Stand | Schritt 3 abgeschlossen — Simplify & Reduktion, Agent `aisdlc-architecture` |
| Nächster Schritt | 4 (Grill-Me-Stresstest), Agent `aisdlc-architecture` |
| Gate | **GATE 1 ist freigegeben** (2026-10-21) — F-01…F-04 binden |
| Vertrag | `docs/api/openapi.yaml` — Entwurf, **Einfrierpunkt ist erst nach Schritt 4** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` (byte-identisch) |
| Stack | `.agent/knowledge/stack.md` — entschieden (ADR-001) |
| Codebasis | `src/` existiert nicht — Neuaufbau, keine Altlast |
| Reduktion | `.agent/artifacts/simplify-report.md` — was entfernt, zusammengefasst und berührt wurde |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/04-grill-me-stress.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `.agent/artifacts/architecture.md` | kompakter Architektur-Katalog (Stand Schritt 3) |
| 6 | `.agent/artifacts/simplify-report.md` | **Reduktionsbericht** — was entfernt wurde und was intakt geblieben ist |
| 7 | `.agent/artifacts/api-review.md` | Vertragsreview inkl. offener Fragen für den Stresstest |
| 8 | `docs/api/openapi.yaml` | der Vertrag (Entwurf) |
| 9 | `docs/architecture/architecture.md`, `docs/architecture/domain_model.md` | Volltext |
| 10 | `docs/adr/README.md` | ADR-Index mit den bestätigten Festlegungen |

Bei Bedarf: `docs/adr/ADR-*.md`, `docs/architecture/diagrams/*.mmd`, `docs/requirements/PRD.md`.

## Entscheidungen, die gelten

| ID | Entscheidung | Bindet |
|---|---|---|
| D-01…D-15 | Festlegungen der Anforderungserhebung — **bei Gate 1 bestätigt** | Vertrag, Datenmodell, Zustellmodell |
| ADR-001…ADR-013, ADR-015…ADR-018 | Architekturentscheidungen — **bei Gate 1 bestätigt** (17 Entscheidungen; ADR-014 ist in ADR-008 aufgegangen und bleibt als Verweisdatei) | alles |
| **F-01** | **O-01 Rate-Limit: 60 Versuche je Endpunkt und Minute, Strategie `delay` (verschieben)** — als Vorgabewert in der Konfiguration (ADR-010) | Betrieb, Worker, Konfiguration |
| **F-02** | **O-02 Aufbewahrung: 30 Tage, getrennt konfigurierbar für Ereignisse und Zustellprotokolle, Fristbeginn = letzter Versuch** — als Vorgabewert (ADR-012) | Betrieb, Purge-Job, Konfiguration |
| **F-03** | **O-03 Replay-Berechtigung: nur der Empfänger-Admin des Endpunkts** (Entwickler und Betrieb: 403) — bestätigt D-15 (ADR-015) | Rollenmatrix, Vertrag |
| **F-04** | **O-06 Wiederholung: 8 Versuche, Basis 1 Minute, ×2, Deckel 60 Minuten, Streuung ± 20 %** — als Vorgabewert (ADR-008) | Worker-Konfiguration |

F-01…F-04 sind **verbindlich**: als Default in der Architektur und im Vertrag
geführt, weiterhin **konfigurierbar** (ADR-008, ADR-010, ADR-012).

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschlüsselung im Ruhezustand — **fachliche Seite**: Ist sie Pflicht? (NFR-004 sagt ja; Abschnitt 13 fragt) | Fachbereich + Datenschutz | Gate 2 |
| O-05 | Vertrag über die Auftragsverarbeitung (AVV bei Fremddaten) | Fachbereich + Recht | Gate 2 |

Architekturseite von O-04 ist entschieden (ADR-003: AES-256-GCM; ADR-017: Suche nur über
Metadaten). O-07 ist entschieden (ADR-008: 5 s + 10 s = 15 s).

## Was du in diesem Schritt **nicht** tust

- **Keine Anwendungscode-Dateien.** `src/` bleibt unberührt; Code entsteht in Schritt 6.
- **Noch kein Vertrags-Freeze.** Der Freeze passiert **nach** Schritt 4. Der Vertrag ist in
  diesem Schritt noch frei — ändere ihn, wenn der Stresstest es verlangt, aber friere ihn
  nicht.
- **Keine Änderung an `docs/requirements/`** und **keine an `docs/Anforderung.md`**.
- **Keine Aufweichung der bestätigten Entscheidungen** D-01…D-15, ADR-001…ADR-018, F-01…F-04.
- **Schema-Spiegel:** jede Änderung an `.agent/knowledge/schema.sql` braucht die
  byte-identische Änderung an `docs/architecture/schema.sql` **im selben Schritt**.
- **Keine Streichung einer Anforderung.** 23 Stories, 101 AKs, 24 Anforderungen — der
  Vertrag muss alle drei weiterhin tragen (`x-acceptance-coverage`, `x-requirement-coverage`).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Spiegel prüfen | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
