# Security-Audit — EchoBox (Schritt 8)

> **Kanonisches Artefakt** des Schritts 8 (Sicherheit & Compliance).
> Der **vollständige** Audit mit allen zehn Prüfungen, dem OWASP-Querschnitt, dem
> SCA-Anhang und der Nachweiskette steht in
> **[`docs/qa/security-audit.md`](../../docs/qa/security-audit.md)**.
> Der QA-Bericht steht in [`docs/qa/qa-report.md`](../../docs/qa/qa-report.md),
> das kanonische QA-Artefakt in [`.agent/artifacts/test-report.md`](test-report.md).

---

## 1. Ergebnis in einem Satz

**0 Critical · 2 High · 5 Medium · 2 Low → Freigabe-Empfehlung: BLOCKIEREN.**
Die Gate-Bedingung („Security 0 Critical und 0 High") ist **nicht** erfüllt; ein
Deployment (Schritt 9) ist **nicht** freigegeben. Rücksprung zu **Schritt 6/7**
(Implementierung) — für **S-24** zusätzlich zu **Schritt 2** (der Vertrag ist
eingefroren).

| Schwere | Anzahl | Befund-IDs |
|---|---|---|
| Critical | **0** | — |
| **High** | **2** | S-19 (Abhängigkeiten), S-20 (Request-Body ohne Grenze bei Chunked-Encoding) |
| Medium | **5** | S-21 (JWT-Audience/Ablauf), S-22 (Schlüssellänge), S-23 (Antwort-Body), S-24 (429), S-25 (CI) |
| Low | **2** | S-26 (apt-Pinning), S-27 (kein Produktionsprofil) |

## 2. Die zwei High im Überblick

| ID | Nachweis | Ausnutzbarkeit |
|---|---|---|
| **S-20** | `src/main/java/com/echobox/tenant/TenantParameterFilter.java:70` und `:81` — die 2-MiB-Grenze wird nur gegen `Content-Length` geprüft; bei `Transfer-Encoding: chunked` greift sie nicht, `readAllBytes()` liest ohne Limit. **Beleg:** `SecurityGapIT` → „expected: 413 but was: 202". | Ein angemeldeter Mandant sendet einen sehr großen chunked JSON-Body → der Body wird vollständig im Heap des **geteilten** JVM allokiert → `OutOfMemoryError` → Ausfall für **alle** Mandanten. |
| **S-19** | `pom.xml:16` — Spring Boot 3.3.5 → `tomcat-embed-core:10.1.31`. OSV: 96 versionsgebundene Advisories, davon **3 im HTTP/1.1-Pfad erreichbar** (CVE-2026-24880 Request-Smuggling, CVE-2025-48988 Multipart-DoS, CVE-2025-48989 „made you reset"). | Jeder HTTP/1.1-Client kann eine ungültige Chunk-Extension senden → Request-Grenzen zwischen Tomcat und dem vorgeschalteten Proxy können auseinanderlaufen. Multipart-DoS: ein angemeldeter Mandant löst eine Tomcat-Verarbeitung ohne wirksame Ressourcenbegrenzung aus. |

## 3. Die zehn Prüfungen — Ergebnis in einer Zeile je Prüfung

| # | Prüfung | Ergebnis | Kurzbeleg |
|---|---|---|---|
| 1 | Authentifizierung | **1 Medium (S-21)** | 401 statt 403/500 ✓ (`S-HARD-1`); RS256-only, kein `none`, kein HS256, Ablauf ✓ (`S-HART-1…5`); **Audience/`exp` fehlen** (S-21) |
| 2 | Autorisierung / IDOR | **kein Fund** | Besitzprüfung auf jeder Objekt-Route (404 vor 403); RLS auf allen 10 Tabellen, `WITH CHECK`, fail-closed, kein `BYPASSRLS`; 3 mandantenübergreifende Eigentümerfunktionen, fail-closed |
| 3 | Secrets | **kein Fund** | Secret-Scan über `src/ tests/ .github/ Dockerfile` → 2 Test-Fixtures; Git-Historie → 0 Treffer; `.env` in `.gitignore:9`; keine Schlüssel in Config/Image/CI; Fehlermeldung bei fehlender Konfiguration ✓ |
| 4 | Injection | **kein Fund** | ausschließlich benannte Parameter; 0 Treffer bei String-Konkatenation in SQL; kein `Runtime.exec`; keine Dateipfade aus Eingaben |
| 5 | Ausgabe / XSS | **kein Fund** | kein `dangerouslySetInnerHTML`/`innerHTML`/`eval`; Fehlerantwort ohne Stacktrace/SQL/Pfade (`S-HARD-5`) |
| 6 | Session und Transport | **kein Fund** | stateless, CSRF aus (ohne Cookie im Browser korrekt); httpOnly/sameSite/secure ✓; Security-Header ✓ (`S-HARD-2`); CORS nicht konfiguriert, Same-Origin-Rewrite — vertretbar |
| 7 | Rate-Limiting und Ressourcen | **2 High/Medium (S-20, S-23, S-24)** | ausgehend ✓ (F-01); 1-MiB-Grenze ✓; Paginierung begrenzt (1…200) ✓; **eingehend fehlt** (S-24); **chunked ohne Grenze** (S-20); **Antwort-Body ohne Grenze** (S-23) |
| 8 | Abhängigkeiten und Container | **1 High (S-19), 2 Low** | 96 Advisories, 3 erreichbar; non-root ✓, gepinnte Tags ✓, Multi-Stage ✓, Health-Check ✓, keine Secrets im Image ✓; CI ohne Secret-/SCA-Scan, statischer Registry-Token (S-25) |
| 9 | Logging und Datenschutz | **kein Fund** | 30 Logstellen, alle nur Metadaten — kein Klartextpayload, kein Secret, kein Token (FR-012/AC-007-2); Retention 30 Tage dokumentiert; Recht auf Löschung über die Purge-Stufe |
| 10 | OWASP Top 10 | **A01–A10 geprüft** | A01 ✓ · A02 (S-22) · A03 ✓ · A04 (S-24) · A05 (S-26/S-27) · A06 (S-19) · A07 (S-21) · A08 ✓ · A09 ✓ · A10 ✓ (eine offene Frage) |

## 4. Nachweiskette — jeder Befund ist festgenagelt

| Art | Tests | Ergebnis |
|---|---|---|
| **Abwehr belegen** | `tests/integration/security/SecurityHardeningIT.java` (7), `tests/unit/security/JwtDecoderHardeningTest.java` (7) | **14 grün** |
| **Lücke belegen** | `tests/unit/security/SecurityGapTest.java` (4), `tests/integration/security/SecurityGapIT.java` (2) | **6 schlagen fehl** — jede Fehlermeldung benennt genau die verletzte Eigenschaft |

Strukturierte Fehlerbeschreibungen: `docs/qa/bugs/BUG-S-19.json`, `BUG-S-20.json`,
`BUG-S-21.json`, `BUG-S-22.json`, `BUG-S-23.json`, `BUG-S-24.json`, `BUG-B-17.json`.

## 5. Was **nicht** behauptet wird

* Kein Befund ohne belegten Nachweis (Datei:Zeile oder ausgeführter Check).
* Die 93 **nicht** erreichbaren Advisories sind nicht als unbedenklich bewertet, sondern
  als *in dieser Konfiguration nicht erreichbar* — mit Begründung je Advisory in
  `docs/qa/security-audit.md`, Anhang A.
* Der eingefrorene Vertrag `docs/api/openapi.yaml` wurde **nicht** verändert. S-24 ist ein
  **Vertrags-/Implementierungs-Gegensatz** und damit ein Befund für den Rücksprung, keine
  stillschweigende Korrektur.
* Der Audit ändert **keine** Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/` oder `docs/requirements/`.

---

*Schritt 8 (Sicherheit & Compliance) · Agent: `aisdlc-qa` · 2026-10-22.*
