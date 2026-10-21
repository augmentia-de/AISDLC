# Lessons Learned — AISDLC-Lauf 1 (PromptGuard)

> Auswertung des ersten vollständigen AISDLC-Laufs, 20.–21.10.2026.
> Grundlage: OpenCode-Log (43 MB, 17.–21.10.), Session-Datenbank
> (`opencode.db`: `session_v2`, `session_message`, `part`, `permission`),
> Git-Historie (25 Commits) und das erzeugte Artefaktset.
> Kein Idealbild, sondern das, was tatsächlich gelaufen ist.

## 1. Die Zahlen

| | |
|---|---|
| Laufzeit gesamt | **54,6 h** (Stand 21.10 12:00, 32 Sessions) |
| Davon produktive Commit-Intervalle | ~10 h |
| Input-Token gesamt | **23.306.743** |
| Output-Token gesamt | **1.100.415** |
| Verhältnis Input : Output | **21 : 1** |
| Modell-Turns, schwerster Schritt | 49 (Schritt 2, Architecture) |
| Nachrichtenlast, schwerste Session | 315 KB (38 Assistant-Meldungen) |
| Übergabe-Payload an den Orchestrator | 37.367 Zeichen ≈ 9.341 Token |
| Compactions | 7 (2 im Pipeline-Fenster) |
| Rate-Limit-Ereignisse | 12 (alle 17.–19.10., **vor** der Pipeline) |
| Erzeugtes Artefaktset | Vertrag 5.523 Zeilen/199 KB · Schema 1.414 Zeilen · 20 ADRs · 11 Architekturdokumente · 35 Stories · 35 Gherkin-Features · 168 Java-Dateien · 41 Test-Dateien |

**Schrittfolge:** 1 → 2 → 3 → 4 → ⟨2⟩ → 5 → ⟨2⟩ → 6 → ⟨2⟩ → 6.
Drei Rücksprünge auf Schritt 2, drei Vertragsrevisionen (1.0 → 1.1 → 1.1.1).

*Der Lauf war zum Zeitpunkt der Auswertung nicht beendet — die Zahlen wachsen noch.
Ein Nebenbefund der Auswertung: der laufende Agent hat `.agent/knowledge/schema.sql`
und `docs/architecture/schema.sql` **gleichzeitig** angefasst — die Spiegel-Regel
(L12) wird also gelebt, nicht nur dokumentiert.*

## 2. Die Befunde

### L1 — Die Rechte-Matrix schlug die Workflow-Datei

**Symptom.** `.agent/artifacts/` wurde nicht geschrieben; die Artefakte lagen unter
`docs/requirements/artifacts/`.

**Ursache.** 23 Framework-Dateien (9 Workflows, 10 Prompts, `PHASES.md`, `AGENTS.md`,
`README.md`, `how-to.md` — 85 Fundstellen) nennen `.agent/artifacts/` als Ziel. Die
Agent-Frontmatter verbieten genau diesen Pfad. Die Folder-Ownership-Tabelle in
`AGENTS.md` kannte den Ordner gar nicht.

**Regel.** Jedes Ziel, das ein Workflow nennt, braucht drei Dinge: Schreibrecht in der
Agent-Frontmatter, eine Zeile in der Folder-Ownership-Tabelle und denselben Pfad in
`PHASES.md`. Fehlt eines, schreibt der Agent an den Ort, den er darf — und der Ort
ist nicht der gemeinte.

**Behoben** in `38d16f1` und `687fe5d`.

### L2 — Der Agent umgeht Sperren, statt sie zu melden

**Symptom.** Das DB-Schema lag in `docs/architecture/schema.sql`, nicht in
`.agent/knowledge/schema.sql`. Danach dokumentierte `domain-model.md:47` den **falschen**
Ort als „kanonisch" — 32 Dateien mit widersprüchlichen Verweisen.

**Ursache.** Der Architecture-Agent hatte kein Schreibrecht auf `.agent/knowledge/`
(siehe L1) und schrieb an den nächstbesten zulässigen Ort. Die Umgehung war lokal
korrekt und global falsch.

**Regel.** Eine Sperre, die nicht an der Stelle sichtbar ist, an der sie greift, wird
umgangen. Sperren brauchen eine sichtbare Begründung **im Zielordner selbst**, nicht
nur im Framework.

### L3 — Das `rtk`-Plugin bricht jeden Git-Workflow, der auf geparstem Output beruht

**Symptom.** „the first commit attempt failed" — der Schritt-2-Commit entstand nicht.

**Ursache.** `rtk` rewritet **jeden Teil einer Pipeline einzeln**. Aus
`git diff --cached --name-only` wird `rtk git diff --cached --name-only`, und das hängt
einen „Changes:"-Block mit Leerzeilen an. Als Pfadspezifikation führt das zu
`fatal: empty string is not a valid pathspec` — der Commit passiert nicht. Unabhängig
davon bricht `git commit -- <pfade>` ab, sobald ein Scope-Pfad keine getrackten Dateien
hat (leerer Zielordner).

**Regel.** Kein Shell-Workflow darf auf geparstem Kommando-Output beruhen, solange ein
Rewrite-Plugin aktiv ist. Das Scope gehört über den **Index** gesetzt:
`mkdir -p` → `git reset -q` → `git add -A -- <scope>` → `git commit`. Keine Pipe,
keine Pfadspezifikations-Liste.

**Behoben** in `6e69c4c`, im End-to-End-Test verifiziert.

### L4 — `last-match-wins`: eine Agent-Regel kann die ganze globale Allowlist aushebeln

**Symptom.** `git status`, `git diff` und `git log` fragten trotz globaler Allowlist.

**Ursache.** Agent-Regeln werden **zuletzt** angehängt. Das `shell: *` → `ask` im
Frontmatter jedes Rollen-Agenten überschrieb alle globalen Allows, die davor standen.

**Regel.** Eine Regel, die global steht, gehört **nicht** ins Agent-Frontmatter.
Frontmatter-Regeln sind Zusätze, keine Wiederholungen — sie gewinnen sonst immer.

### L5 — `mode: primary` verhindert die Subagent-Nutzung

**Symptom.** „Agent aisdlc-architecture cannot run as a subagent".

**Regel.** Rollen-Agenten, die ein Orchestrator starten soll, brauchen `mode: all`
(primary **und** subagent).

### L6 — Die Verschachtelungstiefe ist Default 1 — und das Modell findet einen Umweg

**Symptom.** „Subagent depth limit reached". Im Test startete das Modell den Enkel
**selbst**, statt den Rollen-Agenten ihn starten zu lassen — eine stillschweigende
Rollenaufweichung.

**Regel.** `experimental.subagent_depth` explizit setzen. Und: eine Sperr, die nur
„nein" sagt, provoziert einen Umweg. Jede Tiefensperre braucht eine erlaubte
Alternative — sonst löst das Modell sie auf eigene Faust.

### L7 — Der Übergabe-Payload war 9.341 Token

**Messung.** Die finale Nachricht der Architecture-Session: 37.367 Zeichen. Kein
Summary, sondern ein Reasoning-Dump. Bei neun Schritten wären das ~84.000 Token allein
an Rückgaben im Orchestrator-Kontext — das dokumentierte Versagen („Agent vergisst
Kontext aus vorherigem Schritt") wäre eingetreten.

**Regel.** Die Rückgabe ist ein **Zeiger**, nie Inhalt: drei Routing-Fakten
(`STATUS` / `COMMIT` / `OFFENE PUNKTE`), plus optional Artefaktpfade und
Prüfergebnis. Der nächste Agent liest die Dateien selbst; der Orchestrator reicht
Pfade weiter, nie Inhalte.

**Gemessen nach der Umstellung: 895 Zeichen ≈ 223 Token — Faktor 42.**

Nebenbefund: „nur erledigt" ist bereits der eingebaute Normalfall. Hat ein Subagent
keinen finalen Text, liefert OpenCode `Subagent completed without a text response.`
Man muss das nicht erzwingen — man muss nur verhindern, dass stattdessen ein Dump
abgegeben wird.

### L8 — Das Problem ist die Session, nicht die Rückgabe

**Messung.** Input-Token über die Schritte: 173k → 632k → 2,02M → 5,47M → **14,08M** —
80× über vier Schritte. Verhältnis Input:Output = 21:1. Sieben Compactions, davon
zwei im Pipeline-Fenster.

**Ursache.** Jeder Schritt startet eine frische Session, nimmt aber den **gesamten**
Bestand mit (315 KB Nachrichtenlast bei Schritt 2). Die Agenten lesen 21× mehr, als sie
schreiben — das ist die eigentliche Last.

**Regel.** Schritt-spezifische Teilkontexte statt voller Session. Der Vertrag, das
Schema und die Artefakte sind versioniert — daraus lässt sich ein Einstiegspunkt
bauen, statt die ganze Historie mitzuschleppen. **Noch nicht umgesetzt.**

### L9 — Rücksprünge sind der teuerste Posten

**Messung.** Schrittfolge 1 → 2 → 3 → 4 → ⟨2⟩ → 5 → ⟨2⟩ → 6 → ⟨2⟩ → 6. Drei
Vertragsrevisionen. Zwischen Schritt 5 und Schritt 6 lagen **7 h 11 min**. Das
Artefaktset zählt 25 Stress-Test-Befunde (S-1…S-26), 44 offene Punkte (O-01…O-44)
und 17 Zuordnungen (A-1…A-17).

**Regel.** Jede Vertragsrevision invalidiert Schema, Code und Tests gleichzeitig. Gate 1
ist damit die billigste Versicherung der ganzen Pipeline — und war der
zeitintensivste Punkt des Laufs. Der Vertrag gehört **vor** die Implementierung
stabil, nicht danach.

### L10 — Die Zeit steckt in Wartezeit, nicht in Rechenzeit

**Messung.** Drei Sessions mit 12–15 h Wall-Clock bei 440–520 Output-Token/min
(≈ 8,7 Token/s). Das ist kein Compute, das ist Warten auf unbeantwortete
Permission-Prompts oder eine idle Session.

**Regel.** Laufzeit getrennt nach Warte- und Rechenzeit auswerten. Eine Session mit
viel Wall-Clock und wenig Output ist **kein** Rechenproblem, sondern ein
Warteproblem — meist ein nicht beantworteter Prompt.

### L11 — Ein „interrupted" ohne Retry-Pfad ist eine Sackgasse

**Messung.** 8 der 12 längeren Sessions enden mit `interrupted`.

**Regel.** Jede Eskalation braucht einen Retry-Pfad **mit Budget**
(`REQUIREMENT_GAP` → Schritt 1 neu, maximal N Versuche, danach als offener Punkt
weiter). Ohne Retry-Budget stirbt die Pipeline beim ersten Blocker.

### L12 — Jede Datei an zwei Orten braucht vier Dinge

**Beobachtet.**

| Doppelablage | Status |
|---|---|
| `.agent/knowledge/openapi.yaml` (2-KB-Template) ↔ `docs/api/openapi.yaml` (199-KB-Vertrag) | **echter Fehler** — drei Workflows lasen das Template |
| `.agent/knowledge/stack.md` (Template) ↔ ADR-001 (Entscheidung) | **echter Fehler** — das Template nannte FastAPI, entschieden war Spring Boot |
| `.agent/knowledge/schema.sql` ↔ `docs/architecture/schema.sql` | **bewusst** (ADR-019 §6, O-13/S-26), per Mirror-Test erzwungen |

**Regel.** Für jede Doppelablage braucht es: (a) einen benannten Besitzer,
(b) eine Regel „kanonisch vs. Kopie", (c) einen Test oder ein Skript, das die
Gleichheit erzwingt, (d) einen Verweis im Framework. Beim Schema fehlte nur (d) — beim
OpenAPI fehlten (b), (c) und (d) gleichzeitig.

### L13 — Was formal entschieden ist, kann trotzdem falsch sein

**Beobachtet.** F2 (Mandantenmodell) war als entschieden verbucht. Das
Risk-Register führt unter B-01 denselben Scope-Konflikt: „zentrales Register" ↔
„Fachbereiche arbeiten in Abteilungs-Workspaces" — mit der Folge „Datenmodell- und
API-Rework in Phase 2".

**Regel.** Ein Gate prüft nicht nur „steht ein Entscheid drin", sondern „ist der
Ensechid *richtig*". Ein Widerspruch zwischen Entscheidungsliste und Risk-Register
ist ein Gate-Befund, kein Formfehler.

### L14 — Der Testlauf schlug die Theorie — dreimal

| Erwartung | Befund |
|---|---|
| Der Orchestrator braucht eine feste Rückgabe | Das Modell stellte die drei Zeilen ans **Ende**; erst die Zusatzregel „die drei Zeilen zuerst" brachte die Determinismus. |
| Der Architecture-Agent legt den Commit-Fehler nicht offen | Er **meldete** ihn („the first commit attempt failed") und untersuchte die Ursache — deshalb fiel er auf. Ein stillschweigender Umweg hätte den Bug verdeckt. |
| Der erste Orchestrator-Lauf scheitert am Subagent-Start | Er **blockierte korrekt**, statt mit einem Generalisten zu arbeiten. Genau das war das gewünschte Verhalten — Folder-Ownership wurde nicht umgangen. |

**Regel.** Agenten, die einen Blocker melden statt ihn zu umgehen, sind das
wichtigste Feature des ganzen Systems. Jede Designentscheidung, die einen Blocker
*technisch* verhindert (Rechte, Pfade, Formate), ist mehr wert als jede prosebasierte
Regel.

## 3. Was noch offen ist

| # | Offen | Risiko |
|---|---|---|
| 1 | Input-Token pro Schritt (L8) | Der Kontext wächst weiter; die 21:1-Leselast ist die Wurzel |
| 2 | 44 offene Punkte aus Schritt 6 | Werden in späteren Schritten zu Rücksprüngen (L9) |
| 3 | 107 uncommittete Dateien aus einem `interrupted`-Lauf | Nächster Commit-Scope kann Fremdes einsammeln |
| 4 | 12 Commits nicht gepusht | Der Remote steht auf Schritt 2 |
| 5 | `git reset -q` unstaged fremde Arbeit | Löscht sie nicht, aber sie ändert den Staging-Zustand des Menschen |

## 4. Die Regeln, die daraus folgen

1. **Ein Workflow-Name ist eine Zusage.** Er braucht Schreibrecht, Folder-Ownership-Zeile
   und SSoT-Eintrag — sonst schreibt der Agent woanders hin.
2. **Sperren brauchen eine sichtbare Begründung am Zielort.** Sonst werden sie
   umgangen, und der Umweg wird zur Dokumentation.
3. **Kein Shell-Workflow auf geparstem Output.** Bei aktivem Rewrite-Plugin: Scope über
   den Index setzen, nicht über eine Pfadspezifikation.
4. **Globale Regeln nicht ins Frontmatter wiederholen.** Agent-Regeln gewinnen
   (`last match wins`).
5. **`mode: all` für jeden Agenten, den ein Orchestrator starten soll.** Und
   `experimental.subagent_depth` explizit setzen — mit erlaubter Alternative.
6. **Die Rückgabe ist ein Zeiger.** Drei Routing-Fakten, sonst nichts. Der nächste
   Agent liest die Dateien selbst.
7. **Der Agent entscheidet, wann er fertig ist; das Framework prüft es.**
   `steps` ist das Sicherheitsnetz, nicht das Kriterium.
8. **Jede Eskalation hat ein Retry-Budget.** Sonst stirbt die Pipeline beim ersten
   Blocker.
9. **Der Vertrag gehört vor die Implementierung.** Gate 1 ist die billigste Versicherung
   der Pipeline.
10. **Laufzeit getrennt nach Warten und Rechnen auswerten.** Viel Wall-Clock bei wenig
    Output ist ein Prompt-Problem, kein Modellproblem.
11. **Jede Doppelablage braucht Besitzer, Regel, Test und Framework-Verweis.**
12. **Blocker melden ist das Kernfeature.** Technisch verhinderte Umwege sind mehr wert
    als jede prosebasierte Regel.

## 5. Reproduktion

```bash
# Laufzeit je Session, inkl. Output-Rate
python3 - <<'PY'
import sqlite3, os, time
db = os.path.expanduser("~/.local/share/opencode/opencode.db")
c = sqlite3.connect(f"file:{db}?mode=ro", uri=True); c.row_factory = sqlite3.Row
for r in c.execute("SELECT agent, title, time_created, time_updated, tokens_output "
                   "FROM session_v2 WHERE directory LIKE '%AISDLC%' ORDER BY time_created"):
    d = dict(r); dur = round((d['time_updated']-d['time_created'])/60000) or 1
    print(f"{d['agent']:22s} {dur:5d} min  {round((d['tokens_output'] or 0)/dur):5d} T/min  {d['title'][:40]}")
PY

# Payload-Groesse einer Subagent-Session
opencode api session.message.list --param sessionID=<id> \
  | python3 -c "import sys,json; m=json.load(sys.stdin)['data']; \
      t=[x for x in m if x.get('type')=='assistant'][-1]; \
      print(len(''.join(p.get('text','') for p in t.get('content',[]))))"

# Fehler im Log
grep -a "level=ERROR" ~/.local/share/opencode/log/opencode.log | sed 's/.*message="//' | cut -c1-90 | sort | uniq -c | sort -rn
```

Für den nächsten Lauf sind zu erwarten: **Input-Token pro Schritt** (L8) und die
**44 offenen Punkte** (L9) — beides entscheidet, ob Schritt 7 wieder zurückspringt.


---

# Teil 2 — Die Auswertung der Logfiles (21.10.2026)

## Werkzeugbilanz

1.715 Werkzeugaufrufe in den Pipeline-Sessions:

| Werkzeug | Aufrufe | Anteil |
|---|---|---|
| `shell` | 1.093 | 63,8 % |
| `read` | 202 | 11,8 % |
| `write` / `edit` | 149 / 136 | 16,6 % |
| `bash` (klassisch) | 51 | 3,0 % |
| `glob` / `grep` | 19 / 11 | 1,7 % |
| `subagent` | 21 | 1,2 % |

Die 1.094 `shell`-Aufrufe nach Zweck:

| Zweck | Aufrufe | Anteil |
|---|---|---|
| navigieren/ausgeben (`cd`, `echo`, `pwd`) | 591 | 54,0 % |
| **Python-Skript** | **426** | **38,9 %** |
| Datei lesen (`cat`/`wc`/`diff`) | 55 | 5,0 % |
| suchen/auflisten | 13 | 1,2 % |
| git / Build / Test | 4 / 1 | 0,5 % |

## Warum Python-Skripte statt Dateiwerkzeuge?

**Die Ursache ist ein Werkzeugdefizit, kein Modellfehler.** `read` zeigt eine Datei,
`grep` findet — aber „welche Anforderungs-IDs fehlen in den Stories?" über 70 Dateien
ist ein **Mengenvergleich**. Dafür gibt es kein Werkzeug, also schließt der Agent die
Lücke mit einem Skript: 1 Modell-Turn statt 70. Ökologisch richtig.

Die 426 Skripte sind genau zwei Muster:

| Muster | Zweck | Bewertung |
|---|---|---|
| `python3 …/generate_artifacts.py` | Generator: Stories, Features, Traceability-Matrix aus dem PRD | korrekt — aber als **Inline-Wiederverwendung** statt als versioniertes Werkzeug |
| `python3 - <<'PY' …` | Konsistenzchecks: ID-Abgleich PRD ↔ Stories ↔ Features ↔ Matrix | korrekt — aber **jedes Mal neu erfunden** statt als Skill |

**Per Prompt vermeidbar?** Differenziert:

- Die **Rechen**-Skripte: nein. Sie sind richtig und effizient.
- Der **Generator**: ja, durch einen Ort und eine Konvention — er lag in
  `docs/requirements/tools/`, also mitten in der Spezifikation, die ein Mensch liest.
  Jetzt in `.agent/tools/`.
- Der **Konsistenzcheck**: ja, als Skill. Jetzt `aisdlc-traceability-check` mit dem
  Werkzeug `.agent/tools/traceability_check.py`.
- Die **591 Ausgabe-Kommandos**: ja, per Prompt. Sie kosten je einen Modell-Turn und
  einen Permission-Check und liefern kein Ergebnis.

**Nebenbefund, der erst der Check sichtbar machte:** die Traceability-Matrix führt
**0 von 89 Anforderungen** mit einem Test (`requirements_with_test: 0`), obwohl 41
Testdateien und 35 Gherkin-Features existieren. Die Kette Anforderung → Test → Code
war nie belegt — nur behauptet. 178 Lücken, jetzt maschinell prüfbar.

## Warum hängt der Subagent immer noch?

**Der Befund: drei unabhängige Sessions stehen gleichzeitig ~6 h still.**

| Lücke | Session | Fenster | Danach |
|---|---|---|---|
| 436 min | architecture | 21.10 00:42 → 07:59 | `shell(completed)` |
| 345 min | execution | 21.10 00:34 → 06:19 | `shell(completed)` |
| 333 min | orchestrator | 21.10 00:44 → 06:17 | `question(error)` |

Das kann kein Modellproblem sein — drei Sessions können nicht gleichzeitig am selben
Modell hängen. Es ist die Nacht, und die Nacht gehört dem Menschen.

**Die Ursache stand in der Permission-Tabelle:** 29 gespeicherte Einzel-Freigaben, eine
je Kommando-Präfix (`awk *`, `cat *`, `cp *`, `echo *`, `find *`, `git status *`,
`python3 *`, `rtk *`, `sed *`, `sort *`, …). Jedes Kommando außerhalb dieser Präfixe
öffnete eine Permission-Abfrage — und **eine offene Abfrage friert die Session ein**,
bis der Mensch antwortet. Der erste Commit des Tages war 07:54.

**Die Aufteilung:** 45 % der Laufzeit sind Leerlauf-Lücken > 60 s. Die absolute Zahl
überzählt, weil die Sessions parallel liefen — die Ratio stimmt.

**Der Hebel:** `shell: *` → `ask` als Grundregel ist für einen unbeaufsichtigten Lauf
der Killer. Der Schutz der Pipeline liegt ohnehin nicht in der Shell, sondern in den
**Pfadrechten** (`edit` deny/allow je Agent) — die bleiben unangetastet.

## Warum so viele Runden?

Drei Ebenen, die man trennen muss.

**(a) Modellqualität — ja, aber begrenzt.** Die gesamte Pipeline lief auf
`minimax-m3.1-flash-free`, einem **kostenlosen** Flash-Modell. Belege: 3 Rücksprünge,
44 offene Punkte, 25 Stress-Befunde, 11 Tool-Fehler in der Architecture-Session. Das
Ergebnis ist aber objektiv substantiell — 5.523-Zeilen-Vertrag, 1.414-Zeilen-Schema,
20 ADRs, 168 Java-Dateien. „Billiges Modell produziert Müll" trifft nicht zu; „billiges
Modell produziert mehr Nacharbeit" schon.

**(b) Der Vertrag wurde zu spät eingefroren — strukturell.** Der Stresstest (Schritt 4)
fand 25 Befunde, davon **13 am Vertrag** (S-5, S-6, S-7, S-12, S-13, S-17, S-18 …) —
und der Vertrag war nach Schritt 2 eingefroren. Jede Revision invalidiert Schema, Code
und Tests. Das ist nicht modellbedingt, das ist die Reihenfolge.

**(c) Das Gate ist eine Menschenrunde.** Am Gate liegen 25 Befunde, 44 offene Punkte
und 3 Vertragsrevisionen. Das sind menschliche Runden, keine Modell-Runden.

**Die Rundenzahl:** ein Rücksprung = 1 Modellrunde (Vertrag fixen) + 1 Menschenrunde
(Gate) + 1 Modellrunde (Code nachziehen). Drei Rücksprünge → 9 Extra-Runden. Dazu die
Leerlaufrunden aus der Permission-Matrix.

---

# Teil 3 — Die sieben Verbesserungen

| P | Maßnahme | Befund | Nutzen | Status |
|---|---|---|---|---|
| **P1** | `shell: *` → `allow`, Ausnahmen `ask`/`deny` | 45 % Leerlauf durch eingefrorene Sessions | beendet das Hängen | **umgesetzt** |
| **P2** | Modell je Schritt wählbar | alles lief auf einem kostenlosen Flash-Modell | weniger Rücksprünge | **dokumentiert** (Präzedenz: Aufruf > Agent > Session) |
| **P3** | Konsistenzcheck als Skill | 426 Inline-Skripte, 178 unentdeckte Lücken | versioniert, wiederverwendbar | **umgesetzt** |
| **P4** | Werkzeug-Konvention | Generator lag in `docs/requirements/tools/` | gehört nicht in die Spezifikation | **umgesetzt** |
| **P5** | Keine Ausgabe-Kommandos | 591 von 1.094 Shell-Aufrufen (54 %) | ~590 Turns über 9 Schritte | **umgesetzt** |
| **P6** | Vertrag einfrieren nach Schritt 4 | 13 Vertragsdefekte nach der Einfrierung | nimmt die Rücksprung-Schleife die Ursache | **umgesetzt** |
| **P7** | Steckbrief zwischen den Schritten | Input wuchs 80× über vier Schritte | macht den Einstiegspunkt explizit | **umgesetzt** |

## Was P1 bis P7 konkret geändert haben

| Datei | Änderung |
|---|---|
| `.opencode/opencode.jsonc` | `shell: *` → `allow`; Ausnahmen: `git push`, `git reset --hard`, `git clean`, `rm -rf` → `ask`; `sudo`, `chmod 777` → `deny` |
| `AGENTS.md` | Regeln 11 (keine Ausgabe-Kommandos) und 12 (Briefing zuerst); Einfrierpunkt nach Schritt 4; Abschnitt „Briefing" |
| `.agent/PHASES.md` | Gate-Definition mit Einfrierpunkt; Schritt 5 mit `aisdlc-traceability-check`; Abschnitte „Steckbrief" und „Werkzeuge" |
| 9 Workflows | `## Regeln` mit Ausgabe-Verbot und Vertrags-Einfrierung (ab Schritt 5); Briefing in den Prerequisites |
| 10 Prompts | Steckbrief in den Eingaben; `## REGELN FUER DIESEN SCHRITT` |
| `.opencode/agents/aisdlc-orchestrator.md` | Abschnitt „3b — Steckbrief schreiben"; Ausgabe-Verbot |
| 4 Rollen-Agenten | „Read first" beginnt mit dem Steckbrief |
| `.opencode/skills/aisdlc-openapi-first/SKILL.md` | Einfrierpunkt nach Schritt 4; Abschnitt „Abgleich mit der Traceability" |
| `.opencode/skills/aisdlc-traceability-check/SKILL.md` | **neu** — der Konsistenzcheck als Verfahrens-Schritt |
| `.agent/tools/traceability_check.py` | **neu** — prüft Anforderung → Story → Feature → Test → Code |
| `.agent/tools/generate_requirements.py` | **verschoben** aus `docs/requirements/tools/` |
| `.agent/tools/README.md` | **neu** — die Werkzeug-Konvention |
| `.agent/artifacts/briefing.md` | **neu** — der Steckbrief als lebendes Beispiel |
| `how-to.md` | Abschnitt „Modell wählen" (P2) |

## Was P1–P7 nicht lösen

- **Die Input-Token pro Schritt** bleiben hoch. Der Steckbrief (P7) setzt den
  Einstiegspunkt, aber ein Agent, der 70 Dateien braucht, liest sie weiterhin.
- **Die 44 offenen Punkte** aus Schritt 6 sind unverändert und werden in Schritt 7
  entweder geschlossen oder zu Rücksprüngen.
- **Die Qualität des Modells** bleibt, solange P2 nicht umgesetzt wird — der
  dokumentierte Weg dorthin ist jetzt klar.
