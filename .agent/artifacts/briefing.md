# Steckbrief — Schritt 1 (Nachzug: Traceability-Matrix auf den umgesetzten Stand)

> Einstiegspunkt für den Rollen-Agenten. Ein **Zeiger**: Status, Commit, Festlegungen,
> offene Punkte. **Die Dateien sind die Wahrheit, nicht dieser Steckbrief.**

| Feld | Wert |
|---|---|
| Stand | Schritt 7 abgeschlossen — Commit `bd15f19` `refactor(sdlc-7): Refactoring und Clean-up`; davor Schritt 6 `e9e1bc1`, Schritt 5 `f5b8054`, Schritt 2 `8627bb6`, Schritt 4 `68ffa4e`, Schritt 3 `05bceca`, Schritt 1 `cc67445` |
| Dieser Schritt | 1 (Anforderungserhebung) erneut — **ausschliesslich als Nachzug**: die Traceability-Matrix auf die **umgesetzte** Wirklichkeit nachziehen |
| Nächster Schritt | 8 (Sicherheit & Compliance) — danach **GATE 2** |
| Beleg | `.agent/artifacts/traceability-verification.md` — die **gegen das Repository gepruefte** Kette (Schritt 7) |
| Code | `src/` (263 Dateien), `tests/` (75 Dateien); alles gruen |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership, Commit Convention |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate; Commit pro Schritt |
| 3 | `.agent/workflows/01-requirements.md` | dieser Schritt, Commit-Befehl |
| 4 | `.agent/prompts/orchestrator/01_orchestrator_requirements.md` | dein Systemprompt |
| 5 | `.agent/artifacts/traceability-verification.md` | **der Beleg** — die gepruefte Kette, die Luecken und die Korrekturvorschlaege |
| 6 | `docs/requirements/traceability_matrix.json` | die Matrix, die nachgezogen wird |
| 7 | `.agent/artifacts/test-matrix.md` | die Testmatrix aus Schritt 5 (101 AKs → Testfaelle) |
| 8 | `docs/requirements/stories/US-0xx.json` | die 101 AKs |

## Was zu tun ist — ausschliesslich die Matrix nachziehen
Die Matrix fuehrt die Test- und Code-Referenzen noch mit `state: "planned"` und nennt teils
**stale** Pfade. Nach der Implementierung ist das eine **Unwahrheit** — und Schritt 8 prueft die
Kette gegen die tatsaechlichen Dateien.

### 1 — Zustaende und Pfade korrigieren
- Fuer **jeden** `tests`- und `code`-Eintrag: `state` auf den **tatsaechlichen** Zustand setzen
  (z. B. `implemented`/`verified` — **folge dem Schema der Matrix**, erfinde keine neuen Zustaende
  ohne Grund; wenn das Schema keinen passenden Zustaend kennt, erweitere es **konsistent**).
- **Pfade** auf die existierenden Dateien korrigieren. **Pruefe jeden Pfad** — er muss im
  Repository existieren. Ein Pfad, den es nicht gibt, wird entweder auf den **tatsaechlichen**
  Pfad korrigiert oder als **offen** mit Begruendung und Schritt markiert.

### 2 — Die sechs stale geplanten Codepfade (Abschnitt 4.1 des Belegs)
Fuenf davon sind **geflossen** (die Verantwortung liegt bei einem anderen Baustein): `crypto/SecretStore.java`,
`delivery/PayloadSource.java`, `delivery/RetryScheduler.java`, `tenant/TenantConnectionInitializer.java`,
`config/TenantInstanceConfiguration.java` — **jeweils auf den tatsaechlichen Pfad umhaengen** und
die Erklaerung in der Matrix ergaenzen.
`docker-compose.yml` ist eine **echte Luecke** (gehoert Schritt 9) — als **offen** mit Schritt 9
als Owner markieren, **nicht** als erledigt.

### 3 — Fehlende Testeintraege ergaenzen
- **AC-023-2** und **AC-023-5** (NFR-006) fehlen in der Matrix — die sie belegenden Dateien
  `tests/integration/deployment/TenantInstanceIT.java` und
  `tests/integration/deployment/EnvironmentSeparationIT.java` eintragen.
- Die mit **†** markierten Dateien (Abschnitt 3/4.3 des Belegs): die Matrix soll **jede** Datei
  nennen, die ein AK des Eintrags belegt — also die querschnittlichen Dateien
  (`ForeignResourceIsolationIT`, `RoleCapabilityMatrixIT`, `AlwaysPresentFieldsIT`,
  `AlwaysPresentFieldsContractTest` u. a.) **mit aufnehmen**. Mach daraus keine Duplikate,
  sondern je Anforderung eine **vollstaendige** Liste.
- Die **zwei AK ohne Matrix-Eintrag** (aus dem Beleg) eintragen — mit Story, Anforderung und Test.

### 4 — Was **nicht** geaendert wird
- **Keine** Anforderung, **keine** Story, **kein** Akzeptanzkriterium, **keine** Feature-Datei
  aendern. Der Umfang bleibt 23 Stories / 101 AKs / 24 Anforderungen.
- **Keine** Entscheidung zu O-04, O-05, R-01/E-13, E-12, B-17, B-18 treffen.
- **Keine** Aenderung an `docs/api/`, `docs/adr/`, `docs/architecture/`, `.agent/knowledge/`,
  `docs/Anforderung.md`, `src/`, `tests/`.

## Was du in diesem Schritt **nicht** tust
- **Keinen Anwendungscode**, keine Tests — das ist Schritt 6/7.
- **Keine Gherkin-Szenarien** ergaenzen (entschieden in Schritt 4: die Testmatrix entsteht aus den
  101 AKs, die Szenarien bleiben Smoke-Layer).
- **Keinen Klassen-/Modulnamen** in der Matrix umbenennen (E-05: der Name bleibt; die Semantik
  steht jetzt in ADR-002).
- Kein `git push`. Keine Ausgabe-Kommandos (`echo`, `printf`, `pwd`, `cat` zur Orientierung).

## Prüfung vor dem Commit
- `python3 .agent/tools/traceability_check.py` → gruen.
- **Jeder** Pfad in der Matrix (Test und Code) **existiert** — oder ist ausdruecklich als offen
  mit Owner markiert. Pruefe das maschinell (Datei existiert?).
- Der Umfang bleibt 23 Stories / 101 AKs / 24 Anforderungen.

## Commit — genau einer, Befehl aus der Workflow-Datei
```bash
mkdir -p docs/requirements/ .agent/artifacts/
git reset -q
git add -A -- docs/requirements/ .agent/artifacts/
git commit -m "docs(sdlc-1): Traceability-Matrix auf den umgesetzten Stand nachgezogen" \
  -m "Schritt 1 - Anforderungserhebung (Nachzug) | Agent: aisdlc-requirements | Artefakte: docs/requirements/, .agent/artifacts/"
```
Wenn sich nichts geaendert hat: kein Commit.

## Antwortformat — genau so, die drei Zeilen ZUERST
Danach hoechstens zwei optionalen Zeilen (Pruefergebnis). Keine Herleitung, keine Auszuege.

```
STATUS: done | done_with_open_points | blocked
COMMIT: <hash> <subject>
OFFENE PUNKTE: <kurz, mit O-xx/B-xx> | -
```
