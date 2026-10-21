# improve1 — Traceability sauber schließen statt nachträglich nachziehen

> **Status: Entwurf.** Hier steht, **was zu tun ist** — noch nicht umgesetzt.
> Grundlage: die Analyse des PromptGuard-Laufs in AISDLC (20.–21.10.2026), genauer
> die Commits `835900d`, `60d891c`, `ea9c4fc`.
> Bezug: AISDLC2 (dieses Projekt).

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

## Anhang: Belegstellen im PromptGuard-Lauf

| Beleg | Fundstelle |
|---|---|
| Auslöser des Rücksprungs | `.agent/artifacts/traceability-fill.md`, Kopf |
| Matrix vor dem Nachzug | Commit `835900d^` — 0/89 mit `tests`, 0/89 mit `code` |
| Matrix nach dem Nachzug | Commit `835900d` — 89/89 mit `tests`, 89/89 mit `code` |
| AC-Tags nachträglich | Commit `ea9c4fc` — 35 Feature-Dateien, je +6 Zeilen |
| Matrix-Recheck | Commit `60d891c` — 61 Zeilen, SEC-009 belegt |
| Schritt 7 als Stand | Commit `c227e1c` |
| Check war ab da | `aisdlc-traceability-check` installiert in `0d1ee9a` (21.10 15:00) |
