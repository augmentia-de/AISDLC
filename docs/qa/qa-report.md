# QA-Bericht — EchoBox (Schritt 8)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · Schritt: **Sicherheit & Compliance**
> (QA + Security-Audit). Gate: **GATE 2** — QA 0 Critical, Security 0 Critical und 0 High.
> Kanonisches Artefakt: `.agent/artifacts/test-report.md` · Security-Audit:
> [`docs/qa/security-audit.md`](security-audit.md).

---

## 1. Kurzfassung

| Frage | Antwort | Beleg |
|---|---|---|
| Läuft die Suite grün? | **Ja** — die bestehende Suite (347 Tests) ist grün. **Sechs neue Tests schlagen fehl**; sie sind die **gewollten Belege** der belegten Sicherheitslücken (Abschnitt 6). | `mvn -B verify` → `BUILD SUCCESS`, 89 + 258 = 347 grün (Lauf **vor** dem Hinzunahme der neuen Tests). |
| Sind die 101 Akzeptanzkriterien abgedeckt? | **Ja — 101 von 101 (100 %)**. Jede AK-ID steht im Namen **eines existierenden Testfalls** einer Datei, die die Matrix nennt. | Abschnitt 4; `python3 .agent/tools/traceability_check.py` → grün. |
| Gibt es Regressionen? | **Nein.** In Schritt 6 und 7 wurde **kein** Testfall, **keine** Annotation und **keine** Assertion entfernt. | Abschnitt 5 (Diff-Analyse `e9e1bc1`, `bd15f19`). |
| Wie hoch ist die Testabdeckung des Codes? | **85,4 % Zeilen** (2 084/2 440) — die Schwelle von 80 % ist **erfüllt**, aber **nicht im Build erzwungen** (kein Plugin). | Abschnitt 5; `mvn … org.jacoco:jacoco-maven-plugin:0.8.12:report` → „Analyzed bundle 'echobox' with 133 classes". |
| QA-Bewertung | **0 Critical** — die Gate-Halbbedingung „QA 0 Critical" ist **erfüllt**. | Abschnitt 8. |
| Security-Bewertung | **0 Critical, 2 High, 5 Medium, 2 Low** → **blockieren**. | [`security-audit.md`](security-audit.md). |

---

## 2. Teststand je Ebene — **ausgeführt**, nicht behauptet

Alle Kommandos wurden am 2026-10-22 in diesem Arbeitsverzeichnis ausgeführt; die
Ausgaben liegen unter `/tmp/opencode/*.log`.

| # | Ebene | Kommando | Ergebnis |
|---|---|---|---|
| 1 | **Java Unit** (Surefire) | `mvn -B verify` (Lauf **vor** den neuen Tests) | **BUILD SUCCESS** — `Tests run: 89, Failures: 0, Errors: 0, Skipped: 0` |
| 2 | **Java Integration** (Failsafe, PostgreSQL 16.15 aus Testcontainers, WireMock, eine JVM je Klasse) | `mvn -B verify` (vorher) | `Tests run: 258, Failures: 0, Errors: 0, Skipped: 0` — **BUILD SUCCESS** |
| 3 | **Java Unit + Integration nach den neuen Tests** | `mvn -B verify -Dmaven.test.failure.ignore=true` | Unit `Tests run: 100, Failures: 4` · Integration `Tests run: 267, Failures: 2` — **die 6 Fehlschläge sind ausschließlich die 6 Gap-Tests** (Abschnitt 6) |
| 4 | **Java Vertrag** (`tests/contract`) | `mvn -B -Pcontract-performance verify` | `SignatureContractTest` 6 ✓ · `AlwaysPresentFieldsContractTest` 7 ✓ · `RejectionContractTest` 6 ✓ → **19 grün** |
| 5 | **Java Last** (`tests/performance`) | dieselbe Ausführung | `EventIntakeLoadTest` **2 grün** (Laufzeit 18,6 s) |
| 6 | **Frontend Typprüfung** | `npx tsc --noEmit` (in `src/main/frontend`) | **Exit 0** (`strict: true`, `noUnusedLocals`, `noImplicitOverride`) |
| 7 | **Frontend Lint** | `npm run lint` | **Exit 0** — keine Warnung |
| 8 | **Frontend Unit** (Vitest) | `npx vitest run` | **5 Dateien, 30 Tests grün** (1,23 s) |
| 9 | **Frontend E2E** (Playwright, Chromium) | `npx playwright test` | **31 Tests grün** (15,5 s) |
| 10 | **Traceability-Kette** | `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken — die Kette Anforderung → Story → Feature → Test → Code ist vollstaendig." |
| 11 | **Schema-Spiegel 1** | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | **leer** („Files are identical") |
| 12 | **Schema-Spiegel 2** | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** („Files are identical") |
| 13 | **Secret-Scan** | `grep -rInE "(api[_-]?key\|secret\|password\|token\|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ Dockerfile` | 2 Treffer, beide Test-Fixtures (`tests/unit/frontend/EndpointsPage.test.tsx:177`, `:224`) |
| 14 | **Secret-Scan Git-Historie** | `git log --all -p -- . ':!docs' ':!.agent' \| grep -InE "(api[_-]?key\|secret\|password\|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{12,}"` | **keine** Treffer |
| 15 | **SCA Java** | OSV `querybatch` über `mvn dependency:tree` (76 Koordinaten) | 96 Advisories → **2 erreichbare High** (S-19) |
| 16 | **SCA Node** | `npm audit --json` (in `src/main/frontend`) | 10 Meldungen (2 Critical, 6 High, 2 Moderate) — **alle** in `devDependencies`, **keine** in den Laufzeit-Abhängigkeiten |

### 2.1 Verteilung der Testfälle

| Ebene | Testdateien | Testfälle | grün | fehlschlagend |
|---|---|---|---|---|
| Java Unit (`tests/unit`, Surefire) | 17 Java (+ 5 TSX für Vitest) | 100 | 96 | 4 (Gap-Tests) |
| Java Integration (`tests/integration`, Failsafe, PostgreSQL 16.15 aus Testcontainers) | 34 `*IT` (+ 8 Hilfsklassen) | 267 | 265 | 2 (Gap-Tests) |
| Java Vertrag (`tests/contract`) | 3 Testklassen (+ 2 YAML-Fixtures) | 19 | 19 | 0 |
| Java Last (`tests/performance`) | 1 | 2 | 2 | 0 |
| Frontend Unit (Vitest, `tests/unit/frontend`) | 5 | 30 | 30 | 0 |
| Frontend E2E (Playwright, `tests/e2e`) | 5 Specs (+ 2 Stützdateien) | 31 | 31 | 0 |
| **Summe** | **61 Testdateien** | **449** | **443** | **6 (bewusst)** |

---

## 3. Regressionsprüfung

### 3.1 Kein Test wurde entfernt oder abgeschwächt

Die Schritte 6 und 7 haben die Testsuite geändert. Geprüft wurde die **Diff-Ebene**,
nicht die Prosa:

| Commit | Entfernte `@Test`/`@DisplayName`/Testmethoden | Entfernte Assertions (`assertThat`/`assertEquals`/`assertThrows`) | Sonstiges |
|---|---|---|---|
| `e9e1bc1` (Schritt 6, feat) | **0** | **0** | 32 Testdateien geändert, +1 941/−405 Zeilen; **keine** Datei umbenannt oder verschoben |
| `bd15f19` (Schritt 7, refactor) | **0** | **0** | 17 Testdateien geändert, −27 Zeilen — **alle 27 sind ungenutzte `import`-Anweisungen** (z. B. `java.util.List`, `java.util.Map`, `HttpMethod`, `MockHttpServletRequestBuilder`) |

*Belege:*
`git show e9e1bc1 -- tests/ | grep -E "^-" | grep -cE "@Test|@DisplayName|void [a-z_]+\("` → `0` ·
`git show bd15f19 -- tests/ | grep -E "^-" | grep -cE "@Test|@DisplayName|void [a-z_]+\("` → `0` ·
`git show e9e1bc1 -- tests/ | grep -E "^-" | grep -cE "assertThat|assertEquals|assertThrows"` → `0` ·
`git show bd15f19 -- tests/ | grep -E "^-" | grep -cE "assertThat|assertEquals|assertThrows"` → `0` ·
`git show --stat bd15f19 -- tests/` → 27 Löschungen, alle in `import`-Blöcken.

### 3.2 Die Suite läuft grün

`mvn -B verify` → **BUILD SUCCESS**, 89 + 258 = **347 Tests grün, 0 Fehlschläge, 0 Fehler**.
Nach dem Hinzunahme der 20 neuen Tests (Abschnitt 6) sind es 443 grün und 6 bewusst
fehlschlagende Gap-Tests.

### 3.3 Was dieser Schritt **nicht** verändert hat

Kein Test wurde **abgeschwächt**, **ausgelagert** oder **übersprungen** (`@Disabled`,
`@Ignore`): `grep -rn "@Disabled\|@Ignore" tests/` → **keine** Treffer.
Kein Test wurde **gelöscht** (Datei gelöscht): es wurde in diesem Schritt **keine**
Testdatei entfernt, **eine** hinzugefügt (`tests/unit/security/JwtTestKeys.java`) und
**drei** neue Testklassen angelegt.

---

## 4. Abdeckung der 101 Akzeptanzkriterien

### 4.1 Die geprüfte Menge

| Quelle | Anzahl AK |
|---|---|
| `docs/requirements/traceability_matrix.json` → `acceptance_criteria_index` | **101** |
| `docs/requirements/traceability_matrix.json` → `entries[].acceptance_criteria` (24 Einträge) | **101** (identische Menge) |
| `docs/requirements/stories/US-0xx.json` → `acceptance_criteria` | **101** (identische Menge) |

*Abweichung der Mengen: **keine*** — `in entries not in index: []`, `in index not in entries: []`,
`story ACs not in index: []`, `index ACs not in stories: []`.

> **Hinweis zur Nummerierung:** `.agent/artifacts/traceability-verification.md` spricht von
> „130 AK". Die Matrix enthält **101**. Die Zahl 101 ist die gültige — drei unabhängige
> Quellen (Matrix-Index, Matrix-Einträge, Stories) sind deckungsgleich. Das Artefakt aus
> Schritt 7 ist insoweit **veraltet** (Abweichung A-2).

### 4.2 Das Prüfverfahren

Eine **gefüllte Matrix zählt nicht als Beleg.** Geprüft wurde deshalb gegen die
**Wirklichkeit**:

1. Existiert jede Testdatei, die die Matrix nennt? → **0 fehlend** (alle Pfade aus
   `entries[].tests` existieren auf der Platte).
2. Steht die AK-ID im **Namen eines Testfalls** in einer dieser Dateien? Geprüft wurde
   gegen `@DisplayName("…")`, den Java-Methodennamen und `it(…)`/`test(…)`/`describe(…)`
   — nicht gegen ein Vorkommen irgendwo im Text.

```
geprüft: 101 AK
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
| FR-012 Verschlüsselung im Ruhezustand | 5 | `PayloadCipherTest`, `PayloadEncryptionAtRestIT` |
| FR-013 Vorverschlüsselte Ereignisse | 3 | `PreEncryptedEventTest`, `PreEncryptedEventIT` |
| FR-020 Signaturverfahren | 5 | `SignatureTest`, `SignatureContractTest`, `SignedDeliveryIT` |
| FR-021 Zustellgarantie | 4 | `AtLeastOnceDeliveryIT`, `DeliveryIdentifiersTest` |
| FR-022 Wiederholung / Backoff | 5 | `RetryDeliveryIT`, `BackoffPolicyTest`, `JitterTest` |
| FR-023 Versuchsgrenze / Tote Zustellung | 4 | `DeadLetterIT`, `AttemptLimitIT`, `AttemptLimitTest` |
| FR-024 Erneutes Auslösen | 5 | `RedeliveryIT`, `ForeignResourceIsolationIT` |
| FR-025 Ablehnungssignal | 4 | `RejectionContractTest`, `RejectionDeliveryIT` |
| FR-030 Zustellprotokoll | 8 | `DeliveryProtocolIT`, `AlwaysPresentFieldsContractTest`, `protocol.spec.ts` |
| FR-031 Totenliste / Aufbewahrung | 9 | `DeadDeliveryListIT`, `RetentionEnforcementIT`, `AlwaysPresentFieldsIT` |
| FR-032 Kennzahlen | 4 | `EndpointMetricsIT`, `AlwaysPresentFieldsContractTest` |
| FR-033 Endpunkt-Limit | 4 | `RateLimitedDeliveryIT`, `RateLimiterTest` |
| NFR-001 Nutzlastgrenze | 6 | `EventIntakeTest`, `EventIntakeIT`, `EventIntakePerformanceIT` |
| NFR-002 Antwortzeit | 6 | `EventIntakePerformanceIT`, `EventIntakeLoadTest` |
| NFR-003 Mandantentrennung auf DB-Ebene | 4 | `RowLevelSecurityIT`, `TenantIsolationIT`, `SchemaMirrorIT` |
| NFR-004 Verschlüsselung im Ruhezustand | 5 | wie FR-012 |
| NFR-005 Barrierefreiheit | 4 | `a11y.test.tsx`, `a11y.spec.ts` |
| NFR-006 Betrieb (Container, Umgebungen) | 5 | `ContainerStartupIT`, `HorizontalScalingIT`, `TenantInstanceIT`, `EnvironmentSeparationIT`, `health.spec.ts` |

### 4.4 Lücken der Matrix (nicht der Abdeckung)

| Pfad | Art | Bewertung |
|---|---|---|
| `docker-compose.yml` | **Codepfad** der Matrix für NFR-006 existiert nicht | **echte Lücke** — bereits als **S-22** geführt; gehört zu **Schritt 9** (lokale Entwicklung). **Kein** Test verlangt die Datei; `EnvironmentSeparationIT` prüft Konfigurationen und den Bereitstellungs-Deskriptor, nicht Compose. |
| `src/main/java/com/echobox/crypto/SecretStore.java`, `delivery/PayloadSource.java`, `delivery/RetryScheduler.java`, `tenant/TenantConnectionInitializer.java`, `config/TenantInstanceConfiguration.java` | Codepfade der Matrix existieren nicht | **keine Lücke** — die Verantwortung ist an die Nachbarbausteine geflossen (`PayloadCipher`, `DeliveryDispatcher`, `TenantTransactionRunner`, `EchoBoxProperties`). Die Matrix verweist auf historische Namen; **Dokumentationspflege** für Schritt 1/2. |

**Es gibt keine AK-Lücke.** Jede der 101 AKs hat mindestens einen existierenden,
benannten Testfall.

---

## 5. Testabdeckung des Codes (Coverage)

### 5.1 Befund: die Schwelle ist derzeit **nicht messbar**

| Nachweis | Ergebnis |
|---|---|
| `grep -rn "jacoco" pom.xml` | **0 Treffer** |
| `grep -rn "jacoco" --include=*.xml --include=*.yml .` (ohne `node_modules`) | **0 Treffer** |
| `ls coverage/` | **Verzeichnis existiert nicht** |
| `coverage/coverage-summary.json` (aus dem Orchestrator-Prompt gefordert) | **existiert nicht** |
| `mvn -B verify` | erzeugt **keinen** Coverage-Bericht |

**Es gibt keinen Coverage-Plugin-Eintrag in `pom.xml`** — weder JaCoCo noch ein
anderes Werkzeug. Die im Schritt geforderte Schwelle von **80 %** kann derzeit
weder maschinell geprüft noch als Zahl berichtet werden.

> **Warum das ein Befund ist und kein Detail:** `AGENTS.md` und der Orchestrator-Prompt
> nennen „Quality Gates (z. B. Min. 80 % Testabdeckung)" als **Gate** dieses Schritts.
> Ein Gate, das nicht messbar ist, ist kein Gate — es ist eine ungeprüfte Zusage.
> Die **Schwelle 80 %** selbst kann ich nicht als „erfüllt" oder „verletzt" melden;
> gemeldet werden kann nur: **nicht messbar**.

### 5.2 Nachmessung ohne Änderung am Build

Weil `pom.xml` in diesem Schritt **nicht** geschrieben werden darf (Folder-Ownership:
`src/`, `tests/`, `docs/qa/`, `.agent/artifacts/`), wurde die Coverage **über den
Plugin-Aufruf** gemessen, ohne die Build-Datei anzufassen:

```
mvn -B org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent verify \
      -Dmaven.test.failure.ignore=true \
      org.jacoco:jacoco-maven-plugin:0.8.12:report
```

Ergebnis: siehe **Abschnitt 5.3**.

### 5.3 Die gemessenen Zahlen

```
mvn -B org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent verify \
      -Dmaven.test.failure.ignore=true \
      org.jacoco:jacoco-maven-plugin:0.8.12:report
→ [INFO] Analyzed bundle 'echobox' with 133 classes
→ Bericht: target/site/jacoco/index.html · target/site/jacoco/jacoco.csv
```

| Metrik | gedeckt | gesamt | **Abdeckung** | Schwelle 80 % |
|---|---|---|---|---|
| **Zeilen** | 2 084 | 2 440 | **85,4 %** | **erfüllt** ✅ |
| Anweisungen (Instruction) | 9 889 | 11 745 | 84,2 % | erfüllt ✅ |
| Zweige (Branch) | 537 | 871 | 61,7 % | **nicht** erfüllt ⚠️ |

**Die geforderte Schwelle von 80 % Zeilenabdeckung ist mit 85,4 % erfüllt** — die
Messung ist mit dem obigen Kommando **reproduzierbar**, ohne dass `pom.xml`
angefasst wurde.

Abdeckung je Paket (Zeilen):

| Paket | Abdeckung | Paket | Abdeckung |
|---|---|---|---|
| `com.echobox.event` | 96,3 % | `com.echobox.endpoint` | 86,9 % |
| `com.echobox.config` | 96,0 % | `com.echobox.tenant` | 86,7 % |
| `com.echobox.protocol.api` | 95,5 % | `com.echobox.endpoint.api` | 68,5 % |
| `com.echobox.protocol` | 92,7 % | `com.echobox.operations` | 64,5 % |
| `com.echobox.delivery` | 91,1 % | `com.echobox.delivery.api` | 63,6 % |
| `com.echobox.event.api` | 78,8 % | `com.echobox.common` | 56,8 % |
| `com.echobox.crypto` | 72,1 % | `com.echobox` (Startklasse) | 33,3 % |

**Die niedrigsten Pakete sind die API-Records und die `common`-Hilfsklassen** —
DTO-Records (Konstruktoren, `equals`/`hashCode`, `toString`) und Fehlerklassen werden
nur teilweise ausgeführt. Das ist **kein** Sicherheitsrisiko, aber ein Hinweis für die
Teststrategie (Schritt 9).

> **Wichtig:** Diese Zahlen sind eine **Momentaufnahme**, **kein Gate**. Der Build
> erzeugt den Bericht **nicht** und **prüft** die Schwelle **nicht** — die 85,4 % können
> morgen 70 % sein, ohne dass irgendetwas anschlägt. Genau das ist **A-1**.

---

## 6. Die sechs neuen Tests: Abwehr (grün) und Beleg der Lücke (fehlschlagend)

### 6.1 Neue Abwehrtests — **alle grün** (14)

| Test | Datei | Beweist |
|---|---|---|
| `S-HARD-1` … `S-HARD-7` | `tests/integration/security/SecurityHardeningIT.java` | 401 ohne Token (nicht 403/500) · Security-Header (`nosniff`, `DENY`, `Cache-Control: no-store`) · 1-MiB-Nutzlastgrenze → 413 · 2-MiB-Body-Grenze (mit `Content-Length`) → 413 · keine internen Details in der Fehlerantwort · Mandantenparameter → 422 · nur die Health-Routen ohne Token erreichbar |
| `S-HART-1` … `S-HART-7` | `tests/unit/security/JwtDecoderHardeningTest.java` | Der **echte** Decoder der Produktion: gültiges RS256-Token wird angenommen · `alg: none` wird abgelehnt · HS256 (Algorithmus-Verwirrung) wird abgelehnt · abgelaufenes Token wird abgelehnt · fremder Schlüssel wird abgelehnt · ohne IdP wird jedes Token abgelehnt · die JWKS-Adresse kommt aus der Konfiguration |

> **Diese beiden Dateien schließen eine echte Lücke der bisherigen Testabdeckung:**
> die Integrationstests benutzen `jwt()` aus `spring-security-test`
> (`tests/integration/support/Auth.java:46-51`) — der Post-Processor setzt eine
> **fertige Identität** und umgeht damit **den Decoder**. Vor diesem Schritt war
> **kein einziger Test** für Signaturprüfung, Algorithmus und Ablauf vorhanden.

### 6.2 Die Gap-Tests — **schlagen fehl** (6) und sind der **Beleg** der Lücke

| Test | Datei | Befund | Fehlschlagmeldung (Beleg) |
|---|---|---|---|
| `LUECKE (Befund S-20)` | `tests/integration/security/SecurityGapIT.java` | S-20 (High) | `expected: 413 but was: 202` — der 2-MiB-chunked-Body wurde **akzeptiert** |
| `LUECKE (Befund S-24)` | `tests/integration/security/SecurityGapIT.java` | S-24 (Medium) | `Expecting value to be true but was false` — nach 300 Anfragen **nie** eine 429 |
| `LUECKE (Befund S-21)` ×2 | `tests/unit/security/SecurityGapTest.java` | S-21 (Medium) | `Expecting code to raise a throwable` — fremde Zielgruppe bzw. fehlende Ablaufzeit werden **akzeptiert** |
| `LUECKE (Befund S-22)` | `tests/unit/security/SecurityGapTest.java` | S-22 (Medium) | `Expecting code to raise a throwable` — ein 16-Byte-Schlüssel wird **akzeptiert** |
| `LUECKE (Befund S-23)` | `tests/unit/security/SecurityGapTest.java` | S-23 (Medium) | `Expecting actual: 5242880 to be less than or equal to: 1048576` — die Antwort wird **vollständig** gelesen |

**Jeder dieser Tests schlägt genau an der Lücke fehl** — nicht an einem Randfall
daneben: die Fehlermeldung benennt jeweils die verletzte Eigenschaft.

**Strukturierte Fehlerbeschreibungen** (Pfad, Zeile, Ursache, Nachweis, Empfehlung,
Owner) liegen unter `docs/qa/bugs/`:

| Datei | Befund | Schwere |
|---|---|---|
| `docs/qa/bugs/BUG-S-19.json` | Abhängigkeiten — 96 Advisories, 3 erreichbar | High |
| `docs/qa/bugs/BUG-S-20.json` | Request-Body ohne Grenze bei Chunked-Encoding | High |
| `docs/qa/bugs/BUG-S-21.json` | JWT-Audience / `exp` werden nicht geprüft | Medium |
| `docs/qa/bugs/BUG-S-22.json` | Schlüssellänge wird nicht geprüft (AES-128-Downgrade) | Medium |
| `docs/qa/bugs/BUG-S-23.json` | Antwort-Body des Empfängers ohne Grenze | Medium |
| `docs/qa/bugs/BUG-S-24.json` | Zugesagte API-Ratenbegrenzung (429) fehlt | Medium |
| `docs/qa/bugs/BUG-B-17.json` | Übergangstabelle des Domänenmodells (A-6) | Medium |

> **Bewusst roter Build.** Die Gap-Tests sind **aktive** Tests; sie schlagen fehl, bis
> der Rücksprung zu Schritt 6/7 die Lücken schließt. Damit ist der Build ab diesem
> Commit rot — das ist die **Absicht**: die Lücken sind so lange sichtbar, wie sie
> bestehen. Weil Surefire vor Failsafe läuft, bricht ein **einfaches** `mvn -B verify`
> nach der Unit-Ebene ab; für den **vollständigen** Lauf (alle 449 Tests) ist
> `-Dmaven.test.failure.ignore=true` zu verwenden (so wurde in Abschnitt 2 ausgeführt).
> **Nach dem Rücksprung** sind die sechs Tests die **Abnahme** der Behebung: sie
> werden grün, ohne dass eine Zeile geändert werden muss.

---

## 7. Abweichungen

| ID | Abweichung | Auswirkung | Empfehlung / Owner |
|---|---|---|---|
| **A-1** | **Keine Coverage-Messung im Build** (kein Plugin, `pom.xml:121-205`). Die geforderte Schwelle 80 % ist im Build **nicht durchsetzbar** — obwohl sie **aktuell erfüllt** ist (85,4 % Zeilen, Abschnitt 5.3). | Das Quality-Gate „min. 80 % Coverage" ist **ungeprüft** — es kann jederzeit unbemerkt unterschritten werden. | **Schritt 6/9:** JaCoCo mit `check` (Zeilen ≥ 0,80) in `pom.xml`; Bericht nach `coverage/coverage-summary.json`. *(`pom.xml` ist in diesem Schritt nicht schreibbar.)* |
| **A-2** | `.agent/artifacts/traceability-verification.md` nennt „130 AK"; die Matrix enthält **101**. | Die Zahl im Nachzug-Artefakt ist falsch; die Matrix ist richtig. | **Schritt 1** (Artefakt nachziehen) — die Abdeckung selbst ist unberührt (101/101). |
| **A-3** | `mvn -B verify` ist mit den Gap-Tests **rot** (6 Fehlschläge). | Der Build bricht nach der Unit-Ebene ab, wenn kein Flag gesetzt ist. | **gewollt** — siehe 6.2; nach dem Rücksprung grün. |
| **A-4** | `docker-compose.yml` fehlt (Matrix nennt den Pfad für NFR-006). | Lokale Entwicklung ohne Compose; die Testabdeckung ist **nicht** betroffen. | **S-22** — Schritt 9. |
| **A-5** | Fünf Codepfade der Matrix sind **historische Namen** (`SecretStore`, `PayloadSource`, `RetryScheduler`, `TenantConnectionInitializer`, `TenantInstanceConfiguration`); die Verantwortung ist an Nachbarbausteine geflossen. | Keine Abdeckungslücke; die Matrix verweist ins Leere. | **Schritt 1/2** — Matrix nachziehen. |
| **A-6** | **B-17** (Übergangstabelle des Domänenmodells) ist **nicht** erledigt: `DeliveryStateMachine.TRANSITIONS` (`src/main/java/com/echobox/delivery/DeliveryStateMachine.java:27-35`) erlaubt `delayed` **nur** aus `failed`; der Ratenbegrenzungs-Pfad `DeliveryDispatcher.delay()` (`:334-339`) → `onRateLimited(status)` (`:120-125`) setzt **jeden** offenen Zustand auf `delayed` — auch `scheduled`, den ein beanspruchter Lauf trägt (`DeliveryStateMachine.onClaimed`, `:94-99`). `canTransition(SCHEDULED, DELAYED)` liefert **false**, wird im Produktivcode aber **nie** benutzt (nur in Tests). **Das Verhalten selbst ist gewollt und im Code dokumentiert** (`:109-115`: „Hinweis auf eine Lücke in der Übergangstabelle: das Endpunkt-Limit wirkt auch auf den ersten Versuch eines frischen Laufs"); **offen** ist allein die **Tabelle** — `docs/architecture/domain_model.md:188` nennt nur `failed → delayed`. | **Kein** Datenfehler (der DB-CHECK erlaubt `delayed`, `next_attempt_at` wird gesetzt) und **kein** Vertragsdefekt (Vertrag und ADR-010 nennen `delayed` ohne Einschränkung) — aber die **dokumentierte** Übergangstabelle und das Verhalten weichen voneinander ab, und die „einzige Stelle, die Zustände ändert" wird auf dem Ratenbegrenzungs-Pfad umgangen. **B-17 war Schritt 8 zugewiesen.** | **Architektur + Schritt 6/7:** Übergangstabelle in `DeliveryStateMachine.TRANSITIONS` **und** `docs/architecture/domain_model.md` §3.3 um `pending → delayed` und `scheduled → delayed` ergänzen — **oder** `onRateLimited` auf die Tabelle beschränken. (Beide Dateien sind in diesem Schritt nicht schreibbar.) |
| **A-7** | **S-18** (große Klassen): `DeliveryRepository` 867 Zeilen, `EndpointRepository` 566 Zeilen. | **Kein** Sicherheitsbefund und **kein** Gate-Effekt. Bewertung: die Größe stammt überwiegend aus den SQL-Konstanten und den Row-Mappern; die Kohäsion ist gut (je Klasse **ein** Aggregat), es gibt kein „God-Object". Das eigentliche Risiko ist die Vermischung von drei Konsumenten im `DeliveryRepository` (Zustellprotokoll, Totenliste mandantenübergreifend, Kennzahlen). | **Schritt 9 (Wartbarkeit, kein Gate):** `DeliveryRepository` in `DeliveryRunRepository` / `DeliveryAttemptRepository` / `DeliveryMetricsRepository` teilen. |
| **A-8** | Kein Test prüft die **eingehende** Ratenbegrenzung — weil es sie nicht gibt (S-24). | Die Teststrategie folgt dem Vertrag; die Vertragszusage 429 ist unerfüllt. | **Schritt 6** nach der Behebung von S-24. |

---

## 8. Offene Punkte

### 8.1 Die aus den Vorgaengen übernommenen offenen Punkte — Stand in diesem Schritt

| ID | Thema | Owner | Stand nach Schritt 8 |
|---|---|---|---|
| **O-04** | Verschlüsselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz, Gate 2 | **unverändert offen.** Die **Architekturseite** ist umgesetzt und belegt: AES-256-GCM (`crypto/PayloadCipher.java`), Schlüssel außerhalb der Datenbank, keine Klartextanteile in Tabellen/Indizes/Logs (`PlaintextScanner`, `PayloadEncryptionAtRestIT`), nur Metadaten in der Protokollsuche. Die **fachliche** Frage (Muss? durchsuchbar?) entscheidet der Fachbereich. **Zwei technische Randnotizen:** (a) die ADR-003-Vorgabe „je Mandant ein eigener DEK, verschlüsselt mit einem KEK" ist **nicht** umgesetzt — es gibt **einen** globalen Schlüssel (`application.yml:85`) bei vorgesehener `payload_key_version`-Spalte; die Rotation ist damit **nicht** mandantenweise möglich; (b) die **Schlüssellänge wird nicht geprüft** (S-22). |
| **O-05** | AVV bei Fremddaten | Fachbereich + Recht, Gate 2 | **unverändert offen.** Technisch ist der Dienst datenminimierend (Metadaten im Protokoll, verschlüsselte Nutzlast, Löschfristen). Ein AVV ist eine **rechtliche** Zusage und steht außerhalb der technischen Prüfung. |
| **R-01 / E-13** | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich, Gate 2 | **unverändert offen.** Technisch sauber umgesetzt: `purge-delay` ist konfigurierbar (`application.yml:130`), ohne Wert wird mit 0 Tagen gerechnet (`schema.sql:704-709`), die Karenzzeit ist im Vertrag **nicht** festgeschrieben. Der Wert kommt vom Fachbereich. |
| **E-12** | Härtung der mandantenübergreifenden Sicht (eigene Laufzeitrolle?) | Fachbereich + Plattform, Gate 2 | **unverändert offen — und im Audit bestätigt:** die Applikationsrolle kann `app.cross_tenant` setzen und damit die mandantenübergreifende Sicht öffnen; das ist die einzige verbleibende Lücke zwischen RLS und Anwendung (`docs/adr/README.md:96`, `tenant/CrossTenantScope.java:31-32`). Der Schadensweg ist begrenzt: die drei Funktionen liefern **nur** die Vertragsprojektion (kein Klartext, keine Chiffre, keine Secrets) und sind **fail-closed**. |
| **B-17** | Lücke in der Übergangstabelle des Domänenmodells | Architektur | **nicht erledigt** — präzisiert als **A-6**: die Tabelle erlaubt `delayed` nur aus `failed`, der Ratenbegrenzungs-Pfad setzt aber jeden offenen Zustand auf `delayed`. Kein Vertragsdefekt, aber eine Modell-/Implementierungs-Inkonsistenz. |
| **B-18** | Idempotenzschlüssel der Endpunkt-Registrierung | Fachbereich, Gate 2 | **unverändert offen.** `endpoint_event_types` hat den Primärschlüssel `(endpoint_id, event_type)` (`schema.sql:128`); `endpoint_id` ist weltweit eindeutig, deshalb gibt es keine Mehrfachregistrierung desselben Ereignistyps je Endpunkt. Ob ein **je Mandant** eindeutiger Schlüssel gewünscht ist, ist eine **fachliche** Frage — der Vertrag lässt sie offen. |
| **S-18** | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.) | Schritt 8/9 | **bewertet** — siehe **A-7**: kein Sicherheitsbefund, kein Gate-Effekt, Kohäsion gut; die Aufteilung ist eine **Wartbarkeitsfrage** für Schritt 9. |
| **S-22** | `docker-compose.yml` fehlt | Schritt 9 | **unverändert offen** — bestätigt: die Datei existiert nicht, wird von keinem Test verlangt und gehört zu Schritt 9 (lokale Entwicklung). |
| **S-23** | `.gitignore`: `/test-results/` aus der Wurzel | Schritt 9 | **unverändert offen** — bestätigt: `.gitignore` (9 Zeilen) enthält `/src/main/frontend/test-results/`, aber **nicht** `/test-results/` auf der Wurzelsebene. Da Playwright in `src/main/frontend` läuft (`src/main/frontend/playwright.config.ts`) und das Verzeichnis dort bereits ignoriert wird, ist der **praktische** Schaden gering; die Zeile gehört trotzdem in die Wurzel. **Schritt 9.** |

### 8.2 Neue offene Punkte aus diesem Schritt

| ID | Thema | Owner |
|---|---|---|
| **Q-1** | Terminiert TLS der vorgeschaltete Proxy? Wer setzt HSTS? (Der Container spricht HTTP.) | Schritt 9 / Betrieb |
| **Q-2** | Sind die Branches geschützt? (Aus dem Repository nicht prüfbar.) | Schritt 9 / Betrieb |
| **Q-3** | „Recht auf Löschung": reicht die fristbasierte Purge-Stufe, oder braucht es ein mandantenspezifisches Sofort-Lösch-Kommando? | Fachbereich + Recht, Gate 2 |
| **Q-4** | Soll ein Endpunkt-Ziel in interne Adressbereiche (Link-Local, RFC 1918) verboten werden? | Fachbereich + Plattform, Gate 2 |
| **Q-5** | Restrisiko `jackson-core` (quadratisches Backtracking bei sehr langen Dezimalzahlen) — abhängig vom Jackson-Update in S-19. | Schritt 6 |
| **Q-6** | Bedingungen von CVE-2026-22732 (Spring Security 6.3.4, „HTTP Headers Are not Written Under Some Conditions") — die Anwendung setzt keine eigenen Kopfzeilen; der Wirkungsgrad ist nicht bestimmbar. | Schritt 6 |
| **Q-7** | Schlüsselverwaltung nach ADR-003 §2 (je Mandant ein DEK, verschlüsselt mit einem KEK) — ist der globale Schlüssel die **gewollte** Vereinfachung, oder soll die mandantenweise Rotation nachgezogen werden? (Siehe O-04.) | Architektur + Fachbereich, Gate 2 |

---

## 9. QA-Bewertung und Freigabe-Empfehlung

| Kriterium des Gates | Wert | Erfüllt? |
|---|---|---|
| **QA: 0 Critical** | 0 Critical (kein Fehler in der Anwendung, der Datenverlust, Mandanten-Durchbruch, Secret-Verlust oder RCE verursacht) | **ja** |
| Security: 0 Critical | 0 | **ja** |
| Security: 0 High | **2** (S-19, S-20) | **nein** |
| Alle Tests grün | 443 grün; 6 **bewusst** fehlschlagende Gap-Tests (Beleg der Lücken) | **mit Einschränkung** |
| Keine bekannten kritischen Lücken | 2 High (S-19, S-20) | **nein** |
| Keine Regressionen | keine (0 entfernte Tests/Assertions in Schritt 6/7) | **ja** |
| AK-Abdeckung | 101/101 | **ja** |
| Coverage ≥ 80 % | **85,4 % Zeilen** (2 084/2 440) — **Schwelle erfüllt**, aber **nicht im Build erzwungen** (A-1) | **Wert erfüllt**, Gate **nicht verankert** |

### Freigabe-Empfehlung: **BLOCKIEREN**

* **Für das Deployment (Schritt 9): nein.** Die Gate-Bedingung „Security 0 Critical und
  0 High" ist mit **2 High** nicht erfüllt.
* **Für den Rücksprung: ja, unverzüglich.** Erforderlich sind:
  1. **S-20** (High) — Grenze des Request-Bodies auch bei `Transfer-Encoding: chunked`
     durchsetzen. *Schritt 6/7.*
  2. **S-19** (High) — Abhängigkeiten auf eine Spring-Boot-Version heben, die
     Tomcat ≥ 10.1.55 mitbringt. *Schritt 6/7.*
  3. **S-24** (Medium) — die im **eingefrorenen Vertrag** zugesagte 429-Ratenbegrenzung
     entweder implementieren **oder** den Vertrag in **Schritt 2** ändern. *Schritt 6/7
     (Implementierung) bzw. Schritt 2 (Vertrag).*
  4. **S-21, S-22, S-23** (Medium) — Audience/`exp` prüfen, Schlüssellänge prüfen,
     Antwort-Body begrenzen. *Schritt 6/7.*
  5. **S-25** (Medium) — Secret-Scan, SCA-Scan und OIDC in der CI. *Schritt 9.*
  6. **A-1** — Coverage-Gate (JaCoCo ≥ 80 %) in den Build aufnehmen. *Schritt 6/9.*
  7. **A-6 / B-17** — Übergangstabelle des Domänenmodells angleichen. *Architektur +
     Schritt 6/7.*
* **Die sechs Gap-Tests** sind die **Abnahme** dieser Behebung: sie werden grün, ohne
  dass eine Zeile geändert werden muss.

**O-04, O-05, R-01/E-13, E-12, B-18** sind **fachliche bzw. rechtliche Entscheidungen**
und bleiben dem **Gate 2** vorbehalten — sie sind **keine** technischen Blocker für die
Umsetzung und **keine** Befunde dieses Schritts.

---

*Erzeugt in Schritt 8 (Sicherheit & Compliance). Kanonisches Artefakt:
`.agent/artifacts/test-report.md`.*
