---
name: aisdlc-ci-pipeline
description: Phase 9: CI-Pipeline mit Test-, Coverage-, Security- und Deploy-Gates erzeugen.
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/deployment-patterns + ECC/github-actions-templates
---

## Wann

**Phase 9**, vor dem ersten Deployment — und immer, wenn sich der Stack aendert.

## Das eiserne Gesetz

```text
DIE QUALITAETSGATES GEHOEREN IN DIE PIPELINE, NICHT IN DIE KONVENTION
```

Eine Konvention, die unter Zeitdruck vergessen wird, ist keine Sicherung. Das
Coverage-Gate, der Security-Scan und die Freigabe vor dem Deployment gehoeren in
den Build, nicht in eine Datei, die man liest.

## Jobs

1. **Test-Job** — Abhaengigkeiten installieren, Tests laufen lassen, Coverage
   veroeffentlichen.
2. **Coverage-Gate** — der Job schlaegt unter dem Projektschwellwert fehl. Der Wert
   kommt aus dem Test-Report, nicht geraten.
3. **Statische Analyse** — Linter und, falls konfiguriert, ein Security-Scan
   (Schwachstellen in Abhaengigkeiten, Secret-Erkennung).
4. **Build-Job** — Container-Image, getaggt mit Commit-SHA **und** einem beweglichen
   Tag.
5. **Deploy-Job** — nur auf `main`, erst nach allen Gates. Staging vor Produktion,
   mit Smoke-Test dazwischen.
6. **Freigabe-Gate** — eine geschuetzte Umgebung, die menschliche Freigabe vor dem
   Produktions-Deploy verlangt. Das Gate in `AGENTS.md` und das Gate in der
   Pipeline sind **dasselbe** Gate; die Pipeline ist, wo es erzwungen wird.
7. **Secrets** — ausschliesslich aus dem Secret-Store, nie in ein Log oder eine
   Datei im Repo.

## Output

- `.github/workflows/<pipeline>.yml`
- `.agent/artifacts/ci-pipeline.md` — Jobs, Gates, Schwellwerte, Secret-Namen,
  Rollback-Ausloeser
- `docs/execution/CHANGELOG.md` bei einem Release

## Regeln

- Jeder Job hat ein explizites Bestehen- oder Scheitern-Kriterium. Kein
  "continue on error" in einem Gate-Job.
- Der Coverage-Schwellwert kommt aus dem Test-Report.
- Der Deploy-Job ist ohne Freigabe-Gate nicht erreichbar.
- Die Pipeline schlaegt fehl, wenn der Security-Scan einen kritischen Befund meldet.
- Kein Secret in einem Kommando, das im Log landet.
- Ein Image-Tag ohne SHA ist kein Release.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| Pipeline-Muster, Deploy-Strategien, Rollback | `deployment-patterns` |
| GitHub-Actions-Workflows | `github-actions-templates` |
| Staging-/Produktions-Trennung | `deployment-pipeline-design` |
| Secrets in CI | `secrets-management` |
| SAST-Konfiguration | `sast-configuration` |
