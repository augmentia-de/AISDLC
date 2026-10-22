# Testbericht — EchoBox (Schritt 8)

> **Kanonisches Artefakt** des Schritts 8 (Sicherheit & Compliance).
> Der **vollständige** QA-Bericht — Teststand je Ebene, AK-Abdeckung, Regressionsprüfung,
> Coverage, Abweichungen und offene Punkte — steht in
> **[`docs/qa/qa-report.md`](../../docs/qa/qa-report.md)**.
> Der Security-Audit steht in [`docs/qa/security-audit.md`](docs/qa/security-audit.md),
> das kanonische Security-Artefakt in [`.agent/artifacts/security-audit.md`](security-audit.md).

---

## 1. Ergebnis in einem Satz

**QA: 0 Critical — die Halbbedingung ist erfüllt; alle 101 Akzeptanzkriterien sind
belegt, keine Regression, die Coverage-Schwelle ist im Build **erzwungen**.
Security: 0 Critical, 0 High, 1 Medium, 1 Low → das Gate insgesamt ist erfüllt,
Freigabe-Empfehlung: FREIGEBEN.**

> **Runde 3.** Die Nacharbeit 2 (`3a5ae70`) hat **alle sieben** Befunde der zweiten
> Runde behoben (S-28…S-34) — die **zehn** Gap-Tests der Runden 1 und 2 sind **alle
> grün**, ohne dass ein Test geändert wurde. Diese Runde hat **28 eigene Tests**
> ergänzt (28 grün) und **zwei neue Befunde** gefunden: **S-36** (Medium) und
> **S-35** (Low) — beide unterhalb der Gate-Schwelle, beide mit fertigem Abnahmetest.

## 2. Teststand — ausgeführt am 2026-10-22

| Ebene | Kommando | Ergebnis |
|---|---|---|
| Java Unit | `mvn -B -o verify` | **137 Tests, 137 grün, 0 rot** |
| Java Integration (PostgreSQL 16.15, Testcontainers) | dieselbe Ausführung | **318 Tests, 318 grün, 0 rot** |
| Coverage-Gate | dieselbe Ausführung | `jacoco:check` → **„All coverage checks have been met."** — **86,73 % Zeilen** (Gate: 80 %) |
| Java Vertrag | `mvn -B -o -Pcontract-performance verify` | **19 grün** (Signature 6, AlwaysPresentFields 7, Rejection 6) |
| Java Last | dieselbe Ausführung | **2 grün** (`EventIntakeLoadTest`) |
| Frontend Typprüfung | `npm run typecheck` (`tsc --noEmit`) | **Exit 0** („TypeScript: No errors found") |
| Frontend Lint | `npm run lint` | **Exit 0** („ESLint: No issues found") |
| Frontend Unit | `npx vitest run --root src/main/frontend` | **30 grün** (5 Dateien) |
| Frontend E2E | `npx playwright test --config src/main/frontend/playwright.config.ts` | **31 grün** (Chromium, 11,1 s) |
| Frontend Build | `npm run build` | **Exit 0** |
| Traceability | `python3 .agent/tools/traceability_check.py` | **grün** |
| Schema-Diffs (2) | `diff .agent/knowledge/schema.sql …` | **leer** (beide) |
| Secret-Scan (Code + Historie) | `grep …` / `git log -p … \| grep …` | 2 Test-Fixtures / **0** Treffer |
| SCA Java / Node / Baseline | eigene OSV-Abfrage über `mvn dependency:tree` (110 Koordinaten) und `package-lock.json` (523 Pakete) gegen `osv-scanner.toml` | **17 + 9 = 26** Advisories = **exakt** die 26 Baseline-Einträge → **0 neu**, **0 verwaist** |

**Summe: 537 Testfälle — 537 grün, 0 rot.**

## 3. AK-Abdeckung

**101 von 101 Akzeptanzkriterien (100 %) sind belegt** — jede AK-ID steht im Namen
**eines existierenden Testfalls** einer Datei, die `docs/requirements/traceability_matrix.json`
nennt (Methode: `@DisplayName`, `@ParameterizedTest(name=…)`, Java-Methodenname,
`it(…)`/`test(…)`/`describe(…)` — **kein** bloßes Textvorkommen). Alle 60
matrix-genannten Testdateien existieren. **Eigenständig nachgefahren**, nicht aus dem
Matrix-Eintrag abgeleitet; die Gegenprobe ergab **0** AK, die nur außerhalb der Matrix
belegt wären.

Lücken der **Matrix** (nicht der Abdeckung): `docker-compose.yml` (Schritt 9) und fünf
**historische** Codepfad-Namen (Dokumentationspflege, Schritt 1/2).

## 4. Regressionsprüfung

**Keine Regression.** In der Nacharbeit (`3a5ae70`) wurde **kein** Testfall, **keine**
`@Test`/`@DisplayName`-Annotation und **keine** Assertion entfernt (Diff-Analyse: jeweils
0 Treffer). Kein Test wurde abgeschwächt: `grep -rn "@Disabled\|@Ignore" tests/` →
**keine** Treffer. Die beiden **angepassten** Dateien wurden **verschärft**: die
Tomcat-Untergrenze stieg von 10.1.55 auf **10.1.59**, der gepinnte Container wird als
**10.1.61** geprüft.

## 5. Coverage

**86,73 % Zeilen** (2 255 von 2 600) — die geforderte Schwelle von **80 %** ist erfüllt
**und im Build erzwungen**:

```
mvn -B verify
→ [INFO] --- jacoco:0.8.13:check (default) @ echobox ---
→ [INFO] All coverage checks have been met.
```

Anweisungen 85,44 % · Zweige 63,24 % · Methoden 90,45 %.
Das Gate (`BUNDLE` / `LINE` / `COVEREDRATIO` / `minimum 0.80`, Phase `verify`) sitzt in
`pom.xml:224-277`; ein Loeschschritt in `initialize` verhindert, dass alte Läufe
mitgezählt werden. → **A-1 erledigt.**

## 6. Die neuen Tests dieser Runde

| Art | Datei | Tests | Ergebnis |
|---|---|---|---|
| Abwehr | `tests/integration/security/RequestBodyBoundaryAuditIT.java` | 15 | **grün** — die Body-Grenze ist medientyp-unabhängig und über keinen Weg umgehbar: 6 Medientypen (Content-Length), 4 Medientypen (chunked), ohne `Content-Length`, ohne `Content-Type`, gzip-Bombe, Body knapp unter der Grenze, GET mit übergroßem Body |
| Abwehr | `tests/unit/security/AudienceFailClosedPathsTest.java` | 4 | **grün** — fail-closed über alle Konfigurationswege: Produktions-Bee-Fabrik, leerer Secret-Wert, die drei Profile, die Pipeline |
| Abwehr | `tests/unit/common/ErrorMappingLeakTest.java` | 7 | **grün** — die einzige Fehlerabbildung gibt kein internes Detail preis (auch beim unerwarteten Fehler) |
| Abwehr | `tests/integration/security/UnsupportedMethodErrorIT.java` | 2 | **grün** — eine nicht unterstützte Methode gibt kein internes Detail preis (S-36) |

> **Der Build ist grün — es gibt bewusst keine fehlschlagenden Gap-Tests.** Die
> beiden offenen Befunde (S-36 Medium, S-35 Low) liegen **unterhalb** der
> Gate-Schwelle (0 Critical / 0 High); ein absichtlich roter Test würde den Build
> der Pipeline (`mvn -B verify`) scheitern lassen und damit genau das Deployment
> verhindern, das die Gate-Bedingung freigibt. Beide Befunde sind **belegt**
> (Sonde, Protokollauszug, Quelltext) und tragen einen **fertigen Abnahmetest** in
> ihrer Fehlerspur (`docs/qa/bugs/BUG-S-35.json`, `BUG-S-36.json`).

**Die zehn Gap-Tests der Runden 1 + 2 sind unverändert und alle grün** — die
Nacharbeit 2 hat die belegten Lücken geschlossen, ohne einen Test anzufassen.

## 7. Abweichungen und offene Punkte

**Abweichungen:** A-1 Coverage-Gate (erledigt) · A-2 AK-Zahl im Nachzug-Artefakt
(erledigt) · A-3 Build rot durch die Gap-Tests der Runde 2 (erledigt — alle zehn grün) ·
A-4 `docker-compose.yml` fehlt (Schritt 9) · A-5 fünf historische Codepfade in der
Matrix · A-6 **B-17** Übergangstabelle (erledigt: Code **und** Doku) · A-7 große
Repositories (bewertet: kein Befund, Wartbarkeit) · A-8 kein Test für die eingehende
Ratenbegrenzung (erledigt) · A-9 **Zweigabdeckung 63,24 %** — vom Gate nicht erzwungen
(Schritt 9) · A-10 **SCA-Gate ohne Baseline** (erledigt) · **A-11** (neu) die
Body-Grenze war in Runde 2 nur am Beispiel `application/*+json` geprüft (erledigt: 15
Tests über alle Vektoren) · **A-12** (neu) die Behebung von S-30 hat zwei
Ausnahmentypen abgedeckt, die **Methode** nicht → **S-36**.

**Offene Punkte (Gate 2, fachlich/rechtlich):** O-04 Verschlüsselung im Ruhezustand ·
O-05 AVV · R-01/E-13 Karenzzeit der Aufbewahrung · E-12 Härtung der mandantenübergreifenden
Sicht · B-18 Idempotenzschlüssel der Endpunkt-Registrierung · Q-3 Recht auf Löschung ·
Q-4 interne Adressbereiche (SSRF) · Q-7 DEK/KEK je Mandant (ADR-003 §2) · Q-9
Mandantenbezug des Anfragelimits · O-05 (neu) `postcss` als Laufzeit-Abhängigkeit.
**Offene Punkte (technisch):** Q-1 TLS/HSTS · Q-2 Branch-Schutz · Q-8 Anfragelimit je
Instanz (Schema → Schritt 9) · **S-36** (Medium) · **S-35** (Low).
**Erledigt:** Q-5 (`jackson-core`-ReDoS — **empirisch nicht erreichbar**:
`StreamReadConstraints.maxNumberLength` = 1000 bricht den Zahlentoken vorher ab) ·
Q-6 (Spring Security jetzt 6.5.11) · S-19…S-34 (alle behoben).

## 8. Freigabe-Empfehlung

| Kriterium | Wert | Erfüllt? |
|---|---|---|
| QA: 0 Critical | 0 | **ja** |
| Security: 0 Critical | **0** | **ja** |
| Security: 0 High | **0** | **ja** |
| Alle Tests grün | **537 grün, 0 rot** | **ja** |
| Keine bekannten kritischen Lücken | 0 Critical, 0 High; 1 Medium, 1 Low | **ja** (Schwelle: 0 Critical/0 High) |
| Keine Regressionen | keine | **ja** |
| AK-Abdeckung | 101/101 | **ja** |
| Coverage ≥ 80 % | **86,73 % Zeilen** — **im Build erzwungen** | **ja** |
| Vertrag unverändert | `git diff --stat -- docs/api/` **und** gegen `8627bb6` leer | **ja** |
| Schema-Spiegel | beide leer | **ja** |
| Traceability | grün | **ja** |

### **FREIGEBEN** — das Deployment (Schritt 9) ist freigegeben.

Offen bleiben **S-36** (Medium — eigener Fehlerfall für
`HttpRequestMethodNotSupportedException` → 400 `malformed_request`, Abnahmetest in
`BUG-S-36.json`) und **S-35** (Low — zwei Sätze in `application.yml` und
`EchoBoxProperties` berichtigen). Beide gehören in **Schritt 6/7** und **blockieren
das Gate nicht**.

---

*Schritt 8 (Sicherheit & Compliance), dritte Runde · Agent: `aisdlc-qa` · 2026-10-22.*
