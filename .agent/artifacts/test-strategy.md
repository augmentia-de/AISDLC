# Teststrategie — EchoBox

> Schritt 5 (Test-Driven Setup) · Agent: `aisdlc-execution` · Stand: 2026-10-21
> Begleitdokument zu `.agent/artifacts/test-matrix.md` (dort die Matrix aller 101 AKs).
>
> **Zustand: rot.** Es existiert noch kein Anwendungscode. Die Tests sind
> vollstaendig geschrieben und schlagen rot — aus dem richtigen Grund: die
> **Implementierung fehlt**, nicht die Testlogik. Die Testquellen wurden
> kompiliert (Java) und typgeprueft (TypeScript); die einzigen fehlenden
> Symbole sind die noch nicht existierenden Produktionsklassen.

---

## 1 — Wann welcher Test Ebene

| Ebene | Regel | Wofuer sie in EchoBox |
|---|---|---|
| **Unit** | Eine Regel, keine Infrastruktur, kein HTTP, keine Datenbank. Das Ziel ist ein **reiner** Produktionsbaustein (Wertobjekt, Berechnung, Zustandsuebergang). | URL-Pruefung, Backoff und Streuung, HMAC-Signatur, AEAD-Chiffre und Fingerprint, Zustandsautomat der Zustellung, Versuchsgrenze, Endpunkt-Rate-Limit, Nutzlast-Groessengrenze, Idempotenzentscheidung, Ziel-URL-Validierung. |
| **Integration** | Der Vertrag oder die Persistenz wird gegen die **echte** Abhaengigkeit geprueft. Eine Mock-Datenbank beweist nichts: die Mandantentrennung ist nur gegen echtes PostgreSQL belegbar. | HTTP-Vertrag gegen die laufende Anwendung (MockMvc + echte Datenbank), Mandantentrennung auf Datenbankebene, Verschluesselungsnachweis in Datenbank und Protokollen, Rollenmatrix ueber alle 17 Operationen, Parallelbetrieb zweier Worker. |
| **Vertrag** | Der **eingefrorene Vertrag** selbst ist das Testorakel. Ein Vertragstest schlaegt fehl, wenn eine Zusage fehlt — unabhaengig davon, ob der Code laeuft. | Signaturverfahren und Header-Name (AC-009-3), Ablehnungssignal (AC-014-2), die als „immer vorhanden" zugesagten Felder (V-46). |
| **E2E** | Eine Bedien-Reise ueber mehrere Bausteine im **echten** Browser. Hier zaehlt, was der Mensch sieht: Tastatur, Fokus, Kontrast, Screenreader-Semantik. | Die drei Seiten: Endpunkte, Protokoll, tote Zustellungen; der axe-Scan (AC-021-2); der Kontrast (AC-021-3) — nur der Browser berechnet Farben. |
| **Last** | Eine **Lastannahme** (Anzahl, Dauer, Ziel) wird geprueft. Gehoert nicht in die normale Suite. | NFR-002: p95 < 200 ms ueber **1.000** Einlieferungen. |

### 1.1 Die drei Ebenen, die der erste Stresstest als fehlend erkannt hat (N-12)

| Ebene | Testdatei | Belegt |
|---|---|---|
| **Lasttest** | `tests/performance/EventIntakeLoadTest.java` | NFR-002 / AC-005-5: p95 der Annahmeantwort unter 200 ms ueber 1.000 Einlieferungen |
| **Parallelbetrieb** | `tests/integration/deployment/HorizontalScalingIT.java` | AC-023-3: genau eine Instanz fuehrt den Versuch aus · AC-023-4: Lease-Ablauf gibt den Lauf frei, ohne dass die Versuchsanzahl sprunghaft steigt |
| **Verschluesselungsnachweis** | `tests/integration/event/PayloadEncryptionAtRestIT.java` | AC-007-1: keine Klartextmarkierung in Tabellen, Indizes, Dump und Server-Protokoll · AC-007-2: keine Klartextmarkierung im Anwendungsprotokoll |

---

## 2 — Testdaten: jeder Test erzeugt seine Daten selbst

- **Keine geteilten Fixtures, keine Reihenfolgeabhaengigkeit.** Jeder Test legt
  seine Mandanten (`m-1`, `m-2`), seine Endpunkte und seine Ereignisse selbst an.
- **Jeder Test hat eigene Idempotenzschluessel** (`idem-1`, `enc-3`, `limit-7`, …).
  Dadurch koennen zwei Testfaelle derselben Klasse nebeneinander laufen, ohne
  sich in die Quere zu kommen.
- **Ein Container fuer alle.** `PostgresTestContainer` startet **einen**
  PostgreSQL-16-Container je JVM und teilt ihn. Die *Daten* sind getrennt, die
  *Datenbank* nicht — das ist der Unterschied zwischen Testdaten und
  Testumgebung.
- **Die Uhr ist testbar.** `MutableClock` (Port `ClockPort`) startet an einem
  festen Zeitpunkt (`2026-10-21T09:15:22.481Z`). Backoff, Lease-Ablauf und
  Aufbewahrung rechnen mit dieser Uhr statt mit der Wanduhr — der Test wartet
  nie.
- **Der Empfaenger ist ein Testdouble.** `RecordingDeliveryPort` (Port
  `DeliveryPort`) zeichnet auf, was gesendet wurde, und liefert die
  vorgegebenen Antworten — inklusive **Timeout** und **Netzwerkfehler**. Die
  HTTP-Mechanik selbst wird in `SignedDeliveryIT` gegen einen **echten** Server
  (WireMock) geprueft.
- **Die Klartextmarkierung** ist ein festes Wort: `TOP-SECRET-PAYLOAD`. Es darf
  in **keinem** Artefakt auftauchen — das ist der Messwert, nicht eine
  Randnotiz.

---

## 3 — Testcontainers-Setup

```java
// tests/integration/support/PostgresTestContainer.java
new PostgreSQLContainer<>("postgres:16-alpine")
    .withDatabaseName("echobox")
    .withUsername("postgres")
    .withPassword("postgres");
```

- **Warum Testcontainers und nicht H2/SQLite:** die Stack-Entscheidung
  (`.agent/knowledge/stack.md`) schliesst sie aus. H2 kennt weder RLS noch
  `bytea` noch die partiellen Indizes — die Tests wuerden genau das nicht
  pruefen, worum es geht (AC-022-1, AC-022-2).
- **Die Anwendung migriert selbst.** Die Verbindungsdaten kommen ueber
  `@DynamicPropertySource` in die Spring-Konfiguration; Flyway legt das Schema
  an. Der Test legt das Schema **nicht** selbst an — sonst pruefte er eine
  Kopie statt der Migration.
  - **Ausnahme:** `RowLevelSecurityIT` spielt die Migration direkt ein
    (`src/main/resources/db/migration/V1__initial_schema.sql`), weil er das
    Schema *gegen sich selbst* prueft: jede mandantenfuehrende Tabelle muss RLS
    tragen, und die Policy muss ohne gesetzten Mandanten **fail-closed** sein.
- **Der Rollenwechsel ist Teil des Tests.** Die Anwendung verbindet sich als
  `echobox_app` (kein BYPASSRLS). Der RLS-Test setzt die Rolle mit
  `SET ROLE echobox_app` und ruft `app.set_tenant(...)` **innerhalb** der
  Transaktion, die auch liest — so, wie es ADR-002 §3 vorschreibt.
- **Der Empfaenger-Server** laeuft als WireMock auf einem freien Port
  (`dynamicPort`).

### 3.1 Konfigurationsschluessel, auf die sich die Tests verlassen

Alle Werte kommen aus der Betriebskonfiguration, **kein Wert ist im Code
verdrahtet** (`DeliveryProperties`, `echobox.retention.*`).

| Schluessel | von | wofuer |
|---|---|---|
| `echobox.delivery.worker.enabled` | `false` (Standard in den ITs) | der Test steuert den Worker selbst (`worker.runOnce()`) — sonst ist die Reihenfolge der Versuche nicht eindeutig |
| `echobox.delivery.backoff.base-delay` | `60s` / `1s` in Tests | Backoff-Berechnung |
| `echobox.delivery.backoff.multiplier` | `2.0` | Verdopplung |
| `echobox.delivery.backoff.max-delay` | `60m` | Deckel |
| `echobox.delivery.backoff.jitter` | `0.0` in Tests | Streuung aus, damit die Verdopplung exakt messbar ist; die Streuung selbst prueft `JitterTest` |
| `echobox.delivery.max-attempts` | `8` (Vorgabe F-04), `1`/`2`/`3` in Tests | Versuchsgrenze (AC-012-4, AC-013) |
| `echobox.delivery.rate-limit.limit` | `60` (Vorgabe F-01), `2` in Tests | Endpunkt-Limit (AC-018) |
| `echobox.delivery.rate-limit.window-seconds` | `60` | Zeitfenster |
| `echobox.delivery.rate-limit.strategy` | `delay` | Strategie (F-01) |
| `echobox.delivery.http.connect-timeout` / `read-timeout` | `5s` / `10s` | ADR-008 |
| `echobox.delivery.lease-duration` | `PT1M` | Lease-Ablauf (AC-023-4) |
| `echobox.retention.event-days` / `delivery-days` / `purge-delay` | `30` / `30` / `0` | Aufbewahrung (F-02) |
| `echobox.tenant.id` | `m-1` | Mandanteninstanz (AC-023-2) |

---

## 4 — Parallelbetrieb (AC-023-3, AC-023-4)

Der Test laesst **zwei Worker-Instanzen** gleichzeitig gegen dieselbe Datenbank
arbeiten und prueft, dass **genau eine** den Versuch ausfuehrt.

```
Instanz A  ──┐
             ├──▶  app.delivery_runs  (FOR UPDATE SKIP LOCKED + Lease)
Instanz B  ──┘
```

- Beide Instanzen rufen `worker.runOnce("<Instanz-ID>")` **gleichzeitig** an
  (CountDownLatch als Startsignal). Die Instanz-ID wandert in
  `delivery_runs.lease_owner` — sie ist im Test sichtbar
  (`the_claimed_run_names_the_claiming_instance()`).
- Erwartung: **genau ein** HTTP-Versuch, **genau eine** Zeile in
  `delivery_attempts`, **genau ein** Zustelllauf mit Status `sent`.
- **Lease-Ablauf:** der Test setzt `lease_owner` und `lease_expires_at` auf
  einen abgelaufenen Wert (simulierter Absturz) und prueft, dass eine andere
  Instanz den Lauf uebernimmt — **ohne** dass `attempt_count` sprunghaft
  steigt (I-6, AC-023-4).

---

## 5 — Der rote Zustand als TDD-Ziel

Der rote Zustand ist das **Ziel**, nicht ein Mangel:

| Erwarteter Fehler | Bedeutung |
|---|---|
| `cannot find symbol: class EchoBoxApplication` | die Anwendung fehlt — genau richtig |
| `cannot find symbol: class DeliveryWorker` | der Hintergrundprozess fehlt |
| `cannot find symbol: class PayloadCipher` | die Verschluesselung fehlt |
| `Cannot find module '../../../src/main/frontend/src/app/endpoints/page'` | die Oberflaeche fehlt |
| `FEHLT: src/main/resources/db/migration/V1__initial_schema.sql` | die Migration fehlt |
| `FEHLT: Dockerfile` | das Image fehlt |

**Kein Test darf aus einem anderen Grund rot sein.** Verboten sind:
Syntaxfehler, falsch importierte Klassen, Tippfehler in Erwartungswerten, ein
fehlendes `tearDown`, das die naechste Klasse beschaedigt.

### 5.1 Nachweis, dass die Testquellen in Ordnung sind

| Pruefung | Ergebnis |
|---|---|
| `javac` ueber alle 59 Java-Testquellen mit Stubs der fehlenden Produktionsklassen | **kompiliert** — keine Syntaxfehler; die einzigen fehlenden Symbole sind die noch nicht existierenden Produktionsklassen |
| `tsc --noEmit --strict` ueber alle 9 TypeScript-Testquellen mit Stubs der fehlenden Seiten | **kompiliert** — keine Syntax- oder Typfehler |
| `python3 .agent/tools/traceability_check.py` | gruen |
| `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | leer |

Die Stubs lagen ausserhalb des Projekts und sind **nicht** Teil des Commits —
sie sind ein Pruefwerkzeug, kein Produktionscode.

---

## 6 — Der Testvertrag fuer Schritt 6

Die Tests binden eine **Produktions-API**. Schritt 6 implementiert gegen diesen
Vertrag; weicht die Implementierung ab, weicht der Test ab — dann wird der Test
in Schritt 6 nachgezogen, nicht die Semantik.

### 6.1 Reine Bausteine (Unit-Ebene)

| Klasse | Paket | Genutzte API |
|---|---|---|
| `EndpointTargetUrl` | `com.echobox.endpoint` | `static of(String)`, `value()`, `host()`; wirft `ValidationException` mit `field()`/`code()` |
| `ValidationException` | `com.echobox.common` | `field()`, `code()` |
| `PayloadCipher` | `com.echobox.crypto` | `Ciphertext encrypt(byte[])`, `byte[] decrypt(Ciphertext)`, `byte[] fingerprint(byte[])`; `Ciphertext` = Record mit `bytes()`, `nonce()` |
| `SignatureService` | `com.echobox.crypto` | `sign(secret, Instant, byte[])`, `verify(secret, Instant, byte[], headerValue)`, `static signingInput(Instant, byte[])`, `static timestampHeaderValue(Instant)` |
| `DeliveryStatus` | `com.echobox.delivery` | Enum mit `isTerminal()` |
| `DeliveryOutcome` | `com.echobox.delivery` | `static classify(Integer status, boolean rejectionSignal)`, `isFinal()` |
| `DeliveryStateMachine` | `com.echobox.delivery` | `isTerminal(status)`, `canTransition(from, to)`, `onEndpointPaused(status)`, `nextAttemptAtFor(status)` |
| `BackoffPolicy` | `com.echobox.delivery` | `delayForAttempt(n)`, `nextAttemptAt(Instant, n)`, `baseDelay()`, `multiplier()`, `maxDelay()` |
| `AttemptLimit` | `com.echobox.delivery` | `maxAttempts()`, `isExhausted(count)`, `statusWhenExhausted()` |
| `EndpointRateLimiter` | `com.echobox.delivery` | `acquire(UUID, Instant) -> RateLimitDecision`, `observedAttempts(UUID)`, `delayedAttempts(UUID)` |
| `RateLimitDecision` | `com.echobox.delivery` | Record `permitted()`, `waitTime()`, `retryAt()` |
| `DeliveryHeaders` | `com.echobox.delivery` | Konstanten der Header-Namen + `build(...)` |
| `PayloadSize` | `com.echobox.event` | `MAX_PAYLOAD_BYTES`, `MAX_REQUEST_BODY_BYTES`, `exceedsPayloadLimit(long)`, `exceedsRequestBodyLimit(long)` |
| `PayloadEncoding`, `EventPayload` | `com.echobox.event` | `PLAINTEXT`/`PRE_ENCRYPTED`, `preEncrypted(byte[])`, `plaintext(byte[])`, `isPreEncrypted()`, `sizeBytes()` |
| `StoredEvent`, `IdempotencyDecision` | `com.echobox.event` | `IdempotencyDecision.of(Optional<StoredEvent>, byte[] fingerprint)` |

### 6.2 Ports und Adapter

| Element | Erwartung an Schritt 6 |
|---|---|
| `com.echobox.delivery.DeliveryPort` | **Interface** — die Anwendung spricht den Empfaenger ausschliesslich ueber dieses Port. Nur so ist die Zustelllogik ohne Netz pruefbar (und nur so ersetzt `RecordingDeliveryPort` die HTTP-Implementierung). |
| `DeliveryRequest` / `DeliveryResponse` | Record mit `targetUrl`, `headers`, `payload`, `contentType` bzw. `statusCode`, `body`, `headers`, `failure` (`NONE`/`TIMEOUT`/`NETWORK_ERROR`) |
| `com.echobox.delivery.DeliveryHttpClient` | `implements DeliveryPort`; Konstruktor `(connectTimeout, readTimeout)`; die Timeouts aus `DeliveryProperties` |
| `com.echobox.delivery.DeliveryWorker` | Bean **auch bei deaktiviertem Zeitplan**; `runOnce()` und `runOnce(String instanceId)`; die Instanz-ID landet in `lease_owner` |
| `com.echobox.common.ClockPort` | `Instant now()`; im Betrieb die Systemuhr, im Test `MutableClock` |
| `com.echobox.EchoBoxApplication` | `@SpringBootApplication` in `com.echobox` (die Tests liegen in `com.echobox.*` und finden die Konfiguration so automatisch) |
| Mandant im Token | Claim `organisation_id`; die Rollen als Claim `roles` **und** als Autoritaet `ROLE_<rolle>` — der Test setzt beides, damit die Zuordnung offen bleibt |
| Mandant-Parameter | Body, Query **und** Header ⇒ **422** `tenant_parameter_rejected` (D-01) |
| Migration | `src/main/resources/db/migration/V1__initial_schema.sql` — byte-identisch mit `.agent/knowledge/schema.sql` (ADR-016, `SchemaMirrorIT`) |
| Frontend-Seiten | `src/main/frontend/src/app/{endpoints,deliveries,dead-deliveries}/page.tsx` — **Default-Export**, eine Komponente je Seite |

### 6.3 Build-Anschluss (Schritt 6)

- **Maven:** `tests/unit`, `tests/integration`, `tests/contract`, `tests/performance`
  als zusaetzliche Test-Quellverzeichnisse (`build-helper-maven-plugin` oder
  `testSourceDirectory` je Ausfuehrung); Surefire fuer `*Test`, Failsafe fuer
  `*IT`; die Vertrags- und Performance-Tests in einem eigenen Profil, damit
  der Lasttest **nicht** in der normalen Suite laeuft.
- **Vitest:** `tests/unit/frontend` als Testumgebung (`jsdom`), Setup-Datei mit
  `@testing-library/jest-dom`.
- **Playwright:** `tests/e2e` als `testDir`, `baseURL` aus der Konfiguration;
  `webServer` startet die Anwendung (oder sie laeuft bereits im Betrieb).
- **Flyway und `BEGIN; … COMMIT;`:** siehe Befund **B-03** in der Testmatrix.
  Die Migration muss transaktionsneutral laufen (`execute-in-transaction=false`),
  sonst startet die Anwendung nicht.

---

## 7 — Dokumentierte Grenzen der Testabdeckung

| Grenze | Auswirkung | Gegenmassnahme |
|---|---|---|
| Der Lasttest misst die **Verarbeitungszeit** (MockMvc), nicht die HTTP-Transportzeit. | Die p95-Aussage gilt fuer die Serverarbeit. | In Schritt 6 gegen die laufende Instanz bestaetigen (B-04). |
| Der **WAL** des Testcontainers ist von aussen nicht lesbar. | Der Journal-Nachweis stuetzt sich auf `pg_dump` und das Server-Protokoll. | Die Nutzlast kann nur als Chiffre in den WAL gelangen (`events.payload_ciphertext`). Dokumentiert als B-05. |
| Der **Kontrast** (AC-021-3) ist nur im echten Browser pruefbar. | Der Komponententest (jsdom) prueft die uebrigen axe-Regeln. | Der Kontrast liegt im Playwright-Test `tests/e2e/a11y.spec.ts`. |
| Der **E2E**-Test bedient die API auf Netzwerkebene mit Vertragsdaten (`page.route`). | Der E2E-Lauf beweist die Bedienung und die Barrierefreiheit, nicht den HTTP-Vertrag. | Der HTTP-Vertrag wird in den Integrationstests gegen die echte Anwendung geprueft. |
| **AC-023-1** „Image nicht privilegiert, ohne Secrets" wird am **Dockerfile** geprueft, nicht am laufenden Image. | Der Build (Schritt 6) ist die Bedingung. | `the_image_runs_as_non_root_and_contains_no_secrets()` schlaegt fehl, solange das `Dockerfile` fehlt. |
| **AC-023-5** „getrennte Konfigurationen und Secrets" wird an Konfigurationsdateien und am Bereitstellungsdeskriptor geprueft. | Die eigentliche Pipeline-Pruefung gehoert nach Schritt 9. | `EnvironmentSeparationIT` verlangt eine eigene Staging-Konfiguration, keine fest verdrahteten Secrets und getrennte Bereitstellungsschritte. |

---

## 8 — Abbruchkriterien fuer Schritt 6

Schritt 6 stoppt und meldet zurueck, wenn eines der folgenden gilt:

| # | Kriterium | Warum |
|---|---|---|
| **A-1** | Die Anwendung startet nicht, weil die Migration an einem eigenen `BEGIN; … COMMIT;` scheitert. | Dann sind **alle** Integrationstests aus dem falschen Grund rot (B-03). |
| **A-2** | Die Mandantentrennung ist nur in der Anwendung durchsetzbar (die RLS-Policy greift nicht). | NFR-003 verlangt die Durchsetzung auf Datenbankebene; ein Anwendungsfilter waere ein Ruecksprung nach Schritt 2. |
| **A-3** | `app.set_tenant` muss **ausserhalb** der lesenden Transaktion gesetzt werden (z. B. im Servlet-Filter oder beim Verbindungsaufbau). | Dann liefert jede Abfrage 0 Zeilen — der Dienst waere unbenutzbar (ADR-002 §3, N-01). |
| **A-4** | Der Vertrag und die Implementierung weichen unabhaengig ab (Feld, Statuscode, Header-Name). | Der Vertrag ist eingefroren; die Abweichung geht nach Schritt 2, nicht in den Code. |
| **A-5** | Die Rollenpruefung muss im Datenbank-Laufzeitrollenmodell nachgebaut werden (etwa eine Rolle je Endbenutzerrolle). | Die Rollenpruefung bleibt in der Anwendung (ADR-002 §7, ADR-015). |
| **A-6** | Die Zustellung kann nicht ueber ein Port-Interface angebunden werden (`DeliveryPort`). | Dann ist die Zustelllogik nur ueber das Netz pruefbar — die Teststrategie traegt nicht. |
| **A-7** | Ein Testfall aus der Matrix braucht ein Feld oder eine Operation, die im Vertrag nicht steht. | Kein Feld und keine Operation erfinden; Ruecksprung nach Schritt 2. |
| **A-8** | Die Verschluesselung im Ruhezustand laesst sich nicht so umsetzen, dass die Nutzlast nur als Chiffre gespeichert wird. | FR-012/NFR-004 sind nicht verhandelbar; Ruecksprung nach Schritt 2. |

---

## 9 — Was in diesem Schritt ausdruecklich **nicht** getan wurde

- **Kein Anwendungscode.** `src/` ist unberuehrt; kein `pom.xml`, kein
  `package.json`, kein `Dockerfile` — das ist Schritt 6.
- **Der Vertrag wurde nicht geaendert.** `docs/api/openapi.yaml` ist seit
  `8627bb6` eingefroren; die beiden Befunde B-01 und B-02 sind Meldungen, keine
  Aenderungen.
- **Keine Aenderung** an `docs/requirements/`, `docs/api/`, `docs/adr/`,
  `docs/architecture/`, `.agent/knowledge/`, `docs/Anforderung.md`.
- **Keine Testfaelle ohne Akzeptanzkriterium:** jeder der 305 Testfaelle
  nennt seine AC-ID im Namen — die Zuordnung ist maschinell geprueft.
- **Keine erfundenen Anforderungen:** die acht Rollenrechte ohne eigenes AK
  (V-52) stammen aus der Rollenmatrix des eingefrorenen Vertrags.
