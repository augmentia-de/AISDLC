---
description: Phase 8 — Security and compliance scan
---

# Security Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Read your phase prompt: `.agent/prompts/orchestrator/04_orchestrator_testing.md`.

## Checks

1. **Authentication**: All routes require valid JWT. Test with missing auth header → 401.
2. **Secrets exposure**: Grep for API keys, tokens, passwords in source. Verify `.env` in `.gitignore`.
3. **SQL injection**: Verify parameterized queries (ORM). No string concatenation in SQL.
4. **XSS**: Verify frontend sanitizes input. Check for `dangerouslySetInnerHTML`.
5. **Docker security**: Containers run as non-root, specific base image tags, no unnecessary ports.
6. **OWASP Top 10** general scan.

## Agent & Skill

- **Agent:** `aisdlc-qa`  ·  **Skill:** `aisdlc-security-scan`


## Output
`.agent/artifacts/security-audit.md` with findings rated: Critical / High / Medium / Low.

## Regeln

Siehe `AGENTS.md`: keine Ausgabe-Kommandos, Vertrag nach Schritt 4 eingefroren.

## Commit

```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht" \
  -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
```

- Formel, Scope und Begruendung: `AGENTS.md` -> Commit Convention. Hier nur die Pfade.
- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).

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