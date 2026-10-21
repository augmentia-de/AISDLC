# Rücksprung-Bilanz — Schritt 2 (Konzept & Architektur), **Rücksprung 4**

> **Vorgangsprotokoll** — der Nachweis, dass kein Befund des **zweiten**
> Stresstests liegen geblieben ist. **Kein lebender Wahrheitsort**: die
> geltenden Entscheidungen stehen in `docs/adr/`, der Vertrag in
> `docs/api/openapi.yaml`, die offenen Punkte in `.agent/artifacts/architecture.md`
> §8.
>
> **› Fortsetzung — Rücksprung 3 (Abschnitt 9):** der Mensch hat den
> Vertragswiderspruch **V-26** entschieden; er ist umgesetzt. Der Vertrag war
> dabei **noch nicht eingefroren** — der Einfrierpunkt folgt nach Schritt 4.
>
> **› Fortsetzung — Rücksprung 4 (Abschnitt 10):** der Mensch hat den
> Vertragswiderspruch **V-27** entschieden; er ist umgesetzt. Danach wurde der
> Vertrag **einmal vollständig** durchgesweept: **11** weitere Widersprüche
> (**V-28…V-38**) sind behoben, **6** neue Befunde (**V-39…V-44**) sind
> dokumentiert und **nicht** entschieden. Der Vertrag ist damit
> **einfrierreif** — eingefroren wird er nach Schritt 4 (Delta-Prüfung).
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

**› Stand nach Rücksprung 3 (Abschnitt 9):** **V-26 ist entschieden und
umgesetzt** — `organisation_id` benennt **immer** den **besitzenden Mandanten**
der Ressource. Neu dokumentiert und **nicht** entschieden: **V-27**
(Abschnitt 9.5). Der Vertrag wird **nach Schritt 4** eingefroren.

**› Stand nach Rücksprung 4 (Abschnitt 10):** **V-27 ist entschieden und
umgesetzt** (10 Fundstellen), danach ist der Vertrag **einmal vollständig**
durchgesweept: **11** Widersprüche (**V-28…V-38**) behoben, **6** neue Befunde
(**V-39…V-44**) dokumentiert und **nicht** entschieden.

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
| | | | **Parallel:** ADR-015 §1 (Rollenmatrix) korrigiert — siehe **N-19**. **› Überholt durch V-26** (Rücksprung 3, Abschnitt 9): die hier gewählte Variante („nur in mandantenübergreifenden Antworten gefüllt") ist **nicht mehr gültig** — `DeliveryRun.organisation_id` ist jetzt **immer** gefüllt und benennt den **besitzenden Mandanten**. | |

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

---

## 9. Rücksprung 3 — V-26: `organisation_id` benennt **immer** den besitzenden Mandanten

> **Eingang:** der Mensch hat **V-26 entschieden** (Freigabe vor diesem Schritt) —
> die Entscheidung wird hier **nicht** neu diskutiert, sondern **umgesetzt**.
> Agent: `aisdlc-architecture` · Stand: 2026-10-21 · Vertrag: `docs/api/openapi.yaml`
> (17 Operationen) — **noch nicht eingefroren**, der Einfrierpunkt folgt nach
> Schritt 4 (Delta-Prüfung).

### 9.1 Fundstelle — der Widerspruch im Wortlaut

| # | Stelle (vorher) | Befund |
|---|---|---|
| 1 | `openapi.yaml:2164-2167` — `Endpoint.organisation_id` | Feldbeschreibung: „Mandant des Endpunkts. **Nur** für `service_admin` gefüllt; für andere Rollen ist das Feld `null`, weil der Mandant bereits aus der Identität bekannt ist." — **falsch** |
| 2 | `openapi.yaml:520` — Beispiel 201 `createEndpoint` | zeigte `organisation_id: null` — die einzige Stelle, die die alte Beschreibung stützte |
| 3 | `openapi.yaml:580` (Beispiel 200 `listEndpoints`) und `:720` (Beispiel 200 `pauseEndpoint`) | zeigten `organisation_id: m-1` — auch für den **eigenen** Mandanten (ein `receiver_admin`-Vorgang auf dem Endpunkt des eigenen Mandanten) → **Widerspruch** zur Beschreibung |
| 4 | `openapi.yaml:549-553` (Operationsbeschreibung `listEndpoints`) vs. `:572` (Antwortbeschreibung 200) | die Operationsbeschreibung sagt dem `service_admin` **alle** Mandanten zu, die Antwortbeschreibung sagt „Endpunktliste (**mandantengefiltert**)" → **nicht** in Einklang |
| 5 | `openapi.yaml:2446-2457` — `DeliveryRun.organisation_id` | die Beschreibung aus **V-25** sagte das Gegenteil: „Das Feld ist **nur** in mandantenübergreifenden Antworten gefüllt … Im Zustellprotokoll bleibt das Feld deshalb `null`" |
| 6 | `openapi.yaml:89-90` (Abschnitt „Mandantenübergreifende Listen"), `:3298` (`x-contract-rules.tenant`), `:2625-2631` (`DeadDelivery.organisation_id`) | weitere Stellen mit der antwort- bzw. rollenbezogenen Formulierung |

**Kern des Widerspruchs:** Beschreibung, Beispiele und Operations-/Antwortbeschreibung
sagten drei verschiedene Dinge über dasselbe Feld.

### 9.2 Entscheidung (vom Menschen freigegeben)

**`organisation_id` ist IMMER gefüllt.** Das Feld benennt den **besitzenden
Mandanten** der Ressource. Es ist **kein** Sonderfeld für `service_admin`:

* im eigenen Mandanten: der eigene Mandant,
* in der mandantenuebergreifenden Liste (nur `service_admin`): der Mandant, zu dem
  die Ressource gehört.

Die Aussage „nur für `service_admin` gefüllt; für andere Rollen `null`" ist
**falsch** und wurde korrigiert. Die Entscheidung gilt für **beide** Felder mit
dieser Bedeutung: `Endpoint.organisation_id` **und** `DeliveryRun.organisation_id`
— die beiden Felder werden **gleichlautend** beschrieben.

### 9.3 Korrektur — was im Vertrag geändert wurde

| Stelle (nachher) | vorher | nachher |
|---|---|---|
| `Endpoint.organisation_id` (`:2178-2184`, Pflicht in `:2167`) | `nullable: true`, **nicht** in `required`, „**Nur** für `service_admin` gefüllt … sonst `null`" | **Pflichtfeld**, **nicht** `nullable`: „Der **besitzende Mandant** der Ressource — **immer** gefüllt: im eigenen Mandanten der eigene Mandant, in der mandantenübergreifenden Sicht (`service_admin`) der Mandant, zu dem der Endpunkt gehört (E-01)." |
| `DeliveryRun.organisation_id` (`:2464-2473`, Pflicht in `:2437`) | `nullable: true`, **nicht** in `required`, „**nur** in mandantenübergreifenden Antworten gefüllt … im Zustellprotokoll `null`" | **Pflichtfeld**, **nicht** `nullable`, **derselbe Leitsatz** wie bei `Endpoint` + Kontextsatz: das Zustellprotokoll ist **mandantengefiltert** (E-10) → dort ist es der Mandant aus der Identität |
| `DeadDelivery.organisation_id` (`:2642-2648`, Pflicht in `:2629`) | `nullable: true`, **nicht** in `required`, „in der Totenliste **immer** gefüllt" | **Pflichtfeld**, **nicht** `nullable`, derselbe Leitsatz (die Totenliste ist mandantenübergreifend, AC-016-1) |
| Beispiel 201 `createEndpoint` (`:528`) | `organisation_id: null` | `organisation_id: m-1` — die Antwort eines selbst erstellten Endpunkts gehört zum **eigenen** Mandanten |
| Beispiel 200 `listDeliveries` (`:1086`) und `getDeliveryRun` (`:1149`) | Feld **fehlte** | `organisation_id: m-1` ergänzt (Pflichtfeld) |
| Operationsbeschreibung `listEndpoints` (`:556-570`) | „Ein `receiver_admin` sieht **nur** die Endpunkte seines Mandanten; ein `service_admin` sieht die Endpunkte **aller** Mandanten" + „**In diesem Fall** ist `organisation_id` in **jedem** Datensatz gefüllt" | „Für **alle** Rollen **außer** `service_admin` ist die Liste **mandantengefiltert** … ein `service_admin` sieht die Endpunkte **aller** Mandanten (FR-004, E-01)" + „`organisation_id` … in **jedem** Datensatz gefüllt — es benennt den **besitzenden Mandanten**" (**kein** Sonderfall der mandantenübergreifenden Sicht) |
| Antwortbeschreibung 200 `listEndpoints` (`:583-586`) | „Endpunktliste (mandantengefiltert)." | „Endpunktliste — **mandantengefiltert** für alle Rollen **außer** `service_admin`, der die Endpunkte **aller** Mandanten sieht (E-01); `organisation_id` benennt je Datensatz den **besitzenden Mandanten**." |
| `info.description`, Abschnitt „Mandant" (`:30-34`) | kein Satz zur Feldbedeutung | neuer Absatz: „**`organisation_id` in den Antworten:** Das Feld ist **immer** gefüllt und benennt den **besitzenden Mandanten** der Ressource … Es ist **kein** Sonderfeld einer Rolle und **kein** Filter (D-01)." |
| `info.description`, Abschnitt „Mandantenübergreifende Listen" (`:95-97`) | „In den mandantenübergreifenden Antworten ist `organisation_id` deshalb **im Datensatz** gefüllt." | „`organisation_id` ist **immer** gefüllt — es benennt den **besitzenden Mandanten** der Ressource; in diesen mandantenübergreifenden Antworten steht damit der Mandant, zu dem der Datensatz gehört." |
| `x-contract-rules.tenant` (`:3315`) | „In den mandantenübergreifenden Antworten ist `organisation_id` **im Datensatz** gefüllt." | „`organisation_id` ist **immer** gefüllt und benennt den **besitzenden Mandanten** der Ressource — im eigenen Mandanten den eigenen, in den mandantenübergreifenden Antworten den Mandanten, zu dem der Datensatz gehört." |

**Geprüft und bewusst nicht geändert** (Nachzug ohne Änderungsbedarf):

| Stelle | Befund | Warum unverändert |
|---|---|---|
| `TenantRetention.organisation_id` (`:3018`) und die Beschreibung von `tenants[]` (`:2986`) | „Mandant — **im Datensatz**, damit der Client die Herkunft erkennt (AC-016-1)" | war bereits **Pflichtfeld**, **nicht** `nullable` und **ohne** Rollenbedingung — stimmt mit der Entscheidung überein |
| `x-delivery-protocol` (`:160-3137`) | nennt `organisation_id` **nicht** | der ausgehende Zustellvertrag ist davon nicht betroffen — nichts nachzuziehen |
| Fehlerbeschreibungen (`tenant_parameter_rejected`: `:59`, `:1833`, `:2005`, `:2133`, `:2157`, `:2239`, `:2336`, `:2703`, `:2845`) und das Fehlerbeispiel (`:1864`) | betreffen den **Mandantenparameter im Request** (Body/Query/Header → 422, D-01) | unberührt: das **Antwort**-Feld `organisation_id` ist davon nicht betroffen |
| `listDeadDeliveries` (`:1220`), Totenlisten-Beispiel (`:1277`), Aufbewahrungs-Beispiele (`:1532`, `:1545`) | „`organisation_id` ist in **jedem** Datensatz gefüllt" | stimmt mit „immer gefüllt" überein |

**Umfang unverändert:** 17 Operationen, 23 Stories / 101 AKs / 24 Anforderungen —
**kein** neues Feld, **keine** neue Operation, **keine** Story, **kein**
Akzeptanzkriterium. **Kein** Schema-Anfassen (`diff` leer).

### 9.4 Nachweis

| Prüfung | Ergebnis |
|---|---|
| YAML | **gültig** (`yaml.safe_load`) |
| `$ref` | **176**, **alle** auflösbar |
| Operationen | **17**, alle mit `operationId` (eindeutig), `x-ac-id`, `x-requirement-ids` |
| `x-acceptance-coverage` / `x-requirement-coverage` | **101 / 24**, lückenlos, keine Dubletten |
| Beispiele gegen die Schemas (Pflichtfelder, `null` bei nicht-`nullable`, unbekannte Felder, Enum) | **0 Abweichungen** |
| `organisation_id` in `Endpoint` / `DeliveryRun` / `DeadDelivery` / `TenantRetention` | jeweils **Pflichtfeld**, **nicht** `nullable` |
| Suche nach der alten Zusage | **kein** Treffer auf „nur für `service_admin` gefüllt", „für andere Rollen `null`", „bleibt das Feld deshalb `null`" — die einzige verbliebene `null`-Stelle zu `organisation_id` (`createEndpoint`) ist ersetzt |
| `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken …" (23 Stories / 101 AKs / 24 Anforderungen) |
| `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (byte-identisch) |
| Anforderungen unberührt | `git diff --stat -- docs/requirements/ docs/Anforderung.md` → **leer** |
| Kein Anwendungscode | `git diff --stat -- src/` → **leer** |

### 9.5 Neue Befunde — dokumentiert, **nicht** entschieden

| ID | Befund | Fundstelle | Warum nicht entschieden |
|---|---|---|---|
| **V-27** | **Der `service_admin` bekommt IDs, die er nicht öffnen kann.** Die mandantenübergreifende **Endpunktliste** (und die **Totenliste**) liefert dem `service_admin` Endpunkt- bzw. Zustelllauf-IDs **fremder Mandanten**; der **Einzelabruf** (`getEndpoint`, `getDeliveryRun`) beantwortet eine Ressource eines fremden Mandanten aber mit **HTTP 404** (D-02). Der Vertrag sagt **nicht**, ob die 404 auch für den `service_admin` gilt; das Schema lässt nur die **drei** mandantenübergreifenden **Listen** zu (ADR-002 §7: `list_endpoints_cross_tenant`, `list_dead_deliveries_cross_tenant`, `enforce_retention_cross_tenant`) — ein mandantenübergreifender **Einzelabruf** existiert **nicht**. Die Rollenmatrix in ADR-015 §1 nennt für `service_admin` „Endpunkte verwalten: **ja** (alle Mandanten)", was in die andere Richtung deutet. | `openapi.yaml:625-628` (`getEndpoint`), `:1127-1131` (`getDeliveryRun`), `:556-570` (`listEndpoints`), `:1217-1221` (`listDeadDeliveries`); `docs/adr/ADR-002-mandantentrennung-rls.md` §7 (drei Eigentümerfunktionen); `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:35` | Das ist **kein** Widerspruch im engeren Sinn (beide Sätze können gleichzeitig gelten, wenn „fremd" am Mandanten aus der Identität gemessen wird) — es ist eine **offene Semantikfrage** mit realer Client-Folge: die Liste übergibt IDs, deren Detailabruf 404 endet. Die Auflösung (Vertragssatz „die 404 gilt auch für `service_admin`" vs. ein mandantenübergreifender Einzelabruf als **vierte** Eigentümerfunktion) ist eine **Semantik- und Architekturentscheidung** — sie gehört in die **Delta-Prüfung (Schritt 4)**, vor dem Einfrieren. **Hier nicht entschieden.** Hinweis: `docs/adr/` ist in diesem Schritt nicht änderbar — ADR-015 und ADR-002 blieben unberührt. |

---

## 10. Rücksprung 4 — V-27 und der Abschluss-Sweep des Vertrags

> **Eingang:** der Mensch hat **V-27 entschieden** (Freigabe vor diesem Schritt) —
> die Entscheidung wird hier **nicht** neu diskutiert, sondern **umgesetzt**.
> Danach: **einmaliger, vollständiger Sweep** des gesamten Vertrags — alle
> Achsen in **einem** Zug, nicht Fundstelle für Fundstelle.
> Agent: `aisdlc-architecture` · Stand: 2026-10-21 · Vertrag: `docs/api/openapi.yaml`
> (17 Operationen) — **noch nicht eingefroren**, der Einfrierpunkt folgt nach
> Schritt 4 (Delta-Prüfung).

### 10.1 V-27 — Fundstelle, Entscheidung, Korrektur

**Fundstellen (vorher)**

| # | Stelle | Befund |
|---|---|---|
| 1 | `getEndpoint` (`openapi.yaml:625-628`) | „Ein Endpunkt eines fremden Mandanten wird mit **HTTP 404** beantwortet" — **offen**, ob das auch für `service_admin` gilt |
| 2 | `getDeliveryRun` (`:1127-1131`) | dasselbe für den Zustelllauf |
| 3 | `listEndpoints` (`:556-570`) | nennt die mandantenübergreifende Sicht, **nicht** aber, was mit den gelieferten **fremden IDs** geschieht |
| 4 | `listDeadDeliveries` (`:1217-1221`) | dasselbe für die Totenliste |
| 5 | `info.description` Rollentabelle (`:38-42`) | „`service_admin` \| Endpunkte aller Mandanten **sehen**" — im Singular gelesen: auch der Detailabruf sei möglich |
| 6 | `info.description` Abschnitt „Mandant" (`:22-34`) | die 404 ohne Rollenbezug |
| 7 | `info.description` Abschnitt „Mandantenübergreifende Listen" (`:89-99`) | die Fremd-IDs werden nicht erwähnt |
| 8 | `x-contract-rules.tenant` | dasselbe |

**Entscheidung (vom Menschen freigegeben) — nicht neu diskutiert**

1. Die mandantenübergreifende Sicht gilt **nur** für die **drei**
   Listen-/Betriebsoperationen: **Endpunktliste, Totenliste, Aufbewahrung** (E-01).
2. Der **Einzelabruf** einer Ressource eines **fremden** Mandanten ergibt
   **auch für `service_admin`** **HTTP 404** (D-02).
3. Ein **vierter** mandantenübergreifender Leseweg wird **nicht** gebaut.
4. Der Vertrag **sagt das ausdrücklich**.

**Korrektur — 10 Stellen im Vertrag**

| Stelle (nachher) | Änderung |
|---|---|
| `info.description` Rollentabelle (`:38-48`) | Die Rechte-Zeile des `service_admin` nennt jetzt „Endpunkte **aller** Mandanten **in der Liste** sehen" und „Zustellprotokoll und Kennzahlen (eigener Mandant)". Neu darunter: „**Der Einzelabruf einer Ressource eines fremden Mandanten ergibt HTTP 404 — auch für `service_admin`** (D-02, V-27). Die mandantenübergreifende Sicht gilt **nur** für die **drei** Listen-/Betriebsoperationen … Ein **vierter** mandantenübergreifender Leseweg wird **nicht** gebaut." |
| `info.description` Abschnitt „Mandant" (`:28-31`) | „Das gilt für **alle** Rollen — **auch** für `service_admin`: der **Einzelabruf** einer Ressource eines fremden Mandanten endet mit 404 (V-27)." |
| `info.description` Abschnitt „Mandantenübergreifende Listen" (`:102-108`) | „Die Liste liefert damit auch **IDs fremder Mandanten** — der **Detailabruf** einer solchen ID endet mit **HTTP 404**, auch für den `service_admin` (D-02, V-27)." |
| `listEndpoints` (`:576-583`) | Neuer Absatz „**IDs fremder Mandanten:** … Der **Detailabruf** (`GET /api/v1/endpoints/{endpointId}`) einer solchen ID endet mit **HTTP 404** …" |
| `listDeadDeliveries` (`:1243-1247`) | Neuer Absatz „**IDs fremder Mandanten:** … `GET /api/v1/deliveries/{deliveryRunId}` …" |
| `getEndpoint` (`:641-645`) | „Das gilt **auch** für `service_admin` … ein **Einzelabruf** über die Mandantgrenze hinweg wird **nicht** gebaut (V-27)." |
| `getDeliveryRun` (`:1149-1153`) | dasselbe |
| `getEvent` (`:971-975`) | dasselbe — die Regel gilt für **jeden** Einzelabruf einer fremden Ressource (eine mandantenübergreifende Ereignisliste gibt es nicht) |
| `getEndpointMetrics` (`:1438-1441`) | dasselbe |
| `x-contract-rules.tenant` (`:3359-3360`) | beide Regeln um V-27 ergänzt (404 auch für `service_admin`; die mandantenübergreifende Sicht gilt **nur** für die drei Operationen) |

**Nachweis der Umsetzung**

- Die Klausel „**auch** für `service_admin`" steht jetzt in **7** Zeilen des Vertrags: den **vier** Operationen, die eine fremde Ressource adressieren (`getEndpoint`, `getDeliveryRun`, `getEvent`, `getEndpointMetrics`), und den **drei** Regeltexten (Rollen-Absatz, Abschnitt „Mandant", `x-contract-rules.tenant`). `V-27` kommt **11-mal** im Vertrag vor.
- „Ein **vierter** mandantenübergreifender Leseweg wird **nicht** gebaut" steht in **drei** Texten (Rollen-Absatz, `listEndpoints`, `x-contract-rules.tenant`).
- **Kein** neuer Leseweg: die Zahl der Operationen bleibt **17**; `getEndpoint` und `getDeliveryRun` tragen **keine** mandantenübergreifende Zusage.

**Bewusst nicht geändert (und warum)**

| Stelle | Warum |
|---|---|
| `docs/adr/ADR-015-…md` §1 (Rollenmatrix „Endpunkte verwalten: **ja** (alle Mandanten)") und §2 („Im Zustellprotokoll … ist das Feld `null`") | `docs/adr/` ist in diesem Schritt **nicht** änderbar. Beide Sätze sind durch **V-26** und **V-27** überholt → **V-44** (dokumentiert, nicht entschieden). |
| `docs/adr/ADR-002-…md` §7 | passt bereits: **genau drei** `SECURITY DEFINER`-Eigentümerfunktionen, **kein** mandantenübergreifender Einzelabruf ✓ |
| Schema (`.agent/knowledge/schema.sql` + Spiegel) | unverändert — der Vertrag beschreibt jetzt genau die Architektur, die das Schema ohnehin hat (kein vierter Eigentümer) ✓ |

### 10.2 Der Abschluss-Sweep — das Prüfraster

| Achse | Geprüft | Ergebnis |
|---|---|---|
| **Beschreibungen ↔ Beispiele** | alle 17 Operationen, alle 31 Beispiel-Objekte, alle Komponenten-Beispiele: Feldwerte, Statuswerte, Formate, Größen, Zeitstempel, Zahlen — **nachgerechnet** (Nutzlastbytes 35, Erfolgsquote 100/120, Backoff 1→2 min, Gesamtfenster 123 min ± 20 %, Fristen/Stichtage, Zählersummen je Mandant, Base64-Länge des Cursors, Secret-Längen, UUID-Form) | **3 Widersprüche** (V-30, V-31, V-32) |
| **Beschreibungen ↔ Schemas** | alle 37 Schemas: Pflicht/Nach nullable, Wertebereiche, Formate, Enum-Werte, `additionalProperties` | **5 Widersprüche** (V-33, V-34, V-35, V-37, V-38) + 2 offene Fragen (V-41, V-42) |
| **Beschreibungen ↔ Rollenmatrix** | jede im Vertrag ausgesprochene Rollenregel gegen ADR-015 §1/§2 und F-01…F-04 | **0 Widersprüche** — die drei ausgesprochenen Regeln (Totenliste, Replay, Aufbewahrung) stimmen mit ADR-015 überein; die Rollen**übersicht** in `info.description` war unvollständig und ist mit **V-27** korrigiert |
| **Beschreibungen ↔ bestätigte Entscheidungen** | D-01…D-15, F-01…F-04, E-01…E-11, E-13, V-26, V-27 gegen jede Operation, jedes Schema, jede Regel | **2 Widersprüche** (V-28, V-36) |
| **Fehlersemantik** | 400/401/403/404/409/413/422/429/500/503 je Operation gegen die Fehlersemantik-Tabelle und ADR-015 §5 | **1 Widerspruch** (V-29) + 1 offene Frage (V-39) |
| **Paginierung, Header, Signatur, Ablehnungssignal, Kennungen** | Form von `page`/`limit`/`cursor` an allen drei Listen, `Retry-After`, `Location`, die acht `X-EchoBox-*`-Header, `v1=<hex>`, `<timestamp>.<payload_bytes>`, `X-EchoBox-Rejection: permanent` | **2 Widersprüche** (V-30 Cursor, V-36 Ablehnungs-Header) |
| **`x-delivery-protocol`** | Retry-Matrix gegen ADR-008 §1, 3xx gegen ADR-009, Ablehnungssignal gegen ADR-009, Signatur gegen ADR-004/005, Zeitlimit (5 s + 10 s < 30 s) gegen ADR-008, Dubletten gegen ADR-005 | **1 Widerspruch** (V-36) — sonst deckungsgleich |
| **Abdeckung** | 23 Stories / 101 AKs / 24 Anforderungen gegen `docs/requirements/stories/*.json`; je Operation `operationId`, `x-ac-id`, `x-requirement-ids`; `$ref`-Auflösung; ungenutzte Objekte | **lückenlos** — 0 Befunde |

*Querverweis:* V-30 und V-36 sind zugleich Befunde der Achsen „Beispiele" bzw. „x-delivery-protocol" — sie sind in der Summe **nicht** doppelt gezählt.

### 10.3 Die behobenen Widersprüche (V-28 … V-38)

| ID | Fundstelle | Widerspruch | Korrektur |
|---|---|---|---|
| **V-28** (muss) | `createEndpoint` — Antworten (vorher `:543-544`) | Die Operation kündigte **409 `idempotency_conflict`** an — sie kann aber **keinen** Idempotenzschlüssel annehmen: `EndpointCreateRequest` hat nur `target_url`/`secret`/`event_types` (`additionalProperties: false`), ein `Idempotency-Key`-**Header** ist im Vertrag nicht definiert, D-05/FR-011 betreffen **Ereignisse**, und das Schema hat `UNIQUE (organisation_id, idempotency_key)` nur auf `events` — auf `endpoints` **keine** Eindeutigkeit. Die Zusage war **unerfüllbar**. | **409 entfernt.** Kein neuer Schlüssel, keine neue Zusage: der Idempotenzschlüssel bleibt der des Ereignisses (`EventIntakeRequest.idempotency_key`). *Hinweis:* ein echter Idempotenzschlüssel für die **Registrierung** wäre eine **neue** Zusage — hier nicht eingeführt (siehe **V-39**). |
| **V-29** (muss) | `replayDelivery` (vorher `:1368`) und `enforceRetention` (vorher `:1559`) | Beide Operationen haben einen **Request-Body**, deklarieren aber **keine 400**. Die Fehlersemantik in `info.description` gilt **vertragsweit**: 400 `malformed_request` = „syntaktisch fehlerhafte Anfrage (kein gültiges JSON)". | **400 `MalformedRequest`** bei beiden ergänzt. Der Body ist optional — **ohne** Body bleibt es bei 422/200; **mit** syntaktisch kaputtem Body ist es 400. |
| **V-30** (muss) | `listDeliveries` — Beispiel `page.next_cursor` | Der Cursor war **kein gültiges Base64** (55 Zeichen, nicht durch 4 teilbar → „Incorrect padding") **und** trug `updated_at`. Der Sortierschlüssel des Zustellprotokolls ist aber **`created_at`** (ADR-017 §4 „neueste zuerst (`created_at DESC`)"; Index `delivery_runs_tenant_created_at_idx`). | Cursor auf `{"created_at":"2026-10-21T09:15:22.481Z","delivery_run_id":"7d2b1c9a-…"}` umgestellt (Sortierschlüssel **+** UUID-Tie-Breaker, wie in `info.description` beschrieben) und korrekt Base64-kodiert (132 Zeichen, ≤ 512). |
| **V-31** (muss) | `rotateEndpointSecret` — Beispiel (vorher `:821`) und `SecretRotationRequest.secret.example` | Das Beispiel nannte `"neues-s3cr3t"` — **12 Zeichen**; das eigene Schema fordert `minLength: 16`. Das Beispiel verletzte den Vertrag. | Beide Stellen auf `"neues-s3cr3t-rotationswert"` (27 Zeichen) umgestellt. |
| **V-32** (muss) | `listDeadDeliveries` — Beispiel (vorher `:1275-1277`) und `replayDelivery` — Beispiel (vorher `:1361-1363`) | Dieselbe `delivery_run_id` (`7d2b1c9a-…`) stand in **drei** Beispielen in **drei verschiedenen Zuständen**: `failed` (2 Versuche) in der Protokollliste, `abandoned` (8 Versuche) in der Totenliste und als **Replay-Vorgänger** — replaybar ist aber nur ein **aufgegebener** Lauf. | Die Beispiele sind **entkoppelt**: der Lauf `b4c3d2e1-…` (Ereignis `5d8e9f0a-…`) ist der **aufgegebene** (Totenliste), sein Replay ist der neue Lauf `9a8b7c6d-…` mit **derselben** Ereignis-ID; der Lauf `7d2b1c9a-…` (Ereignis `3c9a7d21-…`) bleibt der `failed` aus Protokoll und Detail. Jede Ressource steht jetzt in **genau einem** Zustand. |
| **V-33** (sollte) | `components.schemas.Problem.violations` (vorher `:1963`) | Die Beschreibung sagte „nur bei `validation_failed`" — das **Beispiel** der 422 `tenant_parameter_rejected` führt aber ein `violations`-Array (`field: organisation_id`). | Beschreibung: „bei `validation_failed` **und** bei `tenant_parameter_rejected` (beide 422-Codes nennen den Feldverstoß)". |
| **V-34** (sollte) | `components.schemas.ValidationViolation.field` (vorher `:1972`) | „**JSON-Pointer** auf das betroffene Feld" — die Beispiele nennen den **Feldnamen** (`target_url`, `organisation_id`), keinen Pointer. | Beschreibung: „Das betroffene Feld (Feldname, z. B. `target_url`)". |
| **V-35** (sollte) | `components.schemas.DeliveryStatus` (vorher `:2029-2039`) | Die Beschreibung nennt **8** der **9** Enum-Werte — **`failed`** fehlte, obwohl der Wert im Vertrag vorkommt (Beispiel `listDeliveries`, DB-Check `delivery_runs_status_ck`). | `failed` beschrieben: „der letzte Versuch ist fehlgeschlagen, ein weiterer ist nach dem Backoff geplant (ADR-008)" — wortgleich mit dem Zustandsautomaten in `docs/architecture/domain_model.md`. |
| ** **V-36** (muss) | `x-delivery-protocol.response.outcomes[410].required_headers` (vorher `:326`) | Der Header `X-EchoBox-Rejection` war `required: false` — ADR-009 legt ihn als **festen Bestandteil** des Ablehnungssignals fest („Statuscode, Headername oder Body-Format"; „Der Vertragstest schlägt fehl, wenn … der Headername … fehlt"). | `required: true`, Beschreibung um „— **Pflicht** beim Signal (ADR-009)" ergänzt. |
| **V-37** (sollte) | `components.schemas.Event.deleted_at` (vorher `:2425`) | „Gesetzt, wenn das Ereignis durch die Aufbewahrung gelöscht wurde" — ADR-012 §3: ein markiertes Ereignis ist **sofort unsichtbar** (RLS filtert `deleted_at IS NULL`) und wird mit **404** beantwortet. Der Wert ist in einer Antwort also **nie** gesetzt. | Beschreibung: „Aufbewahrungsmarker — **in der Antwort immer `null`** … Das Feld trägt keine beobachtbare Information." (Die Frage, ob das Feld ganz entfallen soll, ist **V-43**.) |
| **V-38** (sollte) | `EventIntakeRequest` — Beschreibung (vorher `:2266`), `payload` — Beschreibung (vorher `:2296`), `x-validation-rules.exactly_one_payload` (vorher `:2330`) | Drei Stellen sagten „`payload: null` gilt als **nicht** gesetzt" — das Schema sagt `nullable: false`, und die Fehlersemantik verlangt für **jede** Schemaabweichung **422 `validation_failed`**. | Alle drei Stellen an das Schema angeglichen: „Ein `payload: null` verletzt das Schema (HTTP 422 `validation_failed`) und gilt **nicht** als gesetzt." Das **Schema** bleibt unverändert (`nullable: false`); die Beobachtung (422) bleibt dieselbe. |

**Umfang unverändert:** 17 Operationen · 37 Schemas · 11 Antwort-Komponenten · 7 Parameter · 23 Stories / 101 AKs / 24 Anforderungen — **kein** neues Feld, **keine** neue Operation, **keine** neue Story, **kein** neues Akzeptanzkriterium, **keine** neue Regel. Das **Schema** wurde nicht angefasst (`diff` leer).

### 10.4 Neue Befunde — dokumentiert, **nicht** entschieden

| ID | Befund | Fundstelle | Frage | Optionen | Empfehlung |
|---|---|---|---|---|---|
| **V-39** (muss) | **Reihenfolge der Rollen- und der Mandantenprüfung beim Replay.** Der Vertrag sagt „`developer` und `service_admin` erhalten HTTP 403" (F-03) und zugleich „fremde Ressourcen ⇒ 404" (D-02). Für `service_admin`/`developer` auf einen Zustelllauf **fremder** Mandanten steht nicht, welcher Code gilt. | `replayDelivery`-Beschreibung (`:1352-1357`); `info.description` Fehlersemantik; ADR-015 §3 („403 = dein Mandant, deine Rolle; 404 = nicht dein Mandant") | Rollenprüfung **vor** der Mandantenprüfung (403) oder umgekehrt (404)? | (a) Mandant zuerst ⇒ **404** für jeden fremden Lauf; (b) Rolle zuerst ⇒ **403**; (c) Replay grundsätzlich nur im **eigenen** Mandanten (D-02 ⇒ 404) | **(a)** — die Existenz fremder Läufe bleibt verborgen (D-02); die Rollenprüfung greift nur innerhalb des eigenen Mandanten. **Nicht entschieden** — die Reihenfolge ist eine Festlegung, die F-03/D-15 berührt → Mensch (Gate). |
| **V-40** (muss) | **Der Zeitraumfilter des Zustellprotokolls ist an keinen Zeitstempel gebunden.** `from`/`to` nennen keinen Zeitstempel; die Totenliste bindet ihren Zeitraum ausdrücklich an `abandoned_at`; ADR-017 §4 nennt für das Protokoll `created_at`, `accepted_at` **und** `last_attempt_at`. | `components.parameters.From`/`To`; `listDeliveries` (Filter Zeitraum); `listDeadDeliveries` („Der Zeitraumfilter bezieht sich auf den **Aufgabezeitpunkt**") | Auf welchen Zeitstempel bezieht sich `from`/`to` im Zustellprotokoll? | (a) `created_at` (Sortierschlüssel, ADR-017 §4); (b) `last_attempt_at` (Anker der Aufbewahrung, F-02); (c) `accepted_at` | **(a)** — Filter und Sortierung auf **denselben** Schlüssel; die Randfälle sind eindeutig. **Nicht entschieden** (Schnittstellen-Semantik). |
| **V-41** (sollte) | **`ReadinessStatus` führt Werte, die der Vertrag nie erzeugt.** `status: [ready, not_ready]` und `checks.*: [ok, unavailable]` — der nicht bereite Zustand wird aber mit **503** und dem **Fehlerschema** `Problem` beantwortet (ADR-015 §4: „Ein einziges Schema für alle Fehlerantworten"). | `components.schemas.ReadinessStatus`; `getReadiness` (503 → `ServiceUnavailable`) | Soll der nicht bereite Zustand als **Readiness-Bericht** oder als **Problem** kommen? | (a) Enum auf die beobachtbaren Werte schärfen (`ready` / `ok`); (b) den 503 mit dem Readiness-Bericht beantworten (widerspricht ADR-015 §4); (c) als reserviert dokumentieren | **(a)** — das Modell beschreibt dann genau den Zustand, den der Vertrag liefert. **Nicht entschieden** (Antwortform des 503). |
| **V-42** (sollte) | **`EndpointMetrics.rate_limit` ist optional, wird aber als vorhanden beschrieben.** `required: [endpoint_id, window, data_available]` — `rate_limit` fehlt; die Operationsbeschreibung sagt „Der Abschnitt `rate_limit` liefert …" und „`rate_limit.limit` ist **immer** der wirksame Grenzwert". | `components.schemas.EndpointMetrics`; `getEndpointMetrics`-Beschreibung | Ist der Abschnitt `rate_limit` **immer** Teil der Antwort? | (a) `rate_limit` in `required` aufnehmen; (b) die Beschreibung als optional kennzeichnen | **(a)** — die Zählung läuft unabhängig vom Grenzwert (AC-018-4) und ist damit immer vorhanden. **Nicht entschieden** (neue Zusage). |
| **V-43** (sollte) | **`Event.deleted_at` ist ein totes Feld.** In der Antwort immer `null` (ADR-012 §3, V-37) — die anderen Entitätsmodelle (`Endpoint`, `DeliveryRun`, `DeliveryAttempt`, `DeadDelivery`) führen den Aufbewahrungsmarker **nicht**, weil ein markierter Datensatz unsichtbar ist. | `components.schemas.Event.deleted_at` | Feld behalten (mit dem korrigierten Text aus V-37) oder entfernen? | (a) Feld entfernen (Konsistenz mit den übrigen Modellen, kein Informationsverlust); (b) behalten | **(a)** — die Antwortform wird einheitlich. **Nicht entschieden** (Änderung der Antwortform → nach dem Freeze ein Rücksprung). |
| **V-44** (muss) | **ADR-015 widerspricht in zwei Sätzen den bestätigten Entscheidungen V-26 und V-27.** §1: die Rollenmatrix nennt „`service_admin` \| Endpunkte verwalten \| **ja** (alle Mandanten)" — V-27 besagt: der **Einzelabruf** einer fremden Ressource ergibt 404; die mandantenübergreifende Sicht gilt **nur** für die drei Listen-/Betriebsoperationen. §2: „**Im Zustellprotokoll** … gibt es keine mandantenübergreifende Sicht — dort ist das Feld `null` (E-10)" — V-26 hat `organisation_id` auf „**immer** gefüllt" umgestellt. | `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:35` und `:80-81` | — | — | **Keine Entscheidung nötig — nur eine Korrektur.** `docs/adr/` ist in diesem Schritt **nicht** änderbar; die beiden Sätze sind **in einem Rücksprung** (oder in Schritt 4) an den entschiedenen Stand zu bringen. **Hier nicht geändert.** |

**Hinweis zu V-39 und V-44:** beide betreffen die **Rollenmatrix** — einmal die Reihenfolge der Prüfungen, einmal den Text der ADR. Sie gehören nicht zum Muster „Beschreibung ↔ Beispiel" und wurden **nicht** eigenmächtig entschieden.

### 10.5 Nachweis

| Prüfung | Ergebnis |
|---|---|
| YAML gültig | **gültig** (`yaml.safe_load`) |
| `$ref` | **177**, **alle** auflösbar |
| Operationen | **17**, alle mit `operationId` (eindeutig), `x-ac-id`, `x-requirement-ids` |
| `x-acceptance-coverage` / `x-requirement-coverage` | **101 / 24**, lückenlos gegen `docs/requirements/stories/*.json`, keine Dubletten |
| Beispiele gegen ihr Schema (Pflichtfelder, `null` bei nicht-`nullable`, unbekannte Felder, Enum, Längen, Bereiche) | **0 Abweichungen** |
| Beispiele **nachgerechnet** (Nutzlastbytes, Erfolgsquote, Backoff, Gesamtfenster, Fristen/Stichtage, Zählersummen, Base64-Länge, Secret-Längen, UUID-Form) | **0 Abweichungen** |
| Fehlercodes je Operation (Body ⇒ 400, authentifiziert ⇒ 429, Adresse ⇒ 404) | **vollständig** |
| ungenutzte Objekte | **keine** (37 Schemas, 11 Antwort-Komponenten, 7 Parameter, 1 Header) |
| `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken …" (23 Stories / 101 AKs / 24 Anforderungen) |
| `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (byte-identisch — das Schema wurde nicht angefasst) |
| Anforderungen unberührt | `git diff --stat -- docs/requirements/ docs/Anforderung.md` → **leer** |
| ADRs und Architekturdokumente unberührt | `git diff --stat -- docs/adr/ docs/architecture/` → **leer** |
| Kein Anwendungscode | `git diff --stat -- src/` → **leer** (`src/` existiert nicht) |
