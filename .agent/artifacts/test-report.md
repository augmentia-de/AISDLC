# Testbericht — EchoBox (Schritt 8)

> **Kanonisches Artefakt** des Schritts 8 (Sicherheit & Compliance).
> Der **vollständige** QA-Bericht — Teststand je Ebene, AK-Abdeckung, Regressionsprüfung,
> Coverage, Abweichungen und offene Punkte — steht in
> **[`docs/qa/qa-report.md`](../../docs/qa/qa-report.md)**.
> Der Security-Audit steht in [`docs/qa/security-audit.md`](../../docs/qa/security-audit.md),
> das kanonische Security-Artefakt in [`.agent/artifacts/security-audit.md`](security-audit.md).

---

## 1. Ergebnis in einem Satz

**QA: 0 Critical — die Halbbedingung ist erfüllt; die Suite läuft grün, alle 101
Akzeptanzkriterien sind belegt, keine Regression. Security: 2 High → das Gate
insgesamt ist NICHT erfüllt, Freigabe-Empfehlung: BLOCKIEREN.**

## 2. Teststand — ausgeführt am 2026-10-22

| Ebene | Kommando | Ergebnis |
|---|---|---|
| Java Unit | `mvn -B verify` | **89 grün** (vor den neuen Tests) → mit den neuen: **100 Tests, 4 Fehlschläge** (= die 4 Gap-Tests) |
| Java Integration (PostgreSQL 16.15, Testcontainers) | `mvn -B verify` | **258 grün** → mit den neuen: **267 Tests, 2 Fehlschläge** (= die 2 Gap-Tests) |
| Java Vertrag | `mvn -B -Pcontract-performance verify` | **19 grün** (Signature 6, AlwaysPresentFields 7, Rejection 6) |
| Java Last | dieselbe Ausführung | **2 grün** (`EventIntakeLoadTest`) |
| Frontend Typprüfung | `npx tsc --noEmit` | **Exit 0** |
| Frontend Lint | `npm run lint` | **Exit 0** |
| Frontend Unit | `npx vitest run` | **30 grün** (5 Dateien) |
| Frontend E2E | `npx playwright test` | **31 grün** (Chromium, 15,5 s) |
| Traceability | `python3 .agent/tools/traceability_check.py` | **grün** |
| Schema-Diffs (2) | `diff .agent/knowledge/schema.sql …` | **leer** (beide) |

**Summe: 449 Testfälle — 443 grün, 6 bewusst fehlschlagende Gap-Tests** (Belege der
belegten Sicherheitslücken).

## 3. AK-Abdeckung

**101 von 101 Akzeptanzkriterien (100 %) sind belegt** — jede AK-ID steht im Namen
**eines existierenden Testfalls** einer Datei, die `docs/requirements/traceability_matrix.json`
nennt (Methode: `@DisplayName`, Java-Methodenname, `it(…)`/`test(…)` — **kein** bloßes
Textvorkommen). Lücken der **Matrix** (nicht der Abdeckung): `docker-compose.yml` (S-22,
Schritt 9) und fünf **historische** Codepfad-Namen (Dokumentationspflege).

## 4. Regressionsprüfung

**Keine Regression.** In Schritt 6 (`e9e1bc1`) und Schritt 7 (`bd15f19`) wurde **kein**
Testfall, **keine** `@Test`/`@DisplayName`-Annotation und **keine** Assertion entfernt
(Diff-Analyse: jeweils 0 Treffer). Die 27 gelöschten Zeilen in Schritt 7 sind
**ausschließlich ungenutzte `import`-Anweisungen**.

## 5. Coverage

**85,4 % Zeilen** (2 084 von 2 440) — die geforderte Schwelle von **80 %** ist
**erfüllt**. Gemessen reproduzierbar **ohne** Änderung an `pom.xml`:

```
mvn -B org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent verify \
      -Dmaven.test.failure.ignore=true \
      org.jacoco:jacoco-maven-plugin:0.8.12:report
→ [INFO] Analyzed bundle 'echobox' with 133 classes
```

Anweisungen 84,2 % · Zweige 61,7 %.

**Der Build selbst erzwingt die Schwelle nicht**: `pom.xml` enthält **kein**
Coverage-Plugin (`grep jacoco` → 0 Treffer), `coverage/` existiert nicht,
`coverage/coverage-summary.json` wird nicht erzeugt. Die 85,4 % sind eine
**Momentaufnahme**, kein Gate → **A-1** (Schritt 6/9: JaCoCo mit `check` ≥ 0,80).

## 6. Die 20 neuen Tests

| Art | Datei | Tests | Ergebnis |
|---|---|---|---|
| Abwehr | `tests/integration/security/SecurityHardeningIT.java` | 7 | **grün** |
| Abwehr | `tests/unit/security/JwtDecoderHardeningTest.java` (+ `JwtTestKeys.java`, Testhilfe) | 7 | **grün** |
| Lücke | `tests/unit/security/SecurityGapTest.java` | 4 | **fehlschlagend** (S-21 ×2, S-22, S-23) |
| Lücke | `tests/integration/security/SecurityGapIT.java` | 2 | **fehlschlagend** (S-20, S-24) |

> **Der Build ist ab diesem Commit bewusst rot.** Die Gap-Tests schlagen fehl, bis der
> Rücksprung zu Schritt 6/7 die Lücken schließt — sie sind die **Abnahme** der Behebung.
> Für den vollständigen Lauf: `mvn -B verify -Dmaven.test.failure.ignore=true`.

**Neu abgedeckt** (vorher von **keinem** Test berührt): die **echte** JWT-Decoder-Kette —
die Integrationstests benutzen `jwt()` aus `spring-security-test` und umgehen damit
Signaturprüfung, Algorithmus und Ablauf.

## 7. Abweichungen und offene Punkte

**Abweichungen:** A-1 Coverage nicht messbar · A-2 `traceability-verification.md` nennt 130 AK statt 101 · A-3 Build rot durch die Gap-Tests (gewollt) · A-4 `docker-compose.yml` fehlt (S-22) · A-5 fünf historische Codepfade in der Matrix · A-6 **B-17** Übergangstabelle des Domänenmodells (nicht erledigt) · A-7 **S-18** große Repositories (bewertet: kein Befund, Wartbarkeit) · A-8 kein Test für die eingehende Ratenbegrenzung (weil es sie nicht gibt).

**Offene Punkte (Gate 2, fachlich/rechtlich):** O-04 Verschlüsselung im Ruhezustand ·
O-05 AVV · R-01/E-13 Karenzzeit der Aufbewahrung · E-12 Härtung der mandantenübergreifenden
Sicht · B-18 Idempotenzschlüssel der Endpunkt-Registrierung.
**Offene Punkte (technisch):** Q-1 TLS/HSTS · Q-2 Branch-Schutz · Q-3 Recht auf Löschung ·
Q-4 interne Adressbereiche (SSRF) · Q-5 `jackson-core`-Restrisiko · Q-6 CVE-2026-22732 ·
Q-7 DEK/KEK je Mandant (ADR-003 §2).

## 8. Freigabe-Empfehlung

| Kriterium | Wert | Erfüllt? |
|---|---|---|
| QA: 0 Critical | 0 | **ja** |
| Security: 0 Critical | 0 | **ja** |
| Security: 0 High | **2** (S-19, S-20) | **nein** |
| Alle Tests grün | 443 grün; 6 bewusst fehlschlagende Gap-Tests | mit Einschränkung |
| Keine Regressionen | keine | **ja** |
| AK-Abdeckung | 101/101 | **ja** |
| Coverage ≥ 80 % | **85,4 % Zeilen** — Wert erfüllt, Gate nicht im Build verankert (A-1) | Wert ja, Gate nein |

### **BLOCKIEREN** — kein Deployment (Schritt 9). Rücksprung zu Schritt 6/7 (S-19, S-20,
S-21, S-22, S-23), S-24 zusätzlich zu Schritt 2 (Vertrag), A-1/A-6 mit, S-25 in Schritt 9.

---

*Schritt 8 (Sicherheit & Compliance) · Agent: `aisdlc-qa` · 2026-10-22.*
