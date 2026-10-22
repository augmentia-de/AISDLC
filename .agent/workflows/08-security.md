---
description: Phase 8 — Security and compliance scan
---

# Security Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/briefing.md` **first** — it names the files this step needs. Read only those, not the whole repository.
- All build phases (6–7) must be complete.
- This agent is **read-only** — do NOT modify any source code.

## Checks

1. **Authentication**: All routes require valid JWT. Test with missing auth header → 401.
2. **Secrets exposure**: Grep for API keys, tokens, passwords in source. Verify `.env` in `.gitignore`.
3. **SQL injection**: Verify parameterized queries (ORM). No string concatenation in SQL.
4. **XSS**: Verify frontend sanitizes input. Check for `dangerouslySetInnerHTML`.
5. **Docker security**: Containers run as non-root, specific base image tags, no unnecessary ports.
6. **OWASP Top 10** general scan.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-qa` |
| Instanz (Multi-Instanz-Modus) | `phase4` |
| Skill | `aisdlc-security-scan` |

## Output
`.agent/artifacts/security-audit.md` with findings rated: Critical / High / Medium / Low.

## Regeln

- **No output commands.** `echo`, `printf`, `pwd`, `cat` for orientation produce no result — each costs a model turn and a permission check. Use `read`, `glob`, `grep`.
- **The contract `docs/api/openapi.yaml` is frozen** (since step 4). A contract defect is a finding for a jump back to step 2, not an edit in passing.

## Commit

```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht" \
  -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
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

## Traceability als Gate-Voraussetzung

Vor dem Gate laeuft der Check — **0 Luecken** sind Bedingung:

```bash
python3 .agent/tools/traceability_check.py
```

Eine Luecke heisst: eine Anforderung mit Story hat keinen belegten Test. Der Check
vergleicht die Artefakte mit dem Dateisystem — eine gefuellte Matrix ist kein Beleg,
die existierende Datei ist es.

## HARD GATE
**After Security is complete, stop and await human approval before Deploy Agent runs.**

Prüfen: 0 Critical, 0 High. Bei Vulnerabilities → Rücksprung zu Phase 6.