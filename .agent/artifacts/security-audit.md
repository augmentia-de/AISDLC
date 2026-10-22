# Security-Audit — EchoBox (Schritt 8)

> **Kanonisches Artefakt** des Schritts 8 (Sicherheit & Compliance).
> Der **vollständige** Audit mit allen zehn Prüfungen, dem OWASP-Querschnitt, dem
> SCA-Anhang und der Nachweiskette steht in
> **[`docs/qa/security-audit.md`](../../docs/qa/security-audit.md)**.
> Der QA-Bericht steht in [`docs/qa/qa-report.md`](../../docs/qa/qa-report.md),
> das kanonische QA-Artefakt in [`.agent/artifacts/test-report.md`](test-report.md).

---

## 1. Ergebnis in einem Satz

**0 Critical · 1 High · 4 Medium · 2 Low → Freigabe-Empfehlung: BLOCKIEREN.**
Die Gate-Bedingung („Security 0 Critical und 0 High") ist **nicht** erfüllt; ein
Deployment (Schritt 9) ist **nicht** freigegeben. Erforderlich ist ein Rücksprung zu
**Schritt 6/7** (Implementierung) — der eingefrorene Vertrag bleibt dabei unverändert.

| Schwere | Anzahl | Befund-IDs |
|---|---|---|
| Critical | **0** | — |
| **High** | **1** | **S-28** (Body-Grenze über den Medientyp umgehbar) |
| Medium | **4** | S-29 (Audience fail-open), S-30 (500 statt 4xx), S-31 (Container-Pin unter der Fix-Linie), S-32 (SCA-Gate ohne Baseline) |
| Low | **2** | S-33 (Token ohne Mandant → 500), S-34 (veralteter Javadoc) |

> **Runde 2.** Die Nacharbeit (`d135cda`, Doku `28396e0`) hat S-19…S-27 und A-1 behoben.
> Diese Runde hat **jeden** Befund am aktuellen Stand **mit eigenem Nachweis**
> nachgeprüft und danach ein **erneutes vollständiges Audit** geführt. **Ein** Befund
> ist **neu** (S-28, High) — und er betrifft dieselbe Stelle, die in Runde 1 als S-20
> (High) behoben gemeldet wurde.

## 2. Der High im Überblick

| ID | Nachweis | Ausnutzbarkeit |
|---|---|---|
| **S-28** | `src/main/java/com/echobox/tenant/TenantParameterFilter.java:160-167` — die 2-MiB-Grenze wird nur für Bodies geprüft, deren Content-Type mit `application/json` beginnt; Spring MVC liest aber auch `application/*+json` (`MappingJackson2HttpMessageConverter`). **Beleg:** `SecurityGapRound2IT` → „expected: 413 but was: **202**" bei 2.097.298 Byte; **202** auch bei 20.971.604 Byte. | Ein angemeldeter Mandant (jede Rolle) sendet einen POST mit `Content-Type: application/vnd.custom+json` und einem beliebig grossen JSON-Body → der Body wird **unbegrenzt** in den Heap des **geteilten** JVM allokiert → `OutOfMemoryError` → Ausfall für **alle** Mandanten. Ein gültiges Token genügt. |

## 3. Nachprüfung der Befunde der ersten Runde

| ID | Schwere (Runde 1) | Bewertung | Fundstelle |
|---|---|---|---|
| **S-19** | High | **behoben** (Einschränkung → S-31) | `mvn dependency:tree` → `tomcat-embed-core:10.1.55`; OSV 96 → 20; die drei erreichbaren CVEs nicht mehr enthalten |
| **S-20** | High | **teilweise behoben** → **S-28** | `TenantParameterFilter.java:139-158` (begrenztes Lesen) — greift nur für `application/json` |
| **S-21** | Medium | **teilweise behoben** → **S-29** | `SecurityConfiguration.java:112-163` — die Audience-Prüfung fällt bei leerer Konfiguration still aus (`:126`) |
| **S-22** | Medium | **behoben** | `CoreConfiguration.java:71-79` (exakt 32 Byte) |
| **S-23** | Medium | **behoben** | `DeliveryHttpClient.java:135-187` (`LimitedBodySubscriber`, 1 MiB) |
| **S-24** | Medium | **behoben** | `ApiRateLimitFilter` + `ApiRateLimiter`; `Retry-After` vertragskonform |
| **S-25** | Medium | **behoben** (strukturell) → **S-32** | `deploy.yml:51-93`, `:99-107` — aber ohne Baseline |
| **S-26** | Low | **behoben** | `Dockerfile:36-42` |
| **S-27** | Low | **behoben** | `application-production.yml:25`, `Dockerfile:63`, `deploy.yml:127/:155` |
| **A-1** | QA | **behoben** | `pom.xml:224-277` — JaCoCo `check`, `minimum 0.80`, Phase `verify` |
| **A-6 / B-17** | QA | **behoben** (Code + Doku) | `DeliveryStateMachine.TRANSITIONS:33-41`; `domain_model.md` §3.3 |

**8 behoben · 2 teilweise behoben · 0 offen geblieben.**

## 4. Die zehn Prüfungen — Ergebnis in einer Zeile je Prüfung

| # | Prüfung | Ergebnis | Kurzbeleg |
|---|---|---|---|
| 1 | Authentifizierung | **2 Medium (S-29, S-33)** | 401 statt 403/500 ✓; RS256-only, kein `none`, kein HS256 ✓; `exp` **und** `nbf` geprüft, `exp` Pflicht ✓; Audience ✓ — **aber fail-open bei leerer Konfiguration** (S-29); `iss` nicht geprüft (der Vertrag fordert keinen) |
| 2 | Autorisierung / IDOR | **kein Fund** | Besitzprüfung auf jeder Objekt-Route (404 vor 403); RLS auf allen 10 Tabellen, `WITH CHECK`, fail-closed, kein `BYPASSRLS`; 3 Eigentümerfunktionen, fail-closed; der Mandant stammt ausschließlich aus dem Token (der `application/*+json`-Weg umgeht nur die *Body*-Prüfung — ein Mandantenwechsel bleibt unmöglich, `fail-on-unknown-properties: true`) |
| 3 | Secrets | **kein Fund** | Secret-Scan → 2 Test-Fixtures; Git-Historie → 0 Treffer; `.env` in `.gitignore`; keine Schlüssel in Config/Image/CI; Startfehler bei fehlender Konfiguration ✓ — **Ausnahme: die Audience (S-29)** |
| 4 | Injection | **kein Fund** | ausschließlich benannte Parameter; 0 Treffer bei SQL-Konkatenation; kein `Runtime.exec`; keine Dateipfade aus Eingaben |
| 5 | Ausgabe / XSS | **1 Medium (S-30)** | kein `dangerouslySetInnerHTML`/`innerHTML`/`eval`; Fehlerantwort ohne Stacktrace/SQL/Pfade ✓ — **ein nicht unterstützter Medientyp ergibt aber 500 mit Stacktrace** |
| 6 | Session und Transport | **kein Fund** | stateless, CSRF aus (ohne Cookie im Browser korrekt); httpOnly/sameSite/secure ✓; Security-Header ✓; CORS nicht konfiguriert, Same-Origin-Rewrite — vertretbar; HSTS = offene Frage Q-1 |
| 7 | Rate-Limiting und Ressourcen | **1 High (S-28), 1 Medium (S-30), 1 Low (S-33)** | ausgehend ✓; eingehend ✓ (429 + `Retry-After` vertragskonform, je Mandant); 1-MiB-Nutzlastgrenze ✓; Paginierung begrenzt ✓; **die Body-Grenze ist über den Medientyp umgehbar** (S-28) |
| 8 | Abhängigkeiten und Container | **2 Medium (S-31, S-32)** | 96 → 20 Advisories, die 3 erreichbaren behoben; non-root ✓, gepinnte Tags **und gepinnte apt-Pakete** ✓, Multi-Stage ✓, Health-Check ✓, keine Secrets im Image ✓; CI mit Secret-/SCA-Scan und Lauf-Token ✓ — **aber ohne Baseline schlägt der SCA-Gate immer an** (S-32) und **der Container-Pin liegt unter der Fix-Linie** (S-31) |
| 9 | Logging und Datenschutz | **1 Low (S-33)** | Logstellen nur Metadaten — kein Klartextpayload, kein Secret, kein Token; Retention 30 Tage dokumentiert; Recht auf Löschung über die Purge-Stufe — **Fehler des Aufrufers erzeugen 500er mit Stacktrace** |
| 10 | OWASP Top 10 | **A01–A10 geprüft** | A01 ✓ · A02 ✓ (S-22 behoben) · A03 ✓ · A04 (S-28) · A05 (S-29/S-30) · A06 (S-31) · A07 (S-29) · A08 ✓ · A09 (S-30/S-33) · A10 ✓ (eine offene Frage) |

## 5. Nachweiskette — jeder Befund ist festgenagelt

| Art | Tests | Ergebnis |
|---|---|---|
| **Abwehr belegen** | `SecurityHardeningIT` (7), `JwtDecoderHardeningTest` (7), `RequestBodyLimitIT` (2), `ApiRateLimitIT` (4), **`ApiRateLimitHardeningTest` (5, neu)**, `JwtAudienceHardeningTest` (4), `CryptoKeyLengthTest` (4), `DeliveryResponseLimitTest` (3), `DependencyHardeningTest` (2), `ContainerHardeningIT` (8) | **46 grün** |
| **Lücke belegen (Runde 1)** | `SecurityGapTest` (4), `SecurityGapIT` (2) | **6 grün** — die Nacharbeit hat alle sechs geschlossen, ohne einen Test zu ändern |
| **Lücke belegen (Runde 2)** | `SecurityGapRound2Test` (2), `SecurityGapRound2IT` (2) | **4 schlagen fehl** — jede Fehlermeldung benennt genau die verletzte Eigenschaft |

Strukturierte Fehlerbeschreibungen: `docs/qa/bugs/BUG-S-19.json` … `BUG-S-24.json`,
`BUG-B-17.json` (Runde 1) und **`BUG-S-28.json` … `BUG-S-34.json`** (Runde 2).

## 6. Was **nicht** behauptet wird

* Kein Befund ohne belegten Nachweis (Datei:Zeile oder ausgeführter Check).
* Die 17 **nicht** erreichbaren Advisories sind nicht als unbedenklich bewertet, sondern
  als *in dieser Konfiguration nicht erreichbar* — mit Begründung je Advisory in
  `docs/qa/security-audit.md`, Anhang A.
* Der eingefrorene Vertrag `docs/api/openapi.yaml` wurde **nicht** verändert. S-30 wird
  **innerhalb** des Vertrags gelöst (400 `malformed_request`) — es ist **kein**
  Vertragsdefekt.
* Der Audit ändert **keine** Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/` oder `docs/requirements/`.

---

*Schritt 8 (Sicherheit & Compliance), zweite Runde · Agent: `aisdlc-qa` · 2026-10-22.*
