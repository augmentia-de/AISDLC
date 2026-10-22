# QA-Bericht — EchoBox (Schritt 8, **zweite Runde**)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · Schritt: **Sicherheit & Compliance**
> (QA + Security-Audit). Gate: **GATE 2** — QA 0 Critical, Security 0 Critical und 0 High.
> Kanonisches Artefakt: `.agent/artifacts/test-report.md` · Security-Audit:
> [`docs/qa/security-audit.md`](security-audit.md).
>
> **Runde 2** — nachprueft und erneut auditiert. Die Nacharbeit (`d135cda`, Doku
> `28396e0`) hat S-19…S-27 und A-1 behoben; diese Runde hat **einen neuen High**
> gefunden (S-28) und den QA-Stand neu gemessen.

---

## 1. Kurzfassung

| Frage | Antwort | Beleg |
|---|---|---|
| Laeuft die Suite gruen? | **Fast.** Die bestehende Suite (399 Tests) ist gruen; **4 neue Gap-Tests** schlagen fehl — sie sind die **gewollten Belege** der vier belegten Luecken dieser Runde (Abschnitt 6). Die sechs Gap-Tests der **ersten** Runde sind **alle gruen** — die Nacharbeit hat die belegten Luecken geschlossen. | `mvn -B verify` → Unit **118 grün / 2 rot**, Integration **281 grün / 2 rot**; `jacoco:check` → „All coverage checks have been met." (Abschnitt 2). |
| Sind die 101 Akzeptanzkriterien abgedeckt? | **Ja — 101 von 101 (100 %)**, unabhaengig nachgeprueft: jede AK-ID steht im Namen **eines existierenden Testfalls** einer Datei, die die Matrix nennt; alle 60 matrix-genannten Testdateien existieren. | Abschnitt 4; `python3 .agent/tools/traceability_check.py` → gruen. |
| Gibt es Regressionen? | **Nein.** In der Nacharbeit wurde **kein** Testfall, **keine** Annotation und **keine** Assertion entfernt oder abgeschwaecht. | Abschnitt 5 (Diff-Analyse `d135cda`, `28396e0`). |
| Wie hoch ist die Testabdeckung des Codes? | **86,22 % Zeilen** (2 228/2 584) — die Schwelle von 80 % ist **erfuellt** und im Build **erzwungen** (JaCoCo `check`, Phase `verify`). | Abschnitt 6; `target/site/jacoco/jacoco.csv`. |
| QA-Bewertung | **0 Critical** — die Gate-Halbbedingung „QA 0 Critical" ist **erfuellt**. | Abschnitt 9. |
| Security-Bewertung | **0 Critical, 1 High, 4 Medium, 2 Low** → **blockieren**. | [`security-audit.md`](security-audit.md). |

---

## 2. Teststand je Ebene — **ausgefuehrt**, nicht behauptet

Alle Kommandos wurden am 2026-10-22 in diesem Arbeitsverzeichnis ausgefuehrt; die
Ausgaben liegen unter `/tmp/opencode/*.log`.

| # | Ebene | Kommando | Ergebnis |
|---|---|---|---|
| 1 | **Java Unit** (Surefire) | `mvn -B -o verify -Dmaven.test.failure.ignore=true` | `Tests run: 120, Failures: 2, Errors: 0` → **118 gruen**, 2 bewusst rot (Abschnitt 6.2) |
| 2 | **Java Integration** (Failsafe, PostgreSQL 16.15 aus Testcontainers, WireMock, eine JVM je Klasse) | dieselbe Ausfuehrung | `Tests run: 283, Failures: 2, Errors: 0` → **281 gruen**, 2 bewusst rot |
| 3 | **Java Coverage-Gate** | dieselbe Ausfuehrung | `jacoco:check` → **„All coverage checks have been met."** — **86,22 % Zeilen** (Gate: 80 %) |
| 4 | **Java Vertrag** (`tests/contract`) | `mvn -B -o -Pcontract-performance verify` | `SignatureContractTest` 6 ✓ · `AlwaysPresentFieldsContractTest` 7 ✓ · `RejectionContractTest` 6 ✓ → **19 gruen** (Unit gesamt in diesem Lauf: 141 = 120 + 19 + 2, davon 2 rot) |
| 5 | **Java Last** (`tests/performance`) | dieselbe Ausfuehrung | `EventIntakeLoadTest` **2 gruen** (1 000 Einlieferungen, p95 < 200 ms) || 6 | **Frontend Typpruefung** | `npx tsc --noEmit` (in `src/main/frontend`) | **Exit 0** — „TypeScript: No errors found" (`strict: true`, `noUnusedLocals`, `noImplicitOverride`) |
| 7 | **Frontend Lint** | `npm run lint` | **Exit 0** — „ESLint: No issues found" |
| 8 | **Frontend Unit** (Vitest) | `npx vitest run` | **5 Dateien, 30 Tests gruen** |
| 9 | **Frontend E2E** (Playwright, Chromium) | `npx playwright test` | **31 Tests gruen** (12,5 s) |
| 10 | **Frontend Build** | `npm run build` | **Exit 0** — Next.js-Build erfolgreich |
| 11 | **Traceability-Kette** | `python3 .agent/tools/traceability_check.py` | **gruen** — „Keine Luecken — die Kette Anforderung → Story → Feature → Test → Code ist vollstaendig." |
| 12 | **Schema-Spiegel 1** | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | **leer** („Files are identical") |
| 13 | **Schema-Spiegel 2** | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** („Files are identical") |
| 14 | **Secret-Scan** | `grep -rInE "(api[_-]?key\|secret\|password\|token\|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ Dockerfile` | 2 Treffer, beide Test-Fixtures (`tests/unit/frontend/EndpointsPage.test.tsx:177`, `:224`) |
| 15 | **Secret-Scan Git-Historie** | `git log --all -p -- . ':!docs' ':!.agent' \| grep -InE "(api[_-]?key\|secret\|password\|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{12,}"` | **keine** Treffer |
| 16 | **SCA Java** | `mvn -B dependency:tree` (110 Koordinaten) → OSV `querybatch` | **20** Advisories (Runde 1: 96) — die 3 zuvor erreichbaren sind **behoben**; die 3 neuen Tomcat-Advisories sind **nicht erreichbar**, der Pin liegt aber unter der Fix-Linie (**S-31**) |
| 17 | **SCA Node** | `npm audit --json` (in `src/main/frontend`) | 10 Meldungen (2 Critical, 6 High, 2 Moderate) — **alle** in `devDependencies`, **keine** in den Laufzeit-Abhaengigkeiten |
| 18 | **Vertrag unveraendert** | `git diff --stat -- docs/api/` | **leer** — der eingefrorene Vertrag ist unveraendert |

### 2.1 Verteilung der Testfaelle

| Ebene | Testdateien | Testfaelle | gruen | fehlschlagend |
|---|---|---|---|---|
| Java Unit (`tests/unit`, Surefire) | 22 Java (+ 1 Hilfsklasse, + 5 TSX fuer Vitest) | 120 | 118 | **2 (bewusst — S-29, S-31)** |
| Java Integration (`tests/integration`, Failsafe, PostgreSQL 16.15 aus Testcontainers) | 38 `*IT` (+ 8 Hilfsklassen) | 283 | 281 | **2 (bewusst — S-28, S-30)** |
| Java Vertrag (`tests/contract`) | 3 Testklassen (+ 2 YAML-Fixtures) | 19 | 19 | 0 |
| Java Last (`tests/performance`) | 1 | 2 | 2 | 0 |
| Frontend Unit (Vitest, `tests/unit/frontend`) | 5 | 30 | 30 | 0 |
| Frontend E2E (Playwright, `tests/e2e`) | 5 Specs (+ 2 Stuetzdateien) | 31 | 31 | 0 |
| **Summe** | **90 Dateien unter `tests/`** | **485** | **481** | **4 (bewusst)** |

---

## 3. Die Nachpruefung der Security-Befunde — Ergebnis in einer Zeile je Befund

| ID | Schwere (Runde 1) | Bewertung Runde 2 | Fundstelle / Nachweis |
|---|---|---|---|
| **S-19** Abhaengigkeiten | High | **behoben** | `mvn dependency:tree` → `tomcat-embed-core:10.1.55`; OSV: 96 → 20, die drei erreichbaren CVEs **nicht** mehr enthalten. Einschraenkung → **S-31**. |
| **S-20** Request-Body | High | **teilweise behoben** → **S-28** | `readBounded` existiert (`TenantParameterFilter.java:139-158`), `SecurityGapIT` + `RequestBodyLimitIT` gruen — aber die Grenze haengt am Medientyp (`isBodyRelevant`, `:160-167`). |
| **S-21** JWT-Audience/`exp` | Medium | **teilweise behoben** → **S-29** | `SecurityConfiguration.java:112-163` (Zeitstempel, `exp` Pflicht, Audience); `SecurityGapTest` ×2 + `JwtAudienceHardeningTest` gruen — aber `:126` faellt bei leerer Audience still aus. |
| **S-22** Schluessellaenge | Medium | **behoben** | `CoreConfiguration.java:71-79` (exakt 32 Byte, sonst Startabbruch); `CryptoKeyLengthTest` gruen. |
| **S-23** Antwort-Body | Medium | **behoben** | `DeliveryHttpClient.java:135-187` (`LimitedBodySubscriber`, 1 MiB); `DeliveryResponseLimitTest` gruen. |
| **S-24** 429 + `Retry-After` | Medium | **behoben** | `ApiRateLimitFilter` + `ApiRateLimiter`; `Retry-After` = ganzzahlige Sekunden >= 1, vertragskonform (`openapi.yaml:1826-1830`); `SecurityGapIT` + `ApiRateLimitIT` + neu `ApiRateLimitHardeningTest` gruen. |
| **S-25** CI | Medium | **behoben** (strukturell) → **S-32** | `deploy.yml`: gitleaks, OSV-Scanner, Java inkl. Coverage-Gate, Typpruefung, Lint, Vertrag, Unit, Build, Playwright; `GITHUB_TOKEN` statt statischem Token; `id-token: write`. Aber: **keine Baseline** → der SCA-Schritt schlaegt immer an. |
| **S-26** apt-Pinning | Low | **behoben** | `Dockerfile:36-42`; `ContainerHardeningIT` gruen. |
| **S-27** Produktionsprofil | Low | **behoben** | `application-production.yml:25` (`key-required: true`), `Dockerfile:63` (`production`), `deploy.yml:127/:155` (Profil je Umgebung); `ContainerHardeningIT` gruen. |
| **A-1** Coverage-Gate | QA | **behoben** | `pom.xml:224-277` (JaCoCo `check`, `minimum 0.80`, Phase `verify`, `append=true`, Loeschschritt) → „All coverage checks have been met." |
| **A-6 / B-17** Uebergangstabelle | QA | **behoben** (Code + Doku) | `DeliveryStateMachine.TRANSITIONS:33-41` + `onRateLimited:126-131`; `docs/architecture/domain_model.md` §3.3 (Commit `28396e0`). Rest: veralteter Javadoc → **S-34**. |

**8 behoben · 2 teilweise behoben (mit neuem, belegtem Befund) · 0 offen geblieben.**

---

## 4. Abdeckung der 101 Akzeptanzkriterien

### 4.1 Die gepruefte Menge

| Quelle | Anzahl AK |
|---|---|
| `docs/requirements/traceability_matrix.json` → `acceptance_criteria_index` | **101** |
| `docs/requirements/traceability_matrix.json` → `entries[].acceptance_criteria` (24 Eintraege) | **101** (identische Menge) |
| `docs/requirements/stories/US-0xx.json` → `acceptance_criteria` | **101** (identische Menge) |

*Abweichung der Mengen: **keine*** — `entries` und `acceptance_criteria_index` sind
**mengengleich** (eigenstaendig geprueft: `entries-AK == index-AK → True`).

### 4.2 Das Pruefverfahren (eigenstaendig nachgefahren)

**Eine gefuellte Matrix zaehlt nicht als Beleg.** Geprueft wurde deshalb gegen die
**Wirklichkeit** — mit einem eigenen Skript, nicht ueber `traceability_check.py`
(das nur prueft, ob ueberhaupt ein Test genannt wird):

1. **Existiert jede Testdatei, die die Matrix nennt?** → **0 fehlend** (alle 60
   matrix-genannten Dateien liegen auf der Platte).
2. **Steht die AK-ID im Namen eines Testfalls** in einer dieser Dateien? Geprueft
   wurde gegen `@DisplayName(...)`, `@ParameterizedTest(name=...)`, den Java-Methodennamen
   und `it(...)`/`test(...)`/`describe(...)` — **nicht** gegen ein Vorkommen irgendwo im Text.

```
geprueft: 101 AK
belegt : 101 AK  (jede AK-ID steht im Namen mindestens eines Testfalls einer matrix-genannten Datei)
Lücken : 0
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
| `d135cda` (Schritt 6/7, Nacharbeit) | **0** | **0** | 8 neue Testdateien, 2 bestehende **erweitert** (keine Loeschung); `+955 / −2` Zeilen in `tests/` — die 2 geloeschten Zeilen sind die **umbenannten** `aud`-Zeilen in `JwtTestKeys` (Erweiterung, keine Abschwaechung) |
| `28396e0` (Schritt 2, Doku) | **0** | **0** | **keine** Datei in `tests/` beruehrt (`git show --stat 28396e0 -- tests/` ist leer) |

*Belege:*
`git show d135cda -- tests/ | grep -E "^-" | grep -cE "@Test|@DisplayName|void [a-zA-Z0-9_]+\("` → `0` ·
`git show d135cda -- tests/ | grep -E "^-" | grep -cE "assertThat|assertEquals|assertThrows|assertThatThrownBy"` → `0` ·
`git diff --stat 1ba4160..HEAD -- tests/` → 9 Dateien, alle **neu oder erweitert**.

**Kein Test wurde abgeschwaecht, uebersprungen oder ausgegliedert:**
`grep -rn "@Disabled\|@Ignore" tests/` → **keine** Treffer.
Die Aenderung an `AbstractApplicationIT` (+6 Zeilen) **erweitert** die
Basisklasse um ein hohes Anfragelimit (`echobox.rate-limit.limit=100000`) fuer die
Testsuite — sie **entlastet** die Tests vom neuen Betriebslimit, **ersetzt** es aber
nicht: das **Auslieferungs-Limit** (240) wird in `SecurityGapIT` und `ApiRateLimitIT`
geprueft. Das ist keine Abschwaechung, sondern die Trennung von „Betriebswert" und
„geprueftes Verhalten".

### 5.2 Was dieser Schritt **nicht** veraendert hat

Kein bestehender Test wurde geloescht, abgeschwaecht, umbenannt oder verschoben. Neu
angelegt wurden **drei** Dateien (Abschnitt 6).

---

## 6. Coverage — und die jetzt erzwungene Schwelle

### 6.1 Das Gate ist im Build verankert

| Nachweis | Ergebnis |
|---|---|
| `pom.xml:224-277` | JaCoCo 0.8.13 mit `prepare-agent` (`append=true`), `report` und **`check`** in der Phase **`verify`** (also nach Surefire **und** Failsafe) |
| Regel | `BUNDLE` / `LINE` / `COVEREDRATIO` / **`minimum 0.80`** |
| `pom.xml:183-206` | Loeschschritt in `initialize` — `target/jacoco.exec` wird vor dem Lauf entfernt, sonst zaehlte das Gate die Daten **aller** frueheren Laeufe mit |
| `mvn -B verify` | **`jacoco:check` → „All coverage checks have been met."** |

**A-1 ist damit erledigt:** die Schwelle ist nicht mehr eine Zusage, sondern eine
**Bedingung, deren Bruch den Build abbricht**.

### 6.2 Die gemessenen Zahlen

Aus `target/site/jacoco/jacoco.csv` des Laufs vom 2026-10-22:

| Metrik | gedeckt | gesamt | **Abdeckung** | Schwelle 80 % |
|---|---|---|---|---|
| **Zeilen** | 2 228 | 2 584 | **86,22 %** | **erfuellt, erzwungen** ✅ |
| Anweisungen (Instruction) | 10 489 | 12 342 | 84,99 % | erfuellt ✅ |
| Zweige (Branch) | 584 | 931 | 62,73 % | **nicht** erzwungen ⚠️ |
| Methoden | 578 | 641 | 90,17 % | erfuellt ✅ |

Abdeckung je Paket (Zeilen):

| Paket | Abdeckung | Paket | Abdeckung |
|---|---|---|---|
| `com.echobox.config` | 96,74 % | `com.echobox.tenant` | 89,15 % |
| `com.echobox.event` | 96,35 % | `com.echobox.endpoint` | 86,88 % |
| `com.echobox.protocol.api` | 95,51 % | `com.echobox.event.api` | 78,85 % |
| `com.echobox.protocol` | 92,75 % | `com.echobox.crypto` | 72,06 % |
| `com.echobox.delivery` | 91,35 % | `com.echobox.endpoint.api` | 68,49 % |
| `com.echobox.operations` | 64,52 % | `com.echobox.delivery.api` | 63,64 % |
| `com.echobox.common` | 58,97 % | `com.echobox` (Startklasse) | 33,33 % |

> **Hinweis:** die Zweigabdeckung (62,73 %) ist **nicht** Teil des Gates (das
> Gate misst Zeilen). Sie ist ein Hinweis fuer die Teststrategie, **kein**
> Sicherheitsbefund: die niedrigsten Pakete sind die API-Records (Konstruktoren,
> `equals`/`hashCode`) und die Fehlerklassen. **Abweichung A-9** (siehe Abschnitt 8).

---

## 7. Die vier neuen Gap-Tests — Abwehr (gruen) und Beleg der Luecke (rot)

### 7.1 Neue Abwehrtests — **alle gruen** (5)

| Test | Datei | Beweist |
|---|---|---|
| `ApiRateLimitHardeningTest` (5 Tests) | `tests/unit/security/ApiRateLimitHardeningTest.java` | Der Zaehler des Anfragelimits gilt **je Mandant**; `Retry-After` ist eine **Wartezeit in Sekunden** (>= 1) und **nie kuerzer** als die Wartezeit bis zum Fensterende; ein neues Fenster beginnt von vorn; ein Limit von 0 lehnt jede Anfrage ab; die Grenze kommt aus der Konfiguration (kein Wert im Code verdrahtet). |

### 7.2 Die Gap-Tests — **schlagen fehl** (4) und sind der **Beleg** der Luecke

| Test | Datei | Befund | Fehlschlagmeldung (Beleg) |
|---|---|---|---|
| `LUECKE (Befund S-28)` | `tests/integration/security/SecurityGapRound2IT.java` | **S-28 (High)** | `expected: 413 but was: 202` — ein 2.097.298-Byte-Body mit `Content-Type: application/vnd.custom+json` wurde **angenommen** (die Gegenprobe mit 20.971.604 Byte: ebenfalls 202) |
| `LUECKE (Befund S-29)` | `tests/unit/security/SecurityGapRound2Test.java` | **S-29 (Medium)** | `Expecting code to raise a throwable` — ohne konfigurierte Audience wird ein Token mit **fremder** Zielgruppe **angenommen** |
| `LUECKE (Befund S-30)` | `tests/integration/security/SecurityGapRound2IT.java` | **S-30 (Medium)** | `Expecting actual: 500 to be between: [400, 499]` — ein nicht unterstuetzter Medientyp ergibt **500** |
| `LUECKE (Befund S-31)` | `tests/unit/security/SecurityGapRound2Test.java` | **S-31 (Medium)** | `Expecting value to be true but was false` — der aufgeloeste Container ist **10.1.55**, die Fix-Linie ist **10.1.59** |

**Jeder dieser Tests schlaegt genau an der Luecke fehl** — nicht an einem Randfall
daneben: die Fehlermeldung benennt jeweils die verletzte Eigenschaft.

### 7.3 Die sechs Gap-Tests der **ersten** Runde — **alle gruen** (unveraendert)

| Gap-Test der Runde 1 | Datei | vorher | jetzt |
|---|---|---|---|
| `a_chunked_request_body_above_two_mebibytes_must_be_rejected` (S-20) | `SecurityGapIT` | 202 statt 413 | **gruen (413)** |
| `the_request_limit_must_answer_with_429` (S-24) | `SecurityGapIT` | nie eine 429 | **gruen (429 + `Retry-After`)** |
| `a_token_with_a_foreign_audience_must_be_rejected` (S-21) | `SecurityGapTest` | angenommen | **gruen (abgelehnt)** |
| `a_token_without_an_expiry_must_be_rejected` (S-21) | `SecurityGapTest` | angenommen | **gruen (abgelehnt)** |
| `a_sixteen_byte_key_must_prevent_the_start` (S-22) | `SecurityGapTest` | angenommen | **gruen (Start verhindert)** |
| `the_response_body_of_the_receiver_must_be_read_with_a_limit` (S-23) | `SecurityGapTest` | 5 MiB gelesen | **gruen (<= 1 MiB)** |

**Die Gap-Tests der Runde 1 wurden nicht veraendert** — sie sind die Abnahme der
Behebung. Die vier neuen Gap-Tests folgen demselben Muster: sie sind die **Abnahme**
der Behebung von S-28, S-29, S-30 und S-31.

> **Bewusst roter Build.** Die vier neuen Gap-Tests sind **aktive** Tests; sie schlagen
> fehl, bis der Ruesprung zu Schritt 6/7 die Luecken schliesst. Damit ist der Build
> ab diesem Commit rot — das ist die **Absicht**: die Luecken sind so lange sichtbar,
> wie sie bestehen. Weil Surefire vor Failsafe laeuft, bricht ein einfaches
> `mvn -B verify` nach der Unit-Ebene ab; fuer den vollstaendigen Lauf (alle 485
> Tests) ist `-Dmaven.test.failure.ignore=true` zu verwenden (so wurde in Abschnitt 2
> ausgefuehrt).

---

## 8. Abweichungen

| ID | Abweichung | Auswirkung | Empfehlung / Owner |
|---|---|---|---|
| **A-1** (erledigt) | Die Coverage-Messung war nicht im Build verankert. | — | **erledigt**: JaCoCo `check` in `pom.xml`, Schwelle 0,80, Phase `verify`. |
| **A-2** (erledigt) | `.agent/artifacts/traceability-verification.md` nannte „130 AK"; die Matrix enthaelt **101**. | Die Zahl im Nachzug-Artefakt war falsch; die Matrix ist richtig. | **erledigt** (Commit `825656a`); die Abdeckung selbst ist unberuehrt (101/101). |
| **A-3** (neu) | `mvn -B verify` ist mit den vier neuen Gap-Tests **rot**. | Der Build bricht nach der Unit-Ebene ab, wenn kein Flag gesetzt ist. | **gewollt** — siehe 7.2; nach dem Ruesprung gruen. |
| **A-4** (offen) | `docker-compose.yml` fehlt (Matrix nennt den Pfad fuer NFR-006). | Lokale Entwicklung ohne Compose; die Testabdeckung ist **nicht** betroffen. | **Schritt 9.** |
| **A-5** (offen) | Fuenf Codepfade der Matrix sind **historische Namen** (`SecretStore`, `PayloadSource`, `RetryScheduler`, `TenantConnectionInitializer`, `TenantInstanceConfiguration`). | Keine Abdeckungsluecke; die Matrix verweist ins Leere. | **Schritt 1/2** — Matrix nachziehen. |
| **A-6 / B-17** (erledigt) | Die Uebergangstabelle des Domänenmodells war inkonsistent zum Code. | — | **erledigt**: Code (`DeliveryStateMachine.TRANSITIONS`, `onRateLimited`) **und** Doku (`domain_model.md` §3.3, Commit `28396e0`). Rest: veralteter Javadoc → **S-34**. |
| **A-7** (offen) | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.). | Kein Sicherheitsbefund, kein Gate-Effekt; die Groesse stammt ueberwiegend aus den SQL-Konstanten und den Row-Mappern, die Kohaesion ist gut (je Klasse **ein** Aggregat). | **Schritt 9** (Wartbarkeit): `DeliveryRepository` in drei Repositories teilen. |
| **A-8** (erledigt) | Kein Test pruefte die **eingehende** Ratenbegrenzung — weil es sie nicht gab (S-24). | — | **erledigt**: `ApiRateLimitIT` (4) + `ApiRateLimitHardeningTest` (5) + der Gap-Test der Runde 1. |
| **A-9** (neu) | Die **Zweigabdeckung** liegt bei 62,73 %; das Gate misst nur **Zeilen** (86,22 %). | Kein Sicherheitsrisiko, aber eine Verzerrung: Fehlerpfade (z.B. `catch`-Zweige) sind schlechter getestet als der Normalpfad. | **Schritt 9** (Teststrategie): Zweigschwelle ergaenzen oder die niedrigsten Pakete gezielt testen. |
| **A-10** (neu) | Der **SCA-Gate der Pipeline** schlaegt auf jedem Lauf an (keine Baseline) → **S-32**. | Die Pipeline liefert kein Image; der Bereitstellungsweg ist blockiert. | **Schritt 9** (Baseline `osv-scanner.toml`). |

---

## 9. Offene Punkte

### 9.1 Die aus den Vorgaengen uebernommenen offenen Punkte

| ID | Thema | Owner | Stand nach Schritt 8, Runde 2 |
|---|---|---|---|
| **O-04** | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz, Gate 2 | **unveraendert offen.** Die **Architekturseite** ist umgesetzt und belegt (AES-256-GCM, Schluessel ausserhalb der Datenbank, keine Klartextanteile in Tabellen/Indizes/Logs, nur Metadaten in der Protokollsuche). Die **fachliche** Frage (Muss? durchsuchbar?) entscheidet der Fachbereich. Zwei technische Randnotizen: (a) die ADR-003-Vorgabe „je Mandant ein eigener DEK" ist **nicht** umgesetzt — es gibt **einen** globalen Schluessel; (b) die **Schluessellaenge wird jetzt geprueft** (S-22 erledigt). |
| **O-05** | AVV bei Fremddaten | Fachbereich + Recht, Gate 2 | **unveraendert offen.** Technisch datenminimierend; ein AVV ist eine rechtliche Zusage ausserhalb der technischen Pruefung. |
| **R-01 / E-13** | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich, Gate 2 | **unveraendert offen.** `purge-delay` ist konfigurierbar, ohne Wert wird mit 0 Tagen gerechnet; der Wert kommt vom Fachbereich. |
| **E-12** | Haertung der mandantenuebergreifenden Sicht | Fachbereich + Plattform, Gate 2 | **unveraendert offen — im Audit bestaetigt:** die Applikationsrolle kann `app.cross_tenant` setzen; die drei Funktionen liefern **nur** die Vertragsprojektion und sind fail-closed. |
| **B-18** | Idempotenzschluessel der Endpunkt-Registrierung | Fachbereich, Gate 2 | **unveraendert offen.** `endpoint_event_types` hat den Primaerschluessel `(endpoint_id, event_type)`; ob ein **je Mandant** eindeutiger Schluessel gewuenscht ist, ist eine fachliche Frage. |
| **S-18** | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.) | Schritt 9 | **bewertet** — siehe **A-7**: kein Sicherheitsbefund, kein Gate-Effekt. |
| **S-22** (QA) | `docker-compose.yml` fehlt | Schritt 9 | **unveraendert offen.** |
| **S-23** (QA) | `.gitignore`: `/test-results/` aus der Wurzel | Schritt 9 | **erledigt** — `/test-results/` **und** `/playwright-report/` stehen jetzt in `.gitignore`. |
| **Q-1 … Q-4, Q-7** | TLS/HSTS, Branch-Schutz, Recht auf Loeschung, SSRF-interne Bereiche, Schluesselverwaltung | Schritt 9 / Fachbereich | **unveraendert offen** (siehe `security-audit.md`, Anhang C). |
| **Q-5, Q-6** | jackson-ReDoS, CVE-2026-22732 | — | **erledigt**: Q-5 **empirisch** als nicht erreichbar belegt (Anhang A.2), Q-6 durch Spring Security 6.5.11 erledigt. |
| **Q-8, Q-9** | Rate-Limit je Instanz; nicht angemeldete Anfragen nicht gezaehlt | Schritt 9 / Fachbereich | **neu, offen** — Randbedingungen des Betriebs, **keine** Befunde (Details in `security-audit.md`, Anhang C). |

### 9.2 Neue offene Punkte aus dieser Runde

| ID | Thema | Owner |
|---|---|---|
| **S-28** | Die Grenze des Request-Bodies haengt am Medientyp (`application/*+json` umgeht sie) | Schritt 6/7 |
| **S-29** | Die Audience-Pruefung faellt bei leerer Konfiguration still aus (fail-open) | Schritt 6/7 |
| **S-30** | Ein nicht unterstuetzter Medientyp ergibt 500 mit Stacktrace statt 4xx | Schritt 6/7 |
| **S-31** | Der gepinnte Container (10.1.55) liegt unter der Fix-Linie (10.1.59) von drei Tomcat-Advisories | Schritt 6/7 |
| **S-32** | Der SCA-Gate der Pipeline hat keine Baseline und schlaegt immer an | Schritt 9 |
| **S-33** | Ein Token ohne `organisation_id` ergibt 500 und umgeht das Anfragelimit | Schritt 6/7 |
| **S-34** | Veralteter Javadoc in `DeliveryStateMachine` | Schritt 6/7 |

---

## 10. QA-Bewertung und Freigabe-Empfehlung

| Kriterium des Gates | Wert | Erfuellt? |
|---|---|---|
| **QA: 0 Critical** | 0 Critical | **ja** |
| Security: 0 Critical | 0 | **ja** |
| **Security: 0 High** | **1** (S-28) | **nein** |
| Alle Tests gruen | 481 gruen; **4 bewusst** fehlschlagende Gap-Tests (Beleg der Luecken S-28, S-29, S-30, S-31) | **mit Einschraenkung** |
| Keine bekannten kritischen Luecken | 1 High (S-28) | **nein** |
| Keine Regressionen | keine (0 entfernte Tests/Assertions in der Nacharbeit) | **ja** |
| AK-Abdeckung | **101/101** (eigenstaendig nachgefahren) | **ja** |
| Coverage >= 80 % | **86,22 % Zeilen** (2 228/2 584) — **im Build erzwungen** (JaCoCo `check`) | **ja** |
| Vertrag unveraendert | `git diff --stat -- docs/api/` leer | **ja** |
| Schema-Spiegel | beide **leer** | **ja** |
| Traceability-Kette | gruen | **ja** |

### Freigabe-Empfehlung: **BLOCKIEREN**

* **Fuer das Deployment (Schritt 9): nein.** Die Gate-Bedingung „Security 0 Critical und
  0 High" ist mit **1 High** nicht erfuellt. Der High ist **neu** in dieser Runde und
  **belegt** (ausgefuehrter Check, Datei:Zeile, Fehlermeldung des Gap-Tests).
* **Fuer den Ruesprung: ja, unverzueglich.** Erforderlich sind:
  1. **S-28 (High)** — die Grenze des Request-Bodies unabhaengig vom Medientyp
     durchsetzen (serverseitig begrenzt lesen fuer **jeden** Body, den ein Converter
     liest; idealerweise nur `application/json` akzeptieren). *Schritt 6/7.*
  2. **S-29 (Medium)** — die Audience-Pruefung darf bei leerer Konfiguration nicht still
     ausfallen (`audience-required` oder Startabbruch). *Schritt 6/7.*
  3. **S-30 (Medium)** — `HttpMediaTypeNotSupportedException` auf 400 `malformed_request`
     abbilden, statt 500 mit Stacktrace. *Schritt 6/7.*
  4. **S-31 (Medium)** — `<tomcat.version>` auf mindestens 10.1.59 heben und die beiden
     Regressionstests mitziehen. *Schritt 6/7.*
  5. **S-32 (Medium)** — Baseline fuer den SCA-Gate (`osv-scanner.toml`). *Schritt 9.*
  6. **S-33, S-34 (Low)** — Token ohne Mandant als 401 statt 500; veralteten Javadoc
     berichtigen. *Schritt 6/7.*
  7. **Q-8** (Rate-Limit je Instanz) — Entscheidung ueber ein Schema; *Schritt 9*.
* **Die vier neuen Gap-Tests** sind die **Abnahme** dieser Behebung: sie werden gruen,
  ohne dass eine Zeile geaendert werden muss.

**O-04, O-05, R-01/E-13, E-12, B-18, Q-3, Q-4, Q-7, Q-9** sind **fachliche bzw. rechtliche
Entscheidungen** und bleiben dem **Gate 2** vorbehalten — sie sind **keine** technischen
Blocker fuer die Umsetzung und **keine** Befunde dieses Schritts.

---

*Erzeugt in Schritt 8 (Sicherheit & Compliance), zweite Runde. Kanonisches Artefakt:
`.agent/artifacts/test-report.md`.*
