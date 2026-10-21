# Steckbrief — Schritt 4 (Grill-Me-Stresstest)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. Keine Auszüge, keine Herleitungen. **Die Dateien sind die Wahrheit,
> nicht dieser Steckbrief.** Bei Widerspruch gewinnen die Dateien; der Steckbrief wird
> im nächsten Commit korrigiert.

| Feld | Wert |
|---|---|
| Stand | Schritt 3 abgeschlossen — Commit `296662d` `refactor(sdlc-3): Architektur vereinfacht` |
| Nächster Schritt | 4 (Grill-Me-Stresstest), Agent `aisdlc-architecture` |
| Vertrag | `docs/api/openapi.yaml` — Entwurf, 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen |
| **Einfrierpunkt** | **Nach diesem Schritt wird der Vertrag eingefroren.** Ab Schritt 5 gilt: ein Vertragsdefekt ist ein Befund für einen **Rücksprung zu Schritt 2**, keine Änderung im Vorbeigehen. |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql` (byte-identisch) |
| Stack | `.agent/knowledge/stack.md` — entschieden (ADR-001) |
| Codebasis | `src/` existiert nicht — Neuaufbau |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/04-grill-me-stress.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/02_orchestrator_architecture.md` | dein Systemprompt |
| 5 | `.agent/artifacts/architecture.md` | kompakter Architektur-Katalog (Stand Schritt 3) |
| 6 | `.agent/artifacts/simplify-report.md` | Reduktionsbericht Schritt 3 — was wurde entfernt und warum |
| 7 | `docs/api/openapi.yaml` | **der Vertrag** (`.agent/knowledge/openapi.yaml` ist nur ein Verweis) |
| 8 | `docs/architecture/architecture.md`, `docs/architecture/domain_model.md` | Volltext |
| 9 | `docs/adr/README.md` + `docs/adr/ADR-*.md` | Entscheidungen, die **nicht** neu diskutiert werden |
| 10 | `docs/architecture/GLOSSARY.md` | Fachbegriffe |

Bei Bedarf: `docs/requirements/PRD.md`, `docs/requirements/stories/US-0xx.json`,
`docs/requirements/features/US-0xx.feature` (Normbezug für die Spezifikations-Achse).

## Entscheidungen, die gelten

| ID | Entscheidung | Bindet |
|---|---|---|
| D-01…D-15 | Festlegungen der Anforderungserhebung, bei Gate 1 bestätigt | Vertrag, Datenmodell, Zustellmodell |
| ADR-001…ADR-018 | Architekturentscheidungen, bei Gate 1 bestätigt | alles |
| **F-01** | O-01 Rate-Limit: 60 Versuche je Endpunkt und Minute, Strategie `delay` | Betrieb, Worker |
| **F-02** | O-02 Aufbewahrung: 30 Tage, getrennt für Ereignisse und Protokolle, Fristbeginn = letzter Versuch | Betrieb, Purge-Job |
| **F-03** | O-03 Replay-Berechtigung: nur der Empfänger-Admin des Endpunkts | Rollenmatrix, Vertrag |
| **F-04** | O-06 Wiederholung: 8 Versuche, Basis 1 min, ×2, Deckel 60 min, ±20 % | Worker |

Diese sind **bestätigt** — der Stresstest darf sie **nicht** neu aufrollen. Er darf sie nur
als **Widerspruch** markieren, wenn Architektur und Entscheidung kollidieren.

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschlüsselung im Ruhezustand — fachliche Pflichtfrage (NFR-004 sagt ja, Abschnitt 13 fragt) | Fachbereich + Datenschutz | Gate 2 |
| O-05 | Vertrag über die Auftragsverarbeitung (AVV bei Fremddaten) | Fachbereich + Recht | Gate 2 |

## Was du in diesem Schritt **nicht** tust

- **Keinen Anwendungscode.** `src/` bleibt unberührt.
- **Den Vertrag nicht umbauen.** Der Vertrag wird **nach** diesem Schritt eingefroren. Findest
  du einen Vertragsdefekt: **dokumentiere** ihn exakt (Pfad, Stelle, erwartet vs. tatsächlich,
  Fundstelle, vorgeschlagene Korrektur, Auswirkung auf Schema/Code/Tests) im Stresstest-Bericht
  — **ändere `docs/api/openapi.yaml` nicht**. Der Orchestrator entscheidet danach über einen
  Rücksprung zu Schritt 2. Grund: der Vertrag hat einen einzigen Ort und einen einzigen
  Eigentümer (ADR-016); ein Fix im Vorbeigehen macht ihn zur zweiten Wahrheit.
- **Keine Änderung an `docs/requirements/`** und **keine an `docs/Anforderung.md`**.
- **Keine neue Festlegung** der bestätigten Entscheidungen.
- **No output commands.** Kein `echo`, `printf`, `pwd`, kein `cat` zur Orientierung.
- Kein `git push`.

## Ergebnisse
- **Stresstest-Bericht** als `.agent/artifacts/stress-test.md`:
  - Designbaum von der Wurzel nach unten, je Ast besucht — nichts stillschweigend angenommen.
  - **Zwei getrennte Achsen**: *Standards* (Konsistenz, Naming, Struktur, Konventionen des
    Projekts, Testbarkeit) und *Spezifikation* (`openapi.yaml`, `features/*.feature`, ADRs,
    PRD) — sie duerfen sich nicht gegenseitig maskieren.
  - Je Befund: **Datei:Zeile**, Problem, Auswirkung, Loesung, Empfehlungsstaerke
    (muss / sollte / optional).
  - **Developer-Interface ueber 9 Dimensionen** — Ergebnis je Dimension.
  - Offene Entscheidungen, die der Mensch treffen muss — mit Empfehlung.
  - **Vertragsdefekte** in einem eigenen, klar abgesetzten Abschnitt (siehe oben).
- Aktualisierte Architekturdateien unter `docs/architecture/`, falls der Stresstest etwas
  dort korrigiert (z. B. Diagramm gegen Text, Glossar gegen Domain-Modell).

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
Danach hoechstens zwei optionale Zeilen (Artefaktpfade, Pruefergebnis). Keine Herleitung, keine Auszuege.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit O-xx> | -
```
