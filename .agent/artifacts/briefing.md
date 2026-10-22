# Steckbrief — Schritt 8 (erneute Pruefung nach der Security-Nacharbeit)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 2 (Korrektur) abgeschlossen — Commit `28396e0`; davor Ruecksprung Schritt 6/7 `d135cda`, Schritt 8 (erste Runde) `1ba4160`, Schritt 1 (Nachzug) `825656a`, Schritt 7 `bd15f19`, Schritt 6 `e9e1bc1` |
| Dieser Schritt | 8 (Sicherheit & Compliance) — **zweite Runde**: Nachpruefung der Nacharbeit + erneutes Audit |
| Nächster Schritt | **GATE 2** → danach Schritt 9 (Deployment & Betrieb) |
| Gate-Bedingung | **QA: 0 Critical · Security: 0 Critical und 0 High** |
| **Vertrag** | `docs/api/openapi.yaml` — **EINGEFROREN seit `8627bb6`**; in der Nacharbeit **nicht** angefasst |
| Schema | `.agent/knowledge/schema.sql` + Spiegel + Migration — alle drei byte-identisch |
| Teststand (Nacharbeit) | `mvn verify` BUILD SUCCESS (113 Unit + 281 Integration, **Coverage 86,0 %**, Gate **erzwungen**) · Vertrag 19 + Last 2 gruen · `tsc` 0 · lint 0 · build ok · Vitest 30 · Playwright 31 · beide Schema-Diffs leer · `traceability_check.py` gruen · alle 6 Gap-Tests gruen + 24 neue Regressionstests |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/08-security.md` | dieser Schritt, Pruefpunkte, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/04_orchestrator_testing.md` | dein Systemprompt |
| 5 | `docs/qa/security-audit.md` | **der erste Audit** — die Befunde S-19…S-27 und ihre Nachweise |
| 6 | `docs/qa/qa-report.md` | der erste QA-Bericht — A-1, A-6/B-17, Q-1…Q-7 |
| 7 | `docs/execution/step8-nacharbeit-security.md` | **die Nacharbeit** — was geaendert wurde und wie es belegt ist |
| 8 | `docs/architecture/domain_model.md` §3.3 | die korrigierte Uebergangstabelle |
| 9 | `docs/api/openapi.yaml` | der eingefrorene Vertrag |
| 10 | `.agent/knowledge/schema.sql` | RLS, Eigentuemerfunktionen, Constraints |

## Dein Auftrag

### 1 — Nachpruefung: sind die Befunde **wirklich** behoben?
Pruefe **jeden** Befund des ersten Audits am **aktuellen** Stand — mit **eigenem Nachweis**
(Datei:Zeile oder ausgefuehrter Check), nicht nach dem Bericht der Nacharbeit:
**S-19** (Tomcat-Version im Abhaengigkeitsbaum — fuehre `mvn dependency:tree` aus und pruefe die
erreichbaren CVEs), **S-20** (chunked-Body ⇒ 413; der Body wird begrenzt gelesen), **S-21** (Audience
+ `exp` verpflichtend), **S-22** (Schluessellaenge 32 Byte ⇒ AES-256, sonst klarer Fehler),
**S-23** (Groessengrenze der Empfaengerantwort), **S-24** (eingehende Ratenbegrenzung liefert 429 +
`Retry-After`, **wie der eingefrorene Vertrag es beschreibt**), **S-25** (Pipeline: Secret-Scan,
SCA-Scan, Oberflaechen-Tests, keine statischen Tokens im Log), **S-26** (gepinnte Pakete),
**S-27** (`application-production.yml`, `key-required` zwingend, Image nicht fest auf `staging`),
**A-1** (JaCoCo-Gate **erzwungen**), **A-6/B-17** (Code und Doku konsistent).
Bewerte je Befund: **behoben / teilweise behoben / nicht behoben** — mit Fundstelle im aktuellen Stand.

### 2 — Erneutes **vollstaendiges** Audit
Alle **10 Pruefungen** des Skills `aisdlc-security-scan` **erneut und vollstaendig** — mit Schwerpunkt
auf dem, was **neu** entstanden ist:
- die **eingehende Ratenbegrenzung** (S-24 neu): Grenzen, Fehlerverhalten, Mandantenbezug, Bypass,
  `Retry-After`-Korrektheit;
- die **begrenzte Body-Schaetzung** (S-20 neu): greift die Grenze bei `Content-Length` **und** bei
  chunked, bei Multipart, bei gzip, bei einem Body **ohne** `Content-Length`; ist sie umgehbar?
- die **JWT-Validierung** (S-21 neu): Audience, `exp`, `nbf`, `iss`, Algorithmus, `none`;
- die **Krypto-Schluessellaenge** (S-22 neu): Fehlerverhalten, keine Fallback-Laenge;
- die **Empfaengerantwort-Grenze** (S-23 neu);
- die **CI** (S-25 neu): Secrets in Logs, OIDC, geschuetzte Branches, Pinning;
- das **Produktionsprofil** (S-27 neu).
Dazu die **OWASP-Top-10-Querpruefung** (A01…A10) mit Ergebnis je Zeile.
**Null Funde sind ein Ergebnis — aber nur, wenn geprueft wurde.** Jede Pruefung wird **beantwortet**
(auch mit „kein Fund, geprueft in …“).

### 3 — QA-Bericht neu erstellen
- **Teststand** je Ebene — **ausgefuehrt**, mit Kommando und Ergebnis.
- **Abdeckung der 101 AKs** gegen die nachgezogene Matrix; **Luecken** als Liste. Eine gefuellte Matrix
  **allein** zaehlt nicht als Beleg — der Testfall muss existieren und benannt sein.
- **Regressionspruefung**: kein Test wurde ohne Begruendung entfernt oder abgeschwaecht.
- **Coverage** mit den aktuellen Zahlen und der **erzwungenen** Schwelle.
- **Abweichungen** und offene Punkte.

### 4 — Freigabe-Empfehlung
**Freigeben / blockieren** — mit der Zahl je Schwere und der **Gate-Bedingung** (0 Critical, 0 High)
als Kriterium. Wenn ein High offen bleibt: **blockieren**.

## Was du **nicht** tust
- **Der Audit bleibt read-only** fuer `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/`, `docs/requirements/`. Du darfst **Tests** in `tests/` schreiben.
- **Den eingefrorenen Vertrag nicht aendern.** Vertragsdefekt ⇒ **Befund**.
- Keine Aenderung an `docs/requirements/`. In `docs/` nur `docs/qa/`.
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Ergebnisse
- **`docs/qa/security-audit.md`** — **ersetzen**: Nachpruefung (je Befund mit Fundstelle) + das neue
  Audit (alle 10 Pruefungen + OWASP) + Befundtabelle + Gesamtrating + **Freigabe-Empfehlung**.
- **`docs/qa/qa-report.md`** — **ersetzen**: der neue QA-Stand.
- **`.agent/artifacts/security-audit.md`** und **`.agent/artifacts/test-report.md`** — aktualisieren.
- **Sicherheits-Regressionstests** in `tests/` fuer jeden **neuen** Fund, der sich festnageln laesst.

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht nach der Nacharbeit" \
  -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
```

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Kennzahlen). In der zweiten optionalen Zeile bitte nennen:
**Befunde je Schwere**, **Nachpruefung behoben/offen**, **AK-Abdeckung**, **Freigabe-Empfehlung**.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit S-xx/A-xx/Q-xx> | -
```
