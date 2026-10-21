# ADR-018: Oberfläche — Framework und Barrierefreiheit

## Kontext

NFR-005: *„Die Oberfläche ist ohne Maus bedienbar und mit Screenreader nutzbar;
die Kontraste erfüllen mindestens WCAG 2.1 AA."*

Aus Abschnitt 12 der Anforderung: *„Die Oberfläche zeigt Endpunkte, Zustellprotokoll
und tote Zustellungen und ist mit Tastatur und Screenreader bedienbar."*

AC-021-1: vollständige Tastaturbedienung, sichtbarer Fokus, logische Reihenfolge.
AC-021-2: ein automatischer WCAG-2.1-AA-Scan (axe) über **alle** Seiten meldet
**keine** Verstöße der Schweregradsklassen „kritisch“ oder „schwer“.
AC-021-3: Kontrast ≥ 4,5:1 (Text) bzw. ≥ 3:1 (Bedienelemente).
AC-021-4: Liste, Zeileninhalte und Statusmeldungen werden mit **Rolle und
Inhalt** ausgegeben (Screenreader).

AC-019-1…AC-019-4 und AC-020-1…AC-020-4 definieren das Verhalten der drei
Seiten.

## Entscheidung

### 1. Framework

**Next.js 15 (App Router) auf React 19, TypeScript mit `strict: true`.**

- Die geplanten Pfade der Traceability-Matrix (`src/app/endpoints/page.tsx`,
  `src/app/deliveries/page.tsx`, `src/app/dead-deliveries/page.tsx`) sind
  Next.js-Konventionen — die Entscheidung hält sie gültig.
- Der App Router liefert die Seiten ohne zusätzliche Betriebsstufe aus.
- **Keine** fachliche Logik im Frontend: die Oberfläche ist ein reiner
  API-Konsument. Der Vertrag `docs/api/openapi.yaml` ist die Quelle; die Typen
  in `src/types/api.ts` werden daraus **abgeleitet**, nicht von Hand
  gepflegt.

### 2. Barrierefreiheit ist eine Bauregel, kein Audit

Die vier Anforderungen aus AC-021 werden **strukturell** sichergestellt:

| Anforderung | Bauregel |
|---|---|
| AC-021-1 Tastatur | Alle Bedienelemente sind native `<button>`, `<a>`, `<input>` — **keine** `<div onClick>`. Kein `tabindex` > 0. Die Reihenfolge folgt der DOM-Reihenfolge; es gibt **keine** positive `tabindex`. Fokus ist immer sichtbar (`:focus-visible`). |
| AC-021-2 axe | Der Scan läuft in Schritt 5 über **alle** drei Seiten; die Schwelle ist 0 Verstöße der Klassen „kritisch“/„schwer“. Er ist **Pflicht** in der CI. |
| AC-021-3 Kontrast | Design-Tokens mit festen Werten; ein Kontrastprüfer (z. B. `vitest-axe`/`axe-core`) prüft die Tokens im Build. Text ≥ 4,5:1, Bedienelemente und Fokusrahmen ≥ 3:1. |
| AC-021-4 Screenreader | Semantisches HTML: `<table>` mit `<caption>`, `<th scope="col">`, `<th scope="row">`; Statusmeldungen in einem `role="status"`-Container mit `aria-live="polite"`; Fehler in `role="alert"`, verknüpft per `aria-describedby` mit dem Feld. |

### 3. Status wird **nie** allein über Farbe transportiert

Der Zustand eines Endpunkts oder einer Zustellung ist ein **Text**, nicht nur
eine Farbe. Der `StatusBadge` zeigt Text **und** Farbe **und** ein
unterscheidbares Merkmal (Form/Icon mit Textalternative). Grund: rund 8 % der
Männer haben eine Farbsehschwäche — eine Zustandsanzeige, die nur über Farbe
trägt, ist für sie unlesbar.

Die Palette ist **statusneutral** gewählt: Zustände werden nicht über
Rot/Grün unterschieden, sondern über Farbton + Text + Form. Das ist zugleich
die robuste Lösung für den Kontrast.

### 4. Tabellen

Die drei Seiten zeigen **Daten in Tabellen**. Sie werden als echte Tabellen
umgesetzt (`<table>`), nicht als Div-Grid:

- eine Tabelle ist die einzige Struktur, die Screenreader als „Tabelle mit
  Zeilen und Spalten“ ansagen und **sprungweise** bedienen können;
- `<caption>` benennt die Tabelle, `<th scope="col">` und `<th scope="row">`
  ordnen zu;
- die Sortierung ist über `aria-sort` angekündigt.

### 5. Leerzustände

Eine leere Liste ist ein **verständlicher Leerzustand** mit Erklärung und
Handlungsvorschlag — keine leere Tabelle (AC-020-3). Der Leerzustand wird per
`role="status"` angekündigt.

### 6. Fremde Daten

Ruft ein Nutzer eine URL eines fremden Mandanten auf, antwortet die API mit
HTTP 404 (D-02). Die Oberfläche zeigt den **Leerzustand**, nicht fremde Daten
(AC-020-4) — und **keine** Fehlermeldung mit technischem Text.

### 7. Fehlerdarstellung

Feldbezogene Fehler werden **am Feld** angezeigt, mit `aria-describedby` am
Feld verknüpft und mit dem Code aus `Problem.violations` (AC-019-2). Keine
Fehlermeldung steht **nur** als Toast — Toasts werden von Screenreadern
missen.

### 8. Kein JavaScript-Zwang für die Bedienung

Alle drei Seiten sind ohne JavaScript **bedienbar** (Server Components, progressive
Erhöhung). Das ist die robusteste Form von „ohne Maus bedienbar“: Was nicht
angezeigt wird, kann nicht bedient werden. Interaktive Elemente
(Pause/Reaktivieren, Replay, Filter) arbeiten als Formularaktion.

## Konsequenzen

- **Positiv:** Die Oberfläche ist per Tastatur und Screenreader bedienbar, ohne
  dass dies nachträglich „nachgerüstet“ werden muss.
- **Positiv:** Die Typen stammen aus dem Vertrag — eine Feldänderung im Vertrag
  bricht den TypeScript-Build, wenn die Oberfläche nicht nachgezogen wurde.
- **Positiv:** Der axe-Scan ist Teil der CI; eine Regression wird vor dem
  Deployment gestoppt.
- **Negativ:** Semantisches HTML und `aria`-Attribute kosten mehr Aufwand als
  ein Div-Grid. Das ist der Preis von AC-021-4.
- **Negativ:** Der Vertragstyp-Generator braucht eine klare Namenskonvention
  (`PascalCase` für Schemas, `camelCase` für Properties) — ohne sie entstehen
  Hässlichkeiten wie `XEchoBoxEventId`.
- **Negativ:** Kontrastwerte müssen **gepflegt** werden: ein neuer Farbton kann
  den Kontrast brechen. Der Token-Prüfer im Build ist deshalb Pflicht, nicht
  Kür.

## Vorschlag zur Freigabe bei Gate 1

> **Vorschlag:** Die Oberfläche erfüllt WCAG 2.1 AA als **Bauregel**, nicht als
> Endabnahme. Der axe-Scan über alle drei Seiten ist ein **Pflicht-Gate** in
> der CI mit der Schwelle 0 Verstöße der Klassen „kritisch“ und „schwer“.
> *Warum das der Fachbereich bestätigen sollte:* Der Aufwand für die
> Barrierefreiheit ist eine **Zusage** gegenüber allen Nutzern des Dienstes;
> ob und in welcher Tiefe sie geprüft wird (nur Scan oder zusätzlich manuelle
> Prüfung mit Screenreader-Nutzern) ist eine Produkt- und Personalentscheidung.
> AC-021-4 verlangt einen **Screenreader-Test** — die technische Seite davon
> (semantische Struktur, `role`, `aria-live`) ist hier entschieden; **wer**
> testet, ist eine offene Frage.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Reines React + Vite** | Die `src/app/…/page.tsx`-Pfade der Traceability-Matrix sprechen für den App Router. |
| **Angular / Vue** | Widerspricht der Rahmenbedingung „React, TypeScript“. |
| **Div-Grid statt Tabelle** | Screenreader melden es nicht als Tabelle; das Springen zwischen Zeilen und Spalten geht verloren (AC-021-4). |
| **Farbe als einziger Statusindikator** | Unlesbar für Menschen mit Farbsehschwäche. |
| **Barrierefreiheit als Endabnahme-Audit** | Ein Audit findet Mängel spät; Bauregeln verhindern sie. |
| **Status nur als Text, ohne Farbe** | Die Farbe hilft der Mehrzahl der Nutzer; entscheidend ist, dass sie **nicht die einzige** Information trägt. |
| **Toasts für Fehlermeldungen** | Screenreader nehmen sie nicht zuverlässig wahr. |
| **Client-Side-Routing ohne Server-Rendering** | Ohne JavaScript wäre die Oberfläche unbedienbar. |

## Status

**Angenommen** — Bestätigung bei Gate 1 durch den Menschen.
