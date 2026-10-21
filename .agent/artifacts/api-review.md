# API-Review — EchoBox

> Schritt 2 (Konzept & Architektur) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Geprüfter Vertrag: `docs/api/openapi.yaml` (OpenAPI 3.0.3, `info.version` 1.0.0)
> Vertrag ist ein **Entwurf** — er wird **nach Schritt 4** eingefroren.

---

## 1. Kurzurteil

| Frage | Antwort |
|---|---|
| Deckt der Vertrag alle 23 Stories ab? | **Ja** — jede Story hat mindestens eine Operation oder eine ausdrücklich benannte Nicht-HTTP-Realisierung. |
| Sind alle 101 Akzeptanzkriterien auffindbar? | **Ja** — `x-acceptance-coverage` führt alle 101; die operationellen stehen zusätzlich als `x-ac-id` am Operationsobjekt. |
| Sind alle 24 Anforderungen (FR/NFR) auffindbar? | **Ja** — `x-requirement-coverage` führt alle 24. |
| Fehlen Operationen? | **Nein.** |
| Gibt es Felder ohne Zweck? | **Nein** — jedes Feld ist entweder im Protokoll sichtbar, in der Zustandsmaschine nötig oder ein Mandanten-/Zeitmetadatum. |
| Ist die Fehlersemantik einheitlich? | **Ja** — ein Schema (`Problem`) für alle Fehlerantworten, `code` maschinenlesbar. |
| Ist die Mandantentrennung im Vertrag durchgesetzt? | **Ja** — D-01 (422 bei Mandantenparameter), D-02 (404 bei fremden Ressourcen), global `bearerAuth`. |

---

## 2. Was der Vertrag abdeckt

### 2.1 Eingehende REST-Schnittstelle (18 Operationen)

| # | Operation | Methode und Pfad | Stories |
|---|---|---|---|
| 1 | `createEndpoint` | `POST /api/v1/endpoints` | US-001 |
| 2 | `listEndpoints` | `GET /api/v1/endpoints` | US-004 |
| 3 | `getEndpoint` | `GET /api/v1/endpoints/{endpointId}` | US-004, US-019 |
| 4 | `updateEndpoint` | `PATCH /api/v1/endpoints/{endpointId}` | US-001, US-019 |
| 5 | `pauseEndpoint` | `POST /api/v1/endpoints/{endpointId}/pause` | US-002 |
| 6 | `resumeEndpoint` | `POST /api/v1/endpoints/{endpointId}/resume` | US-002 |
| 7 | `rotateEndpointSecret` | `POST /api/v1/endpoints/{endpointId}/secret-rotation` | US-003 |
| 8 | `submitEvent` | `POST /api/v1/events` | US-005, US-006, US-008 |
| 9 | `listEvents` | `GET /api/v1/events` | US-007 |
| 10 | `getEvent` | `GET /api/v1/events/{eventId}` | US-015, US-007 |
| 11 | `listDeliveries` | `GET /api/v1/deliveries` | US-015, US-020 |
| 12 | `getDeliveryRun` | `GET /api/v1/deliveries/{deliveryRunId}` | US-015, US-012 |
| 13 | `listDeadDeliveries` | `GET /api/v1/dead-deliveries` | US-016, US-020 |
| 14 | `replayDelivery` | `POST /api/v1/deliveries/{deliveryRunId}/replay` | US-013 |
| 15 | `getEndpointMetrics` | `GET /api/v1/endpoints/{endpointId}/metrics` | US-017, US-018 |
| 16 | `enforceRetention` | `POST /api/v1/retention/enforcement` | US-016 |
| 17 | `getHealth` | `GET /health` | US-023 |
| 18 | `getReadiness` | `GET /health/ready` | US-023 |

### 2.2 Ausgehender Zustellvertrag (`x-delivery-protocol`, eingefroren)

| Element | Festlegung |
|---|---|
| Signatur | `HMAC-SHA256`, Header `X-EchoBox-Signature`, Wert `v1=<hex-lowercase>` |
| Signier-Eingabe | `<timestamp>.<payload_bytes>` — der Zeitstempel ist **exakt** der Headerwert |
| Zeitstempel-Header | `X-EchoBox-Timestamp` (ISO-8601 UTC) |
| Kennungen | `X-EchoBox-Event-Id`, `X-EchoBox-Delivery-Run-Id`, `X-EchoBox-Attempt-Id`, `X-EchoBox-Attempt-Number` |
| Ereignistyp | `X-EchoBox-Event-Type` |
| Request-Body | **exakt die Nutzlastbytes**, keine Hülle |
| Ablehnungssignal | HTTP **410**, Header `X-EchoBox-Rejection: permanent`, Body `{rejection, reason, message?, rejected_event_types?}` |
| Wiederholungsmatrix | 5xx / 408 / 429 / Netzwerkfehler ⇒ wiederholen; 2xx ⇒ fertig; 410 und übrige 4xx ⇒ Ende |
| Dubletten | at-least-once; Deduplizierung über `X-EchoBox-Event-Id`, Wiederholungserkennung über gleiche `Delivery-Run-Id` + verschiedene `Attempt-Id` |

### 2.3 Fehlersemantik

| Status | `code` | Ausgelöst durch |
|---|---|---|
| 400 | `malformed_request` | kein gültiges JSON |
| 401 | `unauthorized` | kein/ungültiges Token |
| 403 | `forbidden` | Rolle darf es nicht (Totenliste, Replay, Rotation, Aufbewahrung) |
| 404 | `not_found` | fehlende **oder** fremde Ressource (D-02) |
| 409 | `idempotency_conflict` | gleicher Idempotenzschlüssel, abweichende Nutzlast (D-05) |
| 409 | `delivery_state_conflict` | Replay einer nicht aufgegebenen Zustellung |
| 413 | `payload_too_large` | > 1.048.576 Byte (NFR-001, D-13) |
| 422 | `validation_failed` | semantisch ungültig |
| 422 | `tenant_parameter_rejected` | Mandantenparameter mitgeschickt (D-01) |
| 429 | `rate_limit_exceeded` | API-Anfragelimit (≠ Endpunkt-Limit) |
| 500 | `internal_error` | unerwarteter Fehler, ohne internes Detail |
| 503 | `service_unavailable` | Dienst nicht bereit |

Alle Fehlerantworten nutzen `application/problem+json` mit dem Schema `Problem`.

---

## 3. Was der Vertrag **nicht** abdeckt — und warum

| Nicht abgedeckt | Warum | Wo stattdessen |
|---|---|---|
| Volltextsuche über Nutzlastinhalte | unvereinbar mit FR-012 | ADR-017, D-11 |
| Ausgabe der Ereignisnutzlast über die API | die Nutzlast ist im Ruhezustand verschlüsselt; die API gibt sie nie aus | ADR-003 |
| Mandantenfilter als Query-Parameter | D-01 — der Mandant kommt aus der Identität; der Service-Admin filtert in der Oberfläche | ADR-015 |
| Auswahl des `organisation_id` im Request | D-01 | ADR-015 |
| Ein eigener Replay-„Vorgang“ je Endpunkt | ein Replay erzeugt einen **neuen Zustelllauf**; der alte bleibt erhalten (AC-013-2) | ADR-006, ADR-007 |
| Löschen eines Endpunkts | nicht gefordert; die Aufbewahrung (ADR-012) und das Pausieren decken den Bedarf | — |
| Ein `total` in den Listen | teure Zählung über die wachsende Zustelltabelle; die Aggregation liefert die Zahlen | ADR-011, ADR-017 |
| Sortierparameter | die Sortierung ist implizit `created_at DESC` — die Reihenfolge, in der der Betrieb den Fehler sucht | ADR-017 |
| Webhooks im OpenAPI-`callbacks`-Feld | OpenAPI 3.0.3 kennt `callbacks` nur für Rückkanäle; der ausgehende Vertrag ist eigenständig und deshalb als eigener, eingefrorener Abschnitt geführt | ADR-004 |

---

## 4. Befunde und offene Punkte im Vertrag

| ID | Befund | Behandlung | Status |
|---|---|---|---|
| V-01 | Die **Nutzlast** als JSON-Wert vs. byte-exakte Bytes | Beides angeboten: `payload` (JSON, kanonisch materialisiert) und `payload_base64` (byte-exakt). Genau eines ist Pflicht. | entschieden (ADR-004, AC-007-3) |
| V-02 | `max_attempts` ohne Wert | `null`, bis O-06 entschieden ist | **offen — O-06** |
| V-03 | Rate-Limit-Grenzwert | `rate_limit.limit = null`, Strategie `delay` | **offen — O-01** |
| V-04 | Aufbewahrungsfrist | `retention_days = null`, Mechanismus No-Op ohne Frist | **offen — O-02** |
| V-05 | Replay-Berechtigung | 403 für alle außer dem Empfänger-Admin des Endpunkts | **offen — O-03** |
| V-06 | Ob die Verschlüsselung Pflicht ist | Der Vertrag gibt die Nutzlast nie aus — unabhängig von der Antwort des Fachbereichs bleibt die API gleich | teilweise (ADR-003) |
| V-07 | Ablehnungssignal | HTTP 410 + Header + Body-Format im Vertrag **festgeschrieben** | entschieden (ADR-009) |
| V-08 | Zeitlimit je Versuch | 5 s + 10 s, nicht Teil des eingehenden Vertrags, sondern des ausgehenden | entschieden (ADR-014, O-07) |

---

## 5. Maschinenprüfung

| Prüfung | Ergebnis |
|---|---|
| YAML syntaktisch gültig | **ok** |
| `openapi: 3.0.3`, `info.version: 1.0.0`, `info.description` nicht leer | **ok** |
| globale `security` mit `bearerAuth` | **ok** |
| 18 Operationen, alle mit `operationId`, `summary`, `description`, `responses`, `x-ac-id`, `x-requirement-ids` | **ok** |
| alle `$ref` auflösbar | **ok** |
| jede Operation mit Eingabe hat 400/422 | **ok** |
| 23 Stories im Vertrag auffindbar | **ok** |
| 101 Akzeptanzkriterien im Vertrag auffindbar | **ok** |
| `x-acceptance-coverage` vollständig (101 Einträge, keine unbekannten) | **ok** |
| 24 Anforderungen im Vertrag auffindbar, `x-requirement-coverage` vollständig | **ok** |
| `python3 .agent/tools/traceability_check.py` | **grün** |

---

## 6. Empfehlung für Schritt 4 (Stresstest)

Der Vertrag ist **stabil und vollständig**, aber bewusst **nicht eingefroren**.
Der Stresstest sollte gezielt prüfen:

1. **Grenzfälle des Idempotenzvergleichs** — zwei Nutzlasten, die sich nur in
   der Schlüsselreihenfolge unterscheiden (gleicher Fingerprint?) und zwei, die
   sich in einer Zahl (`1` vs. `1.0`) unterscheiden.
2. **Die 1-MB-Grenze bei `payload_base64`** — gilt die Grenze für die
   *dekodierten* Bytes (so im Vertrag festgelegt) oder für die Base64-Länge?
   Der Vertrag sagt: dekodierte Bytes. Das ist zu bestätigen.
3. **Der Endzustand `rejected` bei einem 4xx ohne Ablehnungssignal** — die
   Unterscheidung `client_error` vs. `permanent_rejection` muss in der
   Oberfläche sichtbar sein.
4. **`delayed` als sichtbarer Zwischenzustand** — trägt der Vertrag die
   Zustandsbezeichnung der Oberfläche?
5. **Der Vertrag bei gleichzeitigen Einlieferungen** — zwei Requests mit
   demselben Idempotenzschlüssel gleichzeitig: was antwortet der zweite?
   (Vertrag: 202 mit `idempotent_replay: true`.)
6. **Der Replay auf einen Endpunkt, der zwischenzeitlich pausiert wurde** —
   der Vertrag sagt 202; das Verhalten ist aber „kein Versuch, Ende ohne
   Zustellung“ (D-03). Die Antwortform ist zu präzisieren.

---

## 7. Offene Punkte dieses Schritts

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-01 | Rate-Limit: Rate und Strategie | Fachbereich | Gate 1 |
| O-02 | Aufbewahrung: Daten, Frist, Bezugspunkt | Fachbereich + Datenschutz | Gate 1 |
| O-03 | Replay-Berechtigung | Fachbereich | Gate 1 |
| O-04 | Verschlüsselung: fachliche Pflicht und Durchsuchbarkeit | Fachbereich + Datenschutz (Verfahren: Architektur, entschieden) | Gate 1 |
| O-05 | Auftragsverarbeitung / AVV | Fachbereich + Recht | Gate 2 |
| O-06 | Versuchsanzahl und Backoff-Werte | Fachbereich | Gate 1 |
| O-07 | Zeitlimit je Versuch | **Architektur — entschieden** (ADR-014) | Gate 1 (Kenntnisnahme) |
