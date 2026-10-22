---
name: aisdlc-prd-to-stories
description: Treibt Phase 1: aus einem unscharfen Auftrag ein PRD, User Stories mit Akzeptanzkriterien, Gherkin-Features und eine Traceability-Matrix machen. Sagt, WANN und WELCHE Artefakte entstehen und wie die Akzeptanzkriterien beschaffen sein muessen. Nutze diesen Skill in Phase 1 (Anforderungserhebung) direkt nach dem Grill-Me.
metadata:
  origin: eigenes Framework-Verfahren; Entwurf aus example/skills-to-create/aisdlc-prd-to-stories
---

## Wann

Phase 1, **nach** dem Grill-Me. Ist die Anforderung noch mehrdeutig, erst
`aisdlc-grill-stress` — dieser Skill setzt eine *klare* Anforderung voraus.

## Das eiserne Gesetz

```text
KEINE STORY OHNE AKZEPTANZKITERIUM
```

Ein Akzeptanzkriterium muss **beobachtbar** sein. "Das System ist schnell" ist kein
Kriterium. "Die Liste rendert in unter 2 s (p95)" ist eins.

## Vorgehen

1. **Faehigkeiten extrahieren** — den Auftrag in Epics gruppieren. Ein Epic = ein
   zusammenhaengendes Ziel.
2. **Stories schreiben** — *Als <Rolle> moechte ich <Faehigkeit>, damit <Nutzen>.*
   Eine Story so klein, dass sie in einem Durchgang gebaut und getestet werden kann.
3. **Akzeptanzkriterien** — je Story mindestens eins, beobachtbar und messbar.
4. **BDD-Features** — eine `.feature` je Story mit `Given/When/Then`:
   der Normalfall, der Verweigerungsfall, der Grenzfall.
   **Jedes Szenario trägt die Kennung seines Akzeptanzkriteriums** als Kommentar:

   ```gherkin
   # Story: US-003 — Secret rotieren
   # Anforderungen: FR-003

   Feature: Secret rotieren

     Scenario: Rotation  # AC-003-1
       Given ein Endpunkt mit Secret S1
       When der Empfänger-Admin rotiert auf Secret S2
       Then verwendet der Dienst ab dem nächsten Zustellversuch ausschließlich S2
   ```

   Die Kennung `AC-<story>-<nr>` steht damit an **drei** Orten: in der Story-JSON
   (`"id"`), im Feature-Szenario (Kommentar) und später im Testfall. Nur so ist die
   Zuordnung Test → Anforderung **mechanisch** — der Traceability-Check liest die
   Kennung aus dem Test und findet die Anforderung über die Story. Ohne sie muss
   zugeordnet werden, und das wird geraten.
5. **Nicht-funktionale Anforderungen** — Performance, Sicherheit, Datenschutz,
   Barrierefreiheit, Verfuegbarkeit. Je eine **messbare** Zielgroesse.
6. **Risiken** — nur Risiken, die eine **Entscheidung** aendern. Je ein Gegenmass, das
   in einer Anforderung verankert ist.
7. **Traceability-Seed** — jede Story nennt die Anforderungs-IDs, die sie abdeckt.

## Output

```
docs/requirements/PRD.md                    # Vision, Personas, Ziele, KPIs, MoSCoW
docs/requirements/stories/<story_id>.json    # eine Datei je Story
docs/requirements/features/<story_id>.feature  # Gherkin
docs/requirements/traceability_matrix.json   # Anforderung -> Story -> Test
.agent/artifacts/requirements.md             # kompakter Katalog
.agent/artifacts/user-stories.md             # lesbare Fassung
.agent/artifacts/risk-register.md            # Top-Risiken
```

Story-Schema:

```json
{
  "id": "US-003",
  "epic": "E1",
  "title": "Token-Lebenszyklus",
  "description": "As a developer, I want ...",
  "requirement_ids": ["FR-007"],
  "acceptance_criteria": [
    { "id": "AC-003-1", "given": "...", "when": "...", "then": "..." }
  ],
  "priority": "must-have",
  "status": "todo"
}
```

## Regeln

- **Jedes** Akzeptanzkriterium hat eine Kennung `AC-<story>-<nr>`, und **jedes**
  Feature-Szenario trägt sie. Ein Szenario ohne Kennung ist am Ende nicht belegbar.
- Jede Story hat mindestens ein Akzeptanzkriterium; jedes Kriterium ist beobachtbar.
- Eine Story haengt nicht an einer anderen unfertigen Story, ohne das zu sagen.
- Was nicht in den Scope gehoert, landet in einem ausdruecklichen **"won't have"** —
  nicht im Backlog.
- Fehlt eine Entscheidung, wird **eskaliert**, nicht erfunden.
- Nach dem Schreiben gilt `aisdlc-traceability-check`.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| Mehrdeutige Anforderung aufklaeren | `intent-driven-development` |
| Story-Lifecycle, Epic-zu-Sprint | `story-lifecycle` |
