# AI SDLC – KI-agentengestützter Softwareentwicklungszyklus

Ein SDLC-Template, in dem ein **Orchestrator-Agent** den Prozess steuert und **OpenCode** die Entwicklung ausführt. Der Orchestrator wechselt je nach SDLC-Phase den Systemprompt (`docs/prompts/`) und regelt, wer in welchem Verzeichnis schreiben bzw. nur lesen darf.

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
│   └── prompts/                             # System-Prompts für Orchestrator & OpenCode
│       ├── sdlc_phase0_legacy.md            # Prompt Phase 0: Legacy-Analyse (optional)
│       ├── sdlc_phase1_requirements.md      # Prompt Phase 1: Anforderungen in Stories/ACs zerlegen
│       ├── sdlc_phase2_architecture.md      # Prompt Phase 2: Architektur, API-Spec, ADRs entwerfen
│       ├── sdlc_phase3_implementation.md    # Prompt Phase 3: Tasks an OpenCode übergeben, validieren
│       ├── sdlc_phase4_testing.md           # Prompt Phase 4: Tests steuern, Coverage & Quality Gates
│       ├── 00_orchestrator_analysis.md      # Legacy-Prompt-Variante (1 Orchestrator für alles)
│       ├── 01_orchestrator_requirements.md  # Requirements-Prompt-Variante (1 Orchestrator)
│       ├── 02_orchestrator_architecture.md  # Architektur-Prompt-Variante (1 Orchestrator)
│       ├── 03_orchestrator_implementation.md# Implementierungs-Prompt-Variante (1 Orchestrator)
│       ├── 04_orchestrator_testing.md       # Testing-Prompt-Variante (1 Orchestrator)
│       └── opencode_task_self_correction.md # OpenCode-Task: fehlgeschlagene Tests selbst beheben
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

1. Orchestrator startet mit `docs/prompts/01_orchestrator_requirements.md`, liest die Anforderung und legt PRD, Stories, `.feature`-Dateien und die Traceability Matrix an.
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

- Jede Instanz trägt ihren eigenen System-Prompt (`docs/prompts/sdlc_phaseN_*.md`) per `agent.prompt.path`.
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

### Hard Gates

- **GATE 1** (nach Phase 2): Human Approval vor Code-Generierung
- **GATE 2** (nach Phase 8): Human Approval vor Deployment

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
- `aisdlc-requirements` — Phase 1 (Anforderungen)
- `aisdlc-architecture` — Phase 2 (Architektur)
- `aisdlc-execution` — Phase 3 (Implementierung)
- `aisdlc-qa` — Phase 4 (Testing/QA)
- `java-backend` — Stack-Agent (Java/Spring Boot)
- `react-frontend` — Stack-Agent (React/TypeScript)

Jeder Agent hat folder-scoped Schreibrechte und einen eigenen System-Prompt.
Siehe `how-to/how-to.md` für Details.
