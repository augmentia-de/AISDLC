---
description: Phase 1a — Change brief (delta flow for hotfix/patch/small feature)
---

# Change Brief Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- `.agent/config.json` has `change_flow.enforce_classification: true`.

## Agent & Skill

- **Agent:** `aisdlc-requirements` (or `aisdlc-execution` for pure technical fixes)
- **Skill:** (optional) `aisdlc-grill-stress` bei Unsicherheit

## Steps

1. **Problem** — Was ist falsch/fertig? Reproduktion oder Anforderung.
2. **Ist/Soll** — Kurz, keine volle PRD.
3. **Akzeptanzkriterien (minimal)** — 1–5 ACs, `AC-<id>-<nr>`.
4. **Betroffene Dateien** — `affected_paths` im Briefing.
5. **Regressionsrisiko** — Welche bestehenden Szenarien könnten brechen.
6. **Testplan** — Welche Tests müssen grün sein.

## Output

- `docs/execution/change-brief-<id>.md`
- Briefing mit `change`-Block aktualisiert.

## Regeln

- Kein volles PRD, keine Story-JSONs, keine Traceability-Matrix-Änderung (außer neue ACs).
- Wenn `contract_impact` `external/public` oder `openapi.yaml` betroffen → **kein Delta-Flow**, volle Phasen 1–9.
- Bei `breaking: true` → volle Phasen 1–9.

## Commit

```bash
mkdir -p docs/execution/ .agent/artifacts/
git reset -q
git add -A -- docs/execution/ .agent/artifacts/
git commit -m "docs(sdlc-fix): Change-Brief und betroffene Pfade" \
  -m "Schritt 1a - Change-Brief | Agent: aisdlc-requirements | Artefakte: docs/execution/, .agent/artifacts/"
```

- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).