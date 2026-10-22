# Werkzeuge — wo liegen sie?

Das Projekt hat drei Werkzeug-Arten. Sie gehören an **drei** Stellen, und jede Stelle
hat eine klare Regel. Das verhindert das Werkzeug-Sammelsurium, das sonst entsteht,
wenn ein Agent bei jeder Gelegenheit ein neues Skript danebenstellt.

| Art | Ort | Regel |
|---|---|---|
| **Phasen-Werkzeug** | `.agent/tools/` | Hilft einem *Schritt* des AI-SDLC, gehört nicht zur Anwendung. Wird vom Framework-Agenten geladen. |
| **Code-Generator** | `src/<modul>/tools/` | Erzeugt Code aus einer Quelle (Vertrag, Schema). Gehört neben den Code, den er erzeugt. |
| **Projekt-Spezifisches** | `src/<modul>/tools/` | Wie Code-Generator. |

## Was aktuell dort liegt

| Datei | Art | Quelle |
|---|---|---|
| `.agent/tools/traceability_check.py` | Phasen-Werkzeug | prüft die Kette Anforderung → Story → Feature → Test → Code; `--fill` schreibt den Beleg |

**Im Framework liegt nur, was stackunabhängig ist.** Ein Generator, der die Stories
*dieses* Projekts erzeugt, ist projektspezifisch und gehört nicht hierher — er gehört
ins Projekt (siehe `src/<modul>/tools/`) oder wird durch den Skill
`aisdlc-prd-to-stories` ersetzt, der die **Methode** statt des Ergebnisses liefert.

## Regeln

1. **Ein Werkzeug hat genau einen Ort.** Ein Phasen-Werkzeug gehört **nicht** in
   `docs/` — `docs/` ist die Anforderungsspezifikation, die ein Mensch liest. Ein
   Generator, der dort liegt, wird Teil der Spezifikation und von niemandem
   ausgeführt.
2. **Phasen-Werkzeuge sind Framework-Infrastruktur** und liegen unter `.agent/tools/`.
   Sie werden mitversioniert, weil sie den Schritt definieren, nicht die Anwendung.
3. **Code-Generatoren liegen neben ihrem Code.** Ein Generator, der Java-Modelle
   erzeugt, gehört ins Backend — er wird dort zusammen mit dem Build benutzt.
4. **Ein Generator hat genau eine Quelle der Wahrheit.** `generate-contract-roles.py`
   liest `x-required-role` aus dem Vertrag; die Rollen werden nicht an zwei Orten
   gepflegt.
5. **Kein Generator ohne Kommentar im Docstring, der das Warum begründet.** Der
   Docstring beantwortet: aus welcher Quelle, wohin, und warum es diese Quelle
   braucht.
6. **Ein Werkzeug, das Dateien schreibt, prüft sein Ergebnis.** Der Vendor-Lauf
   vergleicht nach dem Kopieren jede Datei der Quelle mit dem Ziel und bricht mit
   Exit 1 ab, wenn etwas fehlt. Ein stilles Teil-Vendoring fällt sonst erst auf,
   wenn ein Skill beim Lesen ins Leere zeigt.

## Aufruf

```bash
# Konsistenz der Traceability-Kette pruefen
python3 .agent/tools/traceability_check.py

# Beleg erzeugen (schreibt .agent/artifacts/traceability.md und -fill.md)
python3 .agent/tools/traceability_check.py --fill
```

**Die Grundregel des Werkzeugs:** Es schreibt **nie** einen Pfad, den es nicht belegen
kann. Ein erfundener Pfad ist schlimmer als eine Lücke — er sieht belegt aus und ist
es nicht.
