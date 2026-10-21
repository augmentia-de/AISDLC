# Rücksprung-Bilanz — Schritt 2 (Konzept & Architektur), **Rücksprung 2**

> **Vorgangsprotokoll** — der Nachweis, dass kein Befund des **zweiten**
> Stresstests liegen geblieben ist. **Kein lebender Wahrheitsort**: die
> geltenden Entscheidungen stehen in `docs/adr/`, der Vertrag in
> `docs/api/openapi.yaml`, die offenen Punkte in `.agent/artifacts/architecture.md`
> §8.
>
> **Rücksprung 2 aus Schritt 4 (Grill-Me-Stresstest)** · Agent:
> `aisdlc-architecture` · Stand: 2026-10-21 · Eingang:
> `.agent/artifacts/stress-test.md` (zweite Runde, Commit `c9c8038`)
> · Vertrag: `docs/api/openapi.yaml` (17 Operationen) — **noch nicht eingefroren**
> · Schema: `.agent/knowledge/schema.sql` (kanonisch) + Spiegel
> `docs/architecture/schema.sql`
>
> **Ersetzt** die Bilanz des ersten Rücksprungs (`9bb9bf4`) — deren 22
> Vertragsdefekte, der Architekturbruch S-01 und die drei Standards-Befunde
> sind am Stand des zweiten Stresstests nachgewiesen behoben (§ 1 des
> Stresstestberichts).

Dieser Bericht ist der **Nachweis**, dass kein Befund des zweiten Stresstests
liegen geblieben ist. Je Befund steht: **Fundstelle** (so, wie der Stresstest
sie nennt) → **wo korrigiert wurde** → **wie** → **Nachweis**.

---

## 0. Kurzfassung

| Umfang | Befunde | Behoben | Offen |
|---|---|---|---|
| **Vertragssätze** (V-23, V-24, V-25) | 3 | **3** | 0 |
| **Schema-Befunde** (N-13, N-14, N-15 — „muss“; N-16, N-17 — „sollte“) | 5 | **5** | 0 |
| **ADR-Befunde** (N-18, N-19, N-20 — „sollte“) | 3 | **3** | 0 |
| **Entscheidungen** (E-10, E-11, E-13 umgesetzt; E-12 offengelegt) | 4 | **4** | 0 |
| **Neue Befunde** (V-26) | 1 | **0 — dokumentiert, nicht entschieden** | 1 |

**Beleg:** Das Schema wurde gegen **PostgreSQL 16.15** angewendet und das
Verhalten nachgewiesen (Abschnitt 4). `diff .agent/knowledge/schema.sql
docs/architecture/schema.sql` ist **leer**. `python3
.agent/tools/traceability_check.py` ist **grün** (23 Stories / 101 AKs / 24
Anforderungen, unverändert). Der Vertrag: YAML gültig, **17** Operationen,
**176** `$ref` alle auflösbar, `x-acceptance-coverage` **101/101**,
`x-requirement-coverage` **24/24**, je Operation `operationId`, `x-ac-id` und
`x-requirement-ids`.

**Nicht entschieden** wurde ein beim Korrigieren gefundener Punkt (**V-26**,
Abschnitt 5) — er gehört in die Delta-Prüfung (Schritt 4), nicht in diesen
Schritt.

---

## 1. Die drei Vertragssätze (V-23, V-24, V-25)

Alle drei betreffen **je einen Satz** im Vertrag. Der Vertrag ist damit
**inhaltlich unverändert** in seiner Zusage — nur die Worte wurden an die
entschiedene Semantik angeglichen. **Der Vertrag wird nach Schritt 4
eingefroren**, nicht vorher.

| ID | Fundstelle (Stresstest) | Korrigiert in | Wie | Nachweis |
|---|---|---|---|---|
| **V-23** (muss) | `openapi.yaml:3233` — Abdeckungseintrag **AC-022-3** nennt den „**Connection-Initializer**“ | `openapi.yaml:3241` | Der Eintrag nennt jetzt die **entschiedene** Semantik: „**ADR-002 §3**: der Mandant wird **innerhalb der Transaktion**, die auch liest, aus dem JWT gesetzt (`app.set_tenant`, transaktionslokal) — **nicht** verbindungsgebunden; ein Parameter aus dem Request-Body wird nicht uebernommen.“ Der historische Klassenname (`TenantConnectionInitializer`) bleibt nur dort, wo er historisch ist (ADR-002 §3, Zielstruktur) — **im Vertrag** steht er nicht mehr. | `grep -n "Connection-Initializer" docs/api/openapi.yaml` ⇒ **kein Treffer**. |
| **V-24** (sollte) | `openapi.yaml:480` („Status `aktiv`“) und `:697` („`pausiert`“) — deutsche Wörter in Code-Formatierung | `openapi.yaml:480` und `:697` | Die **Werte** stehen jetzt in Code-Formatierung, die deutsche Übersetzung als Klartext: „mit dem Status **`active`** (aktiv) angelegt“ und „Setzt den Endpunkt auf **`paused`** (pausiert)“. | `grep -n "\`(aktiv\|pausiert)\`" docs/api/openapi.yaml` ⇒ **kein Treffer**. Die Werte `active`/`paused` stehen als **Enum** in `components.schemas.EndpointStatus` und im CHECK-Constraint `endpoints_status_ck` — unverändert. |
| **V-25** (sollte) | `openapi.yaml:2446-2449` — `DeliveryRun.organisation_id`: „**Nur** für `service_admin` gefüllt“ legt eine mandantenübergreifende Protokollliste nahe | `openapi.yaml:2446-2456` | **Gewählte Variante: „nur in mandantenübergreifenden Antworten gefüllt; die Protokollliste ist mandantengefiltert“.** Der Vertrag sagt das jetzt ausdrücklich: „Die Protokollliste ist **mandantengefiltert** und zeigt auch dem `service_admin` nur die Läufe des Mandanten aus der Identität (E-10). Die mandantenübergreifende Sicht gilt nur für Endpunktliste, Totenliste und Aufbewahrung (ADR-002 §7). Im Zustellprotokoll bleibt das Feld deshalb `null`; es ist **kein** Filter (D-01).“ | **Warum diese Variante:** sie ist widerspruchsfrei zum **Geltungsbereich** der Operation (`listDeliveries` 200: „Zustellprotokoll (mandantengefiltert)“), zur **Rollenzeile** (`service_admin` ohne Protokollrecht) und zur **`x-contract-rules`**-Regel („In den mandantenübergreifenden Antworten ist `organisation_id` **im Datensatz** gefüllt“) — sie folgt derselben Regel wie `Endpoint.organisation_id`. Die Alternative („immer gefüllt“) hätte zwei Beispiele und die Pflichtfeldliste verändert und eine **neue** Zusage geschaffen, die keine Entscheidung trägt. |
| | | | **Parallel:** ADR-015 §1 (Rollenmatrix) korrigiert — siehe **N-19**. | |

---

## 2. Die Schema-Befunde (N-13, N-14, N-15, N-16, N-17)

Beide Dateien — `.agent/knowledge/schema.sql` (kanonisch) und
`docs/architecture/schema.sql` (Spiegel) — sind **byte-identisch** geändert.

### 2.1 N-13 (muss) — markierte Versuche bleiben sichtbar und zählen in den Kennzahlen

| | |
|---|---|
| **Fundstelle** | `schema.sql:256-283` (Tabelle `delivery_attempts` — keine Spalte `deleted_at`), `:872-875` (Policy ohne Bezug zum Zustand des Vaters) |
| **Entscheidung** | **Die Policy folgt dem Zustand des Vaters.** `delivery_attempts` bekommt **keine** eigene `deleted_at`-Spalte; die Sichtbarkeit folgt dem Zustelllauf. |
| **Warum diese der Alternative vorzuziehen ist** | Eine eigene `deleted_at`-Spalte auf den Versuchen wäre eine **zweite Wahrheit**: sie müsste bei jedem Markieren mitgeschrieben werden und könnte von `delivery_runs.deleted_at` abweichen. Der Vertrag sagt „sie verlieren **mit ihrem Zustelllauf** die Sichtbarkeit“ — genau das bildet die Policy ab, ohne einen zweiten Zustand. |
| **Umsetzung** | `schema.sql:934-943`: die Policy `delivery_attempts_tenant_isolation` prüft zusätzlich `EXISTS (SELECT 1 FROM app.delivery_runs dr WHERE dr.delivery_run_id = delivery_attempts.delivery_run_id AND dr.deleted_at IS NULL)`. Die Teilabfrage läuft mit den Rechten des Aufrufers; die RLS-Policy auf `delivery_runs` gilt für sie mit — ein Versuch eines fremden Mandanten bleibt damit doppelt ausgeschlossen. |
| **Beide Stufen** | **Markieren:** der Lauf wird markiert ⇒ die Versuche werden für die Applikationsrolle sofort unsichtbar (kein eigener Schreibvorgang nötig). **Purge:** die Versuche werden mit ihrem Lauf physisch gelöscht (expliziter Purge + `ON DELETE CASCADE`) — die Zähler `marked_attempts` / `purged_attempts` bleiben konsistent. |
| **Nachweis** | PostgreSQL 16.15: vor dem Markieren **1** Versuch sichtbar, nach dem Markieren **0**; die Kennzahl „erfolgreiche Versuche“ zählt **0**; der Zähler `marked_attempts` weist **1** aus (nicht 0 — der Durchlauf meldet, was er markiert hat). Siehe § 4.2. |

### 2.2 N-14 (muss) — die Totenlisten-Funktion ist fail-open

| | |
|---|---|
| **Fundstelle** | `schema.sql:568-629` — `AND (p_status IS NULL OR d.status = p_status)`; der Parameter war optional, ohne Wert lieferte die Funktion **alle** Zustände |
| **Entscheidung** | **Der Status ist fest verdrahtet.** Die Funktion hat **keinen** Statusparameter mehr; die Bedingung lautet `AND d.status = 'abandoned'`. |
| **Umsetzung** | `schema.sql:568-655`: neuer Funktionskopf ohne `p_status`, `DROP FUNCTION IF EXISTS … (UUID, TEXT, TEXT, INTEGER, TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ, UUID)` **vor** der Neudefinition (sonst bliebe in einer bestehenden Datenbank die fail-open Variante ausführbar). Der Zeitraumfilter nutzt jetzt `d.abandoned_at` **ohne** `COALESCE` — bei `abandoned` ist `abandoned_at` durch `delivery_runs_abandoned_at_ck` **immer** gesetzt. Eigentuemer-, `REVOKE`- und `GRANT`-Anweisungen auf die neue Signatur umgestellt (`schema.sql:1068`, `:1106`, `:1118`). |
| **Vertragsbezug** | Die Projektion ist damit **per Konstruktion** die des Vertrags: `status` immer `abandoned` (`DeadDeliveryStatus`), `abandoned_at` immer gefüllt (Pflichtfeld). |
| **Nachweis** | PostgreSQL 16.15: `list_dead_deliveries_cross_tenant()` **ohne jeden Parameter** liefert **2** Zeilen, beide `abandoned`, beide mit `abandoned_at`; **0** Zeilen mit einem anderen Status; **0** Zeilen ohne `abandoned_at`. Ein `sent`-Lauf desselben Mandanten ist **nicht** in der Liste. Siehe § 4.3. |

### 2.3 N-15 (muss) — die Replay-Historie verschwindet beim Purge

| | |
|---|---|
| **Fundstelle** | `schema.sql:238-241` — `delivery_runs_replay_of_fk` mit `ON DELETE SET NULL` |
| **Entscheidung** | **E-11(a): `replay_of` ohne Fremdschlüssel.** Die UUID ist ein **historischer Verweis**, kein Live-Bezug. |
| **Umsetzung** | `schema.sql:238-245`: die Constraint `delivery_runs_replay_of_fk` ist **entfallen**; `delivery_runs_replay_of_uq UNIQUE (replay_of)` bleibt **unverändert**. Der Spaltenkommentar (`schema.sql:252-259`) sagt jetzt ausdrücklich, dass der Verweis die Aufbewahrung des Vorgängers überlebt und warum ein Fremdschlüssel ihn zerstören würde. |
| **Nachweis** | PostgreSQL 16.15: `pg_constraint` zeigt **nur** `delivery_runs_replay_of_uq` (Typ `u`), **keine** Fremdschlüssel-Constraint. Nach dem Löschen des Vorgängerlaufs zeigt der überlebende Replay **weiterhin** `replay_of` = ID des gelöschten Laufs. Das zweite Replay desselben Laufs scheitert weiterhin mit **23505**. Siehe § 4.4. |

### 2.4 N-16 (sollte) — logische Löschung ist der Applikationsrolle nicht möglich

| | |
|---|---|
| **Fundstelle** | `schema.sql:860-875` (Policies mit `AND deleted_at IS NULL` im `USING`-Teil) |
| **Entscheidung** | **Dokumentation** — in ADR-002 §7, in der Zielstruktur (`folder-structure.md`, Konvention „Logische Löschung“) **und** im Schema-Kopfkommentar. |
| **Inhalt** | PostgreSQL prüft den `USING`-Ausdruck einer Policy auch gegen die **neue** Zeile. Ein `UPDATE … SET deleted_at = now()` durch `echobox_app` endet deshalb mit `new row violates row-level security policy`. Markiert wird **ausschließlich** über die Eigentümerfunktion `app.enforce_retention_cross_tenant` (SECURITY DEFINER, Eigentümer `echobox_owner`). Das ist **Absicht**: die logische Löschung ist ein mandantenübergreifender Vorgang (ADR-012) und gehört in die schmale Eigentümerfunktion, nicht in eine Allzweckrolle. |
| **Nachweis** | PostgreSQL 16.15: `SET ROLE echobox_app; BEGIN; SELECT app.set_tenant('m-1'); UPDATE app.delivery_runs SET deleted_at = now() …` ⇒ **ERROR: new row violates row-level security policy for table "delivery_runs"**. Siehe § 4.1. |

### 2.5 N-17 (sollte) — die mandantenübergreifenden Listen haben keinen stützenden Index

| | |
|---|---|
| **Fundstelle** | `schema.sql:505-559` und `:568-629` (die Sortierungen der Cross-Tenant-Funktionen), `:901-962` (alle Indizes mit `organisation_id` als erstem Präfix) |
| **Entscheidung** | **Die Indizes folgen der tatsächlichen Sortierung** — vorher geprüft, wie der Stresstest es verlangt: |
| | Endpunktliste: `ORDER BY e.updated_at DESC, e.endpoint_id DESC` ⇒ `endpoints_cross_tenant_updated_idx ON app.endpoints (updated_at DESC, endpoint_id DESC) WHERE deleted_at IS NULL` |
| | Totenliste: `ORDER BY d.created_at DESC, d.delivery_run_id DESC` ⇒ `delivery_runs_cross_tenant_created_idx ON app.delivery_runs (created_at DESC, delivery_run_id DESC) WHERE deleted_at IS NULL` |
| **Umsetzung** | `schema.sql:1030-1043` (neuer Indexblock mit der Begründung, welche Sortierschlüssel er stützt). |
| **Nachweis** | PostgreSQL 16.15 mit 3.000 Endpunkten und 3.000 Läufen: `EXPLAIN` ⇒ `Limit → Index Only Scan using endpoints_cross_tenant_updated_idx` bzw. `Limit → Index Scan using delivery_runs_cross_tenant_created_idx` — **kein** `Sort`-Knoten mehr, **kein** Sequenzscan. Vorher: `Seq Scan` + `Sort`. Siehe § 4.5. |
| **Bewusst nicht gemacht** | Der `OR`-Filter auf die optionalen Parameter (`(p_status IS NULL OR …)`) bleibt. Ein Verzweigen auf getrennte Abfragen brächte **nur** dann etwas, wenn es einen Index auf `status` gäbe — den gibt es nicht, und es soll ihn nicht geben (der Status der Totenliste ist fest verdrahtet). Die teure Operation — die Sortierung — ist durch die Teilindizes behoben. |

---

## 3. Die ADR-Befunde (N-18, N-19, N-20)

| ID | Fundstelle | Korrigiert in | Wie |
|---|---|---|---|
| **N-18** (sollte) | `ADR-002:100-115` — die Grenzenliste nennt nur `app.set_tenant('m-2')` | `docs/adr/ADR-002-mandantentrennung-rls.md` — Abschnitt „Konsequenzen“, **neuer Absatz** „Grenze (ehrlich benannt) — die verbleibende Lücke zwischen RLS und Anwendung“ | Die Applikationsrolle kann auch `app.cross_tenant = 'service_admin'` setzen und damit die mandantenübergreifende Sicht öffnen — Endpunktliste, Totenliste und Aufbewahrung **aller** Mandanten. Das ist als die **einzige** Erweiterung gegenüber dem Normalfall und die **einzige** Stelle benannt, an der eine Fehlkonfiguration mandantenübergreifend sichtbar macht. Die **Härtung** (eigene Laufzeitrolle `echobox_ops`) ist als **offene Entscheidung E-12 (Gate 2)** vermerkt — **nicht** entschieden. |
| **N-19** (sollte) | `ADR-015:31-35` — Rollenmatrix versprach dem `service_admin` ein mandantenübergreifendes Protokoll | `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md` — Rollenmatrix und §2 | Die Zelle „Protokoll lesen“ steht jetzt auf „**ja** (eigener Mandant)“; die Kennzahlen auf „**ja** (eigener Mandant)“. Darunter steht als **Korrekturhinweis** (N-19): die mandantenübergreifende Sicht gilt **nur** für die **drei** Operationen der Eigentümerfunktionen — das **Zustellprotokoll bleibt mandantengefiltert** (E-10); es gibt **kein** mandantenübergreifendes Protokoll, weder im Vertrag noch im Schema. Die Zeile in der Zusatztabelle („Endpunkt-/Protokoll-Listen … mandantengefiltert“) nennt jetzt ausdrücklich **auch den `service_admin`**. **Kein** Vertrags-, **kein** Schema-, **kein** PRD-Änderungsbedarf. |
| **N-20** (sollte) | `ADR-006:98-112`, `ADR-015:123-131` — die Sperrenwartezeit des verlierenden Replays war nicht erwähnt | `docs/adr/ADR-006-zustellprozess-hintergrundprozess.md` §6 **und** `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md` §6 | Beide ADRs nennen jetzt: die Einmal-Regel äußert sich als **SQLSTATE 23505** (`unique_violation`) und wird **ausschließlich** auf `delivery_state_conflict` (HTTP 409) abgebildet — **nicht** auf 500; die Transaktion des Replays setzt **vor** dem `INSERT` ein `lock_timeout` und ein `statement_timeout`, die **beide kürzer sind als das HTTP-Timeout** der Operation, damit der verlierende Replay mit **409** antwortet statt mit einem Timeout. Als **Vorgabe für Schritt 6** vermerkt: beide Zeitlimits kommen aus der **Konfiguration** (`DeliveryProperties`), **kein** Wert ist im Code verdrahtet. |

---

## 4. Die Entscheidungen (E-10, E-11, E-12, E-13)

| ID | Entscheidung | Umsetzung in diesem Schritt |
|---|---|---|
| **E-10** | Das **Zustellprotokoll** bleibt **mandantengefiltert** — die mandantenübergreifende Sicht gilt **nur** für Endpunktliste, Totenliste und Aufbewahrung. Kein vierter Eigentümer, keine Vertragsänderung. | **Umsetzt** in: `DeliveryRun.organisation_id` (V-25), ADR-015 Rollenmatrix (N-19), ADR-015 §2, `docs/architecture/architecture.md` (Zeile „Mandantenübergreifende Sicht“), `docs/architecture/folder-structure.md` (`CrossTenantScope` unverändert, `ProtocolQueryService` unverändert). **Kein** vierter Eigentümer, **keine** Vertragsänderung, **keine** Schema-Erweiterung. |
| **E-11** | Die **Replay-Historie überlebt den Purge**: `replay_of` ist ein **historischer Verweis** ohne Fremdschlüssel. | **Umsetzt** in: `schema.sql:238-245` (Constraint entfallen) und `schema.sql:252-259` (Spaltenkommentar), ADR-006 §6, ADR-012 §4 (implizit), `docs/architecture/domain_model.md` (ER-Diagramm „replay_of (Verweis)“, Beziehungs- und Invariantentabelle I-5). `UNIQUE (replay_of)` bleibt. |
| **E-12** | Soll die mandantenübergreifende Sicht **härter** abgesichert werden (eigene Laufzeitrolle `echobox_ops`)? Empfehlung: jetzt benennen, Härtung als Risiko vormerken. | **Offengelegt, nicht entschieden**: ADR-002 nennt die Grenze jetzt **ehrlich** (N-18) und verweist auf **E-12 (Gate 2)** als offene Entscheidung. `.agent/artifacts/architecture.md` §8 führt E-12 als offenen Punkt mit Owner **Fachbereich + Plattform**. |
| **E-13** | Die **Karenzzeit** der Aufbewahrung bekommt **keinen** Vorgabewert. Der Vertrag bleibt bei „kein Wert festgelegt“; das Schema-Verhalten (Purge im nächsten Durchlauf) wird **explizit** gemacht, nicht stillschweigend. | **Umsetzt, ohne einen Wert zu erfinden**: `schema.sql:698-703` (Kommentar im Funktionskopf: `COALESCE(p_purge_delay_days, 0)` ⇒ Purge im **nächsten** Durchlauf — „das dokumentierte Verhalten **ohne** Wert, **kein** beschlossener Vorgabewert“), ADR-012 §6 (neuer Aufzählungspunkt), `docs/architecture/folder-structure.md` (`RetentionService`), `docs/architecture/architecture.md` (Zeile „Aufbewahrung“). Der **Vertrag** nennt weiterhin **keinen** Wert (`purge_delay_days: null`, „Ein Vorgabewert ist nicht festgelegt“) — unverändert. |

---

## 5. PostgreSQL-16-Nachweis

**Instanz:** PostgreSQL **16.15** (Ubuntu 16.15-0ubuntu0.24.04.1), frische
Datenbank, Unix-Socket, Trust-Auth. Das Schema wurde mit `psql -f
.agent/knowledge/schema.sql` **ohne Fehler** angewendet (Anwendbarkeit geprüft,
**vor** und **nach** der Änderung).

### 5.1 RLS bleibt fail-closed, die Mandantentrennung gilt

| Prüfung | Ergebnis |
|---|---|
| `echobox_app` **ohne** `set_tenant`: `endpoints` / `events` / `delivery_runs` / `delivery_attempts` | **0 / 0 / 0 / 0** Zeilen (fail-closed) |
| `echobox_app` **ohne** `app.cross_tenant`: die drei Eigentümerfunktionen | **0 / 0 / 0** Zeilen (fail-closed) |
| `set_tenant('m-1')`: `endpoints` / `events` / `delivery_runs` / `delivery_attempts` | **1 / 1 / 1 / 1** — nur m-1 |
| `set_tenant('m-1')`: `events` mit `organisation_id = 'm-2'` | **0** Zeilen |
| `set_tenant('m-1')`: `delivery_attempts` mit `organisation_id = 'm-2'` | **0** Zeilen |
| Rollen `echobox_owner` / `echobox_app` / `echobox_worker` | `rolsuper = f`, `rolbypassrls = f`, `rolcanlogin = f` — **keine** Rolle umgeht die RLS |
| `has_function_privilege` für die drei Cross-Tenant-Funktionen | `public_may = f`, `app_may = t`, `worker_may = f` — der Worker ist **nicht** freigegeben |

### 5.2 N-13 — markierte Versuche verlieren die Sichtbarkeit

| Prüfung | Ergebnis |
|---|---|
| vor dem Markieren: Versuche für `m-1` sichtbar | **1** |
| Durchlauf `enforce_retention_cross_tenant(false, 1, 1, 30)` | `m-1`: `marked_events = 1`, `marked_delivery_runs = 1`, **`marked_attempts = 1`**, `purged_* = 0` |
| nach dem Markieren: Versuche für `m-1` sichtbar | **0** |
| nach dem Markieren: Zustellläufe für `m-1` sichtbar | **0** |
| nach dem Markieren: Kennzahl „erfolgreiche Versuche“ | **0** — der markierte Versuch zählt **nicht** mehr mit |
| als Eigner: `delivery_attempts` | **2** — die Zeilen sind physisch da, nur für die Applikationsrolle unsichtbar |

### 5.3 N-14 — die Totenliste liefert ausschließlich `abandoned`

| Prüfung | Ergebnis |
|---|---|
| `list_dead_deliveries_cross_tenant()` **ohne jeden Parameter** | **2** Zeilen — beide `abandoned` |
| davon mit einem Status **≠** `abandoned` | **0** |
| davon mit `abandoned_at IS NULL` | **0** |
| ein `sent`-Lauf desselben Mandanten in der Liste | **nein** (nicht enthalten) |
| Signatur der Funktion | `p_endpoint_id, p_event_type, p_limit, p_from, p_to, p_after_created_at, p_after_delivery_run_id` — **kein** `p_status` |

### 5.4 N-15 / N-16 — Replay-Historie und logische Löschung

| Prüfung | Ergebnis |
|---|---|
| Constraints auf `replay_of` | **nur** `delivery_runs_replay_of_uq UNIQUE (replay_of)` — **kein** Fremdschlüssel |
| zweites Replay desselben Vorgängerlaufs | **ERROR: duplicate key value violates unique constraint "delivery_runs_replay_of_uq"** ⇒ SQLSTATE **23505** ⇒ HTTP 409 `delivery_state_conflict`; **kein** weiterer Lauf angelegt |
| `replay_of` des überlebenden Replays **nach** dem Löschen des Vorgängerlaufs | **unverändert** = ID des gelöschten Laufs (E-11) |
| logische Löschung durch `echobox_app` (`UPDATE … SET deleted_at`) | **ERROR: new row violates row-level security policy for table "delivery_runs"** (N-16) |

### 5.5 N-17 — die Indizes tragen die Sortierung

| Prüfung | Ergebnis |
|---|---|
| Endpunktliste (3.000 Zeilen) | `Limit → Index Only Scan using endpoints_cross_tenant_updated_idx on endpoints e` — **kein** `Sort`, **kein** `Seq Scan` |
| Totenliste (3.000 Zeilen) | `Limit → Index Scan using delivery_runs_cross_tenant_created_idx on delivery_runs d` — **kein** `Sort`, **kein** `Seq Scan` |

### 5.6 Projektionen bleiben ohne Klartext und ohne Secrets

| Prüfung | Ergebnis |
|---|---|
| `list_endpoints_cross_tenant()` (2 Mandanten) | **2** Zeilen, je mit `organisation_id` |
| Treffer auf `deadbeef` / `cafe` / `f00d` (Chiffre und Nonce der Endpunkt-Secrets und der Nutzlast) in der Projektion | **0** |
| `list_dead_deliveries_cross_tenant()` | **2** Zeilen (beide Mandanten), ausschließlich `abandoned` |

---

## 6. Neue Befunde — dokumentiert, **nicht** entschieden

| ID | Befund | Fundstelle | Warum nicht entschieden |
|---|---|---|---|
| **V-26** | **`Endpoint.organisation_id`: Beschreibung und Beispiele widersprechen sich.** Die Feldbeschreibung sagt „**Nur** für `service_admin` gefüllt; für andere Rollen ist das Feld `null`“. Die Beispiele zeigen aber `organisation_id: m-1` — in `pauseEndpoint` (`:720`, ein `receiver_admin`-Vorgang: der Endpunkt seines **eigenen** Mandanten) und in `listEndpoints` (`:580`). Nur `createEndpoint` (`:520`) zeigt `null`. Zusätzlich sagt die Antwortbeschreibung von `listEndpoints` (`:572`) „Endpunktliste (mandantengefiltert)“, während die Operationsbeschreibung (`:549-553`) dem `service_admin` **alle** Mandanten zusagt. | `docs/api/openapi.yaml:572`, `:580`, `:720` vs. `:2164-2167` | Der Widerspruch ist **echt**, aber die Auflösung ist eine **Semantikentscheidung**: entweder ist die Beschreibung richtig (dann sind zwei Beispiele falsch) oder die Beispiele sind richtig (dann ist die Beschreibung falsch und `Endpoint.organisation_id` ist **immer** gefüllt). Beides hätte Folgen für die Clients und für die Implementierung. **Nicht** entschieden — gehört in die **Delta-Prüfung (Schritt 4)**, vor dem Einfrieren. Hinweis: die in diesem Schritt gewählte Variante für `DeliveryRun.organisation_id` (V-25) folgt der **Beschreibung** von `Endpoint.organisation_id` — sie ist damit von dieser Entscheidung **abhängig** und muss mit ihr zusammen geprüft werden. |

---

## 7. Liste aller Änderungen dieses Schritts

### 7.1 Vertrag — `docs/api/openapi.yaml`

Geändert: **3 Sätze** — `info.description` (`:480`, `:697`), die
Feldbeschreibung `DeliveryRun.organisation_id` (`:2446-2456`) und der
Abdeckungseintrag **AC-022-3** (`:3241`).

**Unverändert:** die Zahl der Operationen (**17**), alle `$ref` (**176**,
alle auflösbar), alle Schemas (**37**, keins ungenutzt), alle
Antwort-Komponenten (**11**) und Parameter (**7**), die `x-ac-id`- und
`x-requirement-ids`-Zuordnungen, `x-acceptance-coverage` (**101**) und
`x-requirement-coverage` (**24**), die Beispielwerte.

### 7.2 Schema — `.agent/knowledge/schema.sql` + `docs/architecture/schema.sql`

Geändert: Kopfkommentar (logische Löschung, Versuchssichtbarkeit) ·
RLS-Abschnitt (Kommentarblock zu N-16 und N-13) · `app.delivery_runs`
(Constraint `delivery_runs_replay_of_fk` **entfallen**, Kommentar zu
`replay_of`) · Policy `delivery_attempts_tenant_isolation` (Sichtbarkeit folgt
dem Lauf) · `app.list_dead_deliveries_cross_tenant` (neue Signatur **ohne**
`p_status`, Status fest verdrahtet, Zeitraumfilter ohne `COALESCE`, Kommentar
neu) · `enforce_retention_cross_tenant` (Kommentar zur Karenzzeit **ohne**
Wert, Kommentar zur Versuchssichtbarkeit) · **neu**: zwei partielle Indizes
für die mandantenübergreifenden Listen · Rechte (`ALTER FUNCTION … OWNER`,
`REVOKE … FROM PUBLIC`, `GRANT EXECUTE` auf die neue Signatur).

**Unverändert:** die Zahl der Tabellen (**10**), der Policies (**10**), der
Funktionen (**7**), der CHECK-Constraints, der Fremdschlüssel (außer dem
entfallenen auf `replay_of`), der Spalten (außer keiner).

### 7.3 ADRs — `docs/adr/`

Geändert: **ADR-002** (§7: fest verdrahteter Status, logische Löschung;
neuer Absatz „Grenze (ehrlich benannt) — die verbleibende Lücke“; Verifikation
um drei Zeilen erweitert) · **ADR-006** (§6: historischer Verweis, 23505 ⇒
409, Sperrenwartezeit) · **ADR-012** (§3: Versuche folgen dem Lauf; §6:
Karenzzeit ohne Wert; Konsequenz: fest verdrahteter Totenlisten-Status) ·
**ADR-015** (Rollenmatrix korrigiert, §2, §6: 23505 ⇒
`delivery_state_conflict` + `lock_timeout`/`statement_timeout`) · **README.md**
(neuer Abschnitt „Korrekturen aus dem zweiten Rücksprung“).

**Unverändert:** die Zahl der Entscheidungen (**17** + 1 Verweisdatei).

### 7.4 Architekturdokumente — `docs/architecture/`

Geändert: `architecture.md` (Zeilen „Mandantenübergreifende Sicht“ und
„Aufbewahrung“) · `domain_model.md` (ER-Diagramm `replay_of (Verweis)`,
Beziehungszeile, Invariante **I-5**, **I-9** mit 23505, **neu I-11** zur
Versuchssichtbarkeit) · `folder-structure.md` (`DeliveryService` 23505 +
Zeitlimits, `DeadDeliveryQueryService` fest verdrahteter Status,
`EndpointMetricsService` markierte Versuche, `RetentionService` Karenzzeit +
logische Löschung, Konvention „Logische Löschung“) · `GLOSSARY.md`
(Aufbewahrung: Versuche folgen ihrem Lauf) · `schema.sql` (Spiegel).

**Unverändert** (die in Schritt 4 korrigierten Dateien **nicht**
zurückgedreht): alle fünf Mermaid-Diagramme.

### 7.5 Artefakte — `.agent/artifacts/`

Geändert: `architecture.md` · `api-review.md`. **Ersetzt**:
`ruecksprung-bilanz.md` (dieses Dokument). `briefing.md` wird vom
Orchestrator geschrieben.

---

## 8. Selbstprüfung

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (byte-identisch) |
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken …“ (23 Stories / 101 AKs / 24 Anforderungen) |
| YAML gültig | `python3 -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml'))"` | **gültig** |
| Vertragsform | Skript | **17** Operationen; alle mit `operationId` (eindeutig), `x-ac-id`, `x-requirement-ids`; **15/15** authentifizierte Operationen mit 429 |
| `$ref` / ungenutzte Objekte | Skript | **176** Verweise, **alle** auflösbar; **kein** ungenutztes Schema (37), **keine** ungenutzte Antwort-Komponente (11), **kein** ungenutzter Parameter (7) |
| Abdeckung | Skript | `x-acceptance-coverage` **101/101**, `x-requirement-coverage` **24/24**, keine Dubletten |
| Beispiele | Skript (Pflichtfelder, Enum, unbekannte Felder) | **keine** Abweichung |
| Schema anwendbar | `psql -f .agent/knowledge/schema.sql` (PostgreSQL 16.15) | **ohne Fehler** |
| RLS-Verhalten | § 5.1 | **belegt** (fail-closed, Mandantentrennung, keine `BYPASSRLS`, Rechte) |
| N-13 / N-14 / N-15 / N-16 / N-17 | § 5.2–5.5 | **belegt** |
| Projektionen ohne Klartext/Secrets | § 5.6 | **belegt** |
| Anforderungen unberührt | `git diff --stat -- docs/requirements/ docs/Anforderung.md` | **leer** |
| Kein Anwendungscode | `git diff --stat -- src/` | **leer** (`src/` existiert nicht) |
| Diagramme nicht zurückgedreht | `git diff --stat -- docs/architecture/diagrams/` | **leer** |
| Commit-Scope | `git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/ .agent/knowledge/` | **nur** diese Pfade |
