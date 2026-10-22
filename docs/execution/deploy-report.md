# Deploy-Report — EchoBox (Schritt 9)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · GATE 2 freigegeben am 2026-10-22
> (0 Critical, 0 High; 1 Medium S-36, 1 Low S-35).

## 1. Was deployed wurde

- **Image**: `ghcr.io/<owner>/echobox:<commit-sha>` — Digest wird von der Pipeline
  gesetzt (Build-Job, Tag `github.sha`). **Kein erfundener Digest**; der Wert
  steht im Laufprotokoll der Registry.
- **Promotion**: Staging und Produktion beziehen **dasselbe Image** (gleicher
  Commit-SHA / gleicher Digest). Nur Konfiguration und Secrets unterscheiden
  sich (ADR-013 §4).
- **Umgebungen**:
  - Staging-URL: Platzhalter — wird durch die Umgebungsvariable `STAGING_URL`
    (GitHub-Environment `staging`) gesetzt.
  - Produktions-URL: Platzhalter — wird durch `PRODUCTION_URL`
    (GitHub-Environment `production`) gesetzt.
- **Kein echtes Deployment** in eine fremde Umgebung wurde durchgefuehrt. Die
  Pipeline ist bereit; das erste Deployment erfolgt durch den menschlichen
  Betreiber (Gate 2 freigegeben).

## 2. Smoke-Test-Ergebnisse (lokal, 2026-10-22)

| Schritt | Ergebnis | Befehl / Nachweis |
|---|---|---|
| Image bauen (Multi-Stage, non-root, gepinnt, Health-Check, kein Secret) | **pass** | `docker build -t echobox:local .` → Image-Digest `sha256:10ae0fb89f70fa892f2f46abeca672a8639d3934d22f07c7959220e4d35760bc` (lokales Build, nicht Registry) |
| Container starten (Profil default, Worker aktiv) | **pass** | `docker run … echobox:local` mit `SERVER_PORT=8099` |
| Health (Liveness) | **pass** | `GET /health` → 200 `{"status":"up","service":"echobox","version":"1.0.0",…}` |
| Readiness | **pass** | `GET /health/ready` → 200 `{"status":"ready","checks":{"database":"ok","delivery_worker":"ok","tenant_context":"ok"},…}` |
| Authentifizierung (ohne Token) | **pass** | `POST /api/v1/endpoints` ohne Bearer → 401 `unauthorized` |
| Non-root im Image | **pass** | `docker inspect …User` = `10001:10001` |
| Health-Check im Image | **pass** | `Healthcheck.Test` = `curl -fsS http://127.0.0.1:8080/health` |
| Secret im Image | **pass** | kein Secret in den Layers (nur ENV-Namen, keine Werte) |
| Endpunkt registrieren / Ereignis einliefern / Zustellung beobachten / Protokoll lesen / toter Lauf + Replay | **nicht ausgefuehrt** | benoetigt ein signiertes JWT eines Identity Providers (JWKS). Der Dienst antwortet ohne Token mit 401 (belegt). Diese Schritte sind in den Integrationstests abgedeckt (537 Tests gruen, AK-Abdeckung 101/101). |

**Abweichung**: der vollstaendige End-to-End-Smoke (Registrierung → Einlieferung →
Zustellung → Protokoll → toter Lauf → Replay) wurde **nicht** gegen den lokal
gestarteten Dienst ausgefuehrt, weil kein Identity Provider (JWKS) zur Verfuegung
stand. Die Authentifizierungsgrenze (401) und Health/Readiness wurden belegt.

## 3. Validierung

- `python3 .agent/tools/traceability_check.py` → gruen (keine Luecken).
- Schema-Diffs (beide) → leer (byte-identisch).
- `docs/api/` → unveraendert (eingefroren seit `8627bb6`).
- Docker-Build → erfolgreich (Multi-Stage, non-root uid 10001, gepinnte
  Basis-Images und apt-Pakete, kein Secret im Image, Health-Check).
- `mvn -B verify` → 537 Tests gruen, Coverage 86,73 % (Gate erzwungen).

## 4. Bekannte Restbefunde (nicht Gate-blockierend)

| ID | Schwere | Beschreibung | Betriebshinweis |
|---|---|---|---|
| **S-36** | Medium | Nicht unterstuetzte HTTP-Methode → 500 mit Stacktrace statt 4xx (GlobalExceptionHandler). Antwort gibt kein internes Detail preis. | Monitoring-Verschmutzung; Behebung in Schritt 6/7 (eigener Fall → 400 `malformed_request`). |
| **S-35** | Low | Dokumentation der Audience widerspricht dem fail-closed-Verhalten (application.yml, EchoBoxProperties). | Betriebsdoku: `PRODUCTION_SECURITY_AUDIENCE` / `STAGING_SECURITY_AUDIENCE` duerfen **nicht** leer sein. |
| **Q-8** | technisch | Anfragelimit je Instanz (Schema → Schritt 9). | Bei horizontaler Skalierung ist das Limit je Instanz, nicht global. |
| **Q-9** | technisch | Mandantenbezug des Anfragelimits (unauthentifizierte Anfragen haben keinen Mandanten). | S-36-Anfragen umgehen das Limit; mit S-36-Behebung entschaerft. |
| **O-05** | rechtlich | AVV (Auftragsverarbeitung) — Freigabebedingung vor produktivem Betrieb. | Technisch vorbereitet (Datenminimierung, Mandantentrennung, Loeschfristen). |
| **O-05 (postcss)** | Low | postcss 8.4.31 als Laufzeit-Abhaengigkeit von next 15.5.28 (CSS-Parser, kein CSS-Input). | Behebung mit naechstem next-Upgrade; Eintrag in osv-scanner.toml befristet bis 2027-04-30. |
| **O-04** | rechtlich | Verschluesselung im Ruhezustand (fachliche Pflichtfrage). | Architekturseite entschieden (ADR-003, ADR-017). |
| **R-01 / E-13** | fachlich | Karenzzeit der Aufbewahrung ohne beschlossenen Vorgabewert. | Konfigurierbar (`echobox.retention.purge-delay`); ohne Wert Purge im naechsten Durchlauf. |
| **E-12** | fachlich | Haertung der mandantenuebergreifenden Sicht (eigene Laufzeitrolle `echobox_ops`). | Offen; die Grenze ist in ADR-002 ehrlich benannt. |
| **B-18** | technisch | `endpoint_event_types` hat keinen eindeutigen Schluessel je Mandant. | `endpoint_id` ist weltweit eindeutig; keine Mehrfachregistrierung moeglich. |

## 5. Gate-Entscheidungen

| Gate | Datum | Ergebnis |
|---|---|---|
| GATE 1 (Architektur) | 2026-10-21 | freigegeben — Richtung bestaetigt (F-01 … F-04) |
| GATE 2 (Security) | 2026-10-22 | freigegeben — 0 Critical, 0 High (1 Medium S-36, 1 Low S-35) |

## 6. Rollback-Pfad

- **Ausloeser**: Health/Readiness fehlgeschlagen, Smoke-Test rot, SLO-Burn-Rate ueberschritten, oder manueller Abbruch.
- **Befehl**: vorheriges Image (Commit-SHA, `PREVIOUS_IMAGE`) in Staging/Produktion ausrollen.
- **Reihenfolge**: Code zurueck, **keine** Schema-Migration zurueck (Expand/Contract — nur vorwaerts). Daten bleiben.
- **Dauer**: abhaengig von der Zielumgebung (Container-Restart).
- **Nachkontrolle**: Health + Readiness + Smoke-Tests gruen.

## 7. SLO-/Alerting-Status

- SLIs/SLOs: siehe `docs/execution/runbook.md` (ADR-011: Erfolgsquote, p95-Zustelllatenz).
- Alerting: symptombasiert (Nutzer-Effekt), Runbook-Referenz in `docs/execution/runbook.md`.
- Error Budget: bei Verbrauch Feature-Freeze.
