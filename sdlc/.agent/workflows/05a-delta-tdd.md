---
description: Phase 5a — Delta TDD (only affected tests + regression cases)
---

# Delta TDD Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- `docs/execution/change-brief-<id>.md` exists.
- `affected_paths` im Briefing gesetzt.

## Agent & Skill

- **Agent:** `aisdlc-execution`
- **Skill:** `aisdlc-tdd`

## Steps

1. **Betroffene Tests identifizieren** — grep nach Aufrufern/Imports der geänderten Dateien.
2. **Regressionstests** — mindestens ein bestehendes Szenario, das potenziell betroffen war.
3. **Neue Tests** — für die neuen ACs aus dem Change-Brief.
4. **Failing tests first** — Tests schreiben, dann Code anpassen (TDD).
5. **Traceability (light)** — nur geänderte ACs müssen Beleg haben.

## Output

- Tests in `tests/` (nur betroffene Module + neue).
- `.agent/artifacts/test-report.md` (Delta-Scope).

## Regeln

- Keine Vollteststrategie, keine komplette Testmatrix.
- Kein Ändern von `openapi.yaml` oder externen/public Verträgen.
- Regressionstest gegen mindestens ein bestehendes Szenario Pflicht.

## Commit

```bash
mkdir -p tests/ .agent/artifacts/
git reset -q
git add -A -- tests/ .agent/artifacts/
git commit -m "test(sdlc-fix): Delta-Tests und Regressionsfälle" \
  -m "Schritt 5a - Delta TDD | Agent: aisdlc-execution | Artefakte: tests/, .agent/artifacts/"
```

- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).