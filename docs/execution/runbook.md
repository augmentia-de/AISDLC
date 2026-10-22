# Runbook — EchoBox (Betrieb)

> Agent: `aisdlc-qa` · Stand: 2026-10-22

## 1. Health und Readiness

- **Liveness**: `GET /health` (ohne Authentifizierung) → `{"status":"up",…}`. Der
  Container meldet sich erst nach Readiness als bereit.
- **Readiness**: `GET /health/ready` → `{"status":"ready","checks":{"database":"ok","delivery_worker":"ok","tenant_context":"ok"},…}`.
  Nicht bereit ⇒ HTTP 503 mit `Problem` (`code: service_unavailable`).
- **Container-Health-Check**: `curl -fsS http://127.0.0.1:8080/health` (alle 30 s,
  Timeout 5 s, Start-Periode 45 s, 3 Versuche).
- **Actuator**: `/actuator/prometheus` (Metriken, maschinelle Betriebsueberwachung,
  ADR-011 §4).

## 2. SLIs / SLOs (ADR-011)

| SLI | Definition | SLO (Vorschlag) |
|---|---|---|
| **Erfolgsquote** | `attempts_successful / attempts_total` im Auswertungsfenster (Default 7 Tage) | ≥ 99 % je Endpunkt |
| **Zustelllatenz p95** | 95-Perzentil der Dauer von der Annahme (`events.accepted_at`) bis zum erfolgreichen Versuch | < 60 s |
| **Verfuegbarkeit** | Anteil erfolgreicher Health/Readiness-Pruefungen | ≥ 99,9 % |
| **Fachliche Kennzahl** | `GET /api/v1/endpoints/{endpointId}/metrics` (Service-Admin, Empfaenger-Admin) | `data_available: false` statt 0 % bei fehlenden Daten |

- Ohne Daten im Fenster: `data_available: false`, alle Felder `null` — nicht 0 %.
- p95 misst die **gesamte** Zeit vom Einliefern bis zur erfolgreichen Zustellung.
- Die Kennzahlen werden on demand aus den Versuchen berechnet (keine eigene
  Tabelle).

## 3. Alerting

- **Symptombasiert** (Nutzer-Effekt), nicht ursachenbasiert.
- Alerts:
  - Health/Readiness rot > 2 min → Seite (P1).
  - Erfolgsquote < 95 % ueber 15 min → Seite (P2).
  - p95 > 120 s ueber 15 min → Ticket (P3).
  - 5xx-Rate > 1 % ueber 5 min → Seite (P2).
  - Worker-Durchlauf fehlgeschlagen (Log `Der Zustell-Durchlauf ist fehlgeschlagen`) → Ticket.
- Jeder Alert verweist auf diesen Runbook-Abschnitt und auf
  `docs/execution/deploy-report.md` §6 (Rollback).

## 4. Rollback

- **Ausloeser**: Health/Readiness rot, Smoke-Test rot, SLO-Burn-Rate ueberschritten,
  manueller Abbruch.
- **Befehl**: vorheriges Image (Commit-SHA, Secret `PREVIOUS_IMAGE`) in
  Staging/Produktion ausrollen.
- **Reihenfolge**: Code zurueck; **keine** Schema-Migration zurueck
  (Expand/Contract — nur vorwaerts). Daten bleiben.
- **Dauer**: abhaengig von der Zielumgebung (Container-Restart).
- **Nachkontrolle**: Health + Readiness + Smoke-Tests gruen.

## 5. Aufbewahrung / Purge (F-02, ADR-012)

- **Zwei Fristen**: `event_retention_days` (Ereignisse) und `delivery_retention_days`
  (Zustellprotokolle), Vorgabe je 30 Tage, je Mandant ueberschreibbar
  (`retention_settings`).
- **Fristbeginn**: letzter Zustellversuch (`last_attempt_at`).
- **Zwei Stufen**: Markieren (`deleted_at = now()`, sofort unsichtbar ueber RLS)
  dann Purge nach Karenzzeit (`purge_delay_days`, konfigurierbar; ohne Wert Purge
  im naechsten Durchlauf — kein erfundener Vorgabewert, R-01/E-13 offen).
- **Kaskade**: Ereignis loeschen nimmt Zustelllaeufe und Versuche mit.
- **Durchsetzung**: automatisch (täglich) + manuell ueber
  `POST /api/v1/retention/enforcement` (nur `service_admin`, `dry_run = true`
  Default).
- **Nicht geloescht**: Endpunkte, `endpoint_secret_history`, `audit_log`
  (eigene Frist).

## 6. Secret-Rotation (FR-003)

- **Verschluesselungsschluessel (`ECHobox_CRYPTO_KEY`)**: Rotation erfordert
  Re-Verschluesselung der Nutzlasten (AES-256-GCM). Nur im Wartungsfenster mit
  Datenmigration.
- **JWT-Signierschluessel**: Rotation im Identity Provider; JWKS wird beim Start
  geladen und cached. Neustart erzwingt Neuladen.
- **Endpunkt-Secrets**: Rotation ueber `POST /api/v1/endpoints/{id}/secret:rotate`.
  Die alte Version bleibt fuer laufende Zustellungen gueltig (Secret-Historie).
- **Cursor-HMAC-Schluessel (`ECHobox_CURSOR_KEY`)**: Rotation macht bestehende
  Cursor ungueltig — Clients muessen neu paginieren.

## 7. Was bei einem Mandantenleck zu tun ist (Notfall)

1. **Sofort**: betroffene Instanz stoppen (`docker compose stop api worker` bzw.
   Rollback in der Pipeline).
2. **Mandantenbindung pruefen**: `ECHobox_TENANT_ID` der Instanz mit dem Mandanten
   im Token vergleichen. Ein Abgleich-Fehler ist ein Datenleck in die falsche
   Richtung (ADR-013).
3. **Blast Radius**: die Mandantentrennung liegt in der Datenbank (RLS, ADR-002).
   Ein Leck ueber die mandantenuebergreifende Sicht ist auf die drei
   `SECURITY DEFINER`-Funktionen begrenzt (Endpunktliste, Totenliste,
   Aufbewahrung) — keine Nutzlast, kein Secret.
4. **Aufbewahrung erzwingen**: `POST /api/v1/retention/enforcement` mit
   `dry_run=false` fuer den betroffenen Mandanten (nur `service_admin`).
5. **Audit-Log pruefen**: `app.audit_log` nach Aktionen des betroffenen
   Mandanten.
6. **Secrets rotieren**: Endpunkt-Secrets und (falls noetig) den
   Verschluesselungsschluessel.
7. **Postmortem** mit Ursache, Blast Radius und Prevention.

## 8. Katalog der offenen Punkte

| ID | Owner | Thema | Betriebshinweis |
|---|---|---|---|
| O-04 | Fachbereich + Datenschutz | Verschluesselung im Ruhezustand | Architekturseite entschieden (ADR-003, ADR-017) |
| O-05 | Fachbereich + Recht | AVV (Auftragsverarbeitung) | Freigabebedingung vor produktivem Betrieb |
| O-05 (postcss) | Wartung | postcss 8.4.31 als Laufzeit-Abhaengigkeit | Behebung mit naechstem next-Upgrade |
| R-01 / E-13 | Fachbereich | Karenzzeit der Aufbewahrung | Konfigurierbar; ohne Wert Purge im naechsten Durchlauf |
| E-12 | Fachbereich + Plattform | Haertung der mandantenuebergreifenden Sicht (`echobox_ops`) | Offen; Grenze in ADR-002 benannt |
| Q-8 | Schritt 9 / Plattform | Anfragelimit je Instanz | Bei horizontaler Skalierung je Instanz, nicht global |
| Q-9 | Schritt 6/7 | Mandantenbezug des Anfragelimits | S-36-Anfragen umgehen das Limit |
| S-36 | Schritt 6/7 | Nicht unterstuetzte HTTP-Methode → 500 mit Stacktrace | Monitoring-Verschmutzung; Behebung → 400 `malformed_request` |
| S-35 | Schritt 6/7 | Dokumentation der Audience | `PRODUCTION_SECURITY_AUDIENCE` / `STAGING_SECURITY_AUDIENCE` nicht leer |
| B-18 | Architektur | `endpoint_event_types` kein eindeutiger Schluessel je Mandant | `endpoint_id` weltweit eindeutig |
| Q-1 | Plattform | TLS / HSTS | Offene Frage aus dem Security-Audit |
| Q-2 | Plattform | Branch-Schutz | Konfiguration in GitHub |
| Q-3 | Fachbereich | Recht auf Loeschung | Offene Frage |
| Q-4 | Plattform | Interne Adressbereiche (SSRF) | Offene Frage |
| Q-7 | Architektur | DEK/KEK je Mandant (ADR-003 §2) | Offene Frage |

## 9. Mandanten-Sharding (ADR-013)

- `echobox.tenant.id` bindet eine Instanz an einen Mandanten. Leer = geteilter
  Betrieb.
- Gesetzt: die Instanz bedient **nur** diesen Mandanten; ein Token eines anderen
  Mandanten wird mit HTTP 403 (`tenant_mismatch`) abgelehnt.
- Eine Mandanteninstanz und ein gemeinsamer Betrieb duerfen **nicht** parallel auf
  dieselbe Datenbank zeigen.
- Horizontal skalierbar: API stateless; Worker ueber `FOR UPDATE SKIP LOCKED` +
  Lease (kein verteilter Lock-Dienst).
