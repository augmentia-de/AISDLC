---
name: aisdlc-traceability-check
description: Prueft die Traceability-Kette Anforderung -> Story -> Feature -> Test -> Code und meldet jede Luecke als Liste. Nutze diesen Skill in Phase 1 nach dem Erzeugen der Stories und Features, in Phase 5 nach dem Aufsetzen der Tests und vor jedem Gate. Die Matrix-Eintraege werden dabei gegen die tatsaechlichen Test- und Code-Dateien abgeglichen - eine gefuellte Matrix allein zaehlt nicht als Beleg.
metadata:
  origin: Werkzeug .agent/tools/traceability_check.py, abgeleitet aus den Inline-Konsistenzpruefungen des Requirements-Laufs
---

## Wann

- **Phase 1**, direkt nach dem Erzeugen von Stories, Features und Traceability-Matrix
- **Phase 5**, nach dem Aufsetzen der Tests — dort, wo die Matrix die Test-Bezüge füllen soll
- **Vor jedem Gate**: die Lückenliste ist die Grundlage der Gate-Entscheidung

## Das Werkzeug

```bash
python3 .agent/tools/traceability_check.py            # Lücken als Liste
python3 .agent/tools/traceability_check.py --json     # maschinenlesbar
```

Exit-Codes: `0` keine Lücke · `1` Lücken gefunden · `2` Eingangsdaten fehlen.

## Was geprüft wird

| # | Prüfung | Bedeutung |
|---|---|---|
| 1 | Anforderung in der PRD ohne Story | Lücke — außer bewusst ohne Story geführt (post-MVP) |
| 2 | Story verweist auf unbekannte Anforderung | Lücke — die Story hängt an nichts |
| 3 | Story ohne Feature-Datei | Lücke |
| 4 | Story ohne Anforderungs-Bezug | Lücke |
| 5 | Akzeptanzkriterium ohne Matrix-Eintrag | Lücke |
| 6 | Matrix-Eintrag ohne Test | Lücke |
| 7 | Matrix-Eintrag ohne Code-Referenz | Lücke |

## Die wichtigste Regel

**Eine gefüllte Matrix ist kein Beleg.** Das Werkzeug prüft die Matrix gegen die
Dateisysteme: `tests/` und `code` in der Matrix müssen auf Dateien zeigen, die es
gibt. Ein Matrix-Eintrag, der auf einen Test verweist, den niemand geschrieben hat,
zählt als Lücke.

Umgekehrt gilt: der Test existiert, aber die Matrix führt ihn nicht → ebenfalls Lücke.
Die Matrix ist die Indexierung, nicht die Wahrheit — die Dateien sind die Wahrheit.

## Ergebnis protokollieren

Die Lückenliste wandert nach `.agent/artifacts/traceability.md` (menschlich lesbar)
und als `gaps` in die Traceability-Matrix. **Kein Prozentwert.** Eine Matrix, die zu
87 % vollständig ist, ist weniger nützlich als die Liste der 13 fehlenden Kriterien.

Jede Lücke bekommt eine Entscheidung, nicht nur einen Befund:

| Art der Lücke | Entscheidung |
|---|---|
| Anforderung ohne Story | Story ergänzen **oder** Anforderung als post-MVP markieren |
| Matrix ohne Test | Test ergänzen **oder** Coverage als `manual` begründen |
| Matrix ohne Code | Code-Referenz nachtragen **oder** `coverage_state` auf `seeded` lassen |
| Akzeptanzkriterium ohne Eintrag | Eintrag nachtragen — Kriterien ohne Matrix sind unsichtbar |

## Regeln

- Ein Lücken-Report ohne zugeordnete Entscheidung ist **kein** Ergebnis. Jede Lücke
  braucht eine Begründung, warum sie bleibt.
- Prüfen immer **nach** der Matrix-Aktualisierung, nie davor — sonst misst man den
  alten Stand.
- Der Check ist stack-unabhängig: er liest JSON, Markdown und Gherkin.
- **Nie** Lücken löschen, indem man die Matrix-Einträge entfernt. Das verschwindet
  nur.
