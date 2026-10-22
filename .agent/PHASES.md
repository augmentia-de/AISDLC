# Phasen, Instanzen und Agenten — das Mapping

Dieses Dokument ist die **Single Source of Truth** für die Zuordnung von Workflow-Schritten,
Orchestrator-Instanzen, Agenten und Skills. Vorher existierten drei divergierende
Phasen-Modelle im Projekt (9 Workflow-Schritte, 4 Agenten, 2 Prompt-Familien) — das ist
jetzt hier an einer Stelle zusammengeführt.

## Die drei Ebenen

| Ebene | Was sie beschreibt | Wo sie liegt | Anzahl |
|---|---|---|---|
| **Schritt** | Eine konkrete Arbeits-Aktion mit Vorbedingung und Ergebnis | `.agent/workflows/` | 9 |
| **Instanz** | Ein dauerhaft laufender Orchestrator-Prozess (REST-aktiviert) | `.agent/prompts/multi-instance/` | 5 (phase0–4) |
| **Agent** | Eine OpenCode-Rolle, die ein Nutzer direkt wählt | `.opencode/agents/` | 6 |

Ein Schritt ist **keine** Instanz und **kein** Agent. Instanzen fassen Schritte zusammen;
Agenten sind Rollen, die unabhängig vom Betriebsmodus existieren.

## Schritt → Instanz → Agent → Skill

| Schritt | Workflow-Datei | Instanz | Agent | Skill (Optional) | Gate |
|---|---|---|---|---|---|
| 0 Legacy-Analyse | — | `phase0` | — | — | optional |
| 1 Anforderungserhebung | `01-requirements.md` | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress`, `aisdlc-prd-to-stories`, `aisdlc-traceability-check` | — |
| 1a Anforderungs-Grill-Me | (in 01) | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress` | — |
| 2 Konzept & Architektur | `02-architecture.md` | `phase2` | `aisdlc-architecture` | `aisdlc-openapi-first`, `aisdlc-glossary-adr`, `aisdlc-migration-plan` | **GATE 1** danach |
| 3 Simplify & Reduktion | `03-simplify.md` | `phase2` | `aisdlc-architecture` | `aisdlc-simplify` | — |
| 4 Grill-Me-Stresstest | `04-grill-me-stress.md` | `phase2` | `aisdlc-architecture` | `aisdlc-grill-stress` | — |
| 5 Test-Driven Setup | `05-tdd.md` | `phase3` | `aisdlc-execution` | `aisdlc-tdd`, `aisdlc-test-matrix`, `aisdlc-traceability-check` | — |
| 6 KI-Code-Generation | `06-implementation.md` | `phase3` | `aisdlc-execution` | `aisdlc-feature-scaffold`, `aisdlc-containerize` | — |
| 7 Refactoring & Clean-up | `07-refactoring.md` | `phase3` | `aisdlc-execution` | `aisdlc-simplify` | — |
| 8 Sicherheit & Compliance | `08-security.md` | `phase4` | `aisdlc-qa` | `aisdlc-security-scan` | **GATE 2** danach |
| 9 Deployment & Betrieb | `09-deploy.md` | `phase4` | `aisdlc-qa` | `aisdlc-ci-pipeline`, `aisdlc-containerize`, `aisdlc-deploy` | — |

**Die Skills sind Verfahrens-Schritte, keine Rollen.** Die `aisdlc-*`-Skills liefern
das **Verfahren** (wann, welche Artefakte, welche Prüfungen); das **Handwerk** kommt
aus den Stack-Skills (`skills/`, vendoriert). Beide Schichten ergänzen sich, sie
ersetzen sich nicht. Sie werden vom zuständigen Agent
geladen, wenn der Schritt ansteht — dadurch entstehen keine zusätzlichen Agents und die
Kontextkosten bleiben bei 0, bis der Schritt wirklich gebraucht wird.

## Gate-Definition

| Gate | Nach Schritt | Bedingung | Wirkung |
|---|---|---|---|
| **GATE 1** | 2 (Architektur) | PRD vollständig, Architektur und Entscheidungen ok | Kein Anwendungscode vor menschlicher Freigabe. Prüft die **Richtung**, nicht den fertigen Vertrag. |
| **GATE 2** | 8 (Security) | QA 0 Critical, Security 0 Critical/High, **Traceability 0 Lücken** | Kein Deployment vor menschlicher Freigabe |

**Die Traceability-Bedingung** (`python3 .agent/tools/traceability_check.py` → 0 Lücken)
gehört zu GATE 2, weil sie erst nach Schritt 5/6 überhaupt prüfbar ist: eine
Anforderung mit Story braucht einen **belegten** Test. „Belegt" heißt: der Test
trägt die Akzeptanzkriterien-Kennung und existiert im Projekt. Eine gefüllte Matrix
ist kein Beleg — der Beleg ist die Datei.

Der Check läuft **vor** dem Gate. Im Referenzlauf blieb die Matrix 15 Stunden
unvollständig, weil erst nach Schritt 7 jemand hinsah.

**Einfrierpunkt des Vertrags: nach Schritt 4** (Ende des Stresstests), nicht nach
Gate 1. Der erste Volllauf fand im Stresstest 13 Vertragsdefekte — alle **nach** der
Einfrierung, und jeder davon hat Schema, Code und Tests invalidiert. Vor Schritt 4 ist
der Vertrag ein Entwurf; nach Schritt 4 ist er eingefroren. Eine Änderung danach
erfordert einen Rücksprung zu Schritt 2 — der Vertrag wird nie im Vorbeigehen geändert.

Gates stehen in `AGENTS.md` und werden von den Workflow-Dateien referenziert. Die
Multi-Instanz-Prompts enthalten dieselben Gates an denselben Schrittgrenzen.

**Agent-Wechsel am Gate:** Der Agent des abgeschlossenen Schritts bleibt aktiv, bis der
Mensch die Freigabe erteilt. Erst danach wechselt der Mensch auf den Agenten des
naechsten Schritts (Spalte "Agent" oben). Korrekturen nach einer Gate-Rueckfrage landen
so beim selben Agenten mit demselben Schreibrecht — und die Schreibrechte des
Folgeschritts sind erst aktiv, wenn dieser tatsaechlich beginnt.

## Commit pro Schritt

Jeder Schritt erzeugt **genau einen** Commit. Der Commit entsteht **vor** dem Gate —
das Gate prueft den fertigen Commit. Ohne Commit ist der Schritt nicht abgeschlossen.

| Schritt | Agent | Type | Subject | Pfade |
|---|---|---|---|---|
| 0 Legacy-Analyse | `phase0` | docs | `docs(sdlc-0): Legacy-Analyse` | `docs/00_legacy_analysis/`, `.agent/artifacts/` |
| 1 Anforderungserhebung | `aisdlc-requirements` | docs | `docs(sdlc-1): PRD, User Stories und Traceability-Matrix` | `docs/requirements/`, `.agent/artifacts/` |
| 2 Konzept & Architektur | `aisdlc-architecture` | docs | `docs(sdlc-2): API-Contract, Domain-Modell und ADRs` | `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` |
| 3 Simplify & Reduktion | `aisdlc-architecture` | refactor | `refactor(sdlc-3): Architektur vereinfacht` | `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` |
| 4 Grill-Me-Stresstest | `aisdlc-architecture` | docs | `docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen` | `docs/architecture/`, `.agent/artifacts/` |
| 5 Test-Driven Setup | `aisdlc-execution` | test | `test(sdlc-5): Teststrategie und fehlschlagende Tests` | `tests/`, `.agent/artifacts/` |
| 6 KI-Code-Generation | `aisdlc-execution` | feat | `feat(sdlc-6): Implementierung der Stories` | `src/`, `tests/`, `docs/execution/` |
| 7 Refactoring & Clean-up | `aisdlc-execution` | refactor | `refactor(sdlc-7): Refactoring und Clean-up` | `src/`, `tests/`, `.agent/artifacts/` |
| 8 Sicherheit & Compliance | `aisdlc-qa` | docs | `docs(sdlc-8): Security-Audit und QA-Bericht` | `tests/`, `docs/qa/`, `.agent/artifacts/` |
| 9 Deployment & Betrieb | `aisdlc-qa` | build | `build(sdlc-9): Deployment-Pipeline und Deploy-Report` | `docs/execution/`, `infra/`, `.github/`, `.agent/artifacts/` |

**Body-Format:** `Schritt <n> - <Schrittname> | Agent: <agent-id> | Artefakte: <pfade>`

**Befehl:**

```bash
mkdir -p <pfade>
git reset -q
git add -A -- <pfade>
git commit -m "<Subject>" -m "<Body>"
```

Das Scope wird ueber den **Index** gesetzt, nicht ueber eine Pfadspezifikation.
`git reset -q` leert den Index, `git add -A -- <pfade>` staged nur den Schritt,
`git commit` committet nur den Index.

Zwei Gruende, warum nicht `git commit -- <pfade>`:
- `git commit -- <pfade>` bricht ab, sobald ein Scope-Pfad keine getrackten Dateien
  hat ("pathspec did not match any files") — bei leeren Zielordnern waere der
  Schritt damit nicht committbar.
- Der Weg ueber `--pathspec-from-file` ist unter dem globalen `rtk`-Plugin kaputt:
  `rtk` rewritet `git diff` und haengt einen "Changes:"-Block an, der als
  Pfadspezifikation wiederum mit "empty string is not a valid pathspec" abbricht.

`git reset -q` unstaged fremde Arbeit, loescht sie aber nicht.

Regeln:
- Genau ein Commit pro Schritt. Wenn sich nichts geaendert hat, kein Commit erzeugen
  (`git commit` meldet dann "nothing to commit" — das ist kein Fehler).
- **Ausnahme Nachzug:** Korrigiert ein Schritt die Artefakte eines frueheren Schritts,
  entsteht **ein** zusaetzlicher Commit mit dem Subject des **korrigierten** Schritts
  plus `(Nachzug)`. Der Body nennt den Ausloeser-Schritt und den Grund. **Hoechstens
  ein** Nachzug pro Schritt — ein zweiter bedeutet: die Ursache liegt im Ablauf, nicht
  im Artefakt. Im EchoBox-Lauf gab es zwei, beide in Schritt 1.
- Nur die Pfade des Schritts committen — fremde Aenderungen werden nie mitcommittet.
- Kein `git push` — die Pipeline pusht nicht.
- Der Roh-Input des Schritts (z. B. die Anforderungs-Mail) gehoert in denselben Commit
  wie das daraus erzeugte Artefakt — die Traceability laeuft sonst ins Leere.

## Traceability: zwei Haelften, zwei Schritte

| Haelfte | Inhalt | Entsteht in | Artefakt |
|---|---|---|---|
| **statisch** | Anforderung → Story → Feature | Schritt 1 | `docs/requirements/traceability_matrix.json` |
| **dynamisch** | Anforderung → Test → Code | Schritt 5/6, jederzeit neu | `.agent/artifacts/traceability.md` |

Die Matrix trägt **nur** die statische Hälfte. Die dynamische wird **aus dem
Dateisystem ermittelt** — von `.agent/tools/traceability_check.py --fill`. Damit
kann kein Schritt ein Feld füllen, das er nicht belegen kann, und kein späterer
Schritt muss die Matrix nachtragen.

**Warum das so ist:** Die Matrix mit den Feldern `tests` und `code` war im
Referenzlauf über den gesamten Lauf ein Gerüst (0 von 89 Anforderungen mit
Test-Referenz) und wurde erst nach Schritt 7 gefüllt — drei Schritte zu spät, mit
zwei zusätzlichen Commits in Schritt 1. Der Grund: `tests` und `code` sind Werte,
die erst Schritt 5/6 kennt, die Datei aber dem Requirements-Agenten gehört. Ein
Cross-Step-Artefakt, das der Commit-Scope nicht auflösen kann.

**Akzeptanzkriterien-Kennungen (E):** Jedes Kriterium trägt `AC-<story>-<nr>` — im
Feature-Szenario als Kommentar (`Scenario: Rotation  # AC-003-1`), in der Story-JSON
als `id`, im Testfall als Methodenname oder Kommentar (`ac003_1_...`). Nur damit
ist die Zuordnung Test → Anforderung **mechanisch** und nicht geraten.

## Steckbrief zwischen den Schritten

`.agent/artifacts/briefing.md` ist der Steckbrief, den der Orchestrator am Ende jedes
Schritts schreibt und mit dem Schritt-Commit committet. Er ist der **Einstiegspunkt**
des nächsten Schritts: der Rollen-Agent liest zuerst das Briefing und danach **nur die
darin genannten Dateien**.

| Feld | Inhalt |
|---|---|
| Stand | letzter Schritt, sein Commit, nächster Schritt, dessen Agent |
| Was du lesen musst | die Dateien — und sonst nichts |
| Entscheidungen, die gelten | F-x / ADR-x, die diesen Schritt binden |
| Offene Punkte | O-xx mit Owner |
| Was du nicht tust | z. B. „der Vertrag ist eingefroren" |

Regeln:
- Das Briefing ist **nie** die Wahrheit — die Dateien sind es. Bei Widerspruch gewinnt
  die Datei, und das Briefing wird im nächsten Commit korrigiert.
- Das Briefing ist ein **Zeiger**: Status, Commit, offene Punkte. Keine Auszüge, keine
  Herleitungen.
- Es existiert, weil der Kontext im ersten Volllauf über vier Schritte um den Faktor
  80 wuchs (173k → 14,08M Input-Token): jeder Schritt las alles neu.

## Lauf-Protokoll

`.agent/artifacts/run-log.jsonl` — **eine JSON-Zeile je Ereignis**, append-only.
Geschrieben vom `aisdlc-orchestrator`; er ist der Einzige, der Schrittgrenzen und
Gates sieht.

```json
{"ts":"2026-10-22T01:41:03Z","event":"step_start","step":6,"agent":"aisdlc-execution"}
{"ts":"2026-10-22T02:18:41Z","event":"step_end","step":6,"commit":"825656a","duration_s":2258}
{"ts":"2026-10-22T02:28:11Z","event":"jump_back","from":7,"to":1,"reason":"Traceability-Matrix unvollstaendig"}
{"ts":"2026-10-22T02:29:02Z","event":"gate","gate":1,"result":"approved","by":"human"}
{"ts":"2026-10-22T02:30:00Z","event":"check","name":"traceability_check","result":"fail","gaps":178}
```

**Warum:** Der Ablauf lag bisher nur in der Git-Historie und in Prosa. Ohne Daten
sind Leerlauf, Ruecksprung-Zyklen und Schritt-Dauern nicht messbar — und jede
Verbesserung wurde aus dem Gefuehl entschieden statt aus der Historie. Im
EchoBox-Lauf: 23 Artefakte, davon **0** maschinenlesbar; `ruecksprung-bilanz.md`
mit 102 KB Prosa.

## Werkzeuge

| Ort | Art |
|---|---|
| `.agent/tools/` | Phasen-Werkzeuge (gehören dem Schritt, nicht der Anwendung) |
| `src/<modul>/tools/` | Code-Generatoren (gehören neben den Code, den sie erzeugen) |

| Werkzeug | Zweck |
|---|---|
| `.agent/tools/traceability_check.py` | prueft die Kette; `--fill` schreibt den Beleg |

Konvention und Bestand: `.agent/tools/README.md`.

## Betriebsmodi

| Modus | Prompt-Familie | Nutzung | Session-Steuerung |
|---|---|---|---|
| **Ein-Orchestrator** | `.agent/prompts/orchestrator/` | lokal, interaktiv, ein Agent wechselt die Schritte | keine |
| **Multi-Instanz** | `.agent/prompts/multi-instance/` | Server-Betrieb, 0–4 parallele Phasen | REST `POST /api/messages`, `session_create` |

Beide Familien beschreiben **dieselben Schritte** — sie unterscheiden sich nur in der
Aktivierung und im Session-Handling, nicht im Inhalt.

## Agenten

| Agent | ID | Rolle | Schreibrechte |
|---|---|---|---|
| Requirements Orchestrator | `aisdlc-requirements` | Phase 1 + 1a | `docs/requirements/` |
| Architecture Orchestrator | `aisdlc-architecture` | Phase 2–4 | `docs/architecture/`, `docs/api/`, `docs/adr/` |
| Execution Orchestrator | `aisdlc-execution` | Phase 5–7 | `src/`, `tests/`, `docs/execution/` |
| QA Orchestrator | `aisdlc-qa` | Phase 8–9 | `tests/`, `docs/qa/` |
| Java/Spring Backend | `stack/java/backend` | Implementierung | `src/`, `tests/`, `docs/execution/` |
| React/TypeScript Frontend | `stack/react/frontend` | Implementierung | `src/`, `tests/`, `docs/execution/` |
| *(Original)* | `build` | Generalist, alle Rechte | überall |

**Default-Agent:** `aisdlc-requirements` (in `.opencode/opencode.jsonc`).

## Stack-Schicht

Das Framework ist stackagnostisch. Alles Stack-spezifische liegt in
`opencode.jsonc` im Projekt-Root:

| Was | Wo | Wer pflegt es |
|---|---|---|
| `skills.paths` (Bibliotheken) | `opencode.jsonc` | das Projekt |
| `skill`-Allowlist des Stacks | `opencode.jsonc` | das Projekt |
| Stack-Agenten | `.opencode/agents/stack/<tech>/` | das Projekt |
| Stack-Wissen (Stack, Schema) | `.agent/knowledge/` | der Architecture Orchestrator im Projekt |

**Merge-Regel (OpenCode):** Root-Config zuerst, `.opencode/`-Config danach,
Agent-Frontmatter zuletzt — letzter Treffer gewinnt. Daraus folgt: der
Catch-all-Deny steht in der **Stack-Schicht**, die positiven Regeln des
Frameworks in `.opencode/opencode.jsonc`. Sonst hebt die Framework-Regel die
Stack-Erlaubnisse wieder auf.

## Stack-Ebene

Stack-Wissen kommt **nicht** aus eigenen Dateien, sondern per `skills.paths` direkt aus
den Bibliotheken — kein Kopieren, keine Drift, kein Kontext-Ballast:

```jsonc
"skills": { "paths": ["/…/ECC/skills", "/…/wshobson-agents/plugins/backend-development/skills", …] }
```

Weil ECC 311 und wshobson 185 Skills mitbringen, filtert die `skill`-Permission auf
Stack-Präfixe. Gemessene Kosten der geladenen Beschreibungen:

| Stack | Skills | ≈ Token |
|---|---|---|
| Java + Spring | 5 | 342 |
| React / TypeScript | 15 | 1.029 |
| alle Backends | 36 | 2.281 |

## Eigenes Skill schreiben (Konvention)

```markdown
---
name: aisdlc-<verfahren>              # Präfix aisdlc- → wird in opencode.jsonc erlaubt
description: <Was>. Nutze diesen Skill in Phase <n> (<Schritt>).
metadata:
  origin: adaptiert aus <quellen>     # Provenienz
---
```

Regeln:
- `name` = Verzeichnisname, Präfix `aisdlc-` für projekteigene Skills.
- `description` nennt **wann** der Skill greift — er ist das Auswahlkriterium des Modells.
- Stack-unabhängig formulieren: keine Sprach-Spezifika, wenn der Skill projektabhängig
  sein soll.
- `metadata.origin` nennt die Quellen aus den Bibliotheken (siehe `metadata.source`
  in den kopierten Skills).
- Nach dem Anlegen: `name` in der `skill`-Allowlist von `opencode.jsonc` ergänzen.
