---
description: Phase 8a — Delta QA (affected modules + diff-based security)
---

# Delta QA Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.md` first.
- Code-Änderungen aus 06a abgeschlossen.

## Agent & Skill

- **Agent:** `aisdlc-qa`
- **Skill:** `aisdlc-security-scan`

## Steps

1. **Betroffene Module scannen** — nicht Vollscan.
2. **Secrets/Dependency-Check** auf Diff-Basis.
3. **Traceability (light)** — nur geänderte ACs müssen Beleg haben.
4. **Regressionstests** ausführen (aus 05a).
5. **Kurzbericht** in `docs/qa/`.

## Output

- `docs/qa/delta-report.md`
- `.agent/artifacts/security-audit.md` (Delta-Scope)

## Regeln

- Kein Vollscan, keine vollständige Compliance-Dokumentation (MVP).
- Bei Critical/High → Rücksprung zu 06a.
- Bei `contract_impact` `external/public` → voller Security-Scan Pflicht.

## Commit

```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-fix): Delta-QA und Security-Bericht" \
  -m "Schritt 8a - Delta QA | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
```