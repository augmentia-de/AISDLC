# Traceability-Verifikation — Schritt 7 (Nachzug zum Nachweis)

> Agent: `aisdlc-execution` · Stand: 2026-10-22
> Zweck: der **Beleg** fuer den Nachzug — die **tatsaechliche** Kette
> Anforderung → Akzeptanzkriterium → Testdatei → Testfall → Codepfad,
> geprueft gegen die **existierenden Dateien** des Repositories.
>
> **Abgrenzung:** die Traceability-Matrix selbst liegt in
> `docs/requirements/traceability_matrix.json` und gehoert **Schritt 1**. Dieser
> Bericht **schreibt sie nicht** — er prueft sie gegen die Wirklichkeit und
> benennt, was die Matrix nachziehen muss.

---

## 1. Wie geprueft wurde

Drei Pruefungen, alle gegen das Repository (nicht gegen die Matrix):

| # | Pruefung | Werkzeug | Ergebnis |
|---|---|---|---|
| P1 | **Existiert jede Testdatei, die die Matrix nennt?** | Dateiexistenz ueber alle 24 Matrix-Eintraege | **0 fehlend** — alle 24 × gelistete Testdateien existieren |
| P2 | **Existiert jeder Codepfad, den die Matrix nennt?** | Dateiexistenz ueber alle 24 Matrix-Eintraege | **6 fehlen** (siehe Abschnitt 4) |
| P3 | **Nennt jedes Akzeptanzkriterium einen existierenden Testfall?** | Fuer alle **130** AK: die AK-ID muss in einer existierenden Testdatei **im Namen eines Testfalls** stehen (`@DisplayName`, Methodenname, `it(…)`/`test(…)`) | **130 von 130** — kein AK ohne Testfall |

Zusaetzlich: `python3 .agent/tools/traceability_check.py` → **gruen**
(„Keine Luecken — die Kette Anforderung → Story → Feature → Test → Code ist
vollstaendig.“).

Der Check ist **statisch** und damit **eine** Form des Nachweises: er belegt,
dass ein Testfall **existiert und das AK nennt** — nicht, dass er gruen ist.
Der **Ausfuehrungsnachweis** steht in `.agent/artifacts/refactoring-report.md`
§6 (alle Ebenen gruen).

---

## 2. Das Ergebnis in einem Satz

**Jedes der 130 Akzeptanzkriterien der 24 Anforderungen hat mindestens einen
existierenden Testfall, der es im Namen nennt, und zu jeder Anforderung gibt es
existierende Codepfade.** Die Luecken betreffen nicht die **Abdeckung**,
sondern die **Matrix**: sechs geplante Codepfade aus Schritt 1 gibt es nicht
mehr, und zwei Testdateien, die AK belegen, fehlen in der Matrix.

---

## 3. Die Kette je Anforderung

Legende: **†** = die Testdatei belegt das AK, steht aber **nicht** in der
Matrix (Nachzug fuer Schritt 1).

| Anforderung | AK | Testdateien (existierend, benennen die AK) | Codepfad existiert | geplanter Pfad ohne Datei |
|---|---|---|---|---|
| **FR-001** Endpunkt registrieren | 9 | `tests/e2e/endpoints.spec.ts`<br>`tests/integration/endpoint/EndpointRegistrationIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` **†**<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†**<br>`tests/unit/endpoint/EndpointRegistrationTest.java`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | 6 von 6 | — |
| **FR-002** Pause / Reaktivieren | 9 | `tests/e2e/endpoints.spec.ts`<br>`tests/integration/endpoint/EndpointPauseIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` **†**<br>`tests/unit/endpoint/EndpointPauseTest.java`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | 6 von 6 | — |
| **FR-003** Secret-Rotation | 8 | `tests/e2e/endpoints.spec.ts`<br>`tests/integration/endpoint/SecretRotationIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` **†**<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†**<br>`tests/unit/endpoint/SecretRotationTest.java`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | 5 von 6 | `crypto/SecretStore.java` |
| **FR-004** Mandantentrennung der Sicht | 4 | `tests/contract/AlwaysPresentFieldsContractTest.java` **†**<br>`tests/integration/endpoint/EndpointVisibilityIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java` **†**<br>`tests/integration/security/ForeignResourceIsolationIT.java` **†**<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†** | 3 von 3 | — |
| **FR-010** Einlieferung | 6 | `tests/integration/event/EventIntakeIT.java`<br>`tests/integration/event/EventIntakePerformanceIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†**<br>`tests/performance/EventIntakeLoadTest.java` **†**<br>`tests/unit/event/EventIntakeTest.java` | 4 von 4 | — |
| **FR-011** Idempotenz | 4 | `tests/integration/event/EventIdempotencyIT.java`<br>`tests/unit/event/EventIdempotencyTest.java` | 2 von 2 | — |
| **FR-012** Verschluesselung im Ruhezustand | 5 | `tests/integration/event/PayloadEncryptionAtRestIT.java`<br>`tests/integration/protocol/DeliveryProtocolIT.java` **†**<br>`tests/unit/crypto/PayloadCipherTest.java` | 2 von 2 | — |
| **FR-013** Vorverschluesselte Ereignisse | 3 | `tests/integration/event/PreEncryptedEventIT.java`<br>`tests/unit/event/PreEncryptedEventTest.java` | 2 von 3 | `delivery/PayloadSource.java` |
| **FR-020** Signaturverfahren | 5 | `tests/contract/SignatureContractTest.java` **†**<br>`tests/integration/delivery/SignedDeliveryIT.java`<br>`tests/unit/crypto/SignatureTest.java`<br>`tests/unit/delivery/DeliveryIdentifiersTest.java` **†** | 2 von 2 | — |
| **FR-021** Zustellgarantie | 4 | `tests/integration/delivery/AtLeastOnceDeliveryIT.java`<br>`tests/unit/delivery/DeliveryIdentifiersTest.java`<br>`tests/unit/delivery/PermanentRejectionTest.java` **†** | 2 von 2 | — |
| **FR-022** Wiederholung / Backoff | 5 | `tests/integration/delivery/RetryDeliveryIT.java`<br>`tests/unit/delivery/BackoffPolicyTest.java`<br>`tests/unit/delivery/JitterTest.java` | 2 von 3 | `delivery/RetryScheduler.java` |
| **FR-023** Versuchsgrenze / Tote Zustellung | 4 | `tests/integration/delivery/AttemptLimitIT.java` **†**<br>`tests/integration/delivery/DeadLetterIT.java`<br>`tests/unit/delivery/AttemptLimitTest.java` | 2 von 2 | — |
| **FR-024** Erneutes Ausloesen | 5 | `tests/integration/delivery/RedeliveryIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` **†**<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†** | 2 von 2 | — |
| **FR-025** Ablehnungssignal | 4 | `tests/contract/RejectionContractTest.java` **†**<br>`tests/integration/delivery/RejectionDeliveryIT.java`<br>`tests/unit/delivery/PermanentRejectionTest.java` **†** | 2 von 2 | — |
| **FR-030** Zustellprotokoll | 8 | `tests/contract/AlwaysPresentFieldsContractTest.java` **†**<br>`tests/e2e/dead-deliveries.spec.ts`<br>`tests/e2e/protocol.spec.ts`<br>`tests/integration/protocol/DeliveryProtocolIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java` **†**<br>`tests/unit/frontend/DeadDeliveryPage.test.tsx` **†**<br>`tests/unit/frontend/DeliveryProtocolPage.test.tsx` | 5 von 5 | — |
| **FR-031** Totenliste / Aufbewahrung | 9 | `tests/contract/AlwaysPresentFieldsContractTest.java` **†**<br>`tests/e2e/dead-deliveries.spec.ts`<br>`tests/e2e/protocol.spec.ts`<br>`tests/integration/protocol/DeadDeliveryListIT.java`<br>`tests/integration/protocol/RetentionEnforcementIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java` **†**<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†**<br>`tests/unit/frontend/DeadDeliveryPage.test.tsx` **†**<br>`tests/unit/frontend/DeliveryProtocolPage.test.tsx` | 6 von 6 | — |
| **FR-032** Kennzahlen | 4 | `tests/contract/AlwaysPresentFieldsContractTest.java` **†**<br>`tests/integration/protocol/EndpointMetricsIT.java` | 2 von 2 | — |
| **FR-033** Endpunkt-Limit | 4 | `tests/contract/AlwaysPresentFieldsContractTest.java` **†**<br>`tests/integration/delivery/RateLimitedDeliveryIT.java`<br>`tests/integration/protocol/EndpointMetricsIT.java` **†**<br>`tests/integration/security/AlwaysPresentFieldsIT.java` **†**<br>`tests/unit/delivery/RateLimiterTest.java` | 2 von 2 | — |
| **NFR-001** Nutzlastgrenze | 6 | `tests/integration/event/EventIntakeIT.java`<br>`tests/integration/event/EventIntakePerformanceIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†**<br>`tests/performance/EventIntakeLoadTest.java` **†**<br>`tests/unit/event/EventIntakeTest.java` | 4 von 4 | — |
| **NFR-002** Antwortzeit | 6 | wie NFR-001 | 4 von 4 | — |
| **NFR-003** Mandantentrennung auf DB-Ebene | 4 | `tests/integration/schema/SchemaMirrorIT.java` **†**<br>`tests/integration/security/ForeignResourceIsolationIT.java` **†**<br>`tests/integration/security/RoleCapabilityMatrixIT.java` **†**<br>`tests/integration/tenant/RowLevelSecurityIT.java`<br>`tests/integration/tenant/TenantIsolationIT.java` | 2 von 3 | `tenant/TenantConnectionInitializer.java` |
| **NFR-004** Verschluesselung im Ruhezustand | 5 | wie FR-012 | 2 von 2 | — |
| **NFR-005** Barrierefreiheit | 4 | `tests/e2e/a11y.spec.ts`<br>`tests/unit/frontend/a11y.test.tsx` | 2 von 2 | — |
| **NFR-006** Betrieb (Container, Umgebungen) | 5 | `tests/e2e/health.spec.ts`<br>`tests/integration/deployment/ContainerStartupIT.java`<br>`tests/integration/deployment/EnvironmentSeparationIT.java` **†**<br>`tests/integration/deployment/HorizontalScalingIT.java`<br>`tests/integration/deployment/TenantInstanceIT.java` **†** | 1 von 3 | `docker-compose.yml`<br>`config/TenantInstanceConfiguration.java` |

---

## 4. Die Luecken — und was tatsaechlich da ist

### 4.1 Sechs geplante Codepfade aus Schritt 1 gibt es nicht (P2)

Die Matrix nennt sechs Pfade, die **nicht** angelegt wurden. In fuenf Faellen
ist die Verantwortung an einen anderen Baustein geflossen (der Vertrag und
das Schema sind unveraendert — es ist eine **Dokumentations**-Luecke, keine
Implementierungsluecke). In einem Fall fehlt die Datei wirklich.

| Geplanter Pfad (Schritt 1) | Was heute da ist | Bewertung |
|---|---|---|
| `crypto/SecretStore.java` | `crypto/PayloadCipher.java` (Chiffre des Secrets), `endpoint/EndpointRepository.java` (Spalten `secret_ciphertext`/`secret_nonce`), `delivery/DeliveryDispatcher.secret(…)` (Entschluesselung unmittelbar vor der Signatur) | **geflossen** — der Schritt-1-Verweis ist stale |
| `delivery/PayloadSource.java` | `delivery/DeliveryDispatcher.payloadBytes(…)` — entschluesselt oder reicht die Chiffre unveraendert weiter (FR-013) | **geflossen** |
| `delivery/RetryScheduler.java` | `delivery/DeliveryDispatcher.record(…)` setzt `next_attempt_at` ueber `delivery/BackoffPolicy.java` | **geflossen** |
| `tenant/TenantConnectionInitializer.java` | `tenant/TenantTransactionRunner.java` — `setTenant(…)` **innerhalb** der lesenden Transaktion (ADR-002 §3). `docs/architecture/folder-structure.md` §2.6 nennt den Namen selbst **historisch** | **geflossen** (der Name war von Anfang an als historisch markiert) |
| `config/TenantInstanceConfiguration.java` | `config/EchoBoxProperties.java` (`Tenant.id`) + `delivery/DeliveryWorker.java` (`buildInstanceId(…)`) | **geflossen** |
| `docker-compose.yml` | — **die Datei existiert nicht** | **echte Luecke**: in `docs/architecture/folder-structure.md` §1 und `.agent/knowledge/stack.md` („plus docker-compose fuer die lokale Entwicklung") vorgesehen. **Kein Test verlangt sie** (`EnvironmentSeparationIT` prueft Konfigurationen und den Bereitstellungsdeskriptor, nicht Compose). Befund fuer Schritt 9 |

### 4.2 Zwei Testdateien belegen AK, stehen aber nicht in der Matrix

| AK | Testdatei | Testfall |
|---|---|---|
| **AC-023-2** (Mandanteninstanz) | `tests/integration/deployment/TenantInstanceIT.java` | `the_bound_instance_serves_its_own_tenant()`, `the_bound_instance_does_not_serve_another_tenant()` |
| **AC-023-5** (getrennte Umgebungen) | `tests/integration/deployment/EnvironmentSeparationIT.java` | `staging_has_its_own_configuration()`, `no_configuration_contains_a_hardcoded_secret()`, `the_deployment_provides_staging_and_production_separately()` |

Fuer **beide** AK nennt die Matrix (NFR-006) nur `ContainerStartupIT`,
`HorizontalScalingIT` und `health.spec.ts` — die beiden Dateien, die die AK
tatsaechlich belegen, fehlen in der Liste.

### 4.3 Weitere Testdateien, die AK belegen, ohne in der Matrix gelistet zu sein

Die mit **†** markierten Dateien (im ganzen Bericht **47** Vorkommen ueber
**20** Anforderungen) sind **keine** Luecken der Abdeckung — sie belegen AK, die
die Matrix einem anderen Test zurechnet. Sie sind ein **Pflegepunkt** fuer
Schritt 1: die Matrix sollte jede Datei nennen, die ein AK des Eintrags
belegt. Betroffen sind vor allem die drei querschnittlichen Dateien:

- `tests/integration/security/RoleCapabilityMatrixIT.java` (acht Rollenrechte
  ohne eigenes AK — V-52, der Testmatrix zugeordnet),
- `tests/integration/security/ForeignResourceIsolationIT.java`,
- `tests/integration/security/AlwaysPresentFieldsIT.java` und
  `tests/contract/AlwaysPresentFieldsContractTest.java` (V-46).

---

## 5. Was dieser Bericht **nicht** behauptet

- Er schreibt die Matrix **nicht** — `docs/requirements/` gehoert Schritt 1.
- Er belegt **Existenz und Benennung**, nicht die **Ausfuehrung**. Der
  Ausfuehrungsnachweis (alle Ebenen gruen) steht in
  `.agent/artifacts/refactoring-report.md` §6.
- Er trifft **keine** Aussage ueber die fachliche Richtigkeit eines Testfalls
  — nur darueber, dass es ihn gibt und welches AK er nennt.
- Er aendert **nichts** am Vertrag, am Schema oder an den ADRs.
