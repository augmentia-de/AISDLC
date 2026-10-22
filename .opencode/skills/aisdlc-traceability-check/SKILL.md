---
name: aisdlc-traceability-check
description: Prueft die Traceability-Kette Anforderung -> Story -> Feature -> Test -> Code und meldet jede Luecke als Liste; --fill schreibt den Beleg aus dem Dateisystem. Sagt, WANN geprueft wird (vor jedem Gate) und WIE die Zuordnung mechanisch belegt wird. Nutze diesen Skill in Phase 5 nach den Tests, in Phase 6 nach der Implementierung und vor jedem Gate.
metadata:
  origin: Werkzeug .agent/tools/traceability_check.py; abgeleitet aus den Inline-Konsistenzpruefungen des Requirements-Laufs
---

## Wann

- **Phase 5**, nach dem Aufsetzen der Tests — dort, wo die Kette erstmals prüfbar ist
- **Phase 6**, nach der Implementierung — der Code existiert jetzt
- **Vor GATE 2** — verbindlich, nicht als Hinweis

## Zwei Haelften — nicht verwechseln

| Haelfte | Inhalt | Entsteht in | Wo |
|---|---|---|---|
| **statisch** | Anforderung → Story → Feature | Schritt 1 | `docs/requirements/traceability_matrix.json` |
| **dynamisch** | Anforderung → Test → Code | Schritt 5/6 | `.agent/artifacts/traceability.md` |

Die **Matrix** trägt nur die statische Hälfte. Sie wird **einmal** in Schritt 1
geschrieben und danach **nicht** mehr angefasst. Das ist der Punkt: `tests` und
`code` sind Werte, die erst Schritt 5/6 kennt — ein Feld, das ein früherer Schritt
nicht füllen kann, erzeugt entweder eine Lücke oder einen erfundenen Pfad.

**Warum das so ist:** Im Referenzlauf war die Matrix über den gesamten Lauf ein
Gerüst — 0 von 89 Anforderungen mit Test-Referenz. Gefüllt wurde sie erst nach
Schritt 7, mit zwei zusätzlichen Commits in Schritt 1. Der Check, der das
aufgedeckt hätte, lief nie, weil er erst nach dem Lauf existierte.

## Der Beleg

```bash
python3 .agent/tools/traceability_check.py            # pruefen, nur melden
python3 .agent/tools/traceability_check.py --fill     # pruefen und Beleg schreiben
```

`--fill` schreibt `.agent/artifacts/traceability.md` (der Beleg) und
`.agent/artifacts/traceability-fill.md` (was belegt wurde, was nicht und warum).

**Die Grundregel des Werkzeugs:** Es schreibt **nie** einen Pfad, den es nicht belegen
kann. Ein erfundener Pfad ist schlimmer als eine Lücke — er sieht belegt aus und
ist es nicht.

## Wie die Zuordnung belegt wird

**Test → Anforderung** über die Akzeptanzkriterien-Kennung `AC-<story>-<nr>`:

| Wo | Form |
|---|---|
| Story-JSON | `"id": "AC-003-1"` |
| Feature-Szenario | `Scenario: Rotation  # AC-003-1` |
| Testfall | Methodenname `ac003_1_rotationVerwendetNurS2` oder ein Kommentar `// AC-003-1` |

Die Kennung steht in **allen drei** Orten. Deshalb ist die Zuordnung mechanisch: das
Werkzeug liest die Kennung aus dem Test, findet die Story über die Story-JSON und
deren Anforderungs-IDs. Nichts wird geraten.

**Code → Anforderung**: Ein Test, der eine Produktionsklasse **nennt**, belegt diese
Klasse — sofern sie im Projekt existiert. Mehr ist nicht belegbar und wird nicht
eingetragen.

## Was geprüft wird

| # | Prüfung | Schwere | Bedeutung |
|---|---|---|---|
| 1 | Anforderung in der PRD ohne Story | Lücke | außer bewusst ohne Story geführt (post-MVP) |
| 2 | Story verweist auf unbekannte Anforderung | Lücke | die Story hängt an nichts |
| 3 | Story ohne Feature-Datei | Lücke | |
| 4 | Story ohne Anforderungs-Bezug | Lücke | |
| 5 | Akzeptanzkriterium ohne Matrix-Eintrag | Lücke | Kriterien ohne Matrix sind unsichtbar |
| 6 | Test mit unbekannter AC-Kennung | Lücke | der Test hängt an keiner Anforderung |
| 7 | Anforderung mit Story ohne belegten Test | Lücke | **die Kernbedingung** |
| 8 | Anforderung mit Story ohne belegte Code-Referenz | Lücke | |
| 9 | Produktionsklasse ohne Testreferenz | Hinweis | bei Dependency-Injection normal |
| 10 | Testdatei ohne AC-Kennung | Hinweis | |

**Lücke** blockiert das Gate. **Hinweis** meldet, blockiert nicht. Der Exit-Code
ist 1 bei Lücken, 0 bei reinen Hinweisen.

## Ergebnis protokollieren

Die Lückenliste wandert nach `.agent/artifacts/traceability.md` (menschlich lesbar)
und als `gaps` in die Traceability-Matrix. **Kein Prozentwert.** Eine Matrix, die zu
87 % vollständig ist, ist weniger nützlich als die Liste der 13 fehlenden Kriterien.

Jede Lücke bekommt eine Entscheidung, nicht nur einen Befund:

| Art der Lücke | Entscheidung |
|---|---|
| Anforderung ohne Story | Story ergänzen **oder** Anforderung als post-MVP markieren |
| Anforderung ohne belegten Test | Test ergänzen **oder** Coverage als `manual` begründen |
| Akzeptanzkriterium ohne Eintrag | Eintrag nachtragen — Kriterien ohne Matrix sind unsichtbar |
| Test ohne AC-Kennung | AC-Kennung ergänzen (Methode oder Kommentar) — sonst ist der Test unbelegt |

## Wer füllt

- **Schritt 1** schreibt die statische Matrix — der Requirements-Agent, einmalig.
- **Schritt 5/6** erzeugen Tests und Code. Danach lässt der **Requirements-Agent**
  das Werkzeug laufen (`--fill`) und committet den Beleg. Das ist ein **eigener
  Auftrag**, kein Rücksprung: der Agent, der `docs/requirements/` besitzt, ist derselbe
  wie in Schritt 1.

**Warum so und nicht anders:** Der Execution-Agent darf `docs/requirements/` nicht
schreiben. Ihm das Recht für genau eine Datei zu geben, würde die Folder-Ownership
aufweichen. Der Auftrag an den Requirements-Agenten kostet einen Agentenwechsel und
hält die Trennung sauber.

**Grundsatz:** Ein Nachzug ist **ein** Commit mit dem Subject des korrigierten
Schritts plus `(Nachzug)`. Höchstens einer pro Schritt — ein zweiter bedeutet, dass
die Ursache im Ablauf liegt.

## Regeln

- **Prüfen immer nach** der Matrix-Aktualisierung, nie davor — sonst misst man den
  alten Stand.
- Der Check ist stack-unabhängig: er liest JSON, Markdown, Gherkin und Quelltext.
- **Nie** Lücken löschen, indem man Matrix-Einträge entfernt. Das verschwindet nur.
- Der Beleg ist der **Ende**-Stand, nicht der Wunschstand. Was nicht existiert,
  steht als Lücke drin.
