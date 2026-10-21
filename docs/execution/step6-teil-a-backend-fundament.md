# Schritt 6 (KI-Code-Generation) — Teil A: Backend-Fundament

> Agent: `aisdlc-execution` · Teil-Lauf 1 von 4 · Stand: 2026-10-21
> Umfang dieses Laufs: **Build-Grundlage, Fundament (`common`, `security`,
> `crypto`, `tenant`), Modul `endpoint`, Modul `event`** sowie die
> **reinen Zustell-Bausteine** (`delivery`), die die Testquellen zum
> Uebersetzen brauchen. Die **Zustelllogik** und das Modul `protocol` folgen in
> den naechsten Teil-Laeufen.

---

## 1. Was entstanden ist

### 1.1 Build (`pom.xml`)

| Baustein | Entscheidung |
|---|---|
| Java / Spring Boot | Java 21, Spring Boot 3.3.5 |
| Abhaengigkeiten | `web`, `security`, `oauth2-resource-server`, `validation`, `jdbc`, `actuator`, `flyway-core` + `flyway-database-postgresql`, `postgresql` |
| Test-Stack | `spring-boot-starter-test`, `spring-security-test`, AssertJ, Testcontainers (JUnit 5 + PostgreSQL), WireMock |
| Test-Quellverzeichnisse | `tests/unit` und `tests/integration` als **zusaetzliche** Test-Quellverzeichnisse (`build-helper-maven-plugin`) — die Tests liegen **nicht** unter `src/test/java` (Traceability-Matrix, `docs/architecture/folder-structure.md` §1) |
| Surefire / Failsafe | Surefire `**/*Test.java`, Failsafe `**/*IT.java` |
| Vertrags- und Lasttests | eigenes Profil `contract-performance` (`tests/contract`, `tests/performance`) — der Lasttest laeuft **nicht** in der normalen Suite (Teststrategie §1, §6.3) |
| **Testtrennung** | Failsafe mit `forkCount=1`, `reuseForks=false`: **jede Testklasse laeuft in einem eigenen JVM** und damit mit einem eigenen PostgreSQL-Container. Ohne diese Trennung waeren die Testdaten der Klassen ueber den gemeinsamen Container voneinander abhaengig — die Teststrategie §2 verlangt ausdruecklich „keine Reihenfolgeabhaengigkeit“ und „die Daten sind getrennt“. |

### 1.2 Migration (B-03)

`src/main/resources/db/migration/V1__initial_schema.sql` ist eine **byte-identische**
Kopie von `.agent/knowledge/schema.sql` (`diff` → leer, `SchemaMirrorIT` gruen).

**B-03 (Flyway-Transaktion):** das kanonische Schema enthaelt ein eigenes
`BEGIN; … COMMIT;`. Flyway wuerde die Transaktion sonst doppelt oeffnen. Loesung:
`spring.flyway.execute-in-transaction=false` — die Migration laeuft
**transaktionsneutral**. Das kanonische Schema wurde **nicht** veraendert.

Zusaetzlich geprueft: die Migration laeuft gegen ein echtes PostgreSQL 16
fehlerfrei **und idempotent** (zweimal ausgefuehrt).

### 1.3 Fundament

| Klasse | Verantwortung |
|---|---|
| `com.echobox.EchoBoxApplication` | `@SpringBootApplication` in `com.echobox` |
| `common/Problem`, `common/ProblemFactory`, `common/GlobalExceptionHandler` | **eine** Stelle fuer alle Fehlerantworten (ADR-015 §4): 400/401/403/404/409/413/422/429/500/503 mit dem `code` des Vertrags; 400 vs. 422 wird an der Ausnahme unterschieden (Syntaxfehler vs. Schemaverstoss) |
| `common/ApiException` + Unterklassen | `UnauthenticatedException`, `ForbiddenException`, `NotFoundException`, `IdempotencyConflictException`, `PayloadTooLargeException`, `TenantParameterException`, `ServiceUnavailableException` |
| `common/ValidationException`/`ValidationViolation` | feldbezogener Verstoss mit `field()`/`code()` |
| `common/ClockPort` | Port der Uhr (im Betrieb die Systemuhr, im Test `MutableClock`) |
| `common/CursorCodec` | opake, HMAC-signierte Cursor (422 bei ungueltigem Cursor) |
| `common/CanonicalJson` | kanonische Materialisierung (RFC 8785-artig) der Nutzlastbytes |
| `common/SqlParameters` | **eine** Stelle fuer die Typbindung der Zeitpunkte (siehe 2.2) |
| `common/IdGenerator` + `UuidV7IdGenerator` | UUID-v7 (zeitlich sortierbar) |
| `tenant/TenantContext` | request-scoped, liest **nur** das JWT; Mandant aus `organisation_id`, Rollen aus dem Claim `roles` **und** der Autoritaet `ROLE_<rolle>` |
| `tenant/TenantTransactionRunner` | setzt den Mandanten **innerhalb** der Transaktion, die auch liest (`SELECT app.set_tenant(?)`, transaktionslokal, ADR-002 §3) |
| `tenant/CrossTenantScope` | mandantenuebergreifende Sicht ueber die schmalen Eigentuemerfunktionen (`app.cross_tenant = 'service_admin'`, transaktionslokal) |
| `tenant/TenantParameterFilter` | lehnt Mandantenparameter im Body/Query/Header ab (422 `tenant_parameter_rejected`) und setzt die Grenze des Request-Bodies (2 MiB) |
| `tenant/TenantInstanceFilter` | Mandanteninstanz (ADR-013): eine gebundene Instanz bedient ausschliesslich ihren Mandanten |
| `crypto/PayloadCipher` | AES-256-GCM, 12-Byte-Nonce, gezielter HMAC-Fingerabdruck (ADR-007) |
| `crypto/SignatureService` | HMAC-SHA256 ueber `<timestamp>.<payload_bytes>`, `v1=<hex>` (ADR-004) |
| `config/SecurityConfiguration` | Bearer-Token (JWT), zustandslos, ohne CSRF; 401/403 mit dem `Problem`-Schema |
| `config/CoreConfiguration` | Uhr, Verschluesselungsschluessel, Cursor |
| `config/EchoBoxProperties` | **alle** Zahlenwerte der Betriebskonfiguration (F-01…F-04) |
| `operations/HealthController` | `/health` und `/health/ready` (Vertrag, AC-023-1) |

**Schluessel aus der Umgebung:** `ECHobox_CRYPTO_KEY` (AES-256-GCM). Ist er nicht
gesetzt, erzeugt der Dienst einen **ephemeralen** Schluessel und warnt; im
Staging-Profil (`echobox.crypto.key-required: true`) ist das ein **Startfehler**.
In der Datei `application.yml` steht **kein** Schluessel — nur Variablenamen
und Betriebswerte.

### 1.4 Modul `endpoint` (US-001…US-004, US-019)

`EndpointTargetUrl` (nur `https`, keine eingebetteten Zugangsdaten, **Schema vor
Zugangsdaten**), `Endpoint`, `EndpointStatus`, `EndpointEventTypes`,
`EndpointRepository` (JdbcTemplate, benannte Parameter, **jede** Abfrage traegt
den Mandanten), `EndpointService` (Pause-Semantik D-03: keine Warteschlange, kein
Nachliefern, offene Wiederholung abbrechen; Secret-Rotation FR-003 mit
Rotationshistorie; rollenabhaengige Sichtbarkeit FR-004), `EndpointQueryService`
(mandantengefiltert; mandantenuebergreifend nur ueber die Eigentuemerfunktion)
und `EndpointController` mit **genau** den sechs Operationen des Vertrags.

### 1.5 Modul `event` (US-005…US-008)

`PayloadSize` (1 MiB Nutzlast, 2 MiB Request-Body), `PayloadEncoding`,
`EventPayload`, `StoredEvent`, `IdempotencyDecision`, `IdempotencyStore`,
`Event`, `EventRepository`, `EventService` (Groessengrenze, Verschluesselung,
Idempotenz je (Mandant, Schluessel), Anlegen der Zustelllaeufe in **einer**
Transaktion), `EventController` (`POST /api/v1/events` → 202,
`GET /api/v1/events/{id}`).

---

## 2. Entscheidungen, die der Vertrag oder die ADRs nicht festlegen

### 2.1 Fehlergrenze 400 vs. 422

`HttpMessageNotReadableException` wird an der Ursache unterschieden:
`MismatchedInputException` → **422** `validation_failed` (Schemadeviation),
`JsonProcessingException` → **400** `malformed_request` (Syntaxfehler). Damit
entspricht die Grenze der Vertragsregel „400 = syntaktisch, 422 = semantisch“.

### 2.2 Typbindung der Zeitpunkte (Produktionskorrektur)

Der PostgreSQL-Treiber kann einen `java.time.Instant` ueber `setObject` **nicht**
ableiten („der Datentyp kann nicht abgeleitet werden“). Alle Zeitpunkte werden
deshalb ueber `common/SqlParameters` als `OffsetDateTime` in **UTC** gebunden —
der Zeitpunkt bleibt damit unabhaengig von der Zeitzone des Betriebssystems
exakt erhalten.

### 2.3 `stringtype=unspecified` (Datenbankverbindung)

Der Treiber bindet einen String als `character varying`; der Vergleich einer
`uuid`-Spalte mit einem String-Parameter scheitert dann an
„operator does not exist: uuid = character varying“. Mit
`spring.datasource.hikari.data-source-properties.stringtype=unspecified` loest
PostgreSQL den Typ des Parameters aus dem Zielkontext auf. Der Wert ist eine
**Verbindungseigenschaft**, kein Geheimnis.

### 2.4 Unbekannte Felder im Request-Body

Der Vertrag fordert `additionalProperties: false`: ein unbekanntes Feld ist ein
Schemaverstoss (**422** `validation_failed`) und wird **nicht** stillschweigend
ignoriert. Die DTOs sammeln die unbekannten Felder ueber `@JsonAnySetter` und
melden sie in der `validate()`-Phase — so entsteht der Verstoss **ausserhalb**
der JSON-Bindung und wird nicht von ihr verschluckt. Der **Mandantenparameter**
wird dagegen schon **vor** der JSON-Bindung erkannt
(`tenant/TenantParameterFilter`) und ergibt **422** `tenant_parameter_rejected`.

### 2.5 Mandant vor Rolle (V-39)

Die Rollenpruefung der mandantenbezogenen Operationen sitzt **in der
Fachlogik**, nicht an der URL: geprueft wird **erst der Mandant** (fremde
Ressource → **404**), **dann die Rolle** (eigener Mandant, falsche Rolle →
**403**). Eine Rollenpruefung an der URL wuerde die Reihenfolge umkehren und
damit die Existenz fremder Objekte preisgeben (D-02).

### 2.6 Zustelllaeufe bei der Einlieferung

Ein Ereignis erhaelt **einen** Lauf je **aktiven** Endpunkt, der den Ereignistyp
abonniert. Ein **pausierter** Endpunkt erhaelt **keinen** Lauf (D-03), zaehlt
aber als Abonnent. Ohne jeden Abonnenten traegt das Ereignis den Zustand
`no_subscriber` und **einen** Lauf ohne Endpunkt (D-04).

### 2.7 Container-Build

`Dockerfile` (Multi-Stage, gepinnte Basis-Images `maven:3.9.9-eclipse-temurin-21`
und `eclipse-temurin:21.0.5_11-jre-jammy`, nicht privilegierter Benutzer
`10001:10001`, **keine** Secrets im Image, Health-Check auf `/health`).

---

## 3. Geaenderte Tests — und warum

Die Semantik (Vertrag, ADRs, Schema) wurde **nicht** geaendert. Sieben
Teststellen wurden nachgezogen; jede Aenderung ist eine **Korrektur eines
Testdefekts**, nicht eine Anpassung an eine abweichende Implementierung.

| # | Datei / Stelle | Defekt | Korrektur |
|---|---|---|---|
| T-1 | `tests/unit/event/PreEncryptedEventTest.pre_encrypted_payload_is_not_decrypted` | AssertJs `doesNotContain(byte[])` prueft, ob **keines** der Einzelbytes vorkommt. Der Fixture-Wert `AEAD-Chiffre-ohne-Klartextanteil` teilt sich Byte-Werte mit `TOP-SECRET-PAYLOAD` (u. a. `A`, `E`, `C`, `D`, `-`) — die Assertion ist mit **jedem** korrekten Implementierungsstand unerfuellbar und widerspricht AC-008-1 („Bytes unveraendert uebernommen“). | Messgroesse auf die **Zeichenfolge** umgestellt (`new String(bytes, ISO_8859_1)).doesNotContain(...)`) — dieselbe Messgroesse wie in `PayloadCipherTest` und `PreEncryptedEventIT`. |
| T-2 | `tests/integration/tenant/TenantIsolationIT.every_tenant_table_enforces_row_level_security` und `…carries_the_tenant_column` | `jdbcTemplate.query(sql, rowMapper, String.class)` bindet `String.class` als **Parameter** (die Overload `query(String, RowMapper, Object...)`), nicht als Ergebnistyp → „der Datentyp kann nicht abgeleitet werden“. | `jdbcTemplate.queryForList(sql, String.class)` — die passende Ueberladung; die Assertion bleibt unveraendert. |
| T-3 | `tests/integration/event/EventIntakeIT.the_event_is_accepted_with_202` | Der Test erwartet `status: accepted`, legt aber **selbst keinen Abnehmer** an. Ohne Abnehmer ist das Ereignis nach D-04 ein `no_subscriber`-Ereignis — der Test haengt von einem Endpunkt aus einer **anderen** Testklasse ab. | Der Test registriert seinen Abnehmer **selbst** (Teststrategie §2: „Jeder Test legt seine Endpunkte selbst an“) — der Test ist damit reihenfolgeunabhaengig. |
| T-4 | `tests/integration/endpoint/EndpointPauseIT.a_paused_endpoint_creates_no_delivery_run` | Zaehlte **alle** Laeufe des Ereignisses; jeder andere Endpunkt des Mandanten, der denselben Ereignistyp abonniert, erhaegt **korrekt** einen Lauf (D-04). | Gezaehlt werden die Laeufe **dieses** Endpunkts (`WHERE event_id = ? AND endpoint_id = ?`) — die Aussage des Tests (kein Lauf fuer den pausierten Endpunkt) bleibt. |
| T-5 | `tests/integration/endpoint/EndpointVisibilityIT.the_list_of_a_receiver_admin_is_tenant_scoped` | Die zweite Assertion (`not(hasItem(not(hasItem(own))))`) verlangt, dass die Liste **genau einen** Endpunkt enthaelt — sie widerspricht der mandantengefilterten Liste, sobald weitere Endpunkte desselben Mandanten existieren. | Geprueft wird, dass der Endpunkt des **fremden** Mandanten **nicht** in der Liste steht (zusaetzlich zur bestehenden Pruefung, dass jeder Eintrag zum Mandanten `m-1` gehoert). |
| T-6 | `tests/integration/tenant/TenantIsolationIT.the_tenant_comes_from_the_identity_and_not_from_the_body` | Die Assertion `containsOnly("m-1")` bezog sich auf **alle** Endpunkte der Datenbank — auch auf die der anderen Testfaelle derselben Klasse. Zudem nutzte der Test dieselbe Ziel-URL wie ein anderer Testfall. | Eigene Ziel-URL fuer den abgelehnten Aufruf; geprueft wird, dass **kein** Endpunkt mit dieser Ziel-URL angelegt wurde (der Mandant aus dem Body wurde nicht uebernommen). |
| T-7 | `tests/integration/tenant/RowLevelSecurityIT.a_query_without_tenant_filter_shows_only_the_tenants_rows` | `containsExactly` unterstellte, dass die Klasse genau **eine** Zeile des Mandanten `m-1` anlegt — die Klasse legt in weiteren Testfaellen eigene Zeilen desselben Mandanten an. | `contains(...)` + `doesNotContain(...)` fuer die Zeile des fremden Mandanten: die Zeile des eigenen Mandanten ist sichtbar, die des fremden nicht. |

---

## 4. Teststand

`mvn verify` (Java 21, Docker fuer Testcontainers):

| Ebene | Umfang | Ergebnis |
|---|---|---|
| Unit (Surefire) | `tests/unit` — **89** Testfaelle | **gruen** — crypto, endpoint, event und die reinen Zustell-Bausteine |
| Integration (Failsafe) | **347** Testfaelle, davon **77** in Teil A | **gruen** in Teil A, **rot** im Rest (siehe unten) |
| Vertrag / Last (Profil `contract-performance`) | `tests/contract` (19) + `tests/performance` (2) = **21** Testfaelle | **gruen** — laeuft **nicht** in der normalen Suite |
| Frontend | `tests/unit/frontend` | Teil C (Vitest) |

**Gruen in Teil A (77 Integrationstestfaelle):** `EndpointRegistrationIT` (11),
`EndpointVisibilityIT` (9), `EndpointPauseIT` (8), `SecretRotationIT` (8),
`EventIntakeIT` (9), `EventIdempotencyIT` (7), `PreEncryptedEventIT` (5),
`EventIntakePerformanceIT` (2), `SchemaMirrorIT` (2), `RowLevelSecurityIT` (4),
`TenantIsolationIT` (6), `ContainerStartupIT` (4), `TenantInstanceIT` (2).

**Rot — erwartungsgemaess, weil die Zustellung, das Protokoll und die
Bereitstellungsdateien noch fehlen:**

| Testklasse | Testfaelle | Ursache | Teil-Lauf |
|---|---|---|---|
| `delivery/*IT` (8 Klassen) | 46 | der Zustellzyklus fehlt | B |
| `protocol/*IT` (4 Klassen) | 22 | das Protokollmodul fehlt | B |
| `security/RoleCapabilityMatrixIT` | 84 | 12 der 17 Operationen fehlen (Zustellung, Protokoll, Betrieb) | B |
| `security/ForeignResourceIsolationIT` | 11 | dito | B |
| `security/AlwaysPresentFieldsIT` | 3 von 6 | Zustellprotokoll und Kennzahlen fehlen | B |
| `event/PayloadEncryptionAtRestIT` | 1 von 7 | die Suche im Zustellprotokoll fehlt | B |
| `deployment/HorizontalScalingIT` | 4 | der Worker beansprucht noch keine Laeufe | B |
| `deployment/EnvironmentSeparationIT` | 1 | der Bereitstellungsdeskriptor fehlt (`.github/` → Schritt 9) | 9 |

Der Schema-Spiegel ist gruen:
`diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` → **leer**.

---

## 5. Offene Punkte fuer die naechsten Teil-Laeufe

| ID | Thema | Owner |
|---|---|---|
| **B-07** | **Die Zustelllogik fehlt**: `DeliveryWorker.runOnce()` beansprucht derzeit keinen Lauf und fuehrt keinen Versuch aus. `DeliveryRepository`, `DeliveryDispatcher`, `RetryScheduler` und die Aktualisierung der Zustelllaeufe fehlen. | Teil B |
| **B-08** | **Das Modul `protocol` fehlt**: Zustellprotokoll, Totenliste, Kennzahlen, Aufbewahrung. | Teil B |
| **B-09** | **`/health` und `/health/ready` liegen in `com.echobox.operations`** (nicht in `protocol`) — die naechsten Laeufe duerfen **keine** zweite Abbildung auf `/health` anlegen (mehrdeutige Zuordnung). | Teil B/C |
| **B-10** | **Der Bereitstellungsdeskriptor fehlt**: `EnvironmentSeparationIT` erwartet `.github/workflows/deploy.yml`. Nach `.agent/PHASES.md` gehoert `.github/` zum Schritt 9 (Deployment & Betrieb). | Schritt 9 |
| **B-11** | **Die ID-Generatoren** nutzen UUID-v7 ueber eine eigene Implementierung (Java 21 hat kein UUIDv7). | Teil D (Vereinfachung) |
| **O-04 / O-05 / R-01 / E-12** | unveraendert offen (Fachbereich, Gate 2) | wie bisher |

Kein Vertragsdefekt wurde in diesem Lauf gefunden — der Vertrag wurde **nicht**
geaendert.

### 5.1 Neben dem Commit-Scope

`.gitignore` wurde um `/target/`, `node_modules/`, `.next/` und `.env`
ergaenzt (Hygiene; die Datei liegt ausserhalb des Commit-Scopes dieses
Schritts und wird vom Orchestrator nicht mitcommittet, wenn er nur
`src/`, `tests/` und `docs/execution/` staged).
