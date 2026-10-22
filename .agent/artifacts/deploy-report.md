# Deploy-Report — EchoBox (Schritt 9, kanonisch)

> Agent: `aisdlc-qa` · Stand: 2026-10-22.

## Staging- / Produktions-URL

- Staging: Platzhalter — gesetzt durch GitHub-Environment `staging` (`STAGING_URL`).
- Produktion: Platzhalter — gesetzt durch GitHub-Environment `production` (`PRODUCTION_URL`).
- **Keine erfundenen URLs.** Die Werte entstehen in der Pipeline.

## Image

- Registry: `ghcr.io/<owner>/echobox`
- Tag: Commit-SHA (`github.sha`), immutable. Digest wird von der Pipeline gesetzt.
- Promotion: Staging und Produktion beziehen **dasselbe** Image (gleicher Digest).

## Smoke-Test-Ergebnisse

Siehe `docs/execution/deploy-report.md` §2. Zusammenfassung:

- Image-Build: **pass**
- Container-Start: **pass**
- Health (Liveness): **pass**
- Readiness: **pass**
- Authentifizierung (401 ohne Token): **pass**
- Non-root / Health-Check / kein Secret im Image: **pass**
- Vollstaendiger E2E-Smoke (Registrierung → Einlieferung → Zustellung → Protokoll → toter Lauf → Replay): **nicht ausgefuehrt** (kein Identity Provider im lokalen Lauf). Abgedeckt durch 537 Integrationstests.

## Validierung

- `python3 .agent/tools/traceability_check.py` → gruen.
- Schema-Diffs (beide) → leer.
- `docs/api/` → unveraendert.
- Coverage 86,73 % (Gate erzwungen).

## Freigabe-Empfehlung

**FREIGEBEN** — GATE 2 am 2026-10-22 freigegeben (0 Critical, 0 High). Die
Pipeline und die Betriebsartefakte sind bereit; das erste Deployment erfolgt
durch den menschlichen Betreiber. Offene Befunde (S-36 Medium, S-35 Low, Q-8,
Q-9, O-05, O-04, R-01/E-13, E-12, B-18) sind nicht Gate-blockierend und in
`docs/execution/deploy-report.md` §4 katalogisiert.
