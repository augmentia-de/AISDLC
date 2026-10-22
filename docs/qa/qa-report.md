# QA-Bericht — EchoBox (Schritt 8, **dritte Runde**)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · Schritt: **Sicherheit & Compliance**
> (QA + Security-Audit). Gate: **GATE 2** — QA 0 Critical, Security 0 Critical und 0 High.
> Kanonisches Artefakt: `.agent/artifacts/test-report.md` · Security-Audit:
> [`docs/qa/security-audit.md`](security-audit.md).
>
> **Runde 3** — nachprueft und erneut auditiert. Die zweite Nacharbeit (`3a5ae70`)
> hat **7 von 7** Befunden (S-28…S-34) behoben; diese Runde hat **zwei neue
> Befunde** gefunden (S-36 Medium, S-35 Low), den QA-Stand neu gemessen und
> **28 eigene Tests** ergaenzt.

---

## 1. Kurzfassung

| Frage | Antwort | Beleg |
|---|---|---|
| Laeuft die Suite gruen? | **Ja — vollstaendig gruen.** `mvn -B -o verify` → Unit **137 gruen**, Integration **318 gruen**, 0 Fehler; `jacoco:check` → „All coverage checks have been met."; Vertrag **19** und Last **2** gruen; Frontend **30** (Vitest) + **31** (Playwright) gruen. | Abschnitt 2. |
| Sind die 101 Akzeptanzkriterien abgedeckt? | **Ja — 101 von 101 (100 %)**, unabhaengig nachgeprueft: jede AK-ID steht im Namen **eines existierenden Testfalls** einer Datei, die die Matrix nennt; alle 60 matrix-genannten Testdateien existieren; **keine** AK ist nur ausserhalb der Matrix belegt. | Abschnitt 4; `python3 .agent/tools/traceability_check.py` → gruen. |
| Gibt es Regressionen? | **Nein.** In der Nacharbeit `3a5ae70` wurde **kein** Testfall, **keine** Annotation und **keine** Assertion entfernt oder abgeschwaecht; die einzige Aenderung an einem bestehenden Test war eine **Verschärfung** (Tomcat-Untergrenze 10.1.55 → 10.1.59). | Abschnitt 5 (Diff-Analyse `3a5ae70`). |
| Wie hoch ist die Testabdeckung des Codes? | **86,73 % Zeilen** — die Schwelle von 80 % ist **erfuellt** und im Build **erzwungen** (JaCoCo `check`, Phase `verify`). | Abschnitt 6. |
| QA-Bewertung | **0 Critical** — die Gate-Halbbedingung „QA 0 Critical" ist **erfuellt**. | Abschnitt 9. |
| Security-Bewertung | **0 Critical, 0 High, 1 Medium (S-36), 1 Low (S-35)** → **freigeben**. | [`security-audit.md`](security-audit.md). |

---

## 2. Teststand je Ebene — **ausgefuehrt**, nicht behauptet

Alle Kommandos wurden am 2026-10-22 in diesem Arbeitsverzeichnis ausgefuehrt; die
Ausgaben liegen unter `/tmp/opencode/*.log`.

| # | Ebene | Kommando | Ergebnis |
|---|---|---|---|
| 1 | **Java Unit** (Surefire) | `mvn -B -o verify` | `Tests run: 137, Failures: 0, Errors: 0, Skipped: 0` → **137 gruen** |
| 2 | **Java Integration** (Failsafe, PostgreSQL 16.15 aus Testcontainers, WireMock, eine JVM je Klasse) | dieselbe Ausfuehrung | `Tests run: 318, Failures: 0, Errors: 0, Skipped: 0` → **318 gruen** |
| 3 | **Java Coverage-Gate** | dieselbe Ausfuehrung | `jacoco:check` → **„All coverage checks have been met."** — **86,73 % Zeilen** (Gate: 80 %, **erzwungen**) |
| 4 | **Java Vertrag** (`tests/contract`) | `mvn -B -o -Pcontract-performance verify` | `SignatureContractTest` 6 ✓ · `AlwaysPresentFieldsContractTest` 7 ✓ · `RejectionContractTest` 6 ✓ → **19 gruen** |
| 5 | **Java Last** (`tests/performance`) | dieselbe Ausfuehrung | `EventIntakeLoadTest` **2 gruen** (1 000 Einlieferungen, p95 < 200 ms) |
| 6 | **Frontend Typpruefung** | `npm run typecheck` (`tsc --noEmit`) | **Exit 0** — „TypeScript: No errors found" (`strict: true`, `noUnusedLocals`, `noImplicitOverride`) |
| 7 | **Frontend Lint** | `npm run lint` | **Exit 0** — „ESLint: No issues found" |
| 8 | **Frontend Unit** (Vitest) | `npx vitest run --root src/main/frontend` | **5 Dateien, 30 Tests gruen** (1,21 s) |
| 9 | **Frontend E2E** (Playwright, Chromium) | `npx playwright test --config src/main/frontend/playwright.config.ts` | **31 Tests gruen** (11,1 s) |
| 10 | **Frontend Build** | `npm run build` | **Exit 0** — Next.js-Build erfolgreich |
| 11 | **Traceability-Kette** | `python3 .agent/tools/traceability_check.py` | **gruen** — „Keine Luecken — die Kette Anforderung → Story → Feature → Test → Code ist vollstaendig." |
| 12 | **Schema-Spiegel 1** | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | **leer** („Files are identical") |
| 13 | **Schema-Spiegel 2** | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** („Files are identical") |
| 14 | **Secret-Scan** | `grep -rInE "(api[_-]?key\|secret\|password\|token\|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ Dockerfile` | 2 Treffer, beide Test-Fixtures (`tests/unit/frontend/EndpointsPage.test.tsx:177`, `:224`) |
| 15 | **Secret-Scan Git-Historie** | `git log --all -p -- . ':!docs' ':!.agent' \| grep -InE "(api[_-]?key\|secret\|password\|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{12,}"` | **keine** Treffer |
| 16 | **SCA Java** (eigene Abfrage) | `mvn -B -o dependency:tree` (110 Koordinaten) → OSV `querybatch` | **17** Advisories (Runde 2: 20) — die 3 Tomcat-Advisories sind **nicht** mehr enthalten |
| 17 | **SCA Node** (eigene Abfrage) | `package-lock.json` (523 Pakete) → OSV `querybatch` | **9** Advisories — **alle** in der Baseline `osv-scanner.toml` |
| 18 | **SCA-Baseline** | eigene Abfrage gegen `osv-scanner.toml` | 17 + 9 = **26** = **26** Baseline-Eintraege → **0 neu**, **0 verwaist** |
| 19 | **Vertrag unveraendert** | `git diff --stat -- docs/api/` **und** `git diff --stat 8627bb6..HEAD -- docs/api/` | **leer** — der eingefrorene Vertrag ist unveraendert |
| 20 | **AK-Abdeckung** (eigenes Skript) | 101 AK gegen die **Wirklichkeit** geprueft (Existenz der Datei **und** AK-ID im Namen eines Testfalls) | **101 belegt, 0 Luecken** (Abschnitt 4) |
| 21 | **Regressionspruefung** | `git show 3a5ae70 -- tests/` (entfernte Tests/Assertions), `grep -rn "@Disabled\|@Ignore" tests/` | **0** entfernte Tests, **0** entfernte Assertions, **0** uebersprungene Tests (Abschnitt 5) |

### 2.1 Verteilung der Testfaelle

| Ebene | Testdateien | Testfaelle | gruen | fehlschlagend |
|---|---|---|---|---|
| Java Unit (`tests/unit`, Surefire) | 27 Java (+ 5 TSX + 2 TS-Hilfsdateien fuer Vitest) | 137 | **137** | 0 |
| Java Integration (`tests/integration`, Failsafe, PostgreSQL 16.15 aus Testcontainers) | 44 `*IT` (+ 8 Hilfsklassen) | 318 | **318** | 0 |
| Java Vertrag (`tests/contract`) | 3 Testklassen (+ 2 YAML-Fixtures) | 19 | **19** | 0 |
| Java Last (`tests/performance`) | 1 | 2 | **2** | 0 |
| Frontend Unit (Vitest, `tests/unit/frontend`) | 5 (+ 2 Stuetzdateien) | 30 | **30** | 0 |
| Frontend E2E (Playwright, `tests/e2e`) | 5 Specs (+ 2 Stuetzdateien) | 31 | **31** | 0 |
| **Summe** | **100 Dateien unter `tests/`** | **537** | **537** | **0** |

---

## 3. Die Nachpruefung der Security-Befunde — Ergebnis in einer Zeile je Befund

| ID | Schwere (Runde 2) | Bewertung Runde 3 | Fundstelle / Nachweis |
|---|---|---|---|
| **S-28** Request-Body | High | **behoben** | `TenantParameterFilter.java:88-149` — die Grenze haengt an der **Methode**, nicht am Medientyp; der Converter sieht nur die **begrenzten** Byte (`:123`, `:280-329`). **Eigener Nachweis:** `RequestBodyBoundaryAuditIT` (15, gruen) — 6 Medientypen (Content-Length) → 413 · 4 Medientypen (chunked) → 413 · ohne `Content-Length` → 413 · ohne `Content-Type` → 413 · gzip-Bombe → 4xx, Dienst antwortet weiter · knapp unter der Grenze → 202 · GET mit uebergrossem Body → 200, Dienst antwortet weiter. |
| **S-29** JWT-Audience | Medium | **behoben** | `SecurityConfiguration.java:144-150` (immer in der Kette), `:178-192` (fail-closed), `AudiencePolicy.java:28-36` (Startabbruch). **Eigener Nachweis:** `AudienceFailClosedPathsTest` (4, gruen) — inkl. der Aufloesung eines **leeren** Secret-Wertes. *Rest: die Doku → **S-35**.* |
| **S-30** 500 statt 4xx | Medium | **behoben** | `GlobalExceptionHandler.java:246-253` (Medientyp → 400), `:267-274` (Multipart → 400), je **DEBUG ohne Exception-Objekt**. **Eigener Nachweis:** `ErrorMappingLeakTest` (7, gruen) + `MediaTypeErrorIT` (6, gruen). *Rest: die **Methode** → **S-36**.* |
| **S-31** Container-Pin | Medium | **behoben** | `pom.xml:65` → 10.1.61; `dependency:tree` → `tomcat-embed-core:10.1.61`. **Eigener Nachweis (OSV):** 10.1.55 → **3** Advisories, 10.1.61 → **0**. `DependencyHardeningTest` (gruen) verlangt jetzt ≥ 10.1.59 (**verschaerft**). |
| **S-32** SCA-Baseline | Medium | **behoben** | `osv-scanner.toml` (26 Eintraege) + `deploy.yml:56-64`. **Eigener Nachweis:** 17 + 9 = 26 Advisories = **exakt** die Baseline (**0 neu**, **0 verwaist**); Format und `--config` in osv-scanner **v2.7.0** verifiziert. `ScaBaselineIT` (3, gruen). |
| **S-33** Token ohne Mandant | Low | **behoben** | `TenantTransactionRunner.java:100-105` → `UnauthenticatedException` → **401**. `ErrorMappingLeakTest` (1, gruen) + `MissingTenantIT` (1, gruen). *Rest: das Anfragelimit zaehlt weiterhin nur Anfragen **mit** Mandant → **Q-9**.* |
| **S-34** Javadoc | Low | **behoben** | `DeliveryStateMachine.java:11-31`, `:118-124` — die Nachziehungs-Zusage ist entfernt, beide Uebergaenge sind genannt. `DeliveryStateMachineDocumentationTest` (2, gruen). |

**7 behoben · 0 teilweise behoben · 0 offen geblieben.**

### 3.1 Die zwei neuen Befunde

| ID | Schwere | Fundstelle / Nachweis |
|---|---|---|
| **S-36** | Medium | `GlobalExceptionHandler.java:283-290` — `HttpRequestMethodNotSupportedException` hat **keinen** eigenen Fall → **500 `internal_error`** + Stacktrace im Protokoll. **Belegt** durch eine Sonde ueber 15 Methode/Pfad-Kombinationen: `POST /health`, `PUT /health`, `POST /actuator/health` — **ohne Token** → 500; mit Token 6 weitere Kombinationen → 500; **9 Stacktraces** im Protokoll. Die **Antwort** gibt nachweislich **kein** internes Detail preis (`UnsupportedMethodErrorIT`, 2, gruen). |
| **S-35** | Low | `application.yml:106` und `EchoBoxProperties.java:141-146` — „Leer: die Zielgruppe wird nicht geprueft" beschreibt das **alte** fail-open-Verhalten; der Dienst startet jetzt **nicht** bei leerer Audience. *Das Verhalten ist durch `AudienceFailClosedPathsTest` (4, gruen) festgenagelt.* |

---

## 4. Abdeckung der 101 Akzeptanzkriterien

### 4.1 Die gepruefte Menge

| Quelle | Anzahl AK |
|---|---|
| `docs/requirements/traceability_matrix.json` → `acceptance_criteria_index` | **101** |
| `docs/requirements/traceability_matrix.json` → `entries[].acceptance_criteria` (24 Eintraege) | **101** (identische Menge) |
| `docs/requirements/stories/US-0xx.json` → `acceptance_criteria` | **101** (identische Menge) |

*Abweichung der Mengen: **keine*** — `entries-AK == index-AK → True` (eigenstaendig geprueft).

### 4.2 Das Pruefverfahren (eigenstaendig nachgefahren)

**Eine gefuellte Matrix zaehlt nicht als Beleg.** Geprueft wurde deshalb gegen die
**Wirklichkeit** — mit einem eigenen Skript, nicht ueber `traceability_check.py`
(das nur prueft, ob ueberhaupt ein Test genannt wird):

1. **Existiert jede Testdatei, die die Matrix nennt?** → **0 fehlend** (alle **60**
   matrix-genannten Dateien liegen auf der Platte).
2. **Steht die AK-ID im Namen eines Testfalls** in einer dieser Dateien? Geprueft
   wurde gegen `@DisplayName(...)`, `@ParameterizedTest(name=...)`, den
   Java-Methodennamen und `it(...)`/`test(...)`/`describe(...)` — **nicht** gegen
   ein Vorkommen irgendwo im Text.
3. **Gegenprobe:** gibt es eine AK, die **nur ausserhalb** der Matrix belegt ist?
   → **0**.

```
acceptance_criteria_index: 101 AK
entries (Summe):           101 AK
mengengleich:               True
Matrix-genannte Testdateien: 60, fehlend: 0
geprueft: 101 AK
belegt : 101 AK
Luecken: 0
AK, die nur ausserhalb der Matrix belegt sind: 0
```

### 4.3 Ergebnis je Anforderung

| Anforderung | AK | Beleg durch Testfall (Auszug) |
|---|---|---|
| FR-001 Endpunkt registrieren | 9 | `EndpointRegistrationTest`, `EndpointRegistrationIT`, `ForeignResourceIsolationIT`, `RoleCapabilityMatrixIT`, `endpoints.spec.ts` |
| FR-002 Pause / Reaktivieren | 9 | `EndpointPauseTest`, `EndpointPauseIT`, `ForeignResourceIsolationIT` |
| FR-003 Secret-Rotation | 8 | `SecretRotationTest`, `SecretRotationIT`, `ForeignResourceIsolationIT` |
| FR-004 Mandantentrennung der Sicht | 4 | `EndpointVisibilityIT`, `AlwaysPresentFieldsIT` |
| FR-010 Einlieferung | 6 | `EventIntakeTest`, `EventIntakeIT`, `RoleCapabilityMatrixIT` |
| FR-011 Idempotenz | 4 | `EventIdempotencyTest`, `EventIdempotencyIT` |
| FR-012 Verschluesselung im Ruhezustand | 5 | `PayloadCipherTest`, `PayloadEncryptionAtRestIT` |
| FR-013 Vorverschluesselte Ereignisse | 3 | `PreEncryptedEventTest`, `PreEncryptedEventIT` |
| FR-020 Signaturverfahren | 5 | `SignatureTest`, `SignatureContractTest`, `SignedDeliveryIT` |
| FR-021 Zustellgarantie | 4 | `AtLeastOnceDeliveryIT`, `DeliveryIdentifiersTest` |
| FR-022 Wiederholung / Backoff | 5 | `RetryDeliveryIT`, `BackoffPolicyTest`, `JitterTest` |
| FR-023 Versuchsgrenze / Tote Zustellung | 4 | `DeadLetterIT`, `AttemptLimitIT`, `AttemptLimitTest` |
| FR-024 Erneutes Ausloesen | 5 | `RedeliveryIT`, `ForeignResourceIsolationIT` |
| FR-025 Ablehnungssignal | 4 | `RejectionContractTest`, `RejectionDeliveryIT` |
| FR-030 Zustellprotokoll | 8 | `DeliveryProtocolIT`, `AlwaysPresentFieldsContractTest`, `protocol.spec.ts` |
| FR-031 Totenliste / Aufbewahrung | 9 | `DeadDeliveryListIT`, `RetentionEnforcementIT`, `AlwaysPresentFieldsIT` |
| FR-032 Kennzahlen | 4 | `EndpointMetricsIT`, `AlwaysPresentFieldsContractTest` |
| FR-033 Endpunkt-Limit | 4 | `RateLimitedDeliveryIT`, `RateLimiterTest` |
| NFR-001 Nutzlastgrenze | 6 | `EventIntakeTest`, `EventIntakeIT`, `EventIntakePerformanceIT` |
| NFR-002 Antwortzeit | 6 | `EventIntakePerformanceIT`, `EventIntakeLoadTest` |
| NFR-003 Mandantentrennung auf DB-Ebene | 4 | `RowLevelSecurityIT`, `TenantIsolationIT`, `SchemaMirrorIT` |
| NFR-004 Verschluesselung im Ruhezustand | 5 | wie FR-012 |
| NFR-005 Barrierefreiheit | 4 | `a11y.test.tsx`, `a11y.spec.ts` |
| NFR-006 Betrieb (Container, Umgebungen) | 5 | `ContainerStartupIT`, `HorizontalScalingIT`, `TenantInstanceIT`, `EnvironmentSeparationIT`, `health.spec.ts` |

### 4.4 Luecken der Matrix (nicht der Abdeckung)

| Pfad | Art | Bewertung |
|---|---|---|
| `docker-compose.yml` | **Codepfad** der Matrix fuer NFR-006 existiert nicht | **echte Luecke** — bereits als **S-22** der QA-Liste gefuehrt; gehoert zu **Schritt 9** (lokale Entwicklung). **Kein** Test verlangt die Datei. |
| `src/main/java/com/echobox/crypto/SecretStore.java`, `delivery/PayloadSource.java`, `delivery/RetryScheduler.java`, `tenant/TenantConnectionInitializer.java`, `config/TenantInstanceConfiguration.java` | Codepfade der Matrix existieren nicht | **keine Luecke** — die Verantwortung ist an die Nachbarbausteine geflossen (`PayloadCipher`, `DeliveryDispatcher`, `TenantTransactionRunner`, `EchoBoxProperties`). Die Matrix verweist auf historische Namen → Dokumentationspflege (Schritt 1/2). |

**Es gibt keine AK-Luecke.** Jede der 101 AKs hat mindestens einen existierenden,
benannten Testfall.

---

## 5. Regressionspruefung

### 5.1 Kein Test wurde entfernt oder abgeschwaecht

Geprueft wurde die **Diff-Ebene**, nicht die Prosa:

| Commit | Entfernte `@Test`/`@DisplayName`/Testmethoden | Entfernte Assertions | Sonstiges |
|---|---|---|---|
| `3a5ae70` (Schritt 6/7, Nacharbeit 2) | **0** | **0** | 6 neue Testdateien, 2 bestehende **angepasst** (keine Loeschung) |

*Belege:*
`git show 3a5ae70 -- tests/ \| grep -E "^-" \| grep -cE "@Test\|@DisplayName\|void [a-zA-Z0-9_]+\("` → `0` ·
`git show 3a5ae70 -- tests/ \| grep -E "^-" \| grep -cE "assertThat\|assertEquals\|assertThrows\|assertThatThrownBy"` → `0` ·
`git show --stat 3a5ae70 -- tests/` → 8 Dateien, 6 neu, 2 erweitert.

**Kein Test wurde abgeschwaecht, uebersprungen oder ausgegliedert:**
`grep -rn "@Disabled\|@Ignore" tests/` → **keine** Treffer.

**Die beiden angepassten Dateien wurden verschaerft, nicht abgeschwaecht:**
* `tests/unit/security/DependencyHardeningTest.java` — die geforderte Tomcat-Untergrenze
  stieg von **10.1.55** auf **10.1.59** (die Fix-Linie der drei Tomcat-Advisories);
  die Assertion wurde **enger**.
* `tests/integration/deployment/ContainerHardeningIT.java` — der gepinnte Container
  wird als **10.1.61** geprueft (vorher 10.1.55).

### 5.2 Was dieser Schritt **nicht** veraendert hat

Kein bestehender Test wurde geloescht, abgeschwaecht, umbenannt oder verschoben.
Neu angelegt wurden **drei** Dateien (Abschnitt 6.1).

---

## 6. Coverage — und die erzwungene Schwelle

### 6.1 Das Gate ist im Build verankert

| Nachweis | Ergebnis |
|---|---|
| `pom.xml:224-277` | JaCoCo 0.8.13 mit `prepare-agent` (`append=true`), `report` und **`check`** in der Phase **`verify`** (also nach Surefire **und** Failsafe) |
| Regel | `BUNDLE` / `LINE` / `COVEREDRATIO` / **`minimum 0.80`** |
| `pom.xml:183-206` | Loeschschritt in `initialize` — `target/jacoco.exec` wird vor dem Lauf entfernt, sonst zaehlte das Gate die Daten **aller** frueheren Laeufe mit |
| `mvn -B -o verify` | **`jacoco:check` → „All coverage checks have been met."** |

**A-1 ist damit erledigt:** die Schwelle ist nicht mehr eine Zusage, sondern eine
**Bedingung, deren Bruch den Build abbricht**.

### 6.2 Die gemessenen Zahlen

Aus `target/site/jacoco/jacoco.csv` des Laufs vom 2026-10-22:

| Metrik | gedeckt | gesamt | **Abdeckung** | Schwelle 80 % |
|---|---|---|---|---|
| **Zeilen** | 2 255 | 2 600 | **86,73 %** | **erfuellt, erzwungen** ✅ |
| Anweisungen (Instruction) | 10 606 | 12 413 | 85,44 % | erfuellt ✅ |
| Zweige (Branch) | 585 | 925 | 63,24 % | **nicht** erzwungen ⚠️ |
| Methoden | 587 | 649 | 90,45 % | erfuellt ✅ |

Abdeckung je Paket (Zeilen):

| Paket | Abdeckung | Paket | Abdeckung |
|---|---|---|---|
| `com.echobox.config` | 96,85 % | `com.echobox.tenant` | 89,95 % |
| `com.echobox.event` | 96,35 % | `com.echobox.endpoint` | 86,88 % |
| `com.echobox.protocol.api` | 95,51 % | `com.echobox.delivery` | 91,35 % |
| `com.echobox.protocol` | 92,75 % | `com.echobox.event.api` | 78,85 % |
| `com.echobox.crypto` | 72,06 % | `com.echobox.endpoint.api` | 68,49 % |
| `com.echobox.common` | 65,43 % | `com.echobox.operations` | 64,52 % |
| `com.echobox.delivery.api` | 63,64 % | `com.echobox` (Startklasse) | 33,33 % |

> **Hinweis:** die Zweigabdeckung (63,24 %) ist **nicht** Teil des Gates (das
> Gate misst Zeilen). Sie ist ein Hinweis fuer die Teststrategie, **kein**
> Sicherheitsbefund: die niedrigsten Pakete sind die API-Records (Konstruktoren,
> `equals`/`hashCode`) und die Fehlerklassen. **Abweichung A-9** (Abschnitt 8).

### 6.3 Die drei neuen Testdateien dieser Runde

| Datei | Tests | Ebene | Beweist |
|---|---|---|---|
| `tests/integration/security/RequestBodyBoundaryAuditIT.java` | **15** | Integration | Die Grenze des Request-Bodies haengt **nicht** am Medientyp und ist ueber **keinen** Weg umgehbar: 6 Medientypen (Content-Length), 4 Medientypen (chunked), ohne `Content-Length`, ohne `Content-Type`, gzip-Bombe, Body knapp unter der Grenze (202), GET mit uebergrossem Body (200). |
| `tests/unit/security/AudienceFailClosedPathsTest.java` | **4** | Unit | Die Zielgruppen-Pruefung ist ueber **alle** Konfigurationswege fail-closed: Produktions-Bee-Fabrik, Aufloesung eines leeren Secret-Wertes, die drei ausgelieferten Profile, die Pipeline. |
| `tests/unit/common/ErrorMappingLeakTest.java` | **7** | Unit | Die **einzige** Fehlerabbildung gibt kein internes Detail preis — auch bei einem unerwarteten Fehler (500). |
| `tests/integration/security/UnsupportedMethodErrorIT.java` | **2** | Integration | Eine nicht unterstuetzte Methode gibt **kein** internes Detail preis (S-36, gruener Teil des Befunds). |

**28 neue Tests**, alle gruen.

---

## 7. Die zehn Gap-Tests der Runden 1 + 2 — **alle gruen**

| Gap-Test | Datei | vorher | jetzt |
|---|---|---|---|
| `a_chunked_request_body_above_two_mebibytes_must_be_rejected` (S-20) | `SecurityGapIT` | 202 statt 413 | **gruen (413)** |
| `the_request_limit_must_answer_with_429` (S-24) | `SecurityGapIT` | nie eine 429 | **gruen (429 + `Retry-After`)** |
| `a_token_with_a_foreign_audience_must_be_rejected` (S-21) | `SecurityGapTest` | angenommen | **gruen (abgelehnt)** |
| `a_token_without_an_expiry_must_be_rejected` (S-21) | `SecurityGapTest` | angenommen | **gruen (abgelehnt)** |
| `a_sixteen_byte_key_must_prevent_the_start` (S-22) | `SecurityGapTest` | angenommen | **gruen (Start verhindert)** |
| `the_response_body_of_the_receiver_must_be_read_with_a_limit` (S-23) | `SecurityGapTest` | 5 MiB gelesen | **gruen (<= 1 MiB)** |
| `LUECKE (Befund S-28)` | `SecurityGapRound2IT` | 202 statt 413 | **gruen (413)** |
| `LUECKE (Befund S-30)` | `SecurityGapRound2IT` | 500 | **gruen (400)** |
| `LUECKE (Befund S-29)` | `SecurityGapRound2Test` | kein Fehler geworfen | **gruen (JwtException)** |
| `LUECKE (Befund S-31)` | `SecurityGapRound2Test` | 10.1.55 < 10.1.59 | **gruen (10.1.61)** |

> **Die Gap-Tests wurden nicht veraendert** — sie sind die Abnahme der
> Behebung. **10 von 10 gruen.** Es gibt in dieser Runde **keinen** bewusst
> roten Test: die beiden neuen Befunde (S-35 Low, S-36 Medium) blockieren das
> Gate **nicht** (0 Critical / 0 High), und ein absichtlich roter Test wuerde
> den Build der Pipeline scheitern lassen und damit genau das Deployment
> verhindern, das die Gate-Bedingung freigibt. Beide Befunde sind **belegt**
> (Sonde, Protokollauszug, Quelltext) und tragen einen fertigen **Abnahmetest**
> in ihrer Fehlerspur (`docs/qa/bugs/BUG-S-35.json`, `BUG-S-36.json`).

---

## 8. Abweichungen

| ID | Abweichung | Auswirkung | Empfehlung / Owner |
|---|---|---|---|
| **A-1** (erledigt) | Die Coverage-Messung war nicht im Build verankert. | — | **erledigt**: JaCoCo `check` in `pom.xml`, Schwelle 0,80, Phase `verify`. |
| **A-2** (erledigt) | `.agent/artifacts/traceability-verification.md` nannte „130 AK"; die Matrix enthaelt **101**. | Die Zahl im Nachzug-Artefakt war falsch; die Matrix ist richtig. | **erledigt** (Commit `825656a`); die Abdeckung selbst ist unberuehrt (101/101). |
| **A-3** (erledigt) | `mvn -B verify` war mit den Gap-Tests der Runde 2 **rot**. | — | **erledigt**: alle zehn Gap-Tests sind gruen. |
| **A-4** (offen) | `docker-compose.yml` fehlt (Matrix nennt den Pfad fuer NFR-006). | Lokale Entwicklung ohne Compose; die Testabdeckung ist **nicht** betroffen. | **Schritt 9.** |
| **A-5** (offen) | Fuenf Codepfade der Matrix sind **historische Namen** (`SecretStore`, `PayloadSource`, `RetryScheduler`, `TenantConnectionInitializer`, `TenantInstanceConfiguration`). | Keine Abdeckungsluecke; die Matrix verweist ins Leere. | **Schritt 1/2** — Matrix nachziehen. |
| **A-6 / B-17** (erledigt) | Die Uebergangstabelle des Domänenmodells war inkonsistent zum Code. | — | **erledigt**: Code (`DeliveryStateMachine.TRANSITIONS`, `onRateLimited`) **und** Doku (`domain_model.md` §3.3, Commit `28396e0`). |
| **A-7** (offen) | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.). | Kein Sicherheitsbefund, kein Gate-Effekt; die Groesse stammt ueberwiegend aus den SQL-Konstanten und den Row-Mappern, die Kohaesion ist gut (je Klasse **ein** Aggregat). | **Schritt 9** (Wartbarkeit): `DeliveryRepository` in drei Repositories teilen. |
| **A-8** (erledigt) | Kein Test pruefte die **eingehende** Ratenbegrenzung — weil es sie nicht gab (S-24). | — | **erledigt**: `ApiRateLimitIT` (4) + `ApiRateLimitHardeningTest` (5) + der Gap-Test der Runde 1. |
| **A-9** (offen) | Die **Zweigabdeckung** liegt bei 63,24 %; das Gate misst nur **Zeilen** (86,73 %). | Kein Sicherheitsrisiko, aber eine Verzerrung: Fehlerpfade (z.B. `catch`-Zweige) sind schlechter getestet als der Normalpfad. | **Schritt 9** (Teststrategie): Zweigschwelle ergaenzen oder die niedrigsten Pakete gezielt testen. |
| **A-10** (erledigt) | Der **SCA-Gate der Pipeline** schlug auf jedem Lauf an (keine Baseline) → **S-32**. | — | **erledigt**: `osv-scanner.toml` (26 Eintraege) + `--config` im SCA-Schritt; eigene Abfrage: 26 = 26, 0 neu. |
| **A-11** (neu) | Die **Reichweite der Body-Grenze** war im Audit der Runde 2 nur am Beispiel `application/*+json` geprueft; die **Methode** als Kriterium, chunked ohne `Content-Length`, `gzip` und ein GET mit Body waren **nicht** abgedeckt. | Die Runde 2 konnte die Behebung nicht vollständig belegen. | **erledigt**: `RequestBodyBoundaryAuditIT` (15 Tests) deckt alle Vektoren ab — **S-28 ist damit belegt behoben**. |
| **A-12** (neu) | Die Behebung von S-30 hat **zwei** Ausnahmentypen abgedeckt (Medientyp, Multipart) — die **Methode** blieb unbehandelt → **S-36**. | Ein Fehler des Aufrufers wird als 500er mit Stacktrace protokolliert; ohne Anmeldung erreichbar. | **Schritt 6/7**: eigener Fall + 400 `malformed_request` (Abnahmetest in `BUG-S-36.json`). |

---

## 9. Offene Punkte

### 9.1 Die aus den Vorgaengen uebernommenen offenen Punkte

| ID | Thema | Owner | Stand nach Schritt 8, Runde 3 |
|---|---|---|---|
| **O-04** | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz, Gate 2 | **unveraendert offen.** Die **Architekturseite** ist umgesetzt und belegt (AES-256-GCM, Schluessel ausserhalb der Datenbank, keine Klartextanteile in Tabellen/Indizes/Logs, nur Metadaten in der Protokollsuche). Die **fachliche** Frage (Muss? durchsuchbar?) entscheidet der Fachbereich. Zwei technische Randnotizen: (a) die ADR-003-Vorgabe „je Mandant ein eigener DEK" ist **nicht** umgesetzt — es gibt **einen** globalen Schluessel; (b) die **Schluessellaenge wird jetzt geprueft** (S-22 erledigt). |
| **O-05** | AVV bei Fremddaten | Fachbereich + Recht, Gate 2 | **unveraendert offen.** Technisch datenminimierend; ein AVV ist eine rechtliche Zusage ausserhalb der technischen Pruefung. |
| **R-01 / E-13** | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich, Gate 2 | **unveraendert offen.** `purge-delay` ist konfigurierbar, ohne Wert wird mit 0 Tagen gerechnet; der Wert kommt vom Fachbereich. |
| **E-12** | Haertung der mandantenuebergreifenden Sicht | Fachbereich + Plattform, Gate 2 | **unveraendert offen — im Audit bestaetigt:** die Applikationsrolle kann `app.cross_tenant` setzen; die drei Funktionen liefern **nur** die Vertragsprojektion und sind fail-closed. |
| **B-18** | Idempotenzschluessel der Endpunkt-Registrierung | Fachbereich, Gate 2 | **unveraendert offen.** `endpoint_event_types` hat den Primaerschluessel `(endpoint_id, event_type)`; ob ein **je Mandant** eindeutiger Schluessel wuenscht ist, ist eine fachliche Frage. |
| **S-18** | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.) | Schritt 9 | **bewertet** — siehe **A-7**: kein Sicherheitsbefund, kein Gate-Effekt. |
| **S-22** (QA) | `docker-compose.yml` fehlt | Schritt 9 | **unveraendert offen.** |
| **S-23** (QA) | `.gitignore`: `/test-results/` aus der Wurzel | Schritt 9 | **erledigt** — `/test-results/` **und** `/playwright-report/` stehen jetzt in `.gitignore`. |
| **Q-1 … Q-4, Q-7** | TLS/HSTS, Branch-Schutz, Recht auf Loeschung, SSRF-interne Bereiche, Schluesselverwaltung | Schritt 9 / Fachbereich | **unveraendert offen** (siehe `security-audit.md`, Anhang C). |
| **Q-5, Q-6** | jackson-ReDoS, CVE-2026-22732 | — | **erledigt**: Q-5 **empirisch** als nicht erreichbar belegt (Anhang A.2), Q-6 durch Spring Security 6.5.11 erledigt. |
| **Q-8, Q-9** | Rate-Limit je Instanz; nicht angemeldete Anfragen nicht gezaehlt | Schritt 9 / Fachbereich | **unveraendert offen** — Randbedingungen des Betriebs, **keine** Befunde. **Q-9** ist mit S-36 wieder relevant: die nicht angemeldeten 500er (S-36) werden **nicht** gezaehlt. |
| **O-05 (neu)** | `postcss 8.4.31` ist eine **Laufzeit**-Abhaengigkeit von `next 15.5.28` | Wartung / Schritt 9 | **offen** — vier Advisories sind bis dahin befristet ausgenommen (Begruendung + Ablaufdatum in der Baseline). |

### 9.2 Neue offene Punkte aus dieser Runde

| ID | Thema | Owner |
|---|---|---|
| **S-36** | Eine nicht unterstuetzte HTTP-Methode ergibt 500 mit Stacktrace statt 4xx (auch ohne Anmeldung) | Schritt 6/7 |
| **S-35** | Die Dokumentation der Audience beschreibt noch das alte fail-open-Verhalten | Schritt 6/7 |

---

## 10. QA-Bewertung und Freigabe-Empfehlung

| Kriterium des Gates | Wert | Erfuellt? |
|---|---|---|
| **QA: 0 Critical** | 0 Critical | **ja** |
| Security: 0 Critical | **0** | **ja** |
| **Security: 0 High** | **0** | **ja** |
| Alle Tests gruen | **537 gruen, 0 rot** (137 Unit + 318 Integration + 19 Vertrag + 2 Last + 30 Vitest + 31 Playwright) | **ja** |
| Keine bekannten kritischen Luecken | 0 Critical, 0 High; **1 Medium** (S-36), **1 Low** (S-35) | **ja** (die Gate-Schwelle ist 0 Critical/0 High) |
| Keine Regressionen | keine (0 entfernte Tests/Assertions in der Nacharbeit; die beiden Anpassungen waren **Verschärfungen**) | **ja** |
| AK-Abdeckung | **101/101** (eigenstaendig nachgefahren) | **ja** |
| Coverage >= 80 % | **86,73 % Zeilen** (2 255/2 600) — **im Build erzwungen** (JaCoCo `check`) | **ja** |
| Vertrag unveraendert | `git diff --stat -- docs/api/` **und** gegen `8627bb6` leer | **ja** |
| Schema-Spiegel | beide **leer** | **ja** |
| Traceability-Kette | gruen | **ja** |

### Freigabe-Empfehlung: **FREIGEBEN**

* **Fuer das Deployment (Schritt 9): ja.** Die Gate-Bedingung „Security 0 Critical
  und 0 High" ist mit **0 / 0** erfuellt; die QA-Halbbedingung „0 Critical" ist
  ebenfalls erfuellt. **Alle sieben Befunde der Runde 2 sind behoben** — der
  High **S-28** ist an der Stelle geschlossen, an der er entstanden ist.
* **Mit zwei offenen Punkten, die nicht am Gate scheitern:**
  1. **S-36 (Medium)** — ein eigener Fehlerfall fuer
     `HttpRequestMethodNotSupportedException` (400 `malformed_request`, wie S-30).
     Der Abnahmetest steht fertig in `docs/qa/bugs/BUG-S-36.json`. *Schritt 6/7.*
  2. **S-35 (Low)** — zwei Saetze in `application.yml` und `EchoBoxProperties`
     auf das fail-closed-Verhalten umschreiben. *Schritt 6/7.*
* **Zur Betriebsdokumentation (Schritt 9):** `PRODUCTION_SECURITY_AUDIENCE` bzw.
  `STAGING_SECURITY_AUDIENCE` duerfen **nicht leer** sein — sonst startet der
  Dienst nicht (fail-closed). Und: `POST /health` (ohne Token) erzeugt derzeit
  einen 500er mit Stacktrace (S-36) — bis zur Behebung in der Logrotation
  beruecksichtigen.
* **O-04, O-05, R-01/E-13, E-12, B-18, Q-3, Q-4, Q-7, Q-8, Q-9** sind **fachliche
  bzw. rechtliche Entscheidungen** und bleiben dem **Gate 2** vorbehalten — sie
  sind **keine** technischen Blocker fuer die Umsetzung und **keine** Befunde
  dieses Schritts.

---

*Erzeugt in Schritt 8 (Sicherheit & Compliance), dritte Runde. Kanonisches Artefakt:
`.agent/artifacts/test-report.md`.*
