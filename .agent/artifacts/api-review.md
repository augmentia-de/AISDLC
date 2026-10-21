# API-Review — EchoBox

> Schritt 3 (Simplify & Reduktion) · **Schritt 2 (Rücksprung 4 aus Schritt 4)** ·
> Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Geprüfter Vertrag: `docs/api/openapi.yaml` (OpenAPI 3.0.3, `info.version` 1.0.0)
> Vertrag ist ein **Entwurf** — er wird **nach Schritt 4** eingefroren.
> Die 22 Vertragsdefekte des **ersten** Stresstests sind im ersten Rücksprung
> **alle** behoben; die drei Vertragssätze des **zweiten** Stresstests
> (**V-23, V-24, V-25**) sind im **Rücksprung 2** behoben, **V-26** im
> **Rücksprung 3** und **V-27** im **Rücksprung 4**. Danach ist der Vertrag
> **einmal vollständig** durchgesweept: **11** Widersprüche (**V-28…V-38**)
> behoben, **6** neue Befunde (**V-39…V-44**) dokumentiert und **nicht**
> entschieden. Die Bilanz steht in `.agent/artifacts/ruecksprung-bilanz.md`.
> Die früheren Befund-IDs dieses Reviews heißen jetzt **A-01…A-10**, damit sie
> nicht mit den Defekt-IDs **V-01…V-22** des Stresstests verwechselt werden.

---

## 1. Kurzurteil

| Frage | Antwort |
|---|---|
| Deckt der Vertrag alle 23 Stories ab? | **Ja** — jede Story hat mindestens eine Operation oder eine ausdrücklich benannte Nicht-HTTP-Realisierung. |
| Sind alle 101 Akzeptanzkriterien auffindbar? | **Ja** — `x-acceptance-coverage` führt alle 101; die operationellen stehen zusätzlich als `x-ac-id` am Operationsobjekt. |
| Sind alle 24 Anforderungen (FR/NFR) auffindbar? | **Ja** — `x-requirement-coverage` führt alle 24. |
| Fehlen Operationen? | **Nein.** |
| Gibt es Felder oder Operationen ohne Zweck? | **Nein** — nach der Reduktion (Schritt 3) trägt jede Operation mindestens eine Story und jedes Feld ist im Protokoll sichtbar, in der Zustandsmaschine nötig oder ein Mandanten-/Zeitmetadatum. |
| Ist die Fehlersemantik einheitlich? | **Ja** — ein Schema (`Problem`) für alle Fehlerantworten, `code` maschinenlesbar; **jede** Operation mit Request-Body deklariert **400**, jede authentifizierte **429**, jede per ID adressierte **404** (Rücksprung 4, V-29). |
| Ist die Mandantentrennung im Vertrag durchgesetzt? | **Ja** — D-01 (422 bei Mandantenparameter), D-02 (404 bei fremden Ressourcen — **auch** für `service_admin`, V-27), global `bearerAuth`. |
| Sind die bestätigten Vorgabewerte sichtbar? | **Ja** — F-01…F-04 stehen in `info.description`, in `x-contract-rules.defaults` und an der jeweiligen Operation. |

---

## 2. Was der Vertrag abdeckt

### 2.1 Eingehende REST-Schnittstelle (17 Operationen)

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
| 9 | `getEvent` | `GET /api/v1/events/{eventId}` | US-015, US-007 |
| 10 | `listDeliveries` | `GET /api/v1/deliveries` | US-015, US-020, US-007 |
| 11 | `getDeliveryRun` | `GET /api/v1/deliveries/{deliveryRunId}` | US-015, US-012 |
| 12 | `listDeadDeliveries` | `GET /api/v1/dead-deliveries` | US-016, US-020 |
| 13 | `replayDelivery` | `POST /api/v1/deliveries/{deliveryRunId}/replay` | US-013 |
| 14 | `getEndpointMetrics` | `GET /api/v1/endpoints/{endpointId}/metrics` | US-017, US-018 |
| 15 | `enforceRetention` | `POST /api/v1/retention/enforcement` | US-016 |
| 16 | `getHealth` | `GET /health` | US-023 |
| 17 | `getReadiness` | `GET /health/ready` | US-023 |

**Reduziert in Schritt 3:** `listEvents` (`GET /api/v1/events`) ist entfallen —
sie trug keine Story, kein NFR und keinen Akzeptanzpunkt; die Metadatensuche
läuft über `GET /api/v1/deliveries` (Filter `event_id` / `event_type`).
Details: `.agent/artifacts/simplify-report.md`.

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
| Wiederholungsmatrix | 5xx / 408 / 429 / Netzwerkfehler ⇒ wiederholen; 2xx ⇒ fertig; 410 und übrige 4xx ⇒ Ende; **3xx ⇒ Ende** (`rejected` / `client_error`), Redirects werden **nicht** gefolgt (E-08) |
| Dubletten | at-least-once; Deduplizierung über `X-EchoBox-Event-Id`, Wiederholungserkennung über gleiche `Delivery-Run-Id` + verschiedene `Attempt-Id` |
| **Vorgabewerte (F-04)** | 8 Versuche, Basis 1 min, ×2, Deckel 60 min, Streuung ± 20 %; Zeitlimit je Versuch 5 s + 10 s = 15 s |
| **Vorgabewert (F-01)** | 60 Versuche je Endpunkt und Minute, Strategie `delay` |

### 2.3 Fehlersemantik

| Status | `code` | Ausgelöst durch |
|---|---|---|
| 400 | `malformed_request` | kein gültiges JSON |
| 401 | `unauthorized` | kein/ungültiges Token |
| 403 | `forbidden` | Rolle darf es nicht (Totenliste, Replay, Rotation, Aufbewahrung) |
| 404 | `not_found` | fehlende **oder** fremde Ressource (D-02) |
| 409 | `idempotency_conflict` | gleicher Idempotenzschlüssel, abweichende Nutzlast (D-05) — **nur** `submitEvent` (der Idempotenzschlüssel gehört zum Ereignis, V-28) |
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
| **Eine Liste der Ereignisse** | keine Story, kein NFR, kein Akzeptanzpunkt; die Metadatensuche läuft über das Zustellprotokoll — **entfallen in Schritt 3** | `GET /api/v1/deliveries` |
| Mandantenfilter als Query-Parameter | D-01 — der Mandant kommt aus der Identität; der Service-Admin filtert in der Oberfläche | ADR-015 |
| Auswahl des `organisation_id` im Request | D-01 | ADR-015 |
| Ein eigener Replay-„Vorgang“ je Endpunkt | ein Replay erzeugt einen **neuen** Zustelllauf; der alte bleibt erhalten (AC-013-2) | ADR-006, ADR-007 |
| Löschen eines Endpunkts | nicht gefordert; die Aufbewahrung (ADR-012) und das Pausieren decken den Bedarf | — |
| Ein `total` in den Listen | teure Zählung über die wachsende Zustelltabelle; die Aggregation liefert die Zahlen | ADR-011, ADR-017 |
| Sortierparameter | die Sortierung ist implizit `created_at DESC` — die Reihenfolge, in der der Betrieb den Fehler sucht | ADR-017 |
| Webhooks im OpenAPI-`callbacks`-Feld | OpenAPI 3.0.3 kennt `callbacks` nur für Rückkanäle; der ausgehende Vertrag ist eigenständig und deshalb als eigener, eingefrorener Vertragsabschnitt geführt | ADR-004 |

---

## 4. Befunde und offene Punkte im Vertrag

| ID | Befund | Behandlung | Status |
|---|---|---|---|
| A-01 | Die **Nutzlast** als JSON-Wert vs. byte-exakte Bytes | Beides angeboten: `payload` (JSON, kanonisch materialisiert) und `payload_base64` (byte-exakt). Genau eines ist Pflicht. | entschieden (ADR-008, AC-007-3) |
| A-02 | `max_attempts` ohne Wert | Vorgabewert **8** (F-04), je Lauf eingefroren; `null` = keine Grenze gesetzt | **entschieden (F-04)** |
| A-03 | Rate-Limit-Grenzwert | Vorgabewert **60/Endpunkt/Minute**, Strategie `delay` (F-01), je Endpunkt überschreibbar | **entschieden (F-01)** |
| A-04 | Aufbewahrungsfrist | **zwei** Fristen, Vorgabewert je **30 Tage** ab dem letzten Versuch (F-02); die Antwort weist beide Fristen und Stichtage aus | **entschieden (F-02)** |
| A-05 | Replay-Berechtigung | 403 für alle außer dem Empfänger-Admin des Endpunkts | **entschieden (F-03)** |
| A-06 | Ob die Verschlüsselung Pflicht ist | Der Vertrag gibt die Nutzlast nie aus — unabhängig von der Antwort des Fachbereichs bleibt die API gleich | teilweise (ADR-003); fachlich offen (O-04) |
| A-07 | Ablehnungssignal | HTTP 410 + Header + Body-Format im Vertrag **festgeschrieben** | entschieden (ADR-009) |
| A-08 | Zeitlimit je Versuch | 5 s + 10 s, nicht Teil des eingehenden Vertrags, sondern des ausgehenden | entschieden (ADR-008, O-07) |
| A-09 | **Eine** Frist im Aufbewahrungs-Body, aber **zwei** Fristen in der Wirklichkeit | Antwort auf zwei Fristen und zwei Stichtage umgestellt (`event_retention_days`, `delivery_retention_days`, `event_cutoff`, `delivery_cutoff`) | entschieden in Schritt 3 (F-02) |

---

## 4a. Stand nach dem Rücksprung aus Schritt 4 (2026-10-21)

| Thema | Stand |
|---|---|
| **22 Vertragsdefekte** (V-01…V-22 des Stresstests) | **alle behoben** — Bilanz: `.agent/artifacts/ruecksprung-bilanz.md` §1 |
| **S-01** — `service_admin` sieht alle Mandanten | **behoben** über drei `SECURITY DEFINER`-Eigentümerfunktionen (ADR-002 §7); `organisation_id` ist in den mandantenübergreifenden Antworten **im Datensatz** gefüllt |
| **Rate-Limit** | `rate_limit.limit` = **immer** der wirksame Grenzwert (Vorgabe 60); `rate_limit.limited: false` ⇒ abgeschaltet (`limit: 0`). `null` ist als Grenzwert **ausgeschlossen** |
| **Aufbewahrung** | Anker (`last_attempt_at`), zwei Stufen (`marked_*` / `purged_*`), Karenzzeit (`purge_delay_days`), Kaskade und Geltungsbereich (`scope: all`, `tenants[]`) im Vertrag benannt |
| **Byte-Identität** | „Die zugestellten Bytes sind die **Nutzlastbytes des Ereignisses**“ — bei `payload` kanonisch materialisiert (RFC 8785), bei `payload_base64` exakt geliefert |
| **3xx** | Zeile in der Ergebnismatrix: kein Wiederholen, `rejected` / `client_error`; Redirects werden **nicht** gefolgt |
| **Replay** | **einmal** je aufgegebenem Zustelllauf; der zweite ⇒ **409** (auf Datenbankebene erzwiesen) |
| **Operationen** | **17** — keine neue, keine entfernte |
| A-10 | Ereignisliste ohne Abnehmer (`listEvents`) | entfernt — kein Abnehmer, kein Zweck | entschieden in Schritt 3 |

## 4b. Stand nach Schritt 3 (2026-10-21) — die fünf Restbefunde

| ID | Befund | Stand |
|---|---|---|
| **R-03** | `DeadDelivery.abandoned_at` nicht nullable, der Statusfilter der Totenliste ließ `rejected` zu | **behoben**: die Totenliste enthält **ausschließlich aufgegebene** Zustellungen (`abandoned`) — GLOSSARY „Tote Zustellung“, Domain-Modell §3.1, DB-Check `delivery_runs_abandoned_at_ck`. Neues Schema `DeadDeliveryStatus`; `abandoned_at` bleibt Pflicht |
| **R-04** | Cursor-Semantik der mandantenübergreifenden Listen nicht beschrieben | **behoben**: Abschnitt „Pagination“ im Vertrag — Cursor **über die Mandantgrenzen hinweg**, globale Reihenfolge, Sortierschlüssel + UUID als Tie-Breaker, Keyset statt Offset; `enforceRetention` ist **nicht** paginiert |
| **R-05** | `tenants[]` unterschied „eigene Frist“ nicht von „Vorgabe“ | **behoben**: `event_retention_source` / `delivery_retention_source` (`tenant` \| `default`); die Beschreibung „`null` = kein Override“ war falsch und ist korrigiert (der Wert ist die **angewandte** Frist) |
| **R-01** | Karenzzeit ohne beschlossenen Wert; das Beispiel nannte **7 Tage** | **ohne Festlegung**: Beispielwert entfernt, Feld nullable, „kein Vorgabewert“ ausgesprochen. **Bleibt offen** (Fachbereich, Gate 2). Das **Schema-Verhalten** ist jetzt **explizit** (E-13): ohne gesetzten Wert Purge im nächsten Durchlauf — im Vertrag **kein** Wert erfunden |
| **R-02** | `payload_size_bytes` misst die kanonisch materialisierten Bytes (**35**, nicht 38) | kein Befund — folgt zwingend aus V-04 |

## 4c. Stand nach dem **Rücksprung 2** (2026-10-21) — die drei Vertragssätze

| ID | Befund | Stand |
|---|---|---|
| **V-23** | Der Abdeckungseintrag **AC-022-3** nannte den „**Connection-Initializer**“ — wer den Vertrag als Implementierungsgrundlage nimmt, baut einen **verbindungsgebundenen** Mandantenkontext; unter einem Connection-Pool liefert **jede** Abfrage 0 Zeilen | **behoben** (`openapi.yaml:3241`): der Eintrag nennt jetzt die entschiedene Semantik — der Mandant wird **innerhalb der Transaktion**, die auch liest, aus dem JWT gesetzt (`app.set_tenant`, transaktionslokal), **nicht** verbindungsgebunden |
| **V-24** | „mit dem Status **`aktiv`** angelegt“ / „Setzt den Endpunkt auf **`pausiert`**“ — deutsche Wörter in Code-Formatierung; ein Client, der dem Text folgt, sendet `"aktiv"` und bekommt 422 | **behoben** (`:480`, `:697`): die **Werte** `active` / `paused` stehen in Code-Formatierung, die deutsche Übersetzung als Klartext |
| **V-25** | `DeliveryRun.organisation_id`: „**Nur** für `service_admin` gefüllt“ legt eine mandantenübergreifende Protokollliste nahe, die es nicht gibt | **behoben** (`:2446-2456`): Variante „**nur** in mandantenübergreifenden Antworten gefüllt; die Protokollliste ist **mandantengefiltert**“ (E-10). Sie folgt derselben Regel wie `Endpoint.organisation_id` und ist widerspruchsfrei zur Rollenzeile, zur Antwortbeschreibung von `listDeliveries` und zur `x-contract-rules`-Regel |

**Nicht geändert** wurde am Vertrag: die Zahl der Operationen (**17**), die
Schemas (**37**), die Antwort-Komponenten (**11**), die Parameter (**7**), die
`$ref` (**176**), `x-acceptance-coverage` (**101/101**),
`x-requirement-coverage` (**24/24**), die `x-ac-id`- und
`x-requirement-ids`-Zuordnungen und **alle** Beispielwerte.

**Parallel im Schema behoben** (ohne Vertragsänderung, E-10/E-11): die drei
„muss“-Befunde **N-13**, **N-14**, **N-15** und die „sollte“-Befunde **N-16**,
**N-17** — Nachweis in `.agent/artifacts/ruecksprung-bilanz.md` § 5.

## 4d. Stand nach dem **Rücksprung 4** (2026-10-21) — V-27 und der Abschluss-Sweep

### 4d.1 V-27 — die mandantenübergreifende Sicht und der Einzelabruf

| | |
|---|---|
| **Entscheidung** | Die mandantenübergreifende Sicht gilt **nur** für die **drei** Listen-/Betriebsoperationen (Endpunktliste, Totenliste, Aufbewahrung — E-01). Der **Einzelabruf** einer Ressource eines **fremden** Mandanten ergibt **auch für `service_admin`** **HTTP 404** (D-02). Ein **vierter** mandantenübergreifender Leseweg wird **nicht** gebaut. |
| **Umsetzung** | **10 Stellen**: Rollenübersicht und die Abschnitte „Mandant" und „Mandantenübergreifende Listen" in `info.description`; die Operationsbeschreibungen von `getEndpoint`, `getDeliveryRun`, `getEvent` und `getEndpointMetrics`; die Operationsbeschreibungen von `listEndpoints` und `listDeadDeliveries` (die Liste liefert **IDs fremder Mandanten**, deren Detailabruf 404 ergibt); die Regel `x-contract-rules.tenant`. |
| **Nachweis** | Die Klausel „**auch** für `service_admin`" steht in **7** Vertragszeilen (4 Operationen + 3 Regeltexte), `V-27` **11-mal**. Kein neuer Leseweg: weiterhin **17** Operationen, kein **vierter** Eigentümer im Schema. |
| **Nicht geändert** | `docs/adr/ADR-015` §1/§2 enthalten zwei Sätze, die durch V-26/V-27 überholt sind — `docs/adr/` ist in diesem Schritt nicht änderbar → **V-44**. |

### 4d.2 Der Abschluss-Sweep — 11 behobene Widersprüche

| ID | Widerspruch | Korrektur |
|---|---|---|
| **V-28** | `createEndpoint` kündigte **409 `idempotency_conflict`** an, kann aber keinen Idempotenzschlüssel annehmen (kein Feld, kein Header; D-05/FR-011 betreffen Ereignisse; das Schema erzwingt keine Eindeutigkeit auf `endpoints`) | **409 entfernt** |
| **V-29** | `replayDelivery` und `enforceRetention` haben einen Request-Body, aber **keine 400** | **400 `MalformedRequest`** ergänzt |
| **V-30** | Der Cursor im Beispiel war **kein gültiges Base64** und trug den **falschen** Sortierschlüssel (`updated_at` statt `created_at`, ADR-017 §4) | Cursor trägt jetzt `created_at` + UUID-Tie-Breaker, korrekt kodiert |
| **V-31** | Das Beispiel des Rotations-Secrets (`"neues-s3cr3t"`, 12 Zeichen) verletzte `minLength: 16` | auf 27 Zeichen verlängert |
| **V-32** | Dieselbe `delivery_run_id` stand in **drei** Beispielen in **drei** Zuständen (`failed` / `abandoned` / Replay-Vorgänger) | Beispiele entkoppelt — jede Ressource in genau einem Zustand |
| **V-33** | `Problem.violations` „nur bei `validation_failed`" — das 422-Beispiel `tenant_parameter_rejected` führt `violations` | Beschreibung korrigiert |
| **V-34** | `ValidationViolation.field` „JSON-Pointer" — die Beispiele nennen Feldnamen | Beschreibung an die Beispiele angeglichen |
| **V-35** | `DeliveryStatus` beschrieb **8** von **9** Werten — `failed` fehlte | `failed` beschrieben (ADR-008) |
| **V-36** | `X-EchoBox-Rejection` war `required: false` — ADR-009 macht den Header zum **festen** Teil des Ablehnungssignals | `required: true` |
| **V-37** | `Event.deleted_at` versprach einen gesetzten Wert — ADR-012 §3 macht markierte Ereignisse **sofort unsichtbar** (404) | Beschreibung: in der Antwort immer `null` |
| **V-38** | Drei Stellen sagten „`payload: null` gilt als **nicht** gesetzt", das Schema sagt `nullable: false` | Wortlaut an das Schema angeglichen (Beobachtung bleibt 422) |

### 4d.3 Neu dokumentiert und **nicht** entschieden

| ID | Thema | Empfehlung | Owner |
|---|---|---|---|
| **V-39** | Rollen- **oder** Mandantenprüfung zuerst beim Replay (403 vs. 404) | Mandant zuerst ⇒ 404 | Mensch (Gate) |
| **V-40** | Zeitraumfilter des Zustellprotokolls (`from`/`to`) — an welchen Zeitstempel? | `created_at` (wie die Sortierung) | Architektur (Delta-Prüfung) |
| **V-41** | `ReadinessStatus` führt `not_ready`/`unavailable`, die der Vertrag nie erzeugt | Enum auf `ready`/`ok` schärfen | Architektur (Delta-Prüfung) |
| **V-42** | `EndpointMetrics.rate_limit` optional, aber als vorhanden beschrieben | in `required` aufnehmen | Architektur (Delta-Prüfung) |
| **V-43** | `Event.deleted_at` ist ein totes Feld (immer `null`) | Feld entfernen | Architektur (nach dem Freeze) |
| **V-44** | ADR-015 §1/§2 widersprechen V-26/V-27 | Sätze an den entschiedenen Stand bringen | Architektur (Rücksprung) |

---

## 5. Maschinenprüfung

| Prüfung | Ergebnis |
|---|---|
| YAML syntaktisch gültig | **ok** |
| `openapi: 3.0.3`, `info.version: 1.0.0`, `info.description` nicht leer | **ok** |
| globale `security` mit `bearerAuth` | **ok** |
| 17 Operationen, alle mit `operationId`, `summary`, `description`, `responses`, `x-ac-id`, `x-requirement-ids` | **ok** |
| alle `$ref` auflösbar; kein ungenutztes Schema | **ok** |
| jede Operation mit Eingabe hat 400/422 | **ok** |
| 23 Stories im Vertrag auffindbar | **ok** |
| 101 Akzeptanzkriterien im Vertrag auffindbar | **ok** |
| `x-acceptance-coverage` vollständig (101 Einträge, keine unbekannten, keine Dubletten) | **ok** |
| 24 Anforderungen im Vertrag auffindbar, `x-requirement-coverage` vollständig | **ok** |
| `python3 .agent/tools/traceability_check.py` | **grün** (23 Stories, 101 AKs, 24 Anforderungen) |
| Statuscode-Matrix: **429** von **keiner** Operation erreichbar | **behoben** — 429 wird von allen 15 authentifizierten Operationen referenziert; die 503 von `/health/ready` benutzt die Komponente `ServiceUnavailable`; **keine** tote Komponente mehr |
| `tenant_parameter_rejected` im Enum, aber nicht erzeugbar | **behoben** — die 422 trägt zwei Beispiele und ein `oneOf` über `ValidationFailedProblem` und `TenantParameterRejected` |
| **Rücksprung 2** (2026-10-21): `$ref` aufgelöst (176), ungenutzte Objekte (37 Schemas, 11 Antwort-Komponenten, 7 Parameter), Beispiele gegen ihr Schema (Pflichtfelder, Enum, unbekannte Felder) | **ok** — keine Abweichung, kein ungenutztes Objekt |
| **Rücksprung 2**: `grep "Connection-Initializer"` im Vertrag | **kein Treffer** (V-23) |
| **Rücksprung 2**: `grep "\`(aktiv\|pausiert)\`"` im Vertrag | **kein Treffer** (V-24) |
| **Rücksprung 4**: `$ref` aufgelöst, ungenutzte Objekte (37 Schemas, 11 Antwort-Komponenten, 7 Parameter, 1 Header) | **ok** — 177 Verweise, alle auflösbar, **kein** ungenutztes Objekt |
| **Rücksprung 4**: Beispiele gegen ihr Schema (Pflichtfelder, `null` bei nicht-`nullable`, unbekannte Felder, Enum, Längen, Bereiche) | **0 Abweichungen** |
| **Rücksprung 4**: Beispiele **nachgerechnet** (Nutzlastbytes 35, Erfolgsquote 100/120, Backoff 1→2 min, Gesamtfenster 123 min ± 20 %, Fristen/Stichtage, Zählersummen, Base64-Länge des Cursors, Secret-Längen, UUID-Form) | **0 Abweichungen** |
| **Rücksprung 4**: Fehlercode-Vollständigkeit (Body ⇒ 400, authentifiziert ⇒ 429, Adresse ⇒ 404) | **vollständig** (V-29 behoben) |
| **Rücksprung 4**: `grep "V-27"` | **11 Treffer** in 7 Zeilen — 4 Operationen + 3 Regeltexte |

---

## 6. Stand für Schritt 4 (Delta-Prüfung)

> Der **zweite** Stresstest **ist** gelaufen (`.agent/artifacts/stress-test.md`,
> Commit `c9c8038`). Seine drei Vertragssätze (**V-23, V-24, V-25**) sind im
> **Rücksprung 2** behoben; die drei „muss“-Schema-Befunde (**N-13, N-14,
> N-15**) und die „sollte“-Befunde (**N-16…N-20**) sind im **Rücksprung 2**
> behoben — **ohne** Vertragsänderung (Bilanz § 2, § 3, § 5).
>
> **Offen für die Delta-Prüfung (Schritt 4)** ist damit:
>
> - **V-39** … **V-44** — die sechs im **Rücksprung 4** neu dokumentierten
>   Befunde (Bilanz § 10.4). **V-39** (Reihenfolge der Rollen- und der
>   Mandantenprüfung beim Replay) und **V-44** (ADR-015 §1/§2 gegen V-26/V-27)
>   sind die beiden, die vor dem **Einfrieren** eine Antwort brauchen.
> - **E-12** (Härtung der mandantenübergreifenden Sicht) — offen, Gate 2.
> - **R-01** (= E-13) — die Karenzzeit der Aufbewahrung hat keinen
>   beschlossenen Vorgabewert; der Vertrag behauptet **keinen**; die
>   Entscheidung liegt beim **Fachbereich** (Gate 2).
>
> **Erledigt:** **V-26** (entschieden im Rücksprung 3, umgesetzt) und **V-27**
> (entschieden im Rücksprung 4, umgesetzt an 10 Stellen) sowie **11** weitere
> Widersprüche des Abschluss-Sweeps (**V-28…V-38**).

Die frühere Prüfliste des ersten Stresstests (Grenzfälle des
Idempotenzvergleichs, 1-MB-Grenze bei `payload_base64`, `delayed` als
sichtbarer Zwischenzustand, gleichzeitige Einlieferungen, Replay auf einen
pausierten Endpunkt, Wirkung der Vorgabewerte auf die Antwortfelder, Weg zum
Ereignis seit Entfernen der Ereignisliste) ist **abgearbeitet** — die
zugehörigen Befunde V-01…V-22 und die Entscheidungen E-01…E-09 sind in
`.agent/artifacts/ruecksprung-bilanz.md` einzeln belegt.

---

## 7. Offene Punkte

| ID | Thema | Owner | Gate |
|---|---|---|---|
| O-04 | Verschlüsselung: fachliche Pflicht und Durchsuchbarkeit | Fachbereich + Datenschutz (Verfahren: Architektur, entschieden) | Gate 2 |
| O-05 | Auftragsverarbeitung / AVV | Fachbereich + Recht | Gate 2 |
| **R-01** | **Karenzzeit** der Aufbewahrung („markiert“ → „gelöscht“) — kein beschlossener Vorgabewert; der Vertrag behauptet **keinen** (das Schema-Verhalten ist explizit: Purge im nächsten Durchlauf, E-13) | Fachbereich | Gate 2 |
| **E-12** | **Härtung** der mandantenübergreifenden Sicht — eigene Laufzeitrolle `echobox_ops` statt der Allzweckrolle? Die Grenze ist in ADR-002 ehrlich benannt; die Entscheidung ist **offen** | Fachbereich + Plattform | Gate 2 |
| **V-39** | Reihenfolge der Rollen- **oder** der Mandantenprüfung beim Replay (403 vs. 404) — **nicht** entschieden (Bilanz § 10.4) | Mensch (F-03/D-15) | **vor dem Freeze** |
| **V-44** | ADR-015 §1/§2 widersprechen V-26/V-27 — **Korrektur**, keine Entscheidung; `docs/adr/` war in diesem Schritt nicht änderbar | Architektur (Rücksprung) | **vor dem Freeze** |
| **V-40** | Zeitraumfilter des Zustellprotokolls (`from`/`to`) — an welchen Zeitstempel? | Architektur (Delta-Prüfung) | **vor dem Freeze** |
| **V-41** | `ReadinessStatus` führt Werte, die der Vertrag nie erzeugt | Architektur (Delta-Prüfung) | **vor dem Freeze** |
| **V-42** | `EndpointMetrics.rate_limit` optional, aber als vorhanden beschrieben | Architektur (Delta-Prüfung) | **vor dem Freeze** |
| **V-43** | `Event.deleted_at` ist ein totes Feld (immer `null`) | Architektur (nach dem Freeze) | nach dem Freeze |
