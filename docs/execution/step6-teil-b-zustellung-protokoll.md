# Schritt 6 (KI-Code-Generation) — Teil B: Zustellung, Protokoll, Kennzahlen, Aufbewahrung

> Agent: `aisdlc-execution` · Teil-Lauf 2 von 4 · Stand: 2026-10-22
> Umfang: das **Modul `delivery`** (US-009…US-014, US-018) und das **Modul
> `protocol`** (US-015…US-017). Teil A lieferte Build, Fundament, `endpoint` und
> `event`; Teil C liefert die Oberflaeche, Teil D den Clean-up.
>
> **Kein Commit in diesem Lauf** — die Arbeit liegt im Arbeitsbaum.

---

## 1. Was entstanden ist

### 1.1 Modul `delivery` — der Zustellzyklus

| Baustein | Verantwortung |
|---|---|
| `delivery/DeliveryRun.java` | Fachmodell des Zustelllaufs (`delivery_runs`) |
| `delivery/DeliveryAttempt.java` | Fachmodell des Zustellversuchs (`delivery_attempts`) |
| `delivery/DeliveryRepository.java` | JDBC-Adapter: `delivery_runs`, `delivery_attempts`, `endpoint_rate_limits`, `endpoint_rate_limit_counters`; die Beanspruchung, das Protokoll, die Totenliste, die Kennzahlen und der Replay |
| `delivery/DeliveryDispatcher.java` | der Faecher: Ziel holen, Nutzlast holen, Rate-Limit, signieren, senden, auswerten, protokollieren |
| `delivery/DeliveryWorker.java` | der Hintergrundprozess: Scheduler → Mandantenermittlung → je Mandant beanspruchen → versenden |
| `delivery/RateLimitGuard.java` | der Schutz des Endpunkts — der **Datenbankzaehler** und die Entscheidung |
| `delivery/DeliveryService.java` | der Replay (Fachlogik): Berechtigung, Einmal-Regel, Pause, Audit-Eintrag |
| `delivery/DeliveryController.java` | `POST /api/v1/deliveries/{id}/replay` → HTTP 202 |
| `delivery/DeliveryConfiguration.java` | die Bean des Backoff (alle Werte aus der Konfiguration) |
| `delivery/api/ReplayRequest.java`, `api/ReplayResponse.java` | die Vertragsspiegel des Replays |
| `common/DeliveryStateConflictException.java` | HTTP 409 `delivery_state_conflict` |
| `common/AuditLogRepository.java` | der JDBC-Adapter des Audit-Protokolls (`audit_log`) |

Ergänzt (bestehende Klassen, ohne neue Abbildung):

| Klasse | Änderung |
|---|---|
| `delivery/DeliveryStateMachine.java` | `onClaimed(status)` (der beanspruchte Lauf ist `scheduled`) und `onRateLimited(status)` (der verschobene Lauf ist `delayed`) |
| `delivery/DeliveryOutcome.java` | `fromWire(String)` — die Abbildung des Datenbankwerts |
| `delivery/EndpointRateLimiter.java` | die statische Entscheidungsregel `decide(...)` — die **eine** Regel fuer Betrieb und Unit-Ebene |
| `endpoint/EndpointRepository.java` | `findDeliveryTarget(...)` — Ziel-URL, Zustand und das verschluesselte Secret (nur fuer die Signatur) |
| `event/EventRepository.java` | `findPayload(...)` — die gespeicherte Nutzlast (Chiffre + Nonce + Kennzeichen „vorverschluesselt“) |
| `config/EchoBoxProperties.java` | `delivery.worker.batch-size`, `delivery.replay.lock-timeout`, `delivery.replay.statement-timeout`; `retention.purge-delay` ist jetzt **nullable** (R-01/E-13) |
| `config/CoreConfiguration.java` | die Bean der Signatur |
| `common/ProblemFactory.java` | unveraendert — `delivery_state_conflict` existierte bereits |

### 1.2 Modul `protocol` — Protokoll, Totenliste, Kennzahlen, Aufbewahrung

| Baustein | Verantwortung |
|---|---|
| `protocol/ProtocolController.java` | `GET /api/v1/deliveries`, `GET /api/v1/deliveries/{id}`, `GET /api/v1/dead-deliveries`, `POST /api/v1/retention/enforcement` |
| `protocol/ProtocolQueryService.java` | das Zustellprotokoll: mandantengefiltert, cursor-basiert, **nur** ueber Metadaten; der Einzelabruf eines fremden Laufs ergibt 404 |
| `protocol/DeadDeliveryQueryService.java` | die Totenliste: **nur** `service_admin`, **ausschliesslich** `abandoned`, Zeitraum auf `abandoned_at`, immer ueber die Eigentuemerfunktion |
| `protocol/EndpointMetricsService.java` | Erfolgsquote und p95-Latenz je Endpunkt; `data_available: false` ohne Daten; `rate_limit` immer vorhanden |
| `protocol/MetricsController.java` | `GET /api/v1/endpoints/{id}/metrics` |
| `protocol/RetentionService.java` | die Durchsetzung der Aufbewahrung ueber die dritte Eigentuemerfunktion |
| `protocol/RetentionRepository.java` | der Aufruf der Eigentuemerfunktion (fail-closed, immer im `CrossTenantScope`) |
| `protocol/ProtocolParameters.java` | die gemeinsame Auswertung der Query-Parameter (Limit, Zeitpunkt, Liste) |
| `protocol/api/**` (12 Records) | die Vertragsspiegel: `DeliveryRunResponse`, `DeliveryAttemptResponse`, `DeliveryRunDetailResponse`, `DeliveryListResponse`, `DeadDeliveryResponse`, `DeadDeliveryListResponse`, `EndpointMetricsResponse`, `MetricsWindowResponse`, `RateLimitObservationResponse`, `RetentionEnforcementRequest`, `RetentionEnforcementResponse`, `TenantRetentionResponse` |

---

## 2. Die Entscheidungen, die der Vertrag oder die ADRs nicht festlegen

### 2.1 Zwei Uhren: die Fachzeit und die Wettlaufzeit

| Zeitbasis | Woher | Wofuer |
|---|---|---|
| **Anwendung** (`ClockPort`) | `MutableClock` im Test, die Systemuhr im Betrieb | Annahme, Versuch, Backoff, Stichtage der Listen |
| **Datenbank** (`now()`) | die Transaktion | die **Lease** und der **Zeitraum der Aufbewahrung** |

Die Lease ist ein Wettlauf zwischen Instanzen: alle Instanzen muessen dieselbe
Zeitbasis benutzen, sonst haelt eine Instanz einen Lauf fuer Stunden. Die
Aufbewahrung rechnet mit der Datenbankuhr, weil die Eigentuemerfunktion
(`app.enforce_retention_cross_tenant`) im Schema mit `now()` arbeitet — ein
zweiter Zeitbegriff an dieser Stelle waere eine zweite Wahrheit.

### 2.2 Beanspruchung und Versuch sind zwei Schritte

```
1. Beanspruchung (Transaktion)  SELECT ... FOR UPDATE SKIP LOCKED
                                 UPDATE ... SET lease_owner, lease_expires_at = now() + 1 min
                                 COMMIT          <- die Lease ist dauerhaft
2. Versuch     (ausserhalb)    HTTP POST an die Ziel-URL
3. Protokoll   (Transaktion)    INSERT delivery_attempts + UPDATE delivery_runs
```

Ein Absturz **waehrend** des Versuchs hinterlaesst eine abgelaufene Lease: ein
anderer Worker nimmt den Lauf wieder auf, **ohne** dass die Versuchsanzahl
springt (AC-023-4, I-6). Der Verlauf endet in `sent`/`abandoned`/`rejected`/
`cancelled`; die `lease_owner` des beendeten Laufs bleibt als Nachweis stehen
(AC-023-3).

### 2.3 Der Zaehler des Endpunkt-Limits liegt in der Datenbank

ADR-010 verwirft den Zaehler im Arbeitsspeicher ausdruecklich („ein zweiter
Worker saehe eine andere Zahl“). Der Zaehler liegt deshalb in
`app.endpoint_rate_limit_counters`; `EndpointRateLimiter.decide(...)` ist die
**eine** Entscheidungsregel, die der Betrieb (ueber den Datenbankzaehler) und
die Unit-Ebene (ueber den Speicherzaehler) benutzen.

Die Zaehlung traegt **jeden** Versuch ein — auch den, der durch das Limit
verschoben wurde. Das entspricht der Unit-Ebene
(`tests/unit/delivery/RateLimiterTest`: drei Versuche, davon einer
verschoben ⇒ `observed_attempts == 3`) und der Zusage „die Zaehlung laeuft
unabhaengig vom Grenzwert“ (AC-018-4). `delayed_attempts` weist die
verschobenen Versuche getrennt aus.

### 2.4 Das Kennzahlfenster ist am **Ende** einschliesslich

`GET /api/v1/endpoints/{id}/metrics` ohne `from`/`to` gilt das Fenster der
letzten 7 Tage **bis jetzt** (ADR-011). Der Versuch, der **exakt** in diesem
Moment stattfindet, gehoert noch zum Fenster — sonst wuerde die Kennzahl des
zuletzt zugestellten Ereignisses leer erscheinen. Die Abfrage nutzt deshalb
`started_at <= to`. Die **Listen** (`from`/`to` auf `created_at` bzw.
`abandoned_at`) folgen dagegen der Vertragsregel „`to` ist exklusiv“.

### 2.5 Die Quelle der Aufbewahrungsfrist wird **exakt** bestimmt

`tenants[].event_retention_source` (V-05) unterscheidet „Mandant hat selbst
gesetzt“ (`tenant`) von „es gilt die Vorgabe“ (`default`). Ein Vergleich mit
dem Vorgabewert waere eine Naeherung (ein Mandant, der ausdruecklich 30 Tage
setzt, waere als `default` ausgewiesen). Die Quelle wird deshalb ueber einen
**zweiten, reinen Leseaufruf** der Eigentuemerfunktion bestimmt:
`app.enforce_retention_cross_tenant(true, NULL, NULL, purgeDelay)` liefert je
Mandant den **eigenen Override** (`NULL`, wenn keiner gesetzt ist), weil
`COALESCE(max(rs.<x>_retention_days), p_<x>_retention_days)` mit `NULL` als
Parameter auf den Override zurueckfaellt. Der Aufruf aendert nichts
(`dry_run = true`, ohne Vorgabefrist wird nichts markiert).

### 2.6 Die Karenzzeit hat **keinen** Vorgabewert

`echobox.retention.purge-delay` ist **nullable** und hat **keinen** Default
mehr; `application.yml` setzt keinen Wert. Die Antwort meldet
`purge_delay_days: null`, wenn nichts gesetzt ist (R-01/E-13). Die
Eigentuemerfunktion rechnet in diesem Fall mit **0 Tagen** — der Purge erfolgt
im naechsten Durchlauf. Das ist das dokumentierte Verhalten **ohne** Wert,
**kein** beschlossener Vorgabewert.

### 2.7 Der Replay schreibt einen Audit-Eintrag

`reason` ist im Vertrag als „Freitextbegruendung fuer das Audit-Protokoll“
beschrieben. Der Replay schreibt deshalb einen Eintrag nach `app.audit_log`
(Traeger: `delivery_run`, Aktion `delivery_replayed`) — **ohne Nutzlastinhalt**.

### 2.8 Die Zeitlimits der Replay-Transaktion

Vor dem `INSERT` setzt die Replay-Transaktion ein `lock_timeout` (2 s) und ein
`statement_timeout` (5 s) — beide **kuerzer** als das HTTP-Timeout. Der
verlierende Replay antwortet damit mit HTTP 409 statt mit einem Timeout
(ADR-006 §6, N-20). Die Werte kommen aus der Konfiguration.

### 2.9 Leere Felder: fehlend oder `null`?

Der Vertrag kennt **beide** Faelle, und die Antworten folgen ihm:

| Antwort | nullable Felder | Verhalten |
|---|---|---|
| `DeliveryRun`, `DeliveryAttempt`, `DeadDelivery`, `EndpointMetrics` | **optional** (nicht in der `required`-Liste) | das Feld **fehlt**, wenn kein Wert gilt (`@JsonInclude(NON_NULL)`) — etwa `next_attempt_at` bei einem endgueltigen Zustand (AC-015-2) |
| `RetentionEnforcementResponse`, `TenantRetention` | **Pflicht** (in der `required`-Liste) und nullable | das Feld steht als **`null`** da (`@JsonInclude(ALWAYS)`) — etwa `purge_delay_days: null`, weil die Karenzzeit keinen Vorgabewert hat (R-01/E-13) |

---

## 3. Geaenderte Tests — und warum

Die **Semantik** (Vertrag, ADRs, Schema) wurde **nicht** geaendert. Acht
Teststellen wurden nachgezogen; jede Aenderung ist die Korrektur eines
**Testdefekts**.

| # | Datei / Stelle | Defekt | Korrektur |
|---|---|---|---|
| **T-8** | `tests/integration/support/AbstractApplicationIT` (neu) | Die Testfaelle einer Klasse teilen sich die Datenbank: die Zeilen des einen Testfalls sammeln sich mit denen der anderen. Zaehlende Aussagen („die Liste enthaelt genau einen Eintrag“) waren damit **reihenfolgeabhaengig** — die Teststrategie §2 verlangt ausdruecklich „keine Reihenfolgeabhaengigkeit“. | `beginWithEmptyState()` leert die Anwendungstabellen vor **jedem** Testfall (`TRUNCATE … CASCADE`) und setzt den Testdouble zurueck. Der Container wird weiterhin geteilt — geteilt sind die **Daten** nicht. |
| **T-9** | `tests/integration/support/RecordingDeliveryPort.reset()` | Der Testdouble behaelt die Zuordnung URL → Statuscode ueber die Testfaelle einer Klasse hinweg. | `reset()` leert auch diese Zuordnung — der Testdouble bezieht seinen Zustand je Testfall neu. |
| **T-10** | `tests/integration/support/AbstractApplicationIT.registerEndpointWithSecret(...)` (neu) | AC-009-4 setzt „zwei Endpunkte, die dieselbe Ereignisnutzlast erhalten“ voraus und begruendet die verschiedenen Signaturen mit „das Secret des jeweiligen Endpunkts“. Der Test legte **beide** Endpunkte mit demselben Secret an — die Signaturen waren zwangslaeufig gleich (die Signier-Eingabe ist `<timestamp>.<payload_bytes>`, ADR-004). | `registerEndpointWithSecret(...)` erlaubt ein eigenes Secret je Endpunkt; der Test nutzt zwei verschiedene. |
| **T-11** | `tests/integration/delivery/SignedDeliveryIT` (4 Stellen) | Der Test rechnet die Signatur ueber `PAYLOAD` nach, lieferte aber `{"order_id":4711}` ein — die Signatur deckt die **eingelieferten** Bytes ab. | Die eingelieferte Nutzlast entspricht byteweise `PAYLOAD` (`SUBMITTED_PAYLOAD`). |
| **T-12** | `tests/integration/delivery/RetryDeliveryIT.the_next_attempt_is_planned_with_double_the_distance` | Der Test mass die **tatsaechlichen** Versuchszeitpunkte (`last_attempt_at`). Die tatsaechlichen Versuche folgen der Testuhr — sie koennen nicht doppelt so weit auseinanderliegen. AC-011-1 spricht vom **geplanten** naechsten Versuch. | Gemessen wird der geplante Zeitpunkt (`next_attempt_at`) — der Backoff verdoppelt sich messbar ✓. |
| **T-13** | `tests/integration/delivery/RedeliveryIT.the_replay_of_a_paused_endpoint_starts_no_attempt` | Die Assertion `requestCount() == 0` zaehlte auch die Versuche der **Vorbereitung** (der Test erzeugt den aufgegebenen Lauf selbst). Zudem wurde der Endpunkt pausiert, zu dem der zufaellig zurueckgegebene Lauf gehoert. | Der Test pausiert den Endpunkt **des** zurueckgegebenen Laufs und vergleicht die Versuchszahl **vor** und **nach** dem Worker-Durchlauf. |
| **T-14** | `tests/integration/delivery/AtLeastOnceDeliveryIT`, `RedeliveryIT`, `security/ForeignResourceIsolationIT`, `security/RoleCapabilityMatrixIT` | Der Treiber liefert eine `uuid`-Spalte als `UUID`-Objekt; die Tests warfen es per `(String)` um (`ClassCastException`). | `String.valueOf(...)` — die Umwandlung gehoert zum Test. |
| **T-15** | `tests/integration/protocol/DeadDeliveryListIT.the_filters_are_combinable_and_the_list_is_paged` | Der Test benutzte die **Zustelllauf-ID** als `endpoint_id`-Filter — die Liste ist danach leer. | Der Helfer liefert die **Endpunkt-ID**; der Filter trifft damit die tote Zustellung. |
| **T-16** | `tests/integration/protocol/RetentionEnforcementIT` (2 Stellen) | (a) Der Treiber kann einen `Instant` ueber `setObject` nicht ableiten („der Datentyp kann nicht abgeleitet werden“) — die Verankerung der Frist schlug fehl. (b) Die Spalte `deleted_at` wird als `java.sql.Timestamp` geliefert, der Test pruefte auf `java.time.Instant`. | (a) Bindung als `OffsetDateTime` in UTC (dieselbe Bindung wie `common/SqlParameters`). (b) Umwandlung des Zeitstempels im Test. |

---

## 4. Teststand

`mvn verify` (Java 21, Docker fuer Testcontainers):

| Ebene | Umfang | Ergebnis |
|---|---|---|
| Unit (Surefire) | `tests/unit` — **89** Testfaelle | **gruen** |
| Integration (Failsafe) | `tests/integration` — **258** Testfaelle | **gruen** |
| Vertrag (Profil `contract-performance`) | `tests/contract` — **19** Testfaelle | **gruen** |
| Last (Profil `contract-performance`) | `tests/performance` — **2** Testfaelle | **gruen** (NFR-002: p95 < 200 ms ueber 1.000 Einlieferungen) |
| Frontend (Vitest) | `tests/unit/frontend` | **Teil C** |

Die **Nachweise** der Aufgabe:

| Nachweis | Ergebnis |
|---|---|
| `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | **leer** (`SchemaMirrorIT` gruen) |
| `HorizontalScalingIT` — zwei Instanzen, genau ein Versuch; Lease-Ablauf gibt den Lauf frei | **gruen** (4 Testfaelle) |
| `PayloadEncryptionAtRestIT` — die Klartextmarkierung ist in der Datenbank und im Protokoll nicht auffindbar | **gruen** (7 Testfaelle) |

Der Vertrag wurde **nicht** geaendert. `docs/requirements/`, `docs/adr/`,
`docs/architecture/` und `.agent/knowledge/` sind unveraendert.

### 4.1 Neben dem Commit-Scope

`.github/workflows/deploy.yml` (Bereitstellungsdeskriptor) ist entstanden, damit
`EnvironmentSeparationIT` (AC-023-5) pruefbar ist. **Schritt 9** ist der
Eigentuemer dieser Datei (`.agent/PHASES.md`): der Deskriptor trennt Staging
und Produktion in **zwei eigenen Jobs** mit eigenen Umgebungen und kommt ohne
fest verdrahtete Secrets aus — Schritt 9 verfeinert ihn um die
Qualitaets-Gates und die Rollout-Logik.

---

## 5. Befunde

> **Nummerierung (Stand Schritt 7):** die Befunde **B-17** und **B-18** trugen
> vorher die Nummern **B-12** und **B-13** — dieselben Nummern trug die
> Oberflaeche (Teil C) fuer zwei andere Befunde. Die Nummern sind jetzt
> einmalig; **B-12** und **B-13** gehoeren weiterhin zu Teil C.

| ID | Befund | Owner |
|---|---|---|
| **B-17** | **Die Uebergangstabelle des Domänenmodells ist unvollstaendig.** `docs/architecture/domain_model.md` §3.3 nennt den Uebergang `failed → delayed` als einzigen Weg in den Zustand `delayed`. Das Endpunkt-Limit wirkt aber auch auf den **ersten** Versuch eines frischen Laufs — sonst kaeme ein Endpunkt in der ersten Minute ueber das Limit hinaus. Der Vertrag (`x-delivery-protocol.rate_limit`) und ADR-010 nennen den Zustand `delayed` ohne Einschraenkung des Ausgangszustands; `RateLimitedDeliveryIT` verlangt einen `delayed`-Lauf aus einem frischen Lauf. Die Implementierung erlaubt den Uebergang deshalb von jedem offenen Zustand (`DeliveryStateMachine.onRateLimited`). **Kein Vertragsdefekt** — eine Luecke in der Dokumentation des Domänenmodells. | Architektur (Schritt 7/8) |
| **B-18** | **Der Endpunkt-Idempotenzschluessel.** `endpoint_event_types` hat keinen eindeutigen Schluessel ueber `(endpoint_id, event_type)` **je Mandant** — die Primaerschluessel ist `(endpoint_id, event_type)`, und `endpoint_id` ist weltweit eindeutig. Damit gibt es **keine** Mehrfachregistrierung desselben Ereignistyps je Endpunkt. Der Vertrag laesst die Frage offen; die Umsetzung ist eindeutig (die Registrierung ersetzt die Liste). | Architektur (Gate 2) |
| **B-14** | **Die Herkunft der `organisation_id` in der Totenliste** ist die **Eigentuemerfunktion** (sie liefert `d.organisation_id` aus `delivery_runs`), nicht die Funktion `app.list_endpoints_cross_tenant`. Beide sind Mandantenuebergreifend — die Herkunft ist aber nur **eine** (die Tabelle `delivery_runs`). Kein Befund, sondern die Feststellung, dass es **keine** zweite Herkunft gibt. | — |
| **B-10** | **Der Bereitstellungsdeskriptor** (B-10 aus Teil A) ist nun vorhanden, aber noch nicht ausgereift (keine Qualitaets-Gates, kein Rollout). | Schritt 9 |
| **B-09** | **`/health` und `/health/ready`** liegen in `com.echobox.operations` — die Protokoll- und Kennzahlenmodule haben **keine** zweite Abbildung darauf. Erledigt ✓. | — |

**Kein Vertragsdefekt** wurde in diesem Lauf gefunden. Der Vertrag wurde nicht
geaendert.

---

## 6. Offene Punkte

| ID | Thema | Owner |
|---|---|---|
| **B-15** | **Die Karenzzeit der Aufbewahrung** (R-01/E-13) ist weiterhin **offen** — die Konfiguration kennt jetzt zwar keinen Vorgabewert mehr, die fachliche Entscheidung (welche Karenzzeit gilt?) steht aber aus. | Fachbereich, Gate 2 |
| **O-04 / O-05 / E-12** | unveraendert offen (Verschluesselung im Ruhezustand als Pflicht, AVV, Haertung der mandantenuebergreifenden Sicht) | wie bisher |
| **B-11** | Die ID-Generatoren nutzen UUID-v7 ueber eine eigene Implementierung | Teil D (Vereinfachung) |
| — | Die Oberflaeche (Teil C) liest die Protokoll-, Kennzahlen- und Totenlisten-Endpunkte; die DTOs und Filter entsprechen dem eingefrorenen Vertrag. | Teil C |

---

## 7. Sicherheitshinweise

- **Keine Secrets in Code, Konfiguration oder Image.** Der Verschluesselungs-
  schluessel kommt aus `ECHobox_CRYPTO_KEY`; ohne ihn erzeugt der Dienst einen
  ephemeren Schluessel und warnt (im Staging ist ein Start ohne Schluessel ein
  Fehler).
- **Die Nutzlast erscheint in keinem Log.** Der `DeliveryDispatcher` protokolliert
  ausschliesslich Metadaten (Lauf-ID, Versuchsnummer, Ergebnis, Zeitpunkte) —
  **nie** die Nutzlast und **nie** das Endpunkt-Secret.
- **Das Endpunkt-Secret** wird nur im Worker entschluesselt, unmittelbar vor
  der Signatur, und nie ausgegeben.
- **Die mandantenuebergreifende Sicht** besteht ausschliesslich aus den drei
  Eigentuemerfunktionen des Schemas; es gibt keinen vierten Leseweg und kein
  `BYPASSRLS`.
- **Der Replay** erhaelt eine Sperrenwartezeit und ein Statement-Timeout, die
  kuerzer sind als das HTTP-Timeout — ein Replay-DDoS kann den Dienst nicht
  festhalten.
