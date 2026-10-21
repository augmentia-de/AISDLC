# ADR-016: Kanonische Artefakte — ein Ort pro Wahrheit

## Kontext

Das Framework arbeitet mit Artefakten, die an **mehreren** Stellen liegen
könnten: der API-Vertrag, das Datenbankschema, das Stack-Wissen. Ohne eine
eindeutige Zuordnung entstehen zwei Kopien, die auseinanderlaufen — die
klassische Drift-Quelle.

Der erste Volllauf des Frameworks fand genau das: 13 Vertragsdefekte, alle
**nach** der Einfrierung, und jeder davon hat Schema, Code und Tests
invalidiert. Ein Vertragsdefekt, der nur in einer Kopie behoben wird, ist in
der anderen Kopie weiterhin vorhanden.

Der Vorlauf dieses Projekts hat eine **Spiegel-Regel** eingeführt: Das Schema
ist kanonisch in `.agent/knowledge/schema.sql`, und eine Kopie liegt im
Commit-Scope der Architekturphase. Der Verweis auf ADR-019 §6 in `AGENTS.md`
stammt aus dem Vorlauf und wird hier durch diese ADR abgelöst.

## Entscheidung

### 1. Eine Wahrheit pro Artefakt

| Artefakt | Kanonischer Ort | Weitere Datei | Verhältnis |
|---|---|---|---|
| **API-Vertrag** | `docs/api/openapi.yaml` | `.agent/knowledge/openapi.yaml` | **Verweis**, keine Kopie |
| **Datenbankschema** | `.agent/knowledge/schema.sql` | `docs/architecture/schema.sql` | **byte-identische Kopie**, versioniert |
| **Stack-Wissen** | `.agent/knowledge/stack.md` | — (Quelle: ADR-001) | einzige Fassung |
| **Fachglossar** | `docs/architecture/GLOSSARY.md` | — | einzige Fassung |
| **ADRs** | `docs/adr/ADR-*.md` | `docs/adr/README.md` (Index) · `.agent/artifacts/adr-index.md` (Gate-Vorlage) | Index **einmal**; die Vorlage enthält **keine** Kopie |

> **Korrektur der Dateibeziehung in Schritt 3 (2026-10-21):** Die Zeile „ADRs“
> nannte nur `.agent/artifacts/adr-index.md`. Diese Datei hielt einen **zweiten**
> ADR-Index — und war nach dem Rücksprung **veraltet** (ADR-002 §7 fehlte).
> Sie ist jetzt die **Gate-Vorlage** (bestätigt / offen) und **verweist** auf den
> Index in `docs/adr/README.md`. Die **Entscheidung** — ein Ort pro Wahrheit —
> bleibt unverändert.

### 2. Vertrag: Verweis statt Kopie

`.agent/knowledge/openapi.yaml` enthält **nur** einen Verweis:

- den Pfad `docs/api/openapi.yaml`,
- den Zweck der Datei,
- die Regel „wer den Vertrag ändert, ändert nur `docs/api/openapi.yaml`“.

**Keine** Pfade, **keine** Schemas, **keine** Beispiele in der Verweisdatei.
Eine Kopie wäre eine zweite Wahrheit, die auseinanderlaufen kann — und die
niemand liest.

### 3. Schema: byte-identische Kopie

Warum ausgerechnet das Schema eine Kopie bekommt und der Vertrag nicht?

| Grund | Vertrag | Schema |
|---|---|---|
| Wird von der Migration gelesen | nein (nur von der Anwendung und den Tests) | **ja** — die Migration braucht die Datei im Commit-Scope |
| Wird versioniert und diffbar | ja | **ja** — Änderungen am Schema sollen im Commit sichtbar sein |
| Drift-Risiko | hoch (große Datei) | gering (mechanisch erzeugt) |
| Prüfung | Vertragstest gegen die Datei | **Mirror-Test** `SchemaMirrorIT` (byte-identisch) |

Die Kopie entsteht **nicht** von Hand: Sie wird bei jeder Schema-Änderung in
**demselben** Schritt mitgeschrieben und durch einen Mirror-Test geprüft
(`diff` bzw. Byte-Vergleich). Ein Schritt, der nur eine der beiden Dateien
ändert, ist fehlerhaft.

### 4. Freeze des Vertrags

| Zeitpunkt | Zustand des Vertrags |
|---|---|
| Schritt 2 (dieser) | Entwurf — **stabil und vollständig**, aber noch nicht eingefroren |
| Schritt 3 (Simplify) | unverändert, außer es wird etwas überflüssig gefunden |
| Schritt 4 (Grill-Me-Stresstest) | **Einfrierung** |
| Schritt 5–9 | eingefroren — eine Änderung erfordert einen **Rücksprung zu Schritt 2** |

Der Vertrag wird **nie** im Vorbeigehen geändert. Was sich nicht ausdrücken
lässt, geht zurück nach Schritt 2.

### 5. Stack-Wissen

`.agent/knowledge/stack.md` ist die einzige maschinenlesbare Fassung der
Stack-Entscheidung. Ihre Quelle ist **ADR-001**. Beide werden im selben Schritt
geschrieben, damit sie nicht auseinanderlaufen. Die Datei enthält **keine
Platzhalter** aus der Vorlage — sie ist eine Entscheidung, keine Lücke.

## Konsequenzen

- **Positiv:** Für jedes Artefakt gibt es genau **eine** Stelle, an der es
  geändert wird. Der Drift-Fehler aus dem Vorlauf kann nicht wiederkehren.
- **Positiv:** Der Mirror-Test macht die Schema-Kopie mechanisch geprüft statt
  verträglich.
- **Negativ:** Zwei Stellen für das Schema sind zwei Stellen, die bei einer
  Änderung im selben Schritt aktualisiert werden müssen. Der Mirror-Test ist
  der Preis — ohne ihn wäre die Kopie eine Falle.
- **Negativ:** Die Verweisdatei `.agent/knowledge/openapi.yaml` enthält keine
  Inhalte. Ein Agent, der nur sie liest, muss der Verweis folgen — dafür steht
  der Pfad **oben** in der Datei.
- **Negativ:** Der Vertrag wird erst nach Schritt 4 eingefroren. Zwischen
  Schritt 2 und 4 kann er noch angepasst werden — das ist gewollt, weil der
  Stresstest genau dafür da ist.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Alles kanonisch in `.agent/knowledge/`** | `.agent/knowledge/` ist nicht im Commit-Scope der Architekturphase — Änderungen daran erschienen nicht im Review des Gates. |
| **Vertrag doppelt (Wissen + `docs/api/`)** | Zwei Wahrheiten — genau der Vorlaufsfehler. |
| **Schema nur in `docs/architecture/`** | Die Migration braucht die Datei an einem festen, bekannten Ort, der nicht vom Commit-Scope abhängt. |
| **Schema nur in `.agent/knowledge/`** | Dann erschiene eine Schema-Änderung nicht im Architektur-Commit — der Gate-Reviewer sähe sie nicht. |
| **Schemakopie beim Build erzeugen** | Dann wäre die versionierte Kopie veraltet — der Commit zeigte nicht, was sich geändert hat. |
| **Vertrag sofort nach Schritt 2 einfrieren** | Der erste Volllauf fand 13 Vertragsdefekte im Stresstest — alle nach der Einfrierung. Ein früher Freeze würde jeden davon in Schema, Code und Tests invalidieren. |

## Status

**Angenommen** — bei Gate 1 am 2026-10-21 bestätigt.
