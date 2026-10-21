# Example: TaskFlow — the reference application

This folder documents the **reference application** that proves the AISDLC pipeline works
end to end, and lists the skills that are still missing for it.

| File | Content |
|---|---|
| `taskflow.md` | The application itself: scope, users, features, data model, stack |
| `steps.md` | Per SDLC step: inputs, artifacts produced, skills used — and skills missing |
| `skills-to-create/` | Example `SKILL.md` drafts for the missing skills (ready to drop into `.opencode/skills/`) |

## Status der Drafts — alle installiert

Die Entwurfsskripte in `skills-to-create/` sind **Ausgangsfassungen in Englisch**.
Alle sind inzwischen installiert — als deutsche Verfahrens-Skills in
`.opencode/skills/`, jeweils mit einem Verweis auf den Stack-Skill, der das
Handwerk liefert.

| Draft | Installiert als | Handwerk kommt aus |
|---|---|---|
| `aisdlc-openapi-first` | `.opencode/skills/aisdlc-openapi-first/` | `api-design`, `api-design-principles` |
| `aisdlc-traceability` | `.opencode/skills/aisdlc-traceability-check/` (neu geschrieben) | `.agent/tools/traceability_check.py` |
| `aisdlc-prd-to-stories` | `.opencode/skills/aisdlc-prd-to-stories/` | `intent-driven-development`, `story-lifecycle` |
| `aisdlc-glossary-adr` | `.opencode/skills/aisdlc-glossary-adr/` | `architecture-decision-records` |
| `aisdlc-migration-plan` | `.opencode/skills/aisdlc-migration-plan/` | `database-migrations`, `postgresql-table-design` |
| `aisdlc-test-matrix` | `.opencode/skills/aisdlc-test-matrix/` | `springboot-tdd`, `e2e-testing` |
| `aisdlc-feature-scaffold` | `.opencode/skills/aisdlc-feature-scaffold/` | `contract-first`, `springboot-patterns` |
| `aisdlc-containerize` | `.opencode/skills/aisdlc-containerize/` | `docker-patterns` |
| `aisdlc-ci-pipeline` | `.opencode/skills/aisdlc-ci-pipeline/` | `deployment-patterns`, `github-actions-templates` |

**Die Trennung:** ein `aisdlc-*`-Skill liefert das **Verfahren** (wann, welche
Artefakte, welche Prüfungen). Der Stack-Skill liefert das **Handwerk** (wie man es
baut). Die Entwurfsskripte hier waren 50-zeilige Eigenbauten, wo die
Bibliotheks-Skills 200-500 Zeilen Handwerk liefern — deshalb sind sie jetzt
Verweise statt Duplikate.

## How to use this folder

1. **Read `taskflow.md`** to understand what the reference app is.
2. **Walk `steps.md`** — it is the concrete runbook for building TaskFlow with the AISDLC
   framework: which artifact lands where after each step, which skill does the work.
3. **Die Skills sind bereits installiert.** Neu erzeugen lassen sich die
   Stack-Skills mit:

   ```bash
   python3 .agent/tools/vendor_skills.py --libs <bibliotheken> --out skills
   ```

## Scope note

`skills-to-create/` contains **drafts**, not installed skills. They are deliberately
stack-agnostic: they describe the procedure, the artifacts and the acceptance criteria, and
leave the language-specific parts to the stack skills that come from the libraries
(`springboot-*`, `react-*`, …).

The phase-level skills that already exist stay authoritative for the phase procedure:

| Existing skill | Phase |
|---|---|
| `aisdlc-grill-stress` | 1a, 4 |
| `aisdlc-simplify` | 3, 7 |
| `aisdlc-tdd` | 5 |
| `aisdlc-security-scan` | 8 |
| `aisdlc-deploy` | 9 |
