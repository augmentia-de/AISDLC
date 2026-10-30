---
description: Phase 6a — Delta implementation (slim, focused on affected paths)
---

# Delta Implementation Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- `docs/execution/change-brief-<id>.md` exists.

## Agent & Skill

- **Agent:** `aisdlc-execution`
- **Skill:** `aisdlc-feature-scaffold` (nur bei Bedarf), `aisdlc-simplify`

## Steps

1. **Implementiere** nur die in `affected_paths` genannten Dateien.
2. **Keine parallelen Streams** — fokussierter Einzel-Stream.
3. **Lint/Build** nach jedem Schritt.
4. **Keine Vertragsänderung** — bei Bedarf Rücksprung zu Phase 2.
5. **README** nicht zwingend (nur bei nutzerrelevanten Änderungen).

## Output

- Code in `src/`, `tests/`.
- `.agent/artifacts/` (Delta-Report).

## Regeln

- Nur `affected_paths` ändern; alles andere read-only.
- Kein Refactoring außerhalb des Fix-Scope (das ist Schritt 7).
- Kein `openapi.yaml`-Änderung ohne Jump-Back.

## Commit

```bash
mkdir -p src/ tests/ docs/execution/
git reset -q
git add -A -- src/ tests/ docs/execution/
git commit -m "feat(sdlc-fix): Delta-Implementierung" \
  -m "Schritt 6a - Delta-Implementierung | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/"
```

- Wenn sich nichts geaendert hat: kein Commit erzeugen (kein Fehler).