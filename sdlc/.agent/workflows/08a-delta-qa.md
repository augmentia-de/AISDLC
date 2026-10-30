---
description: Phase 8a — Delta QA (affected modules + diff-based security)
---

# Delta QA workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Code changes from 06a completed.

## Agent & Skill

- **Agent:** `aisdlc-qa`
- **Skill:** `aisdlc-security-scan`

##Steps

1. **Scan affected modules** — not full scan.
2. **Secrets/Dependency Check** on a diff basis.
3. **Traceability (light)** — only changed ACs must have evidence.
4. Run **regression tests** (from 05a).
5. **Short report** in `docs/qa/`.

## Output

- `docs/qa/delta-report.md`
- `.agent/artifacts/security-audit.md` (Delta scope)

##Rules

- No full scan, no full compliance documentation (MVP).
- At Critical/High → jump back to 06a.
- For `contract_impact` `external/public` → full security scan mandatory.

## Commit
```
bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-fix): Delta-QA und Security-Bericht" \
  -m "Schritt 8a - Delta QA | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
```