# Steckbrief — Schritt 1 (Rücksprung: PRD nachziehen)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Rücksprung aus Schritt 4 — Commit `bdb03d3` `docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen` |
| Dieser Schritt | 1 (Anforderungserhebung) erneut — **ausschliesslich zur Nachpflege des PRD** (Befund N-09, Entscheidung E-06) |
| Nächster Schritt danach | 2 (Konzept & Architektur) — Rücksprung, behebt 22 Vertragsdefekte, Schema und ADRs |
| Vertrag | `docs/api/openapi.yaml` — Entwurf, wird im Rücksprung zu Schritt 2 korrigiert und **nach Schritt 4 eingefroren** |
| Codebasis | `src/` existiert nicht — Neuaufbau |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/01-requirements.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/01_orchestrator_requirements.md` | dein Systemprompt |
| 5 | `docs/requirements/PRD.md` | das PRD, das nachgezogen wird |
| 6 | `.agent/artifacts/stress-test.md` | Abschnitt zu **N-09** (Befund) und **E-06** (Entscheidung) |
| 7 | `docs/adr/ADR-008-retry-backoff-streuung.md`, `ADR-010-rate-limit.md`, `ADR-012-aufbewahrung.md`, `ADR-015-fehlersemantik-tenant-rollen.md` | die ADRs tragen die entschiedenen Werte — Quelle fuer die PRD-Aussagen |
| 8 | `docs/requirements/traceability_matrix.json` | Offene-Punkte-Eintraege O-01/O-02/O-03/O-06 |

Bei Bedarf: `docs/requirements/stories/US-0xx.json` (Story-Dateien bleiben unveraendert).

## Entscheidungen, die gelten

| ID | Entscheidung | Stand |
|---|---|---|
| **F-01** | O-01 Rate-Limit: **60 Versuche je Endpunkt und Minute**, Strategie `delay` (verschieben) | entschieden (Gate 1) |
| **F-02** | O-02 Aufbewahrung: **30 Tage, getrennt konfigurierbar** fuer Ereignisse und Zustellprotokolle, Fristbeginn = **letzter Versuch** | entschieden (Gate 1) |
| **F-03** | O-03 Replay-Berechtigung: **nur der Empfänger-Admin des Endpunkts** (Entwickler und Betrieb: 403) | entschieden (Gate 1) |
| **F-04** | O-06 Wiederholung: **8 Versuche, Basis 1 Minute, ×2, Deckel 60 Minuten, Streuung ±20 %** (Gesamtfenster ≈ 2 Stunden) | entschieden (Gate 1) |
| E-01 | Mandantenuebergreifende Sicht des Service-Admins ueber **SECURITY DEFINER-Funktionen** | entschieden (Gate 1-Nachfrage) |
| E-08 | 3xx im ausgehenden Vertrag: **kein Wiederholen**, Status `client_error`; Redirects werden **nicht** gefolgt | entschieden |
| E-09 | Replay auf denselben aufgegebenen Lauf: **einmal**, danach **409** | entschieden |
| D-01…D-15 | Festlegungen der Anforderungserhebung | bestaetigt |

## Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage (NFR-004 sagt ja, Abschnitt 13 der Anforderung fragt) | Fachbereich + Datenschutz | Gate 2 |
| O-05 | Vertrag ueber die Auftragsverarbeitung (AVV bei Fremddaten) | Fachbereich + Recht | Gate 2 |

## Dein Auftrag — eng begrenzt
Der Rucksprung dient **einer** Sache: das PRD an den entschiedenen Stand nachziehen, damit
die Spezifikation sich nicht selbst widerspricht (Befund N-09).
1. **PRD aktualisieren** (`docs/requirements/PRD.md`): die Stellen, die O-01/O-02/O-03/O-06 noch
   als offen fuehren — Zeile zur Rolle „Entwickler/Empfänger-Admin" (Berechtigung Replay),
   FR-033 (zulässige Rate), die Abschnitt-13-Liste der offenen Punkte, MoSCoW — auf den
   entschiedenen Stand bringen und mit **F-01…F-04** bzw. E-01/E-08/E-09 kennzeichnen.
2. **Offene-Punkte-Liste** im PRD: O-01/O-02/O-03/O-06 streichen bzw. als „entschieden am
   2026-10-21 (F-0x)" ausweisen; **O-04 und O-05 bleiben offen** (Gate 2).
3. **Traceability-Matrix** `docs/requirements/traceability_matrix.json`: die Eintraege zu
   O-01/O-02/O-03/O-06 entsprechend nachziehen (entschieden statt offen) — **nur** diese
   Felder, sonst nichts an der Matrix.
4. **Story-Dateien** `docs/requirements/stories/US-0xx.json`: **nicht** aendern. Die
   Akzeptanzkriterien bleiben unveraendert; die Stories nennen ihre offenen Punkte ueber
   `open_points` — dort, wo eine Story O-01/O-02/O-03/O-06 nennt, ersetze den Eintrag durch
   den jeweiligen F-Wert, **ohne** Akzeptanzkriterium oder ID zu aendern.
5. **Feature-Dateien** `docs/requirements/features/US-0xx.feature`: **nicht** aendern.
6. **Keine** neuen Anforderungen, keine **neuen** Akzeptanzkriterien, keine neuen Stories.
   Der Umfang bleibt 23 Stories / 101 AKs / 24 Anforderungen — `python3 .agent/tools/traceability_check.py`
   muss gruen bleiben.

## Was du **nicht** tust
- **Keine Anwendungscode-Dateien.** `src/` bleibt unberuehrt.
- **Keine Änderung an `docs/api/`, `docs/adr/`, `docs/architecture/`, `.agent/knowledge/`,
  `docs/Anforderung.md`** — das gehoert Schritt 2.
- **Keine Gherkin-Szenarien ergaenzen** (Befund N-10 / E-07 ist so entschieden: die Testmatrix
  in Schritt 5 entsteht direkt aus den 101 Akzeptanzkriterien, die Szenarien bleiben ein
  lesbarer Smoke-Layer). Das ist **kein** Lueckenbefund fuer dich.
- **Keinen Namenumbau** der Traceability-Matrix (E-05 ist so entschieden: Name bleibt,
  Semantik wird im Kommentar festgeschrieben).
- **Keine Festlegung von O-04/O-05.**
- **No output commands.** Kein `echo`, `printf`, `pwd`, kein `cat` zur Orientierung.
- Kein `git push`.

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD und Traceability auf den entschiedenen Stand nachgezogen" \
  -m "Schritt 1 - Anforderungserhebung (Ruecksprung) | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Artefaktpfade, Pruefergebnis). Keine Herleitung, keine Auszuege.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit O-xx> | -
```
