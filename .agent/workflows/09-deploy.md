---
description: Phase 9 — Deployment and operations
---

# Deploy Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- Read `.agent/artifacts/test-report.md` — must show zero Critical issues.
- Read `.agent/artifacts/security-audit.md` — must show zero Critical issues.
- Phase 8 HARD GATE must have been passed (human-approved).

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-qa` |
| Instanz (Multi-Instanz-Modus) | `phase4` |
| Skill | `aisdlc-deploy` |

## Steps

1. **Validate reports**: Confirm no Critical issues in QA and Security reports.
2. **Deploy to staging**: Use CI/CD pipeline.
3. **Smoke tests on staging**: Health endpoint, submit test brief, verify PDF download.
4. **Promote to production**: If smoke tests pass.
5. **Produce deploy report** (`.agent/artifacts/deploy-report.md`):
   - Staging URL, Production URL, Smoke test results (pass/fail)

## Output
`.agent/artifacts/deploy-report.md`

## Regeln

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.
- **The contract `docs/api/openapi.yaml` is frozen** (since step 4). A contract defect is a finding for a jump back to step 2, not an edit in passing.

## Commit

```bash
mkdir -p docs/execution/ infra/ .github/ .agent/artifacts/
git reset -q
git add -A -- docs/execution/ infra/ .github/ .agent/artifacts/
git commit -m "build(sdlc-9): Deployment-Pipeline und Deploy-Report" \
  -m "Schritt 9 - Deployment & Betrieb | Agent: aisdlc-qa | Artefakte: docs/execution/, infra/, .github/, .agent/artifacts/"
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das Scope ist technisch erzwungen: es wird nur der Schritt-Scope gestaged, alles
  andere bleibt unangetastet.
- Das Scope wird ueber den **Index** gesetzt, nicht ueber eine Pfadspezifikation:
  `git reset -q` leert den Index, `git add -A -- <pfade>` staged nur den Schritt,
  `git commit` committet nur den Index.
  Grund: `git commit -- <pfade>` bricht ab, sobald ein Scope-Pfad keine getrackten
  Dateien hat ("pathspec did not match any files") — und der Weg ueber
  `--pathspec-from-file` ist unter dem globalen `rtk`-Plugin kaputt: `rtk` rewritet
  `git diff` und haengt einen "Changes:"-Block an, der als Pfadspezifikation
  wiederum mit "empty string is not a valid pathspec" abbricht.
- `git reset -q` unstaged fremde Arbeit, loescht sie aber nicht.
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## Gate
Bei Produktions-Incidents → Rücksprung zu Phase 2.