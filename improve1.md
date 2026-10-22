# improve1 — was zu verbessern ist

> **Status: umgesetzt in AISDLC3.** Dieses Dokument war die Spezifikation; die
> Umsetzung steht im Commit `feat(sdlc): A-H aus improve1.md` in AISDLC3.
> Die Belegstellen unten stammen aus **AISDLC2** (EchoBox-Lauf) — dort ist der
> Mangel entstanden und dort ist er gemessen.
>
> | Abschnitt | Massnahme | Umgesetzt in |
> |---|---|---|
> | 3 | A — Kette als Gate-Bedingung | `PHASES.md` (GATE 2), `AGENTS.md` (HARD GATES), Workflows 08/09 |
> | 3 | B — Matrix statisch, Check dynamisch | `PHASES.md` (Traceability), `aisdlc-traceability-check` |
> | 3 | C — Check wird zum Füll-Werkzeug | `.agent/tools/traceability_check.py --fill` |
> | 3 | D — Commit-Regel lernt den Nachzug | `PHASES.md` (Commit pro Schritt), `AGENTS.md` (Commit Convention) |
> | 3 | E — Stabile AC-Kennungen | `aisdlc-prd-to-stories`, `aisdlc-test-matrix`, Workflow 01 |
> | 9 | F — Logging-Konvention | `AGENTS.md` (Regel 13), `.agent/artifacts/logging.md`, Workflow 06, `aisdlc-security-scan` |
> | 9 | G — Lauf-Protokoll | `AGENTS.md` (Regel 14), `PHASES.md`, `aisdlc-orchestrator`, `.agent/artifacts/run-log.jsonl` |
> | 9 | H — Grosse Artefakte tabellarisieren | `AGENTS.md` (Regel 15), `aisdlc-grill-stress`, `aisdlc-security-scan` |
>
> **Teil 1** — Traceability: die `sdlc-1`-Commits am Ende der Historie. Grundlage:
> die Analyse des PromptGuard-Laufs in AISDLC (20.–21.10.2026) und die Bestätigung im
> EchoBox-Lauf in AISDLC2 (21.–22.10.2026).
> **Teil 2** — Logging und Lauf-Protokoll: die Pipeline hat keine maschinenlesbare
> Spur von dem, was sie tut, und das Framework schreibt der erzeugten Anwendung kein
> Logging vor.

---

## 1. Der Befund

Im AISDLC-Lauf stehen am Ende **drei `sdlc-1`-Commits**, alle nach Schritt 6 und 7:

```
ea9c4fc  18:37  sdlc-1  Akzeptanzkriterien mit eingefrorenem Vertrag abgeglichen
60d891c  18:24  sdlc-1  Traceability-Matrix gegen Endstand nachgezogen
835900d  17:15  sdlc-1  Traceability-Kette Anforderung→Story→Test→Code vervollständigt
```

Die Agenten haben den Auslöser selbst dokumentiert
(`.agent/artifacts/traceability-fill.md`):

> **Schritt:** 1 (Rücksprung) · **Agent:** `aisdlc-requirements`
> **Stand:** HEAD `c227e1c` (Schritt 7 abgeschlossen)
> **Auslöser:** Der Traceability-Check meldete 89 Lücken: bei **allen** 89
> Matrix-Einträgen waren `tests` **und** `code` leer.

Der Zustand der Matrix, gemessen:

| Stand | Anforderungen mit Test-Referenz | mit Code-Referenz |
|---|---|---|
| vor `835900d` | **0 / 89** | **0 / 89** |
| nach `835900d` | 89 / 89 | 89 / 89 |

**Die Traceability-Matrix war über den gesamten Lauf ein Gerüst.** Sie wurde erst
gefüllt, als Tests und Code existierten — also drei Schritte zu spät.

### 1.2 Der zweite Lauf reproduziert dasselbe Muster

Der EchoBox-Lauf in AISDLC2 (21.–22.10.2026) zeigt denselben Ablauf:

| Zeit | Commit | Schritt |
|---|---|---|
| 21.10 17:39 | `6538d9e` docs(sdlc-1): PRD, User Stories und Traceability-Matrix | 1 — regulär |
| 21.10 18:55 | `cc67445` docs(sdlc-1): PRD und Traceability auf den entschiedenen Stand nachgezogen | **Nachzug 1** |
| 21.10 22:20 | `test(sdlc-5)` Teststrategie und fehlschlagende Tests | 5 |
| 22.10 01:41 | `feat(sdlc-6)` Implementierung der Stories | 6 |
| 22.10 02:18 | `refactor(sdlc-7)` Refactoring und Clean-up | 7 |
| 22.10 02:28 | `825656a` docs(sdlc-1): Traceability-Matrix auf den umgesetzten Stand nachgezogen | **Nachzug 2** |

Der zweite Nachzug liegt **zehn Minuten nach Schritt 7** — genau an der Stelle, an der
Tests und Code existieren. Dieselbe Ursache, dieselbe Wirkung.

**Und damit ist die Regel aus D („höchstens ein Nachzug pro Schritt") im EchoBox-Lauf
zwei Mal verletzt** — beide Male in Schritt 1. Das bestätigt die Diagnose: die Ursache
liegt im Ablauf, nicht in der Matrix. Eine Regel gegen das Symptom hätte nichts
geändert.

## 2. Die vier Ursachen

### 2.1 Die Matrix ist ein Cross-Step-Artefakt, das der Commit-Scope nicht auflösen kann

`docs/requirements/traceability_matrix.json` hat die Felder `tests` und `code`. Die
können **erst Schritt 5/6 füllen** — dort entstehen die Tests und der Code. Die Datei
liegt aber im Schreibbereich von `aisdlc-requirements`; der `aisdlc-execution` darf
dort nicht schreiben.

Ergebnis: sieht Schritt 5/6 die Lücke, muss der Requirements-Agent **nachgeladen**
werden → ein zusätzlicher `sdlc-1`-Commit. Strukturell unvermeidbar.

### 2.2 Gate 1 prüft die Kette nicht

```
GATE 1 | 2 (Architektur) | PRD vollständig, Architektur und Entscheidungen ok
```

Kein Wort über Traceability. Gate 1 konnte passieren, obwohl **0 von 89**
Anforderungen einen Test hatten. Wäre die Kette Gate-Bedingung gewesen, wäre die Lücke
**vor** Schritt 5/6 aufgefallen — und der Nachzug wäre Teil von Schritt 1 gewesen.

### 2.3 Die Commit-Regel kennt den Rücksprung nicht

„Genau ein Commit pro Schritt" — Schritt 1 hat drei. Formal regelwidrig, aber die Regel
hat keinen Fall dafür.

### 2.4 Der Check kam mitten im Lauf

Der `aisdlc-traceability-check` wurde am 21.10 15:00 installiert, der PromptGuard-Lauf
begann am 20.10 18:26. Der Check hat **richtig** reagiert (Rücksprung, Lücke geschlossen)
— nur 15 Stunden zu spät und über einen Rücksprung statt über ein Gate.

**In AISDLC2 ist der Check von Anfang an da.** Die Lücke muss also *strukturell*
beseitigt werden, nicht nur früher erkannt.

## 3. Was zu tun ist

### A — Die Kette wird zur geprüften Gate-Bedingung

**Problem:** Die Lücke fällt erst auf, wenn die Schritte 5/6 durch sind — zu spät für
ein Gate.

**Änderung:**

| Datei | Änderung |
|---|---|
| `.agent/PHASES.md` | Gate-Definition: GATE 2 bekommt die Traceability-Bedingung. Wortlaut siehe unten. |
| `AGENTS.md` | Abschnitt „HARD GATES": dieselbe Bedingung, plus der Hinweis, dass der Check **vor** dem Gate läuft. |
| `.agent/workflows/08-security.md` | `## Gate` um die Prüfbedingung ergänzen |
| `.agent/workflows/09-deploy.md` | dito (GATE 2 liegt davor) |
| `.opencode/skills/aisdlc-traceability-check/SKILL.md` | Abschnitt „Ergebnis protokollieren": der Check ist **Gate-Voraussetzung**, nicht nur ein Hinweis |

**Bedingungstext (GATE 2):**

> Traceability belegt: für jede Anforderung mit Story existiert ein Testfall **und**
> eine Code-Referenz; der Check meldet 0 Lücken. Story ohne Akzeptanzkriterium,
> Akzeptanzkriterium ohne Testfall oder Testfall ohne Code-Referenz blockiert das Gate.

**Wichtig:** Die Prüfung läuft **mechanisch** (Werkzeug), nicht nach Augenmaß. Ein
Gate, das der Mensch „beurteilt", ist kein Gate.

### B — Die Matrix wird statisch, der Check wird dynamisch

**Problem:** `tests` und `code` in der Matrix sind Werte, die erst Schritt 5/6 kennt.
Sie zu füllen erzwingt einen Rücksprung in Schritt 1.

**Änderung: die Matrix beschreibt nur noch die statische Kette.**

| Feld | heute | künftig |
|---|---|---|
| `requirement_id` | ja | ja |
| `story_ids` | ja | ja |
| `acceptance_criteria` | ja | ja |
| `features` | ja | ja |
| `tests` | **leer bis Schritt 6** | **entfällt** |
| `code` | **leer bis Schritt 6** | **entfällt** |
| `coverage_state` | manuell gepflegt | **entfällt** — ergibt sich aus dem Check |

**Die statische Kette** (Anforderung → Story → Feature) ist vollständig in Schritt 1
bekannt und wird dort committet. **Die dynamische Kette** (Anforderung → Test → Code)
ist eine Eigenschaft des Dateisystems und wird **jederzeit** aus ihm ermittelt.

**Umgesetzt in:**

| Datei | Änderung |
|---|---|
| `.opencode/skills/aisdlc-traceability-check/SKILL.md` | Trennung statisch/dynamisch beschreiben; Prüfungen 6 und 7 (Matrix ohne Test/Code) **entfallen**, stattdessen: **Test ohne Matrix-Bezug** und **Code ohne Test** sind die neuen Lücken |
| `.agent/tools/traceability_check.py` | Die Prüfungen 6/7 drehen sich um: nicht „hat die Matrix einen Test?", sondern „gibt es einen Test, der auf keine Anforderung zeigt?" und „gibt es Code, der zu keinem Test gehört?" — plus: **Ausgabe der Matrix** (siehe C) |
| `.agent/workflows/01-requirements.md` | Output-Liste: die Matrix enthält jetzt nur noch Anforderung → Story → Feature |
| `.agent/workflows/05-tdd.md` | Der Check läuft hier und erzeugt den Beleg (siehe C) |
| `.agent/workflows/08-security.md` | dito, Ergebnis ist Gate-Voraussetzung |

### C — Der Check wird zum Füll-Werkzeug

**Problem:** Der Check **meldet** Lücken. Jemand muss sie von Hand schließen — im
PromptGuard-Lauf der Requirements-Agent, drei Schritte zu spät.

**Änderung:** `traceability_check.py` bekommt einen Modus `--fill`, der die Matrix
**mechanisch** aus dem Dateisystem füllt:

```
python3 .agent/tools/traceability_check.py --fill
```

**Grundregel:** Das Werkzeug **schreibt nie einen Pfad, den es nicht belegen kann.**
Für jeden Testfall wird geprüft, ob er im Test eine Story- oder Anforderungs-Referenz
trägt (Tag, Dateiname oder Kommentar). Ohne Beleg bleibt das Feld leer und der
Eintrag landet in der Lückenliste. **Ein erfundener Pfad ist schlimmer als eine
Lücke** — er sieht belegt aus und ist es nicht.

**Ziel:** Das Werkzeug liefert statt einer Lückenliste ein **belegtes Ergebnis**:

| Datei | Inhalt |
|---|---|
| `docs/requirements/traceability_matrix.json` | statische Kette (Anforderung → Story → Feature) + `tests`/`code`, **mechanisch gefüllt** |
| `.agent/artifacts/traceability.md` | der Beleg: Anforderung → Test → Code, mit Prüfvermerk |
| `.agent/artifacts/traceability-fill.md` | was gefüllt wurde, was nicht belegbar war und warum |

**Schreibbereich:** Das Werkzeug schreibt `docs/requirements/traceability_matrix.json`
— das liegt beim `aisdlc-requirements`. Zwei Wege:

- **C1 (empfohlen):** Die Füllung ist ein **expliziter Auftrag an den
  `aisdlc-requirements`** im Rahmen von Schritt 5: der Execution-Agent meldet die
  offenen Referenzen, der Requirements-Agent lässt das Werkzeug laufen und committet
  das Ergebnis. Ein Auftrag, ein Commit, kein Rücksprung.
- **C2:** Der `aisdlc-execution` bekommt eine Ausnahme für genau diese eine Datei.
  Bricht die Folder-Ownership auf — nur für ein Werkzeug, das die Matrix erzeugt.

**Offen:** C1 oder C2. C1 hält die Trennung sauber, kostet aber einen zusätzlichen
Agentenwechsel. C2 ist ein Schritt kürzer, braucht eine Ausnahme in der Rechte-Matrix.

### D — Die Commit-Regel lernt den Nachzug

**Problem:** „Genau ein Commit pro Schritt" kennt den Fall nicht, dass ein späterer
Schritt die Artefakte eines früheren verändert.

**Änderung** in `.agent/PHASES.md` und `AGENTS.md`:

> Genau ein Commit pro Schritt. **Ausnahme:** ein Schritt, der die Artefakte eines
> früheren Schritts korrigiert (z. B. die Traceability-Matrix nach den Tests), erzeugt
> einen zusätzlichen Commit mit dem Subject des **korrigierten** Schritts und dem
> Zusatz `(Nachzug)`. Der Nachzug wird im Commit-Body benannt: aus welchem Schritt, aus
> welchem Anlass.

Beispiel-Subject: `docs(sdlc-1): Traceability-Matrix nachgezogen (Nachzug aus Schritt 6)`

**Regel:** höchstens **ein** Nachzug pro Schritt. Ein zweiter Nachzug ist ein
Zeichen dafür, dass die Ursache im Ablauf liegt, nicht in der Matrix.

### E — Der Vertrag bekommt die Traceability-Bezüge

**Problem:** Die Akzeptanzkriterien der Features kannten die Testfälle nicht
(`ea9c4fc` musste sie nachträglich mit AC-Tags versehen).

**Änderung:** Beim Erzeugen der Features in Schritt 1 bekommt jedes
Akzeptanzkriterium eine **stabile Kennung**, die später im Test wiederkehrt:

```
# features/US-003-token-lifecycle.feature
  Szenario: Token wird beim Refresh rotiert  # AC-003-1
```

Und der Testfall führt dieselbe Kennung:

```java
@Test void ac003_1_tokenWirdBeimRefreshRotiert() { … }
```

**Ergebnis:** Der Check kann Test ↔ Akzeptanzkriterium **mechanisch** zuordnen, ohne
Raten. Die Zuordnung über Dateinamen bleibt als Fallback.

**Änderung** in `.opencode/skills/aisdlc-prd-to-stories/SKILL.md` (Schema der Stories
und Features), `.opencode/skills/aisdlc-test-matrix/SKILL.md` (Namenskonvention der
Testfälle) und `.agent/workflows/01-requirements.md`.

## 4. Reihenfolge der Umsetzung

| # | Schritt | Abhängig von |
|---|---|---|
| 1 | **E** — AC-Kennungen in Features und Testfälle | keine |
| 2 | **B** — Matrix statisch machen, Prüfungen 6/7 drehen | 1 |
| 3 | **C** — `--fill` im Werkzeug, Beleg-Artefakte | 2 |
| 4 | **A** — Kette als Gate-Bedingung | 3 |
| 5 | **D** — Nachzug-Regel in der Commit-Konvention | 4 |

E vor B, weil die mechanische Zuordnung ohne stabile AC-Kennungen nicht geht.
C vor A, weil ein Gate erst etwas prüfen kann, was es auch erzeugen kann.

## 5. Was sich messen lässt

| Kennzahl | heute (PromptGuard) | Ziel |
|---|---|---|
| Anforderungen ohne Test-Referenz | 89 von 89 | 0 |
| `sdlc-1`-Commits | 3 | 1 (plus höchstens 1 Nachzug) |
| Zeitpunkt der ersten erkannten Lücke | nach Schritt 7 | nach Schritt 5 |
| Gate-Prüfungen, die der Mensch per Augenmaß macht | 2 von 2 | 0 von 2 |

## 6. Zu entscheiden

| Frage | Optionen | Empfehlung |
|---|---|---|
| Wer schreibt die gefüllte Matrix? | C1 (Requirements-Agent als Auftragnehmer) · C2 (Execution mit Ausnahme) | **C1** — die Folder-Ownership bleibt sauber |
| Wo liegt der Beleg? | nur in der Matrix · zusätzlich als Artefakt | **beides** — die Matrix ist das Arbeitsformat, das Artefakt der Gate-Vorlage |
| Bleiben `coverage_state` und `manual_criteria`? | streichen · als Ableitung behalten | **ableiten** — sie sind von Hand gepflegte Zwillingsdaten |
| Gilt die Nachzug-Regel nur für die Traceability? | allgemein formulieren · nur hier | **allgemein** — der Fall ist nicht matrixspezifisch |

## 7. Nicht Ziel dieser Verbesserung

- **Keine** Änderung an der Schrittreihenfolge. Die 9 Schritte bleiben.
- **Keine** zusätzliche Rolle. Der Requirements-Agent bleibt der Eigentümer von
  `docs/requirements/`.
- **Kein** Ersatz für `aisdlc-traceability-check`. Der Check bleibt, er wird nur
  vollständiger und kann füllen.
- **Keine** Rückwärtskompatibilität mit dem PromptGuard-Stand. Der ist ein
  abgeschlossener Lauf.

---

## Anhang 1: Belegstellen

### A1 — PromptGuard-Lauf (AISDLC)

| Beleg | Fundstelle |
|---|---|
| Auslöser des Rücksprungs | `.agent/artifacts/traceability-fill.md`, Kopf |
| Matrix vor dem Nachzug | Commit `835900d^` — 0/89 mit `tests`, 0/89 mit `code` |
| Matrix nach dem Nachzug | Commit `835900d` — 89/89 mit `tests`, 89/89 mit `code` |
| AC-Tags nachträglich | Commit `ea9c4fc` — 35 Feature-Dateien, je +6 Zeilen |
| Matrix-Recheck | Commit `60d891c` — 61 Zeilen, SEC-009 belegt |
| Schritt 7 als Stand | Commit `c227e1c` |
| Check war ab da | `aisdlc-traceability-check` installiert in `0d1ee9a` (21.10 15:00) |

### A2 — EchoBox-Lauf (AISDLC2)

| Beleg | Fundstelle |
|---|---|
| Nachzug 1 (auf den entschiedenen Stand) | Commit `cc67445`, 21.10 18:55 |
| Nachzug 2 (auf den umgesetzten Stand) | Commit `825656a`, 22.10 02:28 — 10 min nach `refactor(sdlc-7)` |
| Schritt 7 als Voraussetzung | Commit `825656a^` = `refactor(sdlc-7)` |
| Kein JSON-Artefakt | `.agent/artifacts/` — 23 Dateien, davon 0 `.json` |
| Logging-Defekt als Zufallsfund | `.agent/artifacts/security-audit.md`, Befund **S-36** |
| 30+ maschinenlesbare Fehler | `docs/qa/bugs/BUG-*.json` |
| Betriebs-Runbook aus dem Deploy-Skill | `docs/execution/runbook.md` — Health/Readiness, SLI/SLO, Alerting, Rollback |


---

# Teil 2 — Logging und Lauf-Protokoll

> Zweites Verbesserungsfeld, aus dem EchoBox-Lauf. Hier geht es nicht um die
> Anwendung, sondern um **Sichtbarkeit**: was die Pipeline tut — und was der Betrieb
> protokolliert.

## 8. Der Befund

### 8.1 Logging: im Framework eine Prüfung, kein Konzept

Das Framework nennt Logging an **drei Stellen** — alle als *Prüfung*, keine als *Vorgabe*:

| Stelle | Was dort steht |
|---|---|
| `aisdlc-security-scan` §9 „Logging und Datenschutz" | keine Passwörter, Tokens, vollständigen Personendaten in Logs; Bewertungsstufe „Logging unvollständig" → Medium |
| `aisdlc-deploy` | Smoke-Kriterium: „Logs zeigen keine unerwarteten Fehler" |
| `stack/java/backend` (Stack-Agent, **nicht** Framework) | „Structured logging with correlation ID; no secrets or PII in logs" |

**Was fehlt:**

- **Kein Logging-Artefakt.** Kein Schritt erzeugt eines — die 23 Artefakte des
  EchoBox-Laufs enthalten keins.
- **Keine Regel in `AGENTS.md`/`PHASES.md`**, was die erzeugte Anwendung loggen muss:
  Format, Korrelations-ID, Level, was *nicht* hinein darf, Retention.
- **Keine Gate-Bedingung** zu Logging.
- `coverage/` ist die einzige Runtime-Evidenz-Konvention — und das ist eine **Zahl**,
  kein Log.

**Der Beweis, dass die Lücke real ist:** Der Security-Scan fand **S-36** — eine nicht
unterstützte Methode erzeugt **500 mit Stacktrace im Betriebsprotokoll**, belegt durch
eine Sonde über 15 Methode/Pfad-Kombinationen. Ein reiner Logging-Defekt, gefunden
**nur**, weil die Security-Prüfung ihn sucht. Ohne diese Prüfung wäre er
durchgerutscht.

Was es im EchoBox-Lauf immerhin gibt (aus `aisdlc-deploy`): `docs/execution/runbook.md`
mit Health/Readiness, SLIs/SLOs, Alerting, Rollback, Purge, Secret-Rotation. Das ist die
**Anwendung** — entstanden aus dem Deploy-Skill, nicht aus einer Logging-Regel.

### 8.2 Die Pipeline hat kein Lauf-Protokoll

Der gesamte Ablauf liegt nur in der Git-Historie und in Prosa-Artefakten. Es gibt
**keine** maschinenlesbare Liste: welcher Schritt lief, welche Prüfung bestand, welches
Gate erreicht wurde, wie lange, wie viele Rücksprünge.

Konkret: `.agent/artifacts/ruecksprung-bilanz.md` ist **102 KB Prosa** (845 Zeilen,
davon 352 Tabellenzeilen) über **Rücksprung 6** von Schritt 2. Wertvolles Wissen — aber
nicht auswertbar, nicht zählbar, nicht vergleichbar.

Im Projekt sind **null** JSON-Artefakte unter `.agent/artifacts/`. Die einzigen
maschinenlesbaren Dateien sind die Bug-JSONs (`docs/qa/bugs/BUG-*.json`, 30+) und die
Traceability-Matrix. **Die Fehler des Betriebs sind Daten — der Ablauf des Betriebs
nicht.**

### 8.3 Zwei Kosten, die dazukommen

| | im EchoBox-Lauf |
|---|---|
| Artefakte insgesamt | 23 Dateien, 1,5 MB |
| davon Prosa ohne maschinenlesbare Form | 21 Dateien |
| Laufzeit, in der ein Schritt nach dem anderen lag | nicht messbar (nur aus den Commit-Zeitstempeln rekonstruierbar) |
| Rücksprünge | 6 in Schritt 2 allein, 2 davon in Schritt 1 |

## 9. Was zu tun ist

### F — Eine Logging-Konvention ins Framework

**Problem:** Was geloggt wird, entscheidet der Stack-Agent — oder niemand. Ein
Logging-Defekt wie S-36 wird nur gefunden, wenn die Security-Prüfung ihn sucht.

**Änderung:**

| Datei | Änderung |
|---|---|
| `AGENTS.md` | Neue Regel: die erzeugte Anwendung loggt **strukturiert** — je Zeile ein Ereignis mit Korrelations-ID, Ereignistyp, Mandant, Ergebnis, Dauer. **Nie** Nutzdaten, Secrets, Tokens, vollständige Personendaten. |
| `.agent/PHASES.md` | Neues Artefakt in der Schritt-6-Liste: `.agent/artifacts/logging.md` — Log-Schema, verbotene Inhalte, Retention, Korrelations-ID-Fluss |
| `.agent/workflows/06-implementation.md` | Output-Liste um `logging.md` ergänzen; der Feature-Slice-Skill verweist darauf |
| `.opencode/skills/aisdlc-security-scan/SKILL.md` | §9 von „Prüfung" zu „geprüfte Konvention": prüft **gegen** `.agent/artifacts/logging.md`, nicht gegen eine erfundene Erwartung |
| `.opencode/skills/aisdlc-containerize/SKILL.md` | Health-Check und Logausgabe gehören zusammen — der Container schreibt in ein format, das der Health-Check liest |

**Ergebnis:** S-36 wäre eine **systematische Klasse** (Logging-Konvention verletzt),
nicht ein Zufallsfund. Und die Konvention wird einmal geschrieben statt in jedem Stack
neu erfunden.

### G — Ein Lauf-Protokoll als Daten

**Problem:** Der Ablauf ist nur aus Prosa und Commit-Zeitstempeln rekonstruierbar.
Ohne Daten keine Messung — und die letzten Verbesserungen wurden aus dem Gefühl
entschieden, nicht aus der Historie.

**Änderung:** eine Datei `.agent/run-log.jsonl` — **eine Zeile je Ereignis**, append-only:

```json
{"ts":"2026-10-22T01:41:03Z","event":"step_start","step":6,"agent":"aisdlc-execution"}
{"ts":"2026-10-22T02:18:41Z","event":"step_end","step":6,"commit":"825656a","duration_s":2258}
{"ts":"2026-10-22T02:28:11Z","event":"jump_back","from":7,"to":1,"reason":"Traceability-Matrix unvollstaendig"}
{"ts":"2026-10-22T02:29:02Z","event":"gate","gate":1,"result":"approved","by":"human"}
{"ts":"2026-10-22T02:30:00Z","event":"check","name":"traceability_check","result":"fail","gaps":178}
```

**Wer schreibt:** der Orchestrator (`aisdlc-orchestrator`). Er ist der Einzige, der
Schrittgrenzen und Gates sieht. Ein Schritt-Agent kennt seinen Schritt, aber nicht den
Ablauf.

**Änderung:**

| Datei | Änderung |
|---|---|
| `.opencode/agents/aisdlc-orchestrator.md` | Pflicht: je Schrittstart/-ende, Gate und Prüfergebnis eine Zeile nach `.agent/run-log.jsonl` schreiben. Das Artefakt gehört in jeden Schritt-Commit (`.agent/artifacts/` ist überall im Scope). |
| `.agent/artifacts/` | `run-log.jsonl` als **eigenes** Artefakt, nicht in `.agent/` — es ist ein Artefakt des Laufs, kein Teil des Frameworks |
| `AGENTS.md` | Regel: „Jeder Schritt beginnt und endet mit einer Zeile im Lauf-Protokoll" |

**Ergebnis:** Leerlauf, Rücksprung-Zyklen und Schritt-Dauern werden messbar. Und die
nächste Verbesserungsrunde wird **aus den Daten** entschieden, nicht aus dem Gefühl.

### H — Große Artefakte tabellarisieren

**Problem:** `ruecksprung-bilanz.md` (102 KB Prosa) ist auffindbar, aber nicht
auswertbar. Wer „wie viele Rücksprünge gab es und welche Befunde blieben offen"
fragt, muss 102 KB lesen.

**Änderung:** Jedes Artefakt über ~20 KB bekommt **eine Tabelle am Kopf**:
Befund, Schwere, Status, wo behoben. Die Herleitung bleibt darunter. Die Tabelle ist
der Einstieg, die Herleitung der Beleg.

**Änderung** in `AGENTS.md` (Regel „Output") und in den Skills, die große Berichte
erzeugen (`aisdlc-grill-stress`, `aisdlc-security-scan`).

## 10. Reihenfolge der Umsetzung (Teil 2)

| # | Schritt | Abhängig von |
|---|---|---|
| 1 | **G** — Lauf-Protokoll | keine — liefert die Daten, mit denen F und H bewertet werden |
| 2 | **F** — Logging-Konvention | G (der Betrieb zeigt, wo Logs fehlen) |
| 3 | **H** — Artefakte tabellarisieren | G (die Tabelle zeigt, welche Artefakte es betrifft) |

**G zuerst:** Ohne Daten ist die Reihenfolge von F und H geraten. Mit dem Lauf-Protokoll
lässt sich messen, welche Artefakte die teuersten sind und wo Logging fehlt.

## 11. Was sich messen lässt

| Kennzahl | heute | Ziel |
|---|---|---|
| Maschinenlesbare Artefakte | 3 von 23 | 23 von 23 |
| Schritt-Dauern messbar | nein (nur Commit-Zeitstempel) | ja, aus dem Lauf-Protokoll |
| Rücksprünge zählbar | nein (nur aus der Prosa) | ja, aus dem Lauf-Protokoll |
| Logging-Vorgaben im Framework | 0 | 1 (Regel + Artefakt) |
| Logging-Defekte als systematische Klasse | 0 (S-36 per Zufallsfund) | 1 (Prüfung gegen die Konvention) |

## 12. Zu entscheiden

| Frage | Optionen | Empfehlung |
|---|---|---|
| Wo liegt das Lauf-Protokoll? | `.agent/run-log.jsonl` · `.agent/artifacts/run-log.jsonl` | **`.agent/artifacts/`** — es ist ein Artefakt des Laufs, kein Teil des Frameworks. Sonst wäre es beim Kopieren des Frameworks dabei und würde in fremden Läufen verwirren. |
| Schreibt der Orchestrator oder ein Werkzeug? | Orchestrator · Werkzeug mit Hook | **Orchestrator** — er sieht Schrittgrenzen und Gates; ein Werkzeug müsste dafür in jeden Schritt eingebaut werden |
| Wie lange bleibt das Protokoll? | ein Lauf · 30 Tage · unbegrenzt | **ein Lauf** (im Artefakt des Laufs) — die Historie steht ohnehin in Git |
| Wird die Logging-Konvention Teil des Frameworks oder des Stacks? | Framework · Stack | **Framework** — sie ist stackunabhängig (Korrelations-ID, keine Nutzdaten). Der Stack liefert nur die *Umsetzung* (Logback, JSON-Format). |
