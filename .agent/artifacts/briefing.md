# Steckbrief — Schritt 8 (Sicherheit & Compliance)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 1 (Nachzug) abgeschlossen — Commit `825656a`; davor Schritt 7 `bd15f19`, Schritt 6 `e9e1bc1`, Schritt 5 `f5b8054`, Schritt 2 `8627bb6`, Schritt 4 `68ffa4e`, Schritt 3 `05bceca`, Schritt 1 `cc67445` |
| Dieser Schritt | 8 (Sicherheit & Compliance) — **QA + Security-Audit** |
| Nächster Schritt | 9 (Deployment & Betrieb) — **erst nach GATE 2** |
| **Gate-Bedingung** | **QA: 0 Critical · Security: 0 Critical und 0 High.** Sonst: kein Deployment, sondern Rücksprung zu Schritt 6/7. |
| **Vertrag** | `docs/api/openapi.yaml` — **EINGEFROREN seit `8627bb6`**. Ein Vertragsdefekt ist ein Befund fuer einen Ruescksprung zu Schritt 2. |
| Schema | `.agent/knowledge/schema.sql` + Spiegel `docs/architecture/schema.sql` + Migration `src/main/resources/db/migration/V1__initial_schema.sql` — alle drei byte-identisch |
| Code | `src/` (263 Dateien), `tests/` (75 Dateien) |
| Teststand (Schritt 7) | `mvn -q verify` gruen · `tsc --noEmit` 0 · lint 0 · build ok · Vitest 30 gruen · Playwright 31 gruen · beide Schema-Diffs leer · `traceability_check.py` gruen · Matrix: 184 Pfadverweise existierend, 1 offen (`docker-compose.yml`, Schritt 9) |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/08-security.md` | dieser Schritt, Pruefpunkte, Output-Liste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/04_orchestrator_testing.md` | dein Systemprompt |
| 5 | `docs/api/openapi.yaml` | **der eingefrorene Vertrag** — das Sicherheitsmodell der Schnittstelle |
| 6 | `.agent/knowledge/schema.sql` | RLS, Mandantentrennung, Eigentuemerfunktionen, Constraints |
| 7 | `docs/adr/ADR-002-mandantentrennung-rls.md`, `ADR-003-verschluesselung-ruhezustand.md`, `ADR-004-signaturverfahren.md`, `ADR-009-ablehnungssignal.md`, `ADR-013-mandanten-isolation-instanz.md`, `ADR-015-fehlersemantik-tenant-rollen.md`, `ADR-018-barrierefreiheit-frontend.md` | die Sicherheitsentscheidungen — **pruefe sie gegen den Code** |
| 8 | `docs/requirements/traceability_matrix.json` | die **nachgezogene** Matrix (Anforderung → Test → Code) |
| 9 | `.agent/artifacts/traceability-verification.md` | der Beleg aus Schritt 7 |
| 10 | `docs/execution/step6-umsetzungsbericht.md` | Umsetzungsbericht, Nachzuege, offene Punkte |
| 11 | `Dockerfile`, `.github/workflows/deploy.yml`, `pom.xml`, `package.json`, `.gitignore` | Container, CI, Abhaengigkeiten, Secret-Hygiene |
| 12 | `docs/requirements/stories/US-0xx.json` | die 101 AKs (Abdeckungsnachweis) |

Bei Bedarf: `tests/` (die vorhandene Suite), `docs/architecture/architecture.md`, `docs/architecture/GLOSSARY.md`.

## Entscheidungen, die gelten — der Vertrag, die ADRs und das Schema sind das Orakel

D-01…D-15 · ADR-001…ADR-018 · F-01…F-04 · E-01…E-11 · E-13 · V-26…V-27 · V-39…V-44 · V-48…V-49
Das Sicherheitsmodell, das du **belegen** sollst:
- **Mandant aus dem JWT**, **transaktionslokal** gesetzt (ADR-002 §3); RLS auf **jeder** fachlichen
  Tabelle, **fail-closed**, **kein** `BYPASSRLS`.
- **Mandantenuebergreifend** nur ueber die **drei** `SECURITY DEFINER`-Eigentuemerfunktionen;
  deren Projektionen enthalten **keinen** Klartext und **keine** Secrets.
- **Verschluesselung im Ruhezustand** (AES-256-GCM), Schluessel **ausserhalb** der Datenbank.
- **Signatur** HMAC-SHA256, **zeitkonstanter** Vergleich.
- **Ablehnungssignal**, **3 Kennungen** — die ausgehende Schnittstelle ist Teil des Vertrags.
- **Replay** einmal, nur Empfaenger-Admin.
- **Keine Secrets** in Code, Konfiguration, Image, CI.

## Offene Punkte (aus den Vorgaengen — pruefe sie im Audit)

| ID | Thema | Owner |
|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz, Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht, Gate 2 |
| R-01 / E-13 | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich, Gate 2 |
| E-12 | Haertung der mandantenuebergreifenden Sicht (eigene Laufzeitrolle?) | Fachbereich + Plattform, Gate 2 |
| B-17 | Luecke in der Uebergangstabelle des Domänenmodells | Architektur |
| B-18 | Idempotenzschluessel der Endpunkt-Registrierung | Fachbereich, Gate 2 |
| S-18 | `DeliveryRepository` (867 Z.) und `EndpointRepository` (566 Z.) — grosse Klassen | Schritt 8/9 |
| S-22 | `docker-compose.yml` fehlt | **Schritt 9** |
| S-23 | `.gitignore`: `/test-results/` aus der Wurzel | **Schritt 9** |

## Was du in diesem Schritt **nicht** tust
- **Der Audit ist read-only**: keine Datei in `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/`, `docs/requirements/` aendern. Ein Fund wird **gemeldet**, nicht gefixt.
- **Du** darfst **Tests** in `tests/` schreiben — insbesondere **Sicherheits-Regressionstests**, die
  einen Fund festnageln (ein Test, der die Luecke ausfuellt, schlaegt fehl und beweist die Luecke;
  ein Test, der die **Abwehr** beweist, muss gruen sein).
- **Keine** Aenderung an `src/`, auch keine „kleine“ Korrektur. **Keine** Geheimnisse erzeugen.
- **Den eingefrorenen Vertrag nicht aendern.** Vertragsdefekt ⇒ **Befund**.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Ergebnisse
- **`docs/qa/security-audit.md`** — der Audit: Befundtabelle mit **Schwere / Datei:Zeile / Befund /
  Ausnutzbarkeit / Empfehlung**, Gesamt-Rating und **Freigabe-Empfehlung** (freigeben / blockieren).
  Jeder Fund mit `[SECURITY]`-Praefix. **Null Funde sind ein Ergebnis — aber nur, wenn geprueft wurde.**
  Jede der **10 Pruefungen** des Skills muss **beantwortet** sein (auch mit „kein Fund, geprueft in …“).
- **`docs/qa/qa-report.md`** — der QA-Bericht: Teststand je Ebene, **Abdeckung der 101 AKs** gegen die
  Matrix, **Regressionspruefung**, Testabdeckung des Codes (Coverage), Abweichungen, offene Punkte.
- **`.agent/artifacts/security-audit.md`** und **`.agent/artifacts/test-report.md`** — die
  kanonischen Artefakte des Schritts (Inhalt wie die Dokumente in `docs/qa/`, dort verlinkt).
- **Sicherheits-Regressionstests** in `tests/` fuer die Funde, die sich durch einen Test festnageln
  lassen.

## Ergebnis dieses Schritts (Schritt 8 — Sicherheit & Compliance)

> **Gate-Bedingung NICHT erfuellt.** QA: 0 Critical ✓ · Security: **2 High** ✗ →
> **Freigabe-Empfehlung: BLOCKIEREN**, kein Deployment (Schritt 9), Ruecksprung zu
> **Schritt 6/7** ( fuer S-24 zusaetzlich **Schritt 2** — der Vertrag ist eingefroren).

| Schwere | Anzahl | Befunde |
|---|---|---|
| Critical | 0 | — |
| **High** | **2** | **S-19** Abhaengigkeiten (96 Advisories, 3 im HTTP/1.1-Pfad erreichbar) · **S-20** Request-Body ohne Grenze bei `Transfer-Encoding: chunked` |
| Medium | 5 | S-21 JWT-Audience/`exp` · S-22 Schluessellaenge · S-23 Antwort-Body · S-24 zugesagte 429 fehlt · S-25 CI (kein Secret-/SCA-Scan, statischer Registry-Token) |
| Low | 2 | S-26 apt nicht gepinnt · S-27 kein Produktionsprofil |

| Ergebnis | Wert |
|---|---|
| Teststand | 449 Testfaelle: **443 gruen**, **6 bewusst fehlschlagende Gap-Tests** (Belege der Luecken); Java Unit 89+7, Integration 258+7, Vertrag 19, Last 2, Vitest 30, Playwright 31 |
| AK-Abdeckung | **101 von 101** (jede AK-ID im Namen eines existierenden Testfalls einer matrix-genannten Datei) |
| Regression | **keine** — in Schritt 6/7 wurde kein Testfall und keine Assertion entfernt |
| Coverage | **85,4 % Zeilen** (Schwelle 80 % erfuellt) — aber **nicht im Build erzwungen** (kein Plugin, A-1) |
| Schema-Diffs | beide **leer** · `traceability_check.py` **gruen** |
| Neue Tests | `tests/unit/security/JwtDecoderHardeningTest.java` (7 gruen), `tests/integration/security/SecurityHardeningIT.java` (7 gruen), `tests/unit/security/SecurityGapTest.java` (4 **rot**), `tests/integration/security/SecurityGapIT.java` (2 **rot**) |
| Artefakte | `docs/qa/security-audit.md`, `docs/qa/qa-report.md`, `docs/qa/bugs/BUG-*.json`, `.agent/artifacts/security-audit.md`, `.agent/artifacts/test-report.md` |

**Neue offene Punkte aus Schritt 8:** A-1 Coverage-Gate · A-6/B-17 Uebergangstabelle des
Domaenenmodells (`scheduled -> delayed` fehlt in der Tabelle) · Q-1 TLS/HSTS ·
Q-2 Branch-Schutz · Q-3 Recht auf Loeschung · Q-4 interne Adressbereiche (SSRF) ·
Q-5 jackson-core-Restrisiko · Q-6 CVE-2026-22732 · Q-7 DEK/KEK je Mandant (ADR-003 §2).

---

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Diffs | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` · `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
| Secret-Scan | `grep -rInE "(api[_-]?key|secret|password|token|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ .github/ 2>/dev/null` |
| Backend | `mvn -q verify` (inkl. Coverage) |
| Oberflaeche | `npx tsc --noEmit` · `npm run lint` · `npx vitest run` · `npx playwright test` |

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht" \
  -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
```
Wenn sich nichts geaendert hat: kein Commit.
