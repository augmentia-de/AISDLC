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
| 1 Anforderungserhebung | `01-requirements.md` | `phase1` | `aisdlc-requirements` | — | — |
| 1a Anforderungs-Grill-Me | (in 01) | `phase1` | `aisdlc-requirements` | `aisdlc-grill-stress` | — |
| 2 Konzept & Architektur | `02-architecture.md` | `phase2` | `aisdlc-architecture` | — | **GATE 1** danach |
| 3 Simplify & Reduktion | `03-simplify.md` | `phase2` | `aisdlc-architecture` | `aisdlc-simplify` | — |
| 4 Grill-Me-Stresstest | `04-grill-me-stress.md` | `phase2` | `aisdlc-architecture` | `aisdlc-grill-stress` | — |
| 5 Test-Driven Setup | `05-tdd.md` | `phase3` | `aisdlc-execution` | `aisdlc-tdd` | — |
| 6 KI-Code-Generation | `06-implementation.md` | `phase3` | `aisdlc-execution` | — | — |
| 7 Refactoring & Clean-up | `07-refactoring.md` | `phase3` | `aisdlc-execution` | `aisdlc-simplify` | — |
| 8 Sicherheit & Compliance | `08-security.md` | `phase4` | `aisdlc-qa` | `aisdlc-security-scan` | **GATE 2** danach |
| 9 Deployment & Betrieb | `09-deploy.md` | `phase4` | `aisdlc-qa` | `aisdlc-deploy` | — |

**Die 5 Skills sind Verfahrens-Schritte, keine Rollen.** Sie werden vom zuständigen Agent
geladen, wenn der Schritt ansteht — dadurch entstehen keine zusätzlichen Agents und die
Kontextkosten bleiben bei 0, bis der Schritt wirklich gebraucht wird.

## Gate-Definition

| Gate | Nach Schritt | Bedingung | Wirkung |
|---|---|---|---|
| **GATE 1** | 2 (Architektur) | PRD vollständig, `openapi.yaml` stabil, ADRs ok | Kein Anwendungscode vor menschlicher Freigabe |
| **GATE 2** | 8 (Security) | QA 0 Critical, Security 0 Critical/High | Kein Deployment vor menschlicher Freigabe |

Gates stehen in `AGENTS.md` und werden von den Workflow-Dateien referenziert. Die
Multi-Instanz-Prompts enthalten dieselben Gates an denselben Schrittgrenzen.

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
