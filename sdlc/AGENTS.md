# AGENTS.md — AI SDLC Orchestrator Framework

## Project Overview

**Zwei Schichten.** Framework (`.opencode/`, `.agent/`, `AGENTS.md`) ist stackagnostisch.
Stack-Schicht in `opencode.jsonc` (Root) wird pro Stack ersetzt; das Framework bleibt
unberührt. Details: `how-to.md` → "Stack-Schicht".

AI SDLC: KI-agentengestützter Softwareentwicklungszyklus. Der Orchestrator steuert den
Prozess, OpenCode führt aus. Schreibrechte pro Agent, Phasen-Zuordnung in
`.agent/PHASES.md` — SSoT, bei Widerspruch gilt sie.

## Behaviour Rules

1. Always read this file before starting any task. Read the briefing first (rule 12). **Read profile first:** load `.agent/config.json` (profile `mvp` or `enterprise`). The orchestrator is the gatekeeper for profile-based behavior.
2. Write all output artifacts to the designated directories (see Folder Ownership).
3. Never modify files outside your assigned folder (see Folder Ownership).
4. Never delete files without explicit human approval.
5. Write atomic commits: **exactly one commit per SDLC step** (see Commit Convention).
6. Flag any security risk with `[SECURITY]` prefix in your response.
7. Always re-read this file after context compaction or session reset.
8. Load the phase skill when the step calls for it (see Phase Skills) - do not improvise.
9. **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no
    result. Use `read`, `glob`, `grep`. In the first full run, 591 of 1.094 shell
    calls (54 %) were of this kind.
10. **Read the briefing first.** `.agent/artifacts/briefing.json` (Template: `.agent/templates/briefing.json`) names the files this
    step needs. Read only those, not the repository. The briefing is the entry point -
    never the truth (see Briefing).
11. **Log structured, never payload.** One event per line: correlation ID, event type,
    tenant, outcome, duration. Never payload data, secrets, tokens, PII. Convention
    in `.agent/artifacts/logging.md`; the security scan checks against it.
12. **Write the run log.** Every step start/end, gate, check and jump-back as one JSON
    line in `.agent/artifacts/run-log.jsonl`. Only the orchestrator writes it.
12a. **Skill-Invocations loggen (Debug).** Jeder Aufruf eines `aisdlc-*`-Skills wird als
    `skill_start`/`skill_end`-Event in `run-log.jsonl` protokolliert (Helper:
    `.agent/tools/skill_log.py <skill> <agent> <step> [start|end]`). So ist nachvollziehbar,
    welche Skills wirklich liefen.
13. **Big artefacts start with a table.** Artefact > ~20 KB: table on top (finding,
    severity, status, where fixed). Prose stays below. Table is entry point, prose
    is proof.

## HARD GATES - Agent Must Stop and Await Human Approval

- **After Step 2** (Architecture complete) - before any application code is written.
  The agent must produce all artifacts, present a summary, and wait for human approval.
  This gate reviews the **direction**: architecture, decisions, ADR set. The contract
  is still a draft here.
- **After Step 8** (Security & Compliance complete) - before Deploy runs.
  The agent must present QA and Security findings and wait for human approval.
- **CUTOVER-GATE** (nur bei mode=migration/brownfield, vor finalem Umschalten).
  Routing, idempotente Migrationsskripte, getesteter Backout und Monitoring müssen
  nachweisbar sein. Kein finaler Cutover ohne menschliche Freigabe.

Without approval, the pipeline does not advance. No exceptions.

**Profile-aware Gates:** Respect `.agent/config.json`. In `profile: mvp`, gates can be presented in shortened form, but **Folder-Ownership** and **atomic commits** remain mandatory. In `profile: enterprise`, full governance applies.

**Traceability is a gate condition, not a footnote.** The check
`python3 .agent/tools/traceability_check.py` must report **zero gaps** before
GATE 2 in `traceability: full` with `gates.traceability_hard_fail: true`. A gap means: a requirement with a story has no provable test, or a test
that proves nothing. The check compares the artefacts against the file system - a
filled matrix is not a proof, the existing file is. The check runs **before** the
gate. 

**Soft/Hard Fail:** In `traceability: light` or when not all gates are hard-failing, the orchestrator must present a traceability report with gaps and allow human approval ("accept gaps & proceed") when appropriate. Only hard-fail for `external/public` contracts with full traceability.

**Contract freeze.** The contract `docs/api/openapi.yaml` is **frozen after step 4**
(the stress test), not after gate 1. Consult `docs/architecture/contract-matrix.md` for classification (`internal|external|public`). Changes after freeze require a jump back to step 2 **only for `external/public`**; for `internal` use a justified change with impact analysis. 13 contract defects were found in the stress test, every one of them after the freeze.

**Agent-Wechsel am Gate:** the agent of the finished step stays active until the human
approves. Only then does the human switch to the agent of the next step.

## Briefing - der Steckbrief zwischen den Schritten

`.agent/artifacts/briefing.json` (Template: `.agent/templates/briefing.json`) schreibt der Orchestrator am Ende jedes Schritts und
committet ihn mit. Er ist der **Einstiegspunkt** des nächsten Schritts:

| Feld | Inhalt |
|---|---|
| Stand | letzter Schritt, Commit, nächster Schritt, Agent |
| Was du lesen musst | die Dateien — und sonst nichts |
| Entscheidungen, die gelten | F-x / ADR-x, die den Schritt binden |
| Offene Punkte | O-xx mit Owner |
| Was du nicht tust | z. B. „der Vertrag ist eingefroren" |

Regeln: Rollen-Agent liest das Briefing **zuerst**, dann nur die genannten Dateien.
Das Briefing ist **nie** die Wahrheit — die Dateien sind es. Keine Auszüge.


## Commit Convention

**After every SDLC step exactly one commit.** A step is not finished until its commit
exists. The commit is the checkpoint the human reviews at the gate.

| Field | Rule |
|---|---|
| Subject | `<type>(sdlc-<n>): <Kurzbeschreibung>` |
| Body | `Schritt <n> - <Schrittname> \| Agent: <agent-id> \| Artefakte: <pfade>` |
| Scope | index-basiert: `git reset -q` → `git add -A -- <pfade>` → `git commit`. Nur der Schritt-Scope wird gestaged. |
| Order | commit **before** the gate — the gate reviews the finished commit |
| Jump-back | a step that corrects an earlier step's artefacts produces **one** extra commit with the subject of the **corrected** step plus `(Nachzug)`. Maximal einer pro Schritt. |
| Empty | if nothing changed, no commit — no empty commits |
| Push | the pipeline never pushes — `git push` is not part of the pipeline |

```bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```

Types: `docs` Requirements/Architecture/Security · `refactor` Simplify/Refactoring ·
`test` TDD · `feat` Implementation · `build` Deploy.

Subject, paths and command per step: `.agent/PHASES.md` -> "Commit pro Schritt".

## Phase Skills

Verfahrens-Schritte werden als Skills geladen, nicht als eigene Agents:

| Step | Skill | Wird geladen von |
|---|---|---|
| 1a | `aisdlc-grill-stress` | `aisdlc-requirements` |
| 3 | `aisdlc-simplify` | `aisdlc-architecture` |
| 4 | `aisdlc-grill-stress` | `aisdlc-architecture` |
| 5 | `aisdlc-tdd` | `aisdlc-execution` |
| 7 | `aisdlc-simplify` | `aisdlc-execution` |
| 8 | `aisdlc-security-scan` | `aisdlc-qa` |
| 9 | `aisdlc-deploy` | `aisdlc-qa` |

Zusätzlich verfügbar: `strangler-fig`, `module-migration`, `test-self-correction`.

## Folder Ownership

| Folder | Owner (exclusive write) | Lesende Agenten (Read-Only) |
|---|---|---|
| `docs/requirements/` | Requirements Orchestrator | Architecture, Execution, QA, OpenCode |
| `docs/architecture/`, `docs/api/`, `docs/adr/` | Architecture Orchestrator | Execution, QA, OpenCode |
| `src/` | OpenCode (Build-Phase) | Requirements, Architecture, QA |
| `tests/` | OpenCode (Test-Phase) | QA, Execution |
| `docs/execution/` | Execution Orchestrator | QA, OpenCode, menschl. Reviewer |
| `docs/qa/` | QA Orchestrator | Execution, OpenCode (Self-Correction) |
| `coverage/` | CI/Test-Runner | QA, Execution |
| `.agent/artifacts/` | zustaendiger Rollen-Agent des Schritts | Alle (Read-Only) |
| `.agent/artifacts/run-log.jsonl` | `aisdlc-orchestrator` | Alle (Read-Only) |
| `.agent/knowledge/` | Architecture Orchestrator | Alle (Read-Only) |
| `how-to/` | Menschlicher Reviewer | Alle (Read-Only) |
| `.opencode/` | OpenCode-System | Alle (Read-Only) |

**`.agent/artifacts/`** ist der kanonische Zielort der Schritt-Artefakte
(`requirements.md`, `user-stories.md`, `risk-register.md`, `architecture.md`,
`folder-structure.md`, `test-report.md`, `security-audit.md`, `deploy-report.md`,
`briefing.md`, `logging.md`, `traceability.md`, `traceability-fill.md`,
`run-log.jsonl`). Jeder Schritt schreibt eigene Dateien; dort schreiben duerfen
die vier Rollen-Agenten. Ein Rollen-Agent, dessen Workflow das als Ziel nennt,
aber das Schreibrecht nicht besitzt, schreibt sonst an einen Ort, den niemand
findet.

**Spiegel-Regel fuer das Datenbankschema:** `.agent/knowledge/schema.sql` ist
kanonisch, `docs/architecture/schema.sql` die versionierte Kopie — beide
byte-identisch, durch `SchemaMirrorIT` (T-SCHEMA-MIRROR-01) erzwungen. Ein Agent,
der das Schema aendert, aendert **beide** Dateien im selben Schritt (ADR-019 §6,
O-13/S-26).

**Verweis-Regel fuer den API-Vertrag:** `docs/api/openapi.yaml` ist der einzige
Vertrag. `.agent/knowledge/openapi.yaml` ist ein Verweis darauf, keine Kopie.

## Escalation

- `REQUIREMENT_GAP`: Anforderung unvollständig → Rücksprung zu Phase 1.
- `ARCHITECTURAL_BLOCKER`: Design undurchführbar → Rücksprung zu Phase 2.
- Git-Reset der betroffenen `src/`-Dateien bei ARCHITECTURAL_BLOCKER.
- **Fast-Track/Patch (Delta-Flow):** Bei `profile: mvp` und `allow_fast_track: true` sowie `change_flow.allow_delta_for_hotfix_patch: true` dürfen hotfix/patch/feature (type in {hotfix, patch, feature}, contract_impact in {none, internal}, scope in {local, module}, breaking=false) über den Delta-Pfad (1a → 5a → 6a → 8a) laufen. **Niemals** bei `contract_impact in {external, public}` oder Änderungen an `docs/api/openapi.yaml`. Contract-Impact wird zuerst gegen `docs/architecture/contract-matrix.md` geprüft. In `profile: enterprise` ist Delta-Flow nur bei expliziter Freigabe erlaubt.

**Migration (mode=migration/brownfield):** Contract-Freeze gilt nicht für Adapter/ACL zwischen Alt und Neu. Nur neue externe/public Verträge sind eingefroren. Migration erfordert CUTOVER-GATE (Routing, idempotente Migrationsskripte, getesteter Backout, Monitoring) vor finalem Umschalten.

## Secrets

- Alle Umgebungsvariablen in `.env` (nie hardcodiert). `.env` ist in `.gitignore`.
- Secret-Scan in CI/CD (`gitleaks` oder `trufflehog`). Keine Credentials in
  Quellcode, Config-Dateien, Logs, Dockerfiles oder Pipelines.
- In `profile: mvp` enforce at least a minimal secrets/dependency scan (diff-based). In `profile: enterprise` full security requirements apply.

## Output

- Jeder Task endet mit einem Summary-Artefakt in der zugeordneten Directory.
- Große Artefakte (> ~20 KB) beginnen mit einer Tabelle (siehe Regel 15).

## Tech Stack

Siehe `.agent/knowledge/stack.md` — die Entscheidung wird in Phase 2 getroffen und
in `docs/adr/ADR-001-tech-stack.md` dokumentiert. Der Default in dieser Datei ist der
Rahmen, nicht die Entscheidung.

## Rechte-Matrix

Siehe **Folder Ownership** oben — eine einzige Quelle, keine zweite Kopie.
Die technische Durchsetzung steht in den `permissions` der Agent-Frontmatter
(`.opencode/agents/`) und in `.opencode/opencode.jsonc`.
