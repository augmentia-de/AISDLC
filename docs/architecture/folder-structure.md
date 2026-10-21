# Zielstruktur für `src/` — EchoBox

> Schritt 3 (Simplify & Reduktion), korrigiert in Schritt 4 (Grill-Me-Stresstest)
> · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> **Noch kein Code.** Diese Datei beschreibt, **wo** der Code in Schritt 6
> entstehen wird. Die Pfade sind mit den in
> `docs/requirements/traceability_matrix.json` geplanten Test- und Codepfaden
> abgeglichen — sie stimmen überein.

---

## 1. Gesamtbild

```
/
├── src/
│   ├── main/
│   │   ├── java/com/echobox/          ← Backend (Java 21, Spring Boot)
│   │   │   ├── endpoint/              Endpunkte verwalten
│   │   │   ├── event/                 Ereignisse einliefern
│   │   │   ├── delivery/              Zustellung und Zustellgarantie
│   │   │   ├── crypto/                Signatur, Verschlüsselung, Secret
│   │   │   ├── protocol/              Protokoll, Tote Zustellungen, Kennzahlen
│   │   │   ├── tenant/                Mandantenkontext und RLS-Durchsetzung
│   │   │   ├── config/                Konfiguration (Spring beans)
│   │   │   └── common/                Fehlerbild, Problem-Schema, Zeit, IDs
│   │   ├── resources/
│   │   │   ├── application.yml
│   │   │   ├── application-staging.yml
│   │   │   └── db/migration/
│   │   │       └── V1__initial_schema.sql   ← Kopie von .agent/knowledge/schema.sql
│   │   └── frontend/                  ← Oberfläche (Next.js 15, React 19, TS strict)
│   │       └── src/
│   │           ├── app/                App Router: Endpunkte, Protokoll, Tote Zustellungen
│   │           ├── features/           Fachbereiche der Oberfläche
│   │           ├── components/         Wiederverwendbare Bausteine (DataTable)
│   │           ├── lib/                API-Client, Typen, Formatierung
│   │           └── types/              Typen aus dem Vertrag (generiert/gepflegt)
│   └── test/                          → gehört `tests/` (Schritt 5), nicht `src/`
├── tests/
│   ├── unit/                          JUnit 5 + Vitest
│   ├── integration/                   Testcontainers (PostgreSQL 16)
│   ├── e2e/                           Playwright
│   ├── contract/                      Vertragstests gegen docs/api/openapi.yaml
│   └── performance/                   Lasttest: NFR-002 (p95 < 200 ms über 1.000 Einlieferungen)
├── docs/                              (unverändert, Ergebnisbereiche)
└── Dockerfile, docker-compose.yml     (Schritt 6)
```

> **Hinweis zu `src/test`:** Die Traceability-Matrix nennt ausschließlich
> `tests/…`. Deshalb liegen alle Tests unter `tests/` und **nicht** unter
> `src/test/java`. Maven/Test-Build-Konfiguration und Surefire/Failsafe
> bekommen die abweichenden Testquellverzeichnisse in Schritt 6 explizit
> gesetzt.

---

## 2. Backend — Modul für Modul

### 2.1 `endpoint` — Endpunkte verwalten (US-001…US-004, US-019)

| Datei | Verantwortung |
|---|---|
| `endpoint/EndpointController.java` | REST-Adapter: registrieren, lesen, aktualisieren, pausieren, reaktivieren, Secret rotieren. Validiert am Rand, bildet Fehler auf das `Problem`-Schema ab. |
| `endpoint/EndpointService.java` | Fachlogik: Endpunktzustand, Pause-Semantik (D-03: keine Warteschlange, kein Nachliefern, offene Wiederholung abbrechen), Secret-Rotation (FR-003), rollenabhängige Sichtbarkeit (FR-004). |
| `endpoint/Endpoint.java` | Fachmodell: Ziel-URL, Secret (verschlüsselt), Ereignistypen, Status, `secretVersion`. |
| `endpoint/EndpointQueryService.java` | Lesezugriffe für Endpunktliste und Einzelabruf (mandantengefiltert). |
| `endpoint/EndpointRepository.java` | JDBC-Adapter: `endpoints`, `endpoint_event_types`, `endpoint_secret_history`. |
| `endpoint/EndpointEventTypes.java` | Wertobjekt: die abonnierten Ereignistypen. |

### 2.2 `event` — Ereignisse einliefern (US-005…US-008)

| Datei | Verantwortung |
|---|---|
| `event/EventController.java` | REST-Adapter: `POST /api/v1/events` (202), `GET /api/v1/events/{id}`. |
| `event/EventService.java` | Fachlogik: Idempotenz je (Mandant, Schlüssel) mit 409 bei abweichender Nutzlast (D-05), Größengrenze (NFR-001), Verschlüsselung, Anlegen der Zustellläufe. |
| `event/Event.java` | Fachmodell: Typ, Schlüssel, Chiffre, Fingerprint, Status. |
| `event/EventRepository.java` | JDBC-Adapter: `events`. |
| `event/IdempotencyStore.java` | Abgleich der Idempotenzschlüssel inklusive Fingerprint-Vergleich. |

### 2.3 `delivery` — Zustellung und Zustellgarantie (US-009…US-014, US-018)

| Datei | Verantwortung |
|---|---|
| `delivery/DeliveryWorker.java` | Hintergrundprozess: arbeitet **mandantenweise** — Scheduler → `app.tenant_ids_for_worker()` → je Mandant `set_tenant` → beanspruchen; je Mandant eine eigene Transaktion. Beansprucht fällige Läufe, plant Wiederholungen, beendet Läufe. |
| `delivery/DeliveryService.java` | Fachlogik des Zustelllaufs: Zustandsautomat, Replay (neuer Lauf, neue ID, Versuchszahl zurückgesetzt). Ein Vorgängerlauf ist **einmal** replaybar; der zweite Versuch scheitert an `UNIQUE (replay_of)` ⇒ SQLSTATE **23505** ⇒ HTTP 409 `delivery_state_conflict` (ADR-015 §6). Die Replay-Transaktion setzt **vor** dem `INSERT` ein `lock_timeout` und ein `statement_timeout`, die **kürzer** sind als das HTTP-Timeout — der verlierende Replay antwortet mit 409 statt mit einem Timeout (N-20). |
| `delivery/DeliveryController.java` | REST-Adapter: `POST /deliveries/{id}/replay` (US-013). |
| `delivery/DeliveryStateMachine.java` | Die Zustandsübergänge des Zustelllaufs — die einzige Stelle, die Zustände ändert. |
| `delivery/DeliveryDispatcher.java` | Fächer: nimmt einen beanspruchten Lauf, holt Nutzlast, signiert, versendet, wertet aus. |
| `delivery/DeliveryHttpClient.java` | HTTP-Adapter (Port `DeliveryPort`): Timeouts (5 s / 10 s), TLS, Antwortauswertung. |
| `delivery/DeliveryHeaders.java` | Die Namen und Werte der ausgehenden Header — **eingefroren**. |
| `delivery/DeliveryOutcome.java` | Ergebnis eines Versuchs (`success`, `retryable_failure`, `permanent_rejection`, `client_error`, `network_error`, `timeout`, `cancelled`). |
| `delivery/BackoffPolicy.java` | Verdopplung + Streuung; **alle Werte konfigurierbar** (D-09). Vorgabe: 8 Versuche, Basis 1 min, ×2, Deckel 60 min, ± 20 % (F-04). |
| `delivery/RetryScheduler.java` | Setzt `next_attempt_at`; plant nie einen Zeitpunkt in der Vergangenheit (AC-011-5). |
| `delivery/PayloadSource.java` | Liefert die zu sendenden Bytes: entschlüsselt oder reicht die Chiffre unverändert weiter (FR-013). |
| `delivery/EndpointRateLimiter.java` | Zähler je Endpunkt und Zeitfenster; `delay`/`block`; Vorgabe 60 Versuche je Endpunkt und Minute mit `delay` (D-14, F-01). |
| `delivery/DeliveryRepository.java` | JDBC-Adapter: `delivery_runs`, `delivery_attempts`. |

### 2.4 `crypto` — Krypto (US-003, US-007, US-009)

| Datei | Verantwortung |
|---|---|
| `crypto/PayloadCipher.java` | AEAD (AES-256-GCM) für die Nutzlast; `encrypt`, `decrypt`, `fingerprint`. |
| `crypto/SignatureService.java` | HMAC-SHA256 über `<timestamp>.<payload_bytes>`; setzt `X-EchoBox-Signature` (ADR-004). |
| `crypto/SecretStore.java` | Port `SecretPort`: Endpunkt-Secrets und Schlüssel aus dem Secret-Store; nie aus der Datenbank. |

### 2.5 `protocol` — Protokoll und Betrieb (US-015…US-018)

| Datei | Verantwortung |
|---|---|
| `protocol/ProtocolController.java` | REST-Adapter: **Zustellprotokoll** (`GET /deliveries`, `GET /deliveries/{id}`), **Totenliste** und **Aufbewahrung** erzwingen. Der Replay liegt im `delivery`-Modul (Traceability-Matrix, Schritt 1). |
| `protocol/ProtocolQueryService.java` | Protokollsuche **nur über Metadaten** (D-11), cursor-basiert paginiert. |
| `protocol/DeadDeliveryQueryService.java` | Totenliste mandantenübergreifend für `service_admin` — über `app.list_dead_deliveries_cross_tenant` (nur mit `CrossTenantScope`). **Der Status ist fest verdrahtet:** die Funktion hat **keinen** Statusparameter und liefert ausschließlich `abandoned` (ADR-002 §7, N-14). Der Query-Parameter `status` des Vertrags hat nur den Wert `abandoned` (`DeadDeliveryStatus`) und wird **nicht** weitergereicht. |
| `protocol/EndpointMetricsService.java` | Erfolgsquote und p95-Zustelllatenz je Endpunkt; `data_available: false` ohne Daten (FR-032). **Markierte Versuche zählen nicht mit:** sie verlieren mit ihrem Zustelllauf die Sichtbarkeit (N-13). |
| `protocol/MetricsController.java` | REST-Adapter der Kennzahlen. |
| `protocol/RetentionService.java` | Durchsetzung der Aufbewahrung — mandantenübergreifend über `app.enforce_retention_cross_tenant` (nur mit `CrossTenantScope`). Zwei Fristen, Vorgabe je 30 Tage ab dem **letzten Versuch** (D-12, F-02); Anker je Datenart, Stufen *markieren* → *Purge* nach der Karenzzeit, Kaskade Ereignis → Läufe → Versuche. **Ohne gesetzte Karenzzeit** (`purge_delay_days: null`) wird mit 0 Tagen gerechnet ⇒ der Purge erfolgt im **nächsten** Durchlauf — das dokumentierte Verhalten **ohne** Vorgabewert (E-13, R-01 bleibt offen). Die logische Löschung (`deleted_at`) setzt **nur** diese Eigentümerfunktion — die Applikationsrolle kann sie nicht setzen (N-16). |

### 2.6 `tenant` — Mandantentrennung (US-004, US-022)

| Datei | Verantwortung |
|---|---|
| `tenant/TenantContext.java` | Hält `organisation_id` und die Rollen aus dem Token. |
| `tenant/TenantConnectionInitializer.java` | Setzt `app.set_tenant(...)` **innerhalb** der Transaktion, auf der die Abfrage läuft (AC-022-3). **Transaktionsgebunden, nicht verbindungsgebunden** — siehe die Konvention „Mandant“ in §4. Der Name ist historisch; die Traceability-Matrix (Schritt 1) nennt ihn so — die **Semantik** ist transaktionsgebunden und in ADR-002 §3 festgeschrieben. |
| `tenant/TenantFilter.java` | Liest **nur** das JWT und füllt `TenantContext` (request-scoped). **Kein** Datenbankzugriff, **kein** `set_tenant()` — der Mandant wird erst in der Transaktion gesetzt, die auch liest. |
| `tenant/CrossTenantScope.java` | Setzt `app.cross_tenant = 'service_admin'` **transaktionslokal**, **nachdem** die JWT-Rolle geprüft ist, und ruft danach die schmalen Eigentümerfunktionen des Schemas auf (`app.list_endpoints_cross_tenant`, `app.list_dead_deliveries_cross_tenant`, `app.enforce_retention_cross_tenant`). Ohne diesen Scope liefern die Funktionen **keine** Zeile (fail-closed, ADR-002 §7). **Nur** `service_admin` darf sie benutzen. |

> **Warum der Unterschied entscheidend ist:** `app.set_tenant()` nutzt
> `set_config(…, is_local = true)` — der Wert gilt **nur innerhalb der
> Transaktion**, die ihn gesetzt hat. In einem Servlet-Filter oder beim
> Anlegen einer Pool-Verbindung gesetzt, wäre er beim ersten Query entweder
> schon zurückgesetzt (Autocommit) oder auf einer **anderen** Connection
> gesetzt — `current_setting('app.organisation_id', true)` liefert dann `NULL`,
> die RLS-Policy greift **fail-closed** und **jede** Abfrage liefert 0 Zeilen
> (ADR-002 §2).

### 2.7 `common` und `config`

| Datei | Verantwortung |
|---|---|
| `common/Problem.java` | Das einheitliche Fehlerschema (RFC-7807-artig). |
| `common/ProblemFactory.java` | Erzeugt Fehlerantworten — **eine** Stelle für alle Fehler. |
| `common/GlobalExceptionHandler.java` | `@RestControllerAdvice`: ordnet Ausnahmen den Statuscodes zu (400/401/403/404/409/413/422/**429**/**503**/500). |
| `common/PageResponse.java` | Cursor-Paginierung (`limit`, `has_more`, `next_cursor`) — Feldnamen **wie im Vertrag** (`components.schemas.Page`). |
| `common/CursorCodec.java` | Erzeugt und prüft die opaken Cursor (HMAC-signiert, 422 bei ungültigem Cursor). |
| `common/ClockConfig.java` | Port `ClockPort` — die testbare Uhr. |
| `common/IdGenerator.java` | Port `IdGenerator` — UUID-v7, damit IDs zeitlich sortierbar bleiben. |
| `config/TenantInstanceConfiguration.java` | `echobox.tenant.id` — bindet eine Instanz an einen Mandanten (AC-023-2). |
| `config/DeliveryProperties.java` | **Alle** Zahlenwerte der Zustellung: Backoff, Versuchsgrenze, Timeouts, Rate-Limit, Aufbewahrung. Kein Wert ist im Code verdrahtet. |
| `config/SecurityConfiguration.java` | JWT-Prüfung, Rollen, Zugriffsregeln je Operation. |

---

## 3. Oberfläche (Next.js 15 / React 19 / TypeScript strict)

| Pfad | Verantwortung |
|---|---|
| `src/app/layout.tsx` | Grundgerüst, Sprungmarke zum Hauptinhalt, Fokus-Management (AC-021-1). |
| `src/app/endpoints/page.tsx` | Endpunktseite: Liste, Registrierung, Pause, Reaktivieren, Secret-Rotation (US-019). |
| `src/app/deliveries/page.tsx` | Zustellprotokoll mit Filtern (Endpunkt, Status, Zeitraum) (US-020, AC-020-1). |
| `src/app/dead-deliveries/page.tsx` | Tote Zustellungen (US-020, AC-020-2). |
| `src/features/endpoints/EndpointList.tsx` | Tabelle der Endpunkte — Status, Ziel-URL ohne Secret, Ereignistypen (AC-019-1). |
| `src/features/endpoints/EndpointForm.tsx` | Registrierungsformular mit Feldvalidierung (AC-019-2). |
| `src/features/protocol/DeliveryTable.tsx` | Tabelle der Zustellvorgänge: Zustand, Versuche, letzter Versuch, Antwortcode, nächster Versuch (AC-020-1). |
| `src/features/protocol/DeliveryFilters.tsx` | Filterleiste; kombinierbare Filter (AC-015-4). |
| `src/features/dead/DeadDeliveryTable.tsx` | Tabelle der toten Zustellungen mit Leerzustand (AC-020-3). |
| `src/components/DataTable.tsx` | Wiederverwendbare, zugängliche Tabelle: semantische Struktur, Zeilenkopf, `aria-sort`, sichtbarer Fokus (AC-021-4). |
| `src/components/StatusBadge.tsx` | Zustandsdarstellung — Status wird **nicht** allein über Farbe transportiert (AC-021-3). |
| `src/components/EmptyState.tsx` | Verständlicher Leerzustand statt leerer Tabelle (AC-020-3). |
| `src/components/FieldError.tsx` | Fehlerdarstellung am Feld, verknüpft per `aria-describedby` (AC-019-2). |
| `src/lib/apiClient.ts` | HTTP-Client: trägt das Bearer-Token, wertet das `Problem`-Schema aus. |
| `src/lib/cursor.ts` | Cursor-Paginierung im Client. |
| `src/types/api.ts` | **Typen aus dem Vertrag.** `docs/api/openapi.yaml` ist die Quelle; die Typen werden daraus abgeleitet (OpenAPI-Typgenerator) und **nicht** von Hand gepflegt. |

> **Barrierefreiheit (NFR-005, US-021).** Grundregeln für jede Komponente
> oben: vollständige Tastaturbedienung, sichtbarer Fokus, logische
> Reihenfolge, Kontrast ≥ 4,5:1 (Text) bzw. ≥ 3:1 (Bedienelemente),
> Statusinformation zusätzlich als Text, semantische Landmarks. Der
> automatisierte axe-Scan (AC-021-2) läuft in Schritt 5 gegen alle drei Seiten.

---

## 4. Konventionen

| Thema | Konvention |
|---|---|
| Paketnamen | `com.echobox.<modul>`; ein Modul je Verzeichnis, keine Ausnahmen. |
| Schichtentrennung | `*Controller` (HTTP) → `*Service` (Fachlogik) → `*Repository` (JDBC). Die Fachlogik kennt **keine** HTTP- und **keine** JDBC-Typen. |
| Ports | Alle nach außen gerichteten Zugriffe laufen über Ports (`DeliveryPort`, `SecretPort`, `ClockPort`, `IdGenerator`, Repositories). |
| SQL | **Nur** vorbereitete Statements über `JdbcTemplate` mit benannten Parametern. Kein String-Konkatenieren, kein dynamisch zusammengesetztes SQL. Jede Abfrage trägt den Mandanten. |
| **Mandant** | `app.set_tenant(...)` wird **innerhalb** der Transaktion ausgeführt, die auch liest, und **immer** vor der ersten Abfrage. Kein Zugriff auf `app.*` außerhalb einer solchen Transaktion, kein `REQUIRES_NEW` nach dem Setzen, kein Autocommit. Der Kontext ist **transaktionsgebunden**, nicht verbindungsgebunden — ein Connection-Pool ändert daran nichts (ADR-002 §2-3). |
| **Logische Löschung** | `deleted_at` setzt die Applikationsrolle **nicht** selbst: PostgreSQL prüft den `USING`-Ausdruck einer Policy auch gegen die **neue** Zeile, ein `UPDATE … SET deleted_at` endet mit „new row violates row-level security policy“. Die logische Löschung läuft **ausschließlich** über die Eigentümerfunktion `app.enforce_retention_cross_tenant` (ADR-002 §7, N-16). |
| Fehler | Ausnahmen aus `common` (`TenantParameterException`, `IdempotencyConflictException`, `PayloadTooLargeException`, …); die Abbildung auf HTTP geschieht **ausschließlich** in `GlobalExceptionHandler`. |
| Kein Klartext | Nutzlast und Secrets erscheinen in **keinem** Log, **keiner** Fehlermeldung und **keinem** `toString()`. |
| Konfiguration | Alle Zahlenwerte kommen aus `DeliveryProperties` (ENV). Keine Zahl ist im Code verdrahtet. |
| Tests | Unit-Tests ohne Datenbank (Mocks der Ports); Integrationstests gegen eine **echte** PostgreSQL-16-Instanz (Testcontainers) — die Mandantentrennung ist gegen eine echte Datenbank zu belegen (AC-022-1, AC-022-2). **Parallelbetrieb:** zwei Worker-Instanzen gegen dieselbe Datenbank (AC-023-3) und ein Lease-Ablauf im Test (AC-023-4) brauchen eigene Integrationstests. **Last:** NFR-002 (p95 < 200 ms über 1.000 Einlieferungen) braucht einen Lasttest (`tests/performance/`) — der nicht in die normale Unit-/Integrationstest-Suite gehört. |

---

## 5. Planungsauftrag an OpenCode (Schritt 6)

Der Orchestrator steuert OpenCode im **Plan-Modus** an. Auftrag:

> Lies `docs/api/openapi.yaml` und erstelle einen Schritt-für-Schritt-Plan für
> die Implementierung der neuen API-Stubs, DTOs und Typen.

**Zielpfade des Auftrags, auf die Projektstruktur abgebildet:**

| Auftrag | Projektpfad |
|---|---|
| `src/types` (DTOs und Typen) | Backend: `src/main/java/com/echobox/<modul>/api/dto/` — die DTOs sind die Java-Spiegel der Schemas aus `components/schemas` des Vertrags. Oberfläche: `src/main/frontend/src/types/api.ts` — aus demselben Vertrag generiert. |
| `src/controllers` (Controller) | Backend: `src/main/java/com/echobox/<modul>/<Modul>Controller.java` — je Operation ein `operationId` aus dem Vertrag. Oberfläche: `src/main/frontend/src/features/<bereich>/` — Datenquelle der Seite. |

**Reihenfolge des Plans** (Abhängigkeit, keine Halbfertigen Zwischenstände):

1. Gemeinsame Schemas zuerst: `Problem`, `Page`, `ValidationViolation`.
2. `endpoint`-Modul (EndpunktCreateRequest, Endpoint, EndpointList) — keine Abhängigkeiten.
3. `crypto`-Modul (PayloadCipher, SignatureService, SecretStore) — Abhängigkeit von `endpoint`.
4. `event`-Modul (EventIntakeRequest, EventIntakeResponse, Event) — Abhängigkeit von `crypto`.
5. `delivery`-Modul (DeliveryRun, DeliveryAttempt, ReplayRequest/Response) — Abhängigkeit von `event`.
6. `protocol`-Modul (DeliveryList, DeadDeliveryList, EndpointMetrics, Retention) — Abhängigkeit von `delivery`.
7. `common` (ProblemFactory, GlobalExceptionHandler, CursorCodec) — vor jedem Controller.
8. `tenant`-Modul (TenantContext, TenantConnectionInitializer) — vor jedem Repository-Zugriff.
9. Oberfläche — erst, wenn die Controller stehen und die Vertragsfelder feststehen.

**Regeln für den Plan:**

- Kein Feld und keine Operation erfinden, die nicht im Vertrag stehen.
- Kein Feld ohne Zweck; eine Operation ohne Zweck wird **entfernt**, nicht
  dokumentiert.
- Jede Operation bekommt genau einen Vertragstest (Statuscode, Pflichtfelder,
  Fehlerformat); eine Antwort mit **undokumentierten** Feldern ist ein Fehler.
- Der Vertrag wird in Schritt 6 **nicht** geändert. Was sich nicht ausdrücken
  lässt, geht zurück nach Schritt 2.
