# Security-Audit — EchoBox (Schritt 8, **dritte Runde**)

> Agent: `aisdlc-qa` · Stand: 2026-10-22 · Audit **read-only** (keine Datei in `src/`,
> `docs/api/`, `docs/adr/`, `docs/architecture/`, `.agent/knowledge/` oder
> `docs/requirements/` geändert). In `tests/` wurden **neue** Tests angelegt.
> Skill: `aisdlc-security-scan` (alle 10 Prüfungen, **erneut und vollständig**) ·
> Gate: **GATE 2** — QA 0 Critical, Security 0 Critical und 0 High.
>
> **Runde 3.** Die erste Runde (`1ba4160`) fand 0 Critical, 2 High, 5 Medium, 2 Low
> und empfahl **BLOCKIEREN**. Die Nacharbeit 1 (`d135cda`) hat S-19…S-27 und A-1
> behoben. Die zweite Runde (`3d40912`) fand **1 neuen High** (S-28), 4 Medium, 2 Low
> und empfahl erneut **BLOCKIEREN**. Die Nacharbeit 2 (`3a5ae70`) hat **7 von 7**
> Befunden (S-28…S-34) behoben. Dieser Bericht prüft **jeden** dieser Befunde **am
> aktuellen Stand mit eigenem Nachweis** nach und führt danach ein **erneutes
> vollständiges Audit** durch.

---

## 1. Gesamtergebnis

| Schwere | Anzahl | Befund-IDs |
|---|---|---|
| **Critical** | **0** | — |
| **High** | **0** | — |
| **Medium** | **1** | **S-36** (nicht unterstützte HTTP-Methode → 500 mit Stacktrace statt 4xx) |
| **Low** | **1** | **S-35** (Dokumentation der Audience widerspricht dem fail-closed-Verhalten) |

### Freigabe-Empfehlung: **FREIGEBEN**

Die Gate-Bedingung lautet **0 Critical und 0 High**. Beide Werte sind **0** — die
Bedingung ist **erfüllt**. Es liegt **kein** High offen.

* **Alle sieben Befunde der zweiten Runde sind behoben** — jeder mit **eigenem**
  Nachweis (ausgeführter Check oder Quelltextfundstelle), nicht nach dem Bericht
  der Nacharbeit. Der High **S-28** ist an der Stelle geschlossen, an der er
  entstanden ist: die Grenze des Request-Bodies hängt **nicht mehr am Medientyp**
  und ist über **keinen** der geprüften Umgehungspfade erreichbar (Abschnitt 2).
* **Ein neuer Medium-Befund** (S-36) und **ein neuer Low-Befund** (S-35) sind
  offen. Beide sind **keine Gate-Bloecker** (Abschnitt 3): S-36 ist ein Fehler der
  *Statuszuordnung* (die Antwort gibt nachweislich **kein** internes Detail preis),
  S-35 ist ein **Dokumentations**befund (das Verhalten ist fail-closed, also
  sicherer als dokumentiert).
* **Kein Vertragsdefekt.** Der eingefrorene Vertrag `docs/api/openapi.yaml` ist
  unverändert (`git diff --stat -- docs/api/` ist leer, auch gegen `8627bb6`).
  Die Verschärfung `consumes = application/json` ist **vertragskonform**
  (Abschnitt 4.1) — die beiden Operationen mit **optionalem** Request-Body
  akzeptieren weiterhin einen Aufruf **ohne** Body.
* **Der Build ist grün.** Es gibt **keine** bewusst roten Tests. Die zehn
  Gap-Tests der Runden 1 und 2 sind **alle grün**; die Nacharbeit 2 hat die
  belegten Lücken tatsächlich geschlossen, ohne einen Test zu verändern
  (Nachweiskette in **Anhang B**).

> **Warum kein bewusst roter Test für S-36?** Die Runden 1 und 2 haben ihre
> offenen Befunde mit **fehlschlagenden** Gap-Tests festgenagelt — damals lag in
> beiden Fällen ein **High** offen, das Gate war also ohnehin blockiert und der
> rote Build war die ehrliche Darstellung. **Hier liegt kein High offen.** Ein
> absichtlich roter Test wuerde den Build der Pipeline (`deploy.yml:64-65`,
> `mvn -B verify`) scheitern lassen und damit genau das Deployment verhindern,
> das die Gate-Bedingung freigibt — fuer einen Medium-Befund, der die Antwort
> nicht verletzt. S-36 ist stattdessen **belegt** (Sonde ueber 15
> Methode/Pfad-Kombinationen, Protokollauszug, Fehlerspur) und der
> **Abnahmetest** steht fertig in `docs/qa/bugs/BUG-S-36.json`. Die
> *sicherheitsrelevante* Eigenschaft der Antwort — kein internes Detail — ist mit
> `UnsupportedMethodErrorIT` (**gruen**) festgenagelt.

---

## 2. Nachpruefung der Befunde der zweiten Runde

Geprueft wurde **jeder** Befund am aktuellen Stand — mit eigenem Nachweis
(Datei:Zeile oder ausgefuehrter Check), **nicht** nach dem Bericht der Nacharbeit.

| ID | Schwere (Runde 2) | Bewertung | Nachweis (aktueller Stand, eigener) |
|---|---|---|---|
| **S-28** Body-Grenze am Medientyp | High | **behoben** | `src/main/java/com/echobox/tenant/TenantParameterFilter.java:88-149` — die Grenze hängt **nicht mehr am Medientyp**, sondern an der **Methode** (`mayCarryBody`, `:146-149`: POST/PUT/PATCH/DELETE); die Vorpruefung gegen `Content-Length` (`:113`) und das **begrenzte Lesen** (`:117`, `readBounded` `:165-184`) benutzen **dieselbe** Bedingung; der Filter liest den Body **vor** dem Message-Converter und gibt ihm **nur die bereits gelesenen, damit begrenzten Byte** (`CachedBodyRequest`, `:123`, `:280-329`) — der Converter kann nie mehr Bytes sehen, als die Grenze erlaubt. **Eigener Nachweis — `RequestBodyBoundaryAuditIT` (15 Tests, grün):** 6 Medientypen mit `Content-Length` → **413**; 4 Medientypen **chunked** → **413**; **ohne `Content-Length`** (Body bis zum Verbindungsende, roher Socket) → **413**; **ohne `Content-Type`** → **413**; **`Content-Encoding: gzip`** mit einer 20-MiB-Bombe (komprimiert 20 KiB) → **4xx**, und der Dienst antwortet **danach** weiter; ein Body **knapp unter** der Grenze → **202** (der Converter liest die begrenzten Byte — die Grenze ist nicht zu streng); ein **GET** mit überdimensioniertem Body → **200**, der Dienst antwortet weiter. **Die Grenze ist über keinen geprüften Weg umgehbar.** |
| **S-29** Audience fail-open | Medium | **behoben** | `config/SecurityConfiguration.java:144-150` — der Audience-Validator ist **immer** Teil der Pruefkette; `:178-192` — ohne Zielgruppe wird **jedes** Token abgelehnt (fail-closed). `config/AudiencePolicy.java:28-36` — der Dienst **startet nicht**, wenn die Zielgruppe leer ist (`IllegalStateException`); die Bean-Methode `SecurityConfiguration.audiencePolicy` (`:130-133`) ist eine normale, **nicht `lazy`** Bean — sie wird beim Start des Kontexts erzeugt, der Startabbruch ist damit wirksam. **Eigener Nachweis — `AudienceFailClosedPathsTest` (4 Tests, grün):** die **Produktions**-Bee-Fabrik verweigert `""`, `"   "` und `null`; ein **leerer** Secret-Wert löst **nicht** auf den Vorgabewert auf (`${ECHobox_SECURITY_AUDIENCE:echobox}` → `''`, nicht `echobox`); keines der **drei** ausgelieferten Profile leert die Zielgruppe; die Pipeline setzt die Zielgruppe in **beiden** Umgebungen. **Rest:** die *Dokumentation* der Konfiguration widerspricht dem Verhalten → **S-35** (Low). |
| **S-30** 500 statt 4xx | Medium | **behoben** | `common/GlobalExceptionHandler.java:246-253` — `HttpMediaTypeNotSupportedException` **und** `HttpMediaTypeNotAcceptableException` → **400 `malformed_request`** (vertragskonform: der Vertrag kennt fuer einen Fehler des Aufrufers nur 4xx); `:267-274` — `MultipartException` → **400**. Beide loggen auf **DEBUG ohne Exception-Objekt** (`:248`, `:269`) — **kein Stacktrace im Protokoll**. **Eigener Nachweis:** `ErrorMappingLeakTest` (7 Tests, grün) + `MediaTypeErrorIT` (6, grün). **Leaken andere Ausnahmen weiterhin Stacktraces?** **Nein, nicht in der Antwort:** der allgemeine Handler (`:283-290`) beantwortet einen unerwarteten Fehler mit 500 `internal_error` **ohne** Stacktrace, ohne Klassennamen und ohne Pfad — belegt durch `ErrorMappingLeakTest.the_unexpected_error_is_an_internal_error_without_internal_detail`. Im **Protokoll** bleibt der Stacktrace eines unerwarteten Fehlers stehen (`:285`) — das ist die Aufgabe des Betriebsprotokolls, nicht der Antwort; **Fehler des Aufrufers** landen dort nicht mehr (S-30 behoben). **Einschraenkung:** die Behebung gilt fuer den *Medientyp*; die **Methode** (`HttpRequestMethodNotSupportedException`) hat weiterhin keinen eigenen Fall → **S-36** (Medium, neu). |
| **S-31** Container-Pin | Medium | **behoben** | `pom.xml:65` — `<tomcat.version>10.1.61`. `mvn -B -o dependency:tree` → `tomcat-embed-core:jar:10.1.61`, `tomcat-embed-websocket:10.1.61`, `tomcat-embed-el:10.1.61`. **Eigener Nachweis (OSV, unabhaengig abgefragt):** `org.apache.tomcat.embed:tomcat-embed-core:10.1.55` → **3** Advisories (GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5); `…:10.1.61` → **0**. Der Pin liegt damit **oberhalb** der Fix-Linie (10.1.59). Der begleitende Test wurde **verschärft**, nicht abgeschwaecht: `DependencyHardeningTest` verlangt jetzt ≥ **10.1.59** (vorher 10.1.55) — **grün**. |
| **S-32** SCA-Gate ohne Baseline | Medium | **behoben** | `osv-scanner.toml` (Repository-Wurzel, **26** `[[IgnoredVulns]]`-Eintraege) + `.github/workflows/deploy.yml:56-64` (`--config=osv-scanner.toml`). **Eigener Nachweis:** eigene OSV-Abfrage über den **aktuellen** Baum — **110** Maven-Koordinaten (`mvn dependency:tree`) und **523** npm-Pakete (`package-lock.json`) → **17 + 9 = 26** Advisories, **exakt** die 26 Baseline-Eintraege: **0 neu** (der Gate schlägt nur bei neuen Advisories an), **0 verwaist** (kein toter Eintrag). **Formatkompatibilität** mit dem gepinnten Scanner belegt: osv-scanner **v2.7.0** erwartet `[[IgnoredVulns]]` mit `id`, `ignoreUntil` (Datum) und `reason` (`internal/config/config.go`, Tag v2.7.0) und kennt die Option `--config` (`internal/helper/flags.go`); die Action `google/osv-scanner-action@v2.7.0` **existiert** (Tag-Liste) und uebergibt `scan-args` an das Image `ghcr.io/google/osv-scanner-action:v2.7.0`. Jeder Eintrag traegt eine Begruendung und ein Ablaufdatum (2027-04-30) — die Baseline **veraltet von selbst**. |
| **S-33** Token ohne Mandant → 500 | Low | **behoben** | `tenant/TenantTransactionRunner.java:100-105` — ein fehlender Mandant wirft jetzt `UnauthenticatedException`; `common/UnauthenticatedException.java:24-26` → **401 `unauthorized`** (`ProblemFactory.UNAUTHORIZED`), **nicht** 500. Der Mandant wird weiterhin **nicht geraten und nicht ersetzt** (fail-closed). **Eigener Nachweis:** `ErrorMappingLeakTest.a_missing_tenant_is_an_unauthorized_error` (**grün**) + `MissingTenantIT` (1, grün). **Rest (Q-9, unverändert offen):** der Zaehler des Anfragelimits zaehlt weiterhin nur Anfragen **mit** Mandant (`tenant/ApiRateLimitFilter.java:85-98`) — das war in Runde 2 als offene Frage gefuehrt und ist **kein** Befund. |
| **S-34** Veralteter Javadoc | Low | **behoben** | `delivery/DeliveryStateMachine.java:11-31` und `:118-124` — die Aussage „wird in Schritt 2 nachgezogen" ist **entfernt**; der Javadoc nennt **beide** Uebergaenge (`pending -> delayed`, `scheduled -> delayed`) und verweist auf `docs/architecture/domain_model.md` §3.3 als die nachgezogene Tabelle. `DeliveryStateMachineDocumentationTest` (2, grün). |

**Ergebnis der Nachpruefung: 7 Befunde behoben, 0 teilweise behoben, 0 offen.**

---

## 3. Die neuen Befunde dieser Runde

| ID | Schwere | Datei:Zeile | Befund | Ausnutzbarkeit | Empfehlung |
|---|---|---|---|---|---|
| **S-36** | **[SECURITY] Medium** | `src/main/java/com/echobox/common/GlobalExceptionHandler.java:283-290` (kein eigener Fall) | **Eine nicht unterstützte HTTP-Methode ergibt HTTP 500 mit Stacktrace statt eines 4xx.** Die Nacharbeit zu S-30 hat eigene Fälle für `HttpMediaTypeNotSupportedException`, `HttpMediaTypeNotAcceptableException` (`:246-253` → 400) und `MultipartException` (`:267-274` → 400) angelegt — die **Methode** aber nicht: `HttpRequestMethodNotSupportedException` fällt in den allgemeinen Handler, der `LOG.error(..., exception)` protokolliert (Stacktrace) und `internal_error` (500) antwortet. Das ist ein **Fehler des Aufrufers** (die Methode ist für diesen Pfad nicht definiert), kein Serverfehler. **Der Pfad ist ohne Anmeldung erreichbar:** die Gesundheitspfade sind `permitAll` (`SecurityConfiguration.java:70-71`) — ein `POST /health` genügt. | **Belegt** (Sonde über 15 Methode/Pfad-Kombinationen): `POST /health`, `PUT /health`, `POST /actuator/health` — jeweils **ohne Token** → **500 `internal_error`** + Stacktrace auf ERROR im Protokoll; mit Token: `GET`/`PUT`/`DELETE /api/v1/events`, `GET /api/v1/endpoints/{id}/pause`, `DELETE /api/v1/endpoints/{id}`, `GET /api/v1/retention/enforcement` → je **500**. **9 von 15** Sonden erzeugten einen Stacktrace im Protokoll. **Wirkung:** (1) Protokollverstärkung (~2 KB Stacktrace je Anfrage, unbegrenzt, weil das Anfragelimit je Mandant zählt und diese Anfragen keinen Mandanten haben — **Q-9**); (2) Monitoring-Verschmutzung — ein Fehler des Aufrufers erscheint als Serverfehler; (3) **keine** Verletzung der Antwort: die Antwort gibt nachweislich **kein** internes Detail preis (Stacktrace, Klasse, Pfad) — belegt durch `UnsupportedMethodErrorIT` (grün). Der Vertrag kennt für einen Fehler des Aufrufers nur 4xx (400/401/403/404/409/413/422/429); ein 500 ist laut Vertrag „unerwarteter Fehler". | Einen eigenen Fall für `HttpRequestMethodNotSupportedException` in `GlobalExceptionHandler` ergänzen und auf **400 `malformed_request`** abbilden — **vertragskonform**: der eingefrorene Vertrag kennt **kein 405** (geprüft: `grep '"405"' docs/api/openapi.yaml` → keine Treffer; die Fehlersemantik in `openapi.yaml:78-91` kennt nur 400/401/403/404/409/413/422/429/500/503), ein 415/405 wäre ein Vertragswechsel (Rücksprung zu Schritt 2). (Ebenso vertragskonform wäre **404 `not_found`** — die Kombination aus Pfad und Methode existiert nicht; 400 ist die **konsistente** Auflösung zu S-30.) Analog zu S-30 auf **DEBUG ohne Exception-Objekt** protokollieren. Der Abnahmetest steht fertig in `docs/qa/bugs/BUG-S-36.json`. **Schritt 6/7.** |
| **S-35** | **[SECURITY] Low** | `src/main/resources/application.yml:106`, `src/main/java/com/echobox/config/EchoBoxProperties.java:141-146` | **Die Dokumentation der Konfiguration widerspricht dem fail-closed-Verhalten.** `application.yml:106` sagt: „Leer: die Zielgruppe wird nicht geprueft."; der Javadoc der Properties-Klasse (`:143-144`) sagt: „Leer: die Zielgruppe wird nicht geprueft (nur fuer Betriebe ohne Identity Provider mit eigener Audience-Politik)." Beide Aussagen beschreiben den **alten** Zustand von S-29. Der Javadoc war bereits in der **Empfehlung von S-29** benannt („beschreibt den Ist-Zustand, nicht die gewollte Zusage") und wurde bei der Behebung **nicht** mitgezogen. | **Keine.** Das Verhalten ist fail-closed und damit **sicherer** als dokumentiert. Der Fehler ist ein **Wahrheits- und Betriebsbefund**: wer die Betriebskonfiguration liest, haelt eine leere Zielgruppe fuer zulaessig und wuerde den Dienst mit leerem Secret starten wollen — tatsaechlich startet er **nicht** (`AudiencePolicy.required("")` → `IllegalStateException`, der Kontext entsteht nicht). Der Fehler zeigt sich als **unerklaerlicher Startabbruch zur Laufzeit** statt als Konfigurationsfehler zur Deploy-Zeit. Der Javadoc suggeriert zudem einen Betriebszustand („Betriebe ohne Identity Provider"), den der Dienst gar nicht kennt: ohne Audience nimmt er **kein** Token an. | Die beiden Saetze auf den tatsaechlichen Zustand umschreiben: „S-29: die Zielgruppe ist Pflicht — leer verhindert der Dienst den Start (fail-closed)." Zusaetzlich in der Betriebsdokumentation (Schritt 9) festhalten, dass `PRODUCTION_SECURITY_AUDIENCE` bzw. `STAGING_SECURITY_AUDIENCE` **nicht leer** sein darf. **Schritt 6/7.** |

Strukturierte Fehlerbeschreibungen: `docs/qa/bugs/BUG-S-35.json` und
`docs/qa/bugs/BUG-S-36.json` (neu).

> **Warum kein roter Test?** S-35 ist ein **Low**-Befund der Dokumentation, S-36
> ein **Medium**-Befund der Statuszuordnung. Beide blockieren das Gate **nicht**
> (0 Critical / 0 High) — ein absichtlich fehlschlagender Test wuerde den Build
> der Pipeline scheitern lassen und damit das freigegebene Deployment
> verhindern. Beide Befunde sind stattdessen **belegt** (Sonde, Protokollauszug,
> Quelltext) und tragen einen **fertigen Abnahmetest** in ihrer Fehlerspur. Das
> **Verhalten** hinter S-35 ist mit `AudienceFailClosedPathsTest` (grün)
> festgenagelt, die **Antwort** hinter S-36 mit `UnsupportedMethodErrorIT`
> (grün) — jeweils die sicherheitsrelevante Eigenschaft.

---

## 4. Die zehn Pruefungen des Skills — erneut und vollstaendig

### 4.1 Authentifizierung — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Token-Pflicht auf **jeder** geschuetzten Route | **erfuellt** | `SecurityConfiguration.java:69-72` — `anyRequest().authenticated()`; `permitAll` gilt **nur** fuer `/health`, `/health/ready`, `/actuator/health`, `/actuator/health/**` (`:70-71`) — **genau eine** `permitAll`-Stelle im ganzen Code (grep). Das entspricht dem Vertrag (`openapi.yaml:1666`, `:1700` — `security: []` genau fuer diese beiden Operationen) und dem Health-Check des Containers (`Dockerfile:67-68`). |
| **401** ohne Token (nicht 403, nicht 500) | **erfuellt** | `SecurityConfiguration.java:199-204` — eigener `AuthenticationEntryPoint` → 401 mit dem `Problem`-Schema. Belegt: `SecurityHardeningIT` → `S-HARD-1` (**gruen**). |
| Ablauf der Token (`exp`) | **erfuellt** | `SecurityConfiguration.java:146` (`JwtTimestampValidator`) **und** `:158-166` (`expiryRequired()` — `exp` ist **Pflicht**). Belegt: `SecurityGapTest.a_token_without_an_expiry_must_be_rejected` = **gruen**, `S-HARD-4` = **gruen**. |
| Nicht vor (`nbf`) | **erfuellt** | `JwtTimestampValidator` prueft `exp` **und** `nbf` (Spring-Standard). |
| Signaturpruefung | **erfuellt** | Der Decoder prueft die Signatur gegen das JWKS des Identity Providers (`:113`). Belegt: `S-HARD-1` (gueltiges Token wird angenommen), `S-HARD-5` (**gruen**, fremder Schluessel wird abgelehnt). |
| Zielgruppe (**Audience**) | **erfuellt** (S-29 behoben) | `:144-150` — der Validator ist **immer** Teil der Kette; `:178-192` — ohne Zielgruppe wird **jedes** Token abgelehnt; `AudiencePolicy.java:28-36` — der Dienst startet nicht bei leerer Konfiguration. Belegt: `AudienceFailClosedPathsTest` (4, **gruen**), `JwtAudienceHardeningTest` (4, gruen), `SecurityGapRound2Test` (2, gruen). *Dokumentationsrest → **S-35**.* |
| Algorithmus / kein `none` | **erfuellt** | `NimbusJwtDecoder.withJwkSetUri(...)` (`:113`) ist auf RS256 beschraenkt. Belegt: `S-HARD-2` (**gruen**, `alg: none` abgelehnt), `S-HARD-3` (**gruen**, HS256 mit dem oeffentlichen RSA-Schluessel als HMAC-Secret abgelehnt). |
| Aussteller (`iss`) | **kein Befund** | Der Decoder prueft `iss` nicht. Der Vertrag fordert keinen Issuer (`openapi.yaml:1726-1733` nennt nur `organisation_id` und die Rollen); der IdP ist ueber die JWKS-Adresse gebunden. |
| Keine selbstgebaute Kryptografie | **erfuellt** | `javax.crypto`: `Cipher.getInstance("AES/GCM/NoPadding")` (`crypto/PayloadCipher.java:40,84`), `Mac.getInstance("HmacSHA256")` (`crypto/SignatureService.java:32`, `common/CursorCodec.java:28`), `MessageDigest.isEqual` fuer zeitkonstante Vergleiche (`SignatureService.java:68`, `CursorCodec.java:84`). Keine Eigenimplementierung. |

### 4.2 Autorisierung / IDOR — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| **IDOR:** jede Objekt-Route prueft den **Besitz**, nicht nur die Rolle | **erfuellt** | `endpoint/EndpointService.java:184` (`requireOwnEndpoint` → 404), `event/EventService.java:154`, `protocol/ProtocolQueryService.java:106`, `delivery/DeliveryService.java:129` — alle Objektzugriffe laufen ueber `repository.findById(organisationId, id)`. Belegt: `ForeignResourceIsolationIT` (11 Tests, gruen). |
| Reihenfolge **erst Mandant, dann Rolle** | **erfuellt** | `EndpointService.java:102-103`, `:121-123`, `:142-144`, `:164-166` — `requireOwnEndpoint(...)` **vor** der Rollenpruefung. Ein fremdes Objekt ergibt 404, eine falsche Rolle im eigenen Mandanten ergibt 403. |
| Standard ist **verweigern** | **erfuellt** | `anyRequest().authenticated()`; 404 vor 403; keine Operation faellt auf „erlauben" zurueck. |
| Keine `permitAll` auf ganzen Praefixen ohne Begruendung | **erfuellt** | Siehe 4.1 — genau **eine** Stelle, drei Health-Pfade, mit Vertragsbezug. |
| Mandanten-Scoping auf **Query-Ebene** | **erfuellt** | Jede Query traegt `organisation_id = :organisationId` (`DeliveryRepository` 12, `EndpointRepository` 12, `EventRepository` 3 — grep). Darueber erzwacht PostgreSQL die Trennung: RLS auf **allen zehn** fachlichen Tabellen (`schema.sql`: 10 × `ENABLE ROW LEVEL SECURITY`, 10 × `CREATE POLICY`), `USING` **und** `WITH CHECK`, fail-closed. **Kein** `BYPASSRLS` vergeben (grep: nur Kommentare, keine GRANT). Belegt: `RowLevelSecurityIT` (4), `TenantIsolationIT` (6) — gruen. |
| Mandantenuebergreifende Sicht nur ueber die **drei** Eigentuemerfunktionen | **erfuellt** | `schema.sql:474`, `:531`, `:615`, `:699` — vier `SECURITY DEFINER`-Stellen, davon **drei** Eigentuemerfunktionen fuer die mandantenuebergreifende Sicht; alle fail-closed, `search_path` fest verdrahtet, `EXECUTE` nur fuer `echobox_app`, `REVOKE … FROM PUBLIC` (`schema.sql:1102-1120`). |
| Der Mandant stammt **ausschliesslich** aus dem Token | **erfuellt** | `TenantContext.java`; ein mitgeschickter Mandantenparameter wird abgelehnt (`TenantParameterFilter.java:186-198` Query/Header, `:211-235` Body). Die Body-Pruefung haengt **nicht** mehr am Medientyp (S-28 behoben) — sie laeuft fuer **jeden** Body, wird aber weiterhin **vor** dem Converter gelesen (`:124`). Ein Mandantenwechsel ist **nicht** moeglich: `fail-on-unknown-properties: true` (`application.yml:55`) lehnt ein unbekanntes Feld mit 422 ab. |

### 4.3 Secrets — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Secret-Scan ueber `src/`, `tests/`, `.github/`, `Dockerfile` | **kein Fund** | `grep -rInE "(api[_-]?key\|secret\|password\|token\|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ Dockerfile` → **2** Treffer, beide Test-Fixtures: `tests/unit/frontend/EndpointsPage.test.tsx:177` und `:224` (`'s3cr3t-rotationswert'` — derselbe Wert, den auch `AbstractApplicationIT.DEFAULT_SECRET` verwendet). Keine produktiven Geheimnisse. |
| Keine Schluessel in der **Konfiguration** | **erfuellt** | `application.yml`, `application-staging.yml`, `application-production.yml` enthalten ausschliesslich `${ECHobox_…}`-Verweise und Betriebswerte. Belegt: `EnvironmentSeparationIT.no_configuration_contains_a_hardcoded_secret` (gruen). |
| Keine Schluessel im **Image** | **erfuellt** | `Dockerfile:63-65` — `ENV` enthaelt nur Profil, Port und JVM-Optionen; der `COPY` (`:51`) bringt nur das JAR. |
| Keine Schluessel in der **CI** | **erfuellt** | `deploy.yml:128-134`, `:156-162` — nur **Namen** (`${{ secrets.… }}`), keine Werte im Repository; kein Schritt gibt Werte aus. |
| `.env` in `.gitignore` | **erfuellt** | `.gitignore` — `.env` (und `/test-results/`, `/playwright-report/`). |
| **Keine Schluessel in der Git-Historie** | **erfuellt** | `git log --all -p -- . ':!docs' ':!.agent' \| grep -InE "(api[_-]?key\|secret\|password\|token)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{12,}"` → **keine** Treffer. |
| Fehlermeldung bei fehlender Konfiguration | **erfuellt** | `CoreConfiguration.java:58-64` (ohne Schluessel und `key-required: true` bricht der Start ab), `:71-79` (falsche Laenge → klarer Startfehler), `SecurityConfiguration.java:107-112` (ohne JWKS-Adresse wird jedes Token abgelehnt; belegt durch `S-HARD-6`), `AudiencePolicy.java:28-36` (ohne Zielgruppe startet der Dienst nicht). **Ausnahme:** die *Dokumentation* dieser Zusage ist veraltet → **S-35**. |

### 4.4 Injection — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Alle Queries **parametriert** | **erfuellt** | Ausschliesslich `NamedParameterJdbcTemplate` mit benannten Parametern (`common/SqlParameters.java`); `grep` ueber die fuenf Repositories nach SQL-Text in einer Verkettung → **0** Treffer. Kein String-Concatenation in SQL. |
| Keine dynamisch zusammengesetzten SQL-Fragmente | **erfuellt** | Die einzigen `String.format`-Stellen sind die statischen Spaltenlisten `DeliveryRepository.java:74,133,164` — das Argument ist die **Konstante** `RUN_COLUMNS` (`:48-53`). Alle `ORDER BY`-Klauseln sind fest verdrahtet. |
| Kein `Runtime.exec` / `os.system` mit Eingabe | **erfuellt** | `grep -rn "Runtime.getRuntime\|ProcessBuilder\|os.system" src/main/java/` → **keine** Treffer. |
| Kein Path-Traversal | **erfuellt** | `grep -rn "new File(\|Files\.\|Path.of\|FileInputStream" src/main/java/` → **keine** Treffer. Der Dienst verarbeitet **keine** Dateipfade aus Eingaben; die Ziel-URL wird als `URI` benutzt (`delivery/DeliveryHttpClient.java:103`). |

### 4.5 Ausgabe / XSS — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Kein `dangerouslySetInnerHTML` / `v-html` / `\| safe` | **erfuellt** | `grep -rn "dangerouslySetInnerHTML\|v-html\|innerHTML\|eval(\|new Function(" src/main/frontend/src/ src/main/java/` → **keine** Treffer. React escaped standardmaessig. |
| Fehlermeldungen geben **keine** internen Details preis | **erfuellt** | Es gibt **eine** Fehlerabbildung (`common/GlobalExceptionHandler.java`), die ausschliesslich `code`, `title`, `status`, `detail` und `instance` (den **Pfad**, ohne Query-String) erzeugt (`common/ProblemFactory.java:69-90`); Stacktraces, SQL und Pfade tauchen nicht auf. `application.yml:72-74` — `include-stacktrace: never`, `include-message: never`, `include-binding-errors: never`. Belegt: `S-HARD-5` (**gruen**) und **neu** `ErrorMappingLeakTest` (7 Tests, gruen) — auch der **unerwartete** Fehler (500) nennt weder Stacktrace noch Klasse noch Pfad. |
| Nutzlast und Secrets erscheinen in keiner Ausgabe | **erfuellt** | `event/EventRepository.java` gibt niemals `payload_ciphertext` aus; `endpoint/EndpointResponse` fuehrt kein Secret; `PayloadCipher.Ciphertext.toString()` (`crypto/PayloadCipher.java:177-179`) gibt **nur Laengen** aus. |
| Fehler des Aufrufers werden als Serverfehler gemeldet | **erfuellt** (S-30 behoben) | `GlobalExceptionHandler.java:246-253` und `:267-274` — ein nicht unterstuetzter Medientyp und ein Multipart-Body ergeben **400 `malformed_request`**, **ohne** Stacktrace im Protokoll (DEBUG ohne Exception-Objekt). Belegt: `ErrorMappingLeakTest` (gruen), `MediaTypeErrorIT` (6, gruen), `SecurityGapRound2IT` (2, gruen). **Ausnahme:** eine nicht unterstuetzte **Methode** hat keinen eigenen Fall → **S-36** (Medium). |

### 4.6 Session und Transport — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Sitzung | **keine** | `SecurityConfiguration.java:68` — `SessionCreationPolicy.STATELESS`; `:67` — CSRF abgeschaltet (bei einem reinen Bearer-Token ohne Cookie im Browser korrekt und im Code kommentiert). |
| Cookie-Flags der Oberflaeche | **erfuellt** | `src/main/frontend/src/lib/session.ts:115-123` — `httpOnly: true`, `sameSite: 'lax'`, `path: '/'`, `secure` **nur** bei `NODE_ENV === 'production'`. Das Bearer-Token liegt im **httpOnly**-Cookie, **nicht** im Browser-Speicher (`:9-17`). |
| Security-Header | **erfuellt** | Die Spring-Security-Defaults der Kette liefern `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` und `Cache-Control: no-cache, no-store, …`. Belegt: `S-HARD-2` (**gruen**) — alle drei Header werden geprueft. |
| HSTS / TLS | **offene Frage (Q-1)** | HSTS setzt Spring Security nur bei **HTTPS**-Anfragen. Der Container spricht **HTTP** auf Port 8080 (`Dockerfile:55`); TLS wird **vor** dem Dienst terminiert. → **Q-1**, nicht befundbar ohne die Bereitstellungstopologie (Schritt 9). |
| CORS | **erfuellt (vertretbar)** | Es existiert **keine** CORS-Konfiguration → kein `Access-Control-Allow-Origin: *`, keine CORS-Antwortkopfzeilen. Der Browser ruft den Dienst nicht direkt: die Oberflaeche ist ein Next.js-Server, der ueber einen **Same-Origin-Rewrite** weiterleitet (`next.config.mjs`, `rewrites()` auf `/api/v1/:path*`) und den Dienst **serverseitig** spricht (`apiClient.ts`). Voraussetzung fuer den Betrieb: `ECHOBOX_API_BASE_URL` muss eine **interne** Adresse sein. |

### 4.7 Rate-Limiting und Ressourcen — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Ratenbegrenzung **ausgehend** (je Endpunkt) | **erfuellt** | F-01: 60 Versuche je Endpunkt und Minute, konfigurierbar (`application.yml:135-139`), Zaehler je Fenster in `schema.sql:345-356`, durchgesetzt in `delivery/RateLimitGuard.java` und `delivery/EndpointRateLimiter.java`. Belegt: `RateLimiterTest` (6), `RateLimitedDeliveryIT` (5) — gruen. |
| Ratenbegrenzung **eingehend** (API) | **erfuellt** | `tenant/ApiRateLimitFilter.java:42` (hinter der Security-Kette) + `common/ApiRateLimiter.java` (je Mandant und Fenster). **Grenzen:** `echobox.rate-limit.limit` (Vorgabe 240) und `window-seconds` (60) — aus der Konfiguration, kein Wert im Code verdrahtet. **Fehlerverhalten:** **429** `rate_limit_exceeded` mit dem `Problem`-Schema des Vertrags (`:68-72`). **`Retry-After`:** ganzzahlige Sekundenzahl >= 1, **nie kuerzer** als die Wartezeit bis zum Fensterende (belegt: `ApiRateLimitHardeningTest.the_retry_after_is_a_waiting_time_in_seconds`, gruen) — vertragskonform (`openapi.yaml:1826-1830`: `type: integer, minimum: 1`; `RateLimitExceeded` mit `headers.Retry-After` + `Problem`, `:1986-1994`). **Mandantenbezug:** der Schluessel ist die `organisation_id` aus der Identitaet (`:85-98`); ein zweiter Mandant bleibt unberuehrt (belegt: `ApiRateLimitIT.an_other_tenant_is_not_affected`, gruen). **Bypass:** nicht angemeldete Anfragen werden nicht gezaehlt (401 vor dem Filter) — der Gesundheitsendpunkt bleibt erreichbar (belegt: `ApiRateLimitIT.the_health_endpoint_is_not_rate_limited`, gruen); ein Token **ohne** Mandant wird ebenfalls nicht gezaehlt → **Q-9** (offene Frage, kein Befund). **Randbedingung:** der Zaehler liegt im Speicher der Instanz — bei *n* Instanzen wirkt das Limit *n*-fach (**Q-8**, offene Frage). **Fenster:** fest (nicht gleitend). |
| **1-MB-Grenze** der Nutzlast durchgaengig | **erfuellt** | `event/PayloadSize.java:17` (1 048 576 Byte), geprueft in `event/EventService.java:123-124` → HTTP 413; DB-Constraints `events_payload_size_ck` und `events_payload_ciphertext_ck` (`schema.sql:181-184`); Vertrag `openapi.yaml:86`. Belegt: `S-HARD-3` (**gruen**). |
| Grenze des **Request-Bodies** | **erfuellt** (S-28 behoben) | Die Grenze von 2 MiB (`event/PayloadSize.java:20`) haengt **nicht** am Medientyp, sondern an der **Methode** (`TenantParameterFilter.mayCarryBody`, `:146-149`); sie wird **begrenzt gelesen** (`:117`, `readBounded` `:165-184`) — unabhaengig von `Content-Length`, von `Transfer-Encoding: chunked` und vom Medientyp. Belegt: **`RequestBodyBoundaryAuditIT` (15 Tests, gruen)** — 6 Medientypen (mit `Content-Length`) → 413, 4 Medientypen (chunked) → 413, ohne `Content-Length` → 413, ohne `Content-Type` → 413, gzip-Bombe → 4xx (keine Dekomprimierung), Body knapp unter der Grenze → 202, GET mit ueberdimensioniertem Body → Dienst antwortet weiter. Ferner `RequestBodyLimitIT` (2, gruen), `SecurityGapIT` (2, gruen), `RequestBodyMediaTypeLimitIT` (8, gruen), `SecurityGapRound2IT` (2, gruen). |
| Greift die Grenze bei **chunked**, **Multipart**, **gzip**, **ohne `Content-Length`**? | **gemessen** | *chunked*: grenzt (gruen, 4 Medientypen). *ohne `Content-Length`* (HTTP/1.1, Body bis Connection-Close): `getContentLengthLong()` = -1 → `readBounded` begrenzt → **413** (gemessen, roher Socket). *Multipart*: `spring.servlet.multipart.enabled: false` (`application.yml:60-65`) → **kein** Multipart-Resolver; der Container parst den Body nicht; die Grenze greift vor jeder Auswertung → **413**. *gzip*: **nichts** dekomprimiert Request-Bodies (kein `ContentEncoding`-Filter im Code — grep) → die GZIP-Bombe erreicht den Parser als Rohbytes → **4xx**, **keine** Speicher-Gefahr (gemessen: 20 MiB entpackt, 20 KiB komprimiert → 4xx, Dienst antwortet danach weiter). *GET mit Body*: der Body wird nicht gelesen; `max-swallow-size: 2MB` (`application.yml:80`) begrenzt den Rest — **gemessen: HTTP 200, der Dienst antwortet danach weiter** (kein Heap-Zugriff). |
| Unbegrenzte Paginierung verhindert | **erfuellt** | `limit` ist auf 1…200 begrenzt (`endpoint/EndpointController.java:179-187`), der Vertrag ebenso (`openapi.yaml:1752-1760`). Die Paginierung ist **cursor-basiert** (kein Offset) — `common/CursorCodec.java`, HMAC-signiert, zeitkonstant geprueft (`:84`). |
| Antwort-Body des Empfaengers begrenzt | **erfuellt** | `delivery/DeliveryHttpClient.java:135-187` — `LimitedBodySubscriber`, 1 MiB. Belegt: `SecurityGapTest` (gruen), `DeliveryResponseLimitTest` (3 Tests, gruen). |
| Zeitlimits der Zustellung | **erfuellt** | `application.yml:142-143` — 5 s Verbindungs-, 10 s Lese-Timeout (ADR-008). |

### 4.8 Abhaengigkeiten und Container — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| SCA-Scan (bekannte kritische/hohe Luecken) | **17 Java-Advisories, alle nicht erreichbar** | Eigene OSV-Abfrage ueber `mvn -B -o dependency:tree` (110 Koordinaten) → **17** (Runde 2: 20). Die **drei** Tomcat-Advisories sind **nicht** mehr enthalten (Pin 10.1.61) — unabhaengig belegt: `tomcat-embed-core:10.1.55` → 3, `…:10.1.61` → **0**. Die verbleibenden 17 sind mit Begruendung je Advisory in **Anhang A** bewertet. |
| Frontend-Abhaengigkeiten | **kein Laufzeitbefund** | Eigene OSV-Abfrage ueber `package-lock.json` (523 Pakete) → **9** Advisories, **alle** in der Baseline. `npm audit --json` (Runde 2): 10 Meldungen — **2 Critical** (`tinypool`, `vitest`), **6 High**, **2 Moderate**; **alle** betreffen `devDependencies` **ausser** `postcss 8.4.31`, das `next 15.5.28` als *Laufzeit*-Abhaengigkeit mitliefert → **O-05** (offener Punkt, befristet ausgenommen, kein Befund). |
| Baseline des SCA-Gates vorhanden und gepflegt | **erfuellt** (S-32 behoben) | `osv-scanner.toml` — 26 Eintraege, je mit `id`, `reason` und `ignoreUntil` (2027-04-30); die Pipeline uebergibt sie (`deploy.yml:56-64`). **Eigener Nachweis:** die aktuelle Advisory-Menge (17 + 9 = 26) ist **exakt** die Baseline-Menge — **0 neu**, **0 verwaist**; das Format entspricht osv-scanner **v2.7.0** (`internal/config/config.go`) und die Option `--config` existiert in dieser Version (`internal/helper/flags.go`). Belegt: `ScaBaselineIT` (3, gruen). |
| Container laeuft als **non-root** | **erfuellt** | `Dockerfile:45-46` (uid/gid 10001, ohne sudo/Root-Rechte), `:53` (`USER 10001:10001`). |
| **Gepinnte** Basis-Image-Tags | **erfuellt** | `Dockerfile:17` (`maven:3.9.9-eclipse-temurin-21`), `:29` (`eclipse-temurin:21.0.5_11-jre-jammy`) — feste Versionen, **kein** `latest`. |
| Multi-Stage, minimale Images | **erfuellt** | `Dockerfile:17-26` (Build) / `:29-71` (Laufzeit). Im Laufzeitimage liegt nur `/app/echobox.jar` — kein Maven, kein Quellcode, kein `.git`. |
| Keine unnoetigen Ports | **erfuellt** | Nur `EXPOSE 8080` (`Dockerfile:55`); die Datenbank ist nicht im Image. |
| Keine Secrets im Image | **erfuellt** | Siehe 4.3. |
| Health-Check | **erfuellt** | `Dockerfile:67-68` — `curl -fsS http://127.0.0.1:8080/health`. |
| Gepinnte `apt`-Pakete | **erfuellt** | `Dockerfile:36-42` — `curl=${CURL_VERSION}` (7.81.0-1ubuntu1.30) und `tini=${TINI_VERSION}` (0.19.0-1) mit Vorgabewerten. Belegt: `ContainerHardeningIT.the_apt_packages_are_pinned` (gruen). |
| Produktionsprofil | **erfuellt** | `src/main/resources/application-production.yml:25` (`key-required: true`), `Dockerfile:63` (`SPRING_PROFILES_ACTIVE=production`), `deploy.yml:127` (staging) und `:155` (production). Belegt: `ContainerHardeningIT` (8, gruen). **Betriebshinweis (aus S-29):** die Zielgruppe muss in beiden Umgebungen gesetzt sein — sonst startet der Dienst nicht. |
| CI: keine Secrets in Logs | **erfuellt** | `deploy.yml:128-134`, `:156-162` — Geheimnisse ausschliesslich als `env`; kein Schritt gibt Werte aus. |
| CI: OIDC statt statischer Keys | **erfuellt** | `deploy.yml:99-107` — `password: ${{ secrets.GITHUB_TOKEN }}` (kurzlebig, pro Lauf), `permissions: contents: read, packages: write, id-token: write` (`:31-35`). Der statische `REGISTRY_TOKEN` wird **nicht mehr verwendet**. |
| CI: Secret-Scan, SCA-Scan, Oberflaechen-Tests | **vorhanden und kalibriert** | `deploy.yml:51-54` (gitleaks v8.30.1, gepinntes Image), `:56-64` (OSV-Scanner v2.7.0 **mit** Baseline), `:64-65` (Java inkl. Coverage-Gate), `:79-93` (Typpruefung, Lint, Vertragspruefung, Unit-Tests, Build, Playwright). |
| Geschuetzte Branches | **nicht pruefbar** | Branch-Schutz ist eine Einstellung des Repository-Hosts, nicht des Codes → **offene Frage Q-2** (Schritt 9). |

### 4.9 Logging und Datenschutz — **kein Fund**

| Teilprüfung | Ergebnis | Beleg |
|---|---|---|
| Keine Passwoerter, Tokens oder **vollstaendigen Nutzlasten** in Logs | **erfuellt** | Alle **32** Logstellen in `src/main/java/` (grep `LOG.info\|warn\|error\|debug`) geben **nur Metadaten** aus: Kennungen, Mandant, Zaehler, Zeiten, Status (`event/EventService.java:214`, `delivery/DeliveryDispatcher.java:247`, `endpoint/EndpointService.java:173`). **Kein** Klartextpayload, **kein** Secret-Wert, **kein** Token. FR-012 / AC-007-2 sind eingehalten. |
| Audit-Protokoll ohne Nutzlastinhalt | **erfuellt** | `schema.sql:383-399` — `details JSONB` ohne Nutzlast; `common/AuditLogRepository.java` — ausschliesslich Metadatenfelder. |
| Retention dokumentiert | **erfuellt** | F-02: 30 Tage je Datenart, Anker `last_attempt_at`; `ADR-012`, `application.yml:153-160`, `schema.sql:364-380` (Override je Mandant). Die Durchsetzung laeuft mandantenuebergreifend ueber die Eigentuemerfunktion (`schema.sql:699-858`) in zwei Stufen (markieren → Purge). **Das Schema ist seit Runde 1 unveraendert** (beide Spiegel byte-identisch, `git diff` gegen `8627bb6` leer). |
| Recht auf Loeschung | **technisch abgebildet, fachlich offen** | Die Purge-Stufe loescht Ereignisse, Zustelllaeufe und Versuche physisch (`schema.sql:833-841`); die Kaskade ueber `ON DELETE CASCADE` nimmt die Protokollzeilen mit. Ein **mandantenspezifisches Sofort-Loesch-Kommando** gibt es nicht → **offene Frage Q-3** (Recht/Fachbereich, Gate 2), **kein** Befund. |
| Karenzzeit R-01 / E-13 | **offen** | `schema.sql:704-709` — **kein** Vorgabewert; ohne gesetzten Wert wird mit 0 Tagen gerechnet. Bewusst so entschieden und im Vertrag nicht festgeschrieben → offener Punkt des Fachbereichs (Gate 2). |
| Fehler des Aufrufers erzeugen Stacktraces im Protokoll | **teilweise erfuellt → S-36** | `GlobalExceptionHandler.java:248` und `:269` loggen die **erwartbaren** Fehler (Medientyp, Multipart) auf **DEBUG ohne Exception-Objekt**; nur **unerwartete** Fehler bleiben im `ERROR`-Protokoll mit Stacktrace (`:285`). Ein Fehler des Aufrufers (nicht unterstuetzter Medientyp, Multipart, Token ohne Mandant) erzeugt **keinen** Stacktrace mehr. **Offen:** `HttpRequestMethodNotSupportedException` hat keinen eigenen Fall → **500 + Stacktrace** (auch ohne Anmeldung ueber `POST /health`) → **S-36** (Medium). |

### 4.10 OWASP Top 10 (Querpruefung)

| ID | Kategorie | Ergebnis | Beleg / Befund |
|---|---|---|---|
| **A01** | Broken Access Control | **kein Fund** | RLS auf allen zehn fachlichen Tabellen (10 × `ENABLE ROW LEVEL SECURITY`, 10 × `CREATE POLICY`), `WITH CHECK`, fail-closed, kein `BYPASSRLS` vergeben; Besitzpruefung auf **jeder** Objekt-Route (404 vor 403); die mandantenuebergreifende Sicht ist auf **drei** schmale Eigentuemerfunktionen begrenzt, fail-closed und ohne Klartextprojektion; `permitAll` nur auf den drei Health-Pfaden. Belegt: `ForeignResourceIsolationIT` (11), `RoleCapabilityMatrixIT` (84), `RowLevelSecurityIT` (4), `TenantIsolationIT` (6) — alle gruen. Die **Restluecke** E-12 (die Applikationsrolle kann `app.cross_tenant` setzen) ist dokumentiert (`docs/adr/README.md:96`) und ein offener Punkt, kein neuer Befund. |
| **A02** | Cryptographic Failures | **kein Fund** | AES-256-GCM mit 12-Byte-Nonce und 128-Bit-Tag (`crypto/PayloadCipher.java:80-106`), HMAC-SHA256-Fingerprint mit abgeleitetem Teilschluessel (`:119-138`), zeitkonstante Vergleiche (`SignatureService.java:68`, `CursorCodec.java:84`), Schluessel **ausserhalb** der Datenbank, keine Eigenimplementierung. **S-22 bleibt behoben** (die Schluessellaenge wird geprueft, kein AES-128-Downgrade). |
| **A03** | Injection | **kein Fund** | Ausschliesslich benannte Parameter; keine SQL-Konkatenation; keine Befehlsausfuehrung; keine Dateipfade aus Eingaben. Belegt: grep ueber alle fuenf Repositories (0 Treffer). |
| **A04** | Insecure Design | **kein Fund** (S-28 behoben) | Das Design trennt Mandant (RLS) und Rolle (Anwendung) in der **richtigen** Reihenfolge; die mandantenuebergreifende Sicht ist auf drei schmale Funktionen begrenzt; die Replay-Regel ist ueber eine DB-Constraint durchgesetzt (`schema.sql:244-245`). **S-28 ist behoben:** die Grenze haengt jetzt an der **Methode** und am **begrenzten Lesen vor dem Converter** — nicht mehr an einer Eingabevariante (dem Medientyp). Ein Sicherheitsprinzip, das an einer Variante des Eingangs haengt, ist per Konstruktion umgehbar; das ist jetzt behoben. |
| **A05** | Security Misconfiguration | **1 Medium (S-36)** | Fehlermeldungen ohne internes Detail (`include-stacktrace: never`, `include-message: never`, `include-binding-errors: never`), `management.endpoints.web.exposure.include: health,info,metrics` mit `show-details: never` (`application.yml:85-92`) — `/actuator/metrics` bleibt **hinter** der Authentifizierung (belegt durch `S-HARD-7`). S-29 (fail-open), S-30 (500 statt 4xx) und S-33 (500 statt 401) sind **behoben**; S-26/S-27 bleiben behoben. **S-36:** die *Methode* hat noch keinen eigenen Fehlerfall → 500 + Stacktrace (auch ohne Anmeldung). *Rest:* die Dokumentationsabweichung **S-35** (Low). |
| **A06** | Vulnerable and Outdated Components | **kein Fund** | 96 → **20** (Runde 1) → **17** (Runde 2) → **17** (heute, Runde 3, eigene Abfrage) Java-Advisories + **9** npm-Advisories; die 3 zuvor erreichbaren sind behoben, die 3 Tomcat-Advisories sind mit dem Pin **10.1.61** ebenfalls behoben. Die verbleibenden 17 sind **in dieser Konfiguration nicht erreichbar** (Anhang A). Der SCA-Gate ist **kalibriert** (S-32). |
| **A07** | Identification and Authentication Failures | **kein Fund** | 401 statt 403/500; RS256-only; `none` und HS256 abgelehnt; Ablauf (`exp`) **und** `nbf` geprueft, `exp` Pflicht; kein eigener Passwortpfad. **S-29 ist behoben:** die Zielgruppen-Pruefung ist **immer** aktiv, eine leere Konfiguration verhindert den Start (fail-closed). |
| **A08** | Software and Data Integrity Failures | **kein Fund** | Signaturverfahren und Header-Name sind im Vertrag eingefroren und durch einen **Vertragstest** abgesichert (`tests/contract/SignatureContractTest.java`, 6 Tests, gruen); der Idempotenzvergleich laeuft ueber einen gezielten HMAC-Fingerprint statt ueber einen ungeschuetzten Hash (ADR-007); die Idempotenz-Constraint liegt in der Datenbank (`schema.sql:173-174`). Beide Schema-Spiegel sind **byte-identisch** mit `.agent/knowledge/schema.sql` (geprueft: „Files are identical"). |
| **A09** | Security Logging and Monitoring Failures | **1 Medium (S-36)** | Audit-Protokoll fuer die nachvollziehbaren Aktionen (`app.audit_log`, `schema.sql:383-399`); Betriebsprotokoll enthaelt nur Metadaten; Readiness/Health melden den Zustand, nicht die Daten. **S-30/S-33 sind behoben** — Fehler des Aufrufers landen nicht mehr als 500er mit Stacktrace im Protokoll. **S-36:** die nicht unterstuetzte **Methode** landet weiterhin als 500er mit Stacktrace im Protokoll — und zwar **ohne Anmeldung** (`POST /health`), unbegrenzt (das Anfragelimit zaehlt je Mandant, **Q-9**). |
| **A10** | Server-Side Request Forgery (SSRF) | **kein Fund; eine offene Frage** | Die Ziel-URL ist **Eingabe** des Mandanten — das ist das Produkt. Die wirksamen Begrenzungen: Schema **nur** `https` (`endpoint/EndpointTargetUrl.java:52-55`, zusaetzlich DB-Constraint `endpoints_target_url_ck`, `schema.sql:100-101`), **keine** eingebetteten Zugangsdaten (`:56-60`, DB-Constraint `:102-103`), **keine** Weiterleitung (`delivery/DeliveryHttpClient.java:80`, `Redirect.NEVER`), **kein** Trust-all bei TLS, Zeitlimits (5 s / 10 s), Antwortgrenze 1 MiB. **Offene Frage Q-4:** ein Filter gegen interne Adressbereiche (Link-Local `169.254.0.0/16`, RFC1918) fehlt — fuer ein Webhook-Produkt ist das eine **bewusste** Entscheidung, aber eine Sichtbarkeitsfrage und keine belegte Luecke → **kein Befund**. |

---

## Anhang A — SCA: die verbleibenden Advisories und ihre Erreichbarkeit

Eigene Abfrage ueber OSV `querybatch` (2026-10-22) auf dem **aktuellen** Baum:
**110** Maven-Koordinaten (`mvn -B -o dependency:tree`) → **17** Advisories;
**523** npm-Pakete (`package-lock.json`) → **9** Advisories.
**Gesamt 26** — **exakt** die 26 Eintraege der Baseline `osv-scanner.toml`
(**0 neu**, **0 verwaist**).

### A.1 Tomcat (`tomcat-embed-core:10.1.61`) — 0 → **behoben**

| Advisory | Schwere | Betroffen | Behoben in | Stand heute |
|---|---|---|---|---|
| GHSA-9xv2-5v5q-p794 | Critical | 10.1.0-M1 … 10.1.57 | **10.1.58** (veroeffentlicht ab 10.1.59) | **nicht mehr enthalten** (Pin 10.1.61) |
| GHSA-gcx9-497g-6cp6 | Critical | 10.1.0-M1 … 10.1.57 | **10.1.58** | **nicht mehr enthalten** |
| GHSA-h3x4-894j-xpx5 | Critical | 10.1.0-M1 … 10.1.57 | **10.1.58** | **nicht mehr enthalten** |

> **Eigener Nachweis:** `querybatch` → `tomcat-embed-core:10.1.55` ⇒ `[GHSA-9xv2-5v5q-p794,
> GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5]`; `…:10.1.61` ⇒ `[]`.

### A.2 Jackson (`jackson-core:2.21.4`, `jackson-databind:2.21.4`) — 11 → nicht erreichbar

| Advisory-Familie | Anzahl | Warum nicht erreichbar |
|---|---|---|
| ReDoS: quadratisches Backtracking in `NumberInput.PATTERN_FLOAT` (GHSA-p6pp-m3f8-5c89, HIGH) | 1 | **Empirisch gemessen** (Runde 2, unveraendert): `StreamReadConstraints.maxNumberLength` (Vorgabe 1000) bricht lange Zahlen **vor** dem Regex ab; der Parse-Pfad scheitert mit `StreamConstraintsException` nach 30 ms. |
| `UTF8DataInputJsonParser` ohne `maxErrorTokenLength` (GHSA-7hhh-6rmp-j9qf, HIGH) | 1 | Betrifft nur `createParser(DataInput)`; Spring MVC liest ueber `InputStream`/`byte[]` (grep: keine `DataInput`-Nutzung). |
| `@JsonView`-Umgehungen (GHSA-5gvw-p9qm-jgwh, GHSA-mhm7-754m-9p8w) | 2 | Setzen `@JsonView` **und** `@JsonUnwrapped` bzw. `@JsonTypeInfo(EXTERNAL_PROPERTY)` voraus — grep: **keine** Treffer. |
| `@JsonTypeInfo` / `DefaultBaseTypeLimitingValidator` (GHSA-gx83-3vf8-gh7j, GHSA-wv8q-qhhj-9h54) | 2 | Kein `@JsonTypeInfo`, kein `PolymorphicTypeValidator`, kein `activateDefaultTyping`. |
| `InetAddress`/`InetSocketAddress` (GHSA-vvgp-rfg2-7rr6) | 1 | Keine `java.net.InetAddress`/`InetSocketAddress` in den Request-Bodies. |
| `java.nio.file.Path` / `FileSystemProvider` (GHSA-wjgm-6hv5-3cvf) | 1 | Kein `java.nio.file.Path` in den Request-Bodies. |
| `Duration`/`XMLGregorianCalendar` (GHSA-q4xh-88c3-wmh7) | 1 | Keine solchen Feldtypen in den DTOs. |
| quadratische Forward-Reference-Aufloesung (GHSA-cxp5-3px4-pw24) | 1 | Setzt `@JsonIdentityInfo` voraus — nicht verwendet. |
| case-insensitive `@JsonIgnoreProperties` (GHSA-5jmj-h7xm-6q6v) | 1 | Setzt `@JsonIgnoreProperties` auf Properties voraus — nicht verwendet. |

### A.3 Spring MVC (`spring-webmvc:6.2.19`) — 2 → nicht erreichbar

| Advisory | Schwere | Erreichbarkeit |
|---|---|---|
| GHSA-pc63-qcmh-9cmg (XsltView → SSRF/RCE) | Critical | Setzt `XsltView` und eine `/**`-View-Aufloesung voraus — der Dienst liefert **ausschliesslich** JSON (`@RestController`), es gibt keine View-Aufloesung. |
| GHSA-j9f9-w8pj-32f8 (SSE-Stream-Verfaelschung mit View-Fragmenten) | Critical | Setzt Server-Sent Events **mit** View-Fragmenten voraus — der Dienst hat keine SSE-Endpunkte. |

### A.4 Uebrige — 4 → nicht erreichbar (Test-/Build-Werkzeug, Datenbankweg, Logging)

| Advisory | Schwere | Erreichbarkeit |
|---|---|---|
| GHSA-4265-ccf5-phj5, GHSA-4g9r-vxhx-9pgx (`commons-compress:1.24.0`) | Moderate | Pack200-/DUMP-Dateien — betrifft die Datei- bzw. Archivverarbeitung im Test-/Build-Werkzeug, nicht den Request-Pfad. |
| GHSA-qv9r-c865-cp47 (`log4j-api:2.24.3`) | Moderate | Log4j ist **nicht** die Logging-Implementierung (logback); betrifft `MapMessage.asJson()`. |
| GHSA-j92g-9f8w-j867 (`postgresql:42.7.11`) | High | Kanalbindung (`channelBinding=require`) ist nicht konfiguriert; der Angriff setzt einen Angreifer auf dem **Datenbankweg** voraus. |

### A.5 npm — 9 → nicht erreichbar (Test-Werkzeug bzw. CSS-Parser beim Build)

| Advisory | Paket | Erreichbarkeit |
|---|---|---|
| GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr | `tinypool` 1.1.1 (dev) | Fehler im Test-Werkzeug — nur ausnutzbar mit Schreibrecht auf dem Repository. |
| GHSA-82fw-gwwq-j7x9, GHSA-qxg6-89mg-rqv8 | `vitest` / `@vitest/mocker` 3.2.7 (dev) | dito |
| GHSA-vfj7-8cjw-p6xm | `braces` 3.0.3 (dev, transitiv ueber eslint/vite) | dito |
| GHSA-qx2v-qp2m-jg93, GHSA-6g55-p6wh-862q, GHSA-fxqj-rqcc-2cmp, GHSA-r28c-9w8g-f849 | `postcss` 8.4.31 (**Laufzeit**-Abhaengigkeit von `next` 15.5.28) | betrifft den CSS-Parser beim Build; der Dienst nimmt keine CSS-Eingaben entgegen → **O-05** (befristet ausgenommen, Behebung mit dem naechsten `next`-Upgrade). |

> **Hinweis zur Vollstaendigkeit:** die Erreichbarkeits-Einstufung ist eine
> komponentenbezogene Analyse des tatsaechlichen Stacks. Sie ist kein Ersatz
> fuer einen Hersteller-Scan des fertigen Images in der Zielumgebung — der
> gehoert in **Schritt 9**.

---

## Anhang B — Nachweiskette: jeder Befund ist festgenagelt

| Befund | Test / Check | Datei | Ergebnis | Status |
|---|---|---|---|---|
| **S-28** (Body-Grenze) | `RequestBodyBoundaryAuditIT` (**neu, 15**) | `tests/integration/security/RequestBodyBoundaryAuditIT.java` | 6 Medientypen (Content-Length) → 413 · 4 Medientypen (chunked) → 413 · ohne `Content-Length` → 413 · ohne `Content-Type` → 413 · gzip-Bombe → 4xx, Dienst antwortet weiter · knapp unter der Grenze → 202 · GET mit uebergrossem Body → 200, Dienst antwortet weiter | **gruen (15/15)** |
| **S-28** (Medientyp) | `RequestBodyMediaTypeLimitIT` (8), `SecurityGapRound2IT` (2) | `tests/integration/security/` | gruen | **gruen** |
| **S-29** (Audience) | `AudienceFailClosedPathsTest` (**neu, 4**), `AudienceFailClosedTest` (4), `JwtAudienceHardeningTest` (4), `SecurityGapRound2Test` (2) | `tests/unit/security/` | gruen | **gruen** |
| **S-30** (Fehlerabbildung) | `ErrorMappingLeakTest` (**neu, 7**), `MediaTypeErrorIT` (6), `SecurityGapRound2IT` (2) | `tests/unit/common/`, `tests/integration/security/` | gruen | **gruen** |
| **S-31** (Container) | OSV `querybatch` (10.1.55 → 3, 10.1.61 → 0) + `DependencyHardeningTest` (2) | `pom.xml:65` | 10.1.61 ≥ 10.1.59 | **gruen** |
| **S-32** (SCA-Baseline) | eigene OSV-Abfrage (26 = 26, 0 neu, 0 verwaist) + `ScaBaselineIT` (3) | `osv-scanner.toml` | kalibriert | **gruen** |
| **S-33** (Mandant) | `ErrorMappingLeakTest` (1), `MissingTenantIT` (1) | `tests/unit/common/`, `tests/integration/security/` | 401 | **gruen** |
| **S-34** (Javadoc) | `DeliveryStateMachineDocumentationTest` (2) | `tests/unit/delivery/` | gruen | **gruen** |
| **S-35** (Doku) | `AudienceFailClosedPathsTest` (4) — das **Verhalten**; die **Aussage** ist der Befund | `tests/unit/security/` | Verhalten gruen, Doku falsch | **Low, offen** |
| **S-36** (Methode) | `UnsupportedMethodErrorIT` (**neu, 2**) + Sonde ueber 15 Methode/Pfad-Kombinationen | `tests/integration/security/UnsupportedMethodErrorIT.java` | Antwort ohne internes Detail (**gruen**); Status 500 statt 4xx (**offen**) | **Medium, offen** |
| Abwehr: 401, Header, 1-MiB, 2-MiB, keine internen Details, Mandantenparameter, nur Health ohne Token | `S-HARD-1` … `S-HARD-7` | `tests/integration/security/SecurityHardeningIT.java` | gruen | **Abwehr belegt** |
| Abwehr: RS256 / kein `none` / Ablauf / Signatur / kein IdP | `S-HART-1` … `S-HART-7` | `tests/unit/security/JwtDecoderHardeningTest.java` | gruen | **Abwehr belegt** |
| **Die zehn Gap-Tests der Runden 1 + 2** | `SecurityGapTest` (4), `SecurityGapIT` (2), `SecurityGapRound2Test` (2), `SecurityGapRound2IT` (2) | `tests/unit/security/`, `tests/integration/security/` | **10/10 gruen** — kein Test wurde geaendert | **Abwehr belegt** |

---

## Anhang C — Offene Fragen (keine Befunde)

| ID | Frage | Owner | Stand Runde 3 |
|---|---|---|---|
| **Q-1** | Terminiert TLS der vorgeschaltete Proxy? Wer setzt HSTS? Der Container selbst spricht HTTP auf Port 8080. | Schritt 9 / Betrieb | **offen** |
| **Q-2** | Sind die Branches geschuetzt (kein direkter Push auf `main`)? Aus dem Repository **nicht** pruefbar. | Schritt 9 / Betrieb | **offen** |
| **Q-3** | „Recht auf Loeschung": reicht die fristbasierte Purge-Stufe, oder braucht es ein mandantenspezifisches Sofort-Loesch-Kommando? | Fachbereich + Recht, Gate 2 | **offen** |
| **Q-4** | Soll ein Endpunkt-Ziel in interne Adressbereiche (Link-Local, RFC 1918) verboten werden? Fuer ein Webhook-Produkt legitim — eine **Sichtbarkeits**-, keine Sicherheitsfrage. | Fachbereich + Plattform, Gate 2 | **offen** |
| **Q-5** | Restrisiko `jackson-core` (quadratisches Backtracking bei sehr langen Dezimalzahlen). | — | **erledigt** (Runde 2, empirisch belegt) |
| **Q-6** | Bedingungen von CVE-2026-22732 (Spring Security 6.3.4). | — | **erledigt** (Spring Security 6.5.11) |
| **Q-7** | Schluesselverwaltung nach ADR-003 §2 (je Mandant ein DEK) — ist der **globale** Schluessel die gewollte Vereinfachung? | Architektur + Fachbereich, Gate 2 | **offen** (haengt an O-04) |
| **Q-8** | Der Zaehler des Anfragelimits liegt **je Instanz** im Speicher — bei *n* Instanzen wirkt das Limit *n*-fach. Eine instanzuebergreifende Grenze braucht ein Datenbankschema. | Schritt 9 (Schema) | **offen** |
| **Q-9** | Der Rate-Limit-Filter zaehlt nur Anfragen **mit** Mandant; nicht angemeldete Anfragen (401) und Token ohne Mandant werden nicht gezaehlt. Soll es eine **globale** Schranke geben? | Fachbereich + Betrieb, Gate 2 | **offen** (S-33 ist behoben, der Zaehlschluessel bleibt bewusst „je Mandant") |
| **O-05** | `postcss 8.4.31` ist eine **Laufzeit**-Abhaengigkeit von `next 15.5.28`; vier Advisories sind befristet ausgenommen (Begruendung + Ablaufdatum in der Baseline). | Wartung / Schritt 9 | **offen** (befristet) |
| **S-35 (neu)** | Die Dokumentation der Audience beschreibt noch das alte fail-open-Verhalten. | Schritt 6/7 | **offen (Low)** |
| **S-36 (neu)** | Eine nicht unterstuetzte HTTP-Methode ergibt 500 mit Stacktrace statt 4xx (auch ohne Anmeldung). | Schritt 6/7 | **offen (Medium)** — Abnahmetest in `docs/qa/bugs/BUG-S-36.json` |

---

## Anhang D — Was dieser Audit **nicht** behauptet

* Er aendert **keine** Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/` oder `docs/requirements/`. Der eingefrorene Vertrag
  (`docs/api/openapi.yaml`, seit `8627bb6`) ist **unveraendert**
  (`git diff --stat -- docs/api/` ist leer — auch gegen `8627bb6`).
* Er behauptet **nicht**, dass die 17 nicht erreichbaren Java-Advisories unbedenklich
  sind — sie sind fuer **diese** Konfiguration nicht erreichbar, mit Begruendung je
  Advisory in Anhang A; ein Scan des fertigen Images in der Zielumgebung gehoert in
  Schritt 9.
* Er behauptet **nicht**, dass die Rate-Limit-Residuen (Q-8, Q-9) Befunde sind —
  sie sind Randbedingungen des Betriebs und offene Fragen.
* Er behauptet **nicht**, dass die SCA-Baseline die advisories *beseitigt* — sie
  **dokumentiert** sie mit Begruendung und Ablaufdatum; der Gate schlaegt bei
  **neuen** Advisories an.
* **S-35** ist ein **Dokumentations**- und kein Sicherheitsbefund: das Verhalten ist
  fail-closed, also sicherer als dokumentiert.
* **S-36** verletzt **keine** Vertraulichkeitseigenschaft: die Antwort gibt nachweislich
  kein internes Detail preis (`UnsupportedMethodErrorIT`, gruen). Offen ist die
  *Statuszuordnung* (500 statt 4xx) und die daraus folgende Protokollverstaerkung.
* Er ersetzt **nicht** die Freigabe durch den Menschen: **GATE 2** steht aus, und die
  Empfehlung dieses Audits lautet **freigeben** (0 Critical, 0 High).

---

*Erzeugt in Schritt 8 (Sicherheit & Compliance), dritte Runde. Kanonisches Artefakt:
`.agent/artifacts/security-audit.md`.*
