# AGENTS.md — AI SDLC Orchestrator Framework

## Project Overview

**Zwei Schichten.** Das Framework (`.opencode/`, `.agent/`, `AGENTS.md`) ist
systemagnostisch: es kennt keinen Stack, keine Bibliothekspfade und keine
stack-spezifischen Skills. Die **Stack-Schicht** liegt in `opencode.jsonc` im
Projekt-Root und wird bei jedem Stack-Wechsel ersetzt — das Framework bleibt
unberührt. Details: `how-to.md` → "Stack-Schicht".


AI SDLC: KI-agentengestützter Softwareentwicklungszyklus. Ein Orchestrator-Agent steuert den
Prozess und OpenCode führt die Entwicklung aus. Der Orchestrator wechselt je nach SDLC-Schritt
den Systemprompt (`.agent/prompts/`) und regelt, wer in welchem Verzeichnis schreiben bzw.
nur lesen darf.

**Phasen-Zuordnung:** `.agent/PHASES.md` — Schritt → Instanz → Agent → Skill → Gate.
Diese Datei ist die Single Source of Truth; bei Widerspruch gilt sie.

## Behaviour Rules

1. Always read this file before starting any task.
2. Read the orchestrator prompt for the current phase before starting.
3. Write all output artifacts to the designated directories (see Folder Ownership).
4. Never modify files outside your assigned folder (see Folder Ownership).
5. Never delete files without explicit human approval.
6. Write atomic commits: **exactly one commit per SDLC step** (see Commit Convention) — never one commit per file, never several commits for one step.
7. Flag any security risk with `[SECURITY]` prefix in your response.
8. Quote the top 3 rules from this file before taking any action.
9. Always re-read this file after context compaction or session reset.
10. Load the phase skill when the step calls for it (see Phase Skills) - do not improvise the
    procedure if a skill exists for it.
11. **No output commands.** `echo`, `printf`, `pwd` and `cat` for orientation produce no
    result - each one costs a model turn and a permission check. Use `read`, `glob` and
    `grep` to orient yourself. In the first full run, 591 of 1.094 shell calls (54 %)
    were of this kind.
12. **Read the briefing first.** `.agent/artifacts/briefing.md` is the briefing of the
    previous step. Read it, then read only the files it names. The briefing is the
    entry point - never the truth (see Briefing).
13. **Log structured, never payload.** The application logs one event per line with
    correlation ID, event type, tenant, outcome and duration. **Never** payload data,
    secrets, tokens or complete personal data records. The convention lives in
    `.agent/artifacts/logging.md` (step 6); the security scan checks against it, not
    against an invented expectation.
14. **Write the run log.** Every step start, step end, gate, check result and jump-back
    goes as one JSON line into `.agent/artifacts/run-log.jsonl`. The orchestrator
    writes it - it is the only one that sees step boundaries and gates. The run log is
    data; the artefacts are prose. Both are needed, and only the data can be counted.
15. **Big artefacts start with a table.** An artefact over ~20 KB gets a table at the
    top: finding, severity, status, where it was fixed. The derivation stays below it.
    A table is an entry point, prose is the proof.

## HARD GATES - Agent Must Stop and Await Human Approval

- **After Step 2** (Architecture complete) - before any application code is written.
  The agent must produce all artifacts, present a summary, and wait for human approval.
  This gate reviews the **direction**: architecture, decisions, ADR set. The contract
  is still a draft here.
- **After Step 8** (Security & Compliance complete) - before Deploy runs.
  The agent must present QA and Security findings and wait for human approval.

Without approval, the pipeline does not advance. No exceptions.

**Traceability is a gate condition, not a footnote.** The check
`python3 .agent/tools/traceability_check.py` must report **zero gaps** before
GATE 2. A gap means: a requirement with a story has no provable test, or a test
that proves nothing. The check compares the artefacts against the file system - a
filled matrix is not a proof, the existing file is. The check runs **before** the
gate, not after it: in the reference run the matrix stayed unfilled for 15 hours
because nothing looked at it until step 7 was long done.

The **matrix** holds the static half (requirement -> story -> feature). The
**dynamic** half (requirement -> test -> code) is derived from the file system and
written to `.agent/artifacts/traceability.md`. No step fills a field it cannot
prove - a made-up path is worse than a gap, because it looks proven and is not.

**Contract freeze.** The contract `docs/api/openapi.yaml` is **frozen after step 4**
(the stress test), not after gate 1. The first full run found 13 contract defects in
the stress test - 13 of them *after* the contract had been frozen, and every one of
them invalidated schema, code and tests. Changes after the freeze require a jump back
to step 2; the contract is never edited in passing.

**Agent-Wechsel am Gate:** the agent of the finished step stays active until the human
approves. Only then does the human switch to the agent of the next step. Corrections
after a gate request therefore land with the agent that still has the write permission
for that step's folder - and the next step's write permissions only become active when
that step actually starts.

## Briefing - der Steckbrief zwischen den Schritten

`.agent/artifacts/briefing.md` is written by the orchestrator at the end of every step
and committed with it. It is the **entry point** for the next step's agent:

| Section | Content |
|---|---|
| Stand | last completed step, its commit, the next step and its agent |
| Was du lesen musst | the files to read - and nothing else |
| Entscheidungen, die gelten | F-x / ADR-x that bind this step |
| Offene Punkte | O-xx with owner |
| Was du nicht tust | e.g. "the contract is frozen" |

Rules:

- A role agent reads the briefing **first**, then only the files it names. It does not
  walk the repository.
- **The briefing is never the truth - the files are.** If they contradict, the files
  win, and the briefing is corrected in the next commit.
- The briefing is a **pointer**, like the subagent's return value: status, commit, open
  points. No excerpts, no derivations.
- The briefing exists because the first full run's context grew 80x over four steps
  (173k -> 14.08M input tokens): each step re-read everything. A briefing makes the
  entry point explicit.


## Commit Convention

**After every SDLC step exactly one commit.** A step is not finished until its commit
exists. The commit is the checkpoint the human reviews at the gate.

| Field | Rule |
|---|---|
| Subject | `<type>(sdlc-<n>): <Kurzbeschreibung>` |
| Body | `Schritt <n> - <Schrittname> \| Agent: <agent-id> \| Artefakte: <pfade>` |
| Scope | only the files of this step that changed or are new — enforced by the pathspec |
| Order | commit **before** the gate — the gate reviews the finished commit |
| Jump-back | a step that corrects an earlier step's artefacts produces **one** extra commit with the subject of the **corrected** step plus `(Nachzug)`. The body names the step it came from and the reason. **At most one** jump-back per step — a second one means the cause is in the flow, not in the artefact. |
| Empty | if nothing changed, no commit — no empty commits |
| Push | the pipeline never pushes — `git push` is not part of the pipeline |

```bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
  -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```

The scope is enforced technically, not by convention: the index is cleared first, then
only the step's paths are staged, so the commit contains exactly the step's files.

Two reasons why not `git commit -- <pfade>`: it aborts with "pathspec did not match any
files" as soon as one scope path holds no tracked file, and the `--pathspec-from-file`
route is broken under the global `rtk` plugin — `rtk` rewrites `git diff` and appends a
"Changes:" block, which then aborts the commit with "empty string is not a valid
pathspec".

`git reset -q` unstages foreign work but never deletes it.

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

**`.agent/artifacts/` ist der kanonische Zielort der Schritt-Artefakte**
(`requirements.md`, `user-stories.md`, `risk-register.md`, `architecture.md`,
`folder-structure.md`, `test-report.md`, `security-audit.md`, `deploy-report.md`,
`briefing.md`, `logging.md`, `traceability.md`, `traceability-fill.md`,
`run-log.jsonl`).
Jeder Schritt schreibt eigene Dateien — der Bereich ist geteilt, nicht exklusiv.
Dort schreiben zu duerfen: die vier Rollen-Agenten (`aisdlc-requirements`,
`aisdlc-architecture`, `aisdlc-execution`, `aisdlc-qa`). Ein Rollen-Agent, dessen
Workflow `.agent/artifacts/` als Ziel nennt, aber das Schreibrecht nicht besitzt,
schreibt sonst an einen Ort, den niemand mehr findet.

**Spiegel-Regel fuer das Datenbankschema:** `.agent/knowledge/schema.sql` ist
kanonisch, `docs/architecture/schema.sql` ist die versionierte Kopie im
Commit-Scope der Architekturphase. Beide werden byte-identisch gepflegt; der
Mirror-Test `SchemaMirrorIT` (T-SCHEMA-MIRROR-01) erzwingt das. Ein Agent, der
das Schema aendert, aendert **beide** Dateien im selben Schritt. Siehe ADR-019
§6 und Befund O-13/S-26.

**Verweis-Regel fuer den API-Vertrag:** `docs/api/openapi.yaml` ist der einzige
Vertrag. `.agent/knowledge/openapi.yaml` ist ein Verweis darauf, keine Kopie —
wer den Vertrag aendert, aendert nur `docs/api/openapi.yaml`.

## Escalation

- `REQUIREMENT_GAP`: Anforderung unvollständig → Rücksprung zu Phase 1
- `ARCHITECTURAL_BLOCKER`: Design undurchführbar → Rücksprung zu Phase 2, Git-Reset der betroffenen `src/`-Dateien

## Secrets

- All environment variables in `.env` (never hardcoded).
- `.env` is in `.gitignore`.
- Secret-Scan in CI/CD (z. B. `gitleaks` oder `trufflehog`).
- No API keys, tokens, or passwords in source code, config files, or logs.
- No hardcoded credentials in Dockerfiles or CI/CD pipelines.

## Code Style

- TypeScript: strict mode always on.
- Python: type hints on all functions, Black formatter, Python 3.11+.
- All APIs must follow the `openapi.yaml` contract in `docs/api/`.
- All environment variables stored in `.env`, never hardcoded.

## Output

- Always write a summary artifact at the end of every task.
- All artifacts go to the designated directories per Folder Ownership.
- All artifacts are written as files, not just in the conversation.

## Tech Stack (Sovereign Stack)

- **Backend**: FastAPI (Python 3.11+) oder Spring Boot (Java 21+)
- **Frontend**: Next.js 15 (TypeScript, App Router) oder React 19
- **Database**: PostgreSQL 16 (local: raw via docker-compose, prod: Supabase)
- **Auth**: Custom JWT (scoped by `organisation_id`) — no Supabase required locally
- **Deployment**: Cloud Run (Docker) oder Docker Compose
- **LLM**: Gemini 2.0 Flash oder OpenAI (konfigurierbar)
- **GDPR**: EU-hosted, 90-day retention, right to erasure

## Rechte-Matrix

Siehe **Folder Ownership** oben — eine einzige Quelle, keine zweite Kopie.
Die technische Durchsetzung steht in den `permissions` der Agent-Frontmatter
(`.opencode/agents/`) und in `.opencode/opencode.jsonc`.
