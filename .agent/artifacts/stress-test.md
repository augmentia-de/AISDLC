# Stresstest-Bericht — Schritt 4 (Grill-Me-Stresstest), **zweite Runde**

> Schritt 4 · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Skill: `aisdlc-grill-stress` · Eingang: Schritt 3, Commit `05bceca`
> (davor Schritt 2 **nach dem Rücksprung** `9bb9bf4`, Schritt 1 `cc67445`,
> erster Stresstest `bdb03d3`)
> Gelesen: `docs/api/openapi.yaml`, `docs/architecture/*`, `docs/adr/*`,
> `docs/requirements/*`, `.agent/knowledge/schema.sql`, `.agent/artifacts/*`
>
> **Dieser Bericht ersetzt den ersten** (Commit `bdb03d3`). Der erste Bericht
> beschreibt einen überholten Stand; seine Befunde sind hier **nachgeprüft**
> (§ 1) und die **neuen** Befunde stehen in § 3 und § 6.

---

## 0. Kurzfassung

### Ampel: 🟢 **einfrierreif** (mit drei Schema-Nachzügen für Schritt 2)

**Der Vertrag kann eingefroren werden.** Die 26 Befunde des ersten
Stresstests sind **am aktuellen Stand nachweisbar behoben**; die neue Runde
findet **keinen** offenen „muss"-Befund **gegen den Vertrag**. Acht neue
Befunde betreffen das **Schema**, die **ADRs** und die **Diagramme** — drei
davon („muss") sind **Korrekturen auf den Vertrag hin**: der Vertrag beschreibt
die gewünschte Semantik, das Schema weicht davon ab. Sie ändern den Vertrag
**nicht** und sind deshalb **kein** Freeze-Blocker, aber sie müssen **vor
Schritt 5** in Schritt 2 nachgezogen werden (Schema-only).

| Umfang | Befunde | Behoben | Offen |
|---|---|---|---|
| **Nachprüfung** (V-01…V-22, S-01, N-01, N-02, N-08) | 26 | **26** | 0 |
| **Neue Befunde** (N-13…N-20) | 8 | 0 | **8** — davon 3 „muss" (Schema), 5 „sollte" |
| **Neue Vertragsdefekte** (V-23…V-25) | 3 | 0 | **3** — alle „sollte", keiner „muss" |
| **Korrekturen in `docs/architecture/`** | 4 | **4** (in diesem Schritt) | 0 |

### Freeze-Empfehlung — **ausdrücklich**

> **Der Vertrag `docs/api/openapi.yaml` kann eingefroren werden.**
>
> **Ab welchem Commit:** der Vertrag gilt ab dem Commit dieses Schritts
> (`docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen`) als
> **eingefroren** — ab da ist jede Änderung ein Rücksprung zu Schritt 2
> (AGENTS.md, `.agent/PHASES.md` → „Einfrierpunkt des Vertrags: nach Schritt 4").
>
> **Begründung:** 26 von 26 Nachprüfungen sind am aktuellen Stand belegt
> (22 Vertragsdefekte, der Architekturbruch S-01, die drei Standards-Befunde
> N-01/N-02/N-08) — 20 davon allein am **Vertrag** und am **Schema**, S-01
> zusätzlich **gegen eine echte PostgreSQL-16-Instanz** (Abschnitt 1.2). Die
> neue Runde hat **8** neue Befunde und **3** neue Vertragsdefekte gefunden —
> **keiner** davon ist ein „muss" gegen den Vertrag; die drei Vertragsdefekte
> sind „sollte" und betreffen je **einen Satz** (Fundstellen in § 6).
>
> **Auflage (kein Freeze-Blocker, aber vor Schritt 5):** die drei „muss"-Befunde
> N-13, N-14 und N-15 sind **Schema-Korrekturen** (Schritt 2, **ohne**
> Vertragsänderung): das Schema setzt drei Vertragszusagen derzeit nicht um
> (markierte Versuche unsichtbar, Totenlisten-Projektion, Replay-Historie).
> Da der Vertrag die **Zielsemantik** ist, wird er **vor** der
> Schema-Nachführung eingefroren — nicht danach.

**Was sich seit dem ersten Bericht geändert hat:** Der Rücksprung hat
**22 Vertragsdefekte**, den **Architekturbruch S-01** und die drei
**Standards-Befunde** behoben (Commit `9bb9bf4`), Schritt 1 hat das PRD
nachgezogen (`cc67445`), Schritt 3 hat R-03/R-04/R-05 und den `enabled`-Zustand
bearbeitet (`05bceca`). Offen bleiben **O-04**, **O-05** und **R-01**.

**Neu in dieser Runde — die acht Befunde in einem Satz:**
N-13 markierte Versuche bleiben sichtbar und zählen in den Kennzahlen ·
N-14 die Totenlisten-Funktion ist breiter als die Vertragsprojektion (fail-open)
· N-15 die Replay-Historie verschwindet, wenn der Vorgängerlauf aufbewahrt wird ·
N-16 markieren ist für die Applikationsrolle nicht direkt möglich ·
N-17 die mandantenübergreifenden Listen haben keinen stützenden Index ·
N-18 ADR-002 nennt die neue Grenze (Applikationsrolle + `app.cross_tenant`) nicht ·
N-19 ADR-015 verspricht ein mandantenübergreifendes Protokoll, das es nicht gibt ·
N-20 der konkurrierende zweite Replay wartet auf die Sperre des ersten.

---

## 1. Nachprüfung — sind die Korrekturen **wirklich** drin?

Eine Korrektur, die nicht nachweisbar ist, ist kein Fix. Geprüft wurde am
aktuellen Stand (`docs/api/openapi.yaml`, `.agent/knowledge/schema.sql`,
`docs/adr/`, `docs/architecture/`), nicht am Protokoll des Rücksprungs.

### 1.1 Die 22 Vertragsdefekte (V-01…V-22)

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **V-01** | 🟢 behoben | `openapi.yaml:68` | „Antwort unter `page.next_cursor` / `page.has_more`" — deckt sich mit `components.schemas.Page` (`:3109-3122`). |
| **V-02** | 🟢 behoben | `:1456-1477` (Anker, zwei Stufen, Karenzzeit, Kaskade, Geltungsbereich), `:2874-2887` (`purge_delay_days`, nullable, „Ein Vorgabewert ist nicht festgelegt"), `:2923-2956` (`marked_*` / `purged_*`), `:2969-3064` (`TenantRetention` mit `*_retention_source`) | Alle fünf Teile des Befunds sind getrennt ausgewiesen; „gelöscht“ ist über `marked_*`/`purged_*` **entscheidbar**. |
| **V-03** | 🟢 behoben | `:118-126` (zweistufige Auskunft), `:2762-2804` (`RateLimitObservation.limit` ohne `nullable`, `limited` Pflicht), `:425-465` (`states: kein_override / abgeschaltet`), Beispiel `:1424-1430` (`limit: 60, limited: true`) | „Kein Override“ (⇒ 60) und „abgeschaltet“ (⇒ 0) sind **unterscheidbar**; der Standardfall ist nicht mehr „sieht aus wie aus“. |
| **V-04** | 🟢 behoben | `:255-273` (Body = Nutzlastbytes, kanonisch materialisiert bzw. exakt geliefert), `:2276-2285` (`EventIntakeRequest.payload`) | Die Zusage lautet jetzt „identisch mit dem, **was das Ereignis trägt**“ — nicht mit dem Rohtext des Aufrufers. |
| **V-05** | 🟢 behoben | `:2430-2437` (`replay_of`, nullable, „null, wenn der Lauf kein Replay ist“) | Feld vorhanden und beschrieben; in beiden Zustelllauf-Beispielen sichtbar (`:1076`, `:1138`). |
| **V-06** | 🟢 behoben | `:1348` (Beispiel `status: pending`), `:2013-2023` (`DeliveryStatus`) | `pending` = „angelegt, noch kein Versuch — auch nach einem Replay“; `scheduled` = „ein Versuch läuft gerade“. Deckt sich mit `schema.sql:202` (DEFAULT `'pending'`). |
| **V-07** | 🟢 behoben | `:995-997` (Liste `listDeliveries`), `:2477-2481` (`DeliveryRun.next_attempt_at`) | `no_subscriber` steht in **beiden** Endgültigkeits-Listen; deckt sich mit `delivery_runs_terminal_no_next_attempt_ck` (`schema.sql:229-231`). |
| **V-08** | 🟢 behoben | `:2363` (`payload_media_type` in `required`), `:2377-2382` („immer gefüllt“) | `nullable` entfernt, Pflichtfeld ergänzt; deckt sich mit `schema.sql:160` (`NOT NULL DEFAULT 'application/json'`). |
| **V-09** | 🟢 behoben | 15 von 15 authentifizierten Operationen mit `"429"` (maschinell geprüft, § 9), `:1609-1610` (503 über die Komponente `ServiceUnavailable`), `:1851-1866`, `:1880-1892` | Keine tote Komponente mehr; **alle** 15 authentifizierten Operationen nennen die 429. |
| **V-10** | 🟢 behoben | `:1804-1850` (422 mit **zwei** Beispielen), `:1968-1997` (`ValidationFailedProblem`, `TenantParameterRejected`) | Der Code `tenant_parameter_rejected` ist erzeugbar und dokumentiert; die Fehlerform bleibt **ein** Schema (`oneOf` über zwei `allOf` auf `Problem`). |
| **V-11** | 🟢 behoben | `:58-63` (400/422-Grenze), `:1805-1810` (Komponente), `no_tenant_parameter` in **allen 6** Request-Bodies: `:2116`, `:2140`, `:2221`, `:2318`, `:2677`, `:2819` | Kein Request-Body ohne die Regel mehr. |
| **V-12** | 🟢 behoben | `:347-357` (Zeile 3xx in der Ergebnismatrix), `:2055-2056` (`client_error` erfasst 3xx) | „Kein Wiederholen, `rejected`, `rejection_reason = client_error`, Redirects werden nicht befolgt“ — steht im **ausgehenden** Vertrag. |
| **V-13** | 🟢 behoben | `:2047-2064` (`AttemptOutcome`), `:358-373` (Trennlinie im Transportfehler-Block) | `timeout` = Connect-/Lese-Timeout, `network_error` = DNS/TLS/Verbindung **ohne** Timeout. |
| **V-14** | 🟢 behoben | `:1316-1319` | „Ist der Endpunkt pausiert … HTTP 409 `delivery_state_conflict`; es entsteht **kein** neuer Zustelllauf.“ |
| **V-15** | 🟢 behoben | `:1067-1087` und `:1130-1166` (beide `attempt_count: 2`, zwei Versuche, `next_attempt_at` = letzter Versuch + **2 min**), `:912`/`:966`/`:2351` (`payload_size_bytes: 35` = kanonische Bytes), `:520` (`organisation_id: null`) | Die Beispiele widersprechen sich nicht mehr; der Backoff ist rechnerisch konsistent (2 min ± 20 %). |
| **V-16** | 🟢 behoben | `:408-415` (`expected_completion_window`: 7 Wartezeiten = 123 min ≈ 2 h, ± 20 % ⇒ 1 h 38 – 2 h 28) | Die Zusage steht jetzt dort, wo die Empfänger sie lesen; `ADR-008:199` nennt dieselbe Zahl. |
| **V-17** | 🟢 behoben | `:1668-1688` (Parameter `From`/`To`), `:1390-1391` (Beschreibung der Kennzahlen) | „Ohne `from`/`to` gilt das Fenster der **letzten 7 Tage** bis jetzt“ — an beiden Stellen. |
| **V-18** | 🟢 behoben | `:1306-1310` | „Ein aufgegebener Zustelllauf kann **einmal** replayed werden … ein **weiterer** Replay ⇒ HTTP 409“; auf Datenbankebene erzwiesen (`schema.sql:240-241`). |
| **V-19** | 🟢 behoben | `:858-862` (Request-Body bis 2 MiB), `:2316-2317` (Validierungsregel), `:2286-2295` (`payload_base64`, ~1,4×) | An allen drei Stellen gesagt. |
| **V-20** | 🟢 behoben | `:187` | `value: "<payload_media_type des Ereignisses> (Vorgabe: application/json)"` — Wert und Beschreibung stimmen überein. |
| **V-21** | 🟢 behoben | `:2066-2074` | `enum: [permanent_rejection, client_error]`, `nullable: true` — kein Nullwert im Enum. |
| **V-22** | 🟢 behoben | `:2276-2277` (`nullable: false`), `:2247-2250` und `:2311-2313` (Regel: „`payload: null` gilt als **nicht** gesetzt“) | *Anmerkung:* `nullable: false` ohne `type` ist in OpenAPI 3.0 formal ein **No-Op**; die Entscheidbarkeit trägt die ausformulierte Regel in `x-validation-rules` — für die Implementierung eindeutig, für strenge Validatoren auffällig (kein Mangel). |

### 1.2 S-01 — der Architekturbruch (E-01)

| | |
|---|---|
| **Status** | 🟢 **behoben** |
| **Fundstelle** | `schema.sql:479-502` (Regelwerk), `:505-559` (`list_endpoints_cross_tenant`), `:568-629` (`list_dead_deliveries_cross_tenant`), `:655-822` (`enforce_retention_cross_tenant`), `:1021-1026` (`REVOKE … FROM PUBLIC`), `:1037-1039` (`GRANT` nur an `echobox_app`); `ADR-002:49-83` (§ 7), `:100-107` (Grenze); Vertrag `:555-559`, `:1201-1205`, `:1452-1454`, `:3290`; `folder-structure.md:125` (`CrossTenantScope`) |
| **Nachweis** | **erneut gegen eine echte PostgreSQL-16-Instanz** geführt (16.15, Unix-Socket, Trust-Auth, frische Datenbank). Die Ergebnisse in § 1.3. |

#### 1.3 PostgreSQL-16-Nachweis (erneut, 2026-10-21)

Die vier Fragen aus dem Auftrag — je mit dem **gemessenen** Ergebnis:

| Frage | Prüfung | Ergebnis |
|---|---|---|
| **Existieren die drei Funktionen?** | `pg_proc` / `pg_namespace` | **3 von 3**: `list_endpoints_cross_tenant`, `list_dead_deliveries_cross_tenant`, `enforce_retention_cross_tenant` — alle `prosecdef = t`, `proconfig = {"search_path=app, pg_temp"}`, Eigentümer `echobox_owner`. |
| **`search_path` fest verdrahtet?** | `proconfig` | **ja** — bei allen drei `app, pg_temp` (fest verdrahtet, nicht per Aufrufparameter). |
| **`REVOKE`/`GRANT` gesetzt?** | `has_function_privilege` | `public_may = **f**` (REVOKE), `app_may = **t**` (GRANT), `worker_may = **f**` — der Worker ist **nicht** freigegeben. |
| **Rolle mit `BYPASSRLS`?** | `pg_roles` | `echobox_owner`, `echobox_app`, `echobox_worker`: `rolsuper = f`, `rolbypassrls = f`, `rolcanlogin = f`. **Keine** Rolle umgeht die RLS; `FORCE ROW LEVEL SECURITY` ist bewusst nicht gesetzt (10/10 Tabellen mit RLS, je 1 Policy). |
| **Projektionen ohne Nutzlastklartext und ohne Secrets?** | Rückgabe gegen die Klartextwerte geprüft | **0 Treffer** auf `deadbeef`, `c0ffee` (Chiffre/Nonce des Endpunkt-Secrets), `cafe`, `f00d` (Chiffre/Nonce der Nutzlast), `order_id`, `4711`. Die Projektionen enthalten nur Vertragsfelder (`secret_version` = Rotationszähler, `target_url`, `event_types`, Zeitstempel). |
| **Fail-closed: 0 Zeilen ohne Kontext?** | `echobox_app` **ohne** `set_tenant` | `endpoints` **0**, `events` **0**, `delivery_runs` **0**, `delivery_attempts` **0**; alle drei Eigentümerfunktionen **0 Zeilen** ohne `app.cross_tenant`. |
| **Normaler Mandant sieht keine fremden Zeilen?** | `set_tenant('m-1')`, zwei Mandanten | `endpoints`: **1** (nur m-1), `events` mit `organisation_id = 'm-2'`: **0**, `delivery_runs` von m-2: **0**. |
| **`service_admin` sieht mandantenübergreifend, aber nichts weiter?** | `set_config('app.cross_tenant','service_admin', true)` | Endpunktliste: **2 Zeilen** (m-1 **und** m-2), je mit `organisation_id`; Totenliste: beide Mandanten; Aufbewahrung: je Mandant eine Zeile mit den **angewandten** Fristen. Nichts weiter: kein Secret, keine Chiffre, kein Nutzlastinhalt, keine Fremdzeile ohne Mandantenbezug. |

**Weitere Prüfungen, die die Korrekturen bestätigen:**

| Prüfung | Ergebnis |
|---|---|
| `app.endpoint_rate_limits` | Spalte `enabled` **entfallen** (7 Spalten), `limit_value` nullable **ohne** `DEFAULT`, `window_seconds DEFAULT 60`, `strategy DEFAULT 'delay'` — E-02 ✓ |
| `app.delivery_runs` | `delivery_runs_replay_of_uq UNIQUE (replay_of)` vorhanden ✓; `replay_of_fk` mit `ON DELETE SET NULL` ⚠️ → **neuer Befund N-15** |
| Erstes Replay auf einen `abandoned`-Lauf | **erlaubt** ✓ |
| Zweites Replay desselben Laufs | **`unique_violation` (SQLSTATE 23505)** ⇒ HTTP 409 `delivery_state_conflict` ✓ |
| Mehrere Läufe mit `replay_of IS NULL` | **erlaubt** (NULLs sind in einem UNIQUE-Index paarweise verschieden) ✓ — die Constraint blockiert normale Läufe nicht |
| `dry_run = true` | zählt, **ändert nichts** ✓ (`schema.sql:788` — der Schreibblock liegt hinter `IF NOT p_dry_run`) |
| Aufbewahrung 30 Tage, m-1 (40 Tage alt) / m-2 (5 Tage alt) | m-1: **1 Ereignis markiert, 2 Läufe markiert** (Kaskade), 1 Versuch gezählt; m-2: **nichts** ✓ |
| Schema anwendbar | `psql -f .agent/knowledge/schema.sql` auf eine frische Datenbank: **ohne Fehler** ✓ |
| `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** ✓ |

### 1.4 Die drei Standards-Befunde

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **N-01** (Mandantenkontext an die **Transaktion**, nicht an die Verbindung) | 🟢 behoben | `ADR-002:27-38` (§ 3), `schema.sql:25-32` (Kopfkommentar), `:425-436` (`app.set_tenant`), `folder-structure.md:123` (Klassenbeschreibung), `:190` (Konvention „Mandant“), `diagrams/03:12`, `architecture.md:197` | Der Kontext ist **überall** transaktionsgebunden: „innerhalb der Transaktion, die auch liest, immer vor der ersten Abfrage; kein Autocommit, kein `REQUIRES_NEW`“. **Kein Rest auf „Connection-Initializer“-Semantik** — der Klassenname bleibt nur historisch (E-05, ausdrücklich so entschieden). *Restbefund:* der **Vertrag** nennt in seiner Abdeckungstabelle noch den „Connection-Initializer“ (→ **V-23**). |
| **N-02** (Worker beansprucht ohne Mandantenermittlung) | 🟢 behoben | `ADR-006:45-56` (§ 3, mit der Kette und dem SQL), `schema.sql:41-43` (Kopfkommentar), `:454-463` (`app.tenant_ids_for_worker`), `architecture.md:198`, `folder-structure.md:85`, `diagrams/04:9-11` + `:26-29` | Die Kette steht **überall gleich**: Scheduler → `app.tenant_ids_for_worker()` → je Mandant `set_tenant` → Claim; **je Mandant eine eigene Transaktion**. *Restbefund:* `diagrams/05:35` zeigte die Beanspruchung noch ohne den Mandantenschritt (→ **N-21**, in diesem Schritt korrigiert). |
| **N-08** (Schema-Kommentare vor F-01/F-02) | 🟢 behoben | `schema.sql:15-19` (Kopf), `:290-295` (Tabelle), `:327-333` (Tabelle + Spalte), `:350-352` (Zähler), `:368-372` (`retention_settings`), `:425-436`, `:454-463`, `:479-502` | Alle Kommentare nennen den **entschiedenen** Stand: `NULL` = **kein Override** ⇒ es gilt die Vorgabe (60/min bzw. 30 Tage). Kein Kommentar behauptet mehr „vorbelegen, solange O-01/O-02 offen“. |

**Nachprüfung gesamt: 26 von 26 Befunden am aktuellen Stand belegt behoben.**

---

## 2. Designbaum — jeder Ast besucht (zweite Runde)

Die Wurzel ist unverändert die Absicht: *Ein zentraler Dienst nimmt Ereignisse
entgegen, stellt sie signiert und at-least-once an registrierte Empfänger-URLs
weiter und hält ein nachvollziehbares Protokoll über jeden Versuch.* Jeder Ast
wurde erneut geprüft; die neuen Befunde sind **fett** markiert.

```
Wurzel: EchoBox — Ereignis-Briefkasten mit Zustellgarantie
│
├── A. ANNAHME (POST /api/v1/events)                        geprüft, kein neuer Befund
│   ├── A1 Mandant & Identität (D-01)                       → V-23 (Vertragstext), sonst ok
│   ├── A2 Idempotenz (Mandant, Schlüssel)                  → geprüft, ok
│   ├── A3 Größengrenze 1 MB & Kanonisierung               → geprüft, ok (V-04/V-19 behoben)
│   ├── A4 Verschlüsselung im Ruhezustand (FR-012)         → geprüft, ok (O-04 fachlich offen)
│   └── A5 Transaktionales Outbox (Ereignis + Läufe)        → geprüft, ok
│
├── B. ZUSTELLUNG (Worker)                                   geprüft
│   ├── B1 Beanspruchung: SKIP LOCKED + Lease               → N-20 (Sperrenwait), N-21 (Diagramm)
│   ├── B2 Zustandsautomat (9 Zustände)                     → geprüft, ok (V-06 behoben)
│   ├── B3 Backoff & Streuung (F-04)                        → geprüft, ok (V-16 behoben)
│   ├── B4 Signatur & drei Kennungen (ADR-004/005)          → geprüft, ok
│   ├── B5 Ablehnungssignal 410 + 3xx                        → geprüft, ok (V-12/V-13 behoben)
│   ├── B6 Endpunkt-Begrenzung (F-01)                       → geprüft, ok (V-03 behoben, `enabled` entfallen)
│   └── B7 RLS im Worker (mandantenweise)                    → N-21 (Sequenzdiagramm), sonst ok
│
├── C. PROTOKOLL & BETRIEB                                    geprüft
│   ├── C1 Zustellprotokoll (Metadaten, Cursor)             → V-25 (Feld `organisation_id`)
│   ├── C2 Tote Zustellungen (mandantenübergreifend)         → N-14 (Funktion breiter als die Projektion)
│   ├── C3 Kennzahlen (Erfolgsquote, p95)                   → N-13 (markierte Versuche zählen mit)
│   ├── C4 Aufbewahrung (zwei Fristen, F-02)                → N-13, N-15, N-16, N-17
│   └── C5 Health / Readiness                                → geprüft, ok
│
├── D. MANDANT & ROLLEN                                       geprüft
│   ├── D1 RLS fail-closed                                   → geprüft, ok (erneut gemessen)
│   ├── D2 Rollenmatrix (F-03)                               → N-19 (ADR-015 verspricht mehr als der Vertrag)
│   └── D3 service_admin mandantenübergreifend               → N-18 (neue Grenze nicht benannt)
│
├── E. OBERFLÄCHE                                             geprüft, kein neuer Befund
│   ├── E1 Drei Seiten, Datenquellen                         → geprüft, ok
│   ├── E2 Barrierefreiheit als Bauregel (ADR-018)           → geprüft, ok
│   └── E3 Fehlerdarstellung (Problem.violations)            → geprüft, ok (V-10/V-11 behoben)
│
├── F. BETRIEB & CONTAINER                                    geprüft, kein neuer Befund
│   ├── F1 Zwei Rollen, ein Image (ADR-013)                  → geprüft, ok
│   ├── F2 Konfiguration statt verdrahteter Zahlen           → geprüft, ok (R-01 bleibt offen)
│   └── F3 Instanz je Mandant vorbereitet                    → geprüft, ok
│
└── G. QUERVERTRAG & GOVERNANCE                               geprüft
    ├── G1 Ein Ort pro Wahrheit (ADR-016)                    → geprüft, ok
    ├── G2 Glossar als einziger Ort je Begriff               → geprüft, ok
    └── G3 Traceability Anforderung→Story→AK→Feature→Test    → geprüft, ok (32 AKs ohne Szenario: Schritt 5)
```

**Am Rand des Baums** (erneut geprüft): der **Vertrag** (17 Operationen, 176
Verweise, 37 Schemas, alle referenziert), das **Schema** (10 Tabellen, 10
Policies, 33 Indizes, 7 Funktionen, 3 Cross-Tenant-Funktionen), die **17
ADRs**, die **23 Features** (69 Szenarien), die **Traceability-Matrix** und die
**3 Artefakte** in `.agent/knowledge/`.

**Frontier leer.** Die verbleibenden offenen Punkte sind Entscheidungen des
Menschen (§ 7) — nicht Architekturfragen.

---

## 3. Achse 1 — Standards

*Axe: Repo-Konventionen, Benennung, Struktur, Konsistenz zwischen
`docs/architecture/` ↔ `docs/adr/` ↔ `.agent/artifacts/` ↔ `.agent/knowledge/`,
tote Artefakte, Implementierbarkeit.*

### N-13 — Markierte Versuche bleiben sichtbar und zählen in den Kennzahlen — **muss** (Schema)

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:256-283` (Tabelle `delivery_attempts` — **keine** Spalte `deleted_at`), `:870-875` (Policy: `USING (organisation_id = current_setting(...))` — **kein** Bezug zum Zustand des Vaters), `docs/architecture/GLOSSARY.md:205-206` („`deleted_at` markiert die betroffenen Zeilen (sie sind **sofort unsichtbar**)“) |
| **Problem** | Die Aufbewahrung hat zwei Stufen: **markieren** (`deleted_at` setzen → sofort unsichtbar) und **Purge** (physisch löschen). Für `events` und `delivery_runs` stimmt das: deren Policies enthalten `AND deleted_at IS NULL` (`schema.sql:861-871`). Für `delivery_attempts` gibt es **weder** eine `deleted_at`-Spalte **noch** einen Policy-Filter auf den Zustand des Vaters. Ein markierter Lauf ist unsichtbar — **seine Versuche sind es nicht**. |
| **Messung** | Nach dem Markieren (über `enforce_retention_cross_tenant`, `dry_run=false`): Lauf sichtbar → **0** Zeilen; Versuch sichtbar → **1** Zeile; Kennzahl „Versuche je Endpunkt“ → **1**; Kennzahl „erfolgreiche Versuche“ → **1**. Der markierte Versuch zählt also **weiter** in der Erfolgsquote und im p95. |
| **Auswirkung** | Die Vertragszusage „Versuche, die mit ihrem Zustelllauf markiert wurden — sie verlieren mit ihm die Sichtbarkeit“ (`openapi.yaml:2937-2939`) und die ADR-Zusage („sofort unsichtbar“, `ADR-012:58`) sind **nicht** erfüllt. FR-032/AC-017-1…4 weisen Zahlen aus, die aufbewahrte Datensätze enthalten. Bis zum Purge (Karenzzeit, R-01) bleiben die Versuchzeilen auffindbar. |
| **Lösung** | **Schema (Schritt 2):** `delivery_attempts` eine `deleted_at`-Spalte geben **oder** die Policy auf den Zustand des Vaters beziehen (`delivery_run_id` → `delivery_runs.deleted_at IS NULL`); beide Stufen (Markieren und Purge) im selben Durchlauf nachziehen. Der **Vertrag bleibt unverändert** — er beschreibt die gewünschte Semantik. |
| **Empfehlungsstärke** | **muss** (Schema) — nicht am Vertrag |
| **Behandlung** | **Dokumentiert, nicht geändert** (Schema = Schritt-2-Eigentum, außerhalb des Commit-Scopes dieses Schritts). Gehört zum Rücksprung **vor Schritt 5**. |

### N-14 — Die Totenlisten-Funktion ist **breiter** als die Vertragsprojektion (fail-open) — **muss** (Schema)

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:568-629` (`list_dead_deliveries_cross_tenant`), besonders `:619` (`AND (p_status IS NULL OR d.status = p_status)`) und `:622-623` (Zeitraumfilter auf `COALESCE(abandoned_at, created_at)`); Vertrag `:2035-2045` (`DeadDeliveryStatus` — **nur** `abandoned`), `:2602-2609` (`abandoned_at` **Pflicht**), `:2646-2652` („**Immer** gefüllt“) |
| **Problem** | Der Statusfilter der Funktion ist **optional**: ohne Wert liefert sie **alle** Zustände — auch `sent`, auch `cancelled`. Ihre Projektion entspricht dann **nicht** der Vertragsprojektion `DeadDelivery`: `abandoned_at` wäre `NULL` (im Vertrag Pflicht), `status` wäre ein Wert außerhalb des Enums. Der Rücksprung-Bilanz und der Reduktionsbericht haben die Pflicht „die Anwendung setzt `abandoned` **immer**“ erkannt und als **Auflage für Schritt 6** notiert — in einer `SECURITY DEFINER`-Funktion, die die RLS umgeht, ist eine vergessene Parameterbindung aber ein **Datenabfluss**, kein Formfehler. |
| **Messung** | `list_dead_deliveries_cross_tenant()` **ohne** `p_status` lieferte **einen `sent`-Lauf** (`abandoned_at = NULL`) neben den zwei `abandoned`-Läufen. |
| **Auswirkung** | Der `service_admin` sähe bei vergessener Parameterbindung das **vollständige Zustellprotokoll aller Mandanten** statt der Totenliste — eine Verletzung der Datenminimierung (ADR-002 §7: „nur die jeweils benötigte Projektion“) und ein Vertragsbruch. |
| **Lösung** | **Schema (Schritt 2):** den Status **fest verdrahten** (`AND d.status = 'abandoned'`) statt ihn zu parametrisieren, oder den Parameter `NOT NULL` mit Vorgabe `'abandoned'`; zusätzlich `abandoned_at` in der Projektion auf `NOT NULL` festziehen. **Der Vertrag bleibt unverändert** — er ist die Zielsemantik. |
| **Empfehlungsstärke** | **muss** (Schema) |
| **Behandlung** | **Dokumentiert, nicht geändert.** Die **Anwendungspflicht** („`abandoned` immer setzen“) steht jetzt in `docs/architecture/folder-structure.md:113` (in diesem Schritt ergänzt) — das ist die **anwendungsseitige** Absicherung; die **schema-seitige** Absicherung fehlt noch. |

### N-15 — Die Replay-Historie verschwindet, wenn der Vorgängerlauf aufbewahrt wird — **muss** (Schema)

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:238-241` (`delivery_runs_replay_of_fk` mit `ON DELETE SET NULL`), `:248-251` (Spaltenkommentar), Vertrag `:2430-2437` (`DeliveryRun.replay_of`: „Die **Replay-Historie bleibt am Lauf sichtbar.“`), `:2684-2687` (`ReplayResponse.previous_delivery_run_id`), ADR-006 §6 (`:98-112`), ADR-015 §6 (`:123-131`), AC-013-2 |
| **Problem** | `replay_of` ist ein ** Fremdschlüssel mit `ON DELETE SET NULL`**. Wird der Vorgängerlauf aufbewahrt (Purge), setzt die Datenbank `replay_of` des überlebenden Replays auf `NULL` — der Replay-Lauf ist damit **nicht mehr als Replay erkennbar**. |
| **Messung** | Nach dem Löschen des Vorgängerlaufs: `replay_of` des überlebenden Laufs = `NULL` (vorher: der Vorgängerlauf). |
| **Auswirkung** | AC-013-2 („die Historie bleibt erhalten“) ist für überlebende Replays nicht mehr einsehbar; ein Client kann einen Replay nicht von einem Erstlauf unterscheiden. Der Vorgängerlauf (40 Tage alt) wird **früher** aufbewahrt als der jüngere Replay (30 Tage später markiert) — das Fenster, in dem die Historie fehlt, ist real, nicht theoretisch. Zusätzlich: `ON DELETE SET NULL` löst den UNIQUE-Eintrag des Vorgängers auf — die Einmal-Regel (E-09) hängt dann an einem Lauf, den es nicht mehr gibt. |
| **Lösung** | **Schema (Schritt 2):** `replay_of` **ohne** Fremdschlüssel führen (die UUID ist ein **historischer Verweis**, kein Live-Bezug) oder den FK auf `ON DELETE NO ACTION` umstellen und die Löschreihenfolge (Lauf vor Ereignis) einhalten. Der **Vertrag bleibt unverändert** — die Zusage ist richtig und soll gelten. |
| **Empfehlungsstärke** | **muss** (Schema) |
| **Behandlung** | **Dokumentiert, nicht geändert.** |

### N-16 — Markieren ist für die Applikationsrolle **nicht direkt** möglich (RLS-Falle) — **sollte**

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:860-875` (Policies mit `AND deleted_at IS NULL` im `USING`-Teil), `:994-1005` (Rechte: `echobox_app` hat `UPDATE` auf `delivery_runs`) |
| **Problem** | PostgreSQL prüft bei einem `UPDATE` auch den `USING`-Ausdruck gegen die **neue** Zeile. Weil die Policy `deleted_at IS NULL` enthält, ist ein `UPDATE … SET deleted_at = now()` durch `echobox_app` **nicht möglich** — der Lauf endet mit `new row violates row-level security policy`. Das Design ist trotzdem **richtig**: markieren kann ausschließlich die Eigentümerfunktion `enforce_retention_cross_tenant` (SECURITY DEFINER, Eigentümer `echobox_owner`). |
| **Messung** | `SET ROLE echobox_app; BEGIN; SELECT app.set_tenant('m-1'); UPDATE app.delivery_runs SET deleted_at = now() …` → **ERROR: new row violates row-level security policy**; über die Eigentümerfunktion: **1** Lauf markiert, **0** Fehler. |
| **Auswirkung** | Heute kein Fehler — die Aufbewahrung läuft über die Eigentümerfunktion. Aber: **jede** künftige logische Löschung (ein Endpunkt, ein Ereignis, ein Lauf) durch die Applikationsrolle endet als **500**, nicht als 404/409. Das ist eine implementationskritische Falle, die in **keinem** Artefakt steht. |
| **Lösung** | **Dokumentation** (ADR-002 oder `folder-structure.md`): „Logische Löschung ist der Applikationsrolle **nicht** möglich — sie läuft über die Eigentümerfunktion (oder über einen Eigentümer-/`SECURITY DEFINER`-Pfad).“ |
| **Empfehlungsstärke** | **sollte** (Dokumentation) |
| **Behandlung** | **Dokumentiert** (ADR = Schritt-2-Eigentum). Der Vertrag ist nicht betroffen. |

### N-17 — Die mandantenübergreifenden Listen haben **keinen** stützenden Index — **sollte** (Schema/Performance)

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:505-559` (Sortierung `ORDER BY e.updated_at DESC, e.endpoint_id DESC`, `LIMIT v_limit + 1`), `:568-629` (`ORDER BY d.created_at DESC, d.delivery_run_id DESC`); Indizes `:901-962` — **alle** mit `organisation_id` als erstem Präfix |
| **Problem** | Für die mandantenübergreifende Sicht gibt es **keinen** Index, der die globale Sortierung stützt: die vorhandenen Indizes beginnen mit `organisation_id` und sind für „alle Mandanten, sortiert nach `updated_at`“ unbrauchbar. Die Parameter in `WHERE` stehen in `OR`-Form, was den Filter zusätzlich unindizierbar macht. |
| **Messung** | `EXPLAIN` des inneren Queries: `Limit → Sort (Sort Key: e.updated_at DESC, e.endpoint_id DESC) → Nested Loop Left Join → **Seq Scan** on endpoints` — plus ein LATERAL-Aggregat **pro Zeile** (`Bitmap Index Scan` + `Sort` je Endpunkt). Für die Totenliste: `Seq Scan on delivery_runs` + `Sort`. |
| **Auswirkung** | Jede Seite der Totenliste und der Endpunktliste ist ein **Vollscan mit Sortierung** — mit wachsender Datenmenge linear. Für den Betrieb (ein Service-Admin, wenige Aufrufe) unkritisch, für eine Instanz mit vielen Mandanten ein echtes Skalierungsproblem. |
| **Lösung** | **Schema (Schritt 2):** partielle Indizes `endpoints (updated_at DESC, endpoint_id DESC) WHERE deleted_at IS NULL` und `delivery_runs (created_at DESC, delivery_run_id DESC) WHERE deleted_at IS NULL`; die Parameterfilter auf getrennte Abfragen verzweigen (statt `OR`), damit der Planner sie nutzen kann. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Dokumentiert, nicht geändert.** |

### N-18 — ADR-002 nennt die **neue** Grenze nicht: die Applikationsrolle kann `app.cross_tenant` setzen — **sollte** (ADR)

| | |
|---|---|
| **Fundstelle** | `docs/adr/ADR-002:100-115` (Abschnitt „Grenze (ehrlich benannt, Ausnahme)“ und „Grenze (ehrlich benannt)“), `schema.sql:498-501` (Freigabe-Variable), `:1031-1039` (Rechte) |
| **Problem** | ADR-002 benennt zwei Grenzen ehrlich: die mandantenübergreifende Sicht als **bewusste Ausnahme** (`:100-107`) und die Standardgrenze der RLS („RLS schützt vor **Anwendungsfehlern**, nicht vor einem kompromittierten Anwendungsprozess mit Datenbankzugriff“, `:111-115`). Der zweite Punkt nennt nur `app.set_tenant('m-2')`. **E-01 hat die Grenze erweitert, ohne sie zu benennen:** dieselbe Applikationsrolle kann `set_config('app.cross_tenant','service_admin', true)` setzen und danach **alle** Mandanten-Endpunkte und -Totenlisten lesen. |
| **Auswirkung** | Die ehrliche Grenzenliste der zentralen Sicherheitsentscheidung ist **unvollständig** — genau der Abschnitt, der Vertrauen schaffen soll. Eine SQL-Injektion oder ein fehlerhafter Query-Builder in der Applikation hätte über `app.cross_tenant` **alle** Mandanten im Blick. |
| **Lösung** | **ADR-002 (Schritt 2):** einen Absatz in „Grenze (ehrlich benannt)“ ergänzen: „Die Applikationsrolle kann `app.cross_tenant` setzen und damit die mandantenübergreifende Sicht öffnen. Das ist die **einzige** Erweiterung gegenüber dem Normalfall und die einzige Stelle, an der eine Fehlkonfiguration mandantenübergreifend sichtbar macht.“ Optional: eine **eigene** Laufzeitrolle `echobox_ops` für die drei Funktionen (dann wäre die Freigabe nicht an die Allzweckrolle gebunden) — das wäre eine **neue** Festlegung und braucht den Menschen. |
| **Empfehlungsstärke** | **sollte** (Dokumentation; die technische Härtung ist eine offene Entscheidung → § 7, E-12) |
| **Behandlung** | **Dokumentiert, nicht geändert** (ADR = Schritt-2-Eigentum). |

### N-19 — ADR-015 verspricht dem `service_admin` ein **mandantenübergreifendes Protokoll**, das es nicht gibt — **sollte** (ADR ↔ Vertrag)

| | |
|---|---|
| **Fundstelle** | `docs/adr/ADR-015:31-35` (Rollenmatrix: `service_admin` → „Protokoll lesen | **ja (alle Mandanten)**“), Vertrag `:1062` (Antwortbeschreibung `listDeliveries`: „Zustellprotokoll (**mandantengefiltert**)“), `:2446-2449` (`DeliveryRun.organisation_id`: „**Nur** für `service_admin` gefüllt“), `docs/requirements/PRD.md:74` (Rollenzeile: „Endpunkte aller Mandanten sehen, Tote-Zustellungsliste abholen, Aufbewahrung erzwingen“ — **kein** Protokoll) |
| **Problem** | Die Rollenmatrix der ADR verspricht dem `service_admin` mehr als der Vertrag anbietet: die mandantenübergreifende Sicht existiert **nur** für die drei Operationen (Endpunktliste, Totenliste, Aufbewahrung) — für die **Prozessliste** gibt es weder einen Vertragspfad noch eine Eigentümerfunktion. Die PRD-Zeile nennt nur die drei Rechte; **kein** Akzeptanzkriterium verlangt ein mandantenübergreifendes Protokoll (AC-020-1 = Entwickler, AC-020-2 = Totenliste). |
| **Auswirkung** | Wer ADR-015 §1 liest, erwartet eine Funktion, die es nicht gibt — und der Vertragssatz `DeliveryRun.organisation_id` („Nur für `service_admin` gefüllt“) bestätigt diesen falschen Eindruck (→ **V-25**). Kein AK ist betroffen; die Betriebsdeckung ist über die Totenliste gegeben. |
| **Lösung** | **ADR-015 (Schritt 2):** die Zelle der Rollenmatrix auf „eigener Mandant“ korrigieren (die mandantenübergreifende Sicht gilt nur für Endpunktliste, Totenliste und Aufbewahrung). **Kein** Vertrags-, **kein** Schema-, **kein** PRD-Änderungsbedarf. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Dokumentiert, nicht geändert.** |

### N-20 — Der konkurrierende zweite Replay **wartet** auf die Sperre des ersten — **sollte** (Implementierung/ADR)

| | |
|---|---|
| **Fundstelle** | `.agent/knowledge/schema.sql:240-241` (`UNIQUE (replay_of)`), `docs/adr/ADR-006:98-112` (§ 6), `docs/adr/ADR-015:123-131` (§ 6) |
| **Problem** | Die Einmal-Regel ist richtig durchgesetzt — aber der **verlierende** Replay blockiert auf dem UNIQUE-Index, bis die gewinnende Transaktion endet. Fällt in eine unbegrenzte Transaktion oder ein Statement-Timeout, antwortet der Verlierer **nicht** mit 409, sondern mit 500. Die ADRs sagen „die Constraint **erzwingt** die Regel“ — die Sperrenwartezeit erwähnen sie nicht. |
| **Messung** | Zwei gleichzeitige Replays desselben Laufs: der Verlierer wartete **4,005 s** (bis der Gewinner committet hatte) und erhielt dann `unique_violation` (23505) ⇒ 409. |
| **Auswirkung** | Korrekt im Ergebnis (genau ein Replay), aber die Latenz des Verlierers hängt an der Transaktionsdauer des Gewinners. Für einen Endbenutzer sichtbar als „hängen“. |
| **Lösung** | **Implementierung (Schritt 6) + ADR (Schritt 2):** die 23505 auf `delivery_state_conflict` abbilden (schon zugesagt) **und** ein `lock_timeout`/`statement_timeout` setzen, das kürzer ist als das HTTP-Timeout; die Sperrenwartezeit als erwartetes Verhalten in ADR-006 §6 benennen. |
| **Empfehlungsstärke** | **sollte** |
| **Behandlung** | **Dokumentiert.** |

### N-21 — Die Diagramme zeigen den **neuen** Pfad und die **neuen** Regeln nicht — **sollte** (Diagramme) — **in diesem Schritt korrigiert**

| | |
|---|---|
| **Fundstelle** | `docs/architecture/diagrams/03-component-api.mmd:49-55` (alle Datenbankzugriffe mit `JDBC/RLS` — auch die mandantenübergreifenden), `diagrams/05-sequence-delivery.mmd:28` („Status pending/scheduled“), `:35` (Beanspruchung ohne Mandantenschritt), `:40-57` (kein 3xx-Zweig), `:70-76` (Replay ohne Einmal-Regel) — **jeweils der Stand vor der Korrektur in diesem Schritt** |
| **Problem** | E-01, E-08 und E-09 haben das Design verändert, **ohne** dass die C4-Ebene nachgezogen wurde: das Komponentendiagramm des API-Layers zeigte den mandantenübergreifenden Zugriff **nicht** (und behauptete für alle Zugriffe `JDBC/RLS`), das Sequenzdiagramm nannte weder 3xx noch die Mandantenkette noch die Einmal-Regel und behauptete „pending/scheduled“ (V-06). |
| **Auswirkung** | Wer die Diagramme umsetzt, baut den mandantenlosen Claim und die 3xx-Lücke — genau die beiden Fehler, die der erste Stresstest gefunden hat. |
| **Lösung** | Diagramme nachziehen (Komponente `CrossTenantScope` + drei Beziehungen; Sequenz: 3xx-Zweig, Mandantenkette, Einmal-Prüfung, `pending` statt `pending/scheduled`). |
| **Behandlung** | 🟢 **in diesem Schritt korrigiert** (§ 3.1) |

### 3.1 Korrekturen in `docs/architecture/` (in diesem Schritt vorgenommen)

Alle vier betreffen **echte Fehler**; keine bestätigte Festlegung (F-01…F-04,
E-01…E-09, D-01…D-15, ADR-001…ADR-018) wurde angetastet, kein Vertrag und kein
ADR wurde geändert.

| # | Datei | Was | Grund |
|---|---|---|---|
| 1 | `docs/architecture/diagrams/03-component-api.mmd:24`, `:51-54` | Neue Komponente **`CrossTenantScope`** („Mandantenübergreifender Zugriff“, fail-closed, ADR-002 §7) + drei Beziehungen aus den Fachservices + eine Beziehung zur Datenbank mit `JDBC/SECURITY DEFINER` | N-21 — die mandantenübergreifende Sicht (E-01) war im C4-Bild **nicht** vorhanden; alle Zugriffe standen als `JDBC/RLS` |
| 2 | `docs/architecture/diagrams/05-sequence-delivery.mmd:28` | „Status **pending/scheduled**“ → „Status **pending** — ein Versuch ist noch nicht gelaufen“ | V-06 (Rest) — der Vertrag und das Schema sagen `pending`; das Diagramm war die letzte Stelle mit der Doppelangabe |
| 3 | `docs/architecture/diagrams/05-sequence-delivery.mmd:37-39` | Mandantenkette in der Beanspruchung (`tenant_ids_for_worker()` → je Mandant `set_tenant` → Claim) + Notiz zum fail-closed | N-02 (Rest) — die Sequenz zeigte die mandantenlose Beanspruchung |
| 4 | `docs/architecture/diagrams/05-sequence-delivery.mmd:54-57`, `:79-80` | Zweig **3xx** (kein Wiederholen, `client_error`, Redirects nicht befolgt) und die **Einmal-Prüfung** beim Replay | E-08/E-09 — die neuen Regeln fehlten im Ablaufbild |

**Zusätzlich** (kein Fehler, sondern eine Pflichtübergabe, die nirgends stand):
`docs/architecture/folder-structure.md:113` — die Auflage, den Statusfilter der
Totenliste **immer** auf `abandoned` zu setzen (N-14), und
`docs/architecture/domain_model.md:191` — die Einmal-Bedingung in der
Übergangstabelle des Zustandsautomaten (E-09).

---

## 4. Achse 2 — Spezifikation

*Axe: `docs/api/openapi.yaml` ↔ `docs/requirements/` ↔ `docs/adr/` ↔ Schema.*

### 4.1 Die Konsistenzmatrix — geprüft, nicht behauptet

| Gegenstand | Vertrag | Schema | ADR | Ergebnis |
|---|---|---|---|---|
| Zustandswerte (`EndpointStatus`, `EventStatus`, `DeliveryStatus`, `DeadDeliveryStatus`, `AttemptOutcome`, `RejectionReason`) | `:2000-2074` | CHECK-Constraints `:92-93`, `:169-170`, `:217-219`, `:224-225`, `:276-278` | ADR-006 §5-7 | 🟢 **identisch** — inklusive der Vertragsverengung `DeadDeliveryStatus = [abandoned]` (R-03) |
| Spaltennamen ↔ Vertragsfelder | `DeliveryRun`, `DeliveryAttempt`, `DeadDelivery`, `Endpoint`, `Event` | Tabellendefinitionen | ADR-002, ADR-011 | 🟢 1:1 (Fingerprint, `replay_of`, `abandoned_at`, `secret_version`, `paused_at` …) |
| Idempotenz (Mandant, Schlüssel) | `:846-851`, `:3295-3298` | `events_idempotency_uq` `:167-168` | ADR-007 | 🟢 identisch |
| Größengrenze 1.048.576 Byte | `:853-862`, `:2316-2317` | `events_payload_size_ck` `:175-176`, `payload_ciphertext_ck` `:177-178` | NFR-001 | 🟢 identisch — inkl. Chiffre-Reserve (1.048.592) |
| „Kein Abnehmer“ | `EventStatus`, `DeliveryStatus`, `:1003-1004` | `delivery_runs_no_subscriber_ck` `:226-228` | D-04 | 🟢 identisch |
| Endgültige Zustände | `:995-997`, `:2477-2481` | `delivery_runs_terminal_no_next_attempt_ck` `:229-231` | ADR-006 §7 | 🟢 identisch (V-07 behoben) |
| Rate-Limit: **zwei** Zustände | `:118-126`, `:425-465`, `:2762-2804` | `endpoint_rate_limits` `:296-312` (kein `enabled`, `limit_value >= 0`) | ADR-010 §3 | 🟢 identisch (E-02) |
| Aufbewahrung: Anker, Stufen, Kaskade, Karenzzeit | `:1456-1477`, `:2822-3064` | `enforce_retention_cross_tenant` `:655-822` | ADR-012 §2-4 | 🟡 **identisch in der Absicht**, 🟠 **abweichend in der Wirkung** (N-13: markierte Versuche bleiben sichtbar) |
| Replay: einmal, 409 | `:1306-1310` | `delivery_runs_replay_of_uq` `:240-241` | ADR-006 §6, ADR-015 §6 | 🟢 Regel durchgesetzt; 🟠 Historie geht beim Purge verloren (N-15) |
| 3xx: kein Wiederholen, `client_error`, keine Redirects | `:347-357`, `:2055-2056` | `delivery_attempts_outcome_ck` `:276-278` (`client_error`), `rejection_reason_ck` `:224-225` | ADR-008 `:56`, ADR-009 `:88-113` | 🟢 identisch |
| Mandant: Transaktion, nicht Verbindung | `:3233` **nennt noch den „Connection-Initializer“** | `schema.sql:25-32`, `:425-436` | ADR-002 §3 | 🟡 **Vertragstext** (V-23) — Schema und ADR sind richtig |
| Mandantenübergreifende Sicht | `:555-559`, `:1201-1205`, `:1452-1454`, `:3290` | drei `SECURITY DEFINER`-Funktionen `:505-822` | ADR-002 §7, ADR-015 §1-2 | 🟢 identisch; 🟠 **eine** Funktion ist breiter als die Projektion (N-14) |
| Rollenmatrix | `:30-36` | — | ADR-015 §1 | 🟡 **ADR** verspricht mehr (N-19) |
| Cursor über Mandantgrenzen | `:83-95` | Sortierung + Keyset-Vergleich `:554-556`, `:624-626` | ADR-017 | 🟢 identisch (R-04) |
| Herkunft der Fristen (Mandant vs. Vorgabe) | `:3006-3030` (`*_retention_source`) | `retention_settings` `:356-366` | ADR-012 §6 | 🟢 identisch (R-05) |

### 4.2 Was die neue Runde am Vertrag **nicht** gefunden hat

Ein zweiter Blick auf genau die Stellen, die die Korrekturen neu eingeführt
haben, hat **keinen** weiteren Vertragsdefekt ergeben:

- **Aufbewahrungs-Kaskade:** Vertrag (`:1469-1471`), Schema (`:643-645`,
  `:739-758`, `:760-782`) und ADR-012 §4 (`:66-81`) beschreiben **dieselbe**
  Kaskade, **dieselbe** Reihenfolge (Versuche → Läufe → Ereignisse) und
  **dieselbe** Purge-Reihenfolge (Schema `:797-805`). Die Zählerbedeutungen
  (`marked_*` = in diesem Durchlauf markiert, `purged_*` = physisch gelöscht)
  stehen an **einer** Stelle (`openapi.yaml:2830-2833`) und sind eindeutig.
  *Die einzige Abweichung ist die Wirkung* (N-13) — nicht die Beschreibung.
- **3xx:** steht im **ausgehenden** Vertrag (`x-delivery-protocol.response.outcomes`,
  `:347-357`), in der `AttemptOutcome`-Beschreibung (`:2055-2056`), in der
  Ergebnismatrix der Architektur (`architecture.md:179`), in ADR-008 (`:56`) und
  in ADR-009 (`:88-113`) — **an allen fünf Stellen gleich**. Die
  **Wiederholungsmatrix** des Vertrags (`retry_policy`, `:392-423`) nennt die
  3xx nicht ausdrücklich, widerspricht ihr aber auch nicht: sie beschreibt
  **wann** wiederholt wird, und 3xx ist „nein“. → **kein Defekt**, aber eine
  Stelle, an der ein Leser suchen muss.
- **Entfernter `enabled`-Zustand:** `limit_value = 0` (abgeschaltet) und
  `limit_value IS NULL` (kein Override ⇒ 60) stehen **gleich** in
  `x-delivery-protocol.rate_limit.states` (`:445-455`), in `RateLimitObservation`
  (`:2762-2787`), in `info.description` (`:118-126`), in `x-contract-rules.defaults`
  (`:3282`), im Schema (`:290-333`) und in ADR-010 (`:38-85`). **Kein Rest des
  dritten Zustands** — `enabled` kommt im gesamten Repository nicht mehr vor
  (maschinell geprüft).
- **Reduktion aus Schritt 3:** die drei `SECURITY DEFINER`-Funktionen sind
  **nicht** zusammengelegt worden (die Projektionsgrenze bliebe sonst verloren),
  `GET /api/v1/events` ist weiterhin nicht da, keine Story/AK/Anforderung ist
  entfallen, `x-acceptance-coverage` = **101/101**, `x-requirement-coverage` =
  **24/24**. **Keine Abdeckungslücke** durch die Reduktion.
- **Prüfbarkeit der AKs:** die früher „nicht entscheidbaren“ AKs sind es jetzt
  nicht mehr — AC-016-5 (Anker, Stufen, Karenzzeit, Kaskade, Herkunft benannt),
  AC-018-4 (`limit` + `limited`), AC-007-3 (kanonische Bytes), AC-004-2/AC-016-1
  (Mechanismus belegt). Offen bleibt die **Testebene** (32 AKs ohne Gherkin-
  Szenario, Lasttest, Parallelbetriebstest) — das ist **Schritt 5**, kein
  Vertragsbefund.

---

## 5. Developer-Interface — 9 Dimensionen

| # | Dimension | Ergebnis | Begründung / Fundstelle |
|---|---|---|---|
| 1 | **Auffindbarkeit** | 🟢 gut | 17 Operationen, alle mit `operationId`, `summary`, `description`, Tag, `security`, `x-ac-id` **und** `x-requirement-ids`; `x-acceptance-coverage` (101) und `x-requirement-coverage` (24) verweisen jeden Punkt auf seinen Ort; `x-contract-rules` bündelt die vertragsweiten Regeln (`:3279-3304`). *Anmerkung:* die **Rollen** stehen nur im Prosa-Text (`:30-36`), nicht maschinenlesbar — unverändert gegenüber dem ersten Lauf. |
| 2 | **Konsistenz** | 🟢 gut (mit zwei Resten) | Die drei Widersprüche des ersten Laufs sind weg (`next_cursor`, `no_subscriber`, `payload_media_type`). Zwei Reste: der Text nennt den **deutschen** Statuswert `aktiv`/`pausiert` (→ V-24) und der Abdeckungseintrag AC-022-3 nennt den „Connection-Initializer“ (→ V-23). |
| 3 | **Vollständigkeit** | 🟢 gut | Kein unerreichbarer Fehlercode mehr (alle 12 Codes der Fehlertabelle `:43-56` sind erzeugbar; `tenant_parameter_rejected` hat eine eigene 422-Komponente), `replay_of` vorhanden, 3xx definiert, das Vorgabefenster (7 Tage) und das Gesamtfenster (≈ 2 h) genannt, die Karenzzeit als nullable ausgewiesen. Offen: die **Top-Level-Zähler** der Aufbewahrungsantwort sind nicht ausdrücklich als **Summe** über die Mandanten bezeichnet (das Beispiel zeigt es: 128 + 12 = 140) — *optional*. |
| 4 | **Fehlerbehandlung** | 🟢 strukturiert | Ein Schema (`Problem`), 400/422-Grenze ausgeschrieben (`:58-63`), `violations` je Feld, 409 für alle drei Replay-Fälle (`:1358-1359`), 429 an allen 15 authentifizierten Operationen, 503 über die Komponente. Die Antwort auf den pausierten Endpunkt ist festgelegt (409). Einzige Lücke: ein **ungültiger Cursor** wird mit 422 abgelehnt (`:1664`, `:3301`), für die Fehlertabelle ist das aber die **validation_failed`-Variante** — unscharf, aber vertretbar. |
| 5 | **Sicherheit & Mandantentrennung** | 🟢 solide, mit einer offenen Grenze | JWT-only, Mandant ausschließlich aus dem Token, Fremdes ⇒ 404, Secret nie in der Antwort, `writeOnly`, `additionalProperties: false`, RLS fail-closed (erneut gemessen), **kein** `BYPASSRLS`, drei schmale Eigentümerfunktionen mit `search_path` verdrahtet, `REVOKE`/`GRANT` korrekt, Projektionen ohne Nutzlast und ohne Secrets. Offen: die **neue** Grenze (N-18) ist in ADR-002 nicht benannt, und die **Totenlisten-Funktion** ist fail-open (N-14). |
| 6 | **Performanz & Grenzen** | 🟡 brauchbar | Cursor-Paginierung (kein `total`), `limit` ≤ 200, 33 Indizes auf die tenant-skalierten Zugriffspfade, asynchrone Annahme, Metriken on demand, Body-Obergrenze 2 MiB genannt, Zustellfenster ≈ 2 h zugesagt. **Neu:** die mandantenübergreifenden Listen laufen ohne stützenden Index (N-17). |
| 7 | **Idempotenz & Wiederholbarkeit** | 🟢 gut | Idempotenz je (Mandant, Schlüssel) mit gezieltem HMAC-Vergleich, 202 mit der **ersten** ID, 409 bei abweichender Nutzlast, at-least-once mit drei Kennungen, Replay als **neuer** Lauf und **einmal** (409). Offen: der Verlierer eines gleichzeitigen Replays wartet (N-20) — sachlich korrekt, aber mit Wartezeit. |
| 8 | **Testbarkeit** | 🟡 gut (Layer fehlt) | Die früher unentscheidbaren AKs sind entscheidbar; der Zustandsautomat, `ClockPort`, `IdGenerator` und die Ports machen Backoff, Signatur und Replay unit-testbar; RLS, Cross-Tenant-Funktionen, Replay-Constraint und Aufbewahrung sind **gegen echtes PostgreSQL 16** belegt (§ 1.3). Offen (Schritt 5, nicht Vertragsbefund): 32 AKs ohne Gherkin-Szenario, Lasttest (NFR-002), Parallelbetriebstest (AC-023-3/4), und die drei neuen Testfälle aus N-13/N-14/N-15. |
| 9 | **Verständlichkeit & Dokumentation** | 🟢 gut | Eine Erzählung, ein Glossar, ADRs mit Begründung und Alternativen, Beispiele je Operation, ein Abschnitt „Pagination“ und „Vorgabewerte“ in `info.description`. Die Beispiele widersprechen sich **nicht** mehr (maschinell gegen ihre Schemas geprüft: keine Pflichtfeld- oder Enumverstöße). Zwei Textkorrekturen stehen aus (V-23, V-24) — und die Diagramme sind jetzt nachgezogen (N-21). |

---

## 6. Vertragsdefekte in `docs/api/openapi.yaml`

**Der Vertrag wurde nicht verändert.** Grund: ein Eigentümer, eine Wahrheit
(ADR-016); er wird nach diesem Schritt eingefroren. Jeder Defekt hier ist
**Dokumentation**, kein Fix.

Die **22** Defekte des ersten Laufs sind behoben (§ 1.1). Neu gefunden:

| ID | Pfad:Zeile | erwartet | tatsächlich | Auswirkung | konkrete Korrektur | Priorität |
|---|---|---|---|---|---|---|
| **V-23** | `docs/api/openapi.yaml:3233` | Der Abdeckungseintrag zu **AC-022-3** nennt den Mechanismus so, wie er **entschieden** ist: der Mandant wird **innerhalb der Transaktion** gesetzt, die auch liest (ADR-002 §3) | „ADR-002: der Mandant wird im **Connection-Initializer** aus dem JWT gesetzt; ein Parameter aus dem Request-Body wird nicht uebernommen.“ — der Klassenname ist historisch (E-05), aber der Satz nennt **nicht** die transaktionsgebundene Semantik | Wer den **Vertrag** als Implementierungsgrundlage nimmt (und nicht ADR-002 §3), implementiert einen **verbindungsgebundenen** Mandantenkontext → unter einem Connection-Pool liefert **jede** Abfrage 0 Zeilen (fail-closed) — der Dienst wäre tot (N-01, AC-022-3) | `realized_in` auf die entschiedene Semantik umstellen, z. B.: „ADR-002 §3: der Mandant wird **innerhalb der Transaktion**, die auch liest, aus dem JWT gesetzt (`app.set_tenant`, transaktionslokal) — **nicht** verbindungsgebunden; ein Parameter aus dem Request-Body wird nicht übernommen.“ | **muss** |
| **V-24** | `docs/api/openapi.yaml:480` und `:697` | Die **Werte** der Zustände sind `active` / `paused` (Schema `schema.sql:87`, `:92-93`; Vertrag `EndpointStatus` `:2000-2003`) | „Der Endpunkt wird mit dem Status **`aktiv`** angelegt“ (`:480`), „Setzt den Endpunkt auf **`pausiert`**“ (`:697`) — deutsche Wörter in Code-Formatierung, als wären sie die Werte | Ein Client-Entwickler, der dem Text folgt, sendet `"aktiv"`/`"pausiert"` und bekommt 422 `validation_failed` (AC-001-1, AC-002-1) | beide Stellen auf die **Werte** umstellen: „mit dem Status **`active`** angelegt“ bzw. „Setzt den Endpunkt auf **`paused`**“ (die deutsche Übersetzung kann als Klartext ohne Code-Formatierung bleiben) | sollte |
| **V-25** | `docs/api/openapi.yaml:2446-2449` (Feld) vs. `:1062` (Antwortbeschreibung `listDeliveries`) und `:30-36` (Rollenzeile) | `DeliveryRun.organisation_id` beschreibt **denselben** Mandantenbezug wie die Operation, in der der Lauf steht | „**Nur** für `service_admin` gefüllt“ — das legt eine mandantenübergreifende Protokollliste nahe; die Antwortbeschreibung sagt „Zustellprotokoll (**mandantengefiltert**)“, und eine mandantenübergreifende Sicht auf das Protokoll gibt es **weder** im Vertrag noch im Schema (nur die drei Eigentümerfunktionen) | Ein Client, der `organisation_id` als „Mandant des Laufs“ nutzt, bekommt für `developer` **immer** `null` (nicht den eigenen Mandanten) und erwartet für `service_admin` eine mandantenübergreifende Liste, die es nicht gibt. Zusammen mit der Rollenzeile in ADR-015 §1 (N-19) ist die Aussage **zweideutig** | Die Feldbeschreibung an die tatsächliche Semantik anpassen: entweder „**immer** gefüllt — der Mandant des Laufs“ (dann ist das Feld in `DeliveryList`/`DeliveryRunDetail` immer belegt) oder „in mandantenübergreifenden Antworten gefüllt; die Protokollliste ist mandantengefiltert“. Parallel dazu **N-19** in ADR-015 korrigieren | sollte |

**Nicht neu, aber weiterhin gültig (aus dem ersten Lauf, ohne Vertragsbezug):**
die 32 AKs ohne Gherkin-Szenario, die zwei fehlenden Testebenen (Lasttest,
Parallelbetriebstest) und die Prüfungen des Traceability-Werkzeugs — **Schritt 5**.

---

## 7. Offene Entscheidungen — mit Empfehlung

Der Skill verlangt, an dieser Stelle zu **fragen** statt zu entscheiden. Als
Subagent frage ich nicht nach — ich lege die Entscheidung samt Empfehlung vor.
**Keine** dieser Entscheidungen blockiert den **Freeze** des Vertrags.

| ID | Frage | Optionen | **Empfehlung** | Warum | Gate |
|---|---|---|---|---|---|
| **E-10** | Soll das **Zustellprotokoll** des `service_admin` mandantenübergreifend sein? (N-19) | (a) **nein** — die Protokollliste bleibt mandantengefiltert, ADR-015 §1 wird korrigiert; (b) ja — vierte Eigentümerfunktion, Vertrag und Schema wachsen | **(a)** | Kein Akzeptanzkriterium verlangt es (AC-020-1 = Entwickler, AC-020-2 = Totenliste), die PRD-Rollenzeile nennt nur die drei Rechte, und die Totenliste deckt den Betriebsbedarf ab. (b) wäre eine **neue** Funktion mit eigener Projektion, eigenem Cursor und eigener Berechtigung — erheblicher Aufwand ohne Anforderung | vor Schritt 5 (ADR-015) |
| **E-11** | Soll die **Replay-Historie** den Purge überleben? (N-15) | (a) **ja** — `replay_of` ohne Fremdschlüssel (historischer Verweis); (b) nein — der Vertrag wird um einen Vorbehalt ergänzt | **(a)** | AC-013-2 verlangt die sichtbare Historie, und die Zusage des Vertrags ist richtig. (b) würde eine Vertragsänderung **nach** dem Freeze auslösen — genau das, was vermieden werden soll. Der Aufwand ist eine Constraint, keine neue Funktion | vor Schritt 5 (Schema) |
| **E-12** | Soll die mandantenübergreifende Sicht **härter** abgesichert werden? (N-18) | (a) **nein** — die Grenze wird in ADR-002 ehrlich benannt, die Applikation bleibt die einzige Rolle; (b) ja — eigene Laufzeitrolle `echobox_ops` für die drei Funktionen | **(a) jetzt, (b) als Risiko vormerken** | (b) ist die konsequenteste Härtung (die Allzweckrolle `echobox_app` bekäme die Freigabe nicht mehr), verlangt aber eine **getrennte** Verbindungsrolle im Betrieb (ADR-013: zwei Rollen, ein Image) — eine echte Architekturentscheidung, die der Mensch treffen muss. Für diesen Schritt reicht die ehrliche Benennung | Gate 2 (Fachbereich/Plattform) |
| **E-13** | Soll die **Karenzzeit** der Aufbewahrung einen **Vorgabewert** bekommen? (**R-01**, offen seit dem Rücksprung) | (a) **0 Tage** (Purge im nächsten Durchlauf) als Betriebsdefault; (b) ein fachlicher Wert (z. B. 7 oder 30 Tage); (c) **kein** Wert — der Betreiber muss ihn setzen | **(a) als Default, mit (c) als Vertragslage** | Der Vertrag behauptet **keinen** Wert (`purge_delay_days: null`, „Ein Vorgabewert ist nicht festgelegt“ `:1463-1466`, `:2879-2887`) — das bleibt richtig, solange der Wert nicht beschlossen ist. Das **Schema** rechnet heute bereits mit `COALESCE(p_purge_delay_days, 0)` ⇒ Purge im nächsten Durchlauf. (a) macht dieses Verhalten **explizit** statt zufällig; (b) ist eine **fachliche** Zusage (Aufbewahrung vs. Fehleruntersuchung) und gehört zum Fachbereich | Gate 2 (Fachbereich) |

**Weiterhin offen (unverändert, nicht von diesem Schritt zu entscheiden):**
**O-04** (Verschlüsselung im Ruhezustand — fachliche Pflichtfrage),
**O-05** (AVV bei Fremddaten) — beide Fachbereich/Recht, Gate 2;
**R-01** (Karenzzeit) → jetzt E-13.

**Bestätigte Entscheidungen, die dieser Stresstest **nicht** neu aufgerollt hat:**
D-01…D-15, ADR-001…ADR-018, F-01 (60/min, `delay`), F-02 (30 Tage, getrennt, ab
letztem Versuch), F-03 (Replay nur Empfänger-Admin), F-04 (8 Versuche, 1 min,
×2, 60 min, ± 20 %) sowie O-07 (5 s + 10 s). Keiner dieser Werte wurde in
diesem Schritt verändert.

---

## 8. Was **nicht** zu beanstanden war

Damit der Bericht auch Bestand hat — geprüft und **in Ordnung**:

| Geprüft | Ergebnis |
|---|---|
| Vertragsform | OpenAPI 3.0.3, YAML gültig, **17** Operationen, alle mit eindeutigem `operationId`, `summary`, `description`, `security`, `x-ac-id` **und** `x-requirement-ids` |
| Erreichbarkeit der Fehlercodes | Alle **12** Codes der Fehlertabelle (`:43-56`) sind erzeugbar: `tenant_parameter_rejected` über die 422-`oneOf` (`:1804-1850`), 429 an **15/15** authentifizierten Operationen, 503 über die Komponente (`:1609-1610`) — **kein** toter Bestandteil |
| `$ref` und ungenutzte Objekte | Alle Verweise auflösbar; **kein** ungenutztes Schema, **keine** ungenutzte Antwort-Komponente, kein ungenutzter Parameter/Header (maschinell geprüft) |
| Beispiele | Jedes Antwortbeispiel maschinell gegen sein Schema geprüft (Pflichtfelder, Unbekannte-Felder, Enums) — **keine** Abweichung; die beiden Zustelllauf-Beispiele beschreiben denselben Lauf (`attempt_count: 2`, zwei Versuche, `next_attempt_at` = letzter Versuch + 2 min) |
| Zustandswerte ↔ Schema | `EndpointStatus`, `EventStatus`, `DeliveryStatus`, `DeadDeliveryStatus`, `AttemptOutcome`, `RejectionReason` stimmen **exakt** mit den CHECK-Constraints überein |
| Spaltennamen ↔ Vertragsfelder | 1:1 — `attempt_count`, `max_attempts`, `next_attempt_at`, `last_attempt_at`, `last_response_status`, `last_error_code`, `rejection_reason`, `abandoned_at`, `attempt_id`, `attempt_number`, `started_at`, `finished_at`, `duration_ms`, `response_status`, `outcome`, `error_code`, `event_id`, `endpoint_id`, `delivery_run_id`, `secret_version`, `secret_rotated_at`, `paused_at`, `replay_of` |
| Größengrenze | 1.048.576 Byte steht **gleich** in NFR-001, Vertrag (Regel + Beschreibung), DB-CHECK, Fehlercode 413 und der Fehlertabelle — inkl. Chiffre-Reserve (1.048.592) |
| Idempotenz | (Mandant, Schlüssel) ↔ `UNIQUE (organisation_id, idempotency_key)` ↔ gezielter HMAC-Fingerprint ↔ 202/409 — in Vertrag, Schema, ADR-007, Glossar und Domain-Modell **identisch** |
| „Kein Abnehmer“ | Ereignis-Status, Lauf-Status, `endpoint_id IS NULL`, DB-CHECK, Glossar, Zustandsautomat, Vertrag — **konsistent** |
| RLS | fail-closed (erneut gemessen: 0 Zeilen ohne Kontext), **keine** Rolle mit `BYPASSRLS`, `FORCE ROW LEVEL SECURITY` bewusst nicht gesetzt, `SECURITY DEFINER` nur für die schmalen Eigentümerfunktionen, `search_path` verdrahtet, `REVOKE`/`GRANT` korrekt, Worker **nicht** freigegeben |
| Replay-Kette | `replay_of` (Vorgänger) → neuer Lauf mit **neuer** ID, **gleicher** Ereignis-ID, zurückgesetzter Versuchszahl, `attempt_number` beginnt bei 1, Historie am Lauf sichtbar — in Vertrag, Schema, ADR-006 §6, ADR-015 §6, Domain-Modell und Sequenzdiagramm **identisch** |
| 3xx-Kette | kein Wiederholen, `rejected`, `rejection_reason = client_error`, `outcome = client_error`, Redirects werden **nicht** befolgt — an **fünf** Stellen gleich (Vertrag-Ergebnismatrix, `AttemptOutcome`, `architecture.md:179`, ADR-008 `:56`, ADR-009 `:88-113`) |
| Rate-Limit-Zustände | `kein_override` (⇒ 60) und `abgeschaltet` (⇒ 0) — an **sechs** Stellen gleich (Vertrag ×3, `x-contract-rules`, Schema, ADR-010); der entfernte `enabled`-Zustand kommt im Repository **nicht mehr** vor |
| Reduktion aus Schritt 3 | Die drei Eigentümerfunktionen sind **nicht** zusammengelegt (die Projektionsgrenze wäre verloren gegangen), `GET /api/v1/events` ist weiterhin nicht da, keine Story/AK/Anforderung entfallen, 101/101 und 24/24 Abdeckung |
| „Ein Ort pro Wahrheit“ | `.agent/knowledge/openapi.yaml` ist ein **reiner Verweis**; das Schema-Spiegel ist **byte-identisch** (`diff` leer); `docs/adr/README.md` deckt alle 18 Dateien ab (17 Entscheidungen + 1 Verweis) |
| Traceability | `python3 .agent/tools/traceability_check.py` → **grün** (23 Stories / 101 AKs / 24 Anforderungen) |
| p95 < 200 ms (NFR-002) | Der Annahmepfad ist eine begrenzte Folge von Statements, die Nutzlast ist auf 1 MB gedeckelt, die Zustellung läuft im Hintergrundprozessor — der Zielwert ist **plausibel**; die Messung selbst fehlt (Schritt 5) |
| Backoff | monoton wachsend, nie in der Vergangenheit, deterministisch prüfbar (Seed), Streuung ≥ 1 s, Basisabstand (60 s) > Gesamtgrenze (15 s) — **widerspruchsfrei**; das Gesamtfenster 123 min ≈ 2 h steht im Vertrag **und** in ADR-008 |
| 1xx | Die Ergebnismatrix des ausgehenden Vertrags nennt **kein** 1xx. Das ist **kein** Mangel: ein HTTP-Client liefert 1xx nie als Endstatus; 101 (Upgrade) ist für Webhooks nicht vorgesehen. |

---

## 9. Selbstprüfung

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken …“ (23 Stories / 101 AKs / 24 Anforderungen) |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (byte-identisch) |
| Vertrag unverändert | `git diff --stat -- docs/api/openapi.yaml` | **leer** |
| ADRs unverändert | `git diff --stat -- docs/adr/` | **leer** |
| `.agent/knowledge/` unverändert | `git diff --stat -- .agent/knowledge/` | **leer** |
| Anforderungen unverändert | `git diff --stat -- docs/requirements/ docs/Anforderung.md` | **leer** |
| Kein Anwendungscode | `git diff --stat -- src/` | **leer** (`src/` existiert nicht) |
| YAML gültig | `python3 -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml'))"` | **gültig** |
| Vertragsform | Skript | 17 Operationen; 15/15 authentifizierte Operationen mit 429; **alle** `$ref` auflösbar; **kein** ungenutztes Schema/Response/Parameter/Header |
| Abdeckung | Skript | `x-acceptance-coverage` **101/101**, `x-requirement-coverage` **24/24**, keine Dubletten |
| Beispiele | Skript (Pflichtfelder, Enum, Unbekannte-Felder) | **keine** Abweichung |
| Mermaid | Klammer-/Anführungszeichen-Balance | 03: 91/91 Klammern, 198 Anführungszeichen (gerade); 05: 35/35 Klammern, 4 Anführungszeichen (gerade) |
| Markdown | Struktur der geänderten Dateien | **ok** |
| Schema anwendbar | `psql -f .agent/knowledge/schema.sql` (PostgreSQL 16.15) | **ohne Fehler** |
| RLS-Verhalten | § 1.3 | **belegt** (fail-closed, Mandantentrennung, Cross-Tenant, Worker-Sperre) |
| Replay-Regel | § 1.3 | **belegt** (23505 ⇒ 409; NULL-Verhalten; Nebenläufigkeit ⇒ Sperrenwait 4,005 s) |
| Aufbewahrung | § 1.3 | **belegt** (dry_run ändert nichts; Markieren inkl. Kaskade; Purge) |
| Commit-Scope | `git add -A -- docs/architecture/ .agent/artifacts/` | **nur** diese beiden Pfade |

**Fazit für den Orchestrator:** Der Vertrag ist **einfrierreif**. 26 von 26
Nachprüfungen sind belegt behoben; die neue Runde findet **8** neue Befunde
(3 „muss" — alle **Schema**, alle *Korrekturen auf den Vertrag hin*) und
**3** neue Vertragsdefekte (alle „sollte", je ein Satz). **Kein** offener
„muss"-Befund gegen den Vertrag. **Freeze ab dem Commit dieses Schritts.**
Vor Schritt 5 sind N-13, N-14 und N-15 in Schritt 2 nachzuziehen (Schema
**ohne** Vertragsänderung) und V-23 im selben Rücksprung mitzunehmen.
