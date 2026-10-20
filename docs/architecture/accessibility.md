# PromptGuard — Barrierefreiheit, Typsicherheit und Bedienbarkeit

| Feld | Wert |
|---|---|
| **Dokument** | Barrierefreiheit (WCAG 2.1 AA), Typsicherheit, Internationalisierung |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | US-035, NFR-015, NFR-016, NFR-018, AC-035-1…AC-035-4 |

---

## 1. Grundsatz

PromptGuard ist ein **internes Werkzeug**, das von Teams mit unterschiedlicher
Vorbildung genutzt wird — vom erfahrenen Entwickler bis zur rollenden Rotation. Barrierefreiheit
ist deshalb **kein Nice-to-have**, sondern eine Bedingung für die Benutzbarkeit.

| Grundsatz | Umsetzung |
|---|---|
| **Bedienbar mit der Tastatur allein** | alle interaktiven Elemente sind fokussierbar und in logischer Reihenfolge erreichbar |
| **Status wird benannt, nicht nur eingefärbt** | jedes Statuslabel hat einen Text; Farbe ist zusätzlich, nie allein |
| **Technische Striktheit** | TypeScript `strict: true`, Java-Compiler-Warnungen als Fehler, kein `any`, keine unbehandelten Promises |
| **Sprache getrennt** | Oberfläche **deutsch**, API-Felder und Fehlercodes **englisch** |

---

## 2. WCAG 2.1 AA — Umsetzungsplan

### 2.1 Wahrnehmbar

| Kriterium | Umsetzung |
|---|---|
| 1.1.1 Nicht-Text-Inhalte | jedes Icon hat ein `aria-label`; Statuspunkte sind **nie** alleinige Information |
| 1.3.1 Info und Beziehungen | semantische HTML-Elemente über die CSS-Framework-Komponenten (`<table>`, `<nav>`, `<main>`, `<form>`); Formularfelder mit `<label>` |
| 1.3.2 Bedeutende Reihenfolge | DOM-Reihenfolge = visuelle Reihenfolge; kein CSS-`order` zur Umordnung |
| 1.4.1 Nutzung von Farbe | **Status hat immer ein Textlabel** (`APPROVED`, `FLAGGED`, …) und ein Icon mit Textalternative; Kontraste ≥ 4,5:1 für Text, ≥ 3:1 für UI-Komponenten |
| 1.4.3 Kontrast (Minimum) | Design-Tokens mit geprüften Kontrastverhältnissen; Kontrastprüfung ist Teil der CI (Automatisierung) |
| 1.4.4 Text skalieren | Layout in `rem`/`em`; kein `px`-Fixing; bis 200 % zoom ohne horizontalen Scroll |
| 1.4.10 Reflow | 320 px Breite ohne zweispaltiges Layout |
| 1.4.11 Kontrast nicht-Text | Statusfarben ≥ 3:1 gegen den Hintergrund |

### 2.2 Bedienbar

| Kriterium | Umsetzung |
|---|---|
| 2.1.1 Tastatur | **keine** Maus-Interaktion ist erforderlich; `Tab`-Reihenfolge folgt der visuellen; `Escape` schließt Dialoge; `Enter` bestätigt Formulare |
| 2.1.2 Keine Tastaturfalle | Fokus kann jeden Dialog verlassen (`Esc`, Schließen-Button, Fokusfalle im Dialog) |
| 2.1.4 Zeichenkürzel | keine |
| 2.2.1 Timing | **keine** zeitbasierte Interaktion; der Live-Status ist SSE ohne Timeout-Zwang |
| 2.2.2 Pause, Stoppen, Verbergen | der SSE-Stream ist pausierbar (Button „Live-Updates pausieren") |
| 2.3.1 Zeitlimits | Session-Timeout ist **verlängerbar**; keine Daten gehen bei Timeout verloren (Entwurf ist lokal gespeichert) |
| 2.4.3 Fokusreihenfolge | logisch, dokumentiert in den Komponenten-Tests |
| 2.4.7 Fokus sichtbar | `:focus-visible` mit **mindestens** 2 px Kontrastring; nie `outline: none` ohne Ersatz |

### 2.3 Verständlich

| Kriterium | Umsetzung |
|---|---|
| 3.1.1 Sprache der Seite | `lang="de"` (bzw. `lang="en"` gemäß Präferenz) am Wurzelelement |
| 3.2.1 Beim Fokus | Kontextwechsel nur bei **Absicht** (z. B. Absenden), nicht beim Fokuswechsel |
| 3.2.2 Beim Eingeben | keine Kontextwechsel; Formularwerte bleiben |
| 3.2.3 Navigation | konsistente, wiedererkennbare Navigation; breadcrumbs in der Tiefenhierarchie |
| 3.2.4 Identifizierung von Zwecken | Felder haben Labels, die Zweck beschreiben („Verbindungszeichen an den Support" statt „Feld 2") |
| 3.3.1 Fehlererkennung | Fehler stehen **beim Feld**, mit Text und `aria-describedby` |
| 3.3.2 Beschriftungen/Anweisungen | sichtbare Labels; Pflichtfelder gekennzeichnet |
| 3.3.3 Fehlervermeidung | Eingaben werden **serverseitig** vorab validiert; der Client validiert zusätzlich, aber der Server ist die Wahrheit |

### 2.4 Robust

| Kriterium | Umsetzung |
|---|---|
| 4.1.2 Name, Rolle, Wert | ARIA-Rollen für Statusregionen, Dialoge, Tabellen; `aria-live="polite"` für den Live-Status |
| 4.1.3 Statusmeldungen | **Statuswechsel werden angekündigt**: `aria-live="polite"` + Textlabel (AC-035-2) |
| 4.1.3 Fehlermeldungen | Fehlerregion mit `role="alert"` |
| 4.1.1 Parsing | valides HTML, keine doppelten `id`s, korrekte Überschriftenhierarchie |

### 2.5 Statuskommunikation (Kernfall AC-035-2)

```html
<!-- Prüfstatus: Text UND Farbe UND Symbol -->
<span class="status status--flagged" role="status" aria-live="polite">
  <svg aria-hidden="true" …></svg>
  Geflaggt — 2 Risiken gefunden
</span>
```

* Der Status ist **Text**, nicht nur Farbe — Pflicht für Screenreader und für
  Farbfehlsichtige.
* Die Statusänderung wird über `aria-live="polite"` **angekündigt**, ohne den Fokus zu
  verschieben.
* Der Statuswechsel wird zusätzlich im **Verlauf** (Historie) als Text geführt.

---

## 3. Nachweisverfahren

| Nachweis | Verfahren | Automatisiert? |
|---|---|---|
| **Tastaturbedienung** | manuelle Abnahme der Pfade Prompt-Liste → Detail → Editor → Versionshistorie; Fokusreihenfolge und Sichtbarkeit geprüft | teilweise ( axe + Fokus-Assertions ) |
| **Screenreader** | manuelle Abnahme mit mindestens einem Screenreader (VoiceOver/NVDA); Status wird mit Textlabel angesagt | nein |
| **Kontraste** | automatisierte Prüfung der Design-Tokens gegen die WCAG-Schwellen | **ja** (CI) |
| **Semantik/Aria** | `axe-core` in den Komponententests (Vitest + Testing Library) | **ja** (CI) |
| **Formularfehler** | Komponententests prüfen `aria-describedby`, `role="alert"`, Text | **ja** (CI) |
| **Kontrast im Status** | Test, dass das Statuslabel Text enthält (kein Farb-only) | **ja** (CI) |

> Die automatisierten Tests decken die Regeln ab, die sich regelbasiert prüfen lassen. Die
> **Screenreader-Abnahme** bleibt manuell — sie ist im MVP manuell (US-035, `test_level:
> manual`) und gehört in die Abnahme des Gates.

---

## 4. Typsicherheit (AC-035-3, NFR-016)

| Ebene | Regel | Durchsetzung |
|---|---|---|
| **TypeScript** | `strict: true`, `noUncheckedIndexedAccess`, `noImplicitAny` | `tsc --noEmit` im Build; **kein** `any`, kein `!` ohne Begründung |
| **TypeScript (async)** | keine unbehandelten Promises | ESLint-Regel `@typescript-eslint/no-floating-promises` (Fehler, nicht Warnung) |
| **Java** | Compiler-Warnungen als Fehler | `-Werror`; keine ungeprüften/ungesicherten Null-Pointer |
| **Vertragsbindung** | API-Typen werden **aus dem Vertrag erzeugt** | `openapi-typescript` bzw. generierte Client-Typen; `x-stories`/`x-required-role` werden nicht per Hand nachgebaut |
| **Lint/Format** | einheitlich | ESLint + Prettier, Checkstyle/Formatter im Maven-Build; **keine** ungelösten Warnungen |
| **CI** | Build bricht bei Typ- oder Lint-Fehlern ab | Pflichtschritt, Merge blockiert |

**Kein `any`:** Der einzige zulässige Einsatzzustand ist die **unbekannte** Antwort des
Providers — dort wird ein **Schema validiert** (JSON Schema), und ein Parsing-Fehler führt zu
`FAILED`, nicht zu `any`.

---

## 5. Internationalisierung (NFR-018, AC-035-4)

| Aspekt | Regel |
|---|---|
| **Oberfläche** | **deutsch** (Zielgruppe) |
| **API-Feldnamen, `operationId`s, Enums, Fehlercodes** | **englisch** |
| **API-Fehlertexte (`detail`)** | **englisch** (maschinenlesbar, international nutzbar) |
| **Datenschutzhinweis** | **deutsch und englisch**, versioniert |
| **Sprachwahl** | umschaltbar; die Wahl wird **gespeichert** (`locale` am Konto) und beim nächsten Aufruf wiederhergestellt |
| **Umschaltung im Betrieb** | `PATCH /api/v1/me` mit `locale`; wirkt ab dem nächsten Abruf (die Oberfläche liest die Einstellung beim Laden) |

**Umsetzung:** Schlüsselbasierte Übersetzung (`de` als Quelle, `en` als Übersetzung), keine
festen Texte im Code. Die Oberfläche übersetzt Fehlerklassen anhand des **englischen**
`title`/`code` aus dem Problem-Details-Body — nicht anhand des deutschen `detail`.

---

## 6. Bedienbarkeit (NFR-016, P3)

| Thema | Regel |
|---|---|
| **Reaktionsfähigkeit** | die Oberfläche blockiert **nie** auf einen Scan — Speichern antwortet sofort, der Status kommt asynchron (P3) |
| **Verständliche Zustände** | jeder Status hat Text, Erklärung und (bei `FAILED`) eine Handlungsoption |
| **Verluste vermeiden** | Entwürfe werden lokal zwischengespeichert, damit ein Session-Timeout keine Eingabe vernichtet (AC-035-3, 2.3) |
| **Rückmeldung** | jede Aktion erzeugt eine Rückmeldung (Toast/Statuszeile); Fehler bleiben sichtbar, bis sie quittiert werden |
| **Tastaturkürzel** | nur für fortgeschrittene Nutzer; alle Kürzel sind zusätzlich, nie die einzige Möglichkeit |
| **Lokalisierbarkeit** | Datums- und Zeitzonendarstellung in der Oberfläche (`Europe/Berlin` für Anzeige, UTC in der API) |

---

## 7. Abdeckung der Akzeptanzkriterien

| AK | Erwartung | Umsetzung |
|---|---|---|
| AC-035-1 | Prompt-Liste, Detail, Editor, Versionshistorie vollständig per Tastatur bedienbar; Fokus immer sichtbar | 2.2, 3 |
| AC-035-2 | Scanstatus wird mit **Textlabel** angesagt; Formularfelder haben Labels; Statuswechsel wird angesagt | 2.5 |
| AC-035-3 | Build scheitert bei Typ- oder Lint-Fehlern; kein `any`, keine unbehandelten Promises, keine ungelösten Lint-Warnungen | 4 |
| AC-035-4 | Oberfläche deutsch, API-Fehlercodes und Feldnamen englisch; Sprache umschaltbar und **gemerkt** | 5, `locale` in `User`/`ProfileUpdateRequest` |

---

## 8. Verweise

* `docs/api/openapi.yaml` — `User.locale`, `ProfileUpdateRequest.locale`, `Problem` (englische Fehlerklassen)
* `docs/architecture/overview.md` — Modulübersicht
* `docs/architecture/error-retry-strategy.md` — Fehlerformat und Korrelations-ID
* `docs/requirements/PRD.md` — NFR-015, NFR-016, NFR-018, US-035
* `docs/adr/ADR-001-tech-stack.md` — Toolchain (ESLint, Prettier, axe, Playwright)
