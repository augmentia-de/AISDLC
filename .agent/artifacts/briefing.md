# Steckbrief — Schritt 8 (Sicherheit & Compliance)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 7 abgeschlossen — Commit `refactor(sdlc-7): Refactoring und Clean-up` (Hash: `git log -1`); davor Schritt 6 `e9e1bc1`, Schritt 5 `f5b8054`, Schritt 2 `8627bb6`, Schritt 4 `68ffa4e`, Schritt 3 `05bceca`, Schritt 1 `cc67445` |
| Dieser Schritt | 8 (Sicherheit & Compliance) — danach **GATE 2** |
| Nächster Schritt | **GATE 2** (menschliche Freigabe), danach 9 (Deployment & Betrieb) |
| **Vertrag** | `docs/api/openapi.yaml` — **EINGEFROREN seit `8627bb6`**. Ein Vertragsdefekt ist ein Befund fuer einen Ruescksprung zu Schritt 2, **keine** Aenderung im Vorbeigehen. |
| Schema | `.agent/knowledge/schema.sql` + Spiegel `docs/architecture/schema.sql` + Migration `src/main/resources/db/migration/V1__initial_schema.sql` — **alle drei byte-identisch** |
| Code | `src/` (Backend + `src/main/frontend/`), `tests/` — in Schritt 7 vereinfacht (**+290 / −486 Zeilen**, 48 Dateien), **verhaltenserhaltend** |
| Teststand | `mvn -q verify` gruen (89 Unit, 258 Integration, 19 Vertrag, 2 Last) · `tsc --noEmit` 0 · ESLint 0 · `next build` ok · Vitest 30 gruen · Playwright 31 gruen (3 ohne JavaScript) · beide Schema-Diffs leer · `contract:check` ok · `traceability_check.py` gruen |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/08-security.md` | dieser Schritt, Pruefliste, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/04_orchestrator_testing.md` | dein Systemprompt |
| 5 | `.agent/artifacts/refactoring-report.md` | das **Schulden-Inventar** und die verbleibenden Schulden (S-18 … S-22) — die Kandidaten fuer den Security-Scan |
| 6 | `.agent/artifacts/traceability-verification.md` | der Nachweis der Kette Anforderung → AK → Testfall → Codepfad (130 von 130 AK belegt) |
| 7 | `.agent/knowledge/stack.md` | Stack- und Stilentscheidungen (Auth, Verschluesselung, Deployment) |
| 8 | `docs/api/openapi.yaml` | der eingefrorene Vertrag — das Orakel des Scans |

Bei Bedarf: `docs/adr/ADR-*.md`, `docs/requirements/stories/US-0xx.json`,
`docs/execution/step6-umsetzungsbericht.md`.

## Entscheidungen, die gelten — der Vertrag, die ADRs und das Schema sind das Orakel

D-01…D-15 · ADR-001…ADR-018 · F-01…F-04 · E-01…E-11 · E-13 · V-26…V-27 · V-39…V-44 · V-48…V-49
**Schritt 7 hat das Verhalten nicht geaendert.** Diese Entscheidungen bleiben unangetastet.

## Offene Punkte

| ID | Thema | Owner |
|---|---|---|
| O-04 | Verschluesselung im Ruhezustand — fachliche Pflichtfrage | Fachbereich + Datenschutz, Gate 2 |
| O-05 | AVV bei Fremddaten | Fachbereich + Recht, Gate 2 |
| R-01 / E-13 | Karenzzeit der Aufbewahrung — kein Vorgabewert | Fachbereich, Gate 2 |
| E-12 | Haertung der mandantenuebergreifenden Sicht | Fachbereich + Plattform, Gate 2 |
| B-10 | Bereitstellungsdeskriptor nur vorbereitet | Schritt 9 |
| B-12 | `next lint` ueberholt → Lint als eigener CI-Schritt | Schritt 9 |
| B-13 | npm-Workspace-Wurzel fuer die Tests unter `tests/` | Schritt 9 |
| B-17 | Die Uebergangstabelle des Domänenmodells nennt nur `failed → delayed` | Architektur (Schritt 8) |
| B-18 | `endpoint_event_types` ohne eindeutigen Schluessel je Mandant | Gate 2 |
| S-18 | `DeliveryRepository` (867 Z.) / `EndpointRepository` (566 Z.) — Aufteilung beruehrt Transaktionsgrenzen | Schritt 8/9 |
| S-22 | `docker-compose.yml` fehlt (lokale Entwicklung) | Schritt 9 |
| ~~B-16~~ | ~~doppelte Nummern in den Nachzug-Begruendungen~~ | **erledigt** in Schritt 7 ✓ |

## Was du in diesem Schritt **nicht** tust
- **Kein Anwendungscode.** Schritt 8 ist **read-only** fuer `src/` — der
  Security-Scan dokumentiert, er repariert nicht.
- **Den eingefrorenen Vertrag nicht aendern.** Vertragsdefekt ⇒ **Befund** melden.
- **Keine Aenderung** an `docs/requirements/`, `docs/api/`, `docs/adr/`, `docs/architecture/`,
  `.agent/knowledge/`, `docs/Anforderung.md`. In `docs/` nur `docs/qa/`.
- **Kein Schema-Anfassen** — die Migration bleibt byte-identisch.
- **Keine Secrets** in Code, Konfiguration oder Image — und **keine** Secrets in
  den Audit-Bericht (Werte nur als Fundstelle, nie als Wert).
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Ergebnisse
- `.agent/artifacts/security-audit.md` — der Scan mit **Critical / High / Medium / Low**.
- `docs/qa/` — der QA-Bericht (Regressionspruefung, Abdeckung, Restrisiken).
- `tests/` — nur wenn der Scan **neue** Testfaelle braucht (der Schritt darf Tests
  ergaenzen; `src/` nicht).

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| Schema-Diffs | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` und `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` |
| Backend | `mvn -q verify` |
| Oberflaeche | `npx tsc --noEmit` · `npm run lint` · `npm run build` · `npx vitest run` |
| Browser-Tests | `npx playwright test` **im Verzeichnis `src/main/frontend`** — aus der Projektwurzel findet Playwright keine Konfiguration und zieht die Vitest-Dateien mit. Aus der Wurzel: `npm run test:e2e` |
| Selbstkorrektur bei Fehlschlag | Skill `test-self-correction` |

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p tests/ docs/qa/ .agent/artifacts/
git reset -q
git add -A -- tests/ docs/qa/ .agent/artifacts/
git commit -m "docs(sdlc-8): Security-Audit und QA-Bericht" \
  -m "Schritt 8 - Sicherheit & Compliance | Agent: aisdlc-qa | Artefakte: tests/, docs/qa/, .agent/artifacts/"
```
Wenn sich nichts geändert hat: kein Commit.
