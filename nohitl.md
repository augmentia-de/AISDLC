# Betriebsmodus ohne HITL

> **Status: Entwurf.** Die Umsetzung ist offen. Punkt (a) — `.agent/knowledge/`
> als Schreibziel des Architecture Orchestrators — ist bereits umgesetzt
> (`38d16f1`). Alles Weitere in diesem Dokument ist beschrieben, aber noch
> nicht gebaut.

## Zweck

Ein unbeaufsichtigter Probelauf der gesamten Pipeline: 9 Schritte, 9 Commits,
keine Rückfragen, Abbruch nur bei einem echten Fehler. Zweck ist nicht
Produktivbetrieb — es ist der Nachweis, dass die Pipeline sich selbst
durchziehen kann, ohne am Menschen zu hängen.

Der Normalbetrieb bleibt HITL. Ohne HITL fällt die Freigabeentscheidung weg —
das ist ein Verlust an Kontrolle, kein Gewinn an Tempo. Deshalb ist der Modus
per Schalter abwesbar, nicht per Umbau.

## Zwei unabhängige Schalter

Berechtigungs-Nachfragen und Gates sind zwei verschiedene Dinge und werden
getrennt geschaltet.

| Was | Wie | Dateiänderung |
|---|---|---|
| Berechtigungs-Nachfragen (Shell, Web, …) | `--auto` beim Start | keine |
| Gates / Human-in-the-Loop | `"hitl": false` in `.agent/runtime.json` | eine Datei |

### `--auto`

Genehmigt alle Berechtigungsanfragen, die nicht ausdrücklich auf `deny`
stehen. **Die `deny`-Regeln bleiben in Kraft** — das Folder-Ownership bleibt
also technisch erzwungen, auch im unbeaufsichtigten Lauf.

### `.agent/runtime.json` (neu)

```json
{
  "hitl": true,
  "max_step_retries": 2,
  "continue_on_open_points": true
}
```

`hitl` steuert die Gates. `max_step_retries` begrenzt, wie oft ein Schritt bei
einem Blocker neu gestartet wird, bevor er als „mit offenen Punkten
abgeschlossen" durchgewertet wird. `continue_on_open_points` legt fest, ob die
Pipeline bei offenen Punkten weiterläuft oder abbricht.

**Warum eine eigene Datei und kein Key in `opencode.jsonc`:** das
Config-Schema hat `additionalProperties: false`. Ein eigener Top-Level-Key
(`"aisdlc": { … }`) bricht die Schema-Validierung. Eine Datei im
Framework-Bereich ist außerdem ohne Neustart umschaltbar.

## Was sich ändert

Sieben Stellen. Die Schalterlogik kommt **einmal** in `AGENTS.md` (das liest
jeder Agent zuerst), die übrigen Stellen bekommen je einen bedingten Hinweis,
damit keine zwei Dokumente dasselbe erzählen.

| # | Datei | Änderung |
|---|---|---|
| 1 | `.agent/runtime.json` | **neu** — der Schalter |
| 2 | `AGENTS.md` | Abschnitt „Runtime-Schalter"; HARD GATES, Regel 5 (Löschen) und die Agent-Wechsel-Regel werden bedingt |
| 3 | `.agent/PHASES.md` | Gate-Tabelle: Spalte „Bei `hitl:false`" + Abschnitt zum Verhalten ohne Menschen |
| 4 | `.agent/workflows/01`–`09` | je 1 Zeile in den 9 `## Gate`/`## HARD GATE`-Sektionen |
| 5 | `.agent/prompts/` (10 Dateien) | 28 Treffer, davon 4 echte HARD-GATE-Formulierungen → bedingt |
| 6 | `.opencode/agents/aisdlc-orchestrator.md` | Gate-Abschnitt bedingt + Eskalations-Politik + `steps: 300` |
| 7 | `.opencode/agents/aisdlc-requirements.md`, `aisdlc-qa.md` | bei `hitl:false` melden statt stoppen |

### Verhalten bei `hitl: false`

- **Kein Halt.** Das Gate wird nicht abgewartet.
- **Kein `question`-Werkzeug.** Der Orchestrator fragt nicht — er prüft die
  Gate-Bedingung selbst, schreibt das Ergebnis in die Zusammenfassung und
  fährt mit Schritt *n+1* fort.
- **Die Prüfung wird protokolliert.** Das Ergebnis landet in
  `.agent/artifacts/` und im Commit-Body des Schritts, damit der Lauf
  nachvollziebar bleibt, auch ohne Freigabe.
- **Der Agent-Wechsel passiert automatisch.** Der Orchestrator startet den
  Rollen-Agenten des nächsten Schritts als Subagenten — das ist der
  vorgesehene Weg, kein Workaround.

## Eskalation ohne HITL — der Teil, der sonst hängen bleibt

Ohne Menschen muss ein Blocker einen anderen Ausweg bekommen, sonst steht die
Pipeline. Vorgesehene Regel:

| Signal | Reaktion | Grenze |
|---|---|---|
| `REQUIREMENT_GAP` | Schritt 1 neu starten, Lücke als Auftrag mitgeben | `max_step_retries` |
| `ARCHITECTURAL_BLOCKER` | Schritt 2 neu starten | `max_step_retries` |
| Subagent ohne Commit | 1× neu starten | 1 |
| Gate-Bedingung nicht erfüllt | Befund protokollieren, weiter | — |

Nach Erschöpfung der Versuche gilt der Schritt als **mit offenen Punkten
abgeschlossen**: der Commit entsteht mit einem `Hinweis: offene Punkte`-Vermerk
im Body, die Pipeline läuft weiter. Der Lauf endet nie an einem Blocker — er
endet am letzten Schritt, mit einer Liste offener Punkte.

## Randbedingungen — empirisch geprüft, nicht vermutet

| Beobachtung | Folge |
|---|---|
| `--auto` genehmigt `shell: …` → `ask` | keine Shell-Nachfragen im Testlauf |
| `--auto` genehmigt **nicht** das `question`-Werkzeug | die Frage wird abgebrochen — **nicht fatal**: der Agent trifft eine Annahme und macht weiter. Deshalb muss der Orchestrator bei `hitl:false` gar nicht erst fragen. |
| `deny`-Regeln bleiben unter `--auto` aktiv | Folder-Ownership bleibt erzwungen |
| `opencode.jsonc` hat `additionalProperties: false` | der Schalter muss eine Datei sein |
| Kein Rollen-Agent nutzt `question` | nur der Orchestrator muss angepasst werden |
| `steps` hat keinen Default | 300 als Versicherung ist trotzdem sinnvoll |
| Das globale `rtk`-Plugin rewritet Kommandos vor der Permission-Prüfung | Shell-Muster müssen tolerant sein (`*git log*`, nicht `git log *`) |
| `git commit -- <pfade>` bricht bei leeren oder fehlenden Pfaden ab | der Commit-Scope nutzt `--pathspec-from-file` (siehe `.agent/PHASES.md`) |

## Bereits erledigt

**`.agent/knowledge/` als Schreibziel** (`38d16f1`). Die Folder-Ownership wies
den Bereich dem Architecture Orchestrator zu, die Agent-Frontmatter nicht. Der
Agent umging die Sperre und legte das DB-Schema nach
`docs/architecture/schema.sql` (29,7 KB), während
`.agent/knowledge/schema.sql` das generische 2,6-KB-Template enthielt — das
Drift-Muster, das bei den Artefakten schon einmal zugeschlagen hat. Jetzt
stimmen Rechte-Matrix und Folder-Ownership überein.

Das Schema wird an seinen kanonischen Ort verschoben, sobald die laufende
Architecture-Session ihre Arbeit beendet hat.

## Testlauf

Nicht im echten Projekt: dort läuft eine Session, und der Test würde mitten
im Schritt 2 landen. Stattdessen eine Kopie.

```bash
git clone /home/torsten/dev/my-projects/AISDLC /tmp/opencode/aisdlc-autotest
cd /tmp/opencode/aisdlc-autotest
# .agent/runtime.json: "hitl": false
opencode run --standalone --auto --agent aisdlc-orchestrator \
  --model opencode-go/<modell> \
  "Starte die AI-SDLC-Pipeline vollstaendig von Schritt 0 bis Schritt 9. Keine Rueckfragen."
```

**Erwartung:** 9 Schritt-Commits mit dem Subject `<type>(sdlc-<n>): …`, keine
Nachfrage, Abbruch ausschließlich bei einem echten Fehler.

**Prüfungen danach:**

```bash
git log --oneline --grep="sdlc-"   # 9 Commits, lückenlos 1..9
git status --short                # leer
git diff HEAD~9 --stat            # nur Artefakte, kein Fremdzeug
```

## Rückkehr zu HITL

`"hitl": true` in `.agent/runtime.json` — dann gilt exakt das heutige
Verhalten: Gates halten, `question`-Werkzeug, der Mensch schaltet den Agenten
von Hand. `--auto` weglassen, und die Berechtigungs-Nachfragen kommen zurück.

Der Modus hinterlässt keine Spuren im Framework: die Gates bleiben in den
Dateien stehen, nur ihr Auslöser wird umgeschaltet.
