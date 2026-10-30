# Steckbrief — <Schritt N> (<Schrittname>)

> **Template.** Der Orchestrator schreibt diese Datei am Ende jedes Schritts neu
> und committet sie mit dem Schritt. Der Rollen-Agent des nächsten Schritts liest
> sie **zuerst** und danach nur die Dateien, die hier genannt sind.

| Feld | Wert |
|---|---|
| Stand | Schritt <N> abgeschlossen, Commit <hash> |
| Nächster Schritt | <N+1> (<Schrittname>), Agent <agent-id> |
| Vertrag | `docs/api/openapi.yaml`, Revision <x> — **eingefroren seit Schritt 4** |
| Schema | `.agent/knowledge/schema.sql` (kanonisch) |
| Stack | `.agent/knowledge/stack.md` |

## Was du lesen musst — und sonst nichts

| # | Datei | Warum |
|---|---|---|
| 1 | `AGENTS.md` | Regeln, Gates, Folder Ownership |
| 2 | `.agent/PHASES.md` | Schritt → Instanz → Agent → Skill → Gate |
| 3 | `<workflow-datei>` | dieser Schritt |
| 4 | <weitere Datei> | <warum> |

## Entscheidungen, die gelten

| ID | Entscheidung | Bindet |
|---|---|---|
| <F-x / ADR-x> | <Kurzfassung> | <was> |

## Offene Punkte

| ID | Thema | Owner |
|---|---|---|
| <O-xx> | <kurz> | <wer> |

## Was du in diesem Schritt **nicht** tust

- <z. B. „der Vertrag ist eingefroren" — ein Vertragsdefekt ist ein Befund für
  einen Rücksprung zu Schritt 2, keine Änderung im Vorbeigehen.>

## Werkzeuge

| Zweck | Kommando |
|---|---|
| Konsistenz der Traceability-Kette | `python3 .agent/tools/traceability_check.py` |
| <weitere> | <Kommando> |
