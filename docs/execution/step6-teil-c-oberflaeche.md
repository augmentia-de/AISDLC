# Schritt 6 (KI-Code-Generation) — Teil C: die Oberfläche

> Agent: `aisdlc-execution` · Teil-Lauf 3 von 4 · Stand: 2026-10-22
> Umfang dieses Laufs: **die Oberfläche** — Next.js-15-Projekt, die Typen aus
> dem Vertrag, den API-Client und die drei Seiten der Stories US-019, US-020
> und US-021 samt Barrierefreiheit (ADR-018) und Tests.

---

## 1. Was entstanden ist

### 1.1 Das Frontend-Projekt (`src/main/frontend/`)

| Baustein | Entscheidung |
|---|---|
| Framework | **Next.js 15.5.28** (App Router) auf **React 19.2.9**, **TypeScript 5.9.3** mit `strict: true` (ADR-018 §1) |
| Struktur | `src/app/` (Router), `src/features/` (Endpunkte, Protokoll, Tote Zustellungen), `src/components/` (DataTable, StatusBadge, EmptyState, FieldError, Notice, ErrorMessage, Pagination), `src/lib/` (API-Client, Cursor, Formatierung, Identität, Fehlerbild), `src/types/` (die Typen aus dem Vertrag) |
| TypeScript | `tsconfig.json` mit `strict`, `noUnusedLocals`, `noUnusedParameters`, `noImplicitOverride`, `noFallthroughCasesInSwitch`; der Wurzel-`tsconfig.json` zieht zusätzlich `tests/unit/frontend` und `tests/e2e` ein — `npx tsc --noEmit` prüft **die Oberfläche und die Tests** in einem Lauf |
| Lint | **ESLint 9** (Flat Config) auf `eslint-config-next` 15.5.28 (`next/core-web-vitals`, `next/typescript`) |
| Testumgebung | **Vitest 3.2.7** mit `jsdom` 26.1.0, Setup-Datei mit `@testing-library/jest-dom`; `include` = `tests/unit/frontend` (Teststrategie §6.3) |
| Browser-Test | **Playwright 1.64.1**, `testDir` = `tests/e2e`, `baseURL` aus der Konfiguration, `webServer` startet die Oberfläche (Standard: der Produktionsbuild) |
| Barrierefreiheit | **axe-core 4.14.0** (Komponentenebene) und **@axe-core/playwright 4.14.0** (Browser) als Pflicht-Gate: `npm run test:a11y` und `npm run test:e2e:a11y` |

**Die npm-Wurzel liegt im Projekt-Root** (`package.json` mit
`workspaces: ["src/main/frontend"]`). Grund: die Tests aus Schritt 5 liegen
unter `tests/`, **ausserhalb** des Frontend-Projekts. Ohne die Workspace-Wurzel
koennten die Testdateien die Pakete weder zur Laufzeit (Vite-Aufloesung) noch
zur Typpruefung (TypeScript) finden.

### 1.2 Die Typen aus dem Vertrag — eine Quelle

`src/types/api.ts` bildet genau die Schemas ab, die die Oberflaeche braucht
(11 Schemas, 3 Aufzaehlungen). Jeder Typ nennt im Kommentar sein Schema im
Vertrag; die Feldnamen bleiben **snake_case**, damit die Oberflaeche nicht
still von der JSON-Antwort abweicht.

**Der Abgleich mit dem Vertrag ist ein Skript, kein Kommentar:**
`npm run contract:check` (`scripts/check-contract-types.mjs`) liest
`docs/api/openapi.yaml`, loest die Schemas auf (auch `allOf`) und vergleicht
die Feldnamen mit den Typen. Ein Feld, das im Vertrag neu ist und in der
Oberflaeche fehlt — oder umgekehrt — **bricht den Check**. Der Vertrag wird
dabei **nicht** geaendert.

### 1.3 Der API-Client (`src/lib/apiClient.ts`)

- spricht **ausschliesslich** die im Vertrag definierten Operationen
  (`listEndpoints`, `createEndpoint`, `pauseEndpoint`, `resumeEndpoint`,
  `rotateEndpointSecret`, `listDeliveries`, `listDeadDeliveries`,
  `replayDelivery`);
- **kein Mandantenparameter** (D-01): der Client kennt kein
  `organisation_id` — weder im Query noch im Body. Der Mandant steckt im
  Token;
- **derselbe Ursprung**: der Client ruft `/api/v1/...` relativ auf, und
  Next.js leitet die Anfrage an den Dienst weiter (`next.config.mjs`,
  `ECHOBOX_API_BASE_URL`). Damit braucht die Oberflaeche **keine
  CORS-Konfiguration** und keine im Code verdrahtete Adresse;
- **das Fehlerbild** des Vertrags (`Problem`) wird ausgewertet: jede
  Antwort, die kein 2xx ist, wird zu einem `ApiError` mit `status`, `code`,
  `detail` und `violations`;
- das Bearer-Token kommt aus dem Browser-Speicher (`echobox.token`).

### 1.4 Die drei Seiten

| Seite | Pfad | Inhalt |
|---|---|---|
| Endpunkte | `src/app/endpoints/page.tsx` | Liste (Status, Ziel-URL **ohne** Secret, Ereignistypen, Secret-Generation), Registrierung, Pausieren/Reaktivieren, Secret-Rotation (FR-001…FR-003) |
| Zustellprotokoll | `src/app/deliveries/page.tsx` | Zustand, Versuche, letzter Versuch, Antwortcode, naechster Versuchszeitpunkt; Filter nach Endpunkt, Status und Zeitraum; Cursor-Paginierung **ohne** `total` (FR-030) |
| Tote Zustellungen | `src/app/dead-deliveries/page.tsx` | die toten Zustellungen mit Ereignis-ID, Endpunkt, Ereignistyp, Versuchen und Aufgabezeitpunkt; **erneut ausloesen** (FR-024) |

Jede Seite ist **eine Komponente** (Default-Export), eine Client-Komponente
und traegt die fachliche Logik nicht — sie liest den Zustand aus einem Hook
des jeweiligen Fachbereichs.

### 1.5 Die Barrierefreiheit als Bauregel (ADR-018)

| Regel | Wie sie im Code aussieht |
|---|---|
| native Bedienelemente | `<button>`, `<a>`, `<input>`, `<select>` — **kein** `div` mit Klickhandler, keine positive `tabindex` |
| echte Tabelle | `<table>` mit `<caption>`, `<th scope="col">`, `<th scope="row">` — kein Div-Grid |
| Status nie nur ueber Farbe | `StatusBadge` traegt **Text + Farbe + Form** (`●`, `■`, `▲`, `✓`, `✕`, `○`) |
| sichtbarer Fokus | `:focus-visible { outline: 3px solid … }` — nirgends `outline: none` |
| Kontrast | feste Token: Text `#111827` (17,7:1), Akzent `#1d4ed8` (6,7:1), Badges 6,4:1 bis 8,5:1 — **Text** >= 4,5:1, **Bedienelemente** >= 3:1 |
| Formulare mit Labels | jedes Feld hat ein `<label for>`; Fehler mit `aria-describedby` am Feld und `role="alert"` |
| Laenderollen und Ueberschriften | `<main>`, `<nav aria-label>`, `<header>`, `<footer>`, genau eine `<h1>` je Seite |
| `aria-live` | Lade- und Fehlerzustaende als `role="status"` / `role="alert"` |
| ohne Maus | die Sprungmarke fuehrt zum Hauptinhalt; die Reihenfolge folgt der DOM-Reihenfolge |

---

## 2. Entscheidungen, die der Vertrag oder die ADRs nicht festlegen

### 2.1 Die Pruefung der Ziel-URL passiert **vor** dem Senden

Die Feldpruefung im Browser wiederholt die `x-validation-rules` des Vertrags
(https-Pflicht, keine eingebetteten Zugangsdaten, Laenge 2000). **Sie
verhindert, dass ueberhaupt ein Endpunkt angelegt wird** (AC-019-2) — der
Dienst prueft trotzdem, denn die Oberflaeche ist kein Sicherheitsmechanismus.

**Die Meldungen sind wortgleich mit dem Vertrag:** „Die Ziel-URL muss das
Schema https verwenden.“ — so steht es im Fehlerbild des Vertrags und so
prueft es der Test.

### 2.2 Die Rotation sendet auch eine leere Eingabe

Das Feld „Neues Secret“ ist **nicht** `required` und hat **keine**
`minlength`-Beschraenkung: ein Browser, der die Formularpruefung des
Browsers benutzt, wuerde das Abschicken blockieren. Die Schaltfläche sendet
den eingegebenen Wert — auch einen leeren. Der Dienst weist ihn dann mit
**HTTP 422** und dem Verstoss am Feld (`violations[].field = "secret"`) zurueck;
die Oberflaeche zeigt diese Meldung **am Feld**. So bleibt die Laengenpruefung
eine **einzige** Stelle (der Dienst) und die Oberflaeche meldet den Verstoss
trotzdem am richtigen Ort.

### 2.3 Die Anzeige der Zustaende

Die **Endpunktseite** zeigt die deutschen Zustaende („aktiv“, „pausiert“),
das **Zustellprotokoll** die maschinenlesbaren Werte des Vertrags
(`sent`, `failed`, `abandoned`, …). Das ist so vom Testvertrag vorgegeben
(Schritt 5 §6) und passt zu den Filtern: der **Wert** des Filters ist immer
der Vertragswert, nur die **Beschriftung** der Filterliste ist uebersetzt.

### 2.4 Zeitpunkte in UTC

Die Zeitpunkte des Vertrags sind RFC-3339 in UTC; die Anzeige rechnet sie
ohne Zeitzonenumrechnung um (`TT.MM.JJJJ HH:MM:SS`). Sonst wuerde derselbe
Vorgang je nach Browser anders aussehen. Der Zeitraumfilter der Seite liest
ein Datum (`JJJJ-MM-TT`) und schickt `from` als `T00:00:00Z` und `to` als
`T23:59:59.999Z` — beide als UTC, damit der Filter eindeutig ist.

### 2.5 Ein 404 ist ein Leerzustand, kein Fehler

Ein 404 des Dienstes bedeutet **fremde Ressource** (D-02, V-39). Die Seite
zeigt dann den **Leerzustand** und keine Fehlermeldung mit technischem Text
(ADR-018 §6, AC-020-4). Ein 403 (eigene Ressource, falsche Rolle) wird als
Rollenmeldung angezeigt.

### 2.6 Das erneute Ausloesen wird nur angeboten, wenn es erlaubt ist

Das erneute Ausloesen ist **ausschliesslich** dem Empfaenger-Admin des
betroffenen Endpunkts erlaubt (D-15, F-03). Die Oberflaeche liest die Rollen
aus dem Token (nur fuer die **Anzeige** — die Pruefung bleibt beim Dienst)
und bietet die Aktion nur an, wenn `receiver_admin` in der Identitaet steht.
Die Rollen werden **im Effekt** gelesen, damit das Serverrendern und der
Browser dasselbe erzeugen (kein Hydration-Unterschied).

### 2.7 Ohne JavaScript bedienbar? — der ehrliche Teil

ADR-018 §8 verlangt, dass alle drei Seiten **ohne JavaScript** bedienbar sind
(Server Components, progressive Erhoehung). **Das ist in diesem Lauf nicht
erfuellt:** die Seiten sind Client-Komponenten, weil die Tests aus Schritt 5
sie direkt rendern und ihr Verhalten (Laden, Pausieren, Filtern) pruefen.
Ein Umsetzen waere eine **Neuausrichtung** des Schritts — die Seiten wurden
dann als Server Components entworfen und die Tests muessten neu geschrieben
werden. **Befund O-13** (siehe Abschnitt 5).

---

## 3. Geaenderte Tests — und warum

Die Semantik (Vertrag, ADRs, Schema) wurde **nicht** geaendert. Zwei
Teststellen wurden nachgezogen; jede Aenderung ist eine **Korrektur eines
Testdefekts**, keine Anpassung an eine abweichende Implementierung.

| # | Datei / Stelle | Defekt | Korrektur |
|---|---|---|---|
| **T-17** | `tests/unit/frontend/DeliveryProtocolPage.test.tsx` — `AC-020-1` | Der Test erwartet den Zeitpunkt `09.10.2026 09:16:24`, sein eigener Fixture-Wert ist aber `last_attempt_at = 2026-10-21T09:16:24.000Z`. Der **Schwester-Testfall** der toten Zustellungen legt das Format fest: `abandoned_at = 2026-10-21T10:22:11.900Z` wird als `21.10.2026 10:22:11` erwartet — also `TT.MM.JJJJ HH:MM:SS`. Die beiden Erwartungen widersprechen sich; nur die des Schwester-Testfalls passt zu ihrem eigenen Wert. | Erwartung auf `21.10.2026 09:16:24` gezogen (Kommentar im Test benennt den Grund). Das Format, die Zeitzone (UTC) und die Semantik bleiben unveraendert. |
| **T-18** | `tests/unit/crypto/PayloadCipherTest.the_encryption_key_is_not_hardcoded_in_the_project` | Der Testlauf ging ueber **jede** Datei unter `src/` und las sie als UTF-8. Mit der Oberflaeche liegt dort auch der **Build-Output** (`.next/`, 61 MB, mit binaeren Dateien) — `Files.readString` bricht an einer ungueltigen UTF-8-Folge mit `MalformedInputException` ab. Der Nachweis „der Schluessel steht in keinem Projektartefakt“ (AC-007-4) war damit **nicht mehr ausfuehrbar**. | Zwei Aenderungen: (a) die erzeugten Verzeichnisse (`node_modules`, `.next`, `target`) werden nicht geprueft — sie sind keine Projektartefakte, sie werden beim Build erzeugt und stehen nicht im Versionsverwaltung; (b) die Dateien werden als **Bytes** gelesen und als ISO-8859-1 interpretiert — der Nachweis gilt damit **auch fuer binaere Dateien** (der Schluessel ist ASCII) und bricht nicht mehr ab. Die Aussage des Tests ist damit nicht schwächer, sondern strenger. |
| **T-19** | `tests/integration/deployment/ContainerStartupIT.the_project_contains_no_hardcoded_secret` | Derselbe Defekt an derselben Stelle: der Test ging ueber jede Datei unter `src/` und las sie als UTF-8 — mit dem Build-Output der Oberflaeche bricht er mit `MalformedInputException` ab. Der Nachweis „im Projekt ist kein fest verdrahtetes Secret hinterlegt“ (AC-023-1) war damit **nicht ausfuehrbar**. | Dieselbe Korrektur wie T-18: die erzeugten Verzeichnisse werden ausgenommen, die Dateien werden als Bytes gelesen und als ISO-8859-1 interpretiert. Die Aussage des Tests bleibt unveraendert — und gilt nun auch fuer binaere Dateien. |

---

## 4. Teststand

### 4.1 Frontend

| Ebene | Umfang | Ergebnis |
|---|---|---|
| TypeScript | `npx tsc --noEmit` (strict) ueber `src/main/frontend` **und** `tests/unit/frontend` **und** `tests/e2e` | **gruen** — keine Fehler |
| Lint | `npm run lint` (ESLint 9, Flat Config, `next/core-web-vitals` + `next/typescript`) | **gruen** — keine Fehler, keine Warnungen |
| Build | `npm run build` (Next 15.5.28, Produktionsbuild, 5 Seiten) | **gruen** — „Compiled successfully“, Typpruefung im Build eingeschlossen |
| Vertragsabgleich | `npm run contract:check` | **gruen** — 11 Schemas, 3 Aufzaehlungen stimmen mit `docs/api/openapi.yaml` ueberein |
| Komponententests | `npx vitest run` — `tests/unit/frontend` | **gruen** — **14** Testfaelle in 4 Dateien |
| Browser-Tests | `npx playwright test` — `tests/e2e/endpoints.spec.ts`, `protocol.spec.ts`, `dead-deliveries.spec.ts`, `a11y.spec.ts` | **gruen** — **22** Testfaelle (Chromium) |
| Barrierefreiheit | `npm run test:a11y` (axe-core) und `npm run test:e2e:a11y` (axe im Browser) | **gruen** — 0 Verstoeße der Klassen „kritisch“/„schwer“, Kontrast >= 4,5:1 |

**Nicht ausgefuehrt:** `tests/e2e/health.spec.ts` — der Test spricht den
**Dienst** an (`ECHOBOX_API_BASE_URL`, Standard `http://localhost:8080`), nicht
die Oberflaeche. Ohne laufenden Dienst (PostgreSQL, JWT) kann er nicht gruen
sein; er gehoert nicht zum Umfang dieses Teil-Laufs. Der Test schlaegt **nur**
an der fehlenden Dienstinstanz fehl (`ECONNREFUSED`), nicht an der Oberflaeche.

### 4.2 Backend (unveraendert)

`mvn -q verify` (Java 21, Docker fuer Testcontainers) — **gruen**. Die
Oberflaeche hat keinen Einfluss auf die Java-Tests; die einzige Beruehrung
waren T-18 und T-19 (siehe dort) — zwei Tests, die ueber `src/` gelaufen sind
und am **Build-Output** der Oberflaeche gescheitert sind.

---

## 5. Offene Punkte

> **Nummerierung (Stand Schritt 7):** **B-12** und **B-13** sind die Befunde
> dieses Teils. Teil B trug dieselben Nummern frueher fuer zwei andere Befunde —
> sie heissen dort jetzt **B-17** und **B-18**.

| ID | Thema | Owner |
|---|---|---|
| **O-13** | **Die Seiten sind Client-Komponenten.** ADR-018 §8 verlangt, dass alle drei Seiten **ohne JavaScript** bedienbar sind (Server Components, progressive Erhoehung). Das ist **nicht erfuellt**: die Tests aus Schritt 5 rendern die Seiten direkt und pruefen ihr Verhalten im Browser. Eine Umstellung auf Server Components waere eine **Neuausrichtung** des Schritts (die Tests muessten neu geschrieben werden) — sie gehoert nicht in einen Teil-Lauf. Bis dahin gilt: **Bedienung ohne Maus** ist erfuellt (AC-021-1), **Bedienung ohne JavaScript** nicht. | Schritt 7 / Gate 2 |
| **O-14** | **Das erneute Ausloesen ist ueber die Totenliste fuer den Empfaenger-Admin nicht erreichbar.** Der Vertrag erlaubt `listDeadDeliveries` nur der Service-Administration, `replayDelivery` nur dem Empfaenger-Admin des Endpunkts (D-15, F-03). Die Totenliste kann der Empfaenger-Admin also gar nicht lesen — die Zustelllauf-ID fuer den Replay muesste er aus dem Zustellprotokoll kennen. **Kein Vertragsdefekt** (der Vertrag ist so gemeint), aber eine **Luecke in der Bedienung**: die Oberflaeche bietet die Aktion auf der Totenliste an, sobald `receiver_admin` in der Identitaet steht — im Betrieb wird dieser Fall also selten erreicht. | Fachbereich, Gate 2 |
| **O-15** | **Die Oberflaeche hat keine eigene CORS-Konfiguration.** Sie spricht den Dienst ueber denselben Ursprung an (`/api/v1/...` + Next-Rewrite). Das ist beabsichtigt (kein im Code verdrahteter Dienst, keine zweite Origin-Regel) — fuer einen **getrennten** Betrieb (Oberflaeche und Dienst in verschiedenen Origins) muesste der Dienst eine CORS-Regel bekommen. | Schritt 9 (Betrieb) |
| **B-12** | **`next lint` ist in Next 15.5 ueberholt.** Der Build prueft die Regeln deshalb nicht selbst (`eslint.ignoreDuringBuilds`), `npm run lint` laeuft als **eigener** Schritt der Pipeline. Fuer die CI (Schritt 9) ist das eine Zeile. | Schritt 9 |
| **B-13** | **Die npm-Installation laeuft ueber die Workspace-Wurzel** (`npm install` im Projekt-Root). `package-lock.json` liegt deshalb im Root. Wer nur das Backend baut, braucht kein Node. | nach Bedarf |
| **O-04 / O-05 / R-01 / E-12** | unveraendert offen (Fachbereich, Gate 2) | wie bisher |

**Kein Vertragsdefekt wurde in diesem Lauf gefunden** — der Vertrag wurde
**nicht** geaendert.

### 5.1 Neben dem Commit-Scope

`.gitignore` wurde um `/src/main/frontend/test-results/`,
`/src/main/frontend/playwright-report/` und `*.tsbuildinfo` ergaenzt
(Hygiene; die Datei liegt ausserhalb des Commit-Scopes dieses Schritts).

---

## 6. Die Bedienung in einem Satz

Endpunkte anlegen, pausieren, reaktivieren, das Secret rotieren; das
Zustellprotokoll nach Endpunkt, Status und Zeitraum filtern und seitenweise
blättern; die toten Zustellungen sehen und — als Empfaenger-Admin — erneut
ausloesen: alles ueber native Bedienelemente, echte Tabellen und einen
Text, den auch ein Screenreader vorliest.
