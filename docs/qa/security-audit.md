# Security-Audit — EchoBox (Schritt 8)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · Audit **read-only** (keine Datei in `src/`,
> `docs/api/`, `docs/adr/`, `docs/architecture/`, `.agent/knowledge/` oder
> `docs/requirements/` geändert).
> Skill: `aisdlc-security-scan` (alle 10 Prüfungen) · Gate: **GATE 2** — QA 0 Critical,
> Security 0 Critical und 0 High.

---

## 1. Gesamtergebnis

| Schwere | Anzahl | Befund-IDs |
|---|---|---|
| **Critical** | **0** | — |
| **High** | **2** | S-19, S-20 |
| **Medium** | **5** | S-21, S-22, S-23, S-24, S-25 |
| **Low** | **2** | S-26, S-27 |

### Freigabe-Empfehlung: **BLOCKIEREN**

Die Gate-Bedingung verlangt **0 Critical und 0 High**. Es liegen **2 High** vor.
Beide sind **belegt** (Datei:Zeile bzw. ausgeführter Check) und **ausnutzbar**
beschrieben — kein Verdacht, keine Vermutung:

* **S-20** — der Request-Body wird bei `Transfer-Encoding: chunked` **ohne
  Größengrenze** in den Speicher gelesen. Beleg: `mvn verify` → der
  Gap-Test `SecurityGapIT.a_chunked_request_body_above_two_mebibytes_must_be_rejected`
  schlägt fehl mit **„expected: 413 but was: 202"** — ein 2-MiB-Body wurde
  **akzeptiert**.
* **S-19** — 96 versionsgebundene Advisories im Abhängigkeitsbaum, davon **3 im
  HTTP/1.1-Pfad erreichbar** (u. a. CVE-2026-24880, Request/Response-Smuggling).
  Beleg: OSV-Abfrage gegen `mvn dependency:tree` (Anhang A).

**Konsequenz:** kein Deployment. Rücksprung zu **Schritt 6/7** (Implementierung und
Refactoring) ist erforderlich; der Vertrag `docs/api/openapi.yaml` bleibt dabei
**eingefroren** — die Behebung von S-24 berührt den Vertrag und braucht daher einen
Rücksprung zu **Schritt 2** (siehe S-24).

---

## 2. Befundtabelle

| ID | Schwere | Datei:Zeile | Befund | Ausnutzbarkeit | Empfehlung |
|---|---|---|---|---|---|
| **S-19** | **[SECURITY] High** | `pom.xml:16` (Spring Boot 3.3.5 → `tomcat-embed-core:10.1.31`, `spring-security:6.3.4`, `spring-framework:6.1.14`, `jackson:2.17.2`) | Der Abhängigkeitsbaum trägt **96** versionsgebundene Advisories (9 Critical, 36 High, 35 Moderate, 16 Low). **Drei davon sind im HTTP/1.1-Pfad dieses Dienstes erreichbar**: CVE-2026-24880 (Request/Response-Smuggling via invalid chunk extension, behoben in 10.1.52), CVE-2025-48988 (DoS in multipart upload, behoben in 10.1.42), CVE-2025-48989 („made you reset", behoben in 10.1.44). Die übrigen 93 sind in dieser Konfiguration **nicht** erreichbar (Begründung je Advisory in Anhang A). | **Smuggling:** jeder HTTP/1.1-Client sendet einen Request mit ungültiger Chunk-Extension; Tomcat und ein vorgeschalteter Proxy (Ingress/Load-Balancer) können die Request-Grenzen unterschiedlich auslegen → eine Anfrage wird in den Kontext eines anderen Aufrufs verschoben (Umgehung einer Proxy-Authentifizierung, Vergiftung von Antworten). **Multipart-DoS:** ein angemeldeter Mandant sendet `Content-Type: multipart/form-data`; der von Spring Boot automatisch registrierte `StandardServletMultipartResolver` veranlasst Tomcat, den Body zu parsen — ohne wirksame Ressourcenbegrenzung → Belastung von Worker-Threads und Heap. | Spring Boot auf eine Version heben, die Tomcat ≥ 10.1.55 mitbringt (Spring Boot ≥ 3.5.15 bzw. 4.0.7 — im Rahmen des Rücksprungs zu prüfen). **Rücksprung Schritt 6/7.** |
| **S-20** | **[SECURITY] High** | `src/main/java/com/echobox/tenant/TenantParameterFilter.java:70` und `:81` | Die Grenze für die Größe des Request-Bodies (2 MiB, `PayloadSize.MAX_REQUEST_BODY_BYTES`) wird **ausschließlich** gegen `request.getContentLengthLong()` geprüft. Bei `Transfer-Encoding: chunked` (ohne `Content-Length`) liefert die Methode `-1`; `-1 > 2097152` ist falsch → **die Grenze greift nicht**. Zeile 81 liest den Body danach mit `request.getInputStream().readAllBytes()` **ohne jede Grenze** in den Heap. Tomcat setzt hier keine wirksame Grenze: `maxPostSize` gilt nur für Form-POSTs, `max-swallow-size` (4 MB, `application.yml:68`) greift nur beim Schlucken eines **nicht** gelesenen Rests. | Ein angemeldeter Mandant (jede Rolle, jeder Mandant) sendet einen chunked POST mit einem sehr großen JSON-Body. Der Dienst allokiert den Body vollständig im Heap → `OutOfMemoryError` im **geteilten** JVM → **Ausfall für alle Mandanten** (Availability über die Mandantengrenze hinweg). Der Security-Filter läuft vor dem Mandantenfilter (`DEFAULT_FILTER_ORDER + 10`), der Angriff setzt also ein gültiges Token voraus. | Body-Grenze unabhängig von `Content-Length` durchsetzen: serverseitig **begrenzt** lesen (höchstens N Bytes, danach abbrechen) **und** den Container zwingen, die Grenze zu erzwingen (`server.tomcat.max-swallow-size`/`maxPostSize` greifen nur bei Form-POSTs — zusätzlich `Content-Length` **verlangen** oder die Chunked-Übertragung ablehnen). |
| **S-21** | **[SECURITY] Medium** | `src/main/java/com/echobox/config/SecurityConfiguration.java:90` | `NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()` wird **ohne** einen eigenen `JwtValidator` erzeugt. Der Default-Validator (`JwtValidators.createDefault()`) prüft **nur** die Zeitstempel; eine **Audience** wird nicht geprüft und ein `exp` ist **nicht erforderlich** (ein Token ohne `exp` ist unbegrenzt gültig). | Ein Token, das derselbe Identity Provider für eine **andere** Zielgruppe (`aud`) ausgestellt hat, wird von EchoBox angenommen, sofern er `organisation_id` und eine Rolle enthält. Ein fremder Client desselben IdP (z. B. ein anderes Mandantenportal) erhält damit Zugriff auf die EchoBox-API. Ein abgelaufenes bzw. widerrufenes Token ohne `exp` bleibt gültig. | `JwtValidators.createDefaultWithValidators(...)` mit einem `JwtClaimValidator<List<String>>` auf `aud` setzen; `aud` als Konfiguration (`echobox.security.audience`) ergänzen; `exp` verpflichtend machen. |
| **S-22** | **[SECURITY] Medium** | `src/main/java/com/echobox/config/CoreConfiguration.java:69` | Die **Länge** des `ECHobox_CRYPTO_KEY` wird nicht geprüft: `new SecretKeySpec(decode(configured), "AES")` akzeptiert jede gültige AES-Länge. Ein **16-Byte**-Schlüssel wird stillschweigend zu **AES-128** statt der in ADR-003 §1 festgelegten **AES-256**. | Ein Operator liefert — durch Fehlkonfiguration des Secret-Stores oder durch einen Tippfehler in der Basis64-/Hex-Kodierung — einen 16-Byte-Schlüssel. Der Dienst startet **ohne Warnung** und schützt die Nutzlasten mit AES-128; die Zusage aus FR-012 / NFR-004 / ADR-003 („AES-256-GCM") ist nicht mehr eingehalten, ohne dass jemand es bemerkt. | Länge prüfen (exakt 32 Byte) und den Start sonst mit einer klaren Fehlermeldung abbrechen (analog zu `key-required`, `CoreConfiguration.java:58-62`). |
| **S-23** | **[SECURITY] Medium** | `src/main/java/com/echobox/delivery/DeliveryHttpClient.java:53` | Die Antwort des Empfängers wird mit `HttpResponse.BodyHandlers.ofString()` **ohne Größengrenze** gelesen. Der Body wird zwar **nicht** ausgewertet und **nicht** gespeichert (`DeliveryResponse.body()` wird nirgends protokolliert), aber er wird vollständig in den Speicher geladen. | Ein Angemeldeter, der einen Endpunkt auf einen bösartigen Server zeigt, antwortet mit einem sehr großen Body → der Zustell-Worker allokiert ihn vollständig → Speicherdruck im Worker, der die Zustellung stört (der eigene Tenant und — bei gemeinsamem Worker-Heap — weitere Tenants). | `BodyHandlers.limiting(...)` (feste Obergrenze) verwenden und den Body oberhalb der Grenze verwerfen; die Antwort ist vertragsseitig bedeutungslos (nur Statuscode und Ablehnungssignal zählen, `DeliveryResponse`). |
| **S-24** | **[SECURITY] Medium** | `docs/api/openapi.yaml:89` und `:1979-1994` (eingefrorener Vertrag) ↔ `src/main/java/com/echobox/common/ProblemFactory.java:46` | Der **eingefrorene Vertrag** kennt HTTP **429** `rate_limit_exceeded` mit dem Header `Retry-After` als Antwort **jeder** Operation („API-seitiges Anfragelimit (nicht das Endpunkt-Limit aus FR-033)"). Die Implementierung hat **keine** eingehende Ratenbegrenzung: `ProblemFactory.RATE_LIMIT_EXCEASED` wird **nirgends** geworfen, ein `Retry-After` wird **nie** gesetzt (grep über `src/main/java/` und `tests/`: nur die Konstante und die Dokumentation). | Ein angemeldeter Mandant ruft die API beliebig häufig auf (Ereignis-Einlieferung, Listenabfragen) → unbegrenzte Datenbank- und CPU-Last; es gibt **keinen** Mechanismus, die Last zu drosseln, obwohl der Vertrag genau das verspricht. | **Zwei Wege, einer ist zwingend:** (a) die Implementierung nachziehen — Bucket je Mandant/Identität, `Retry-After` setzen, Ablehnung mit `Problem`; oder (b) den Vertrag in **Schritt 2** ändern (429 aus dem Antwortsatz nehmen). Der Vertrag ist eingefroren — eine stillschweigende Änderung in diesem Schritt ist nicht zulässig. |
| **S-25** | **[SECURITY] Medium** | `.github/workflows/deploy.yml:30-31`, `:46`, `:55-56` | Die einzige Pipeline (a) führt **weder** einen Secret-Scan (gitleaks/trufflehog) **noch** einen SCA-Scan **noch** die Tests der Oberfläche aus — sie ruft nur `mvn -B verify` auf; (b) meldet sich an der Registry mit einem **statischen** Token (`secrets.REGISTRY_TOKEN`) an, obwohl der Kommentar der Datei (`:28-29`) den **OIDC-Token** ankündigt, und `permissions:` kein `id-token: write` enthält; (c) `AGENTS.md` („Secrets") verlangt einen Secret-Scan in der CI. | Ein eingeschleuster Commit mit einem Schlüssel wird unbemerkt veröffentlicht (der Secret-Scan fehlt); eine verwundbare Abhängigkeit erreicht das Image ohne Gate. Der statische Registry-Token ist ein langlebiges Geheimnis ohne automatische Rotation — im Unterschied zum OIDC-Token, der pro Lauf kurzlebig ist. | Schritt 9: `gitleaks`/`trufflehog` und ein SCA-Scan (`dependency-check`/OSV) **und** `npm run check` als Gates ergänzen; Registry-Anmeldung auf OIDC umstellen (`permissions: id-token: write` + `docker/login-action` mit `password: ${{ secrets.GITHUB_TOKEN }}`). |
| **S-26** | **[SECURITY] Low** | `Dockerfile:29-31` | `apt-get install -y --no-install-recommends curl tini` **ohne Versionspinning** → die Paketversionen des Laufzeitimages folgen dem Repository-Zustand des Bauzeitpunkts; das Image ist nicht reproduzierbar. | Nur Provenance/Reproduzierbarkeit, kein direkter Angriffsvektor. | Pakete pinnen (`curl=<version>`) oder ein vorab gebautes, gehärtetes Basis-Image verwenden. |
| **S-27** | **[SECURITY] Low** | `src/main/resources/application.yml:86`, `src/main/resources/application-staging.yml:24`, `Dockerfile:46` | Es existiert **kein** `application-production.yml`. `echobox.crypto.key-required` ist nur im Staging-Profil `true`; der Container pinnt `SPRING_PROFILES_ACTIVE=staging`. Startet der Dienst mit dem Profil `production`, greift das **Default**-Profil → `key-required: false` → der Dienst startet mit einem **ephemeren** Schlüssel (Warnung statt Fehler, `CoreConfiguration.java:65-66`). | Nach dem ersten Neustart sind alle Nutzlasten und Endpunkt-Secrets unlesbar (Datenverlust im Ruhezustand); die Signaturen der Empfänger brechen (der HMAC-Schlüssel ist das Endpunkt-Secret). Der Fehler ist still — nur eine Warnung im Protokoll. | `application-production.yml` mit `key-required: true` anlegen und im Bereitstellungs-Deskriptor `SPRING_PROFILES_ACTIVE=production` setzen (Schritt 9). |

---

## 3. Die zehn Prüfungen des Skills

### 3.1 Authentifizierung — **kein Critical/High; ein Medium (S-21)**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Token-Pflicht auf **jeder** geschützten Route | **erfüllt** | `src/main/java/com/echobox/config/SecurityConfiguration.java:60-63` — `anyRequest().authenticated()`. `permitAll` gilt **nur** für `/health`, `/health/ready`, `/actuator/health`, `/actuator/health/**`; das entspricht dem Vertrag (`docs/api/openapi.yaml:1666` und `:1700` — `security: []` genau für diese beiden Operationen) und dem Health-Check des Containers (`Dockerfile:50-51`). **Kein** `permitAll` auf einem ganzen Präfix ohne Begründung. |
| **401** ohne Token (nicht 403, nicht 500) | **erfüllt** | `SecurityConfiguration.java:99-103` — eigener `AuthenticationEntryPoint` → HTTP 401 mit dem `Problem`-Schema. Belegt durch den **neuen** Test `tests/integration/security/SecurityHardeningIT.java` → `S-HARD-1` (**grün**): 401, `code=unauthorized`, `application/problem+json`. Zusätzlich `tests/integration/security/RoleCapabilityMatrixIT.java` (84 Tests, grün). |
| Ablauf der Token | **erfüllt** | Der Decoder setzt den Default-Validator `JwtValidators.createDefault()` (Zeitstempelprüfung). Belegt durch `tests/unit/security/JwtDecoderHardeningTest.java` → `S-HARD-4` (**grün**, abgelaufenes Token wird abgelehnt). |
| Signaturprüfung | **erfüllt** | Der Decoder prüft die Signatur gegen den JWKS des Identity Providers. Belegt durch `S-HARD-1` (gültiges Token wird angenommen) und `S-HARD-5` (**grün**, fremder Schlüssel wird abgelehnt). |
| Zielgruppe (**Audience**) | **NICHT erfüllt → S-21** | Kein Audience-Validator. Belegt durch `tests/unit/security/SecurityGapTest.java` → `LUECKE (Befund S-21)` (**schlägt fehl**): „Expecting code to raise a throwable". |
| Algorithmus / kein `none` | **erfüllt** | Der JWK-Set-Decoder ist auf RS256 beschränkt. Belegt durch `S-HART-2` (**grün**, `alg: none` wird abgelehnt) und `S-HART-3` (**grün**, `HS256` mit dem öffentlichen RSA-Schlüssel als HMAC-Secret — Algorithmus-Verwirrung — wird abgelehnt). |
| Keine selbstgebaute Kryptografie | **erfüllt** | `javax.crypto`: `Cipher.getInstance("AES/GCM/NoPadding")` (`crypto/PayloadCipher.java:40,84`), `Mac.getInstance("HmacSHA256")` (`crypto/SignatureService.java:32`, `common/CursorCodec.java:28`), `MessageDigest.isEqual` für zeitkonstante Vergleiche (`SignatureService.java:68`, `CursorCodec.java:84`). Keine Eigenimplementierung. |

> **Wichtige Lücke der bisherigen Testabdeckung (behoben in diesem Schritt):** die
> Integrationstests benutzen den Post-Processor `jwt()` aus `spring-security-test`
> (`tests/integration/support/Auth.java:46-51`). Er setzt eine **fertige Identität**
> und umgeht damit den Decoder — Signatur, Algorithmus und Ablauf wurden bisher
> **von keinem Test** geprüft. `JwtDecoderHardeningTest` schließt diese Lücke.

### 3.2 Autorisierung / IDOR — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| **IDOR:** jede Objekt-Route prüft den **Besitz**, nicht nur die Rolle | **erfüllt** | `endpoint/EndpointService.java:184-190` (`requireOwnEndpoint` → 404), `event/EventService.java:155`, `protocol/ProtocolQueryService.java:107`, `delivery/DeliveryService.java:131` — alle Objektzugriffe laufen über `repository.findById(organisationId, id)` bzw. `requireOwnEndpoint`, also **mit** dem Mandanten aus der Identität. Belegt: `tests/integration/security/ForeignResourceIsolationIT.java` (11 Tests, grün). |
| Reihenfolge **erst Mandant, dann Rolle** (V-39) | **erfüllt** | `EndpointService.java:102-103`, `:121-123`, `:142-144`, `:164-166` — `requireOwnEndpoint(...)` **vor** `requireEndpointAdministrator()`/`requireSecretRotationRight()`. Ein fremdes Objekt ergibt 404, eine falsche Rolle im eigenen Mandanten ergibt 403. |
| Standard ist **verweigern** | **erfüllt** | `anyRequest().authenticated()`; `notFound` vor `forbidden`; keine Operation fällt auf „erlauben" zurück. |
| Keine `permitAll` auf ganzen Präfixen ohne Begründung | **erfüllt** | Siehe 3.1 — genau drei Health-Pfade, mit Vertragsbezug (AC-023-1) und Dockerfile-Health-Check. |
| Mandanten-Scoping auf **Query-Ebene**, nicht nur im Controller | **erfüllt** | **Jede** Query trägt `organisation_id = :organisationId` (z. B. `endpoint/EndpointRepository.java:69,72,86`; `delivery/DeliveryRepository.java:66,83,112,117,130,139,150,185,206,217`; `event/EventRepository.java`). **Darüber** erzwacht PostgreSQL die Trennung: RLS auf **allen zehn** fachlichen Tabellen (`schema.sql:888-961`), `USING` **und** `WITH CHECK`, `fail-closed` (fehlender Mandant → `current_setting(..., true)` liefert `NULL` → keine Zeile). **Kein** `BYPASSRLS`: `echobox_app`/`echobox_worker` sind `NOLOGIN` ohne `BYPASSRLS` (`schema.sql:62-78`). Belegt: `tests/integration/tenant/RowLevelSecurityIT.java` (4) und `TenantIsolationIT.java` (6), beide grün. |
| Mandantenübergreifende Sicht nur über die **drei** Eigentümerfunktionen | **erfüllt** | `schema.sql:513,592,680` — genau drei `SECURITY DEFINER`-Funktionen, alle **fail-closed** (`current_setting('app.cross_tenant', true) IS DISTINCT FROM 'service_admin'` → `RETURN`), `search_path` fest verdrahtet, `EXECUTE` nur für `echobox_app`, `REVOKE … FROM PUBLIC` (`schema.sql:1102-1120`). Kein vierter Leseweg. |

### 3.3 Secrets — **kein Fund (ein Low: S-27)**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Secret-Scan über `src/`, `tests/`, `.github/`, `Dockerfile` | **kein Fund** | `grep -rInE "(api[_-]?key\|secret\|password\|token\|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ Dockerfile` → **2** Treffer, beide Test-Fixtures: `tests/unit/frontend/EndpointsPage.test.tsx:177` und `:224` (`'s3cr3t-rotationswert'` — derselbe Wert, den auch `AbstractApplicationIT.DEFAULT_SECRET` verwendet). Keine produktiven Geheimnisse. |
| Keine Schlüssel in der **Konfiguration** | **erfüllt** | `src/main/resources/application.yml` und `application-staging.yml` enthalten ausschließlich `${ECHobox_…}`-Verweise und Betriebswerte; die Zeilen 85, 88, 91 sind die einzigen Geheimnis-Quellen und alle leer per Vorgabe. Belegt: `tests/integration/deployment/EnvironmentSeparationIT.java` → `no_configuration_contains_a_hardcoded_secret` (grün). |
| Keine Schlüssel im **Image** | **erfüllt** | `Dockerfile:46-48` — `ENV` enthält nur Profil, Port und JVM-Optionen. Der `COPY` (`:40`) bringt nur das JAR. |
| Keine Schlüssel in der **CI** | **erfüllt** | `.github/workflows/deploy.yml:71-76` und `:95-100` — nur **Namen** (`${{ secrets.… }}`); keine Werte im Repository. |
| `.env` in `.gitignore` | **erfüllt** | `.gitignore:9` — `.env`. |
| **Keine Schlüssel in der Git-Historie** | **erfüllt** | `git log --all -p -- . ':!docs' ':!.agent' \| grep -InE "(api[_-]?key\|secret\|password\|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{12,}"` → **keine** Treffer. |
| Fehlermeldung bei fehlender Konfiguration | **erfüllt, mit einer Ausnahme** | `config/CoreConfiguration.java:58-62` — ohne Schlüssel und `key-required: true` bricht der Start mit `IllegalStateException` ab. `config/SecurityConfiguration.java:85-89` — ohne JWKS-Adresse wirft der Decoder `JwtException("Kein Identity Provider konfiguriert.")`; belegt durch `S-HARD-6` (**grün**). **Ausnahme:** `key-required` ist nur im Staging-Profil `true` → **S-27** (Low). |

### 3.4 Injection — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Alle Queries **parametriert** | **erfüllt** | Ausschließlich `NamedParameterJdbcTemplate` mit benannten Parametern (`common/SqlParameters.java`); `grep -rcE '\+ *"'` über die fünf Repositories → **0** Treffer. Kein String-Concatenation in SQL. |
| Keine dynamisch zusammengesetzten SQL-Fragmente | **erfüllt** | Die einzigen `String.format`-Stellen sind die statischen Spaltenlisten `DeliveryRepository.java:74,133,164` — das Argument ist die **Konstante** `RUN_COLUMNS` (`:48-53`), kein Eingabewert. Alle `ORDER BY`-Klauseln sind fest verdrahtet (grep: 7 Stellen, alle statisch). |
| Kein `Runtime.exec` / `os.system` mit Eingabe | **erfüllt** | `grep -rn "Runtime.getRuntime\|ProcessBuilder\|os.system\|exec(" src/main/java/` → **keine** Treffer. |
| Kein Path-Traversal | **erfüllt** | `grep -rn "new File(\|Files\.\|Path.of\|FileInputStream" src/main/java/` → **keine** Treffer. Der Dienst verarbeitet **keine** Dateipfade aus Eingaben; die Ziel-URL wird als `URI` benutzt (`delivery/DeliveryHttpClient.java:67`). |

### 3.5 Ausgabe / XSS — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Kein `dangerouslySetInnerHTML` / `v-html` / `\| safe` | **erfüllt** | `grep -rn "dangerouslySetInnerHTML\|v-html\|innerHTML\|eval(\|new Function(" src/main/frontend/src/ src/main/java/` → **keine** Treffer. React escaped standardmäßig; die einzige dynamische Textausgabe ist der Kindinhalt. |
| Fehlermeldungen geben **keine** internen Details preis | **erfüllt** | Es gibt **eine** Fehlerabbildung (`common/GlobalExceptionHandler.java`), die ausschließlich `code`, `title`, `status`, `detail` und `instance` (den **Pfad**, ohne Query-String) erzeugt (`common/ProblemFactory.java:69-90`); Stacktraces, SQL und Pfade tauchen nicht auf. `application.yml:64-66` — `include-stacktrace: never`, `include-message: never`, `include-binding-errors: never`. Der 500er nennt nur „Die Anfrage konnte nicht verarbeitet werden." (`GlobalExceptionHandler.java:230-237`). Belegt: `S-HARD-5` (**grün**) — die Fehlerantwort enthält weder `com.echobox` noch `org.springframework` noch `Exception` noch `/home/` noch `SELECT`. |
| Nutzlast und Secrets erscheinen in keiner Ausgabe | **erfüllt** | `event/EventRepository.java` gibt niemals `payload_ciphertext` aus; `endpoint/EndpointResponse` führt kein Secret; `PayloadCipher.Ciphertext.toString()` (`crypto/PayloadCipher.java:177-179`) gibt **nur Längen** aus. |

### 3.6 Session und Transport — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Sitzung | **keine** | `SecurityConfiguration.java:59` — `SessionCreationPolicy.STATELESS`; `:58` — CSRF abgeschaltet (bei einem reinen Bearer-Token ohne Cookie im Browser korrekt und im Code kommentiert). Der Dienst nutzt **keine** HttpSession. |
| Cookie-Flags der Oberfläche | **erfüllt** | `src/main/frontend/src/lib/session.ts:115-123` — `httpOnly: true`, `sameSite: 'lax'`, `path: '/'`, `secure` **nur** bei `NODE_ENV === 'production'`. Das Bearer-Token liegt im **httpOnly**-Cookie, **nicht** im Browser-Speicher (`session.ts:9-17`); der Browser spricht den Dienst **nie** direkt an. |
| Security-Header | **erfüllt** | Die Spring-Security-Defaults der Kette liefern `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` und `Cache-Control: no-cache, no-store, …`. Belegt: `S-HARD-2` (**grün**) — alle drei Header werden geprüft. Es gibt **keine** eigene `headers(...)`-Konfiguration; die Defaults sind aktiv. |
| HSTS / TLS | **offene Frage** | HSTS setzt Spring Security nur bei **HTTPS**-Anfragen. Der Container spricht **HTTP** auf Port 8080 (`Dockerfile:44`), TLS wird **vor** dem Dienst terminiert. Solange der vorgeschaltete Proxy/TLS-Terminierer HSTS nicht setzt, fehlt der Header. → **offene Frage Q-1**, nicht befundbar ohne die Bereitstellungstopologie (Schritt 9). |
| CORS | **erfüllt (vertretbar)** | Es existiert **keine** CORS-Konfiguration → kein `Access-Control-Allow-Origin: *`, keine CORS-Antwortkopfzeilen. Der Browser ruft den Dienst nicht direkt: die Oberfläche ist ein Next.js-Server, der über einen **Same-Origin-Rewrite** weiterleitet (`src/main/frontend/next.config.mjs`, `rewrites()` auf `/api/v1/:path*`) und den Dienst **serverseitig** spricht (`src/main/frontend/src/lib/apiClient.ts` — „Der Browser spricht den Dienst nie direkt an — damit braucht es keine CORS-Regel und kein Token im Browser-Speicher"). **Bewertung:** vertretbar — der Rewrite ist Same-Origin, das Token bleibt httpOnly, und die Browser-Sicht bekommt keinen fremden Origin-Zugriff. Voraussetzung für den Betrieb: `ECHOBOX_API_BASE_URL` muss eine **interne** Adresse sein (Standard `http://localhost:8080`, `next.config.mjs`). |

### 3.7 Rate-Limiting und Ressourcen — **zwei Befunde (S-20 High, S-23/S-24 Medium)**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Ratenbegrenzung **ausgehend** (je Endpunkt) | **erfüllt** | F-01: 60 Versuche je Endpunkt und Minute, konfigurierbar (`application.yml:109-113`), Zähler je Fenster in `schema.sql:345-356`, durchgesetzt in `delivery/RateLimitGuard.java` und `delivery/EndpointRateLimiter.java`. Belegt: `tests/unit/delivery/RateLimiterTest.java` (6) und `tests/integration/delivery/RateLimitedDeliveryIT.java` (5), beide grün. |
| Ratenbegrenzung **eingehend** (API) | **NICHT erfüllt → S-24** | Kein `429` im Code, kein `Retry-After`. Belegt: `LUECKE (Befund S-24)` (**schlägt fehl**), grep: `RATE_LIMIT_EXCEEDED` nur als Konstante. |
| **1-MB-Grenze** durchgängig | **erfüllt** | `event/PayloadSize.java:17` (1 048 576 Byte), geprüft in `event/EventService.java:123-124` → HTTP 413; DB-Constraints `events_payload_size_ck` und `events_payload_ciphertext_ck` (`schema.sql:181-184`); Vertrag: `docs/api/openapi.yaml:86`. Belegt: `S-HARD-3` (**grün**). |
| Grenze des **Request-Bodies** | **NICHT durchgängig → S-20 (High)** | Die Grenze von 2 MiB (`PayloadSize.java:20`) wird nur gegen `Content-Length` geprüft (`TenantParameterFilter.java:70`); bei Chunked-Encoding greift sie nicht, und der Body wird unbegrenzt gelesen (`:81`). Belegt: `S-HARD-4` (**grün**, mit `Content-Length`) gegen `LUECKE (Befund S-20)` (**schlägt fehl**, „expected: 413 but was: 202"). |
| Unbegrenzte Paginierung verhindert | **erfüllt** | `limit` ist auf 1…200 begrenzt (`endpoint/EndpointController.java:179-187`, `EndpointQueryService.MAX_LIMIT = 200`), der Vertrag begrenzt ebenso (`docs/api/openapi.yaml:1752-1760`). Die Paginierung ist **cursor-basiert** (kein Offset) — `common/CursorCodec.java`, HMAC-signiert, zeitkonstant geprüft (`:84`). |
| Antwort-Body des Empfängers begrenzt | **NICHT erfüllt → S-23** | `delivery/DeliveryHttpClient.java:53` — `BodyHandlers.ofString()` ohne Grenze. Belegt: `LUECKE (Befund S-23)` (**schlägt fehl**): „Expecting actual: 5242880 to be less than or equal to: 1048576". |
| Zeitlimits der Zustellung | **erfüllt** | `application.yml:114-117` — 5 s Verbindungs-, 10 s Lese-Timeout (ADR-008). |

### 3.8 Abhängigkeiten und Container — **ein Befund (S-19 High), zwei Low**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| SCA-Scan (bekannte kritische/hohe Lücken) | **Befund → S-19** | OSV-Abfrage (`https://api.osv.dev/v1/querybatch`) über alle 76 Koordinaten aus `mvn dependency:tree`: **96** versionsgebundene Advisories (9 Critical, 36 High, 35 Moderate, 16 Low). **3** davon sind im HTTP/1.1-Pfad erreichbar → S-19. Vollständige Aufstellung mit Erreichbarkeitsbegründung je Advisory in **Anhang A**. |
| Frontend-Abhängigkeiten | **kein Laufzeitbefund** | `npm audit --json` (2026-10-22): 10 Meldungen — **2 Critical** (`tinypool`, `vitest`), **6 High** (`eslint-config-next`, `@next/eslint-plugin-next`, `braces`, `fast-glob`, `micromatch`, `postcss`), **2 Moderate** (`vitest`, `next`). **Alle** betreffen ausschließlich `devDependencies` (`src/main/frontend/package.json` — `vitest`, `tinypool` transitiv, `eslint-config-next`, `postcss` transitiv über `next`); die **Laufzeit**-Abhängigkeiten (`next` 15.5.28, `react`/`react-dom` 19.2.9) sind **nicht** betroffen. Ausnutzbar ist ein Fehler im Test-Werkzeug nur, wer bereits Schreibrecht auf dem Repository hat. **Empfehlung (Schritt 9):** Update von `vitest`/`eslint-config-next` und ein SCA-Gate in der CI. |
| Container läuft als **non-root** | **erfüllt** | `Dockerfile:42` — `USER 10001:10001`; der Benutzer wird mit `groupadd`/`useradd` (`:34-35`) ohne `sudo`/Root-Rechte angelegt. |
| **Gepinnte** Basis-Image-Tags | **erfüllt** | `Dockerfile:14` (`maven:3.9.9-eclipse-temurin-21`) und `:26` (`eclipse-temurin:21.0.5_11-jre-jammy`) — feste Versionen, **kein** `latest`. |
| Multi-Stage, minimale Images | **erfüllt** | `Dockerfile:13-23` (Build) / `:25-54` (Laufzeit). Im Laufzeitimage liegt nur `/app/echobox.jar` (`:40`) — kein Maven, kein Quellcode, kein `.git`. |
| Keine unnötigen Ports | **erfüllt** | Nur `EXPOSE 8080` (`Dockerfile:44`); die Datenbank ist nicht im Image. |
| Keine Secrets im Image | **erfüllt** | Siehe 3.3. |
| Health-Check | **erfüllt** | `Dockerfile:50-51` — `curl -fsS http://127.0.0.1:8080/health`. |
| CI: keine Secrets in Logs | **erfüllt** | `deploy.yml:71-76,95-100` — die Geheimnisse werden als Umgebungsvariablen gesetzt und **nicht** ausgegeben; die Schritte `:77` und `:101` geben nur einen erklärenden Text aus. |
| CI: OIDC statt statischer Keys | **NICHT erfüllt → Teil von S-25** | `deploy.yml:55-56` — `password: ${{ secrets.REGISTRY_TOKEN }}` (statisch), obwohl `:28-29` den OIDC-Token ankündigt. |
| Geschützte Branches | **nicht prüfbar** | Branch-Schutz ist eine Einstellung des Repository-Hosts, nicht des Codes → **offene Frage Q-2** (Schritt 9). |

### 3.9 Logging und Datenschutz — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Keine Passwörter, Tokens oder **vollständigen Nutzlasten** in Logs | **erfüllt** | Alle 30 Logstellen in `src/main/java/` (grep `LOG.info|warn|error`) geben **nur Metadaten** aus: Kennungen, Mandant, Zähler, Zeiten, Status (`event/EventService.java:214` — „Ereignis {} im Mandant {} angenommen ({} Abonnenten)", `delivery/DeliveryDispatcher.java:247` — Versuch/Status, `endpoint/EndpointService.java:173` — Secret-Rotation auf Version N). **Kein** Klartextpayload, **kein** Secret, **kein** Token. FR-012 / AC-007-2 sind eingehalten. |
| Audit-Protokoll ohne Nutzlastinhalt | **erfüllt** | `schema.sql:383-399` — `details JSONB` ohne Nutzlast; `common/AuditLogRepository.java:30-37` — ausschließlich Metadatenfelder. |
| Retention dokumentiert | **erfüllt** | F-02: 30 Tage je Datenart, Anker `last_attempt_at`; `ADR-012`, `application.yml:123-130`, `schema.sql:364-380` (Override je Mandant). Die Durchsetzung läuft mandantenübergreifend über die Eigentümerfunktion (`schema.sql:680-858`) in zwei Stufen (markieren → Purge). |
| Recht auf Löschung | **technisch abgebildet, fachlich offen** | Die Purge-Stufe löscht Ereignisse, Zustellläufe und Versuche physisch (`schema.sql:833-841`); die Kaskade über `ON DELETE CASCADE` (`schema.sql:240-243`) nimmt die Protokollzeilen mit. Ein **mandantenspezifisches Sofort-Lösch-Kommando** gibt es nicht — die Löschung läuft über die Fristen bzw. einen Override in `retention_settings`. → **offene Frage Q-3** (Recht/Fachbereich, Gate 2), **kein** Befund. |
| Karenzzeit R-01 / E-13 | **offen** | `schema.sql:704-709` — **kein** Vorgabewert; ohne gesetzten Wert wird mit 0 Tagen gerechnet. Bewusst so entschieden und im Vertrag nicht festgeschrieben → offener Punkt des Fachbereichs (Gate 2). |

### 3.10 OWASP Top 10 (Querprüfung)

| ID | Kategorie | Ergebnis | Beleg / Befund |
|---|---|---|---|
| **A01** | Broken Access Control | **kein Fund** | RLS auf allen zehn fachlichen Tabellen (`schema.sql:888-961`), `WITH CHECK`, fail-closed, kein `BYPASSRLS`; Besitzprüfung auf **jeder** Objekt-Route (404 vor 403); drei mandantenübergreifende Eigentümerfunktionen, fail-closed und ohne Klartextprojektion; `permitAll` nur auf den drei Health-Pfaden. Belegt: `ForeignResourceIsolationIT` (11), `RoleCapabilityMatrixIT` (84), `RowLevelSecurityIT` (4), `TenantIsolationIT` (6) — alle grün. Die **Restlücke** E-12 (die Applikationsrolle kann `app.cross_tenant` setzen) ist **dokumentiert** (`docs/adr/README.md:96`) und ein offener Punkt, kein neuer Befund. |
| **A02** | Cryptographic Failures | **kein Fund (ein Medium: S-22)** | AES-256-GCM mit 12-Byte-Nonce und 128-Bit-Tag (`crypto/PayloadCipher.java:80-106`), HMAC-SHA256-Fingerprint mit abgeleitetem Teilschlüssel (`:119-138`), zeitkonstante Vergleiche (`SignatureService.java:68`, `CursorCodec.java:84`), Schlüssel **außerhalb** der Datenbank, keine Eigenimplementierung. **S-22:** die Schlüssellänge wird nicht geprüft (AES-128-Downgrade möglich). |
| **A03** | Injection | **kein Fund** | Ausschließlich benannte Parameter; keine SQL-Konkatenation; keine Befehlsausführung; keine Dateipfade aus Eingaben. Belegt: grep über alle Repositories (0 Treffer). |
| **A04** | Insecure Design | **kein Fund; eine Vertragslücke (S-24)** | Das Design trennt Mandant (RLS) und Rolle (Anwendung) in der **richtigen** Reihenfolge; die mandantenübergreifende Sicht ist auf drei schmale Funktionen begrenzt; die Replay-Regel (einmal, nur Empfänger-Admin) ist über eine DB-Constraint (`UNIQUE (replay_of)`, `schema.sql:244-245`) durchgesetzt. **S-24:** die im Vertrag zugesagte eingehende Ratenbegrenzung fehlt in der Implementierung. |
| **A05** | Security Misconfiguration | **kein Fund (zwei Low: S-26, S-27)** | Fehlermeldungen ohne internes Detail, `include-stacktrace: never`, `management.endpoints.web.exposure.include: health,info,metrics` mit `show-details: never` (`application.yml:70-77`) — `/actuator/metrics` bleibt **hinter** der Authentifizierung (`SecurityConfiguration.java:60-63`; belegt durch `S-HARD-7`). **S-26:** apt-Pakete nicht gepinnt. **S-27:** kein Produktionsprofil → ephemerer Schlüssel möglich. |
| **A06** | Vulnerable and Outdated Components | **Befund → S-19 (High)** | 96 versionsgebundene Advisories, 3 im HTTP/1.1-Pfad erreichbar. Siehe Anhang A. |
| **A07** | Identification and Authentication Failures | **kein Fund (ein Medium: S-21)** | 401 statt 403/500; RS256-only; `none` und HS256 abgelehnt; Ablauf geprüft; kein eigener Passwortpfad. **S-21:** Audience wird nicht geprüft, `exp` ist nicht erforderlich. |
| **A08** | Software and Data Integrity Failures | **kein Fund** | Signaturverfahren und Header-Name sind im Vertrag eingefroren und durch einen **Vertragstest** abgesichert (`tests/contract/SignatureContractTest.java`, 6 Tests, grün); der Idempotenzvergleich läuft über einen gezielten HMAC-Fingerprint statt über einen ungeschützten Hash (ADR-007); die Idempotenz-Constraint liegt in der Datenbank (`UNIQUE (organisation_id, idempotency_key)`, `schema.sql:173-174`). |
| **A09** | Security Logging and Monitoring Failures | **kein Fund** | Audit-Protokoll für die nachvollziehbaren Aktionen (`app.audit_log`, `schema.sql:383-399`); Betriebsprotokoll enthält nur Metadaten; Readiness/Health melden den Zustand, nicht die Daten. |
| **A10** | Server-Side Request Forgery (SSRF) | **kein Fund; eine offene Frage** | Die Ziel-URL ist **Eingabe** des Mandanten — das ist das Produkt. Die wirksamen Begrenzungen: Schema **nur** `https` (`endpoint/EndpointTargetUrl.java:52-55`, zusätzlich DB-Constraint `endpoints_target_url_ck`, `schema.sql:100-101`), **keine** eingebetteten Zugangsdaten (`:56-60`, DB-Constraint `:102-103`), **keine** Weiterleitung auf Weiterleitungen (`delivery/DeliveryHttpClient.java:45`, `Redirect.NEVER`), **kein** Trust-all bei TLS (`:27-28`), Zeitlimits (5 s / 10 s). **Offene Frage Q-4:** ein Filter gegen interne Adressbereiche (Link-Local `169.254.0.0/16`, RFC1918) fehlt — für ein Webhook-Produkt ist das eine **bewusste** Entscheidung (Zustellung an interne Ziele ist ein legitimer Anwendungsfall), aber es ist eine **Sichtbarkeitsfrage** und keine belegte Lücke → **kein Befund**. |

---

## Anhang A — SCA: versionsgebundene Advisories und ihre Erreichbarkeit

Scan: `mvn -B dependency:tree` (76 Koordinaten) → OSV `querybatch` (2026-10-22). Gesamt:
**96** Advisories — 9 Critical, 36 High, 35 Moderate, 16 Low.

### A.1 Erreichbar (3) → **S-19 (High)**

| Advisory | CVE | Schwere | Betroffene Version | Behoben in | Erreichbarkeit in dieser Konfiguration |
|---|---|---|---|---|---|
| GHSA-563x-q5rq-57qp | CVE-2026-24880 | High | `tomcat-embed-core:10.1.31` | 10.1.52 | **Erreichbar** — HTTP/1.1-Chunked-Parser; jeder Client kann eine ungültige Chunk-Extension senden. Ausnutzbarkeit hängt von der Auslegung durch den vorgeschalteten Proxy ab (Topologie: Schritt 9). |
| GHSA-h3gc-qfqq-6h8f | CVE-2025-48988 | High | `tomcat-embed-core:10.1.31` | 10.1.42 | **Erreichbar** — Spring Boot registriert `StandardServletMultipartResolver` automatisch; ein angemeldeter Client mit `Content-Type: multipart/form-data` veranlasst Tomcat, den Body ohne wirksame Ressourcenbegrenzung zu parsen. |
| GHSA-gqp3-2cvr-x8m3 | CVE-2025-48989 | High | `tomcat-embed-core:10.1.31` | 10.1.44 | **Erreichbar** — „made you reset" auf HTTP/1.1-Verbindungen. |

### A.2 Nicht erreichbar — mit Begründung (93)

| Advisory-Familie | Anzahl | Warum nicht erreichbar |
|---|---|---|
| Tomcat **HTTP/2** (GHSA-r29c-68gh-xp6x, GHSA-25xr-qj8w-c4vf) | 2 | HTTP/2 ist **nicht aktiviert**: `server.http2.enabled` ist nirgends gesetzt (`application.yml`), der `http2`-Connectors fehlen in `mvn dependency:tree`, der Container spricht HTTP/1.1 auf 8080. |
| Tomcat **Default-Servlet/Schreibzugriff** (GHSA-83qj-6fr2-vhqg, GHSA-27hp-xhwr-wr2m, GHSA-wmwf-9ccg-fff5) | 3 | Erfordern „writes enabled for the default servlet" (Spring Boot registriert den DefaultServlet **nicht**) bzw. PUT/Partial-PUT (keine PUT-Route im Vertrag). |
| Tomcat **Authentifizierung** (DIGEST/FORM) (GHSA-9xv2-5v5q-p794, GHSA-h6fc-48rj-7qqh, GHSA-h3x4-894j-xpx5) | 3 | Der Dienst nutzt ausschließlich Bearer-Token; keine Container-Authentifizierung ist konfiguriert. |
| Tomcat **Security-Constraints** (GHSA-gcx9-497g-6cp6, GHSA-5m62-pw8w-7w9f) | 2 | Keine `<security-constraint>`-Konfiguration (eingebetteter Container ohne `web.xml`); die Zugriffskontrolle liegt in der Anwendung. |
| Tomcat **sonstige** (JsonAccessLogValve, LoadBalancerDrainingValve, WebSocket, WebDAV, Session-Fixation, Installer, WebSocket-Header, Logfile-Info, OCSP/Tomcat-Native, HTTP-Priority, Multipart-Integer-Overflow, „made you reset"-Randfälle, Ressourcen-Freigabe) | 21 | Erfordern nicht konfigurierte Komponenten (`JsonAccessLogValve`, `LoadBalancerDrainingValve`, WebSocket, WebDAV, Tomcat Native/OCSP) bzw. nicht genutzte Protokolle; Multipart-Integer-Overflow (GHSA-wr62-c79q-cv37) setzt die „unlikely multipart upload"-Konfiguration voraus. |
| Spring Framework **Methodensicherheit / Annotationen** (GHSA-jmp9-x22r-554x) | 1 | `@EnableMethodSecurity` wird **nicht** verwendet (grep über `src/main/` → 0 Treffer). |
| Spring Framework **SpEL / XSS / Skript-Views / Pfadbegrenzung / Versionierung / Form-Tags** (GHSA-r5w3-xv2f-j59q, GHSA-3chg-m5w7-qfv5, GHSA-pc63-qcmh-9cmg, GHSA-6p4f-wcwh-5vvm, GHSA-4773-3jfm-qmx3, GHSA-6r3c-xf4w-jxjm, GHSA-72pg-x5f8-j25j, GHSA-957g-f97v-vppc, GHSA-cjpg-rgq5-fr37, GHSA-wg35-8jpf-2xv3, GHSA-wxpp-56q6-5pcg, GHSA-6p4f-wcwh-5vvm, GHSA-x23c-287f-qqv5) | 13 | Erfordern SpEL-Auswertung, `XsltView`/Skript-Views, `VersionResourceResolver`, JSP-Form-Tags oder Pfadbegrenzung — **keine** davon wird verwendet; der Dienst liefert **keine** statischen Ressourcen über Spring MVC aus (die Oberfläche ist eine separate Next.js-Anwendung). |
| Spring Security **Passwort / Umleitung / X.509 / Methodensicherheit** (GHSA-mg83-c7gq-rv5c, GHSA-x2r2-rvhq-2mqv, GHSA-293q-567p-wmwq, GHSA-xf7-qj7q-83fh) | 4 | Kein Passwort-Encoder, keine Weiterleitung (stateless, kein Formular-Login), keine Client-Zertifikate, kein `DaoAuthenticationProvider`. |
| Spring Boot **Actuator / Cloud Foundry / EndpointRequest / Temp-Verzeichnis** (GHSA-mgvc-8q2h-5pgc, GHSA-rc42-6c7j-7h5r, GHSA-ggg2-9786-hwc8, GHSA-wwpq-f5c3-7hvx) | 4 | Kein `spring-cloud-cloudfoundry`; `EndpointRequest.to()` wird **nicht** verwendet (die Kette nutzt Zeichenketten-Matcher, `SecurityConfiguration.java:61`); kein Artemis; das Temp-Verzeichnis betrifft nur lokale Mehrbenutzer-Hosts (der Container läuft als uid 10001, `Dockerfile:42`). |
| Spring Security **HTTP-Header** (GHSA-mf92-479x-3373) | 1 | Upstream **Critical**, aber die Bedingung („Anwendungen, die Kopfzeilen setzen") ist hier nicht gegeben: die Anwendung setzt **keine** eigenen Antwortkopfzeilen; die Defaults der Kette sind aktiv. Der **Wirkungsgrad** ist nicht sicher bestimmbar → als **offene Frage** geführt, nicht als Befund. |
| Jackson **polymorphe Typen / Deserialisierungssonderfaelle** (GHSA-rmj7-2vxq-3g9f, GHSA-j3rv-43j4-c7qm, GHSA-gx83-3vf8-gh7j, GHSA-wjgm-6hv5-3cvf, GHSA-wv8q-qhhj-9h54, GHSA-5gvw-p9qm-jgwh, GHSA-5jmj-h7xm-6q6v, GHSA-3pjw-73gf-8qr5, GHSA-vvgp-rfg2-7rr6) | 9 | Kein `PolymorphicTypeValidator`, kein `activateDefaultTyping`, kein `@JsonTypeInfo`, kein `java.nio.file.Path`/`InetAddress` in den Request-Bodies; die DTOs sind Records mit expliziten `@JsonProperty` und `@JsonAnySetter` (Prüfung auf unbekannte Felder). |
| Jackson **DoS** (GHSA-q4xh-88c3-wmh7, GHSA-p6pp-m3f8-5c89, GHSA-cxp5-3px4-pw24, GHSA-r7wm-3cxj-wff9, GHSA-7hhh-6rmp-j9qf, GHSA-72hv-8253-57qq, GHSA-q4xh-88c3-wmh7) | 7 | Betreffen den **asynchronen** Parser bzw. extreme Zahlenformate; Spring MVC liest Request-Bodies **synchron**. Ein Restrisiko (quadratisches Backtracking bei sehr langen Dezimalzahlen) ist **nicht** ausgeschlossen → als **offene Frage** geführt (Q-5). |
| PostgreSQL-JDBC **SCRAM/Kanalbindung/PBKDF2** (GHSA-j92g-9f8w-j867, GHSA-hq9p-pm7w-8p54, GHSA-98qh-xjc8-98pq) | 3 | Betreffen die **Datenbankverbindung** (Kanalbindung `require` ist nicht konfiguriert; die PBKDF2-DoS setzt einen Angreifer auf dem Datenbankweg voraus). Nicht durch die API erreichbar. |
| Übrige (json-smart, logback, log4j, commons-compress, micrometer, xmlunit, assertj) | 12 | `json-smart` nur über `@JsonPath` (nicht verwendet); logback/log4j/commons-compress/XMLUnit/AssertJ betreffen Datei- bzw. XML-Verarbeitung im Test- bzw. Build-Werkzeug, nicht den Request-Pfad. |

> **Hinweis zur Vollständigkeit:** die Erreichbarkeits-Einstufung ist eine
> **komponentenbezogene** Analyse des tatsächlichen Stacks (Konfiguration,
> Dependency-Tree, Routing). Sie ist kein Ersatz für einen Hersteller-Scan des
> fertigen Images in der Zielumgebung — der gehört in **Schritt 9**.

---

## Anhang B — Die Nachweiskette: jeder Befund ist festgenagelt

| Befund | Test | Datei | Ergebnis | Status |
|---|---|---|---|---|
| S-21 (Audience, `exp`) | `LUECKE (Befund S-21)` ×2 | `tests/unit/security/SecurityGapTest.java` | schlägt fehl: „Expecting code to raise a throwable" | **Beleg der Lücke** |
| S-22 (Schlüssellänge) | `LUECKE (Befund S-22)` | `tests/unit/security/SecurityGapTest.java` | schlägt fehl: „Expecting code to raise a throwable" | **Beleg der Lücke** |
| S-23 (Antwort-Body) | `LUECKE (Befund S-23)` | `tests/unit/security/SecurityGapTest.java` | schlägt fehl: „Expecting actual: 5242880 to be less than or equal to: 1048576" | **Beleg der Lücke** |
| S-20 (chunked Body) | `LUECKE (Befund S-20)` | `tests/integration/security/SecurityGapIT.java` | schlägt fehl: „expected: 413 but was: 202" | **Beleg der Lücke** |
| S-24 (429) | `LUECKE (Befund S-24)` | `tests/integration/security/SecurityGapIT.java` | schlägt fehl: „Expecting value to be true but was false" | **Beleg der Lücke** |
| S-19 (Abhängigkeiten) | — | `pom.xml:16` | OSV-Abfrage, Anhang A | **belegt durch Check** |
| Abwehr: 401 | `S-HARD-1` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: Security-Header | `S-HARD-2` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: 1-MiB-Grenze | `S-HARD-3` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: 2-MiB-Grenze (Content-Length) | `S-HARD-4` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: keine internen Details | `S-HARD-5` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: Mandantenparameter | `S-HARD-6` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: nur Health ohne Token | `S-HARD-7` | `tests/integration/security/SecurityHardeningIT.java` | grün | Abwehr belegt |
| Abwehr: RS256 / kein `none` / Ablauf / Signatur / kein IdP | `S-HART-1`…`S-HART-7` | `tests/unit/security/JwtDecoderHardeningTest.java` | grün | Abwehr belegt |

---

## Anhang C — Offene Fragen (keine Befunde)

| ID | Frage | Owner |
|---|---|---|
| **Q-1** | Terminiert TLS der vorgeschaltete Proxy? Wer setzt HSTS (`Strict-Transport-Security`)? Der Container selbst spricht HTTP auf Port 8080. | Schritt 9 / Betrieb |
| **Q-2** | Sind die Branches geschützt (kein direkter Push auf `main`)? Aus dem Repository **nicht** prüfbar. | Schritt 9 / Betrieb |
| **Q-3** | „Recht auf Löschung": reicht die fristbasierte Purge-Stufe, oder braucht es ein mandantenspezifisches Sofort-Lösch-Kommando? | Fachbereich + Recht, Gate 2 |
| **Q-4** | Soll ein Endpunkt-Ziel in interne Adressbereiche (Link-Local, RFC 1918) verboten werden? Für ein Webhook-Produkt ist die Zustellung an interne Ziele legitim — die Frage ist eine **Sichtbarkeits**-, keine Sicherheitsfrage. | Fachbereich + Plattform, Gate 2 |
| **Q-5** | Restrisiko `jackson-core` (quadratisches Backtracking bei sehr langen Dezimalzahlen im Request-Body) — abhängig vom späteren Jackson-Update. | Schritt 6 (mit S-19) |
| **Q-6** | Bedingungen von CVE-2026-22732 („HTTP Headers Are not Written Under Some Conditions", Spring Security 6.3.4) — die Anwendung setzt keine eigenen Kopfzeilen; der Wirkungsgrad ist nicht bestimmbar. | Schritt 6 (mit S-19) |
| **Q-7** | Schlüsselverwaltung nach ADR-003 §2 (je Mandant ein DEK, verschlüsselt mit einem KEK) — ist der **globale** Schlüssel (`application.yml:85`) die gewollte Vereinfachung, oder soll die mandantenweise Rotation nachgezogen werden? (Hängt an O-04.) | Architektur + Fachbereich, Gate 2 |

---

## Anhang D — Was dieser Audit **nicht** behauptet

* Er ändert **keine** Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/` oder `docs/requirements/`. Der eingefrorene Vertrag
  (`docs/api/openapi.yaml`, seit `8627bb6`) ist unverändert.
* Er behauptet **nicht**, dass die 93 nicht erreichbaren Advisories unbedenklich
  sind — sie sind für **diese** Konfiguration nicht erreichbar; ein Scan des
  fertigen Images in der Zielumgebung gehört in Schritt 9.
* Er behauptet **nicht**, dass die Abhängigkeiten gezielt angegriffen werden — die
  Bewertung ist eine **Versions**- und **Erreichbarkeits**-Bewertung.
* Er ersetzt **nicht** die Freigabe durch den Menschen: **GATE 2** steht aus, und
  die Empfehlung dieses Audits lautet **blockieren**.

---

*Erzeugt in Schritt 8 (Sicherheit & Compliance). Kanonisches Artefakt:
`.agent/artifacts/security-audit.md`.*
