---
description: Phase 3 — Simplify & Reduction (vor dem Stresstest)
---

# Simplify Workflow

## Prerequisites

- Read `AGENTS.md` in project root.
- Read `.agent/artifacts/architecture.md` and `.agent/knowledge/stack.md`.

## Agent & Skill

| Rolle | Wert |
|---|---|
| Agent | `aisdlc-architecture` |
| Instanz (Multi-Instanz-Modus) | `phase2` |
| Skill | `aisdlc-simplify` |

## Steps

1. **Komplexität identifizieren** — welche Teile der Architektur sind überdimensioniert?
2. **Reduzieren** — nur das Nötigste behalten, Over-Engineering eliminieren.
3. **Vereinfachen** — Klarheit vor Vollständigkeit.

## Output
`.agent/artifacts/` (aktualisierte Architektur, Reduktionsbericht)

## Commit

```bash
git add -A .
git diff --cached --name-only -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/ \
  | git commit -m "refactor(sdlc-3): Architektur vereinfacht" \
             -m "Schritt 3 - Simplify & Reduktion | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/api/, docs/adr/, .agent/artifacts/" \
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

## Gate
Await human review. Bei zu viel Komplexität → Rücksprung zu Phase 2.