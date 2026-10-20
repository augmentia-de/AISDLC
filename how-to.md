# How-To: Wann welcher Agent und welcher Skill aktiv sein sollte

## Übersicht

Das Projekt hat **6 Primary-Agents** und **7 Skills**. Die Agenten sind *Rollen*, die Skills
sind *Verfahrens-Schritte*. Pro Schritt ist **genau ein** Agent der Haupt-Agent; Skills
werden zusätzlich geladen, wenn der Schritt sie braucht.

| Ebene | Umfang | Ort |
|---|---|---|
| **Schritt** | 9 Arbeits-Schritte (01–09) | `.agent/workflows/` |
| **Instanz** | 5 Orchestrator-Prozesse (phase0–4) | `.agent/prompts/multi-instance/` |
| **Agent** | 6 Rollen | `.opencode/agents/` |
| **Skill** | 7 Verfahrens-Schritte | `.opencode/skills/` |

**Single Source of Truth:** `.agent/PHASES.md` (Schritt → Instanz → Agent → Skill → Gate).

## Die 6 Agents

| Agent-ID | Rolle | Default? | Schreibt in |
|---|---|---|---|
| `aisdlc-requirements` | Requirements Orchestrator (Schritt 1 + 1a) | **ja** | `docs/requirements/` |
| `aisdlc-architecture` | Architecture Orchestrator (Schritt 2–4) | nein | `docs/architecture/`, `docs/api/`, `docs/adr/` |
| `aisdlc-execution` | Execution Orchestrator (Schritt 5–7) | nein | `src/`, `tests/`, `docs/execution/` |
| `aisdlc-qa` | QA & Compliance Orchestrator (Schritt 8–9) | nein | `tests/`, `docs/qa/` |
| `stack/java/backend` | Java/Spring-Backend-Implementierung | nein | `src/`, `tests/`, `docs/execution/` |
| `stack/react/frontend` | React/TypeScript-Frontend-Implementierung | nein | `src/`, `tests/`, `docs/execution/` |
| *(Original)* `build` | Generalist | nein | überall |

**Default-Agent:** `aisdlc-requirements` — eine neue Session startet in Schritt 1.

## Die 7 Skills

| Skill | Wann | Herkunft |
|---|---|---|
| `aisdlc-grill-stress` | Schritt 1a + 4 — Designbaum, Deepening, Standards/Spec als zwei Achsen | mattpocock (grilling, codebase-design), wshobson |
| `aisdlc-simplify` | Schritt 3 + 7 — verhaltenserhaltend vereinfachen, Dead Code, Schulden | ECC (code-simplifier, refactor-cleaner), wshobson |
| `aisdlc-tdd` | Schritt 5 — RED/GREEN/REFACTOR, Failure-Paths, Workflow-Invarianten | superpowers, ECC, nnennandukwe |
| `aisdlc-security-scan` | Schritt 8 — read-only Audit, OWASP Top 10 | ECC, wshobson |
| `aisdlc-deploy` | Schritt 9 — Pipeline, Rollout, Validierung, Smoke, SLO, Runbook | wshobson, ECC |
| `strangler-fig` | Legacy-Modul-Migration entwerfen | eigen |
| `module-migration` | Legacy-Modul umsetzen (Characterization Tests) | eigen |
| `test-self-correction` | Fehlgeschlagene Tests reparieren | eigen |

**Warum Skills statt eigener Agents:** Simplify, TDD, Security und Deploy sind *Verfahren*,
keine Rollen. Als Skill kostet der Schritt 0 Token, bis er gebraucht wird, und der
zuständige Agent bleibt der einzige Schreiber.

---

## Schritt-für-Schritt

### Schritt 1 — Anforderungserhebung

**Agent:** `aisdlc-requirements` (Default) · **Instanz:** `phase1` · **Skill:** `aisdlc-grill-stress` (1a)

Erstellt PRD, User Stories, `.feature`-Dateien, Traceability Matrix, Risk Register.

**Wechsel zu Schritt 2**, wenn PRD, Stories und Acceptance Criteria vollständig und geprüft sind.
**Eskalation:** `REQUIREMENT_GAP` → zurück zu Schritt 1.

### Schritt 2 — Konzept & Architektur-Design

**Agent:** `aisdlc-architecture` · **Instanz:** `phase2` · **Skill:** keiner

Erstellt `docs/api/openapi.yaml`, C4-Diagramme, ADRs, Domain-Modell.

**🚧 GATE 1** — danach anhalten, Zusammenfassung zeigen, **menschliche Freigabe abwarten**.

### Schritt 3 — Simplify & Reduktion

**Agent:** `aisdlc-architecture` · **Instanz:** `phase2` · **Skill:** `aisdlc-simplify`

Überdimensionierte Teile der Architektur reduzieren, **bevor** der Stresstest läuft.

### Schritt 4 — Grill-Me-Stresstest

**Agent:** `aisdlc-architecture` · **Instanz:** `phase2` · **Skill:** `aisdlc-grill-stress`

Architektur stress-testen: Designbaum abfragen, Deepening-Opportunitäten finden,
Standards- und Spec-Achse getrennt prüfen.

**Eskalation:** Architekturfehler → zurück zu Schritt 2.

### Schritt 5 — Test-Driven Setup

**Agent:** `aisdlc-execution` · **Instanz:** `phase3` · **Skill:** `aisdlc-tdd`

Teststrategie, Testebenen, Failure-Paths, Workflow-Invarianten.

### Schritt 6 — KI-Code-Generation

**Agent:** `aisdlc-execution` · **Instanz:** `phase3` · **Skill:** keiner

Parallele Build-Streams (Backend / Frontend / Infra), synchronisiert über `openapi.yaml`.

**Eskalation:** fehlende Tests → zurück zu Schritt 5.

### Schritt 7 — Code-Refactoring & Clean-up

**Agent:** `aisdlc-execution` · **Instanz:** `phase3` · **Skill:** `aisdlc-simplify`

**Zweiter Simplify-Durchlauf** auf dem fertigen Code: jetzt sieht man defensiven Code,
der durch Tests ersetzt wurde, und Abstraktionen, die sich nicht bewährt haben.

**Eskalation:** unsauberer Code → zurück zu Schritt 6.

### Schritt 8 — Sicherheit & Compliance Scan

**Agent:** `aisdlc-qa` · **Instanz:** `phase4` · **Skill:** `aisdlc-security-scan`

Read-only Audit: AuthN/AuthZ, Secrets, Injection, XSS, Container, Abhängigkeiten, OWASP Top 10.

**🚧 GATE 2** — danach anhalten, Befunde zeigen, **menschliche Freigabe abwarten**.

### Schritt 9 — Deployment & Betrieb

**Agent:** `aisdlc-qa` · **Instanz:** `phase4` · **Skill:** `aisdlc-deploy`

Pipeline, Rollout-Strategie, Pre-Flight, Smoke-Tests, Promotion, Rollback, SLOs, Runbook.

**Eskalation:** Produktions-Incident → zurück zu Schritt 2.

---

## Commit pro Schritt

**Jeder Schritt erzeugt genau einen Commit.** Ein Schritt ist erst abgeschlossen, wenn sein
Commit existiert. Der Commit entsteht **vor** dem Gate — das Gate prüft den fertigen Commit.

```bash
git add -A .
git diff --cached --name-only -- docs/requirements/ .agent/artifacts/ \
  | git commit -m "docs(sdlc-1): PRD, User Stories und Traceability-Matrix" \
             -m "Schritt 1 - Anforderungserhebung | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/" \
             --pathspec-from-file=-
git restore --staged .
```

| Regel | Bedeutung |
|---|---|
| Subject | `<type>(sdlc-<n>): <Kurzbeschreibung>` |
| Body | `Schritt <n> - <Schrittname> \| Agent: <agent-id> \| Artefakte: <pfade>` |
| Scope | nur die geänderten/neuen Dateien des Schritts — technisch erzwungen |
| Reihenfolge | Commit vor dem Gate bzw. vor dem Handoff |
| Leer | nichts geändert → kein Commit, keine Leer-Commits |
| Push | die Pipeline pusht nie — `git push` bleibt menschliche Entscheidung |

Das Scope ist kein blosses Dokumentations-Versprechen: committet werden nur Dateien, die
im Pfad-Scope geändert oder neu sind. Die Form mit `--pathspec-from-file` ist nötig, weil
`git commit -- <pfade>` abbricht, sobald ein Scope-Pfad leer oder noch nicht vorhanden ist
— dann würde gar nichts committet.

Subject und Pfade pro Schritt: `.agent/PHASES.md` → "Commit pro Schritt".

---

## Regeln für den Agenten-Wechsel

### 1. Schritt-Reihenfolge einhalten

Die Schritte bauen aufeinander auf. Nicht Schritt 6 starten, bevor Schritt 2 freigegeben ist.

### 2. Agenten nicht mischen

Pro Schritt ist **genau ein** Agent der Haupt-Agent. Zwei Agents mit verschiedenen
Schreibrechten gleichzeitig aktiv führen zu widersprüchlichen Rechten.

### 3. Wechseln ohne Neustart

```bash
Tab                               # nächster Agent (vorwärts)
Shift+Tab                         # vorheriger Agent (rückwärts)
Ctrl+X, dann a                   # Agentenliste (oder /agents)
opencode run --agent <agent-id> "Auftrag"
opencode reload                  # Config ohne Neustart übernehmen
```

`default_agent` in `.opencode/opencode.jsonc` wirkt **nur für neue Sessions** — eine
bestehende Session behält ihren gespeicherten Agenten.

### 3a. Wann wechseln? — nach der Freigabe, vor dem nächsten Schritt

```
Schritt N läuft  →  Agent committet  →  GATE: Artefakte vorstellen
                                                    │
                                     Freigabe?  ──────┴────── nein
                                        ja                      │
                                        ▼                       ▼
                              Agent auf Schritt N+1        Korrekturen im
                              wechseln, dann Schritt N+1    selben Agenten
                              starten                       (bleibt aktiv)
```

**Nach der Freigabe wechseln, nicht vorher.** Grund:

- Der Gate-Checkpoint gehört zu dem gerade abgeschlossenen Schritt. Der Agent, der den
  Schritt geliefert hat, bleibt aktiv, bis du entschieden hast — Rückfragen und
  Korrekturen landen bei demselben Agenten mit demselben Schreibrecht.
- Vor der Freigabe zu wechseln nimmt dem Requirements-Agenten das Recht auf
  `docs/requirements/`. Eine Korrektur an der PRD wäre dann blockiert.
- Erst der Agent des nächsten Schritts darf aktiv sein, wenn dieser Schritt beginnt —
  `aisdlc-architecture` darf nur `docs/architecture/`, `docs/api/`, `docs/adr/` schreiben.

**Kontext:** Es gibt keinen automatischen Kontext-Transfer zwischen den Schritten — das
ist gewollt. Der neue Agent liest die Artefakte vom Dateisystem (PRD, `openapi.yaml`,
`.agent/artifacts/`). Deshalb ist der Commit **vor** dem Gate wichtig: der neue Agent
arbeitet auf einem versionierten, sauberen Stand.

### 4. Skills nachladen, nicht improvisieren

Wenn ein Schritt ein Skill hat, dieses Skill laden statt das Verfahren zu improvisieren:

```text
Nutze das Skill aisdlc-security-scan für den Security-Audit.
```

### 5. Subagenten bei Bedarf delegieren

```text
Nutze den stack/java/backend-Subagenten, um die Persistenzschicht zu prüfen.
```

Subagenten laufen in eigener Kind-Session mit frischem Kontext — das Ergebnis kommt als
Zusammenfassung zurück.

---

## Stack-Ebene

Stack-Wissen kommt **nicht** aus eigenen Dateien, sondern per `skills.paths` direkt aus den
Bibliotheken. Kein Kopieren, keine Drift:

```jsonc
"skills": { "paths": ["/…/ECC/skills", "/…/wshobson-agents/plugins/backend-development/skills", …] }
```

| Stack | Verfügbar über | ≈ Token Beschreibungen |
|---|---|---|
| Java + Spring | `stack/java/backend` + `springboot-*`, `java-*` | 342 |
| React / TypeScript | `stack/react/frontend` + `react-*`, `typescript-*` | 1.029 |
| Python, Go, Rust, .NET | Stack-Agent anlegen + `python-*` usw. | ≈ 2.281 gesamt |

**Eigenen Stack-Agent anlegen:**

```bash
mkdir -p .opencode/agents/stack/python
# .opencode/agents/stack/python/backend.md anlegen  →  ID: stack/python/backend
```

Dann in `opencode.jsonc` erlauben: `{ "action": "subagent", "resource": "stack/*", "effect": "allow" }`
(der `stack/*`-Eintrag deckt alle Stacks ab).

**Stack wechseln:** `default_agent` in `.opencode/opencode.jsonc` umsetzen, z. B. von
`aisdlc-requirements` auf `stack/java/backend`, und `opencode reload` ausführen.

---

## Berechtigungen (wer darf was)

| Agent | Lesen | Schreiben | Shell |
|---|---|---|---|
| `aisdlc-requirements` | alles | `docs/requirements/` | ask |
| `aisdlc-architecture` | alles | `docs/architecture/`, `docs/api/`, `docs/adr/` | ask |
| `aisdlc-execution` | alles | `src/`, `tests/`, `docs/execution/` | ask |
| `aisdlc-qa` | alles | `tests/`, `docs/qa/` | ask |
| `stack/java/backend` | alles | `src/`, `tests/`, `docs/execution/` | ask |
| `stack/react/frontend` | alles | `src/`, `tests/`, `docs/execution/` | ask |
| `build` | alles | alles | ask (außer git status/diff/log) |

**Wichtig:** Ein Agent darf nur in seine zugewiesenen Verzeichnisse schreiben. Versuche
außerhalb werden blockiert. `build` bleibt bewusst allmächtig.

---

## Eskalation und Rücksprung

| Situation | Signal | Rücksprung zu |
|---|---|---|
| Unklare oder unvollständige Anforderung | `REQUIREMENT_GAP` | Schritt 1 |
| Undurchführbares Design | `ARCHITECTURAL_BLOCKER` | Schritt 2 |
| Fehlende Tests bei Implementierung | (implizit) | Schritt 5 |
| Unsauberer Code | (implizit) | Schritt 7 |
| Security-Vulnerability | (implizit) | Schritt 6 |
| Produktions-Incident | (implizit) | Schritt 2 |

---

## Typische Fehler

| Fehler | Ursache | Lösung |
|---|---|---|
| Agent ändert Dateien außerhalb seines Bereichs | Falscher Agent aktiv | `Shift+Tab` zum richtigen Agenten wechseln |
| Agent schreibt nicht in das erwartete Verzeichnis | Permissions zu restriktiv | `opencode debug agents` prüfen, Permissions in `opencode.jsonc` kontrollieren |
| Stack-Skill wird nicht gefunden | `skills.paths` zu eng gefiltert | `skill`-Allowlist in `opencode.jsonc` um das Präfix erweitern |
| Agent kennt die API-Spec nicht | `docs/api/openapi.yaml` fehlt | Schritt 2 zuerst abschließen |
| Tests schlagen fehl, Agent gibt auf | Kein Self-Correction-Loop | `aisdlc-qa` nutzt `{{TEST_RESULTS_JSON}}` — bei manueller Ausführung: Fehlerlog an OpenCode zurückgeben |
| Agent vergisst Kontext aus vorherigem Schritt | Kein automatischer Kontext-Transfer | Artefakte (PRD, openapi.yaml) müssen vorhanden sein — der Agent liest sie beim Start |
| Falsche Workflow-Datei gewählt | Zwei Prompt-Familien | `.agent/PHASES.md` als Single Source of Truth nutzen |

---

## Schnellreferenz

```bash
# Phase starten (Default ist aisdlc-requirements)
opencode
opencode run --agent aisdlc-architecture "Entwirf die API für [Feature]"
opencode run --agent stack/java/backend "Implementiere [Task] gemäß openapi.yaml"
opencode run --agent stack/react/frontend "Baue [Komponente]"

# Agentenliste
/agents
Shift+Tab

# Skill manuell laden
Nutze das Skill aisdlc-deploy für das Deployment.

# Config neu laden
opencode reload

# Nachsehen, was geladen ist
opencode debug agents
```

| Datei | Inhalt |
|---|---|
| `AGENTS.md` | Regeln, Gates, Folder Ownership, Phase-Skills |
| `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate |
| `.agent/workflows/` | 9 Workflow-Dateien, je Schritt |
| `.agent/prompts/orchestrator/` | Prompts für den Ein-Orchestrator-Modus |
| `.agent/prompts/multi-instance/` | Prompts für den Server-Betrieb (0–4 Instanzen) |
| `.opencode/agents/` | 6 Primary-Agents |
| `.opencode/skills/` | 7 Skills |
| `how-to.md` | dieses Dokument |