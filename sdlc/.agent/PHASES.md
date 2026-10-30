# Phasen, Instanzen und Agenten — das Mapping

Dieses Dokument ist die **Single Source of Truth** für die Zuordnung von Workflow-Schritten,
Orchestrator-Instanzen, Agenten und Skills. Vorher existierten drei divergierende
Phasen-Modelle im Projekt (9 Workflow-Schritte, 4 Agenten, 2 Prompt-Familien) — das ist
jetzt hier an einer Stelle zusammengeführt.

## Die drei Ebenen

| Ebene | Was sie beschreibt | Wo | Anzahl |
|---|---|---|---|
| **Schritt** | Eine konkrete Arbeits-Aktion | `.agent/workflows/` | 9 |
| **Instanz** | Ein dauerhaft laufender Orchestrator-Prozess (REST-aktiviert) | `.agent/prompts/multi-instance/` | 5 (phase0–4) |
| **Agent** | Eine OpenCode-Rolle, die ein Nutzer direkt wählt | `.opencode/agents/` | 7 |

Ein Schritt ist **keine** Instanz und **kein** Agent.

## Schritt → Instanz → Agent → Skill

| Schritt | Workflow-Datei | Instanz | Agent | Skill (Optional) | Gate | MVP | Enterprise | Bemerkung |
|---|---|---|---|---|---|---|---|---|
| 0 Legacy-Analyse | — | `phase0` | — | — | optional | optional | optional | Nur bei Bedarf |
| 0a Migration-Assessment | `00-migration-assessment.md` | `phase0` | `aisdlc-requirements` | `module-migration`, `strangler-fig` | optional | optional | **yes** (bei mode=migration) | Nur bei mode=migration/brownfield |
| 1 Anforderungserhebung | `01-requirements.md` | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress`, `aisdlc-prd-to-stories`, `aisdlc-traceability-check` | — | **yes** | **yes** | Kern - PRD reicht meist im MVP |
| 1a Change-Brief (Delta) | `01a-change-brief.md` | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress` (opt.) | — | **yes** (hotfix/patch) | **yes** | Nur fuer hotfix/patch/small feature |
| 1a Anforderungs-Grill-Me | (in 01) | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress` | — | optional | **yes** | Im MVP gezielter Einsatz möglich |
| 2 Konzept & Architektur | `02-architecture.md` | `phase2` | `aisdlc-architecture` | `aisdlc-openapi-first`, `aisdlc-glossary-adr`, `aisdlc-migration-plan` | **GATE 1** danach | minimal | **yes** | MVP: nur minimaler Architektur-Überblick, keine vollständigen ADR-Sets falls nicht nötig |
| 2b Migration-Plan | `02b-migration-plan.md` | `phase2` | `aisdlc-architecture` | `aisdlc-migration-plan`, `strangler-fig`, `aisdlc-glossary-adr` | **GATE 1 (Migration)** | optional | **yes** (mode=migration) | Nur bei mode=migration/brownfield |
| 3 Simplify & Reduktion | `03-simplify.md` | `phase2` | `aisdlc-architecture` | `aisdlc-simplify` | — | no | **yes** | Oft überspringbar im MVP |
| 4 Grill-Me-Stresstest | `04-grill-me-stress.md` | `phase2` | `aisdlc-architecture` | `aisdlc-grill-stress` | — | optional | **yes** | Für externe/public APIs empfohlen, sonst überspringbar im MVP |
| 5 Test-Driven Setup | `05-tdd.md` | `phase3` | `aisdlc-execution` | `aisdlc-tdd`, `aisdlc-test-matrix`, `aisdlc-traceability-check` | — | optional | **yes** | MVP: fokussierte Tests, nicht zwingend vollständige Teststrategie |
| 5a Delta TDD | `05a-delta-tdd.md` | `phase3` | `aisdlc-execution` | `aisdlc-tdd` | — | **yes** (hotfix/patch) | **yes** | Nur betroffene Tests + Regressionen |
| 6 KI-Code-Generation | `06-implementation.md` | `phase3` | `aisdlc-execution` | `aisdlc-feature-scaffold`, `aisdlc-containerize` | — | **yes** | **yes** | Kern |
| 6a Delta-Implementierung | `06a-delta-impl.md` | `phase3` | `aisdlc-execution` | `aisdlc-feature-scaffold` (opt.), `aisdlc-simplify` | — | **yes** (hotfix/patch) | **yes** | Nur affected_paths |
| 7 Refactoring & Clean-up | `07-refactoring.md` | `phase3` | `aisdlc-execution` | `aisdlc-simplify` | — | optional | **yes** | Im MVP oft Teil von Schritt 6 |
| 8 Sicherheit & Compliance | `08-security.md` | `phase4` | `aisdlc-qa` | `aisdlc-security-scan` | **GATE 2** danach | minimal | **yes** | MVP: mindestens Secrets/Dependency-Scan, keine vollständige Compliance-Dokumentation |
| 8a Delta QA | `08a-delta-qa.md` | `phase4` | `aisdlc-qa` | `aisdlc-security-scan` | **GATE 2 (light)** | **yes** (hotfix/patch) | **yes** | Diff-basiert, betroffene Module |
| 9 Deployment & Betrieb | `09-deploy.md` | `phase4` | `aisdlc-qa` | `aisdlc-ci-pipeline`, `aisdlc-containerize`, `aisdlc-deploy` | — | **yes** | **yes** | Kern |

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
| **CUTOVER-GATE** | 2b/9 (Migration) | Routing ok, Migrationsskripte idempotent, Backout getestet, Monitoring vorhanden | Kein finaler Cutover vor menschlicher Freigabe |

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
| 0a Migration-Assessment | `aisdlc-requirements` | docs | `docs(sdlc-0): Migration-Assessment und Legacy-Inventar` | `docs/migration/`, `.agent/artifacts/` |
| 1 Anforderungserhebung | `aisdlc-requirements` | docs | `docs(sdlc-1): PRD, User Stories und Traceability-Matrix` | `docs/requirements/`, `.agent/artifacts/` |
| 1a Change-Brief (Delta) | `aisdlc-requirements` | docs | `docs(sdlc-fix): Change-Brief und betroffene Pfade` | `docs/execution/`, `.agent/artifacts/` |
| 2 Konzept & Architektur | `aisdlc-architecture` | docs | `docs(sdlc-2): API-Contract, Domain-Modell und ADRs` | `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` |
| 2b Migration-Plan | `aisdlc-architecture` | docs | `docs(sdlc-2b): Migration-Plan und Cutover-Strategie` | `docs/migration/`, `docs/adr/`, `.agent/artifacts/` |
| 3 Simplify & Reduktion | `aisdlc-architecture` | refactor | `refactor(sdlc-3): Architektur vereinfacht` | `docs/architecture/`, `docs/api/`, `docs/adr/`, `.agent/artifacts/` |
| 4 Grill-Me-Stresstest | `aisdlc-architecture` | docs | `docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen` | `docs/architecture/`, `.agent/artifacts/` |
| 5 Test-Driven Setup | `aisdlc-execution` | test | `test(sdlc-5): Teststrategie und fehlschlagende Tests` | `tests/`, `.agent/artifacts/` |
| 5a Delta TDD | `aisdlc-execution` | test | `test(sdlc-fix): Delta-Tests und Regressionsfälle` | `tests/`, `.agent/artifacts/` |
| 6 KI-Code-Generation | `aisdlc-execution` | feat | `feat(sdlc-6): Implementierung der Stories` | `src/`, `tests/`, `docs/execution/` |
| 6a Delta-Implementierung | `aisdlc-execution` | feat | `feat(sdlc-fix): Delta-Implementierung` | `src/`, `tests/`, `docs/execution/` |
| 7 Refactoring & Clean-up | `aisdlc-execution` | refactor | `refactor(sdlc-7): Refactoring und Clean-up` | `src/`, `tests/`, `.agent/artifacts/` |
| 8 Sicherheit & Compliance | `aisdlc-qa` | docs | `docs(sdlc-8): Security-Audit und QA-Bericht` | `tests/`, `docs/qa/`, `.agent/artifacts/` |
| 8a Delta QA | `aisdlc-qa` | docs | `docs(sdlc-fix): Delta-QA und Security-Bericht` | `tests/`, `docs/qa/`, `.agent/artifacts/` |
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

Die Matrix trägt **nur** die statische Hälfte. Die dynamische wird aus dem
Dateisystem ermittelt (`traceability_check.py --fill`). Im Referenzlauf war die
Matrix über den gesamten Lauf ein Gerüst (0/89 mit Test-Referenz) und wurde erst
nach Schritt 7 gefüllt — weil `tests`/`code` erst Schritt 5/6 kennt, die Datei
aber dem Requirements-Agenten gehört.

**AC-Kennungen (E):** Jedes Kriterium trägt `AC-<story>-<nr>` — im Feature-Szenario
als Kommentar, in der Story-JSON als `id`, im Testfall als Methodenname. Nur damit
ist die Zuordnung mechanisch und nicht geraten.

## Steckbrief zwischen den Schritten

`.agent/artifacts/briefing.json` schreibt der Orchestrator am Ende jedes Schritts und
committet ihn mit. Er ist der **Einstiegspunkt** des nächsten Schritts: der
Rollen-Agent liest zuerst das Briefing, dann nur die genannten Dateien. Das Briefing
ist **nie** die Wahrheit — die Dateien sind es. Keine Auszüge, keine Herleitungen.

## Lauf-Protokoll

`.agent/artifacts/run-log.jsonl` — **eine JSON-Zeile je Ereignis**, append-only.
Geschrieben vom `aisdlc-orchestrator`; er ist der Einzige, der Schrittgrenzen und
Gates sieht. Ereignisse: `step_start`, `step_end`, `jump_back`, `gate`, `check`,
`retry`. Format und Begruendung: `AGENTS.md` (Regel 14).

## Werkzeuge

Phasen-Werkzeuge: `.agent/tools/` (z. B. `traceability_check.py`).
Code-Generatoren: `src/<modul>/tools/`. Konvention: `.agent/tools/README.md`.

## Betriebsmodi

| Modus | Prompt-Familie | Nutzung |
|---|---|---|
| **Ein-Orchestrator** | `.agent/prompts/orchestrator/` | lokal, interaktiv, ein Agent wechselt die Schritte |
| **Multi-Instanz** | `.agent/prompts/multi-instance/` | Server-Betrieb, 0–4 parallele Phasen, REST-Steuerung |

Beide Familien beschreiben **dieselben Schritte** — sie unterscheiden sich nur in der
Aktivierung und im Session-Handling.

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
`opencode.jsonc` im Projekt-Root: `skills.paths`, `skill`-Allowlist und
Stack-Agenten unter `.opencode/agents/stack/<tech>/`.

**Merge-Regel (OpenCode):** Root-Config zuerst, `.opencode/`-Config danach,
Agent-Frontmatter zuletzt — letzter Treffer gewinnt. Daraus folgt: der
Catch-all-Deny steht in der **Stack-Schicht**, die positiven Regeln des
Frameworks in `.opencode/opencode.jsonc`.

## Stack-Ebene

Stack-Wissen kommt **per `skills.paths`** direkt aus den Bibliotheken — kein Kopieren,
keine Drift. Weil ECC 311 und wshobson 185 Skills mitbringen, filtert die
`skill`-Permission auf Stack-Präfixe.

## Eigenes Skill schreiben (Konvention)

```markdown
---
name: aisdlc-<verfahren>              # Präfix aisdlc- → wird in opencode.jsonc erlaubt
description: <Was>. Nutze diesen Skill in Phase <n> (<Schritt>).
metadata:
  origin: adaptiert aus <quellen>     # Provenienz
---
```

Regeln: `name` = Verzeichnisname; `description` nennt **wann** der Skill greift (das ist
das Auswahlkriterium des Modells); Stack-unabhängig formulieren; `metadata.origin` nennt
die Quellen. Nach dem Anlegen: `name` in der `skill`-Allowlist von `opencode.jsonc`
ergänzen.
