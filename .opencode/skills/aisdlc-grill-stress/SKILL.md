---
name: aisdlc-grill-stress
description: Stresstest fuer Konzept und Architektur vor der Implementierung - Designbaum abfragen, Deepening-Opportunitaeten finden, Standards und Spezifikation als zwei getrennte Achsen pruefen. Nutze diesen Skill in Phase 4 (Grill-Me-Stresstest) nach der Architekturentscheidung.
metadata:
  origin: adaptiert aus mattpocock/grilling + mattpocock/improve-codebase-architecture + mattpocock/codebase-design + wshobson/architecture-patterns
---

## Wann

Nach Phase 2 (Architektur), **vor** der Implementierung. Ziel: Architekturfehler finden,
solange sie noch billig zu korrigieren sind.

## 1. Designbaum abfragen

Die Absicht ist die Wurzel. Jede Entscheidung haengt an den Entscheidungen darunter.

**Frontier** = alle Entscheidungen, deren Voraussetzungen bereits geklaert sind - die Fragen,
die du **jetzt** stellen kannst, ohne Antworten zu erfinden. Frage die ganze Frontier in
einer Runde, nummeriere die Fragen und gib jeweils deine Empfehlung. Warte dann auf die
Antworten, bevor die naechste Runde beginnt.

```text
❓ **F1** - <Fragetitel>: <Frage, ggf. mit Antwortoptionen>

➡️ <deine Empfehlung>

---

❓ **F2** - <Fragetitel>: <Frage>

➡️ <deine Empfehlung>
```

Jede Frage so formulieren, dass "ja" deine Empfehlung annimmt. Eine Frage, deren Antwort von
einer noch offenen Frage dieser Runde abhaengt, gehoert in eine **spaetere** Runde.

**Fakten sind deine Aufgabe, nicht die des Nutzers.** Braucht eine Frontier-Frage eine
Umgebungs-Faktum (Dateisystem, Tooling, bestehender Code), dann ermittle es selbst oder
delegiere es an einen Subagenten - frage den Nutzer nicht danach.

Die Sitzung ist fertig, wenn die Frontier leer ist: jeder Ast besucht, nichts stillschweigend
angenommen. Handle erst, wenn der Nutzer das gemeinsame Verstaendnis bestaetigt.

## 2. Deepening-Opportunitaeten finden

Ziel: flache Module in tiefe ueberfuehren - testbarer und KI-navigierbarer.

**Deletion-Test:** Wuerde das Loeschen des Moduls die Komplexitaet buendeln oder nur verschieben?
"Ja, es buendelt" ist das gesuchte Signal.

**Vokabular** (aus `codebase-design`): *Modul*, *Interface*, *Tiefe*, *Seam*, *Adapter*,
*Leverage*, *Lokalitaet*. Diese Begriffe exakt verwenden, nicht in "Komponente",
"Service", "API" oder "Boundary" abdriften.

**Zwei Achsen getrennt pruefen** (Standards und Spezifikation duerfen sich nicht gegenseitig maskieren):

| Achse | Quelle | Befund |
|---|---|---|
| Standards | Repo-Konventionen, Linter, Struktur, Naming, Testabdeckung | |
| Spezifikation | `docs/api/openapi.yaml`, `docs/requirements/features/*.feature`, ADRs | |

**Quellen zuerst lesen:** `GLOSSARY.md` (Domänenbegriffe), `docs/adr/` (Entscheidungen, die
nicht neu diskutiert werden), `docs/architecture/` (aktuelles Design).

**Friction-Signale, auf die du achtest:**
- Ein Konzept zu verstehen erfordert Sprünge zwischen vielen kleinen Modulen
- Module sind **flach**: das Interface ist fast so komplex wie die Implementierung
- Reine Funktionen wurden nur fuer Testbarkeit extrahiert, die Bugs liegen aber im Zusammenspiel
- Enge Kopplung ueberlebt die Seam
- Ungetestete oder schwer testbare Teile

**ADR-Konflikte:** Widerspricht ein Vorschlag einem bestehenden ADR, nur dann vorschlagen,
wenn die Friction real ist. Im Befund deutlich markieren.

## 3. Ergebnis

- Befunde getrennt nach Standards-Achse und Spezifikation-Achse
- Je Befund: Datei:Zeile, Problem, Auswirkung, Loesung, Empfehlungsstaerke
- Offene Entscheidungen, die der Mensch treffen muss
- Keine Implementierung, solange das gemeinsame Verstaendnis nicht bestaetigt ist
