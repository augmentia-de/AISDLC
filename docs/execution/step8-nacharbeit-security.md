# Schritt 6/7 (Rücksprung) — Nacharbeit zu den Security-Befunden des Audits aus Schritt 8

> Agent: `aisdlc-execution` · Stand: 2026-10-22 · Quelle der Befunde:
> [`docs/qa/security-audit.md`](../qa/security-audit.md) (S-19 … S-27) und
> [`docs/qa/qa-report.md`](../qa/qa-report.md) (A-1, A-6/B-17).
>
> Der **eingefrorene Vertrag** `docs/api/openapi.yaml` ist **unveraendert**
> (`git diff --stat -- docs/api/` ist leer). S-24 ist **kein** Vertragsdefekt:
> der Vertrag sagt die 429 bereits zu — hier wird die Zusage **erfuellt**.
>
> Kanonisches Artefakt: `.agent/artifacts/step8-nacharbeit-security.md`.

---

## 1. Ergebnis in einem Satz

**Alle neun Befunde (S-19 … S-27) und beide QA-Befunde (A-1, A-6/B-17) sind
behoben**; die sechs roten Gap-Tests des Audits sind gruen, ohne dass eine
Zeile geaendert wurde. Offen bleibt allein die **Dokumentationszeile** in
Schritt 2 (die Uebergangstabelle) — der Code ist dafuer jetzt angeglichen.

| Befund | Schwere | Status | Nachweis |
|---|---|---|---|
| **S-19** | High | **behoben** | `mvn dependency:tree` → `tomcat-embed-core:10.1.55`; `DependencyHardeningTest` |
| **S-20** | High | **behoben** | `SecurityGapIT` (2 MiB chunked → 413) + `RequestBodyLimitIT` |
| **S-21** | Medium | **behoben** | `SecurityGapTest` ×2 + `JwtAudienceHardeningTest` |
| **S-22** | Medium | **behoben** | `SecurityGapTest` + `CryptoKeyLengthTest` |
| **S-23** | Medium | **behoben** | `SecurityGapTest` + `DeliveryResponseLimitTest` |
| **S-24** | Medium | **behoben** | `SecurityGapIT` (300 Anfragen → 429) + `ApiRateLimitIT` |
| **S-25** | Medium | **behoben** | `.github/workflows/deploy.yml` (Gates + OIDC-Token) |
| **S-26** | Low | **behoben** | `Dockerfile` (gepinnte `apt`-Pakete) + `ContainerHardeningIT` |
| **S-27** | Low | **behoben** | `application-production.yml` + `ContainerHardeningIT` |
| **A-1** | QA | **behoben** | JaCoCo-Gate in `pom.xml` (Zeilen ≥ 80 %) + `ContainerHardeningIT` |
| **A-6/B-17** | QA | **Code behoben**, Doku in Schritt 2 | `DeliveryStateMachine.TRANSITIONS` |

---

## 2. Die zwei High-Befunde

### 2.1 S-19 — der eingebettete Container (High)

**Der Befund.** `pom.xml:16` (Spring Boot 3.3.5 → `tomcat-embed-core:10.1.31`).
Drei im HTTP/1.1-Pfad **erreichbare** Advisories: CVE-2026-24880
(Request/Response-Smuggling, behoben in 10.1.52), CVE-2025-48988
(Multipart-DoS, behoben in 10.1.42), CVE-2025-48989 („made you reset", behoben
in 10.1.44).

**Was geaendert wurde.**

| Datei | Aenderung |
|---|---|
| `pom.xml` | Eltern-POM **3.3.5 → 3.5.16** (bringt `tomcat-embed-core` **10.1.55** mit) |
| `pom.xml` | zusaetzlich `<tomcat.version>10.1.55</tomcat.version>` — die Untergrenze ist damit unabhaengig von der Spring-Boot-Version festgeschrieben |
| `src/main/resources/application.yml` | `spring.servlet.multipart.enabled: false` — der Dienst nimmt nur JSON entgegen; ohne den Multipart-Resolver parst der Container **keinen** Multipart-Body. Der Pfad des Multipart-DoS ist damit zusaetzlich zur gehobenen Version geschlossen (Defense in Depth) |

**Warum 3.5.16 und nicht 4.x.** Spring Boot 4 (Framework 7, Jakarta EE 11) waere
ein Sprung ueber zwei major-Versionen mit Migrationsrisiko fuer den gesamten
Stack; 3.5.16 ist die **aktuelle** 3.5-Linie und bringt die geforderte Tomcat-
Version mit. Die beiden anderen lokal verfuegbaren Linien (4.0.8, 4.1.1)
bringen Tomcat 11.0.x mit — auch sie erfuellen die Untergrenze, waeren aber
der groessere Eingriff.

**Der Nachweis.**

```
$ mvn -B dependency:tree | grep tomcat-embed-core
[INFO] |  +- org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.55:compile
```

* `tests/unit/security/DependencyHardeningTest.java` (2 Tests, gruen) liest die
  Version aus dem **Klassenpfad des Tests** — also die Version, die der Build
  tatsaechlich aufloest — und prueft: ≥ 10.1.55 sowie ≥ den drei
  Fix-Versionen 10.1.52 / 10.1.42 / 10.1.44.
* `tests/integration/deployment/ContainerHardeningIT.java` prueft zusaetzlich die
  Build-Datei (`<tomcat.version>` und die Spring-Boot-Version).

**Vollstaendig behoben: ja.** Mit dem Upgrade kommen zugleich `spring-security`
6.3.4 → **6.5.11**, `spring-framework` 6.1.14 → **6.2.19** und `jackson`
2.17.2 → **2.21.4** mit — die offenen Fragen **Q-5** (Restrisiko `jackson-core`)
und **Q-6** (CVE-2026-22732 in Spring Security 6.3.4) sind damit materiell
entschaerft; sie bleiben als Beobachtung, nicht als Befund.

**Was nicht behoben werden konnte.** Die **93 nicht erreichbaren** Advisories
(Anhang A des Audits) bleiben bestehen — sie sind in dieser Konfiguration nicht
erreichbar. Sie werden jetzt **laufend** ueberwacht: der OSV-SCA-Scan der
Pipeline (S-25) meldet neue Advisories, auch fuer die noch nicht
erreichbaren. Ein Scan des **fertigen Images** in der Zielumgebung bleibt
Schritt 9 (dort ist die Image-Registry und der Betrieb bekannt).

### 2.2 S-20 — die Grenze des Request-Bodies (High)

**Der Befund.** `TenantParameterFilter.java:70,81` — die Grenze (2 MiB) wurde
**nur** gegen `Content-Length` geprueft; bei `Transfer-Encoding: chunked` ist
der Wert `-1`, die Grenze greift **nicht**, und der Body wurde mit
`readAllBytes()` **unbeschaenkt** in den Heap des geteilten JVM gelesen
(Ausfall fuer alle Mandanten).

**Was geaendert wurde.**

| Datei | Aenderung |
|---|---|
| `src/main/java/com/echobox/tenant/TenantParameterFilter.java` | der Body wird **begrenzt** gelesen: `readBounded(request, 2 MiB)` liest hoechstens 2 MiB + 1 Byte und bricht dann ab → **HTTP 413** `payload_too_large`. Die Grenze haengt **nicht** an `Content-Length`; die Frueherpruefung gegen `Content-Length` bleibt als schneller Weg erhalten (sie spart das Lesen bei klaren Faellen) |
| `src/main/resources/application.yml` | `server.tomcat.max-swallow-size`: **4MB → 2MB** — nach dem Abbruch puffert der Container hoechstens noch 2 MiB des Restkoerpers; `server.tomcat.max-http-form-post-size: 2MB` (auch fuer Form-POSTs) |

**Der Nachweis.**

* `tests/integration/security/SecurityGapIT.java` →
  `a_chunked_request_body_above_two_mebibytes_must_be_rejected`:
  **vorher** „expected: 413 but was: 202", **jetzt gruen**.
* `tests/integration/security/RequestBodyLimitIT.java` (neu, 2 Tests, gruen):
  ein chunked Body **unter** der Grenze wird angenommen (kein
  Funktionsverlust), ein chunked Body **ueber** der Grenze ergibt 413 mit dem
  Fehlerschema des Vertrags.

**Vollstaendig behoben: ja.** Der Angriff ist nicht mehr moeglich: der Body
wird fruehestens nach 2 MiB + 1 Byte abgebrochen, und der Container puffert
den Rest hoechstens bis `max-swallow-size`.

---

## 3. Die uebrigen Security-Befunde

### 3.1 S-21 — Zielgruppe und Ablaufzeit (Medium)

**Was geaendert wurde.** `SecurityConfiguration.jwtDecoder` setzt jetzt eine
eigene Pruefkette (`DelegatingOAuth2TokenValidator`):

1. `JwtTimestampValidator` — Zeitstempel (wie bisher),
2. **Ablaufzeit Pflicht** — ein Token ohne `exp` wird abgelehnt (bisher war es
   unbegrenzt gueltig),
3. **Zielgruppe** — das Token muss die konfigurierte Audience tragen
   (`echobox.security.audience`, Vorgabe `echobox`, Umgebung
   `ECHobox_SECURITY_AUDIENCE`).

Die Audience ist **konfigurierbar** (ADR-009: kein Wert im Code verdrahtet);
ist sie leer, wird die Zielgruppe nicht geprueft, die Ablaufzeit aber weiterhin
erzwungen.

**Der Nachweis.** `SecurityGapTest` ×2 (S-21a fremde Zielgruppe, S-21b Token
ohne `exp`) — **jetzt gruen**; `tests/unit/security/JwtAudienceHardeningTest.java`
(4 Tests, gruen): die konfigurierte Audience wird angenommen, die Audience ist
konfigurierbar, ein Token **ohne** Audience wird abgelehnt, ein Token mit
mehreren Zielgruppen wird angenommen, wenn eine passt.

**Vollstaendig behoben: ja.** Der Vertrag nennt keine Zielgruppe — er bleibt
unveraendert. **Hinweis fuer den Betrieb (Schritt 9):** der Identity Provider
muss Token mit `aud = echobox` (oder dem konfigurierten Wert) ausstellen; sonst
werden alle Token abgelehnt. Das ist eine bewusste, harte Voreinstellung
(„fail closed").

### 3.2 S-22 — die Schluessellaenge (Medium)

**Was geaendert wurde.** `CoreConfiguration.payloadEncryptionKey` prueft die
Laenge des dekodierten Schluessels: **exakt 32 Byte** (ADR-003 §1, AES-256).
Jede andere Laenge ergibt einen klaren Startfehler
(`IllegalStateException`: „Der Verschluesselungsschluessel muss genau 32 Byte
(256 Bit, AES-256) lang sein, hat aber N Byte."). Ein stilles AES-128 ist
damit ausgeschlossen.

**Der Nachweis.** `SecurityGapTest` → `a_sixteen_byte_key_must_prevent_the_start`
— **jetzt gruen**; `tests/unit/crypto/CryptoKeyLengthTest.java` (4 Tests,
gruen): 32 Byte werden angenommen, 16 Byte und 64 Byte verhindern den Start,
der ephemere Schluessel hat ebenfalls 32 Byte.

**Vollstaendig behoben: ja.**

### 3.3 S-23 — der Antwortkoerper des Empfaengers (Medium)

**Was geaendert wurde.** `DeliveryHttpClient` liest die Antwort mit einem
eigenen `LimitedBodySubscriber`: gespeichert werden hoechstens **1 MiB**
(`echobox.delivery.http.max-response-body-bytes`, Vorgabe 1 MiB), der Rest wird
verworfen. Der Koerper wird **weiterhin vollstaendig konsumiert** — damit
bleibt der Request-Timeout wirksam und die Verbindung bleibt wiederverwendbar
(ein `ofInputStream()` haette den Timeout aus dem Spiel genommen).

**Der Nachweis.** `SecurityGapTest` → `the_response_body_of_the_receiver_must_be_read_with_a_limit`
— **jetzt gruen**; `tests/unit/delivery/DeliveryResponseLimitTest.java`
(3 Tests, gruen): ein normaler Koerper kommt **vollstaendig** an (kein
Funktionsverlust), ein 5-MiB-Koerper wird nach der Grenze verworfen, die
Grenze ist konfigurierbar.

**Vollstaendig behoben: ja.** Der Antwortkoerper ist vertragsseitig
bedeutungslos (nur Statuscode und Ablehnungssignal zaehlen) — die Aenderung
beruehrt das Verhalten der Zustellung nicht.

### 3.4 S-24 — das zugesagte Anfragelimit der API (Medium)

**Der Befund.** Der **eingefrorene Vertrag** (`openapi.yaml:89` und
`:1979-1994`) sagt fuer **jede** Operation HTTP **429** `rate_limit_exceeded`
mit `Retry-After` zu. Die Implementierung hatte keine eingehende
Ratenbegrenzung: `ProblemFactory.RATE_LIMIT_EXCEEDED` wurde nirgends geworfen.

**Was geaendert wurde.**

| Datei | Aenderung |
|---|---|
| `src/main/java/com/echobox/common/ApiRateLimiter.java` (neu) | der Zaehler: je Mandant ein Fenster; `acquire(key)` liefert `permitted` und `retryAfterSeconds` |
| `src/main/java/com/echobox/tenant/ApiRateLimitFilter.java` (neu) | der Filter (nach der Security-Kette): bei Ueberschreitung **429** `rate_limit_exceeded` mit dem Header **`Retry-After`** (Wartezeit in Sekunden, ≥ 1) und dem Fehlerschema `Problem` |
| `src/main/java/com/echobox/config/EchoBoxProperties.java` | `echobox.rate-limit.limit` (Vorgabe **240**) und `echobox.rate-limit.window-seconds` (60) |
| `src/main/resources/application.yml` | dieselben Werte als Konfiguration (`ECHobox_RATE_LIMIT`, `ECHobox_RATE_LIMIT_WINDOW_SECONDS`) |

Der **Vertrag wurde nicht geoeffnet** — die Implementierung liefert die
Zusage. Das Endpunkt-Limit aus FR-033 (`echobox.delivery.rate-limit`) bleibt
unberuehrt und weiterhin je Endpunkt.

**Der Nachweis.**

* `tests/integration/security/SecurityGapIT.java` →
  `the_request_limit_must_answer_with_429`: **vorher** „Expecting value to be
  true but was false" (nach 300 Anfragen nie eine 429), **jetzt gruen**.
* `tests/integration/security/ApiRateLimitIT.java` (neu, 4 Tests, gruen): die
  Anfrage ueber dem Limit ergibt 429 **mit `Retry-After` ≥ 1** und dem
  Fehlerschema des Vertrags; ein **anderer Mandant** ist nicht betroffen; der
  Gesundheitsendpunkt bleibt erreichbar; die Betriebskonfiguration nennt
  ueberhaupt ein Limit.

**Vollstaendig behoben: ja — mit einer offenen Randbedingung.** Der Zaehler
liegt **im Speicher der Instanz**: bei *n* Instanzen ist das wirksame Limit
*n ×* Limit. Eine instanzuebergreifende Grenze waere ein Datenbankschema
(„Zaehlertabelle"), und das Schema darf in diesem Schritt **nicht**
angefasst werden. Fuer den geteilten Betrieb ist das eine bewusste, in diesem
Schritt nicht aenderbare Randbedingung — **Schritt 9** kann sie mit dem
Schema entscheiden. Ausserdem: der Vorgabewert (240 Anfragen je Mandant und
Minute) ist ein **Betriebswert**, kein Vertragswert; er ist ueber
`ECHobox_RATE_LIMIT` anpassbar.

**Anmerkung zu den Tests.** Die uebrigen Integrationstests erzeugen weit mehr
Anfragen, als das Betriebslimit erlaubt (die Rollenmatrix allein rund 700 je
Testklasse) und wuerden sonst mit 429 antworten statt ihr eigentliches
Verhalten zu pruefen. `AbstractApplicationIT` setzt deshalb fuer die Testsuite
ein hohes Limit (`echobox.rate-limit.limit=100000`); **das Anfragelimit selbst
wird in `ApiRateLimitIT` geprueft**. `SecurityGapIT` laeuft ausserhalb dieser
Basisklasse und prueft das **Auslieferungs-Limit** (240).

### 3.5 S-25 — die Pipeline (Medium)

**Was geaendert wurde** (`.github/workflows/deploy.yml`):

| Vorher | Nachher |
|---|---|
| nur `mvn -B verify` | Gates: **Secret-Scan** (gitleaks v8.30.1, gepinntes Image), **SCA-Scan** (OSV-Scanner v2.7.0), Java-Build **inkl. Coverage-Gate**, Typpruefung, Lint, Vertragspruefung, Unit-Tests, Build und **End-to-End-Tests** der Oberflaeche (Playwright, Chromium) |
| Registry-Anmeldung mit dem **statischen** `secrets.REGISTRY_TOKEN` | Anmeldung mit dem **Token des Laufs** (`secrets.GITHUB_TOKEN`) — kurzlebig, automatisch entzogen; `permissions: packages: write`, `id-token: write` fuer die OIDC-Anmeldung |
| `permissions: contents: read` | `contents: read`, `packages: write`, `id-token: write` |
| kein Push-Schritt | der Login-Schritt wurde in „anmelden" umbenannt, der **Push** ist ein eigener Schritt |
| — | das **Profil je Umgebung** wird gesetzt (`SPRING_PROFILES_ACTIVE`) — gehoert zu S-27 |

**Keine Secrets in Logs:** die Geheimnisse werden ausschliesslich als
Umgebungsvariablen gesetzt; kein Schritt gibt Werte aus. Das statische
`REGISTRY_TOKEN` wird **nicht mehr verwendet** und kann aus dem Secret-Store
** geloescht** werden (Schritt 9 / Betrieb).

**Der Nachweis.** Die Datei selbst (Struktur, YAML-Parsing durch
`EnvironmentSeparationIT`, das den Deskriptor mit SnakeYAML liest) und
`ContainerHardeningIT` (Profil je Umgebung). **Ehrliche Grenze:** die Pipeline
kann hier **nicht ausgefuehrt** werden (es gibt keinen GitHub-Runner in dieser
Umgebung) — geprueft ist die Datei, nicht ihr Lauf.

**Vollstaendig behoben: ja** (die Datei ist umgesetzt und geparst); die
**Ausfuehrung** der Pipeline ist Sache von Schritt 9.

### 3.6 S-26 — die `apt`-Pakete (Low)

**Was geaendert wurde.** `Dockerfile`: die Pakete stehen mit Version fest:

```dockerfile
ARG CURL_VERSION=7.81.0-1ubuntu1.30
ARG TINI_VERSION=0.19.0-1
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl=${CURL_VERSION} \
        tini=${TINI_VERSION} \
    && rm -rf /var/lib/apt/lists/*
```

Die Versionen wurden **aus dem Basis-Image selbst gelesen**
(`apt-cache policy` in `eclipse-temurin:21.0.5_11-jre-jammy`) und die
Installation dort **ausgefuehrt und geprueft** (`dpkg -l`, `tini --version`).

**Der Nachweis.** `ContainerHardeningIT.the_apt_packages_are_pinned` und
`…the_pinned_versions_have_a_default` (gruen) — der Test liest den
`apt-get install`-Aufruf samt Zeilenfortsetzungen aus dem Dockerfile.

**Vollstaendig behoben: ja.** **Wartungshinweis (im Dockerfile vermerkt):**
dreht Ubuntu die Paketversion weiter, schlaegt der Build fehl — die Versionen
sind dann in den beiden `ARG`s nachzuziehen. Das ist der Preis der
Reproduzierbarkeit.

### 3.7 S-27 — das Produktionsprofil (Low)

**Was geaendert wurde.**

| Datei | Aenderung |
|---|---|
| `src/main/resources/application-production.yml` (neu) | eigenes Produktionsprofil mit `echobox.crypto.key-required: true` (Vorlage: `application-staging.yml`) |
| `Dockerfile` | `ENV SPRING_PROFILES_ACTIVE=staging` → **`production`** — das Image ist das Produktionsartefakt und ist **nicht** auf Staging gepinnt |
| `.github/workflows/deploy.yml` | jede Umgebung setzt ihr Profil (`staging` / `production`) |

**Der Nachweis.** `ContainerHardeningIT` (3 Tests, gruen): die
Produktionskonfiguration existiert und erzwingt den Schluessel; das Image
setzt `production`; die Bereitstellung setzt das Profil je Umgebung.

**Vollstaendig behoben: ja.** Ohne Schluessel startet der Dienst im
Produktionsprofil **nicht** (kein ephemerer Schluessel, kein stiller
Datenverlust nach dem Neustart).

---

## 4. Die QA-Befunde

### 4.1 A-1 — das Coverage-Gate

**Was geaendert wurde.** `pom.xml`: das **JaCoCo**-Plugin mit drei
Ausfuehrungen — `prepare-agent` (vor den Tests), `report` und **`check`** (in
der Phase `verify`, also nach Surefire **und** Failsafe). Die Regel:
`BUNDLE` / `LINE` / `COVEREDRATIO` / `minimum 0.80`. Der Build **bricht ab**,
wenn die Zeilenabdeckung unter 80 % faellt.

Zwei Feinheiten, ohne die das Gate **nichts** waere:

* `append=true` am Agenten — jeder Testlauf startet eine eigene JVM, und jede
  JVM schreibt ihr Ergebnis in dieselbe Datei. Ohne `append` wuerde die letzte
  JVM die Daten der uebrigen ueberschreiben.
* ein Loeschschritt in der Phase `initialize` (`maven-clean-plugin`, nur
  `target/jacoco.exec`) — sonst wuerde das Gate die Daten **aller** frueheren
  Laeufe mitzaehlen und eine Regression verdecken.

**Der Nachweis.** `mvn -B verify` → `jacoco:check` → „All coverage checks have
been met."; `ContainerHardeningIT.the_build_enforces_the_coverage_threshold`
prueft zusaetzlich die Build-Datei.

**Vollstaendig behoben: ja.**

### 4.2 A-6 / B-17 — die Uebergangstabelle des Domänenmodells

**Der Befund.** `DeliveryStateMachine.TRANSITIONS` erlaubte `delayed` nur aus
`failed`; der Ratenbegrenzungs-Pfad `DeliveryDispatcher.delay()` →
`onRateLimited(status)` setzt **jeden** offenen Zustand auf `delayed` — auch
`scheduled`, den ein beanspruchter Lauf traegt. `canTransition(SCHEDULED,
DELAYED)` lieferte `false`, wurde im Produktivcode aber nie benutzt.

**Was geaendert wurde.** Der **Code** ist jetzt an der Tabelle ausgerichtet:

* `DeliveryStateMachine.TRANSITIONS` enthaelt jetzt `pending -> delayed` und
  `scheduled -> delayed` (zusaetzlich zu `failed -> delayed`);
* `onRateLimited` wird **aus der Tabelle abgeleitet**
  (`canTransition(status, DELAYED)`) — die Tabelle ist damit die eine Quelle
  der Wahrheit, und `canTransition` ist nicht mehr toter Code.

**Das Verhalten ist unveraendert**: `pending`, `scheduled` und `failed` werden
weiterhin auf `delayed` gesetzt; ein `delayed` bleibt `delayed` (ein
Zustandswechsel in sich selbst ist **kein** Uebergang); endgueltige Zustaende
bleiben unveraendert.

**Was Schritt 2 zu tun hat (Dokumentation, `docs/architecture/`).** Die
Uebergangstabelle in `docs/architecture/domain_model.md` §3.3 ist um **zwei**
Uebergaenge zu ergaenzen. Die zu dokumentierende Tabelle lautet:

```
pending   -> scheduled, cancelled, no_subscriber, delayed
scheduled -> sent, rejected, failed, cancelled, delayed
failed    -> scheduled, delayed, abandoned, cancelled
delayed   -> scheduled, abandoned, cancelled
```

`delayed -> delayed` ist **kein** Uebergang (der Zustand aendert sich nicht).
Die Begruendung fuer `pending -> delayed` und `scheduled -> delayed` steht im
Code (`DeliveryStateMachine`, Javadoc `onRateLimited`): das Endpunkt-Limit
(FR-033) wirkt auch auf den **ersten** Versuch eines frischen Laufs — sonst
kaeme ein Endpunkt in der ersten Minute ueber das Limit hinaus. Vertrag und
ADR-010 nennen den Zustand `delayed` ohne Einschraenkung des Ausgangszustands.

---

## 5. Was **nicht** behoben werden konnte

| Punkt | Warum | Owner |
|---|---|---|
| Die **93 nicht erreichbaren** Advisories (Anhang A des Audits) | in dieser Konfiguration nicht erreichbar; ein Upgrade auf "keine Advisories" gibt es nicht. Sie werden durch den OSV-Scan der Pipeline (S-25) laufend ueberwacht | Schritt 9 (Scan des fertigen Images) |
| Das Anfragelimit (S-24) ist **je Instanz**, nicht mandantenuebergreifend | ein instanzuebergreifender Zaehler braucht ein Datenbankschema — das Schema darf in diesem Schritt nicht angefasst werden | Schritt 9 |
| Der **Wert** des Anfragelimits (240/min) und der **Audience** (`echobox`) | Betriebswerte; der Vertrag legt keine fest. Beide sind konfigurierbar | Betrieb / Schritt 9 |
| Ein **Scan des fertigen Images** in der Zielumgebung | die Zielumgebung existiert in diesem Schritt nicht | Schritt 9 |
| Die **Dokumentationszeile** zur Uebergangstabelle | `docs/architecture/` gehoert Schritt 2 | Schritt 2 |
| Der statische Registry-Token `REGISTRY_TOKEN` in der Secret-Verwaltung | loeschen kann nur der Betreiber des Secret-Stores | Betrieb |

**Keine offene Luecke mit `[SECURITY]`-Praefix**: alle Befunde sind behoben.

---

## 6. Verifikation

| # | Pruefung | Kommando | Ergebnis |
|---|---|---|---|
| 1 | Dienst (Unit + Integration) inkl. **Coverage-Gate** | `mvn -B verify` | **BUILD SUCCESS** — 113 Unit + 281 Integration gruen, `jacoco:check`: „All coverage checks have been met" — **86,0 % Zeilen** (2 221/2 584), 84,7 % Anweisungen, 62,5 % Zweige |
| 2 | Typpruefung | `npx tsc --noEmit` | **0 Fehler** |
| 3 | Lint | `npm run lint` | **Exit 0** |
| 4 | Build der Oberflaeche | `npm run build` | **Exit 0** |
| 5 | Unit-Tests der Oberflaeche | `npx vitest run` | **30 gruen** (5 Dateien) |
| 6 | End-to-End-Tests | `npx playwright test` (in `src/main/frontend`) | **31 gruen** |
| 7 | Schema-Spiegel 1 | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | **leer** |
| 8 | Schema-Spiegel 2 | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** |
| 9 | Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **gruen** — „Keine Luecken" |
| 10 | Der **eingefrorene Vertrag** | `git diff --stat -- docs/api/` | **leer** — unveraendert |
| 11 | Vertrags- und Lasttests | `mvn -B -Pcontract-performance verify` | **BUILD SUCCESS** — 19 Vertragstests + 2 Lasttests (1 000 Einlieferungen, p95 < 200 ms) gruen, Coverage-Gate erfuellt |

### 6.1 Die sechs Gap-Tests des Audits — alle gruen

| Gap-Test des Audits | Datei | vorher | jetzt |
|---|---|---|---|
| `a_chunked_request_body_above_two_mebibytes_must_be_rejected` (S-20) | `SecurityGapIT` | 202 statt 413 | **gruen (413)** |
| `the_request_limit_must_answer_with_429` (S-24) | `SecurityGapIT` | nie eine 429 | **gruen (429 + `Retry-After`)** |
| `a_token_with_a_foreign_audience_must_be_rejected` (S-21) | `SecurityGapTest` | angenommen | **gruen (abgelehnt)** |
| `a_token_without_an_expiry_must_be_rejected` (S-21) | `SecurityGapTest` | angenommen | **gruen (abgelehnt)** |
| `a_sixteen_byte_key_must_prevent_the_start` (S-22) | `SecurityGapTest` | angenommen | **gruen (Start verhindert)** |
| `the_response_body_of_the_receiver_must_be_read_with_a_limit` (S-23) | `SecurityGapTest` | 5 MiB gelesen | **gruen (≤ 1 MiB)** |

Die Gap-Tests wurden **nicht** veraendert — sie sind die Abnahme der Behebung.

### 6.2 Die neuen Regressionstests

| Datei | Tests | Beweist |
|---|---|---|
| `tests/unit/security/DependencyHardeningTest.java` | 2 | S-19: die Version des aufgeloesten Containers (aus dem Klassenpfad) ≥ 10.1.55 und ≥ den drei Fix-Versionen |
| `tests/unit/security/JwtAudienceHardeningTest.java` | 4 | S-21: Audience-Pruefung, Konfigurierbarkeit, Token ohne Audience, Token mit mehreren Audiences |
| `tests/unit/crypto/CryptoKeyLengthTest.java` | 4 | S-22: 32 Byte werden angenommen, 16/64 Byte verhindern den Start, ephemerer Schluessel mit 32 Byte |
| `tests/unit/delivery/DeliveryResponseLimitTest.java` | 3 | S-23: normaler Koerper vollstaendig, uebergrosser Koerper verworfen, Grenze konfigurierbar |
| `tests/integration/security/RequestBodyLimitIT.java` | 2 | S-20: chunked unter der Grenze wird angenommen, chunked ueber der Grenze ergibt 413 |
| `tests/integration/security/ApiRateLimitIT.java` | 4 | S-24: 429 mit `Retry-After`, anderer Mandant unberuehrt, Gesundheitsendpunkt frei, Limit in der Konfiguration |
| `tests/integration/deployment/ContainerHardeningIT.java` | 8 | S-26, S-27, A-1, S-19: gepinnte `apt`-Pakete, Produktionsprofil, Profil je Umgebung, Coverage-Gate, Container-Version |

**Teststand gesamt:** 113 Unit-Tests + 281 Integrationstests + 30 Vitest-Tests
+ 31 Playwright-Tests — **alle gruen**; die Zeilenabdeckung liegt bei
**86,0 %** (Gate: ≥ 80 %).

---

## 7. Die Dateien ausserhalb des Commit-Scopes von `src/`, `tests/`, `docs/execution/`

Der Commit-Scope dieses Schritts umfasst `src/`, `tests/` und
`docs/execution/`. Zusaetzlich geaendert (und **im selben Commit** enthalten):

| Datei | Warum |
|---|---|
| `pom.xml` | S-19 (Spring Boot 3.5.16, `tomcat.version`) und A-1 (JaCoCo-Gate) — die Build-Datei gehoert zu diesem Schritt, weil das Gate dort durchgesetzt wird |
| `Dockerfile` | S-26 (gepinnte `apt`-Pakete) und S-27 (Profil `production`) |
| `.github/workflows/deploy.yml` | S-25 (Secret-Scan, SCA-Scan, Oberflaechen-Tests, OIDC-Token) und S-27 (Profil je Umgebung). Die Datei gehoert sonst Schritt 9 — die CI-Aenderung ist hier **zwingend**, weil S-25 die Pipeline betrifft |
| `.gitignore` | `/test-results/` und `/playwright-report/` aus der Wurzel (offener Punkt **S-23** des QA-Berichts, Owner Schritt 9) — mitgenommen, weil die Wurzel-Playwright-Ausgabe sonst im Arbeitsbaum landet |
| `CHANGELOG.md` | die Changelog-Pflicht dieses Schritts (`[Unreleased]`) |

**Nicht** geaendert: `docs/api/` (eingefroren), `docs/adr/`,
`docs/architecture/`, `.agent/knowledge/`, `docs/requirements/`, das
Datenbankschema (beide Spiegel byte-identisch).

---

## 8. Der eine Commit

```
fix(sdlc-6): Security-Befunde S-19 bis S-27 und Coverage-Gate behoben

Schritt 6 - KI-Code-Generation (Ruecksprung) | Agent: aisdlc-execution | Artefakte: src/, tests/, docs/execution/
```

---

*Erzeugt im Ruecksprung zu Schritt 6/7. Kanonisches Artefakt:
`.agent/artifacts/step8-nacharbeit-security.md`.*
