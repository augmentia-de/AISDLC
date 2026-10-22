# Nacharbeit zu den Security-Befunden (Schritt 8, **zweite Runde**) — Ruecksprung zu Schritt 6/7

> Agent: `aisdlc-execution` · Stand: 2026-10-22 · Quelle:
> `docs/qa/security-audit.md` (S-28 … S-34) und `docs/qa/qa-report.md` (Q-8, Q-9).
>
> Der **eingefrorene** Vertrag `docs/api/openapi.yaml` ist **unveraendert**
> (`git diff --stat -- docs/api/` ist leer). S-30 wurde **innerhalb** des
> Vertrags geloest (400 `malformed_request`) — es ist kein Vertragsdefekt.

## 1. Ergebnis

| Feld | Wert |
|---|---|
| Befunde | **7 von 7** behoben (S-28, S-29, S-30, S-31, S-32, S-33, S-34) |
| davon **High** | **1** (S-28) — der Angriff (Ausfall fuer alle Mandanten) ist geschlossen |
| Lueckenbelege der zweiten Runde | **alle vier gruen** (`SecurityGapRound2IT` 2 Tests, `SecurityGapRound2Test` 2 Tests) |
| Neue Regressionstests | **6 Dateien, 22 Tests** (S-28, S-29, S-30, S-31, S-32, S-33, S-34) |
| Vertrag | unveraendert — der Vertrag wurde **erfuellt**, nicht verhandelt |
| Schema | unveraendert — beide Spiegel byte-identisch |
| Offen (kein Befund) | **O-05** (postcss als Laufzeit-Abhaengigkeit, befristet), **Q-8**, **Q-9** |

## 2. Je Befund

### S-28 (High) — die Grenze des Request-Bodies haengt am Medientyp

**Fundstelle (vorher):** `src/main/java/com/echobox/tenant/TenantParameterFilter.java:82`
(Vorpruefung gegen `Content-Length`), `:98` (begrenztes Lesen), `:160-167`
(`isBodyRelevant()` — die Medientyp-Bedingung).

**Ursache.** Die Grenze galt nur fuer Bodies, deren `Content-Type` mit
`application/json` beginnt. Spring MVC liest aber auch `application/*+json`
(`MappingJackson2HttpMessageConverter`) — ein solcher Body fiel an **beiden**
Grenzen vorbei und landete unbeschaenkt im Heap des **geteilten** JVM
(`OutOfMemoryError` → Ausfall fuer **alle** Mandanten).

**Aenderung.**

1. Die Bedingung der Grenze ist **nicht mehr der Medientyp**, sondern die
   **Methode**: `mayCarryBody(request)` (POST, PUT, PATCH, DELETE). Die
   Vorpruefung gegen `Content-Length` und das begrenzte Lesen benutzen
   **dieselbe** Bedingung (`TenantParameterFilter.java:88-107`).
2. Der Filter liest den Body **vor** dem Message-Converter **begrenzt**
   (max. 2 MiB, `readBounded`) und gibt dem Converter ausschliesslich diese
   bereits gelesenen, damit begrenzten Byte (`CachedBodyRequest`). Der
   Converter kann so nie mehr Bytes sehen, als die Grenze erlaubt — die Grenze
   haengt **nicht** mehr an einer Medienpruefung eines Converters.
3. Die Pruefung des **Mandantenparameters** im Body ist ebenfalls nicht mehr an
   den Medientyp gebunden: der (begrenzte) Body wird als JSON gelesen,
   unabhaengig vom `Content-Type` (`findTenantParameterInBody`).
4. **Zusaetzlich (Haertung in der Tiefe):** die sechs Operationen mit
   Request-Body akzeptieren nur `application/json`
   (`consumes = MediaType.APPLICATION_JSON_VALUE`) — `application/*+json` wird
   gar nicht erst gelesen. Das entspricht dem eingefrorenen Vertrag, dessen
   Request-Bodies ausschliesslich `application/json` kennen.

**Nachweis.**

| Test | Umfang | Ergebnis |
|---|---|---|
| `RequestBodyMediaTypeLimitIT` (neu, 8 Tests) | `application/json`, `application/vnd.custom+json`, `application/problem+json`, `text/plain`, `multipart/form-data`, `application/octet-stream` — je **413 `payload_too_large`**; **ohne** `Content-Type` (roher Socket) — **413**; Mandantenparameter bei `application/*+json` — **422 `tenant_parameter_rejected`** | gruen |
| `SecurityGapRound2IT` (Lueckenbeleg) | `application/vnd.custom+json` + 2.097.298 Byte — **vorher 202, jetzt 413** | gruen |
| `RequestBodyLimitIT` (S-20) | chunked unter/ueber der Grenze | gruen (unveraendert) |
| `SecurityGapIT` | chunked ueber der Grenze → 413 | gruen (unveraendert) |

**Vollstaendig behoben: ja.** Der Angriff ist geschlossen: der Body jeder
Anfrage — unabhaengig vom Medientyp — wird begrenzt gelesen, und der Converter
sieht nur die begrenzten Byte.

### S-29 (Medium) — die Zielgruppen-Pruefung faellt still aus (fail-open)

**Fundstelle (vorher):** `src/main/java/com/echobox/config/SecurityConfiguration.java:126`
(`if (audience != null && !audience.isBlank())`), `application.yml:107`.

**Ursache.** `audience: ${ECHobox_SECURITY_AUDIENCE:echobox}` greift der
Vorgabewert nur bei einer *nicht gesetzten* Variablen. Ein **leerer**
Secret-Wert ergibt `''` → die Pruefung wurde **nicht** in die Pruefkette
aufgenommen → ein Token, das derselbe Identity Provider fuer einen anderen
Client ausgestellt hat, wurde angenommen. Anders als beim Schluessel (S-27)
gab es **keinen** Startfehler.

**Aenderung.**

1. `validators(...)` nimmt die Zielgruppen-Pruefung **immer** auf
   (`SecurityConfiguration.java:139-150`). Ist keine Zielgruppe konfiguriert,
   lehnt die Pruefung **jedes** Token ab (fail-closed) — eine leere
   Konfiguration schaltet die Pruefung nicht mehr ab.
2. `AudiencePolicy` (neu, `src/main/java/com/echobox/config/AudiencePolicy.java`):
   der Dienst **startet nicht**, wenn die Zielgruppe leer ist
   (`IllegalStateException` mit klarer Meldung) — analog zur Schluesselpruefung
   (`CoreConfiguration.payloadEncryptionKey`, S-27). Der Vorgabewert `echobox`
   bleibt unveraendert.

**Nachweis.** `SecurityGapRound2Test` (Lueckenbeleg — **vorher rot**: „Expecting
code to raise a throwable", jetzt gruen) · `AudienceFailClosedTest` (neu, 4
Tests: jedes Token wird ohne Zielgruppe abgelehnt; Leerzeichen sind ebenfalls
leer; `AudiencePolicy.required("")` verhindert den Start; die konfigurierte
Zielgruppe bleibt wirksam) · `JwtAudienceHardeningTest` (4 Tests, gruen —
unveraendert) · `JwtDecoderHardeningTest` (7 Tests, gruen — unveraendert).

**Vollstaendig behoben: ja.**

> **Betriebshinweis (Schritt 9):** das Secret `PRODUCTION_SECURITY_AUDIENCE`
> (bzw. `STAGING_SECURITY_AUDIENCE`) darf **nicht leer** sein — sonst startet
> der Dienst nicht mehr. Das ist die gewollte fail-closed-Zusage, aber es ist
> eine **Aenderung** des Startverhaltens und muss in der Betriebsdokumentation
> stehen.

### S-30 (Medium) — 500 statt 4xx bei nicht unterstuetztem Medientyp

**Fundstelle (vorher):** `src/main/java/com/echobox/common/GlobalExceptionHandler.java:230`
— kein eigener Fall; der allgemeine Handler protokollierte
`LOG.error(..., exception)` (Stacktrace) und antwortete **500 `internal_error`**.

**Aenderung.**

1. Eigener `@ExceptionHandler` fuer `HttpMediaTypeNotSupportedException` **und**
   `HttpMediaTypeNotAcceptableException` → **400 `malformed_request`**. Der
   eingefrorene Vertrag kennt fuer einen Fehler des Aufrufers nur 4xx; 415
   waere ein Vertragswechsel (Rücksprung zu Schritt 2) — deshalb 400.
2. `MultipartException` → ebenfalls **400 `malformed_request`** (der Dienst
   nimmt nur JSON entgegen; der Multipart-Resolver ist abgeschaltet).
3. **Kein Stacktrace im Protokoll**: die neuen Faelle loggen auf **DEBUG**
   **ohne** das Exception-Objekt. Nur unerwartete Fehler bleiben im
   `ERROR`-Protokoll mit Stacktrace.

**Nachweis.** `SecurityGapRound2IT` (Lueckenbeleg — **vorher**: „Expecting
actual: 500 to be between: [400, 499]", jetzt gruen) · `MediaTypeErrorIT` (neu,
6 Tests: `text/plain`, `application/octet-stream`, `application/xml`,
`multipart/form-data`, `application/vnd.custom+json` — je 4xx **und** nicht
500, mit `malformed_request` und **ohne** `internal_error`; die Antwort gibt
kein internes Detail preis — kein `org.springframework`, kein `com.echobox`, kein
`Exception`, kein `java.`).

**Vollstaendig behoben: ja.**

### S-31 (Medium) — der gepinnte Container lag unter der Fix-Linie

**Fundstelle (vorher):** `pom.xml:52` — `<tomcat.version>10.1.55</tomcat.version>`.

**Aenderung.** `<tomcat.version>` → **10.1.61**. Das ist die **hoechste
erreichbare** Version der 10.1.x-Linie: `maven-metadata.xml` von Maven Central
weist `10.1.59`, `10.1.60`, `10.1.61` aus — **`10.1.58` wurde nie
veroeffentlicht**; die Fix-Linie der drei Advisories (GHSA-9xv2-5v5q-p794,
GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5 — betroffen „10.1.0-M1 … 10.1.57",
behoben ab 10.1.58) beginnt also mit **10.1.59**.

**Erreichbarkeit — belegt (nicht behauptet).**

```
mvn -B dependency:get -Dartifact=org.apache.tomcat.embed:tomcat-embed-core:10.1.61
→ Exit 0
→ Downloaded from central: .../tomcat-embed-core-10.1.61.jar (3.7 MB)
```

Der Build laedt `tomcat-embed-core`, `tomcat-embed-websocket` und
`tomcat-embed-el` in **10.1.61** (`mvn compile`, Log vom 2026-10-22).

**Wirksamkeit — belegt.** Die OSV-Abfrage ueber den **neuen** Abhaengigkeitsbaum
(110 Koordinaten) ergibt **17 statt 20** Advisories: die drei Tomcat-Advisories
sind **nicht** mehr enthalten. Die 17 verbleibenden sind genau die im Audit
(Anhang A.2 bis A.4) als nicht erreichbar bewerteten.

**Mitgezogene Regressionstests.** `DependencyHardeningTest` (Untergrenze jetzt
**10.1.59** — die Fix-Linie; der Pin ist 10.1.61) · `ContainerHardeningIT` (der
Pin wird als 10.1.61 geprueft) · `SecurityGapRound2Test` (Lueckenbeleg,
verlangt ≥ 10.1.59 — **vorher rot**, jetzt gruen).

**Vollstaendig behoben: ja.** Es wurde **keine** Version erfunden: 10.1.61 ist
heruntergeladen, `maven-metadata.xml` weist sie aus, und der Build laeuft damit.

### S-32 (Medium) — der SCA-Gate schlug auf jedem Lauf an (keine Baseline)

**Fundstelle (vorher):** `.github/workflows/deploy.yml:56-61` — der
SCA-Schritt ohne Ignorierliste; der aktuelle Baum traegt 20 (Java) + 10 (npm)
Advisories, also **schlug der Lauf immer an**.

**Aenderung.**

1. **`osv-scanner.toml` (neu, Repository-Wurzel)** — die Baseline: **26**
   `[[IgnoredVulns]]`-Eintraege (17 Java + 9 npm), je mit `id`, **`ignoreUntil`**
   (2027-04-30) und **`reason`** (die Begruendung der Erreichbarkeit aus dem
   Audit, Anhang A). Erzeugt aus dem **aktuellen** Stand: `mvn -B dependency:tree`
   (110 Koordinaten) → OSV `querybatch`; `package-lock.json` (522 Pakete) → OSV
   `querybatch`.
2. **Die Pipeline uebergibt die Baseline**:
   `scan-args: --config=osv-scanner.toml --recursive ./`. Damit schlaegt der
   Lauf nur noch bei **neuen** Advisories an.
3. **Die Pflege ist dokumentiert** (Dateikopf): wer eine Abhaengigkeit
   aktualisiert, entfernt die betroffenen Eintraege **im selben Schritt**; jeder
   Eintrag traegt eine Begruendung und ein Ablaufdatum von **hoechstens sechs
   Monaten**; ist das Datum ueberschritten, meldet der Scanner das Advisory
   wieder — die Baseline **veraltet von selbst**. Die Baseline wird zu Beginn
   jeder Audit-Runde neu erzeugt (Owner `aisdlc-qa`), die Freigabe erfolgt mit
   GATE 2 (Owner: menschlicher Reviewer).

**Korrektur gegenueber der Empfehlung des Audits (mit Beleg).** Das Audit
empfahl `--ignore-dev` fuer die devDependencies des Frontends. **Diesen Schalter
gibt es in osv-scanner v2.7.0 nicht** — geprueft in der Quelle des Tags
(`cmd/osv-scanner/internal/helper/flags.go`: weder `--ignore-dev` noch
`--ignore-dev-groups`; die verfuegbaren Schalter sind u. a. `--config`,
`--recursive`, `--lockfile`). Die Alternative ist die
`[[PackageOverrides]]`-Regel `group = "dev"`; sie wurde **nicht** verwendet,
weil die Gruppe im Lockfile-Fall nicht gesetzt ist. Stattdessen traegt jedes
Advisory einen **Einzeleintrag** in der Baseline.

**Ein Befund an der Aussage des Audits (Wahrheitsbefund, im Bericht
dokumentiert).** Das Audit nennt alle zehn npm-Advisories „ausschliesslich
devDependencies". Das trifft fuer **fuenf** zu (`tinypool` ×2, `vitest`,
`@vitest/mocker`, `braces`). **`postcss 8.4.31` wird von `next 15.5.28` als
*Laufzeit*-Abhaengigkeit mitgeliefert** (im `package-lock.json` **nicht** als
`dev` markiert) — die vier postcss-Advisories betreffen damit eine
Laufzeit-Abhaengigkeit. Begruendung und Befristung stehen in der Baseline; die
Behebung erfolgt mit dem naechsten `next`-Upgrade → **offener Punkt O-05**
(Wartung / Schritt 9), **kein** Befund.

**Nachweis.** `ScaBaselineIT` (neu, 3 Tests: die Baseline existiert; jeder
Eintrag traegt ID, Begruendung und ein **nicht abgelaufenes** Ablaufdatum; die
Datei enthaelt **keine unbekannten Schluessel** — der Scanner lehnt die Datei
sonst ab; die Pipeline uebergibt `--config=osv-scanner.toml`) ·
`python3 -c "tomllib.load(...)"` → 26 Eintraege, alle gueltig, alle nicht
abgelaufen · die OSV-Abfrage → genau diese 26 IDs.

**Vollstaendig behoben: ja** (der Gate ist kalibriert). **Offen:** O-05.

### S-33 (Low) — ein Token ohne Mandant ergab 500

**Fundstelle (vorher):** `src/main/java/com/echobox/tenant/TenantTransactionRunner.java:92` —
`IllegalArgumentException` → allgemeiner Handler → **500 `internal_error`** mit
Stacktrace.

**Aenderung.** `setTenant` wirft jetzt `UnauthenticatedException` → **401
`unauthorized`** (neu: eigener Konstruktor mit eigener Handlungsanweisung in
`common/UnauthenticatedException`). Der Mandant wird weiterhin **nicht**
geraten und **nicht** ersetzt — die Ablehnung bleibt fail-closed.

**Nachweis.** `MissingTenantIT` (neu, 1 Test: ein Token **ohne**
`organisation_id` → **401** mit `unauthorized`, **ohne** `internal_error`).

**Vollstaendig behoben: ja.**

> **Hinweis (kein Befund):** der Zaehler des Anfragelimits zaehlt weiterhin nur
> Anfragen **mit** Mandant (`ApiRateLimitFilter.organisationIdOf()`). Das ist
> die offene Frage **Q-9** des Audits (Owner: Fachbereich + Betrieb, GATE 2) —
> sie bleibt **offen** und wird in diesem Schritt nicht entschieden, weil die
> Aenderung der Zaehlschluessel (z. B. auf `sub`) die zugesagte Semantik
> „je Mandant" veraendern wuerde.

### S-34 (Low) — veralteter Javadoc

**Fundstelle (vorher):** `src/main/java/com/echobox/delivery/DeliveryStateMachine.java:28-29`
und `:120-121` — der Verweis auf eine ausstehende Nachziehung der
Uebergangstabelle. Die Tabelle **ist** nachgezogen (Commit `28396e0`).

**Aenderung.** Beide Stellen aktualisiert: der Javadoc nennt die Uebergaenge
`pending -> delayed` und `scheduled -> delayed` und verweist auf
`docs/architecture/domain_model.md` §3.3 als die nachgezogene Tabelle. Die
Aussage „wird in Schritt 2 nachgezogen" ist entfernt.

**Nachweis.** `DeliveryStateMachineDocumentationTest` (neu, 2 Tests: der
Quelltext enthaelt keine ausstehende Nachziehungs-Zusage mehr; er nennt beide
Uebergaenge und den Verweis auf die Tabelle).

**Vollstaendig behoben: ja.**

## 3. Was **nicht** behebbar war

**Nichts.** Alle sieben Befunde sind vollstaendig behoben; kein Befund bleibt
als offene Luecke stehen, kein Test bleibt bewusst rot.

Es bleiben **drei Punkte offen**, die **keine Befunde** sind und in diesem
Schritt nicht entschieden werden konnten:

| Punkt | Sachverhalt | Owner |
|---|---|---|
| **O-05** | `postcss 8.4.31` ist eine **Laufzeit**-Abhaengigkeit von `next 15.5.28`; vier Advisories sind bis dahin befristet ausgenommen (Begruendung + Ablaufdatum in der Baseline) | Wartung / Schritt 9 |
| **Q-8** | Der Zaehler des Anfragelimits liegt **je Instanz** im Speicher — eine instanzuebergreifende Grenze braucht ein Schema | Schritt 9 |
| **Q-9** | Anfragen **ohne** Mandant werden nicht gezaehlt (sie werden nun mit 401 abgelehnt, aber nicht gezaehlt) | Fachbereich + Betrieb, GATE 2 |

## 4. Verifikation

| Pruefung | Ergebnis |
|---|---|
| `mvn -B verify` | **gruen** — 126 Unit + 301 Integration, 0 Fehler; Coverage-Gate: „All coverage checks have been met"; **85,99 % Zeilen** (Gate ≥ 80 %) |
| `npx tsc --noEmit` | **0** Fehler |
| `npm run lint` | **0** |
| `npm run build` | **ok** |
| `npx vitest run` | **30** gruen |
| `npm run test:e2e` (Playwright) | **31** gruen |
| Schema-Diffs (`.agent/knowledge/schema.sql` ↔ `docs/architecture/schema.sql` ↔ Migration) | **leer** (byte-identisch) |
| `python3 .agent/tools/traceability_check.py` | **gruen** — „Keine Luecken" |
| `git diff --stat -- docs/api/` | **leer** — der Vertrag blieb eingefroren |

**Die vier Lueckenbelege der zweiten Runde sind gruen:**

| Lueckenbeleg | vorher | jetzt |
|---|---|---|
| `SecurityGapRound2IT` — S-28 (Medientyp umgeht die Grenze) | 202 statt 413 | **gruen** (413) |
| `SecurityGapRound2IT` — S-30 (500 statt 4xx) | 500 | **gruen** (400) |
| `SecurityGapRound2Test` — S-29 (Audience fail-open) | kein Fehler geworfen | **gruen** (JwtException) |
| `SecurityGapRound2Test` — S-31 (Container-Pin) | 10.1.55 < 10.1.59 | **gruen** (10.1.61) |

## 5. Geaenderte Dateien

| Bereich | Datei | Befund |
|---|---|---|
| `src/main/java` | `com/echobox/tenant/TenantParameterFilter.java` | S-28 |
| `src/main/java` | `com/echobox/tenant/TenantTransactionRunner.java` | S-33 |
| `src/main/java` | `com/echobox/config/SecurityConfiguration.java` | S-29 |
| `src/main/java` | `com/echobox/config/AudiencePolicy.java` (neu) | S-29 |
| `src/main/java` | `com/echobox/common/GlobalExceptionHandler.java` | S-30 |
| `src/main/java` | `com/echobox/common/UnauthenticatedException.java` | S-33 |
| `src/main/java` | `com/echobox/delivery/DeliveryStateMachine.java` | S-34 |
| `src/main/java` | `com/echobox/event/EventController.java`, `endpoint/EndpointController.java`, `delivery/DeliveryController.java`, `protocol/ProtocolController.java` | S-28 (nur `application/json`) |
| `tests/` | `integration/security/RequestBodyMediaTypeLimitIT.java` (neu) | S-28 |
| `tests/` | `unit/security/AudienceFailClosedTest.java` (neu) | S-29 |
| `tests/` | `integration/security/MediaTypeErrorIT.java` (neu) | S-30 |
| `tests/` | `unit/security/DependencyHardeningTest.java` (angepasst) | S-31 |
| `tests/` | `integration/deployment/ContainerHardeningIT.java` (angepasst) | S-31 |
| `tests/` | `integration/deployment/ScaBaselineIT.java` (neu) | S-32 |
| `tests/` | `integration/security/MissingTenantIT.java` (neu) | S-33 |
| `tests/` | `unit/delivery/DeliveryStateMachineDocumentationTest.java` (neu) | S-34 |
| `pom.xml` | `<tomcat.version>` 10.1.55 → 10.1.61 (+ Kommentar) | S-31 |
| `.github/workflows/deploy.yml` | `--config=osv-scanner.toml` im SCA-Schritt | S-32 |
| `osv-scanner.toml` (neu, Repository-Wurzel) | die Baseline (26 Eintraege) | S-32 |

**Nicht angefasst:** `docs/api/` (eingefroren), `docs/adr/`,
`docs/architecture/`, `docs/requirements/`, `.agent/knowledge/`, das Schema, die
Oberflaeche, `package.json` / `package-lock.json`.

## 6. Zusaetzliche Dateien im Commit (ueber den Pfad-Scope hinaus)

Der Commit-Scope des Schritts ist `src/`, `tests/`, `docs/execution/`. Zusaetzlich
enthalten sind — wie in der Nacharbeit der ersten Runde (`d135cda`) —:

| Datei | Grund |
|---|---|
| `pom.xml` | der Container-Pin (S-31) |
| `.github/workflows/deploy.yml` | die Baseline im SCA-Schritt (S-32) |
| `osv-scanner.toml` | die Baseline selbst (S-32) |
| `.agent/artifacts/step8-nacharbeit-security-run2.md` | das Artefakt des Schritts (AGENTS.md: „Always write a summary artifact at the end of every task") |
| `CHANGELOG.md` | der Eintrag unter `[Unreleased]` → `Security` |
