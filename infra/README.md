# EchoBox — Infrastruktur und Betrieb

## Lokaler Start

```bash
# Image bauen (Multi-Stage, non-root, gepinnt, Health-Check, kein Secret)
docker build -t echobox:local .

# Lokales Entwicklung-Setup starten
docker compose -f infra/docker-compose.yml up -d

# Health und Readiness pruefen
curl -fsS http://127.0.0.1:8080/health
curl -fsS http://127.0.0.1:8080/health/ready
```

## Pflicht-Variablen

| Variable | Wirkung |
|---|---|
| `ECHobox_CRYPTO_KEY` | AES-256-GCM-Schluessel (32 Byte, Base64 oder Hex). Ohne Wert erzeugt der Dienst einen ephemeralen Schluessel (nur Entwicklung). In Staging/Produktion **Pflicht** (key-required). |
| `ECHobox_DATABASE_URL` / `STAGING_DATABASE_URL` / `PRODUCTION_DATABASE_URL` | JDBC-URL der Datenbank |
| `ECHobox_DATABASE_USERNAME` / `..._USERNAME` | Datenbankbenutzer (`echobox_app`) |
| `ECHobox_DATABASE_PASSWORD` / `..._PASSWORD` | Datenbankpasswort |
| `ECHobox_SECURITY_JWK_SET_URI` / `STAGING_JWK_SET_URI` / `PRODUCTION_JWK_SET_URI` | JWKS-Endpunkt des Identity Providers |
| `ECHobox_SECURITY_AUDIENCE` / `..._AUDIENCE` | Erwartete Audience (`aud`) des JWT |

## Optionale Variablen

| Variable | Vorgabe | Wirkung |
|---|---|---|
| `ECHobox_TENANT_ID` / `STAGING_TENANT_ID` / `PRODUCTION_TENANT_ID` | leer | Bindet eine Instanz an einen Mandanten (ADR-013). Leer = geteilter Betrieb. |
| `ECHobox_CURSOR_KEY` | leer (ephemer) | HMAC-Schluessel der opaken Cursor |
| `ECHobox_RATE_LIMIT` | 240 | Anfragelimit der API je Mandant und Fenster |
| `ECHobox_DATABASE_POOL_SIZE` | 10 | HikariCP-Poolgroesse |
| `ECHobox_DELIVERY_WORKER_ENABLED` | true | Worker in dieser Instanz ein-/ausschalten |
| `ECHobox_DELIVERY_WORKER_POLL_INTERVAL` | 1s / 2s | Polling-Takt des Workers |
| `ECHobox_DELIVERY_MAX_ATTEMPTS` | 8 | Versuche je Zustelllauf |
| `ECHobox_VERSION` | 1.0.0 | Versionsangabe in `/health` |

## Schluesselrotation

- **Verschluesselungsschluessel (`ECHobox_CRYPTO_KEY`)**: Rotation erfordert Re-Verschluesselung der Nutzlasten (AES-256-GCM). Der Dienst entschluesselt nur mit dem aktuell konfigurierten Schluessel. Rotation nur in einem Wartungsfenster mit Datenmigration.
- **JWT-Signierschluessel**: Rotation im Identity Provider; der Dienst liest das JWKS beim Start und cached. Neustart erzwingt Neuladen.
- **Endpunkt-Secrets**: Rotation ueber `POST /api/v1/endpoints/{id}/secret:rotate` (Vertrag). Die alte Version bleibt fuer laufende Zustellungen gueltig (Secret-Historie).
- **Cursor-HMAC-Schluessel (`ECHobox_CURSOR_KEY`)**: Rotation macht bestehende Cursor ungueltig — Clients muessen neu paginieren.

## Was bei einem Mandantenleck zu tun ist

1. **Sofort**: die betroffene Instanz stoppen (`docker compose stop api worker` bzw. Rollback in der Pipeline).
2. **Mandantenbindung pruefen**: `ECHobox_TENANT_ID` der Instanz und den Mandanten im Token vergleichen — ein Abgleich-Fehler ist ein Datenleck in die falsche Richtung (ADR-013).
3. **Betroffene Daten**: die Mandantentrennung liegt in der Datenbank (RLS, ADR-002). Ein Leck ueber die mandantenuebergreifende Sicht ist auf die drei `SECURITY DEFINER`-Funktionen begrenzt (Endpunktliste, Totenliste, Aufbewahrung) — keine Nutzlast, kein Secret.
4. **Aufbewahrung erzwingen**: `POST /api/v1/retention/enforcement` mit `dry_run=false` fuer den betroffenen Mandanten (nur `service_admin`).
5. **Audit-Log pruefen**: `app.audit_log` nach Aktionen des betroffenen Mandanten.
6. **Secrets rotieren**: Endpunkt-Secrets und (falls noetig) den Verschluesselungsschluessel.
7. **Postmortem** mit Ursache, Blast Radius und Prevention.

## Umgebungsdatei

Die Datei `.env` im Projekt-Root ist in `.gitignore` und enthaelt die lokalen Werte. Niemals committen.
