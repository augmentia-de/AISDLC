# Security-Audit — EchoBox (Schritt 8, **zweite Runde**)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · Audit **read-only** (keine Datei in `src/`,
> `docs/api/`, `docs/adr/`, `docs/architecture/`, `.agent/knowledge/` oder
> `docs/requirements/` geändert). In `tests/` wurden **neue** Tests angelegt.
> Skill: `aisdlc-security-scan` (alle 10 Prüfungen, **erneut und vollständig**) ·
> Gate: **GATE 2** — QA 0 Critical, Security 0 Critical und 0 High.
>
> **Runde 2.** Die erste Runde (`1ba4160`) fand 0 Critical, 2 High, 5 Medium, 2 Low und
> empfahl **BLOCKIEREN**. Die Nacharbeit (`d135cda`, Doku `28396e0`) hat S-19…S-27 und
> A-1 behoben. Dieser Bericht prüft **jeden** dieser Befunde **am aktuellen Stand mit
> eigenem Nachweis** nach und führt danach ein **erneutes vollständiges Audit** durch.

---

## 1. Gesamtergebnis

| Schwere | Anzahl | Befund-IDs |
|---|---|---|
| **Critical** | **0** | — |
| **High** | **1** | **S-28** (Body-Grenze über den Medientyp umgehbar) |
| **Medium** | **4** | S-29 (Audience fail-open), S-30 (500 statt 4xx), S-31 (Container-Pin unter der Fix-Linie), S-32 (SCA-Gate ohne Baseline) |
| **Low** | **2** | S-33 (Token ohne Mandant → 500), S-34 (veralteter Javadoc) |

### Freigabe-Empfehlung: **BLOCKIEREN**

Die Gate-Bedingung verlangt **0 Critical und 0 High**. Es liegt **1 High** vor —
**neu in dieser Runde**, belegt durch einen ausgeführten Check, nicht durch einen
Verdacht:

* **S-28** — die Grenze des Request-Bodies (2 MiB) haengt weiterhin **am Medientyp**.
  `TenantParameterFilter.isBodyRelevant()` (`src/main/java/com/echobox/tenant/TenantParameterFilter.java:160-167`)
  prueft nur Bodies, deren Content-Type mit `application/json` beginnt; Spring MVC liest
  aber auch `application/*+json` (`MappingJackson2HttpMessageConverter`). **Beleg:**
  `SecurityGapRound2IT` → „expected: 413 but was: **202**" — ein Body von
  **2.097.298 Byte** mit `Content-Type: application/vnd.custom+json` wurde **angenommen**;
  die Gegenprobe mit **20.971.604 Byte** ergab ebenfalls **202**. Der Body landet
  unbeschaenkt im Heap des **geteilten** JVM → `OutOfMemoryError` → Ausfall fuer
  **alle** Mandanten. Das ist dieselbe Schadensklasse wie der urspruengliche S-20,
  der in Runde 1 als **High** bewertet wurde und als behoben galt.

**Konsequenz:** kein Deployment. Erforderlich ist ein Rücksprung zu **Schritt 6/7**
(Implementierung) fuer S-28, S-29, S-30, S-31 und — gemeinsam mit Schritt 9 — fuer S-32.
Der **eingefrorene Vertrag** `docs/api/openapi.yaml` bleibt dabei **unveraendert**:
S-30 wird innerhalb des Vertrags geloest (400 `malformed_request`), es ist **kein**
Vertragsdefekt.

> **Wichtig:** Die sechs Gap-Tests der ersten Runde sind **alle gruen** — die
> Nacharbeit hat die belegten Luecken tatsaechlich geschlossen. Der Build ist erst mit
> den **vier neuen** Gap-Tests dieser Runde wieder rot. Das ist die Absicht: die Luecken
> bleiben so lange sichtbar, wie sie bestehen (Nachweiskette in **Anhang B**).

---

## 2. Nachpruefung der Befunde der ersten Runde

Geprueft wurde **jeder** Befund am aktuellen Stand — mit eigenem Nachweis
(Datei:Zeile oder ausgefuehrter Check), **nicht** nach dem Bericht der Nacharbeit.

| ID | Schwere (Runde 1) | Bewertung | Nachweis (aktueller Stand) |
|---|---|---|---|
| **S-19** Abhaengigkeiten | High | **behoben** (mit einer Einschraenkung → **S-31**) | `mvn -B dependency:tree` → `org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.55:compile`; daneben `spring-security:6.5.11`, `spring-core:6.2.19`, `jackson-databind:2.21.4`. **Die drei erreichbaren CVEs sind behoben:** CVE-2026-24880 (Smuggling, fix 10.1.52), CVE-2025-48988 (Multipart-DoS, fix 10.1.42), CVE-2025-48989 („made you reset", fix 10.1.44) — **keine** dieser drei IDs erscheint mehr in der OSV-Abfrage (Anhang A). Gesamtbestand: **96 → 20** Advisories. *Einschraenkung:* der Pin `tomcat.version=10.1.55` liegt unter der Fix-Linie (10.1.58) von drei **neuen** Tomcat-Advisories → **S-31**. |
| **S-20** Request-Body ohne Grenze | High | **teilweise behoben** → **S-28 (neu, High)** | Das begrenzte Lesen existiert: `TenantParameterFilter.java:98` ruft `readBounded(request, 2 MiB)` (`:139-158`), der Abbruch ergibt **413**. Belegt: `SecurityGapIT.a_chunked_request_body_above_two_mebibytes_must_be_rejected` = **gruen**, `RequestBodyLimitIT` (2 Tests) = **gruen** (chunked unter der Grenze wird angenommen, chunked ueber der Grenze ergibt 413). **Aber:** die Grenze gilt nur fuer `application/json` (`:160-167`); `application/*+json` umgeht sie → **S-28**. |
| **S-21** JWT-Audience / `exp` | Medium | **teilweise behoben** → **S-29 (neu, Medium)** | Eigene Pruefkette: `SecurityConfiguration.java:112` → `validators(...)` (`:122-130`) = `JwtTimestampValidator` (prueft `exp` **und** `nbf`) + `expiryRequired()` (`:138-146`, `exp` ist Pflicht) + `audienceRequired(audience)` (`:154-163`). Belegt: `SecurityGapTest` ×2 = **gruen**, `JwtAudienceHardeningTest` (4 Tests) = **gruen**. **Aber:** die Audience-Pruefung faellt bei leerer Konfiguration still aus (`:126`) → **S-29**. `iss` wird **nicht** geprueft — der Vertrag fordert keinen Issuer (`openapi.yaml:1726-1733`), der IdP ist ueber die JWKS-Adresse gebunden; kein Befund. |
| **S-22** Schluessellaenge | Medium | **behoben** | `CoreConfiguration.java:71-79` — der dekodierte Schluessel muss **exakt 32 Byte** sein, sonst `IllegalStateException` mit klarer Meldung; **kein** Fallback auf eine kuerzere Laenge. Der ephemere Schluessel ist ebenfalls 32 Byte (`:65-66`). Belegt: `SecurityGapTest.a_sixteen_byte_key_must_prevent_the_start` = **gruen**, `CryptoKeyLengthTest` (4 Tests) = **gruen**. |
| **S-23** Antwortkoerper des Empfaengers | Medium | **behoben** | `DeliveryHttpClient.java:89` → eigener `LimitedBodySubscriber` (`:135-187`): speichert hoechstens `maxResponseBodyBytes` (Vorgabe 1 MiB, `application.yml:147`), der Rest wird verworfen — der Body wird weiterhin **vollstaendig konsumiert**, damit Timeout und Verbindungs-Wiederverwendung wirksam bleiben. Belegt: `SecurityGapTest.the_response_body_of_the_receiver_must_be_read_with_a_limit` = **gruen**, `DeliveryResponseLimitTest` (3 Tests) = **gruen**. |
| **S-24** 429 / `Retry-After` | Medium | **behoben** | `ApiRateLimiter` (neu) + `ApiRateLimitFilter` (`:42`, hinter der Security-Kette) + `ProblemFactory.RATE_LIMIT_EXCEEDED`. Die Antwort traegt **`Retry-After`** als **ganzzahlige Sekundenzahl >= 1** — genau wie der eingefrorene Vertrag es beschreibt (`openapi.yaml:1826-1830`: `type: integer, minimum: 1`; `RateLimitExceeded` mit `headers.Retry-After` + `Problem`, `:1986-1994`). Belegt: `SecurityGapIT.the_request_limit_must_answer_with_429` = **gruen**, `ApiRateLimitIT` (4 Tests) = **gruen**, neu `ApiRateLimitHardeningTest` (5 Tests) = **gruen**. Der Vertrag blieb **eingefroren** (`git diff --stat -- docs/api/` ist leer). |
| **S-25** CI (Secret-/SCA-Scan, OIDC) | Medium | **behoben** (strukturell) → **S-32 (neu, Medium)** | `.github/workflows/deploy.yml`: Secret-Scan (gitleaks v8.30.1, gepinntes Image, `:51-54`), SCA-Scan (osv-scanner v2.7.0, `:56-61`), Java-Build **inkl. Coverage-Gate** (`:64-65`), Typpruefung/Lint/Vertragspruefung/Unit-Tests (`:79-83`), Build (`:85-86`), **End-to-End-Tests** (`:88-93`). Registry-Anmeldung mit dem **Token des Laufs** (`:107` `secrets.GITHUB_TOKEN`) statt des statischen `REGISTRY_TOKEN`; `permissions: packages: write, id-token: write` (`:32-35`). **Keine Secrets in Logs** — die Geheimnisse werden ausschliesslich als `env` gesetzt (`:128-134`, `:156-162`), kein Schritt gibt Werte aus. **Aber:** der SCA-Schritt hat **keine Baseline** und schlaegt damit auf **jedem** Lauf an → **S-32**. |
| **S-26** Gepinnte `apt`-Pakete | Low | **behoben** | `Dockerfile:36-42` — `curl=${CURL_VERSION}` (7.81.0-1ubuntu1.30) und `tini=${TINI_VERSION}` (0.19.0-1) mit Vorgabewerten; `rm -rf /var/lib/apt/lists/*`. Belegt: `ContainerHardeningIT.the_apt_packages_are_pinned` und `…the_pinned_versions_have_a_default` = **gruen**. |
| **S-27** Produktionsprofil | Low | **behoben** | `src/main/resources/application-production.yml` (neu) mit `echobox.crypto.key-required: true` (`:25`); `Dockerfile:63` setzt `SPRING_PROFILES_ACTIVE=production` (nicht mehr `staging`); die Pipeline setzt das Profil je Umgebung (`deploy.yml:127` staging, `:155` production). Belegt: `ContainerHardeningIT` (3 Tests) = **gruen**. |
| **A-1** Coverage-Gate | QA | **behoben** | `pom.xml:224-277` — JaCoCo 0.8.13 mit `prepare-agent` (`append=true`), `report` und **`check`** in der Phase `verify`: `BUNDLE` / `LINE` / `COVEREDRATIO` / `minimum 0.80`. Der Build **bricht ab**, wenn die Zeilenabdeckung unter 80 % faellt; ein Loeschschritt in `initialize` (`:183-206`) verhindert, dass alte Daten mitgezaehlt werden. **Beleg:** `mvn -B verify` → „All coverage checks have been met."; gemessen **86,22 % Zeilen**. `ContainerHardeningIT.the_build_enforces_the_coverage_threshold` = **gruen**. |
| **A-6 / B-17** Uebergangstabelle | QA | **behoben** (Code **und** Doku) | Code: `DeliveryStateMachine.TRANSITIONS` (`:33-41`) enthaelt jetzt `pending -> delayed` und `scheduled -> delayed`; `onRateLimited` (`:126-131`) leitet sich aus `canTransition(status, DELAYED)` ab — die Tabelle ist die eine Quelle der Wahrheit, `canTransition` ist kein toter Code mehr. Doku: `docs/architecture/domain_model.md` §3.3 fuehrt beide Uebergaenge (Commit `28396e0`). *Rest:* der Javadoc der Klasse verweist noch auf eine ausstehende Nachziehung → **S-34** (Low). |

**Ergebnis der Nachpruefung:** 8 Befunde **behoben**, 2 **teilweise behoben**
(S-20 → S-28, S-21 → S-29), 2 QA-Befunde **behoben**. **Kein Befund der ersten Runde
ist offen geblieben** — jeder wurde behoben oder durch einen belegten neuen Befund
derselben Sache ersetzt.

---

## 3. Die neuen Befunde dieser Runde

| ID | Schwere | Datei:Zeile | Befund | Ausnutzbarkeit | Empfehlung |
|---|---|---|---|---|---|
| **S-28** | **[SECURITY] High** | `src/main/java/com/echobox/tenant/TenantParameterFilter.java:160-167` (Grenze), `:82` (Content-Length-Vorpruefung) | **Die Grenze des Request-Bodies haengt am Medientyp.** `isBodyRelevant()` akzeptiert nur Bodies, deren Content-Type mit `application/json` beginnt. Spring MVC liest aber auch `application/*+json` (`MappingJackson2HttpMessageConverter` unterstuetzt `application/json` **und** `application/*+json`). Eine Anfrage mit `Content-Type: application/vnd.custom+json` faellt damit an **beiden** Grenzen vorbei: die Vorpruefung gegen `Content-Length` (`:82`) und das begrenzte Lesen (`:98`) — der Body wird vom Message-Converter **vollstaendig** in den Heap gelesen. | Ein angemeldeter Mandant (jede Rolle) sendet einen POST `/api/v1/events` mit `Content-Type: application/vnd.custom+json` und einem beliebig grossen JSON-Body (belegt: 2.097.298 Byte → **202**, 20.971.604 Byte → **202**). Der Dienst allokiert den Body vollstaendig im Heap des **geteilten** JVM → `OutOfMemoryError` → **Ausfall fuer alle Mandanten** (Availability ueber die Mandantengrenze hinweg). Ein gueltiges Token genuegt. Nebenwirkung: derselbe Weg umgeht auch die Mandantenparameter-Pruefung im Body — **nachweislich ohne Ausnutzung**, weil `fail-on-unknown-properties: true` ein unbekanntes Feld mit 422 ablehnt und der Mandant aus dem Token stammt. | Die Grenze darf **nicht** am Medientyp haengen. Sie muss fuer **jeden** Body gelten, den ein Converter liest (serverseitig begrenzt lesen unabhaengig vom Content-Type, oder eine Container-Grenze, die auch fuer Chunked gilt). Zusaetzlich: der Dienst sollte nur `application/json` akzeptieren (`consumes` je Operation), damit `application/*+json` gar nicht erst gelesen wird. **Schritt 6/7.** |
| **S-29** | **[SECURITY] Medium** | `src/main/java/com/echobox/config/SecurityConfiguration.java:126` ↔ `src/main/resources/application.yml:107`, `.github/workflows/deploy.yml:161` | **Die Zielgruppen-Pruefung faellt bei leerer Konfiguration still aus (fail-open).** `validators()` fuegt den Audience-Validator nur hinzu, wenn die Audience **nicht leer** ist. `audience: ${ECHobox_SECURITY_AUDIENCE:echobox}` — der Vorgabewert greift nur bei einer *nicht gesetzten* Variablen; ein **leerer** Secret-Wert (typisch: leeres Secret in der Umgebung `production`) ergibt `''` → die Pruefung faellt aus. Anders als beim Schluessel (S-27, `key-required`) gibt es **keinen** Startfehler. | Ein Token desselben Identity Providers, das fuer einen **anderen** Client ausgestellt wurde, wird angenommen, sofern `organisation_id` und eine Rolle passen. Der Angreifer braucht ein gueltig signiertes Token — gerade **nicht** eines, das fuer EchoBox ausgestellt wurde. Voraussetzung ist ein leerer Secret-Wert (Fehlkonfiguration, die der Dienst stillschweigend akzeptiert). | Ein `required`-Schalter analog zu `key-required` (`echobox.security.audience-required`, im Produktionsprofil `true`) **oder** ein Startabbruch, wenn die Audience leer ist. Der Javadoc „Leer: die Zielgruppe wird nicht geprueft" beschreibt den Ist-Zustand, nicht die gewollte Zusage. **Schritt 6/7.** |
| **S-30** | **[SECURITY] Medium** | `src/main/java/com/echobox/common/GlobalExceptionHandler.java:230` (kein eigener Fall) | **Ein nicht unterstuetzter Medientyp ergibt HTTP 500 mit Stacktrace statt eines 4xx.** `HttpMediaTypeNotSupportedException` hat keinen `@ExceptionHandler`; sie faellt in den allgemeinen Handler, der `LOG.error(..., exception)` protokolliert (`:232`) und `internal_error` (500) antwortet. Betroffen sind u.a. `text/plain` und `multipart/form-data` (der Multipart-Resolver ist per `spring.servlet.multipart.enabled: false` abgeschaltet, `application.yml:60-65`). | Ein Aufrufer, der bewusst einen nicht unterstuetzten Medientyp sendet, erzeugt **unbegrenzt** 500er **mit Stacktrace** — Protokollverstärkung, die Protokolldatei fuellt sich, das Monitoring sieht Serverfehler. Der Vertrag kennt fuer einen Fehler des Aufrufers nur 4xx (400 `malformed_request`); 500 ist laut Vertrag „unerwarteter Fehler". Kein Angriff auf Daten oder Mandanten. | `HttpMediaTypeNotSupportedException` (und `HttpMediaTypeNotAcceptableException`) auf **400 `malformed_request`** abbilden — der eingefrorene Vertrag kennt kein 415, ein Vertragswechsel waere ein Rücksprung zu Schritt 2. Stacktraces nur fuer unerwartete Fehler loggen. **Schritt 6/7.** |
| **S-31** | **[SECURITY] Medium** | `pom.xml:52` (`<tomcat.version>10.1.55</tomcat.version>`), `:18-25` (Kommentar) | **Der gepinnte Container liegt unter der Fix-Linie von drei aktuell veroeffentlichten Tomcat-Advisories.** OSV meldet fuer `tomcat-embed-core:10.1.55`: **GHSA-9xv2-5v5q-p794** (DIGEST-Authentifizierung, Capture-Replay, CVSS Critical), **GHSA-gcx9-497g-6cp6** (Security-Constraint-Bypass, CVSS Critical), **GHSA-h3x4-894j-xpx5** (FORM-Authentifizierung, CVSS Critical) — alle drei betreffen „10.1.0-M1 through 10.1.57" und sind erst in **10.1.58** behoben. 10.1.58 wurde nie veroeffentlicht; die erste verfuegbare Version darueber ist **10.1.59** (10.1.61 ist die juengste 10.1.x). Der Pin wurde ausdruecklich eingefuehrt, um die erreichbaren Advisories zu beheben — er deckt diese drei nicht mehr ab. | **In dieser Konfiguration nicht ausnutzbar:** der Dienst nutzt ausschliesslich Bearer-Token (keine Container-Authentifizierung, keine `<login-config>`), es gibt **keine** `<security-constraint>` (eingebetteter Container ohne `web.xml` — `find src/main -name web.xml` liefert nichts) und kein FORM-Login. Der Befund ist ein Härtungs- und Wahrheitsbefund: die eigene Untergrenze ist unterschrritten, und der SCA-Gate (S-32) meldet die drei bei jedem Lauf. | `<tomcat.version>` auf mindestens **10.1.59** heben und die beiden Regressionstests mitziehen (`DependencyHardeningTest.java:34`, `ContainerHardeningIT.java:201` — beide halten 10.1.55 fest). **Schritt 6/7.** |
| **S-32** | **[SECURITY] Medium** | `.github/workflows/deploy.yml:56-61` | **Der neue SCA-Gate schlaegt auf jedem Lauf an — es fehlt eine Baseline.** `osv-scanner-action@v2.7.0` mit `--recursive ./` beendet den Lauf mit **Exit-Code 1**, sobald Advisories gefunden werden (osv-scanner v2.7.0, `cmd/osv-scanner/internal/cmd/run.go`: `case errors.Is(err, osvscanner.ErrVulnerabilitiesFound): return 1`). Es gibt **keine** Ignorierliste (`osv-scanner.toml` mit `[[IgnoredVulns]]`). Der aktuelle Baum traegt **20** Advisories, der Frontend-Lockfile **10** (2 Critical in `tinypool`/`vitest`, 6 High) — alle in `devDependencies`. | Kein Angriffspfad — ein **Betriebsdefekt**: der Push auf `main` scheitert im SCA-Schritt, das Image wird nicht gebaut und nicht gepusht. Der Gate ist vorhanden, aber nicht kalibriert: er unterscheidet nicht zwischen „neu" und „seit langem bekannt". | Eine Baseline einfuehren (`osv-scanner.toml` mit `[[IgnoredVulns]]`-Eintraegen fuer die im Audit als nicht erreichbar bewerteten Advisories, je mit Begruendung und `ignoreUntil`), damit nur **neue** Advisories den Lauf scheitern lassen; `--ignore-dev` fuer die devDependencies des Frontends. **Schritt 9** (gemeinsam mit Schritt 6/7 fuer S-31). |
| **S-33** | **[SECURITY] Low** | `src/main/java/com/echobox/tenant/TenantTransactionRunner.java:92`, `src/main/java/com/echobox/common/GlobalExceptionHandler.java:230`, `src/main/java/com/echobox/tenant/ApiRateLimitFilter.java:85-98` | **Ein Token ohne `organisation_id` ergibt 500 mit Stacktrace und umgeht das Anfragelimit.** `TenantContext.organisationId()` liefert `""`; `setTenant` wirft `IllegalArgumentException` (fail-closed) → allgemeiner Handler → **500 `internal_error`** + Stacktrace im Protokoll. Zugleich zaehlt `ApiRateLimitFilter.organisationIdOf()` nur Anfragen mit nicht-leerem Mandanten — genau diese Anfragen werden **nicht** gezaehlt. | Setzt ein gueltig signiertes Token ohne Mandanten voraus — das kann nur der Identity Provider (oder eine Kompromittierung des IdP) erzeugen. Wirkung: Protokollrauschen und ein nicht greifendes Anfragelimit fuer genau die Anfragen, die am wenigsten zaehlbar sind. | Ein Token ohne Mandanten ist ein Fehler des Aufrufers → **401** (nicht 500). Der Rate-Limit-Filter sollte von einem stabilen Schluessel der Identitaet ausgehen (`sub` bzw. Mandant mit Fallback), nicht vom Mandanten allein. **Schritt 6/7.** |
| **S-34** | **[SECURITY] Low** | `src/main/java/com/echobox/delivery/DeliveryStateMachine.java:28-29` und `:120-121` | **Veralteter Javadoc.** Der Kommentar sagt: „Die Tabelle in `docs/architecture/domain_model.md` §3.3 wird in Schritt 2 nachgezogen (A-6 / B-17)". Die Tabelle **ist** nachgezogen (Commit `28396e0`). | Keiner — reine Dokumentationspflege. Code und Doku sind inhaltlich konsistent; nur der Verweis im Code ist veraltet. | Die beiden Saetze streichen oder in die Vergangenheitsform umschreiben. **Schritt 6/7.** |

Strukturierte Fehlerbeschreibungen (Pfad, Zeile, Ursache, Nachweis, Empfehlung, Owner):
`docs/qa/bugs/BUG-S-28.json` … `BUG-S-34.json` (neu) sowie `BUG-S-19.json` … `BUG-S-24.json`
und `BUG-B-17.json` (Runde 1).

---

## 4. Die zehn Prüfungen des Skills — erneut und vollständig

### 4.1 Authentifizierung — **kein Critical/High; zwei Medium (S-29, S-33)**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Token-Pflicht auf **jeder** geschuetzten Route | **erfuellt** | `SecurityConfiguration.java:69-72` — `anyRequest().authenticated()`; `permitAll` gilt **nur** fuer `/health`, `/health/ready`, `/actuator/health`, `/actuator/health/**`. Das entspricht dem Vertrag (`openapi.yaml:1666`, `:1700` — `security: []` genau fuer diese beiden Operationen) und dem Health-Check des Containers (`Dockerfile:67-68`). **Kein** `permitAll` auf einem ganzen Praefix ohne Begruendung. |
| **401** ohne Token (nicht 403, nicht 500) | **erfuellt** | `SecurityConfiguration.java:170-175` — eigener `AuthenticationEntryPoint` → 401 mit dem `Problem`-Schema. Belegt: `SecurityHardeningIT` → `S-HARD-1` (**gruen**). |
| Ablauf der Token (`exp`) | **erfuellt** | `SecurityConfiguration.java:125` (`JwtTimestampValidator`) **und** `:138-146` (`expiryRequired()` — `exp` ist **Pflicht**). Belegt: `SecurityGapTest.a_token_without_an_expiry_must_be_rejected` = **gruen**, `S-HART-4` = **gruen**. |
| **Nicht vor** (`nbf`) | **erfuellt** | `JwtTimestampValidator` prueft `exp` **und** `nbf` (Spring-Standard). |
| Signaturpruefung | **erfuellt** | Der Decoder prueft die Signatur gegen den JWKS des Identity Providers. Belegt: `S-HARD-1` (gueltiges Token wird angenommen), `S-HART-5` (**gruen**, fremder Schluessel wird abgelehnt). |
| Zielgruppe (**Audience**) | **teilweise erfuellt → S-29** | `SecurityConfiguration.java:154-163` — der Token muss die konfigurierte Audience tragen. **Aber:** bei leerer Konfiguration faellt die Pruefung still aus (`:126`). Belegt: `JwtAudienceHardeningTest` (4 Tests, gruen) gegen `SecurityGapRound2Test` → **LUECKE (Befund S-29)**, „Expecting code to raise a throwable". |
| Algorithmus / kein `none` | **erfuellt** | `NimbusJwtDecoder.withJwkSetUri(...)` (`:111`) ist auf RS256 beschraenkt. Belegt: `S-HART-2` (**gruen**, `alg: none` abgelehnt), `S-HART-3` (**gruen**, HS256 mit dem oeffentlichen RSA-Schluessel als HMAC-Secret abgelehnt). |
| Aussteller (`iss`) | **kein Befund** | Der Decoder prueft `iss` nicht. Der Vertrag fordert keinen Issuer (`openapi.yaml:1726-1733` nennt nur `organisation_id` und die Rollen); der IdP ist ueber die JWKS-Adresse gebunden. |
| Keine selbstgebaute Kryptografie | **erfuellt** | `javax.crypto`: `Cipher.getInstance("AES/GCM/NoPadding")` (`crypto/PayloadCipher.java:40,84`), `Mac.getInstance("HmacSHA256")` (`crypto/SignatureService.java:32`, `common/CursorCodec.java:28`), `MessageDigest.isEqual` fuer zeitkonstante Vergleiche (`SignatureService.java:68`, `CursorCodec.java:84`). Keine Eigenimplementierung. |

### 4.2 Autorisierung / IDOR — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| **IDOR:** jede Objekt-Route prueft den **Besitz**, nicht nur die Rolle | **erfuellt** | `endpoint/EndpointService.java` (`requireOwnEndpoint` → 404), `event/EventService.java:155`, `protocol/ProtocolQueryService.java:107`, `delivery/DeliveryService.java:131` — alle Objektzugriffe laufen ueber `repository.findById(organisationId, id)`. Belegt: `ForeignResourceIsolationIT` (11 Tests, gruen). |
| Reihenfolge **erst Mandant, dann Rolle** | **erfuellt** | `EndpointService.java:102-103`, `:121-123`, `:142-144`, `:164-166` — `requireOwnEndpoint(...)` **vor** der Rollenpruefung. Ein fremdes Objekt ergibt 404, eine falsche Rolle im eigenen Mandanten ergibt 403. |
| Standard ist **verweigern** | **erfuellt** | `anyRequest().authenticated()`; 404 vor 403; keine Operation faellt auf „erlauben" zurueck. |
| Keine `permitAll` auf ganzen Praefixen ohne Begruendung | **erfuellt** | Siehe 4.1 — genau drei Health-Pfade, mit Vertragsbezug. |
| Mandanten-Scoping auf **Query-Ebene** | **erfuellt** | **Jede** Query traegt `organisation_id = :organisationId` (z.B. `endpoint/EndpointRepository.java:69,72,86`; `delivery/DeliveryRepository.java:66,83,112,117,130,139,150,185,206,217`). Darueber erzwacht PostgreSQL die Trennung: RLS auf **allen zehn** fachlichen Tabellen (`schema.sql:888-961`), `USING` **und** `WITH CHECK`, fail-closed. **Kein** `BYPASSRLS` (`schema.sql:62-78`). Belegt: `RowLevelSecurityIT` (4), `TenantIsolationIT` (6) — gruen. |
| Mandantenuebergreifende Sicht nur ueber die **drei** Eigentuemerfunktionen | **erfuellt** | `schema.sql:513,592,680` — genau drei `SECURITY DEFINER`-Funktionen, alle fail-closed, `search_path` fest verdrahtet, `EXECUTE` nur fuer `echobox_app`, `REVOKE … FROM PUBLIC` (`schema.sql:1102-1120`). |
| Der Mandant stammt **ausschliesslich** aus dem Token | **erfuellt** (mit einer Luecke, die **keine** Ausnutzung ist) | `TenantContext.java:46-53`; ein mitgeschickter Mandantenparameter wird mit 422 abgelehnt (`TenantParameterFilter.java:112-120`). Der Weg `application/*+json` umgeht die **Body**-Pruefung (S-28), aber ein unbekanntes Feld `organisation_id` wird wegen `fail-on-unknown-properties: true` mit 422 abgelehnt — ein Mandantenwechsel ist **nicht** moeglich. |

### 4.3 Secrets — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Secret-Scan ueber `src/`, `tests/`, `.github/`, `Dockerfile` | **kein Fund** | `grep -rInE "(api[_-]?key\|secret\|password\|token\|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ Dockerfile` → **2** Treffer, beide Test-Fixtures: `tests/unit/frontend/EndpointsPage.test.tsx:177` und `:224` (`'s3cr3t-rotationswert'` — derselbe Wert, den auch `AbstractApplicationIT.DEFAULT_SECRET` verwendet). Keine produktiven Geheimnisse. |
| Keine Schluessel in der **Konfiguration** | **erfuellt** | `application.yml`, `application-staging.yml`, `application-production.yml` enthalten ausschliesslich `${ECHobox_…}`-Verweise und Betriebswerte. Belegt: `EnvironmentSeparationIT.no_configuration_contains_a_hardcoded_secret` (gruen). |
| Keine Schluessel im **Image** | **erfuellt** | `Dockerfile:63-65` — `ENV` enthaelt nur Profil, Port und JVM-Optionen; der `COPY` (`:51`) bringt nur das JAR. |
| Keine Schluessel in der **CI** | **erfuellt** | `deploy.yml:128-134`, `:156-162` — nur **Namen** (`${{ secrets.… }}`), keine Werte im Repository; kein Schritt gibt Werte aus. |
| `.env` in `.gitignore` | **erfuellt** | `.gitignore` — `.env` (und `/test-results/`, `/playwright-report/` aus dem Root, was der offene Punkt S-23 der Runde 1 war — **erledigt**). |
| **Keine Schluessel in der Git-Historie** | **erfuellt** | `git log --all -p -- . ':!docs' ':!.agent' \| grep -InE "(api[_-]?key\|secret\|password\|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{12,}"` → **keine** Treffer. |
| Fehlermeldung bei fehlender Konfiguration | **erfuellt, mit einer Ausnahme → S-29** | `CoreConfiguration.java:58-64` (ohne Schluessel und `key-required: true` bricht der Start ab), `:71-79` (falsche Laenge → klarer Startfehler), `SecurityConfiguration.java:105-110` (ohne JWKS-Adresse wird jedes Token abgelehnt; belegt durch `S-HARD-6`). **Ausnahme:** die **Audience** faellt bei leerer Konfiguration still aus → **S-29**. |

### 4.4 Injection — **kein Fund**

| Teilpruefung | Ergebnis | Beleg |
|---|---|---|
| Alle Queries **parametriert** | **erfuellt** | Ausschliesslich `NamedParameterJdbcTemplate` mit benannten Parametern (`common/SqlParameters.java`); `grep -rcE '\+ *"'` ueber die fuenf Repositories → **0** Treffer. Kein String-Concatenation in SQL. |
| Keine dynamisch zusammengesetzten SQL-Fragmente | **erfuellt** | Die einzigen `String.format`-Stellen sind die statischen Spaltenlisten `DeliveryRepository.java:74,133,164` — das Argument ist die **Konstante** `RUN_COLUMNS` (`:48-53`). Alle `ORDER BY`-Klauseln sind fest verdrahtet. |
| Kein `Runtime.exec` / `os.system` mit Eingabe | **erfuellt** | `grep -rn "Runtime.getRuntime\|ProcessBuilder\|os.system\|exec(" src/main/java/` → **keine** Treffer. |
| Kein Path-Traversal | **erfuellt** | `grep -rn "new File(\|Files\.\|Path.of\|FileInputStream" src/main/java/` → **keine** Treffer. Der Dienst verarbeitet **keine** Dateipfade aus Eingaben; die Ziel-URL wird als `URI` benutzt (`delivery/DeliveryHttpClient.java:103`). |

### 4.5 Ausgabe / XSS — **kein Fund; ein Medium (S-30)**

| Teilpruefung | Ergebnis | Beleg |
|---|---|---|
| Kein `dangerouslySetInnerHTML` / `v-html` / `\| safe` | **erfuellt** | `grep -rn "dangerouslySetInnerHTML\|v-html\|innerHTML\|eval(\|new Function(" src/main/frontend/src/ src/main/java/` → **keine** Treffer. React escaped standardmaessig. |
| Fehlermeldungen geben **keine** internen Details preis | **erfuellt** | Es gibt **eine** Fehlerabbildung (`common/GlobalExceptionHandler.java`), die ausschliesslich `code`, `title`, `status`, `detail` und `instance` (den **Pfad**, ohne Query-String) erzeugt (`common/ProblemFactory.java:69-90`); Stacktraces, SQL und Pfade tauchen nicht auf. `application.yml:72-74` — `include-stacktrace: never`, `include-message: never`, `include-binding-errors: never`. Belegt: `S-HARD-5` (**gruen**) — die Fehlerantwort enthaelt weder `com.echobox` noch `org.springframework` noch `Exception` noch `/home/` noch `SELECT`. |
| Nutzlast und Secrets erscheinen in keiner Ausgabe | **erfuellt** | `event/EventRepository.java` gibt niemals `payload_ciphertext` aus; `endpoint/EndpointResponse` fuehrt kein Secret; `PayloadCipher.Ciphertext.toString()` (`crypto/PayloadCipher.java:177-179`) gibt **nur Laengen** aus. |
| Fehler des Aufrufers werden als Serverfehler gemeldet | **NICHT erfuellt → S-30** | `HttpMediaTypeNotSupportedException` faellt in den allgemeinen Handler (`GlobalExceptionHandler.java:230`) → **500** + Stacktrace im Protokoll. Belegt: `SecurityGapRound2IT` → **LUECKE (Befund S-30)**, „Expecting actual: 500 to be between: [400, 499]". |

### 4.6 Session und Transport — **kein Fund**

| Teilpruefung | Ergebnis | Beleg |
|---|---|---|
| Sitzung | **keine** | `SecurityConfiguration.java:68` — `SessionCreationPolicy.STATELESS`; `:67` — CSRF abgeschaltet (bei einem reinen Bearer-Token ohne Cookie im Browser korrekt und im Code kommentiert). |
| Cookie-Flags der Oberflaeche | **erfuellt** | `src/main/frontend/src/lib/session.ts:115-123` — `httpOnly: true`, `sameSite: 'lax'`, `path: '/'`, `secure` **nur** bei `NODE_ENV === 'production'`. Das Bearer-Token liegt im **httpOnly**-Cookie, **nicht** im Browser-Speicher (`:9-17`). |
| Security-Header | **erfuellt** | Die Spring-Security-Defaults der Kette liefern `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` und `Cache-Control: no-cache, no-store, …`. Belegt: `S-HARD-2` (**gruen**) — alle drei Header werden geprueft. |
| HSTS / TLS | **offene Frage (Q-1)** | HSTS setzt Spring Security nur bei **HTTPS**-Anfragen. Der Container spricht **HTTP** auf Port 8080 (`Dockerfile:55`); TLS wird **vor** dem Dienst terminiert. → **Q-1**, nicht befundbar ohne die Bereitstellungstopologie (Schritt 9). |
| CORS | **erfuellt (vertretbar)** | Es existiert **keine** CORS-Konfiguration → kein `Access-Control-Allow-Origin: *`, keine CORS-Antwortkopfzeilen. Der Browser ruft den Dienst nicht direkt: die Oberflaeche ist ein Next.js-Server, der ueber einen **Same-Origin-Rewrite** weiterleitet (`next.config.mjs`, `rewrites()` auf `/api/v1/:path*`) und den Dienst **serverseitig** spricht (`apiClient.ts`). Voraussetzung fuer den Betrieb: `ECHOBOX_API_BASE_URL` muss eine **interne** Adresse sein. |

### 4.7 Rate-Limiting und Ressourcen — **ein High (S-28), ein Medium (S-30), ein Low (S-33)**

| Teilpruefung | Ergebnis | Beleg |
|---|---|---|
| Ratenbegrenzung **ausgehend** (je Endpunkt) | **erfuellt** | F-01: 60 Versuche je Endpunkt und Minute, konfigurierbar (`application.yml:135-139`), Zaehler je Fenster in `schema.sql:345-356`, durchgesetzt in `delivery/RateLimitGuard.java` und `delivery/EndpointRateLimiter.java`. Belegt: `RateLimiterTest` (6), `RateLimitedDeliveryIT` (5) — gruen. |
| Ratenbegrenzung **eingehend** (API) | **erfuellt** | `ApiRateLimitFilter` (`:42`, hinter der Security-Kette) + `ApiRateLimiter` (je Mandant und Fenster). **Grenzen:** `echobox.rate-limit.limit` (Vorgabe 240) und `window-seconds` (60) — aus der Konfiguration, kein Wert im Code verdrahtet. **Fehlerverhalten:** **429** `rate_limit_exceeded` mit dem `Problem`-Schema des Vertrags. **`Retry-After`:** ganzzahlige Sekundenzahl >= 1, **nie kuerzer** als die Wartezeit bis zum Fensterende (belegt: `ApiRateLimitHardeningTest.the_retry_after_is_a_waiting_time_in_seconds`, gruen) — vertragskonform (`openapi.yaml:1826-1830`). **Mandantenbezug:** der Schluessel ist die `organisation_id` aus der Identitaet (`ApiRateLimitFilter.java:85-98`); ein zweiter Mandant bleibt unberuehrt (belegt: `ApiRateLimitIT.another_tenant_is_not_affected`, gruen). **Bypass:** nicht angemeldete Anfragen werden nicht gezaehlt (401 vor dem Filter) — der Gesundheitsendpunkt bleibt erreichbar (belegt: `ApiRateLimitIT.the_health_endpoint_is_not_rate_limited`, gruen); ein Token **ohne** Mandant wird ebenfalls nicht gezaehlt → **S-33**. **Randbedingung:** der Zaehler liegt im Speicher der Instanz — bei *n* Instanzen wirkt das Limit *n*-fach (im Schritt nicht aenderbar, braucht ein Schema → Schritt 9). **Fenster:** fest (nicht gleitend) — am Fensterwechsel ist ein Doppelpeak moeglich. |
| **1-MB-Grenze** der Nutzlast durchgaengig | **erfuellt** | `event/PayloadSize.java:17` (1 048 576 Byte), geprueft in `event/EventService.java:123-124` → HTTP 413; DB-Constraints `events_payload_size_ck` und `events_payload_ciphertext_ck` (`schema.sql:181-184`); Vertrag `openapi.yaml:86`. Belegt: `S-HARD-3` (**gruen**). |
| Grenze des **Request-Bodies** | **NICHT durchgaengig → S-28 (High)** | Die Grenze von 2 MiB (`PayloadSize.java:20`) wird nur fuer `application/json` geprueft (`TenantParameterFilter.java:160-167`) und **nur** gegen `Content-Length` (`:82`) — bei Chunked greift sie nicht, und `application/*+json` umgeht sie **voellig**. Belegt: `RequestBodyLimitIT` (gruen) gegen `SecurityGapRound2IT` → **LUECKE (Befund S-28)**, „expected: 413 but was: 202" (2.097.298 Byte) und **202** bei 20.971.604 Byte. |
| Greift die Grenze bei **chunked**, **Multipart**, **gzip**, **ohne `Content-Length`**? | **gemessen** | *chunked*: grenzt (gruen). *ohne `Content-Length`* (HTTP/1.1, Body bis Connection-Close): `getContentLengthLong()` = -1 → `readBounded` begrenzt → 413. *Multipart*: `spring.servlet.multipart.enabled: false` (`application.yml:60-65`) → **kein** Multipart-Resolver, der Container parst den Body nicht; gemessen: **500** (→ S-30), **kein** Speicherzugriff. *gzip*: **nichts** dekomprimiert Request-Bodies (kein `ContentEncoding`-Filter im Code) → Jackson scheitert an den Rohbytes → **400 `malformed_request`** (gemessen), **keine** GZIP-Bomb-Gefahr. |
| Unbegrenzte Paginierung verhindert | **erfuellt** | `limit` ist auf 1…200 begrenzt (`endpoint/EndpointController.java:179-187`), der Vertrag ebenso (`openapi.yaml:1752-1760`). Die Paginierung ist **cursor-basiert** (kein Offset) — `common/CursorCodec.java`, HMAC-signiert, zeitkonstant geprueft (`:84`). |
| Antwort-Body des Empfaengers begrenzt | **erfuellt** | `delivery/DeliveryHttpClient.java:135-187` — `LimitedBodySubscriber`, 1 MiB. Belegt: `SecurityGapTest` (gruen), `DeliveryResponseLimitTest` (3 Tests, gruen). |
| Zeitlimits der Zustellung | **erfuellt** | `application.yml:142-143` — 5 s Verbindungs-, 10 s Lese-Timeout (ADR-008). |

### 4.8 Abhaengigkeiten und Container — **ein Medium (S-31), ein Medium (S-32)**

| Teilpruefung | Ergebnis | Beleg |
|---|---|---|
| SCA-Scan (bekannte kritische/hohe Luecken) | **20 Advisories, davon 3 im HTTP/1.1-Pfad erreichbar → behoben** | `mvn -B dependency:tree` (110 Koordinaten) → OSV `querybatch` (2026-10-22): **96 → 20**. Die **drei** zuvor erreichbaren (CVE-2026-24880, CVE-2025-48988, CVE-2025-48989) sind **nicht** mehr enthalten — der Container ist auf 10.1.55 gehoben. Die verbleibenden 20 sind mit Begruendung je Advisory in **Anhang A** bewertet. **Neue** Tomcat-Advisories unterhalb der Fix-Linie → **S-31**. |
| Frontend-Abhaengigkeiten | **kein Laufzeitbefund** | `npm audit --json` (2026-10-22): 10 Meldungen — **2 Critical** (`tinypool`, `vitest`), **6 High** (`eslint-config-next`, `@next/eslint-plugin-next`, `braces`, `fast-glob`, `micromatch`, `postcss`), **2 Moderate** (`vitest`, `next`). **Alle** betreffen ausschliesslich `devDependencies`; die **Laufzeit**-Abhaengigkeiten (`next` 15.5.28, `react`/`react-dom` 19.2.9) sind **nicht** betroffen. Ausnutzbar ist ein Fehler im Test-Werkzeug nur, wer bereits Schreibrecht auf dem Repository hat. |
| Container laeuft als **non-root** | **erfuellt** | `Dockerfile:45-46` (uid/gid 10001, ohne sudo/Root-Rechte), `:53` (`USER 10001:10001`). |
| **Gepinnte** Basis-Image-Tags | **erfuellt** | `Dockerfile:17` (`maven:3.9.9-eclipse-temurin-21`), `:29` (`eclipse-temurin:21.0.5_11-jre-jammy`) — feste Versionen, **kein** `latest`. |
| Multi-Stage, minimale Images | **erfuellt** | `Dockerfile:17-26` (Build) / `:29-71` (Laufzeit). Im Laufzeitimage liegt nur `/app/echobox.jar` — kein Maven, kein Quellcode, kein `.git`. |
| Keine unnoetigen Ports | **erfuellt** | Nur `EXPOSE 8080` (`Dockerfile:55`); die Datenbank ist nicht im Image. |
| Keine Secrets im Image | **erfuellt** | Siehe 4.3. |
| Health-Check | **erfuellt** | `Dockerfile:67-68` — `curl -fsS http://127.0.0.1:8080/health`. |
| Gepinnte `apt`-Pakete | **erfuellt** | `Dockerfile:36-42` — `curl=${CURL_VERSION}`, `tini=${TINI_VERSION}` mit Vorgabewerten. |
| CI: keine Secrets in Logs | **erfuellt** | `deploy.yml:128-134`, `:156-162` — Geheimnisse ausschliesslich als `env`; kein Schritt gibt Werte aus. |
| CI: OIDC statt statischer Keys | **erfuellt** | `deploy.yml:99-107` — `password: ${{ secrets.GITHUB_TOKEN }}` (kurzlebig, pro Lauf), `permissions: packages: write, id-token: write` (`:32-35`). Der statische `REGISTRY_TOKEN` wird **nicht mehr verwendet**. |
| CI: Secret-Scan, SCA-Scan, Oberflaechen-Tests | **vorhanden** (S-25) | `deploy.yml:51-54` (gitleaks v8.30.1), `:56-61` (OSV-Scanner v2.7.0), `:64-65` (Java inkl. Coverage-Gate), `:79-93` (Typpruefung, Lint, Vertragspruefung, Unit-Tests, Build, Playwright). **Aber:** der SCA-Schritt ist nicht kalibriert → **S-32**. |
| Geschuetzte Branches | **nicht pruefbar** | Branch-Schutz ist eine Einstellung des Repository-Hosts, nicht des Codes → **offene Frage Q-2** (Schritt 9). |

### 4.9 Logging und Datenschutz — **kein Fund; ein Low (S-33)**

| Teilpruefung | Ergebnis | Beleg |
|---|---|---|
| Keine Passwoerter, Tokens oder **vollstaendigen Nutzlasten** in Logs | **erfuellt** | Alle Logstellen in `src/main/java/` (grep `LOG.info\|warn\|error`) geben **nur Metadaten** aus: Kennungen, Mandant, Zaehler, Zeiten, Status (`event/EventService.java:214`, `delivery/DeliveryDispatcher.java:247`, `endpoint/EndpointService.java:173`). **Kein** Klartextpayload, **kein** Secret, **kein** Token. FR-012 / AC-007-2 sind eingehalten. |
| Audit-Protokoll ohne Nutzlastinhalt | **erfuellt** | `schema.sql:383-399` — `details JSONB` ohne Nutzlast; `common/AuditLogRepository.java:30-37` — ausschliesslich Metadatenfelder. |
| Retention dokumentiert | **erfuellt** | F-02: 30 Tage je Datenart, Anker `last_attempt_at`; `ADR-012`, `application.yml:153-160`, `schema.sql:364-380` (Override je Mandant). Die Durchsetzung laeuft mandantenuebergreifend ueber die Eigentuemerfunktion (`schema.sql:680-858`) in zwei Stufen (markieren → Purge). |
| Recht auf Loeschung | **technisch abgebildet, fachlich offen** | Die Purge-Stufe loescht Ereignisse, Zustelllaeufe und Versuche physisch (`schema.sql:833-841`); die Kaskade ueber `ON DELETE CASCADE` (`schema.sql:240-243`) nimmt die Protokollzeilen mit. Ein **mandantenspezifisches Sofort-Loesch-Kommando** gibt es nicht → **offene Frage Q-3** (Recht/Fachbereich, Gate 2), **kein** Befund. |
| Karenzzeit R-01 / E-13 | **offen** | `schema.sql:704-709` — **kein** Vorgabewert; ohne gesetzten Wert wird mit 0 Tagen gerechnet. Bewusst so entschieden und im Vertrag nicht festgeschrieben → offener Punkt des Fachbereichs (Gate 2). |
| Fehler des Aufrufers erzeugen Stacktraces im Protokoll | **NICHT erfuellt → S-30 / S-33** | `GlobalExceptionHandler.java:232` protokolliert bei jedem unerwarteten Fehler den Stacktrace — auch fuer Fehler, die der Aufrufer verursacht hat (nicht unterstuetzter Medientyp → 500; Token ohne Mandant → 500). |

### 4.10 OWASP Top 10 (Querpruefung)

| ID | Kategorie | Ergebnis | Beleg / Befund |
|---|---|---|---|
| **A01** | Broken Access Control | **kein Fund** | RLS auf allen zehn fachlichen Tabellen (`schema.sql:888-961`), `WITH CHECK`, fail-closed, kein `BYPASSRLS`; Besitzpruefung auf **jeder** Objekt-Route (404 vor 403); drei mandantenuebergreifende Eigentuemerfunktionen, fail-closed und ohne Klartextprojektion; `permitAll` nur auf den drei Health-Pfaden. Belegt: `ForeignResourceIsolationIT` (11), `RoleCapabilityMatrixIT` (84), `RowLevelSecurityIT` (4), `TenantIsolationIT` (6) — alle gruen. Die **Restluecke** E-12 (die Applikationsrolle kann `app.cross_tenant` setzen) ist dokumentiert (`docs/adr/README.md:96`) und ein offener Punkt, kein neuer Befund. |
| **A02** | Cryptographic Failures | **kein Fund** | AES-256-GCM mit 12-Byte-Nonce und 128-Bit-Tag (`crypto/PayloadCipher.java:80-106`), HMAC-SHA256-Fingerprint mit abgeleitetem Teilschluessel (`:119-138`), zeitkonstante Vergleiche (`SignatureService.java:68`, `CursorCodec.java:84`), Schluessel **ausserhalb** der Datenbank, keine Eigenimplementierung. **S-22 ist behoben** (Schluessellaenge wird geprueft, kein AES-128-Downgrade). **S-29** betrifft die Konfiguration der Token-Pruefung, nicht die Kryptografie. |
| **A03** | Injection | **kein Fund** | Ausschliesslich benannte Parameter; keine SQL-Konkatenation; keine Befehlsausfuehrung; keine Dateipfade aus Eingaben. Belegt: grep ueber alle fuenf Repositories (0 Treffer). |
| **A04** | Insecure Design | **kein Fund; ein High (S-28) und eine Vertragsluecke (S-30)** | Das Design trennt Mandant (RLS) und Rolle (Anwendung) in der **richtigen** Reihenfolge; die mandantenuebergreifende Sicht ist auf drei schmale Funktionen begrenzt; die Replay-Regel ist ueber eine DB-Constraint durchgesetzt (`schema.sql:244-245`). **S-28:** eine Sicherheitsgrenze, die an einer **Eingabevariante** (dem Medientyp) haengt, ist per Konstruktion umgehbar — der Entwurf muss die Grenze an die **Nutzlastgrenze des Containers** binden, nicht an eine Medienpruefung. **S-30:** der Vertrag kennt keinen 4xx fuer einen nicht unterstuetzten Medientyp — die Fehlerabbildung ist eine Design-Entscheidung, die im Code fehlt. |
| **A05** | Security Misconfiguration | **kein Fund; zwei Medium (S-29, S-30) und ein Low (S-33)** | Fehlermeldungen ohne internes Detail, `include-stacktrace: never`, `management.endpoints.web.exposure.include: health,info,metrics` mit `show-details: never` (`application.yml:85-92`) — `/actuator/metrics` bleibt **hinter** der Authentifizierung (belegt durch `S-HARD-7`). **S-29:** fail-open bei leerer Audience-Konfiguration. **S-30:** 500 statt 4xx. **S-33:** 500 bei Token ohne Mandant. **S-26/S-27 sind behoben** (gepinnte Pakete, Produktionsprofil). |
| **A06** | Vulnerable and Outdated Components | **Befund → S-31 (Medium)** | 96 → **20** Advisories; die 3 zuvor erreichbaren sind behoben. Die verbleibenden sind **in dieser Konfiguration nicht erreichbar** (Anhang A) — mit einer Ausnahme: der **gepinnte** Container liegt unter der Fix-Linie von drei Critical-Advisories (**S-31**). |
| **A07** | Identification and Authentication Failures | **kein Fund; ein Medium (S-29)** | 401 statt 403/500; RS256-only; `none` und HS256 abgelehnt; Ablauf (`exp`) **und** `nbf` geprueft, `exp` Pflicht; kein eigener Passwortpfad. **S-29:** die Zielgruppen-Pruefung faellt bei leerer Konfiguration still aus. |
| **A08** | Software and Data Integrity Failures | **kein Fund** | Signaturverfahren und Header-Name sind im Vertrag eingefroren und durch einen **Vertragstest** abgesichert (`tests/contract/SignatureContractTest.java`, 6 Tests, gruen); der Idempotenzvergleich laeuft ueber einen gezielten HMAC-Fingerprint statt ueber einen ungeschuetzten Hash (ADR-007); die Idempotenz-Constraint liegt in der Datenbank (`schema.sql:173-174`). Beide Schema-Spiegel sind **byte-identisch** mit `.agent/knowledge/schema.sql` (geprueft). |
| **A09** | Security Logging and Monitoring Failures | **kein Fund; zwei Low (S-30, S-33)** | Audit-Protokoll fuer die nachvollziehbaren Aktionen (`app.audit_log`, `schema.sql:383-399`); Betriebsprotokoll enthaelt nur Metadaten; Readiness/Health melden den Zustand, nicht die Daten. **S-30/S-33:** Fehler des Aufrufers landen als 500 mit Stacktrace im Protokoll — das Protokoll ist dann kein verlaessliches Signal mehr. |
| **A10** | Server-Side Request Forgery (SSRF) | **kein Fund; eine offene Frage** | Die Ziel-URL ist **Eingabe** des Mandanten — das ist das Produkt. Die wirksamen Begrenzungen: Schema **nur** `https` (`endpoint/EndpointTargetUrl.java:52-55`, zusaetzlich DB-Constraint `endpoints_target_url_ck`, `schema.sql:100-101`), **keine** eingebetteten Zugangsdaten (`:56-60`, DB-Constraint `:102-103`), **keine** Weiterleitung (`delivery/DeliveryHttpClient.java:80`, `Redirect.NEVER`), **kein** Trust-all bei TLS, Zeitlimits (5 s / 10 s), Antwortgrenze 1 MiB. **Offene Frage Q-4:** ein Filter gegen interne Adressbereiche (Link-Local `169.254.0.0/16`, RFC1918) fehlt — fuer ein Webhook-Produkt ist das eine **bewusste** Entscheidung, aber eine Sichtbarkeitsfrage und keine belegte Luecke → **kein Befund**. |

---

## Anhang A — SCA: die 20 verbleibenden Advisories und ihre Erreichbarkeit

Scan: `mvn -B dependency:tree` (110 Koordinaten) → OSV `querybatch` (2026-10-22).
**Gesamt 20** (Runde 1: 96) — 3 Tomcat, 11 jackson, 2 spring-webmvc, 2 commons-compress,
1 log4j-api, 1 postgresql.

### A.1 Tomcat (`tomcat-embed-core:10.1.55`) — 3 → **S-31**

| Advisory | Schwere | Betroffen | Behoben in | Erreichbarkeit in dieser Konfiguration |
|---|---|---|---|---|
| GHSA-9xv2-5v5q-p794 | Critical | 10.1.0-M1 … 10.1.57 | **10.1.58** | **nicht erreichbar** — die DIGEST-Authentifizierung des Containers wird nicht benutzt (ausschliesslich Bearer-Token; keine `<login-config>`; `find src/main -name web.xml` → nichts). |
| GHSA-gcx9-497g-6cp6 | Critical | 10.1.0-M1 … 10.1.57 | **10.1.58** | **nicht erreichbar** — der Bypass setzt eine `<security-constraint>`-Konfiguration voraus; es gibt keine (eingebetteter Container ohne Deployment-Descriptor). |
| GHSA-h3x4-894j-xpx5 | Critical | 10.1.0-M1 … 10.1.57 | **10.1.58** | **nicht erreichbar** — die FORM-Authentifizierung wird nicht benutzt. |

> **Nicht erreichbar heisst nicht unbedenklich.** Der Pin wurde gerade eingefuehrt, um
> die erreichbaren Advisories zu beheben — er liegt jetzt unter der Fix-Linie dieser
> drei. → **S-31** (Medium). 10.1.58 wurde nie veroeffentlicht; die erste verfuegbare
> Version darueber ist **10.1.59**.

### A.2 Jackson (`jackson-core:2.21.4`, `jackson-databind:2.21.4`) — 11 → nicht erreichbar

| Advisory-Familie | Anzahl | Warum nicht erreichbar |
|---|---|---|
| ReDoS: quadratisches Backtracking in `NumberInput.PATTERN_FLOAT` (GHSA-p6pp-m3f8-5c89, HIGH) | 1 | **Empirisch gemessen.** Die Schwachstelle braucht eine sehr lange Ziffernfolge, die der Jackson-Parser aber **vor** dem Regex abbricht: `StreamReadConstraints.maxNumberLength` (Vorgabe 1000) wirft `StreamConstraintsException`. Gemessen mit `jackson-core 2.21.4` auf dem Klassenpfad des Projekts: `looksLikeValidNumber("1"*40000 + ".")` = 9 305 ms — **aber nur bei direktem Aufruf**; der Parse-Pfad bricht bei 10 000 Ziffern mit `StreamConstraintsException` ab (30 ms). **Der Restrisiko-Punkt Q-5 der Runde 1 ist damit erledigt: nicht erreichbar, empirisch belegt.** |
| `UTF8DataInputJsonParser` ohne `maxErrorTokenLength` (GHSA-7hhh-6rmp-j9qf, HIGH) | 1 | Betrifft nur `createParser(DataInput)`; Spring MVC liest ueber `InputStream`/`byte[]` (grep: keine `DataInput`-Nutzung im Code). |
| `@JsonView`-Umgehungen (GHSA-5gvw-p9qm-jgwh, GHSA-mhm7-754m-9p8w) | 2 | Setzen `@JsonView` **und** `@JsonUnwrapped` bzw. `@JsonTypeInfo(EXTERNAL_PROPERTY)` voraus — grep ueber `src/main/java/`: **keine** Treffer. |
| `@JsonTypeInfo` / `DefaultBaseTypeLimitingValidator` (GHSA-gx83-3vf8-gh7j, GHSA-wv8q-qhhj-9h54) | 2 | Kein `@JsonTypeInfo`, kein `PolymorphicTypeValidator`, kein `activateDefaultTyping` im Code. |
| `InetAddress`/`InetSocketAddress` (GHSA-vvgp-rfg2-7rr6) | 1 | Kein `java.net.InetAddress`/`InetSocketAddress` in den Request-Bodies. |
| `java.nio.file.Path` / `FileSystemProvider` (GHSA-wjgm-6hv5-3cvf) | 1 | Kein `java.nio.file.Path` in den Request-Bodies. |
| `Duration`/`XMLGregorianCalendar` (GHSA-q4xh-88c3-wmh7) | 1 | Keine solchen Feldtypen in den DTOs (die Treffer in `EchoBoxProperties` sind Spring-Konfiguration, kein Request-Body). |
| quadratische Forward-Reference-Aufloesung (GHSA-cxp5-3px4-pw24) | 1 | Setzt `@JsonIdentityInfo` voraus — nicht verwendet. |
| case-insensitive `@JsonIgnoreProperties` (GHSA-5jmj-h7xm-6q6v) | 1 | Setzt `@JsonIgnoreProperties` auf Properties voraus — nicht verwendet. |

### A.3 Spring MVC (`spring-webmvc:6.2.19`) — 2 → nicht erreichbar

| Advisory | Schwere | Erreichbarkeit |
|---|---|---|
| GHSA-pc63-qcmh-9cmg (XsltView → SSRF/RCE) | Critical | Setzt `XsltView` und eine `/**`-View-Aufloesung voraus — der Dienst liefert **ausschliesslich JSON** (`@RestController`), es gibt keine View-Aufloesung (grep: keine `@Controller`, kein `ViewResolver`, kein `XsltView`). |
| GHSA-j9f9-w8pj-32f8 (SSE-Stream-Verfälschung mit View-Fragmenten) | Critical | Setzt Server-Sent Events **mit** View-Fragmenten voraus — der Dienst hat keine SSE-Endpunkte (grep: kein `SseEmitter`, kein `text/event-stream`). |

### A.4 Uebrige — 4 → nicht erreichbar (Test-/Build-Werkzeug, Datenbankweg, Logging)

| Advisory | Schwere | Erreichbarkeit |
|---|---|---|
| GHSA-4265-ccf5-phj5, GHSA-4g9r-vxhx-9pgx (`commons-compress:1.24.0`) | Moderate | Pack200-/DUMP-Dateien — betrifft Datei- bzw. Archivverarbeitung im Test-/Build-Werkzeug, nicht den Request-Pfad. |
| GHSA-qv9r-c865-cp47 (`log4j-api:2.24.3`) | Moderate | Log4j ist **nicht** die Logging-Implementierung (logback 1.5.34); betrifft `MapMessage.asJson()`. |
| GHSA-j92g-9f8w-j867 (`postgresql:42.7.11`) | High | Kanalbindung (`channelBinding=require`) ist nicht konfiguriert; der Angriff setzt einen Angreifer auf dem **Datenbankweg** voraus. |

> **Hinweis zur Vollstaendigkeit:** die Erreichbarkeits-Einstufung ist eine
> komponentenbezogene Analyse des tatsaechlichen Stacks (Konfiguration,
> Dependency-Tree, Routing, gemessenes Laufzeitverhalten). Sie ist kein Ersatz
> fuer einen Hersteller-Scan des fertigen Images in der Zielumgebung — der
> gehoert in **Schritt 9**. Der **SCA-Gate der Pipeline** (S-25) meldet alle 20
> bei jedem Lauf — ohne Baseline schlaegt er dabei immer an (**S-32**).

---

## Anhang B — Nachweiskette: jeder Befund ist festgenagelt

| Befund | Test / Check | Datei | Ergebnis | Status |
|---|---|---|---|---|
| **S-28** (Body-Grenze umgehbar) | `LUECKE (Befund S-28)` | `tests/integration/security/SecurityGapRound2IT.java` | schlaegt fehl: „expected: 413 but was: **202**" (2.097.298 Byte); **202** bei 20.971.604 Byte | **Beleg der Lücke** |
| **S-29** (Audience fail-open) | `LUECKE (Befund S-29)` | `tests/unit/security/SecurityGapRound2Test.java` | schlaegt fehl: „Expecting code to raise a throwable" | **Beleg der Lücke** |
| **S-30** (500 statt 4xx) | `LUECKE (Befund S-30)` | `tests/integration/security/SecurityGapRound2IT.java` | schlaegt fehl: „Expecting actual: 500 to be between: [400, 499]" | **Beleg der Lücke** |
| **S-31** (Container-Pin) | `LUECKE (Befund S-31)` | `tests/unit/security/SecurityGapRound2Test.java` | schlaegt fehl: „Expecting value to be true but was false" (10.1.55 < 10.1.59) | **Beleg der Lücke** |
| S-19 (Abhaengigkeiten) | OSV-Abfrage ueber `mvn dependency:tree` | `pom.xml:27-52` | 96 → **20**; die 3 erreichbaren CVEs **nicht** mehr enthalten | **belegt durch Check** |
| S-20 (chunked Body) | `a_chunked_request_body_above_two_mebibytes_must_be_rejected` | `tests/integration/security/SecurityGapIT.java` | gruen | **Abwehr belegt** |
| S-20 (chunked unter der Grenze) | `RequestBodyLimitIT` (2 Tests) | `tests/integration/security/RequestBodyLimitIT.java` | gruen | **Abwehr belegt** |
| S-21 (Audience, `exp`) | `JwtAudienceHardeningTest` (4 Tests) | `tests/unit/security/JwtAudienceHardeningTest.java` | gruen | **Abwehr belegt** |
| S-22 (Schluessellaenge) | `CryptoKeyLengthTest` (4 Tests) | `tests/unit/crypto/CryptoKeyLengthTest.java` | gruen | **Abwehr belegt** |
| S-23 (Antwort-Body) | `DeliveryResponseLimitTest` (3 Tests) | `tests/unit/delivery/DeliveryResponseLimitTest.java` | gruen | **Abwehr belegt** |
| S-24 (429 + `Retry-After`) | `the_request_limit_must_answer_with_429`, `ApiRateLimitIT` (4) | `tests/integration/security/ApiRateLimitIT.java` | gruen | **Abwehr belegt** |
| S-24 (Zaehlereigenschaften) | `ApiRateLimitHardeningTest` (5 Tests) | `tests/unit/security/ApiRateLimitHardeningTest.java` | gruen | **Abwehr belegt (neu)** |
| S-25 (Pipeline) | Dateipruefung + YAML-Parsing | `.github/workflows/deploy.yml` | Datei umgesetzt und geparst; **Ausfuehrung** nicht moeglich (kein Runner) | **belegt durch Check** |
| S-26 / S-27 / A-1 | `ContainerHardeningIT` (8 Tests) | `tests/integration/deployment/ContainerHardeningIT.java` | gruen | **Abwehr belegt** |
| Abwehr: 401, Header, 1-MiB, 2-MiB, keine internen Details, Mandantenparameter, nur Health ohne Token | `S-HARD-1` … `S-HARD-7` | `tests/integration/security/SecurityHardeningIT.java` | gruen | **Abwehr belegt** |
| Abwehr: RS256 / kein `none` / Ablauf / Signatur / kein IdP | `S-HART-1` … `S-HART-7` | `tests/unit/security/JwtDecoderHardeningTest.java` | gruen | **Abwehr belegt** |

---

## Anhang C — Offene Fragen (keine Befunde)

| ID | Frage | Owner | Stand Runde 2 |
|---|---|---|---|
| **Q-1** | Terminiert TLS der vorgeschaltete Proxy? Wer setzt HSTS? Der Container selbst spricht HTTP auf Port 8080. | Schritt 9 / Betrieb | **offen** |
| **Q-2** | Sind die Branches geschuetzt (kein direkter Push auf `main`)? Aus dem Repository **nicht** pruefbar. | Schritt 9 / Betrieb | **offen** |
| **Q-3** | „Recht auf Loeschung": reicht die fristbasierte Purge-Stufe, oder braucht es ein mandantenspezifisches Sofort-Loesch-Kommando? | Fachbereich + Recht, Gate 2 | **offen** |
| **Q-4** | Soll ein Endpunkt-Ziel in interne Adressbereiche (Link-Local, RFC 1918) verboten werden? Fuer ein Webhook-Produkt legitim — eine **Sichtbarkeits**-, keine Sicherheitsfrage. | Fachbereich + Plattform, Gate 2 | **offen** |
| **Q-5** | Restrisiko `jackson-core` (quadratisches Backtracking bei sehr langen Dezimalzahlen). | — | **erledigt**: **nicht erreichbar** — `StreamReadConstraints.maxNumberLength` (1000) bricht den Zahlentoken vorher ab; empirisch gemessen (Anhang A.2). |
| **Q-6** | Bedingungen von CVE-2026-22732 (Spring Security 6.3.4). | — | **erledigt**: Spring Security ist jetzt **6.5.11** (`mvn dependency:tree`). |
| **Q-7** | Schluesselverwaltung nach ADR-003 §2 (je Mandant ein DEK) — ist der **globale** Schluessel die gewollte Vereinfachung? | Architektur + Fachbereich, Gate 2 | **offen** (haengt an O-04) |
| **Q-8** | Der Zaehler des Anfragelimits liegt **je Instanz** im Speicher — bei *n* Instanzen wirkt das Limit *n*-fach. Eine instanzuebergreifende Grenze braucht ein Datenbankschema. | Schritt 9 (Schema) | **offen** — im Schritt 6/7 nicht aenderbar (Schema) |
| **Q-9** | Der Rate-Limit-Filter zaehlt nur Anfragen **mit** Mandant; nicht angemeldete Anfragen (401) und Token ohne Mandant werden nicht gezaehlt. Soll es eine **globale** (mandantenuebergreifende) Schranke geben? | Fachbereich + Betrieb, Gate 2 | **offen** — S-33 nennt den konkreten Restfall |

---

## Anhang D — Was dieser Audit **nicht** behauptet

* Er aendert **keine** Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/` oder `docs/requirements/`. Der eingefrorene Vertrag
  (`docs/api/openapi.yaml`, seit `8627bb6`) ist **unveraendert**
  (`git diff --stat -- docs/api/` ist leer).
* Er behauptet **nicht**, dass die 17 nicht erreichbaren Advisories unbedenklich sind —
  sie sind fuer **diese** Konfiguration nicht erreichbar, mit Begruendung je Advisory
  in Anhang A; ein Scan des fertigen Images in der Zielumgebung gehoert in Schritt 9.
* Er behauptet **nicht**, dass die beiden ungemaessigten Rate-Limit-Residuen (Q-8, Q-9)
  ein Befund sind — sie sind Randbedingungen des Betriebs und offene Fragen.
* Er ersetzt **nicht** die Freigabe durch den Menschen: **GATE 2** steht aus, und die
  Empfehlung dieses Audits lautet **blockieren**.

---

*Erzeugt in Schritt 8 (Sicherheit & Compliance), zweite Runde. Kanonisches Artefakt:
`.agent/artifacts/security-audit.md`.*
