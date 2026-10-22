# Steckbrief — Schritt 9 (Deployment & Betrieb)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 8 abgeschlossen — Commit `907e497`; **GATE 2 am 2026-10-22 vom Menschen freigegeben** (0 Critical, 0 High) |
| Dieser Schritt | 9 (Deployment & Betrieb) — **abgeschlossen** (Pipeline, Container, Bereitstellung, Betrieb) |
| Naechster Schritt | **keiner** — Schritt 9 ist der letzte Schritt; GATE 2 am 2026-10-22 freigegeben |
| Vertrag | `docs/api/openapi.yaml` — **EINGEFROREN seit `8627bb6`**; Vertragsdefekt ⇒ Befund fuer Schritt 2 |
| Schema | `.agent/knowledge/schema.sql` + Spiegel + Migration — byte-identisch; **nicht** anfassen |
| Code | `src/`, `tests/` — **537 Tests gruen**, Coverage 86,73 % (Gate erzwungen), AK-Abdeckung 101/101 |
| Pipeline | `.github/workflows/deploy.yml` (aus Schritt 6/8: Secret-Scan, SCA-Scan mit Baseline, Build, Tests, OIDC) — wird in diesem Schritt **zur Betriebspipeline ausgebaut** |
| Container | `Dockerfile` (Multi-Stage, non-root, gepinnte Basis-Images, gepinnte apt-Pakete, kein Secret im Image, Health-Check) |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention, Secrets |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/09-deploy.md` | dieser Schritt, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/04_orchestrator_testing.md` | dein Systemprompt |
| 5 | `.agent/artifacts/security-audit.md` | **der Audit** — 0 Critical, 0 High; die verbleibenden Befunde und ihre Empfehlungen |
| 6 | `.agent/artifacts/test-report.md` | **der QA-Bericht** — 0 Critical; Teststand, Coverage, AK-Abdeckung |
| 7 | `.agent/artifacts/architecture.md` | Komponenten, Betrieb, Mandanten-Isolation (ADR-013) |
| 8 | `Dockerfile`, `.github/workflows/deploy.yml`, `pom.xml`, `package.json`, `.gitignore` | die vorhandenen Betriebsartefakte |
| 9 | `docs/adr/ADR-011-metriken-slo.md`, `ADR-012-aufbewahrung.md`, `ADR-013-mandanten-isolation-instanz.md` | Metriken/SLOs, Aufbewahrung, Containerbetrieb |
| 10 | `docs/api/openapi.yaml` | die **eingefrorene** Zusage (Health/Readiness, Metriken, Betriebsoperationen) |

## Skills
`aisdlc-ci-pipeline` (Pipeline-Stufen mit Test-, Coverage-, Security- und Deploy-Gates) ·
`aisdlc-containerize` (Multi-Stage, non-root, gepinnte Basis-Images, keine Secrets im Image, Health-Check) ·
`aisdlc-deploy` (Approval-Gates, Rollout, Validierung, Smoke-Tests, Promotion, Rollback, SLIs/SLOs, Runbook)

## Dein Auftrag

### 1 — Voraussetzungen pruefen (der erste Schritt des Workflows)
Belege im Bericht: **0 Critical** im QA-Bericht **und** im Security-Audit, **GATE 2 freigegeben**.
Wenn doch ein Critical auftaucht: **stoppen** und melden.

### 2 — `infra/` — die Betriebsartefakte
- **`docker-compose.yml`** — lokale Entwicklung **und** getrennte Umgebungen (Staging/Produktion
  **getrennt**, ADR-013: zwei Rollen `api` und `worker` in **einem** Image, `echobox.tenant.id` bindet
  eine Instanz an einen Mandanten). Der Dienst laeuft in Containern, ist auf **eine Instanz je Mandant**
  vorbereitet und skaliert horizontal (NFR-006). **Keine Secrets** in der Datei — nur
  `${ENV:-Vorgabe}`-Verweise auf Variablen; `.env` bleibt in `.gitignore`.
- **Bereitstellungsdeskriptoren** fuer die Umgebungen (z. B. Cloud-Run- oder Container-Deployment),
  **kein** Secret im Deskriptor, Health-Check, Ressourcenlimits, **keine** unnoetigen Ports
  (S-26/S-27), `SPRING_PROFILES_ACTIVE` je Umgebung (Produktion ⇒ `production`, **nicht** `staging`).
- **`infra/README.md`** — wie kommt man lokal und in die Umgebungen, welche Variablen sind Pflicht,
  welche sind optional, wie rotiert man Schluessel.

### 3 — `.github/workflows/` — die Betriebspipeline
Die vorhandene `deploy.yml` (aus Schritt 6/8) **ausbauen** zu einer Pipeline mit **Approval-Gates**:
- **Build** (Image bauen: Multi-Stage, non-root, gepinnte Basis-Images, kein Secret im Image,
  Health-Check) ⇒ **Test-Gate** (Java + Oberflaeche, **Coverage-Gate ≥ 80 %**, Vertragstests) ⇒
  **Security-Gate** (Secret-Scan, SCA-Scan mit **Baseline**, Image-Scan) ⇒ **Deploy Staging**
  ⇒ **Smoke-Tests** ⇒ **Approval-Gate (menschlich)** ⇒ **Deploy Produktion** ⇒ **Smoke-Tests** ⇒
  **Rollback-Pfad**.
- **Rollout-Strategie** und **Rollback** beschreiben und — soweit moeglich — automatisieren.
- **Promotion** von Staging nach Produktion nachvollziehbar (gleiches Image, gleicher Digest).
- **Keine Secrets in Logs**; Registry-Zugriff ueber OIDC; **geschuetzte Branches** und
  Environment-Gates (production braucht Freigabe).

### 4 — `docs/execution/` — Betrieb und Runbook
- **Deploy-Report** (`docs/execution/deploy-report.md`): was deployed wurde (Image-Digest), Smoke-Test-
  Ergebnisse (pass/fail), **URLs fuer Staging und Produktion** (als Konfiguration, nicht als erfundene
  Realitaet), Validierung, bekannte Restbefunde (S-35/S-36, Q-8/Q-9, O-05).
- **Runbook** (`docs/execution/runbook.md`): Health/Readiness, **SLIs/SLOs** (aus ADR-011:
  Erfolgsquote, p95-Zustelllatenz), **Alarmierung**, **Rollback**, **Aufbewahrung/Purge** (F-02),
  **Secret-Rotation** (FR-003), was bei einem Mandantenleck zu tun ist (Notfall: Mandant aus der
  Mandantenliste entfernen, Service anhalten).
- **Katalog der offenen Punkte** mit Owner: O-04, O-05, R-01/E-13, E-12, Q-8, Q-9, S-35, S-36, O-05
  (postcss) — und was im Betrieb zu tun ist.

### 5 — `.agent/artifacts/deploy-report.md`
Der kanonische Deploy-Report (Staging-/Produktions-URL, Smoke-Test-Ergebnisse, Validierung,
Freigabe-Empfehlung).

## Regeln
- **Kein Anwendungscode.** `src/` und `tests/` bleiben unberuehrt (ausser `tests/` fuer
  **Rauchtest-/Deploy-Tests**, falls noetig).
- **Keine Aenderung** an `docs/api/`, `docs/adr/`, `docs/architecture/`, `.agent/knowledge/`,
  `docs/requirements/`. In `docs/` nur `docs/execution/`.
- **Kein Schema-Anfassen** (beide Diffs leer halten).
- **Keine Secrets** in Code, Konfiguration, Image, Deskriptoren oder Pipeline — nur Verweise auf
  Variablen. `.env` bleibt in `.gitignore`.
- **Kein echtes Deployment** in eine fremde Umgebung: Du **erstellst** die Pipeline und die
  Artefakte und **validierst** sie lokal (Image-Build, Compose-Start, Health-Check, Smoke-Test gegen
  den lokalen Dienst). Erfinde **keine** Staging-/Produktions-URLs und **keine** Digeste, die es nicht
  gibt — beschreibe, **wie** sie entstehen, und welche Platzhalter die Pipeline setzt.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Diffs | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` · `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
| Image bauen | `docker build` (mit BuildKit) |
| Lokal starten | `docker compose up` + Health-Check |
| Backend / Oberflaeche | `mvn -q verify` · `npx tsc --noEmit` · `npm run lint` · `npx vitest run` |

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p docs/execution/ infra/ .github/ .agent/artifacts/
git reset -q
git add -A -- docs/execution/ infra/ .github/ .agent/artifacts/
git commit -m "build(sdlc-9): Deployment-Pipeline und Deploy-Report" \
  -m "Schritt 9 - Deployment & Betrieb | Agent: aisdlc-qa | Artefakte: docs/execution/, infra/, .github/, .agent/artifacts/"
```

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Kennzahlen, Validierung). Keine Herleitung, keine Auszuege.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit S-xx/O-xx/Q-xx> | -
```
