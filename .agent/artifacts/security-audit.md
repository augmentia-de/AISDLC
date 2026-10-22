# Security-Audit — EchoBox (Schritt 8)

> **Kanonisches Artefakt** des Schritts 8 (Sicherheit & Compliance).
> Der **vollständige** Audit mit allen zehn Prüfungen, dem OWASP-Querschnitt, dem
> SCA-Anhang und der Nachweiskette steht in
> **[`docs/qa/security-audit.md`](../../docs/qa/security-audit.md)**.
> Der QA-Bericht steht in [`docs/qa/qa-report.md`](../../docs/qa/qa-report.md),
> das kanonische QA-Artefakt in [`.agent/artifacts/test-report.md`](test-report.md).

---

## 1. Ergebnis in einem Satz

**0 Critical · 0 High · 1 Medium · 1 Low → Freigabe-Empfehlung: FREIGEBEN.**
Die Gate-Bedingung („Security 0 Critical und 0 High") ist **erfüllt**; ein Deployment
(Schritt 9) ist **freigegeben**. Zwei Befunde bleiben offen (S-36 Medium, S-35 Low) —
beide sind **keine** Gate-Blocker, beide tragen einen fertigen Abnahmetest in ihrer
Fehlerspur.

| Schwere | Anzahl | Befund-IDs |
|---|---|---|
| Critical | **0** | — |
| **High** | **0** | — |
| Medium | **1** | **S-36** (nicht unterstützte HTTP-Methode → 500 mit Stacktrace statt 4xx) |
| Low | **1** | **S-35** (Dokumentation der Audience widerspricht dem fail-closed-Verhalten) |

> **Runde 3.** Die Nacharbeit 2 (`3a5ae70`) hat **alle sieben** Befunde der zweiten
> Runde behoben (S-28…S-34). Diese Runde hat **jeden** davon am aktuellen Stand **mit
> eigenem Nachweis** nachgeprüft und danach ein **erneutes vollständiges Audit**
> geführt. Zwei Befunde sind **neu** — beide unterhalb der Gate-Schwelle.

## 2. Der neue Medium-Befund im Überblick

| ID | Nachweis | Ausnutzbarkeit |
|---|---|---|
| **S-36** | `src/main/java/com/echobox/common/GlobalExceptionHandler.java:283-290` — `HttpRequestMethodNotSupportedException` hat **keinen** eigenen Fall (die Nacharbeit zu S-30 hat nur *Medientyp* und *Multipart* abgedeckt) → **500 `internal_error`** + **Stacktrace** im Protokoll. **Belegt** durch eine Sonde über 15 Methode/Pfad-Kombinationen: `POST /health`, `PUT /health`, `POST /actuator/health` — **ohne Token** → 500; 9 Stacktraces im Protokoll. Die **Antwort** gibt nachweislich **kein** internes Detail preis (`UnsupportedMethodErrorIT`, grün). | Ein Aufrufer erzeugt unbegrenzt 500er **mit** Stacktrace im Betriebsprotokoll — das Anfragelimit zählt je Mandant, diese Anfragen haben keinen Mandanten (**Q-9**). Monitoring-Verschmutzung, Protokollverstärkung. **Keine** Verletzung der Antwort, keine Daten, kein Mandantenbruch, keine Verfügbarkeitswirkung. |

## 3. Nachprüfung der Befunde der zweiten Runde

| ID | Schwere (Runde 2) | Bewertung | Fundstelle |
|---|---|---|---|
| **S-28** | High | **behoben** | `TenantParameterFilter.java:88-149` — die Grenze hängt an der **Methode**, nicht am Medientyp; der Converter sieht nur die **begrenzten** Byte. `RequestBodyBoundaryAuditIT` (15, grün): 6 Medientypen, chunked, ohne `Content-Length`, ohne `Content-Type`, gzip-Bombe, knapp unter der Grenze, GET mit übergroßem Body. |
| **S-29** | Medium | **behoben** | `SecurityConfiguration.java:144-150, 178-192`; `AudiencePolicy.java:28-36` (Startabbruch). `AudienceFailClosedPathsTest` (4, grün). *Rest: die Doku → **S-35**.* |
| **S-30** | Medium | **behoben** | `GlobalExceptionHandler.java:246-253, 267-274` — Medientyp und Multipart → 400 ohne Stacktrace. `ErrorMappingLeakTest` (7, grün). *Rest: die **Methode** → **S-36**.* |
| **S-31** | Medium | **behoben** | `pom.xml:65` → 10.1.61; eigene OSV-Abfrage: 10.1.55 → **3** Advisories, 10.1.61 → **0**. `DependencyHardeningTest` (grün) verlangt jetzt ≥ 10.1.59. |
| **S-32** | Medium | **behoben** | `osv-scanner.toml` (26 Einträge) + `deploy.yml:56-64`; eigene Abfrage: 17 + 9 = **26** = **exakt** die Baseline (**0 neu**, **0 verwaist**). |
| **S-33** | Low | **behoben** | `TenantTransactionRunner.java:100-105` → `UnauthenticatedException` → **401**. `ErrorMappingLeakTest` + `MissingTenantIT` (grün). *Rest: **Q-9**.* |
| **S-34** | Low | **behoben** | `DeliveryStateMachine.java:11-31, 118-124` — die Nachziehungs-Zusage ist entfernt. `DeliveryStateMachineDocumentationTest` (2, grün). |

**7 behoben · 0 teilweise behoben · 0 offen geblieben.**

## 4. Die zehn Prüfungen — Ergebnis in einer Zeile je Prüfung

| # | Prüfung | Ergebnis | Kurzbeleg |
|---|---|---|---|
| 1 | Authentifizierung | **kein Fund** | 401 statt 403/500 ✓; RS256-only, kein `none`, kein HS256 ✓; `exp` **und** `nbf` geprüft, `exp` Pflicht ✓; Audience **immer** aktiv, leere Konfiguration ⇒ Startabbruch ✓ (S-29 behoben); `iss` nicht geprüft (der Vertrag fordert keinen) |
| 2 | Autorisierung / IDOR | **kein Fund** | Besitzprüfung auf jeder Objekt-Route (404 vor 403); RLS auf allen 10 Tabellen, `WITH CHECK`, fail-closed, kein `BYPASSRLS`; 3 Eigentümerfunktionen, fail-closed; der Mandant stammt ausschließlich aus dem Token |
| 3 | Secrets | **kein Fund** | Secret-Scan → 2 Test-Fixtures; Git-Historie → 0 Treffer; `.env` in `.gitignore`; keine Schlüssel in Config/Image/CI; Startfehler bei fehlender Konfiguration ✓ (Schlüssel, Zielgruppe, JWKS) |
| 4 | Injection | **kein Fund** | ausschließlich benannte Parameter; 0 Treffer bei SQL-Konkatenation; kein `Runtime.exec`; keine Dateipfade aus Eingaben |
| 5 | Ausgabe / XSS | **1 Medium (S-36)** | kein `dangerouslySetInnerHTML`/`innerHTML`/`eval`; Fehlerantwort ohne Stacktrace/SQL/Pfade ✓ (auch beim unerwarteten Fehler); Medientyp und Multipart → 4xx ✓ — **die nicht unterstützte Methode bleibt 500** (S-36) |
| 6 | Session und Transport | **kein Fund** | stateless, CSRF aus (ohne Cookie im Browser korrekt); httpOnly/sameSite/secure ✓; Security-Header ✓; CORS nicht konfiguriert, Same-Origin-Rewrite — vertretbar; HSTS = offene Frage Q-1 |
| 7 | Rate-Limiting und Ressourcen | **kein Fund** (ein Low-Rest: Q-9) | ausgehend ✓; eingehend ✓ (429 + `Retry-After` vertragskonform, je Mandant); 1-MiB-Nutzlastgrenze ✓; Paginierung begrenzt ✓; **die Body-Grenze ist medientyp-unabhängig und nicht umgehbar** ✓ (S-28 behoben) |
| 8 | Abhängigkeiten und Container | **kein Fund** | 20 → **17** Java-Advisories, alle nicht erreichbar; die 3 Tomcat-Advisories mit dem Pin 10.1.61 behoben; non-root ✓, gepinnte Tags **und gepinnte apt-Pakete** ✓, Multi-Stage ✓, Health-Check ✓, keine Secrets im Image ✓; CI mit Secret-/SCA-Scan, **kalibrierter** Baseline und Lauf-Token ✓ (S-32 behoben) |
| 9 | Logging und Datenschutz | **1 Medium (S-36)** | Logstellen nur Metadaten — kein Klartextpayload, kein Secret, kein Token; Retention 30 Tage dokumentiert, Purge-Stufe wirksam; Recht auf Löschung fachlich offen (Q-3) — **die nicht unterstützte Methode erzeugt weiterhin 500er mit Stacktrace** (S-36) |
| 10 | OWASP Top 10 | **A01–A10 geprüft** | A01 ✓ · A02 ✓ · A03 ✓ · A04 ✓ (S-28 behoben) · A05 (S-36) · A06 ✓ · A07 ✓ (S-29 behoben) · A08 ✓ · A09 (S-36) · A10 ✓ (eine offene Frage) |

## 5. Nachweiskette — jeder Befund ist festgenagelt

| Art | Tests | Ergebnis |
|---|---|---|
| **Abwehr belegen** (neu in dieser Runde) | `RequestBodyBoundaryAuditIT` (15), `AudienceFailClosedPathsTest` (4), `ErrorMappingLeakTest` (7), `UnsupportedMethodErrorIT` (2) | **28 grün** |
| **Abwehr belegen** (aus den Runden 1 + 2) | `SecurityHardeningIT` (7), `JwtDecoderHardeningTest` (7), `RequestBodyLimitIT` (2), `RequestBodyMediaTypeLimitIT` (8), `ApiRateLimitIT` (4), `ApiRateLimitHardeningTest` (5), `JwtAudienceHardeningTest` (4), `AudienceFailClosedTest` (4), `CryptoKeyLengthTest` (4), `DeliveryResponseLimitTest` (3), `DependencyHardeningTest` (2), `ContainerHardeningIT` (8), `ScaBaselineIT` (3), `MediaTypeErrorIT` (6), `MissingTenantIT` (1), `DeliveryStateMachineDocumentationTest` (2) | **70 grün** |
| **Lücke belegen (Runden 1 + 2)** | `SecurityGapTest` (4), `SecurityGapIT` (2), `SecurityGapRound2Test` (2), `SecurityGapRound2IT` (2) | **10/10 grün** — die Nacharbeit 2 hat alle zehn geschlossen, **ohne einen Test zu ändern** |
| **Offen** | `BUG-S-35.json` (Low, Dokumentation), `BUG-S-36.json` (Medium, Statuszuordnung) | **beide mit fertigem Abnahmetest**; die sicherheitsrelevante Eigenschaft ist jeweils grün festgenagelt |

## 6. Was **nicht** behauptet wird

* Kein Befund ohne belegten Nachweis (Datei:Zeile oder ausgeführter Check).
* Die 17 **nicht** erreichbaren Advisories sind nicht als unbedenklich bewertet, sondern
  als *in dieser Konfiguration nicht erreichbar* — mit Begründung je Advisory in
  `docs/qa/security-audit.md`, Anhang A; ein Scan des fertigen Images in der
  Zielumgebung gehört in Schritt 9.
* Der eingefrorene Vertrag `docs/api/openapi.yaml` wurde **nicht** verändert. S-30 und
  S-36 werden **innerhalb** des Vertrags gelöst (400 `malformed_request`) — es ist
  **kein** Vertragsdefekt.
* **S-36** verletzt **keine** Vertraulichkeitseigenschaft: die Antwort gibt
  nachweislich kein internes Detail preis. Offen sind die *Statuszuordnung* und die
  daraus folgende Protokollverstärkung.
* **S-35** ist ein **Dokumentations**befund: das Verhalten ist fail-closed, also
  sicherer als dokumentiert.
* Der Audit ändert **keine** Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/` oder `docs/requirements/`.
* **Der Build ist grün** — es gibt bewusst keine fehlschlagenden Gap-Tests: die beiden
  offenen Befunde liegen unterhalb der Gate-Schwelle, und ein absichtlich roter Test
  würde den Build der Pipeline scheitern lassen und damit das freigegebene Deployment
  verhindern.

---

*Schritt 8 (Sicherheit & Compliance), dritte Runde · Agent: `aisdlc-qa` · 2026-10-22.*
