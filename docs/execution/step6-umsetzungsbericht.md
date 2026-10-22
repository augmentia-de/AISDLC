# Schritt 6 (KI-Code-Generation) — Gesamtbericht

> Agent: `aisdlc-execution` · Teil-Lauf 4 von 4 (Teil D) · Stand: 2026-10-22
> Umfang: der Abschluss von Schritt 6 — **D1** ADR-018 §8 durchsetzen,
> **D2** O-14 beheben, **D3** O-15/B-12/B-13 klaeren, **D4** die
> Build-Konfiguration in den Commit-Scope bringen, **D5** die
> Gesamtverifikation, **D6** diese Dokumentation, **D7** der eine Commit.
>
> Die Arbeitsprotokolle der drei vorherigen Teil-Laeufe bleiben erhalten:
> [`step6-teil-a-backend-fundament.md`](step6-teil-a-backend-fundament.md) ·
> [`step6-teil-b-zustellung-protokoll.md`](step6-teil-b-zustellung-protokoll.md) ·
> [`step6-teil-c-oberflaeche.md`](step6-teil-c-oberflaeche.md)

---

## 1. Was implementiert wurde

### 1.1 Backend (Java 21, Spring Boot 3) — Teil A und Teil B

| Modul | Verantwortung | Dateien |
|---|---|---|
| Fundament | Fehlerbild (`Problem`), Cursor, Zeit, IDs, JWT-Filter, Mandantenkontext, Krypto (AES-256-GCM, HMAC-SHA256) | `src/main/java/com/echobox/{common,config,crypto,tenant,security}` |
| `endpoint` | Registrierung, Pause/Reaktivieren, Secret-Rotation, rollenabhaengige Sichtbarkeit | `.../endpoint/` |
| `event` | Einlieferung, Idempotenz je (Mandant, Schluessel), Groessengrenze, Anlegen der Zustelllaeufe | `.../event/` |
| `delivery` | Zustellzyklus, Backoff, Rate-Limit, Replay (einmal, `UNIQUE (replay_of)`) | `.../delivery/` |
| `protocol` | Zustellprotokoll, Totenliste, Kennzahlen, Aufbewahrung | `.../protocol/` |
| Datenbank | Flyway-Migration als **byte-identische** Kopie von `.agent/knowledge/schema.sql` | `src/main/resources/db/migration/V1__initial_schema.sql` |

Der ausfuehrliche Bericht je Modul steht in den Teilberichten A und B.

### 1.2 Oberflaeche (Next.js 15, React 19, TypeScript strict) — Teil C und **Teil D**

Die Oberflaeche liegt in `src/main/frontend/` (so wie es
`docs/architecture/folder-structure.md` §1 vorsieht).

| Baustein | Verantwortung |
|---|---|
| `src/app/{endpoints,deliveries,dead-deliveries}/page.tsx` | die drei Seiten — **Server Components** |
| `src/app/page.tsx` | die Startseite mit der **Anmeldung** (Formularaktion → httpOnly-Cookie) |
| `src/app/actions.ts` | die **Server Actions** (`'use server'`): anlegen, pausieren, reaktivieren, rotieren, replayen, anmelden, abmelden |
| `src/lib/apiClient.ts` | der HTTP-Client — **serverseitig**, `cache: 'no-store'`, Bearer-Token aus der Sitzung |
| `src/lib/session.ts` | die Sitzung: das Token im **httpOnly-Cookie**, die Rollen und der Mandant aus dem Token |
| `src/lib/feedback.ts` | die Rueckmeldung einer Aktion: **Codes** statt Text in der URL |
| `src/features/*/\*Queries.ts` | die Abfragen der Listen (je Seite eine) |
| `src/features/*/*.tsx` | die Anzeigen und Formulare (Server Components) |
| `src/types/api.ts` | die Typen aus dem Vertrag (`npm run contract:check` prueft das) |

---

## 2. D1 — ADR-018 §8 ist jetzt durchgesetzt

### 2.1 Der Verstoss

ADR-018 §8 (bei Gate 1 am 2026-10-21 **bestaetigt**): *„Alle drei Seiten sind
ohne JavaScript **bedienbar** (Server Components, progressive Erhoehung) […]
Interaktive Elemente (Pause/Reaktivieren, Replay, Filter) arbeiten als
**Formularaktion**.“*

Die Umsetzung aus Teil C nutzte in **allen drei** Seiten `'use client'`: die
Seiten holten ihre Daten im Browser und brauchten JavaScript fuer jede
Aktion. Das war ein **echter Verstoss** gegen eine bestaetigte Entscheidung
(O-13 aus Teil C).

### 2.2 Was geaendert wurde

| Punkt | vorher | nachher |
|---|---|---|
| Die drei Seiten | Client Components (`'use client'`) | **Server Components** — sie rendern auf dem Server |
| Datenholen | `fetch` im Browser (`localStorage`-Token) | `fetch` **serverseitig** (`lib/apiClient.ts`), `cache: 'no-store'` |
| Das Token | `localStorage` (`echobox.token`) | **httpOnly-Cookie** (`lib/session.ts`) — ein `localStorage`-Token waere fuer einen Server Component nicht erreichbar und ueber ein XSS-Skript auslesbar |
| Pause/Reaktivieren | `<button onClick>` | `<form action={serverAction}>` mit einem versteckten `endpoint_id` |
| Secret-Rotation | `<form onSubmit>` im Browser | `<form action={serverAction}>` |
| Replay | `<button onClick>` | `<form action={serverAction}>` |
| Filter | `<select onChange>` + Zustand im Browser | **GET-Formular** — die Filter stehen in der URL der Seite |
| Seitennavigation | Buttons mit Zustand im Browser | **Links** — der Cursor steht in der URL |
| Registrierung | `<form onSubmit>` im Browser | `<form action={serverAction}>` mit der Pruefung **vor** dem Senden |
| Rueckmeldung | Zustand im Browser (`useState`) | ein **Code** in der URL (`ergebnis=angelegt`), der Text steht an einer Stelle (`lib/feedback.ts`) |
| Anmeldung | **fehlte** — es gab keinen Weg, ein Token zu hinterlegen | die Startseite hat ein Anmeldeformular (Formularaktion → Cookie) |

**Der Vertrag wurde nicht angefasst.** Die Aktionen sprechen genau die
Operationen des Vertrags an (`createEndpoint`, `pauseEndpoint`,
`resumeEndpoint`, `rotateEndpointSecret`, `replayDelivery`).

### 2.3 Der Nachweis

Der Nachweis ist **zweistufig** — Quelle und Ergebnis:

1. **Die Quelle** (`tests/unit/frontend/ProgressiveEnhancement.test.tsx`):
   - **keine** Datei der Oberflaeche enthaelt `'use client'`;
   - **keine** Datei enthaelt `onClick=`, `onSubmit=`, `useState(`,
     `useEffect(` oder `localStorage.`;
   - der Filter ist ein **GET-Formular** (`method="get"`, `action="/deliveries"`);
   - jede **aktivierbare** Schaltflaeche liegt in einem `<form>`.

2. **Das Ergebnis** (`tests/e2e/`, drei Bloecke mit
   `test.use({ javaScriptEnabled: false })`): der Browser laeuft **ohne
   JavaScript** — das Pausieren, der Filter und das erneute Ausloesen
   funktionieren, die Seite zeigt den neuen Zustand. Das ist der
   staerkste moegliche Nachweis: nicht „ohne JavaScript wuerde es
   funktionieren“, sondern **es funktioniert ohne JavaScript**.

Zusaetzlich: der Browser-Build zeigt alle fuenf Routen als
`ƒ (Dynamic) server-rendered on demand` — es gibt **keine** statisch
vorgerenderte Seite, die ohne Server nicht auskommen wuerde.

---

## 3. D2 — O-14: das erneute Ausloesen ist fuer den Empfaenger-Admin erreichbar

### 3.1 Die Luecke

Der Vertrag erlaubt `listDeadDeliveries` **nur** der Service-Administration
(E-01); ein `receiver_admin` erhaelt dort **HTTP 403**. Das erneute
Ausloesen (`replayDelivery`) ist dagegen **nur** dem Empfaenger-Admin des
betroffenen Endpunkts erlaubt (F-03). Die Totenliste konnte den
Empfaenger-Admin also gar nicht erreichen — der Weg war **geschlossen**.

### 3.2 Die Loesung

Die Seite liest die toten Zustellungen mit **der Operation, die die
Identitaet lesen darf**:

| Identitaet | Quelle der Liste | Replay angeboten? |
|---|---|---|
| `service_admin` | `GET /api/v1/dead-deliveries` (mandantenuebergreifend) | **nein** (F-03: der Service-Admin ⇒ 403) |
| `receiver_admin` | `GET /api/v1/deliveries?status=abandoned` (eigener Mandant) | **ja**, aber **nur** fuer die Zustaelle **seines** Mandanten |
| `developer` | `GET /api/v1/deliveries?status=abandoned` | **nein** (F-03) |

Die Berechtigung wird **zweifach** geprueft: die Rolle `receiver_admin` muss
im Token stehen **und** der Zustelllauf muss dem Mandanten der Identitaet
gehoeren (`mayReplay(...)` in
`src/main/frontend/src/features/dead/deadDeliveryQueries.ts`). Der Dienst
prueft dasselbe unabhaengig davon — ein fremder Mandant ergibt **404**, die
eigene Ressource mit falscher Rolle **403** (V-39).

Das ist **kein Vertragsdefekt**: der Vertrag ist so gemeint. Die Luecke lag in
der **Bedienung**, und sie ist jetzt geschlossen.

### 3.3 Der Nachweis

| Ebene | Test | Was er beweist |
|---|---|---|
| Unit | `DeadDeliveryPage.test.tsx` — „der Empfaenger-Admin liest die aufgegebenen Zustellungen ueber das Zustellprotokoll“ | die Seite fragt `/deliveries?status=abandoned` und **nicht** `/dead-deliveries` |
| Unit | „der Empfaenger-Admin darf seinen eigenen Vorgang erneut ausloesen“ | die Aktion **ist** da |
| Unit | „der Entwickler darf den Vorgang nicht erneut ausloesen“ | die Aktion ist **nicht** da |
| Unit | „der Empfaenger-Admin eines anderen Mandanten darf … nicht“ | die Aktion ist **nicht** da (Mandantenschutz) |
| Unit | `mayReplay(...)` — vier Faelle | die Regel selbst (F-03, V-39) |
| Unit | „das erneute Ausloesen loest genau einen Aufruf beim Dienst aus“ | genau **ein** `POST /api/v1/deliveries/{id}/replay` |
| Playwright | „O-14: der Empfaenger-Admin loest seinen Vorgang erneut aus“ | im echten Browser: die Schaltflaeche, die Bestaetigung **und** der aufgezeichnete POST beim Dienst |
| Playwright | „O-14: der Entwickler darf … nicht“ / „… eines anderen Mandanten …“ | die Aktion fehlt im Browser |

---

## 4. D3 — O-15, B-12, B-13

### 4.1 O-15 (CORS) — erledigt, die Antwort ist jetzt klarer

**Befund aus Teil C:** „Die Oberflaeche hat keine eigene CORS-Konfiguration.
Sie spricht den Dienst ueber denselben Ursprung an (`/api/v1/...` +
Next-Rewrite).“

**Entscheidung:** **Keine CORS-Regel — und jetzt auch kein Rewrite im
Betriebsfall.** Die Seiten sind Server Components: der Browser ruft den
Dienst **nicht** an, sondern nur die Oberflaeche (same-origin). Die
Oberflaeche ruft den Dienst **serverseitig** an — ein Server-zu-Server-Aufruf
kennt kein CORS. Damit gilt:

- **keine** CORS-Konfiguration im Dienst,
- **kein** im Code verdrahteter Dienst (die Adresse kommt aus
  `ECHOBOX_API_BASE_URL`),
- **kein** `Access-Control-*` im Antwortkopf des Dienstes,
- der Browser kennt die Adresse des Dienstes **nicht**.

Der Same-Origin-Rewrite in `next.config.mjs` **bleibt** bestehen: er ist der
Weg, auf dem ein Browser-seitiger Aufruf (z. B. eine spaetere Erweiterung)
ohne CORS-Regel auskomme. Er ist im Standardfall **inaktiv**, weil kein
Browser-seitiger Aufruf stattfindet.

**Beleg, dass der Aufruf tatsaechlich funktioniert:** die Browser-Tests laufen
gegen einen **laufenden** Dienst (den Dienst-Doppelgaenger, siehe 5.4) — die
gesamte Strecke Browser → Oberflaeche → HTTP → Dienst ist gruen. Die
Playwright-Konfiguration startet beide Server; mit `ECHOBOX_API_BASE_URL` zeigt
sie auf einen echten Dienst.

### 4.2 B-12 (`next lint` ist ueberholt) — dokumentiert fuer Schritt 9

`next lint` ist in Next 15.5 ueberholt. Die Konfiguration setzt deshalb
`eslint: { ignoreDuringBuilds: true }`; **der Build prueft die Regeln nicht
selbst**. `npm run lint` laeuft als **eigener** Schritt der Pipeline.

*Fuer Schritt 9 (CI):* der Lint-Job ist eine eigene Zeile neben dem Build-Job
— z. B. `npm run lint` als eigener `run`-Schritt, mit `eslint .` im
Frontend-Verzeichnis. Die Datei `src/main/frontend/eslint.config.mjs` liegt
bereits im Commit-Scope (`src/`).

### 4.3 B-13 (npm-Workspace-Wurzel) — dokumentiert, mit Beweis

Die Tests aus Schritt 5 liegen unter `tests/`, **ausserhalb** des
Frontend-Projekts. Ohne die npm-Workspace-Wurzel koennten die Testdateien ihre
Abhaengigkeiten weder zur Laufzeit (Vite-Aufloesung) noch zur Typpruefung
(TypeScript) finden: die Aufloesung geht vom Verzeichnis der Testdatei nach
oben, und dort liegt kein `node_modules`.

Deshalb liegt die npm-Wurzel im Projekt-Root und `src/main/frontend` ist ein
Workspace-Mitglied (`workspaces: ["src/main/frontend"]`). Wer nur das Backend
baut, braucht kein Node: `mvn -q verify` laeuft ohne `npm install`.

*Fuer Schritt 9 (CI):* der Node-Job installiert **am Projekt-Root**
(`npm ci`), nicht im Frontend-Verzeichnis — dort liegt die `package-lock.json`.

---

## 5. D5 — Gesamtverifikation

Alle Pruefungen wurden **nach** der Umstellung ausgefuehrt.

| # | Pruefung | Ergebnis |
|---|---|---|
| 1 | `mvn -q verify` (Java 21, Docker fuer Testcontainers) | **gruen** (Exit 0) — Unit, Integration, Vertrag; der Lasttest laeuft im eigenen Profil `contract-performance` |
| 2 | `npx tsc --noEmit` (strict, aus der Projektwurzel) | **gruen** — 0 Fehler (Oberflaeche **und** Tests) |
| 3 | `npm run lint` (ESLint 9, Flat Config) | **gruen** — 0 Fehler, 0 Warnungen |
| 4 | `npm run build` (Next 15.5.28, Produktionsbuild) | **gruen** — „Compiled successfully“, alle 5 Routen `ƒ (Dynamic)` |
| 5 | `npx vitest run` | **gruen** — **30** Testfaelle in 5 Dateien |
| 6 | `npx playwright test` | **gruen** — **31** Testfaelle (Chromium), mit laufendem Dienst |
| 7 | `diff .agent/knowledge/schema.sql src/main/resources/db/migration/V1__initial_schema.sql` | **leer** (`[ok] Files are identical`) |
| 8 | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (`[ok] Files are identical`) |
| 9 | `python3 .agent/tools/traceability_check.py` | **gruen** — „Keine Luecken — die Kette Anforderung → Story → Feature → Test → Code ist vollstaendig.“ |
| 10 | `npm run contract:check` | **gruen** — 11 Schemas, 3 Aufzaehlungen stimmen mit dem Vertrag ueberein |

### 5.1 Teststand je Ebene

| Ebene | Umfang | Ergebnis |
|---|---|---|
| Backend Unit (Surefire) | `tests/unit` — **89** Testfaelle | **gruen** |
| Backend Integration (Failsafe) | `tests/integration` — **258** Testfaelle | **gruen** |
| Vertrag (Profil `contract-performance`) | `tests/contract` — **19** Testfaelle | **gruen** |
| Last (Profil `contract-performance`) | `tests/performance` — **2** Testfaelle | **gruen** (NFR-002: p95 < 200 ms ueber 1.000 Einlieferungen) |
| Oberflaeche Unit (Vitest) | `tests/unit/frontend` — **30** Testfaelle in 5 Dateien | **gruen** |
| Oberflaeche Browser (Playwright) | `tests/e2e` — **31** Testfaelle | **gruen** |

### 5.2 Die nachgezogenen Tests — Teil D (T-20 … T-27)

Die Semantik (Vertrag, ADRs, Schema) wurde **nicht** geaendert. Jede Aenderung
ist entweder eine **Anpassung an die neue Architektur** (Server Component
statt Client Component) oder eine **Korrektur eines Testdefekts**.

| # | Datei / Stelle | Defekt oder Anpassung | Korrektur | AC bleibt abgedeckt durch |
|---|---|---|---|---|
| **T-20** | `tests/unit/frontend/EndpointsPage.test.tsx` (vollstaendig neu) | Die Seite ist ein **Server Component**: sie laesst sich nicht mehr mit `render(<Page />)` rendern (die Props sind ein `Promise`, die Seite ist `async`). | Der Test rendert die Seite **mit dem Ergebnis**: `render(await EndpointsPage({ searchParams }))`. Die **Aktionen** werden ueber einen echten Formularabschick geprueft (`runAction` — der Helfer faengt die Weiterleitung von Next.js ab und liefert das Ziel). | **AC-019-1** (Status, Ziel-URL ohne Secret, Ereignistypen — unveraendert), **AC-019-2** (Meldung am Feld + `aria-describedby` + **kein** Anlegen), **AC-019-3** (Statuswechsel + genau ein POST an `/pause`), **AC-019-4** (Bestaetigung ohne Klartext) |
| **T-21** | `tests/unit/frontend/DeliveryProtocolPage.test.tsx` (vollstaendig neu) | Wie T-20; zusaetzlich: der Filter war eine `onChange`-Reaktion im Browser. | Der Test prueft die **Anfrage** (kombinierbare Filter: `endpoint_id`, `status`, `from`, `to` als UTC-Grenzen) und die **Anzeige**. Der Filter wird zusaetzlich als GET-Formular geprueft (`method="get"`, `action="/deliveries"`). | **AC-020-1** (Zustand, Versuche, letzter Versuch, Antwortcode, naechster Versuch — unveraendert; die Kombination der Filter jetzt an der Anfrage statt an der Auswahl), **AC-020-3** (Leerzustand), **AC-020-4** (404 → Leerzustand, keine fremden Daten) |
| **T-22** | `tests/unit/frontend/DeadDeliveryPage.test.tsx` (vollstaendig neu) | Wie T-20; zusaetzlich: die Tabelle zeigte direkt das Vertragsmodell `DeadDelivery`. | Die Seite fuehrt ein **Zeilenmodell** (`DeadDeliveryRow`) — die Quelle kann die Totenliste **oder** das Zustellprotokoll sein (D2). Der Test deckt beide Quellen und alle vier Berechtigungsfaelle ab. | **AC-020-2** (Ereignis-ID, Endpunkt, Ereignistyp, Versuche, Aufgabezeitpunkt — unveraendert), **AC-020-3** (Leerzustand), **FR-024** (Replay fuer den Empfaenger-Admin, **nur** seinen) |
| **T-23** | `tests/unit/frontend/a11y.test.tsx` | Der Scan lief ueber die **Client-Komponenten**; es gibt sie nicht mehr. | Der Scan laeuft ueber die **gerenderten Seiten** — der Test prueft damit die echte Ausgabe inklusive Formularen und Tabellen. | **AC-021-2** (axe ueber alle drei Seiten, 0 schwere Verstoesse), **AC-021-4** (Rollen und Inhalte) |
| **T-24** | `tests/unit/frontend/ProgressiveEnhancement.test.tsx` (**neu**) | Es gab **keinen** Test fuer die Zusage aus ADR-018 §8. | Der Test prueft Quelle und Ergebnis (siehe 2.3). **Neu** gegenueber Schritt 5. | **ADR-018 §8** (ohne JavaScript bedienbar) —Deckung fuer AC-021-1 (Tastaturbedienung) und AC-019-*/AC-020-* (Bedienbarkeit der Aktionen) |
| **T-25** | `tests/e2e/{endpoints,protocol,dead-deliveries,a11y}.spec.ts` | Die Seiten sind Server Components: `page.route` wirkt nur auf **Browser**-Anfragen und koennte den Dienst-Aufruf nicht mehr abfangen. | Der Dienst-Doppelgaenger steht jetzt an der **Dienstgrenze** (`tests/e2e/support/mock-api.mjs`); die Oberflaeche spricht ihn ueber HTTP an — genau wie im Betrieb. Die Tests steuern seinen Zustand ueber `/__control/zustand`. **Drei zusaetzliche Tests mit `javaScriptEnabled: false`** (Pausieren, Filter, Replay). | **AC-019-1…4**, **AC-020-1…4**, **AC-021-1…4** (unveraendert) + **ADR-018 §8** (neu) + **FR-024/O-14** (neu) |
| **T-26** | `tests/e2e/health.spec.ts` | Der Test sprach den Dienst unter `ECHOBOX_API_BASE_URL` (Standard `localhost:8080`) an — im Testlauf laeuft dort kein Dienst. | Der Test spricht den **Dienst-Doppelgaenger** an (ueber `ECHOBOX_MOCK_BASE_URL`); mit gesetzter `ECHOBOX_API_BASE_URL` spricht er den **echten** Dienst an. | **AC-023-1** (Liveness und Readiness) |
| **T-27** | `tests/unit/frontend/support/{actionRunner,token}.ts`, `tests/e2e/support/{harness,mock-api.mjs}` (**neu**) | Die Tests brauchten Hilfen: den Aufruf einer Server Action, ein Token mit Rollen, den Doppelgaenger. | Drei kleine Hilfsmodule. Sie enthalten **keine** Testfaelle. | — |

### 5.3 Die Nachzuege der Tests aus Teil A, B und C (T-1 … T-19)

Unveraendert uebernommen aus den Teilberichten:

| # | Datei | Korrektur | Status |
|---|---|---|---|
| T-1 | `tests/unit/event/PreEncryptedEventTest` | die Messgroesse auf die **Zeichenfolge** umgestellt (`doesNotContain` auf `byte[]` prueft Einzelbytes) | erledigt |
| T-2 | `tests/integration/tenant/TenantIsolationIT` | `queryForList(sql, String.class)` statt `query(sql, rowMapper, String.class)` — der Treiber kann den Typ nicht ableiten | erledigt |
| T-3 | `tests/integration/event/EventIntakeIT` | der Test registriert seinen Abnehmer **selbst** (keine Reihenfolgeabhaengigkeit) | erledigt |
| T-4 | `tests/integration/endpoint/EndpointPauseIT` | gezaehlt werden die Laeufe **dieses** Endpunkts | erledigt |
| T-5 | `tests/integration/endpoint/EndpointVisibilityIT` | geprueft wird nur, dass der **fremde** Endpunkt fehlt | erledigt |
| T-6 | `tests/integration/tenant/TenantIsolationIT` | eigene Ziel-URL; geprueft wird, dass **kein** Endpunkt mit dieser URL angelegt wurde | erledigt |
| T-7 | `tests/integration/tenant/RowLevelSecurityIT` | `contains`/`doesNotContain` statt `containsExactly` | erledigt |
| T-8 (Teil B) | `tests/integration/support/AbstractApplicationIT` (**neu**) | `beginWithEmptyState()` leert die Tabellen vor **jedem** Testfall | erledigt |
| T-9 (Teil B) | `tests/integration/support/RecordingDeliveryPort` | `reset()` leert auch die URL→Status-Zuordnung | erledigt |
| T-10 (Teil B) | `tests/integration/support/AbstractApplicationIT.registerEndpointWithSecret(...)` (**neu**) | eigenes Secret je Endpunkt — sonst sind die Signaturen zwangslaeufig gleich | erledigt |
| T-11 | `tests/integration/delivery/SignedDeliveryIT` | die eingelieferte Nutzlast entspricht byteweise `PAYLOAD` | erledigt |
| T-12 | `tests/integration/delivery/RetryDeliveryIT` | gemessen wird der **geplante** Zeitpunkt (`next_attempt_at`) — AC-011-1 | erledigt |
| T-13 | `tests/integration/delivery/RedeliveryIT` | Vergleich der Versuchszahl **vor** und **nach** dem Worker-Durchlauf | erledigt |
| T-14 | `tests/integration/**` (4 Stellen) | `String.valueOf(...)` statt `(String)` — der Treiber liefert eine `UUID` | erledigt |
| T-15 | `tests/integration/protocol/DeadDeliveryListIT` | der Helfer liefert die **Endpunkt**-ID | erledigt |
| T-16 | `tests/integration/protocol/RetentionEnforcementIT` | Zeitpunkt als `OffsetDateTime` in UTC binden; `deleted_at` als `Timestamp` umwandeln | erledigt |
| T-17 (Teil C) | `tests/unit/frontend/DeliveryProtocolPage.test.tsx` | die Zeitpunkt-Erwartung an den eigenen Fixture-Wert angepasst (`21.10.2026 09:16:24`) | **ueberholt** — die Datei wurde in Teil D neu geschrieben (T-21); die Formatregel `TT.MM.JJJJ HH:MM:SS` (UTC) gilt unveraendert |
| T-18 (Teil C) | `tests/unit/crypto/PayloadCipherTest` | erzeugte Verzeichnisse ausgenommen, Dateien als **Bytes** gelesen (ISO-8859-1) | erledigt |
| T-19 (Teil C) | `tests/integration/deployment/ContainerStartupIT` | dieselbe Korrektur wie T-18 (Teil C) | erledigt |

> **Befund B-16 (Nummerierung) — erledigt in Schritt 7:** die Nummern
> **T-8 bis T-10** wurden in Teil B und in Teil C **jeweils doppelt** vergeben,
> ebenso **B-12** und **B-13**. Die Nummern sind jetzt **einmalig** vergeben;
> die Nummerierung folgt der Reihenfolge der Teile **A → B → C → D**:
>
> | Bereich | Nummern | Verteilung |
> |---|---|---|
> | Teil A | **T-1 … T-7** | unveraendert |
> | Teil B | **T-8 … T-16** | unveraendert |
> | Teil C | **T-17 … T-19** | frueher T-8 … T-10 (Teil C) |
> | Teil D | **T-20 … T-27** | frueher T-17 … T-24 |
>
> Die Befunde **B-17** (Ubergangstabelle des Domänenmodells) und **B-18**
> (Endpunkt-Idempotenzschluessel) sind aus Teil B; **B-12** (`next lint`) und
> **B-13** (npm-Workspace-Wurzel) bleiben die Befunde aus Teil C. Die Reihenfolge
> der Erklaerungen ist unveraendert — nur die Nummern sind eindeutig.

### 5.4 Der Dienst im Browser-Test (genaue Angabe)

Die Browser-Tests brauchen einen **laufenden Dienst**, mit dem die Oberflaeche
spricht. Im Testlauf startet die Playwright-Konfiguration **zwei** Server:

1. den **Dienst-Doppelgaenger** (`tests/e2e/support/mock-api.mjs`, Port 4010) —
   er bedient den **Vertrag**: dieselben Pfade, Felder, Statuscodes und
   Rollenregeln (`F-03`, `V-39`, `E-01`); er ist zustandsbehaftet (Pause,
   Rotation, Replay wirken) und zeichnet die Anfragen auf;
2. die **Oberflaeche** (`next start`, Port 3000) mit
   `ECHOBOX_API_BASE_URL` auf den Doppelgaenger.

**Warum ein Doppelgaenger statt des echten Dienstes?** Der echte Dienst
braucht PostgreSQL 16, ein Schema und ein JWT; das in den Playwright-Lauf
einzubinden waere eine zweite Betriebsstufe im Testlauf. Der Doppelgaenger
steht **an der Dienstgrenze** — die Oberflaeche spricht ihn ueber HTTP an,
genau wie im Betrieb. Der HTTP-Vertrag selbst wird in den Integrationstests
gegen die **echte** Implementierung geprueft (`tests/contract`, 19 Testfaelle,
gruen).

**Wie der Lauf gegen den echten Dienst geht:** `ECHOBOX_API_BASE_URL` auf die
Adresse des laufenden Dienstes setzen — dann startet die Konfiguration
**keinen** Doppelgaenger, und die Tests sprechen den echten Dienst an. Der
Gesundheitstest (`health.spec.ts`) nutzt dann automatisch den echten Dienst.

---

## 6. D4 — die Dateien ausserhalb des Commit-Scopes

Der Commit-Scope von Schritt 6 ist `src/`, `tests/`, `docs/execution/`. Die
**Build-Konfiguration der Oberflaeche** liegt jetzt in `src/main/frontend/`
(`package.json`, `tsconfig.json`, `vitest.config.ts`, `playwright.config.ts`,
`eslint.config.mjs`, `next.config.mjs`, `scripts/check-contract-types.mjs`) —
sie ist damit **im** Scope. `vitest.config.ts` zeigt auf `tests/unit/frontend`,
`playwright.config.ts` auf `tests/e2e` ✓.

**Acht Dateien in der Projektwurzel sind zwingend noetig** und werden als
**dokumentierte Abweichung** mitcommittet:

| # | Datei | Warum sie zwingend noetig ist |
|---|---|---|
| 1 | `pom.xml` | der **Maven-Build des Backends**. `mvn -q verify` und damit alle 368 Backend-Testfaelle (89 Unit, 258 Integration, 19 Vertrag, 2 Last) laufen ohne diese Datei nicht — der Build ist die Grundlage des Schritts. |
| 2 | `package.json` | die **npm-Workspace-Wurzel** (B-13). Die Testdateien unter `tests/` loesen ihre Abhaengigkeiten nur ueber die Workspace-Wurzel auf (die Aufloesung geht vom Verzeichnis der Testdatei nach oben). |
| 3 | `package-lock.json` | die **Versionsdatei** der Workspace-Wurzel — ohne sie ist die Installation nicht reproduzierbar (`npm ci` in Schritt 9). |
| 4 | `tsconfig.json` | die **Weiterleitung des strikten Typchecks**: `npx tsc --noEmit` aus der Projektwurzel prueft die Oberflaeche **und** die Tests unter `tests/` in **einem** Lauf (der Frontend-Ordner allein wuerde die Tests nicht einbeziehen). |
| 5 | `vitest.config.ts` | die **Weiterleitung der Komponententests**: `npx vitest run` aus der Projektwurzel findet die Tests unter `tests/unit/frontend` und **nicht** die Playwright-Spezifikationen unter `tests/e2e`. |
| 6 | `.gitignore` | schliesst `node_modules/`, `target/`, `.next/`, `test-results/`, `playwright-report/`, `*.tsbuildinfo` und `.env` aus. Eine `.gitignore` **im** Unterverzeichnis wuerde die Verzeichnisse der Wurzel nicht erfassen. |
| 7 | `Dockerfile` | der **Container-Build**; `ContainerStartupIT` (4 Testfaelle) prueft ihn (multi-stage, nicht privilegierter Benutzer, kein Secret im Image, Health-Check). |
| 8 | `.github/workflows/deploy.yml` | der **Bereitstellungsdeskriptor**; `EnvironmentSeparationIT` prueft, dass es ihn gibt (getrennte Staging- und Produktions-Jobs). Schritt 9 verfeinert ihn um die Qualitaets-Gates und den Rollout. |

**Ergaenzung (nicht im Scope, aber noetig):** `CHANGELOG.md` — die
Aenderungen dieses Schritts sind dort unter `[Unreleased]` festgehalten. Die
Datei liegt in der Wurzel (wie `pom.xml`); sie wird mitcommittet.

**Was nicht im Scope liegt und nicht zwingend noetig ist**, ist bereinigt:
die Dateien `src/main/frontend/src/lib/auth.ts`,
`src/main/frontend/src/lib/useRoles.ts`,
`src/main/frontend/src/lib/problem.ts` und die drei `use*.ts`-Hooks sind
**geloescht** (sie gehoerten zur Client-Architektur aus Teil C).

---

## 7. Die offenen Punkte und wie sie behandelt wurden

| ID | Thema | Behandlung in Schritt 6 | Stand |
|---|---|---|---|
| **O-13** | Die Seiten waren Client Components (Verstoss gegen ADR-018 §8) | **erledigt** in Teil D: die drei Seiten sind Server Components, die Aktionen sind Server Actions, der Filter ist ein GET-Formular. Nachweis: `ProgressiveEnhancement.test.tsx` (Quelle und Ergebnis) + drei Playwright-Tests mit abgeschaltetem JavaScript. | **erledigt** ✓ |
| **O-14** | Das erneute Ausloesen war fuer den Empfaenger-Admin nicht erreichbar | **erledigt** in Teil D: die Seite liest mit der Operation, die die Identitaet lesen darf (`/deliveries?status=abandoned`), und bietet das Replay nur dem `receiver_admin` des **eigenen** Mandanten an. Nachweis: 6 Unit- und 4 Playwright-Tests. | **erledigt** ✓ |
| **O-15** | Keine CORS-Regel; der Weg lief ueber einen Same-Origin-Rewrite | **geklaert** (siehe 4.1): mit Server Components spricht nur die Oberflaeche den Dienst an — serverseitig. Es braucht **weder** CORS **noch** einen Rewrite im Betriebsfall; der Rewrite bleibt als same-origin-Weg fuer Browser-seitige Aufrufe erhalten. Beleg: die Browser-Tests laufen gegen einen laufenden Dienst ueber die volle Strecke. | **erledigt** ✓ (Schritt 9: nur noch die Betriebsentscheidung „ein Origin oder zwei?“) |
| **B-03** | Flyway-Transaktion der Migration | erledigt in Teil A: `spring.flyway.execute-in-transaction=false`; das kanonische Schema blieb unveraendert. | **erledigt** ✓ |
| **B-10** | Der Bereitstellungsdeskriptor fehlte | erledigt in Teil B (`.github/workflows/deploy.yml`); **Schritt 9** verfeinert ihn um die Qualitaets-Gates und den Rollout. | **offen** (Schritt 9) |
| **B-12** | `next lint` ist ueberholt → Lint als eigener CI-Schritt | **dokumentiert** (siehe 4.2): `npm run lint` laeuft als eigener Schritt; der Build prueft die Regeln nicht selbst. | **offen** (Schritt 9) |
| **B-13** | npm-Workspace-Wurzel fuer die Tests unter `tests/` | **dokumentiert** (siehe 4.3): die Workspace-Wurzel liegt im Projekt-Root; `npm ci` laeuft dort. Wer nur das Backend baut, braucht kein Node. | **offen** (Schritt 9) — die Entscheidung selbst steht |
| **B-16** | Die Nummern T-8…T-10 und B-12/B-13 sind in den Teilberichten doppelt vergeben | **erledigt** in Schritt 7: die Nummern sind einmalig vergeben (T-1…T-7 = Teil A, T-8…T-16 = Teil B, T-17…T-19 = Teil C, T-20…T-27 = Teil D; B-17/B-18 = Teil B). Die Verweise in diesem Bericht und in den drei Teilberichten sind korrigiert. | **erledigt** ✓ |
| **B-17** | Die Uebergangstabelle des Domänenmodells nennt nur `failed → delayed`; das Endpunkt-Limit wirkt aber auf jeden offenen Zustand | **dokumentiert** in Teil B: die Implementierung erlaubt den Uebergang von jedem offenen Zustand (`DeliveryStateMachine.onRateLimited`) — Vertrag und ADR-010 nennen den Zustand `delayed` ohne Einschraenkung. **Kein Vertragsdefekt.** | **offen** (Architektur, Schritt 8) |
| **B-18** | `endpoint_event_types` hat keinen eindeutigen Schluessel ueber `(endpoint_id, event_type)` **je Mandant** | **dokumentiert** in Teil B: `endpoint_id` ist weltweit eindeutig, damit gibt es keine Mehrfachregistrierung desselben Ereignistyps je Endpunkt. Der Vertrag laesst die Frage offen. | **offen** (Gate 2) |
| **O-04 / O-05 / R-01 / E-12** | Verschluesselung im Ruhezustand als Pflicht, AVV bei Fremddaten, Karenzzeit der Aufbewahrung, Haertung der mandantenuebergreifenden Sicht | unveraendert — fachliche Entscheidungen des Fachbereichs | **offen** (Gate 2) |

**Kein Vertragsdefekt wurde gefunden.** Der Vertrag
`docs/api/openapi.yaml` wurde **nicht** geaendert; `docs/requirements/`,
`docs/api/`, `docs/adr/`, `docs/architecture/` und `.agent/knowledge/` sind
unveraendert.

---

## 8. Die Bedienung in einem Satz

Anmelden (Token → httpOnly-Cookie); Endpunkte anlegen, pausieren,
reaktivieren, das Secret rotieren; das Zustellprotokoll nach Endpunkt, Status
und Zeitraum filtern und blättern; die toten Zustellungen sehen und — als
Empfaenger-Admin des eigenen Mandanten — erneut ausloesen: alles ueber native
Bedienelemente, echte Tabellen und einen Text, den auch ein Screenreader
vorliest — **ohne JavaScript**.

---

## 9. Sicherheitshinweise

- **Keine Secrets in Code, Konfiguration oder Image.** Der
  Verschluesselungsschluessel kommt aus `ECHobox_CRYPTO_KEY`; ohne ihn erzeugt
  der Dienst einen ephemeren Schluessel und warnt (im Staging ist ein Start
  ohne Schluessel ein Fehler).
- **Das Token liegt in einem httpOnly-Cookie** (nicht im `localStorage`) und
  wird nur **serverseitig** verwendet — der Browser kann es nicht auslesen.
- **Die Nutzlast erscheint in keinem Log.** Der `DeliveryDispatcher`
  protokolliert ausschliesslich Metadaten; das Endpunkt-Secret wird nur im
  Worker entschluesselt, unmittelbar vor der Signatur, und nie ausgegeben.
- **Die mandantenuebergreifende Sicht** besteht ausschliesslich aus den drei
  Eigentuemerfunktionen des Schemas; es gibt keinen vierten Leseweg und kein
  `BYPASSRLS`.
- **Die Rueckmeldung einer Aktion traegt nur einen Code** in der URL — kein
  fremder Inhalt wird in die Seite zurueckgespiegelt.
- **Der Replay** erhaelt eine Sperrenwartezeit und ein Statement-Timeout, die
  kuerzer sind als das HTTP-Timeout — ein Replay-DDoS kann den Dienst nicht
  festhalten.
- **Kein Schluessel im Client-Bundle**: die Server Actions laufen auf dem
  Server; der Browser kennt weder die Dienstadresse noch das Token.
