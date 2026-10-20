# AI-Agent-gestützte Softwareentwicklung (SDLC)

Dieses Dokument bietet eine strukturierte Übersicht über die spezifischen Ergebnisse, deren Formate und die Weiterverwendung innerhalb eines KI-agenten- und OpenCode-gestützten Software-Entwicklungszyklus (SDLC).

---

## Übersicht der SDLC-Phasen & Artefakte

| SDLC-Schritt | Ergebnis (Output) | Form / Format | Nutzbarkeit & Verwendungsort |
| :--- | :--- | :--- | :--- |
| **Anforderungen & Analyse** | **PRD & Specs** | Markdown (`/docs/prd.md`), Confluence, Notion | Zielvorgabe für Architektur, Validierungsgrundlage für Product Owner und Orchestrator. |
| | **User Stories** | Tickets (Jira, GitHub Issues, Linear), JSON/YAML | Backlog-Items für den Orchestrator, direkte Aufgabenstellung für OpenCode. |
| | **Akzeptanzkriterien** | Gherkin Syntax (`Feature:` / `Scenario:`), Plain Text | Direct Input für BDD-Tools und zur automatisierten Testgenerierung im Testing-Schritt. |
| | **Requirement Traceability Matrix** | JSON / YAML / Tabelle | Zuordnung von Anforderungen zu Code-Modulen & Testfällen zur Compliance-Prüfung. |
| **Architektur & Systemdesign** | **Architekturdiagramme** | PlantUML, Mermaid.js, Structurizr (C4-Modell als Code) | In Repo eingebunden; dient OpenCode als Leitfaden zur Einhaltung von Modul- und Systemgrenzen. |
| | **API-Spezifikationen** | OpenAPI / Swagger 3.0+ (YAML/JSON) | Spezifikation zur automatischen Generierung von DTOs, API-Stubs, Mock-Servern & Integrationstests. |
| | **Architecture Decision Records (ADRs)** | Markdown (z. B. unter `docs/adr/0001-xxx.md`) | Kontextspeicher für den Orchestrator bei künftigen Refactorings oder Designentscheidungen. |
| **Implementierung & Codierung** | **Quellcode & Refactorings** | Programmiersprachen-Dateien (`.ts`, `.py`, `.go`, etc.) | Kompiliert/Interpretiert durch Build-Tools, dient als Kontext für Linter, LSP und OpenCode. |
| | **Pull Requests (PRs)** | Git Commits & Markdown PR-Description | Übergabepunkt an den Orchestrator/Mensch für automatisierte Sanity-Checks und CI/CD-Pipelines. |
| | **Inline-Doku & Change Logs** | JSDoc/Docstrings & `CHANGELOG.md` | Entwickler-Dokumentation sowie Input für automatisierte Release Notes und API-Referenzen. |
| **Testing & Qualitätssicherung** | **Test-Suiten** | Code-Dateien (`*.spec.ts`, `test_*.py`, etc.) | Automatische Ausführung in CI/CD-Pipelines & lokale Regressionstests vor PR-Merge. |
| | **Coverage Reports** | HTML, XML, LCOV | Qualitätsschranke (Quality Gate) für den Orchestrator zur automatischen Freigabe/Ablehnung. |
| | **Bug-Reports & Trace Logs** | Structured JSON, GitHub Issues mit Stacktrace | Direkter Re-Input für OpenCode zur automatisierten Fehlerbehebung und Self-Correction. |

---

## Detaillierte Phasenaufschlüsselung

### 1. Anforderungen & Analyse
In dieser Phase übersetzt der Orchestrator-Agent unstrukturierte Idee-Prompts in maschinen- und menschenlesbare Spezifikationen.

* **Product Requirement Documents (PRDs):**
  * *Format:* Markdown Datei im Repository (z. B. `docs/requirements/PRD.md`).
  * *Nutzung:* Dient als "Single Source of Truth" für das Gesamtsystem.
* **User Stories & Akzeptanzkriterien:**
  * *Format:* Gherkin (`.feature`) oder strukturiertes JSON/YAML.
  * *Nutzung:* Wird in kleine Häppchen zerlegt und einzeln an OpenCode als Task übergeben.
* **Traceability Matrix:**
  * *Format:* YAML/JSON Mapping (`requirement_id` $ightarrow$ `test_id` $ightarrow$ `code_file`).
  * *Nutzung:* Der Orchestrator prüft vor Freigabe, ob jede Anforderung durch mindestens einen Test abgedeckt ist.

---

### 2. Architektur & Systemdesign
Bevor OpenCode Zeile für Zeile implementiert, definiert der Orchestrator die strukturellen Leitplanken.

* **Architekturdiagramme (Diagrams-as-Code):**
  * *Format:* Mermaid.js (`.mmd`) oder PlantUML (`.puml`).
  * *Nutzung:* Kann direkt in Markdown-Docs gerendert werden; wird dem OpenCode-Kontext beigelegt, um Architekturverletzungen zu vermeiden.
* **API-Spezifikationen:**
  * *Format:* OpenAPI Specification (OAS) in `openapi.yaml`.
  * *Nutzung:* OpenCode nutzt diese Datei, um Controller, Routen und Typendefinitionen automatisch zu generieren.
* **Architecture Decision Records (ADRs):**
  * *Format:* Standardisierte Markdown-Dateien nach Nygard-Schema.
  * *Nutzung:* Verhindert, dass KI-Agenten in späteren Iterationen bereits verworfene Architekturentscheidungen erneut vorschlagen.

---

### 3. Implementierung & Codierung
Hier übernimmt OpenCode die Ausführung unter ständiger Kontrolle und Steuerung durch den Orchestrator.

* **Quellcode:**
  * *Format:* Native Code-Dateien im Repository.
  * *Nutzung:* Produktives System, Ausführung von Sprachtools (LSP, Linter).
* **Pull Requests & Commits:**
  * *Format:* Git Commits nach Conventional Commits Schema (`feat:`, `fix:`, `refactor:`) und strukturierte PR-Descriptions.
  * *Nutzung:* Dient der Historie und stellt den Prüfstein für das finale menschliche Review dar.
* **Inline-Dokumentation:**
  * *Format:* Sprachspezifische Doc-Kommentare (Typen, Parameter, Return Values).
  * *Nutzung:* Verbessert das Verständnis der Codebasis für OpenCode bei nachfolgenden Aufgaben.

---

### 4. Testing & Qualitätssicherung
Der Feedback-Loop zwischen Testausführung und OpenCode stellt die funktionale Korrektheit sicher.

* **Test-Suiten (Unit, Integration, E2E):**
  * *Format:* Ausführbare Testskripte (z. B. Jest, PyTest, Playwright).
  * *Nutzung:* Werden vom Orchestrator nach jeder OpenCode-Änderung gestartet.
* **Coverage Reports & Linter Logs:**
  * *Format:* LCOV, JUnit XML, JSON Summary.
  * *Nutzung:* Bei Unterschreitung von Schwellenwerten (z. B. < 80% Coverage) stößt der Orchestrator automatisch eine Nachbesserung durch OpenCode an.
* **Automatisierte Bug-Reports:**
  * *Format:* JSON-Struktur mit File Path, Line Number, Expected vs. Actual, Stacktrace.
  * *Nutzung:* Wird direkt als Reparatur-Task in den Prompt von OpenCode eingespeist (`Self-Correction Loop`).
