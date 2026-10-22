# Testbericht — EchoBox (Schritt 8)

> **Kanonisches Artefakt** des Schritts 8 (Sicherheit & Compliance).
> Der **vollständige** QA-Bericht — Teststand je Ebene, AK-Abdeckung, Regressionsprüfung,
> Coverage, Abweichungen und offene Punkte — steht in
> **[`docs/qa/qa-report.md`](../../docs/qa/qa-report.md)**.
> Der Security-Audit steht in [`docs/qa/security-audit.md`](../../docs/qa/security-audit.md),
> das kanonische Security-Artefakt in [`.agent/artifacts/security-audit.md`](security-audit.md).

---

## 1. Ergebnis in einem Satz

**QA: 0 Critical — die Halbbedingung ist erfüllt; alle 101 Akzeptanzkriterien sind
belegt, keine Regression, die Coverage-Schwelle ist im Build **erzwungen**.
Security: 1 High (neu) → das Gate insgesamt ist NICHT erfüllt, Freigabe-Empfehlung:
BLOCKIEREN.**

> **Runde 2.** Die Nacharbeit (`d135cda`, Doku `28396e0`) hat S-19…S-27 und A-1 behoben:
> die sechs Gap-Tests der ersten Runde sind **alle grün**, ohne dass ein Test geändert
> wurde. Diese Runde hat **vier neue** Gap-Tests angelegt — für **S-28** (High),
> **S-29**, **S-30** und **S-31**.

## 2. Teststand — ausgeführt am 2026-10-22

| Ebene | Kommando | Ergebnis |
|---|---|---|
| Java Unit | `mvn -B -o verify -Dmaven.test.failure.ignore=true` | **120 Tests, 118 grün, 2 rot** (= die 2 neuen Gap-Tests) |
| Java Integration (PostgreSQL 16.15, Testcontainers) | dieselbe Ausführung | **283 Tests, 281 grün, 2 rot** (= die 2 neuen Gap-Tests) |
| Coverage-Gate | dieselbe Ausführung | `jacoco:check` → **„All coverage checks have been met."** — **86,22 % Zeilen** (Gate: 80 %) |
| Java Vertrag | `mvn -B -Pcontract-performance verify` | **19 grün** (Signature 6, AlwaysPresentFields 7, Rejection 6) |
| Java Last | dieselbe Ausführung | **2 grün** (`EventIntakeLoadTest`) |
| Frontend Typprüfung | `npx tsc --noEmit` | **Exit 0** („TypeScript: No errors found") |
| Frontend Lint | `npm run lint` | **Exit 0** („ESLint: No issues found") |
| Frontend Unit | `npx vitest run` | **30 grün** (5 Dateien) |
| Frontend E2E | `npx playwright test` | **31 grün** (Chromium, 12,5 s) |
| Frontend Build | `npm run build` | **Exit 0** |
| Traceability | `python3 .agent/tools/traceability_check.py` | **grün** |
| Schema-Diffs (2) | `diff .agent/knowledge/schema.sql …` | **leer** (beide) |
| Secret-Scan (Code + Historie) | `grep …` / `git log -p … \| grep …` | 2 Test-Fixtures / **0** Treffer |
| SCA Java / Node | OSV `querybatch` über `mvn dependency:tree` / `npm audit --json` | **20** Advisories (Runde 1: 96) / 10 (alle `devDependencies`) |

**Summe: 485 Testfälle — 481 grün, 4 bewusst fehlschlagende Gap-Tests** (Belege der
belegten Lücken S-28, S-29, S-30, S-31).

## 3. AK-Abdeckung

**101 von 101 Akzeptanzkriterien (100 %) sind belegt** — jede AK-ID steht im Namen
**eines existierenden Testfalls** einer Datei, die `docs/requirements/traceability_matrix.json`
nennt (Methode: `@DisplayName`, `@ParameterizedTest(name=…)`, Java-Methodenname,
`it(…)`/`test(…)`/`describe(…)` — **kein** bloßes Textvorkommen). Alle 60
matrix-genannten Testdateien existieren. **Eigenständig nachgefahren**, nicht aus dem
Matrix-Eintrag abgeleitet.

Lücken der **Matrix** (nicht der Abdeckung): `docker-compose.yml` (Schritt 9) und fünf
**historische** Codepfad-Namen (Dokumentationspflege, Schritt 1/2).

## 4. Regressionsprüfung

**Keine Regression.** In der Nacharbeit (`d135cda`) und im Doku-Nachzug (`28396e0`) wurde
**kein** Testfall, **keine** `@Test`/`@DisplayName`-Annotation und **keine** Assertion
entfernt (Diff-Analyse: jeweils 0 Treffer; `28396e0` berührt `tests/` gar nicht). Kein
Test wurde abgeschwächt: `grep -rn "@Disabled\|@Ignore" tests/` → **keine** Treffer.

## 5. Coverage

**86,22 % Zeilen** (2 228 von 2 584) — die geforderte Schwelle von **80 %** ist erfüllt
**und im Build erzwungen**:

```
mvn -B verify
→ [INFO] --- jacoco:0.8.13:check (default) @ echobox ---
→ [INFO] All coverage checks have been met.
```

Anweisungen 84,99 % · Zweige 62,73 % · Methoden 90,17 %.
Das Gate (`BUNDLE` / `LINE` / `COVEREDRATIO` / `minimum 0.80`, Phase `verify`) sitzt in
`pom.xml:224-277`; ein Loeschschritt in `initialize` verhindert, dass alte Läufe
mitgezählt werden. → **A-1 erledigt.**

## 6. Die neuen Tests dieser Runde

| Art | Datei | Tests | Ergebnis |
|---|---|---|---|
| Abwehr | `tests/unit/security/ApiRateLimitHardeningTest.java` | 5 | **grün** — Zähler je Mandant, `Retry-After` als Wartezeit in Sekunden (≥ 1, nie kürzer als bis zum Fensterende), neues Fenster, Limit 0, Grenze aus der Konfiguration |
| Lücke | `tests/unit/security/SecurityGapRound2Test.java` | 2 | **fehlschlagend** (S-29 Audience fail-open, S-31 Container-Pin 10.1.55 < 10.1.59) |
| Lücke | `tests/integration/security/SecurityGapRound2IT.java` | 2 | **fehlschlagend** (S-28 Body-Grenze umgehbar: „expected: 413 but was: 202"; S-30 „Expecting actual: 500 to be between: [400, 499]") |

> **Der Build ist ab diesem Commit bewusst rot.** Die vier neuen Gap-Tests schlagen fehl,
> bis der Rücksprung zu Schritt 6/7 die Lücken schließt — sie sind die **Abnahme** der
> Behebung. Für den vollständigen Lauf: `mvn -B verify -Dmaven.test.failure.ignore=true`.

**Die sechs Gap-Tests der ersten Runde sind unverändert und alle grün** — die
Nacharbeit hat die belegten Lücken geschlossen, ohne einen Test anzufassen.

## 7. Abweichungen und offene Punkte

**Abweichungen:** A-1 Coverage-Gate (erledigt) · A-2 AK-Zahl im Nachzug-Artefakt
(erledigt) · A-3 Build rot durch die neuen Gap-Tests (gewollt) · A-4 `docker-compose.yml`
fehlt (Schritt 9) · A-5 fünf historische Codepfade in der Matrix · A-6 **B-17**
Übergangstabelle (erledigt: Code **und** Doku) · A-7 große Repositories (bewertet: kein
Befund, Wartbarkeit) · A-8 kein Test für die eingehende Ratenbegrenzung (erledigt) ·
A-9 **Zweigabdeckung 62,73 %** — vom Gate nicht erzwungen (Schritt 9) · A-10 **SCA-Gate
ohne Baseline** (→ S-32).

**Offene Punkte (Gate 2, fachlich/rechtlich):** O-04 Verschlüsselung im Ruhezustand ·
O-05 AVV · R-01/E-13 Karenzzeit der Aufbewahrung · E-12 Härtung der mandantenübergreifenden
Sicht · B-18 Idempotenzschlüssel der Endpunkt-Registrierung · Q-3 Recht auf Löschung ·
Q-4 interne Adressbereiche (SSRF) · Q-7 DEK/KEK je Mandant (ADR-003 §2) · Q-9
Mandantenbezug des Anfragelimits.
**Offene Punkte (technisch):** Q-1 TLS/HSTS · Q-2 Branch-Schutz · Q-8 Anfragelimit je
Instanz (Schema → Schritt 9) · S-28…S-34 (Details im Security-Audit).
**Erledigt:** Q-5 (`jackson-core`-ReDoS — **empirisch nicht erreichbar**:
`StreamReadConstraints.maxNumberLength` = 1000 bricht den Zahlentoken vorher ab) ·
Q-6 (Spring Security jetzt 6.5.11).

## 8. Freigabe-Empfehlung

| Kriterium | Wert | Erfüllt? |
|---|---|---|
| QA: 0 Critical | 0 | **ja** |
| Security: 0 Critical | 0 | **ja** |
| Security: 0 High | **1** (S-28) | **nein** |
| Alle Tests grün | 481 grün; 4 bewusst fehlschlagende Gap-Tests | mit Einschränkung |
| Keine bekannten kritischen Lücken | 1 High (S-28) | **nein** |
| Keine Regressionen | keine | **ja** |
| AK-Abdeckung | 101/101 | **ja** |
| Coverage ≥ 80 % | **86,22 % Zeilen** — **im Build erzwungen** | **ja** |
| Vertrag unverändert | `git diff --stat -- docs/api/` leer | **ja** |

### **BLOCKIEREN** — kein Deployment (Schritt 9). Rücksprung zu **Schritt 6/7**:
**S-28** (High — Body-Grenze über den Medientyp umgehbar), S-29, S-30, S-31, S-33, S-34;
**S-32** gemeinsam mit Schritt 9. **Q-8** (Rate-Limit je Instanz) braucht ein Schema →
Schritt 9.

---

*Schritt 8 (Sicherheit & Compliance), zweite Runde · Agent: `aisdlc-qa` · 2026-10-22.*
