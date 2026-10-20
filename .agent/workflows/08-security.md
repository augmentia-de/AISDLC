---
description: Phase 8 — Security and compliance scan
---

# Security Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
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

## Commit

```bash
git add -A .
git diff --cached --name-only -- tests/ docs/qa/ .agent/artifacts/ \
  | git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht" \
             -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/" \
             --pathspec-from-file=-
git restore --staged .
```

- Genau **ein** Commit pro Schritt. Wenn sich nichts geändert hat, kein Commit erzeugen.
- Nur die Pfade dieses Schritts committen — fremde Änderungen niemals stagen.
- Das Scope ist technisch erzwungen: committet werden nur Dateien, die im Pfad-Scope
  geaendert oder neu sind — fremde Aenderungen bleiben unangetastet.
- Der Commit-Scope laeuft ueber `--pathspec-from-file`, damit ein leerer oder noch nicht
  existierender Pfad den Commit nicht verhindert (git bricht sonst mit "pathspec did not
  match any files" ab und es wird gar nichts committet).
- Der Commit entsteht **vor** dem Gate: das Gate prüft den fertigen Commit.
- Kein `git push` — die Pipeline pusht nicht.
- Konvention: `.agent/PHASES.md` → "Commit pro Schritt".

## HARD GATE
**After Security is complete, stop and await human approval before Deploy Agent runs.**

Prüfen: 0 Critical, 0 High. Bei Vulnerabilities → Rücksprung zu Phase 6.