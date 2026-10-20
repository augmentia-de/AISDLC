# AI SDLC – KI-agentengestützter Softwareentwicklungszyklus

Ein SDLC-Template, in dem ein **Orchestrator-Agent** den Prozess steuert und **OpenCode** die Entwicklung ausführt. Der Orchestrator wechselt je nach SDLC-Phase den Systemprompt (`.agent/prompts/`) und regelt, wer in welchem Verzeichnis schreiben bzw. nur lesen darf.

## Ablauf (Iterationsschleifen)

Der Orchestrator durchläuft pro Feature vier Feedback-Loops und eskalieren sich gegenseitig:

```
[1. Requirements] ──> [2. Architecture] ──> [3. Implementation] ──> [4. Testing/QA] ──> PR & Merge
       ^                    ^                       |                       |
       └── REQUIREMENT_GAP ─┘                       └── Blocker/Fehler ────┘
                              [ARCHITECTURAL_BLOCKER ──> Redesign, Git-Reset, Re-Entry]
```

1. **Planungs-/Architektur-Iteration** – Machbarkeit prüfen, betroffene Dateien analysieren (`impact_analysis.json`).
2. **Build- & Lint-Iteration** – Code kompiliert & Stilvorgaben erfüllt; Fehler werden als Log an OpenCode zurückgegeben (`lint_build_results.json`).
3. **Test-Self-Correction-Loop** – Test-Runner läuft; Fehlschläge werden geparst und an OpenCode zur Korrektur übergeben (`test_results.json`).
4. **Quality-Gate-Loop** – Gesamtabnahme (Syntax, Tests, Security, Coverage) vor dem Merge (`gate_evaluation.json`).

**Eskalation:** Erkennt OpenCode während der Implementierung eine unvollständige Anforderung (`REQUIREMENT_GAP`) oder ein undurchführbares Design (`ARCHITECTURAL_BLOCKER`), stoppt es sofort und meldet sich mit strukturiertem JSON an den Orchestrator. Dieser ergänzt die Anforderungen (nahtloser Weiterbau) bzw. überarbeitet Architektur/API, setzt betroffene Dateien per Git zurück und startet die Implementierung neu.

## Verzeichnisstruktur

```
AISDLC/
├── README.md                                # Dieses Dokument: Struktur, Ablauf, Rollen
├── dis.txt                                  # Quelle/Verlauf des Konzepts (Anforderung & Analyse)
├── docs/
│   ├── sdlc_ergebnisse.md                   # Ergebnistabelle: jede Phase -> Output, Format, Verwendung
│   ├── requirements/                 (✍️ Requirements Orchestrator)
│   │   ├── PRD.md                           # Product Requirement Document: Single Source of Truth
│   │   ├── traceability_matrix.json         # Mapping Requirement-ID -> Test -> Code (Compliance)
│   │   ├── features/*.feature               # BDD-Szenarien in Gherkin (Given/When/Then)
│   │   └── stories/*.json                   # User Stories als tickerartige Aufgaben für OpenCode
│   ├── architecture/                 (✍️ Architecture Orchestrator)
│   │   ├── domain_model.md                  # Systemkontext & Domain-Modell
│   │   └── diagrams/*.mmd                   # C4-Diagramme als Code (Mermaid.js)
│   ├── api/                           (✍️ Architecture Orchestrator)
│   │   └── openapi.yaml                     # API-Vertrag 3.0+, Grundlage für Stubs & Tests
│   ├── adr/                           (✍️ Architecture Orchestrator)
│   │   └── ADR-*.md                         # Architecture Decision Records (Nygard-Schema)
│   ├── execution/                     (✍️ Execution Orchestrator)
│   │   ├── pull_requests/PR_*.md            # Generierte PR-Beschreibungen pro Task
│   │   └── CHANGELOG.md                     # Versionshistorie unter [Unreleased]
│   ├── qa/                           (✍️ QA Orchestrator)
│   │   └── bugs/BUG-*.json                  # Strukturierte Bug-Reports (Pfad, Zeile, Ursache)
├── .agent/                          # Agent Control Layer (ASDF-Stil)
│   ├── prompts/                     # System-Prompts für Orchestrator & OpenCode
│   │   ├── orchestrator/            # Ein-Orchestrator-Modus (1 Agent, wechselt Phasen)
│   │   │   ├── 00_orchestrator_analysis.md
│   │   │   ├── 01_orchestrator_requirements.md
│   │   │   ├── 02_orchestrator_architecture.md
│   │   │   ├── 03_orchestrator_implementation.md
│   │   │   └── 04_orchestrator_testing.md
│   │   └── multi-instance/         # Multi-Instanz-Modus (jede Phase als eigene Instanz)
│   │       ├── sdlc_phase0_legacy.md
│   │       ├── sdlc_phase1_requirements.md
│   │       ├── sdlc_phase2_architecture.md
│   │       ├── sdlc_phase3_implementation.md
│   │       └── sdlc_phase4_testing.md
│   ├── workflows/                   # Workflow-Definitionen pro Phase (9 Dateien)
│   ├── knowledge/                   # openapi.yaml, schema.sql, stack.md
│   └── artifacts/                   # Ergebnisse jeder Phase
├── .opencode/                       # OpenCode-System
│   ├── agents/                      # 6 Primary-Agents
│   └── skills/                      # Skills (inkl. strangler-fig, module-migration, test-self-correction)
├── src/                             (✍️ OpenCode Build-Modus)
│   └── ...                                  # Produktiver Quellcode (nur vom Orchestrator gelesen)
├── tests/                           (✍️ OpenCode QA-Modus)
│   ├── unit/                                # Unit-Tests
│   ├── integration/                         # Integrationstests
│   └── e2e/                                 # End-to-End-Tests
├── coverage/                        (✍️ CI / Test-Runner)
│   └── coverage-summary.json                # Coverage-Report als Qualitätsschranke (Quality Gate)
└── .opencode/                       (✍️ OpenCode-System)
    └── agent_context.json                   # Kontext & Zustand der OpenCode-Sitzungen
```

## Rechte-Matrix (eigener Schreiber, andere lesen nur)

| Verzeichnis | Schreibender Agent (exklusiv) | Lesende Agenten (Read-Only) |
| :--- | :--- | :--- |
| `docs/requirements/` | Requirements Orchestrator | Architecture, Execution, QA, OpenCode |
| `docs/architecture/`, `docs/api/`, `docs/adr/` | Architecture Orchestrator | Execution, QA, OpenCode |
| `docs/execution/` | Execution Orchestrator (Tech Lead) | QA, OpenCode, menschl. Reviewer |
| `docs/qa/` | QA Orchestrator | Execution, OpenCode (Self-Correction) |
| `src/` | OpenCode (Build-Phase) | Requirements, Architecture, QA |
| `tests/` | OpenCode (Test-Phase) | QA, Execution |
| `coverage/` | CI/Test-Runner | QA, Execution |

## Nutzung

1. Orchestrator startet mit `.agent/prompts/01_orchestrator_requirements.md`, liest die Anforderung und legt PRD, Stories, `.feature`-Dateien und die Traceability Matrix an.
2. Phase 2 (`02_orchestrator_architecture.md`, Plan-Modus): Architekturdiagramme, `openapi.yaml`, ADRs entstehen.
3. Phase 3 (`03_orchestrator_implementation.md`, Build-Modus): OpenCode bekommt atomare Tasks, der Orchestrator validiert Linter/Build und bereitet Commits & PRs vor.
4. Phase 4 (`04_orchestrator_testing.md`): Test-Suiten schreiben und ausführen; bei Fehlern übergibt der Orchestrator `{{TEST_RESULTS_JSON}}` an `opencode_task_self_correction.md` (Self-Correction-Loop).
5. Eskalation bei `REQUIREMENT_GAP`/`ARCHITECTURAL_BLOCKER` über strukturiertes JSON – danach Re-Entry in die jeweilige Phase; bei Blocker Git-Reset der betroffenen `src/`-Dateien.

### Multi-Instanz-Betrieb (0–4 parallele Phase-Services)

Neben dem Ein-Orchestrator-Modus kann jede Phase als eigener `ai-agent-service`-Instanz laufen (siehe discord-channel `start-sdlc.sh`, Observer als Supervisor):

```
observer-service (18100, Supervisor) ── start/cancel/active ──► phase1..phase4
phase0 (18082) → phase1 (18083) → phase2 (18084) → phase3 (18085) → phase4 (18086)
     ↑ REST-Handoff pro Phase (send_message → nächste Instanz /api/messages)
```

- Jede Instanz trägt ihren eigenen System-Prompt (`.agent/prompts/sdlc_phaseN_*.md`) per `agent.prompt.path`.
- Ein Wechsel bleibt ohne `docs/00_legacy_analysis/` (Phase 0 optional, Einstieg in Phase 1 direkt möglich).
- Die gemeinsame SQLite-`trace.db` macht den Phasen-Wechsel im Observer als "aktive Phase" sichtbar.
---

## Upgrade: ASDF-Stil (Immune System, Hard Gates, Knowledge Base)

Dieses Projekt wurde um Konzepte aus dem [Agentic Software Delivery Framework](https://github.com/kasampra/Agentic-Software-Delivery-Framework) erweitert:

### Neue Dateien und Verzeichnisse

| Datei/Verzeichnis | Beschreibung |
|---|---|
| `AGENTS.md` | Immune System — Behaviour Rules, Hard Gates, Folder Ownership, Secrets. Jeder Agent liest diese Datei zuerst. |
| `.agent/knowledge/` | Zentrale Knowledge Base: `openapi.yaml`, `schema.sql`, `stack.md` |
| `.agent/artifacts/` | Zentraler Artefaktspeicher für alle Phasen-Ergebnisse |
| `.agent/workflows/` | 9 Workflow-Dateien (01-requirements bis 09-deploy) mit Gates und Output-Definitionen |
| `.agent/PHASES.md` | **Single Source of Truth**: Schritt → Instanz → Agent → Skill → Gate |

### Hard Gates

- **GATE 1** (nach Schritt 2 — Architektur): Human Approval vor Code-Generierung
- **GATE 2** (nach Schritt 8 — Security): Human Approval vor Deployment

### Parallele Build-Phasen (Phase 6)

Backend, Frontend und Infra werden gleichzeitig gebaut (3 parallele Streams), synchronisiert über den `openapi.yaml`-Contract aus Phase 2.

### Referenz-App: TaskFlow

Das Projekt dient als Vorlage für eine Task-App mit folgenden Komponenten:
- Backend: FastAPI oder Spring Boot (JWT-Auth, CRUD, Status-Workflow)
- Frontend: Next.js oder React (Task-Liste, Filter, Sortierung)
- Infra: Docker Compose, GitHub Actions CI/CD
- Tests: Unit, Integration, E2E, Coverage ≥ 80%
- Security: OWASP Top 10 Audit

### Nutzung der neuen Struktur

1. `AGENTS.md` lesen — enthält alle Regeln und Gates.
2. `.agent/knowledge/` — Tech-Stack-Entscheidungen und API-Spec.
3. `.agent/workflows/` — Workflow-Anweisungen pro Phase.
4. `.agent/artifacts/` — Ergebnisse jeder Phase landen hier.

### OpenCode-Agents

Sechs Primary-Agents sind in `.opencode/agents/` definiert:

**Phasen-Agents (stack-agnostisch)**

| Agent | Schritte | Schreibt in |
|---|---|---|
| `aisdlc-requirements` | 1 + 1a | `docs/requirements/` |
| `aisdlc-architecture` | 2–4 | `docs/architecture/`, `docs/api/`, `docs/adr/` |
| `aisdlc-execution` | 5–7 | `src/`, `tests/`, `docs/execution/` |
| `aisdlc-qa` | 8–9 | `tests/`, `docs/qa/` |

**Stack-Agents (austauschbar)**

| Agent | Stack | Schreibt in |
|---|---|---|
| `stack/java/backend` | Java / Spring Boot | `src/`, `tests/`, `docs/execution/` |
| `stack/react/frontend` | React / TypeScript | `src/`, `tests/`, `docs/execution/` |

**Skills (Verfahrens-Schritte)**

| Skill | Schritte |
|---|---|
| `aisdlc-grill-stress` | 1a + 4 |
| `aisdlc-simplify` | 3 + 7 |
| `aisdlc-tdd` | 5 |
| `aisdlc-security-scan` | 8 |
| `aisdlc-deploy` | 9 |
| `strangler-fig`, `module-migration`, `test-self-correction` | nach Bedarf |

Jeder Agent hat folder-scoped Schreibrechte und einen eigenen System-Prompt.
Details: `how-to/how-to.md`, Zuordnung: `.agent/PHASES.md`.

### Stack-Wissen aus den Bibliotheken (ohne Kopien)

Stack-Skills kommen per `skills.paths` direkt aus den geklonten Bibliotheken — kein Kopieren,
keine Drift. Die `skill`-Permission in `opencode.jsonc` filtert auf Stack-Präfixe, damit nicht
alle 496 Bibliotheks-Skills in den Kontext geladen werden (Java+Spring: 342 Token, React/TS: 1.029).
