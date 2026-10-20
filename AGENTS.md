# AGENTS.md — AI SDLC Orchestrator Framework

## Project Overview

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
10. Load the phase skill when the step calls for it (see Phase Skills) — do not improvise the
    procedure if a skill exists for it.

## HARD GATES — Agent Must Stop and Await Human Approval

- **After Step 2** (Architecture complete) — before any application code is written.
  The agent must produce all artifacts, present a summary, and wait for human approval.
- **After Step 8** (Security & Compliance complete) — before Deploy runs.
  The agent must present QA and Security findings and wait for human approval.

Without approval, the pipeline does not advance. No exceptions.

## Commit Convention

**After every SDLC step exactly one commit.** A step is not finished until its commit
exists. The commit is the checkpoint the human reviews at the gate.

| Field | Rule |
|---|---|
| Subject | `<type>(sdlc-<n>): <Kurzbeschreibung>` |
| Body | `Schritt <n> - <Schrittname> \| Agent: <agent-id> \| Artefakte: <pfade>` |
| Scope | only the files of this step that changed or are new — enforced by the pathspec |
| Order | commit **before** the gate — the gate reviews the finished commit |
| Empty | if nothing changed, no commit — no empty commits |
| Push | the pipeline never pushes — `git push` is not part of the pipeline |

```bash
git add -A .
git diff --cached --name-only -- docs/requirements/ .agent/artifacts/ \
  | git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
             -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/" \
             --pathspec-from-file=-
git restore --staged .
```

The scope is enforced technically, not by convention: only files that changed or are new
inside the path scope are committed. The `--pathspec-from-file` form is used because
`git commit -- <pfade>` aborts with "pathspec did not match any files" as soon as one
scope path is empty or does not exist yet — then nothing would be committed at all.

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
| `.agent/knowledge/` | Architecture Orchestrator | Alle (Read-Only) |
| `how-to/` | Menschlicher Reviewer | Alle (Read-Only) |
| `.opencode/` | OpenCode-System | Alle (Read-Only) |

**`.agent/artifacts/` ist der kanonische Zielort der Schritt-Artefakte**
(`requirements.md`, `user-stories.md`, `risk-register.md`, `architecture.md`,
`folder-structure.md`, `test-report.md`, `security-audit.md`, `deploy-report.md`).
Jeder Schritt schreibt eigene Dateien — der Bereich ist geteilt, nicht exklusiv.
Dort schreiben zu duerfen: die vier Rollen-Agenten (`aisdlc-requirements`,
`aisdlc-architecture`, `aisdlc-execution`, `aisdlc-qa`). Ein Rollen-Agent, dessen
Workflow `.agent/artifacts/` als Ziel nennt, aber das Schreibrecht nicht besitzt,
schreibt sonst an einen Ort, den niemand mehr findet.

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
