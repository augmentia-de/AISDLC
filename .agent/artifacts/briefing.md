# Steckbrief — Schritt 8 (dritte Pruefrunde vor GATE 2)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Ruecksprung Schritt 6/7 abgeschlossen — Commit `3a5ae70` `fix(sdlc-6): Security-Befunde S-28 bis S-34 behoben`; davor Schritt 8 (Runde 2) `3d40912`, Schritt 2 (Korrektur) `28396e0`, Ruecksprung 6/7 `d135cda`, Schritt 8 (Runde 1) `1ba4160`, Schritt 7 `bd15f19`, Schritt 6 `e9e1bc1` |
| Dieser Schritt | 8 (Sicherheit & Compliance) — **dritte Runde**: Nachpruefung + erneutes Audit |
| Nächster Schritt | **GATE 2** → danach Schritt 9 (Deployment & Betrieb) |
| Gate-Bedingung | **Security: 0 Critical und 0 High · QA: 0 Critical** |
| **Vertrag** | `docs/api/openapi.yaml` — **EINGEFROREN seit `8627bb6`**, in beiden Nacharbeiten **nicht** angefasst |
| Schema | `.agent/knowledge/schema.sql` + Spiegel + Migration — alle drei byte-identisch |
| Teststand | `mvn verify` gruen (126 Unit + 301 Integration, **Coverage 85,99 %**, Gate erzwungen) · Vertrag 19 + Last 2 gruen · `tsc` 0 · lint 0 · build ok · Vitest 30 · Playwright 31 · beide Schema-Diffs leer · `traceability_check.py` gruen · **alle 10 Gap-Tests der Runden 1+2 gruen** + 22 neue Regressionstests |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/08-security.md` | dieser Schritt, Pruefpunkte, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/04_orchestrator_testing.md` | dein Systemprompt |
| 5 | `docs/qa/security-audit.md` | **Runde 2** — die Befunde S-28…S-34 und die Nachpruefung der Runde 1 |
| 6 | `docs/qa/qa-report.md` | der QA-Stand, Q-8/Q-9 |
| 7 | `docs/execution/step8-nacharbeit-security.md` und `…-run2.md` | die beiden Nacharbeiten |
| 8 | `docs/api/openapi.yaml` | der eingefrorene Vertrag |
| 9 | `.agent/knowledge/schema.sql` | RLS, Eigentuemerfunktionen, Constraints |

## Dein Auftrag

### 1 — Nachpruefung mit **eigenem** Nachweis
Pruefe **jeden** Befund der Runde 2 am aktuellen Stand — **nicht** nach dem Bericht der Nacharbeit:
**S-28** (Body-Grenze unabhaengig vom Medientyp; teste **jeden** Medientyp: `application/json`,
`application/*+json`, `text/plain`, `multipart/form-data`, `application/octet-stream`, ohne
`Content-Type`; ist die Grenze noch umgehbar?), **S-29** (leere Audience ⇒ Startfehler, fail-closed),
**S-30** (nicht unterstuetzter Medientyp ⇒ 4xx ohne Stacktrace), **S-31** (Tomcat-Version oberhalb der
Fix-Linie — `mvn dependency:tree`), **S-32** (SCA-Baseline vorhanden und gepflegt), **S-33** (Token
ohne Mandant ⇒ 401), **S-34** (Javadoc aktuell).
Bewerte je Befund: **behoben / teilweise behoben / nicht behoben**, mit Fundstelle.

### 2 — Erneutes **vollstaendiges** Audit
Alle **10 Pruefungen** des Skills `aisdlc-security-scan` **erneut und vollstaendig**, mit Schwerpunkt
auf dem, was **neu** entstanden ist:
- die **neue** Body-Schaetzung (S-28 neu) — ist sie jetzt medientyp-unabhaengig? Umgehbar ueber
  `Content-Encoding: gzip`, `Transfer-Encoding`, fehlenden `Content-Length`, oder einen Body, den der
  Message-Converter selbst liest?
- die **fail-closed** Audience-Pruefung (S-29 neu) — Fehlerverhalten, Konfigurationswege;
- die **neue** Exception-Handler-Abbildung (S-30 neu) — leaken andere Ausnahmen weiterhin Stacktraces?
- die **Rate-Limit-Antwort** (S-24, Runde 1) — ist sie weiterhin korrekt (429 + `Retry-After`)?
- die **CI** (S-25/S-32 neu) — Secrets in Logs, Baseline-Pflege, OIDC, geschuetzte Branches.
- das **Produktionsprofil** und die **Containerhaertung** (S-26/S-27 neu).
Dazu die **OWASP-Querpruefung** A01…A10 mit Ergebnis je Zeile.
**Null Funde sind ein Ergebnis — aber nur, wenn geprueft wurde.** Jede Pruefung wird **beantwortet**
(auch mit „kein Fund, geprueft in …“). Kein Befund ohne **belegten** Nachweis; Vermutungen sind
**offene Fragen**.

### 3 — QA-Bericht neu erstellen
- **Teststand** je Ebene — **ausgefuehrt**, mit Kommando und Ergebnis.
- **Abdeckung der 101 AKs** gegen die nachgezogene Matrix; **Luecken** als Liste (eine gefuellte
  Matrix **allein** zaehlt nicht als Beleg — der Testfall muss existieren und benannt sein).
- **Regressionspruefung**: kein Test ohne Begruendung entfernt oder abgeschwaecht.
- **Coverage** mit den aktuellen Zahlen und der **erzwungenen** Schwelle.

### 4 — Freigabe-Empfehlung
**Freigeben / blockieren**, mit der Zahl je Schwere und der Gate-Bedingung (0 Critical, 0 High) als
Kriterium. Bleibt ein High offen: **blockieren** — und nenne Befund und Ursache.

## Was du **nicht** tust
- **Der Audit bleibt read-only** fuer `src/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/`, `docs/requirements/`. Du darfst **Tests** in `tests/` schreiben.
- **Den eingefrorenen Vertrag nicht aendern.** Vertragsdefekt ⇒ **Befund**.
- In `docs/` nur `docs/qa/`. Kein `git push`. Keine Ausgabe-Kommandos.

## Ergebnisse
- **`docs/qa/security-audit.md`** — **ersetzen**: Nachpruefung + neues Audit + Befundtabelle +
  Gesamtrating + **Freigabe-Empfehlung**.
- **`docs/qa/qa-report.md`** — **ersetzen**.
- **`.agent/artifacts/security-audit.md`**, **`.agent/artifacts/test-report.md`** — aktualisieren.
- **Sicherheits-Regressionstests** fuer jeden **neuen** Fund, der sich festnageln laesst.

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht nach der zweiten Nacharbeit" \
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
