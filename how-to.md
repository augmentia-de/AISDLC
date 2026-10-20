# How-To: Wann welcher Agent aktiv sein sollte

## Übersicht

OpenCode hat sechs Primary-Agents für das AISDLC-Projekt. Jeder Agent hat eine
eigene Rolle, eigene Schreibrechte und einen eigenen System-Prompt. Sie werden
nicht gleichzeitig aktiv — **pro Phase ist genau einer der Haupt-Agent**.

| Agent | Phase | Default? | Schreibt in | Liest von |
|---|---|---|---|---|
| `aisdlc-requirements` | 1 (Anforderungen) | **ja** | `docs/requirements/` | User Input, PRD, Domain-Modell |
| `aisdlc-architecture` | 2 (Architektur) | nein | `docs/architecture/`, `docs/api/`, `docs/adr/` | PRD, Features, Domain-Modell |
| `aisdlc-execution` | 3 (Implementierung) | nein | `src/`, `tests/`, `docs/execution/` | Stories, `openapi.yaml`, Plan |
| `aisdlc-qa` | 4 (Testing/QA) | nein | `tests/`, `docs/qa/` | Features, `src/`, bestehende Tests |
| `java-backend` | Stack (Backend) | nein | `src/`, `tests/` | `openapi.yaml`, `features/`, Code |
| `react-frontend` | Stack (Frontend) | nein | `src/`, `tests/` | `openapi.yaml`, `features/`, Code |

---

## Phasen-Ablauf (empfohlene Reihenfolge)

```
[1. Anforderungen] ──► [2. Architektur] ──► [3. Implementierung] ──► [4. Testing/QA]
       │                       │                      │                      │
       └── REQUIREMENT_GAP ────┘                      └── Blocker/Fehler ────┘
                              [ARCHITECTURAL_BLOCKER ──► Redesign, Git-Reset, Re-Entry]
```

### Schritt 1: Anforderungen erfassen

**Aktiver Agent:** `aisdlc-requirements` (ist der Default)

```bash
cd /home/torsten/dev/my-projects/AISDLC
opencode
```

Du startest in Phase 1. Der Agent liest deine Anforderung und erstellt:
- PRD (`docs/requirements/PRD.md`)
- User Stories (`docs/requirements/stories/<story_id>.json`)
- BDD-Szenarien (`docs/requirements/features/<feature_name>.feature`)
- Traceability Matrix (`docs/requirements/traceability_matrix.json`)

**Wann zu Phase 2 wechseln:** Wenn PRD, Stories und Acceptance Criteria vollständig und
geprüft sind. Wechsel mit `Shift+Tab` oder `/agents`.

**Eskalation:** Bei unklaren Anforderungen meldet der Agent `REQUIREMENT_GAP` als
strukturiertes JSON und stoppt — er rät nicht.

---

### Schritt 2: Architektur entwerfen

**Aktiver Agent:** `aisdlc-architecture`

```bash
opencode run --agent aisdlc-architecture "Entwirf die Architektur auf Basis des PRD"
```

Oder in der TUI: `Shift+Tab` bis `aisdlc-architecture` ausgewählt ist.

Der Agent liest PRD, Features und Domain-Modell und erstellt:
- API-Spezifikation (`docs/api/openapi.yaml`)
- Architekturdiagramme (`docs/architecture/diagrams/*.mmd`)
- Architecture Decision Records (`docs/adr/ADR-<ID>-<titel>.md`)

**Wann zu Phase 3 wechseln:** Wenn `openapi.yaml` und ADRs vollständig sind und
die Schnittstellen feststehen.

**Eskalation:** Bei undurchführbarem Design meldet der Agent `ARCHITECTURAL_BLOCKER`
als strukturiertes JSON und empfiehlt einen Git-Reset der betroffenen Dateien.

---

### Schritt 3: Implementieren

**Aktiver Agent:** `aisdlc-execution` (oder `java-backend` / `react-frontend` für Stack-spezifische Arbeit)

```bash
opencode run --agent aisdlc-execution "Implementiere Task CRUD gemäß openapi.yaml"
```

Der Execution Orchestrator zerlegt den Implementierungsplan in atomare Tasks und
übergibt sie an OpenCode. Er validiert Lint/Build nach jedem Schritt und bereitet
Commits und PRs vor.

**Stack-spezifisch:** Wenn du direkt im Backend oder Frontend arbeitest, nutze
`java-backend` oder `react-frontend` statt des Execution Orchestrators:

```bash
opencode run --agent java-backend "Implementiere den OrderService"
opencode run --agent react-frontend "Erstelle die TaskList-Komponente"
```

**Wann zu Phase 4 wechseln:** Wenn die Implementierung läuft und Code vorhanden ist,
der getestet werden kann.

---

### Schritt 4: Testen und Qualitätssicherung

**Aktiver Agent:** `aisdlc-qa`

```bash
opencode run --agent aisdlc-qa "Schreibe Tests für TaskService"
```

Der QA Orchestrator schreibt Tests, führt sie aus, analysiert Fehler und leitet
Self-Correction-Schleifen ein. Er erstellt Bug-Reports bei Fehlern und prüft
Quality Gates (Coverage ≥ 80 %, alle Tests grün, keine kritischen Vulnerabilities).

**Rückgabe an Phase 3:** Bei fehlgeschlagenen Tests oder Blockern übergibt der
QA Orchestrator `{{TEST_RESULTS_JSON}}` an OpenCode zur Korrektur (Self-Correction-Loop).

---

## Regeln für den Agenten-Wechsel

### 1. Phasenreihenfolge einhalten

Die Phasen bauen aufeinander auf. Nicht Phase 3 starten, bevor Phase 2 abgeschlossen ist.
Ausnahmen: bei `REQUIREMENT_GAP` zurück zu Phase 1, bei `ARCHITECTURAL_BLOCKER` zurück zu Phase 2.

### 2. Agenten nicht mischen

Pro Phase ist **genau ein** Agent der Haupt-Agent. Nicht gleichzeitig `aisdlc-requirements`
und `aisdlc-architecture` aktiv haben — das führt zu widersprüchlichen Schreibrechten
und unklarer Verantwortlichkeit.

### 3. Wechseln ohne Neustart

```bash
# Agent durchschalten
Shift+Tab

# Agentenliste öffnen
Ctrl+X, dann a   (oder /agents)

# One-shot mit bestimmtem Agenten
opencode run --agent <agent-id> "Auftrag"

# Config ohne Neustart übernehmen
opencode reload
```

### 4. Subagenten bei Bedarf delegieren

Auch wenn der Haupt-Agent feststeht, können Subagenten bei Bedarf delegiert werden:

```text
Nutze den security-reviewer-Subagenten, um die Authentifizierung zu prüfen.
```

Subagenten laufen in eigener Kind-Session mit frischem Kontext — das Ergebnis kommt als
Zusammenfassung zurück.

---

## Berechtigungen (wer darf was)

| Agent | Lesen | Schreiben | Shell |
|---|---|---|---|
| `aisdlc-requirements` | alles | `docs/requirements/` | ask |
| `aisdlc-architecture` | alles | `docs/architecture/`, `docs/api/`, `docs/adr/` | ask |
| `aisdlc-execution` | alles | `src/`, `tests/`, `docs/execution/` | ask |
| `aisdlc-qa` | alles | `tests/`, `docs/qa/` | ask |
| `java-backend` | alles | alles (Base-Policy) | ask (außer git status/diff/log) |
| `react-frontend` | alles | alles (Base-Policy) | ask (außer git status/diff/log) |

**Wichtig:** Ein Agent darf nur in seine zugewiesenen Verzeichnisse schreiben. Wenn ein Agent
versucht, außerhalb seiner Rechte zu schreiben, wird die Operation blockiert.

---

## Eskalation und Rücksprung

| Situation | Signal | Rücksprung zu |
|---|---|---|
| Unklare oder unvollständige Anforderung | `REQUIREMENT_GAP` | Phase 1 (Requirements) |
| Undurchführbares Design | `ARCHITECTURAL_BLOCKER` | Phase 2 (Architecture) |
| Fehlende Tests bei Implementierung | (implizit) | Phase 5 (TDD) |
| Unsauberer Code | (implizit) | Phase 7 (Refactoring) |
| Security-Vulnerability | (implizit) | Phase 6 (Code-Generation) |
| Produktions-Incident | (implizit) | Phase 2 (Architecture) |

---

## Typische Fehler

| Fehler | Ursache | Lösung |
|---|---|---|
| Agent ändert Dateien außerhalb seines Bereichs | Falscher Agent aktiv | `Shift+Tab` zum richtigen Agenten wechseln |
| Agent schreibt nicht in das erwartete Verzeichnis | Permissions zu restriktiv | `opencode debug agents` prüfen, Permissions in `opencode.jsonc` kontrollieren |
| Agent kennt die API-Spec nicht | `docs/api/openapi.yaml` fehlt | Phase 2 (Architecture) zuerst abschließen |
| Tests schlagen fehl, Agent gibt auf | Kein Self-Correction-Loop | QA-Agent nutzt `{{TEST_RESULTS_JSON}}` — bei manueller Ausführung: Fehlerlog an OpenCode zurückgeben |
| Agent vergisst Kontext aus vorheriger Phase | Kein automatischer Kontext-Transfer | Artefakte (PRD, openapi.yaml, etc.) müssen vorhanden sein — der Agent liest sie beim Start |

---

## Schnellreferenz

```bash
# Phase 1: Anforderungen
opencode run --agent aisdlc-requirements "Erstelle PRD für TaskFlow"

# Phase 2: Architektur
opencode run --agent aisdlc-architecture "Entwirf die API für TaskFlow"

# Phase 3: Implementierung
opencode run --agent aisdlc-execution "Implementiere Task CRUD gemäß openapi.yaml"
opencode run --agent java-backend "Implementiere den OrderService"
opencode run --agent react-frontend "Erstelle die TaskList-Komponente"

# Phase 4: Testing/QA
opencode run --agent aisdlc-qa "Schreibe Tests für TaskService"

# Interaktiv: Agent wechseln
Shift+Tab                    # nächster Agent
Ctrl+X, dann a               # Agentenliste
/agents                      # alle Agents anzeigen

# Config ohne Neustart
opencode reload
```