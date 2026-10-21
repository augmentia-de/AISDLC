# Rücksprung-Bilanz — Schritt 2 (Konzept & Architektur)

> **Vorgangsprotokoll** — der Nachweis, dass kein Befund des Stresstests liegen
> geblieben ist. **Kein lebender Wahrheitsort**: die geltenden Entscheidungen
> stehen in `docs/adr/`, der Vertrag in `docs/api/openapi.yaml`, die offenen
> Punkte in `.agent/artifacts/architecture.md` §8.
>
> **Rücksprung aus Schritt 4 (Grill-Me-Stresstest)** · Agent: `aisdlc-architecture`
> · Stand: 2026-10-21 · Eingang: `.agent/artifacts/stress-test.md` (Commit `bdb03d3`)
> · Vertrag: `docs/api/openapi.yaml` (17 Operationen) — **noch nicht eingefroren**
> · Schema: `.agent/knowledge/schema.sql` (kanonisch) + Spiegel `docs/architecture/schema.sql`

Dieser Bericht ist der **Nachweis**, dass kein Befund des Stresstests liegen
geblieben ist. Je Defekt steht: **Fundstelle** (so, wie der Stresstest sie
nennt) → **wo korrigiert wurde** → **wie**.

---

## 0. Kurzfassung

| Umfang | Befunde | Behandelt | Offen für Schritt 4 |
|---|---|---|---|
| **Vertragsdefekte** (V-01…V-22) | 22 | **22** | 0 |
| **Architekturbruch** (S-01) | 1 | **1** (E-01) | 0 |
| **Standards-Befunde** (N-01, N-02, N-08) | 3 | **3** | 0 |
| **Entscheidungen** (E-01…E-09) | 9 | **9** | 0 |
| **Neue Befunde** (R-01…R-05) | 5 | **0 — dokumentiert, nicht entschieden** | 5 |

**Nicht entschieden** wurden fünf beim Beheben gefundene Punkte (R-01…R-05,
Abschnitt 5) — sie sind Befunde für Schritt 4, keine Entscheidungen dieses
Schritts.

**Beleg:** Das Schema wurde gegen **PostgreSQL 16** angewendet und das
Verhalten nachgewiesen (Abschnitt 4). `diff .agent/knowledge/schema.sql
docs/architecture/schema.sql` ist **leer**. `python3
.agent/tools/traceability_check.py` ist **grün** (23 Stories / 101 AKs / 24
Anforderungen, unverändert).

---

## 1. Die 22 Vertragsdefekte (V-01…V-22)

Alle Zeilenangaben der linken Spalte sind die **Fundstellen aus dem
Stresstest** (Stand vor diesem Schritt); die rechte Spalte nennt die Stelle
**nach** der Korrektur.

| ID | Fundstelle (Stresstest) | Korrigiert in | Wie |
|---|---|---|---|
| **V-01** | `:59-60` | `openapi.yaml:68` | `page.nextCursor` / `page.hasMore` → `page.next_cursor` / `page.has_more`. Zwei Wörter im Prosa-Teil von `info.description`. |
| **V-02** | `:1296-1306`, `:2508-2521`, `:2522-2533` | `openapi.yaml:1413-1441` (Beschreibung `enforceRetention`), `:2743-2823` (`RetentionEnforcementResponse`), `:2883-2955` (`TenantRetention`, **neu**), `:1459-1490` (Beispiel) | **Anker** je Datenart (`anchor: last_attempt_at`, Ereignisse ohne Versuch ⇒ `accepted_at`); **Stufen**: `marked_*` (markieren) und `purged_*` (Purge) als getrennte Zähler — die `deleted_*` sind entfallen; **Karenzzeit** als Feld `purge_delay_days`; **Kaskade** im Beschreibungstext („das Löschen eines Ereignisses nimmt seine Zustellläufe und Versuche mit"); **Geltungsbereich** als `scope: all` plus die je-Mandant-Liste `tenants[]` (Fristen, Stichtage, Zähler). Die Top-Level-Fristen sind als **kürzeste** angewandte Frist über alle Mandanten ausgewiesen. |
| **V-03** | `:2442-2449`, `:1273` (Beispiel), `:373` | `openapi.yaml:2683-2700` (`RateLimitObservation`), `:1391` (Beispiel), `:420-437` (`x-delivery-protocol.rate_limit.states`), `:93-101` (`info.description`) | `limit` ist **immer** der wirksame Grenzwert (`nullable` entfernt, Vorgabe 60). „Abgeschaltet“ ist ein **eigenes Feld** `limited: false` (⇒ `limit: 0`). Neuer Abschnitt `states:` im ausgehenden Vertrag benennt `kein_override` und `abgeschaltet`. Beispiel auf `limit: 60, limited: true` umgestellt. |
| **V-04** | `:220-224` vs. `:1962-1965` | `openapi.yaml:275-289` (`x-delivery-protocol.request.body`), `:2203-2210` (`EventIntakeRequest.payload`) | „Der Request-Body sind **die Nutzlastbytes des Ereignisses**“ — bei `payload` die **kanonisch materialisierten** Bytes (RFC 8785), bei `payload_base64` die **exakt gelieferten**. „Byteidentisch“ heißt: identisch mit dem, **was das Ereignis trägt** — nicht mit dem Rohtext des Aufrufers. Der Hinweis steht jetzt **auch** in `EventIntakeRequest.payload`. |
| **V-05** | `:2103-2181` | `openapi.yaml:2352-2358` (`DeliveryRun.replay_of`) | Feld `replay_of` (UUID, nullable) aufgenommen, beschrieben als „`null`, wenn der Lauf kein Replay ist“ (AC-013-2). In beiden Zustelllauf-Beispielen als `replay_of: null` sichtbar. |
| **V-06** | `:1203` (Beispiel) vs. `schema.sql:190` | `openapi.yaml:1300` (Beispiel), `:1958-1970` (`DeliveryStatus`) | Beispiel `ReplayResponse.status: scheduled` → **`pending`**. Die `DeliveryStatus`-Beschreibung schärft: `pending` = angelegt, **noch kein Versuch** (Ausgangszustand eines neuen Laufs, auch nach einem Replay); `scheduled` = ein Versuch läuft gerade. Der Widerspruch zum Schema ist aufgelöst. |
| **V-07** | `:883-885`, `:2162-2165` | `openapi.yaml:975-978` (`listDeliveries`), `:2444-2449` (`DeliveryRun.next_attempt_at`) | `no_subscriber` in **beiden** Listen der endgültigen Zustände ergänzt — deckt sich mit `delivery_runs_terminal_no_next_attempt_ck`. |
| **V-08** | `:2077-2079` vs. `schema.sql:148` | `openapi.yaml:2310-2315` (`Event.payload_media_type`), `:2302` (`Event.required`) | `nullable: true` entfernt, `payload_media_type` in `required` aufgenommen. „Nicht leer (Vorgabe `application/json`)“ ist jetzt beschrieben. |
| **V-09** | `:1619-1634`, `:1648-1660` (ungenutzt), `:1401-1413` | `openapi.yaml:1796-1809` (`RateLimitExceeded`), `:1825-1836` (`ServiceUnavailable`), alle 15 authentifizierten Operationen (je `"429"`), `/health/ready` (503) | Die 429 ist jetzt **erreichbar**: sie wird von **allen 15 authentifizierten Operationen** referenziert (nicht nur von den drei im Stresstest genannten — das API-Anfragelimit gilt für jede Anfrage, eine Beschränkung auf Schreiboperationen wäre eine neue Inkonsistenz). Die 503 von `/health/ready` benutzt jetzt die Komponente `ServiceUnavailable` statt einer Inline-Definition. **Keine** toten Komponenten mehr. |
| **V-10** | `:1698` (Enum), `:2760` (Regel) vs. `:1599-1618` | `openapi.yaml:1749-1782` (`responses.ValidationFailed`), `:1913-1940` (`ValidationFailedProblem`, `TenantParameterRejected` — **neu**) | Der Code `tenant_parameter_rejected` ist jetzt **erreichbar und dokumentiert**: die 422-Antwort trägt **zwei Beispiele** (`validation_failed`, `tenant_parameter_rejected`) und ihr Schema ist ein `oneOf` über `ValidationFailedProblem` und `TenantParameterRejected` (beide `allOf` auf `Problem` — die Form bleibt **ein** Fehlerschema, ADR-015 §4). *Randbedingung:* OpenAPI 3.0 erlaubt **eine** Antwort je Statuscode; eine eigene 422-Komponente je Code wäre nicht darstellbar — deshalb der `oneOf` über zwei **Schemas** mit eingeengtem `code`-Enum. |
| **V-11** | `:1793`, `:1835`, `:1956`, `:2344`, `:2470` vs. `:45`/`:51`/`:1830-1831` | `openapi.yaml:48-63` (Fehlertabelle), `:2937-2941` (`x-contract-rules.errors`), `:2015-2020` (`EndpointUpdateRequest`), `:2176-2190` (`EventIntakeRequest`), `:2140-2145` (`SecretRotationRequest`), `:2770-2776` (`ReplayRequest`), `:2812-2818` (`RetentionEnforcementRequest`) | Die 400/422-Grenze steht jetzt **ausgeschrieben**: 400 = syntaktisch fehlerhaft (kein gültiges JSON), 422 = jedes Feld, das das Schema des Request-Bodys verletzt (unbekanntes Feld, falscher Typ, fehlendes Pflichtfeld), plus `violations`. Der Feldname `organisation_id` ist in **allen** `x-validation-rules.no_tenant_parameter` genannt — auch dort, wo die Regel bisher fehlte (`SecretRotationRequest`, `ReplayRequest`, `RetentionEnforcementRequest` haben sie jetzt). |
| **V-12** | `:261-311` | `openapi.yaml:322-332` | Neue Zeile in der Ergebnismatrix: **3xx ⇒ kein Wiederholen**, der Lauf endet `rejected` mit `rejection_reason = client_error`; **Redirects werden nicht gefolgt** (Begründung: die Ziel-URL ist Teil der Signatur-Eingabe). E-08. |
| **V-13** | `:1769-1779` vs. `:303-311` | `openapi.yaml:333-348` (Transportfehler-Block), `:1980-1997` (`AttemptOutcome`) | Trennlinie: `timeout` = **Verbindungs-Timeout** (5 s) oder **Lese-Timeout** (10 s); `network_error` = DNS-, TLS- oder Verbindungsfehler **ohne** Timeout. Beide ⇒ Wiederholung nach Backoff, im Protokoll **unterschiedlich** ausgewiesen. |
| **V-14** | `:1173-1174` | `openapi.yaml:1281-1286` | „Ist der Endpunkt pausiert …“ ⇒ **HTTP 409** `delivery_state_conflict`; es entsteht **kein** neuer Zustelllauf, der Vorgängerlauf bleibt unverändert (D-03, AC-013-4). |
| **V-15** | `:962-963` vs. `:1021-1022`/`:1050`; `:804`; `:438` | `openapi.yaml:1043-1062` (`listDeliveries`-Beispiel), `:1104-1150` (`getDeliveryRun`-Beispiel), `:887` und `:941` (`payload_size_bytes`), `:495` (`organisation_id`) | Beide Zustelllauf-Beispiele beschreiben **denselben** Lauf: `attempt_count: 2`, zwei Versuche, `last_attempt_at 09:16:24.000Z`, `next_attempt_at 09:18:24.190Z` (= Ende des 2. Versuchs + 2 min Backoff — die alte Angabe war +4 min 40 s); der Cursor in der Liste wurde auf denselben `updated_at` umgestellt. `organisation_id` in der 201-Antwort von `createEndpoint` ist `null` (der Ersteller ist ein `receiver_admin`). `payload_size_bytes` ist **35** — die Größe der **kanonisch materialisierten** Bytes (siehe R-02). |
| **V-16** | `:330-353` (`x-delivery-protocol.retry_policy`) | `openapi.yaml:383-391` | Neues Feld `expected_completion_window`: **123 Minuten ≈ 2 Stunden** (7 Wartezeiten = 1+2+4+8+16+32+60), mit ± 20 % Streuung zwischen 1 h 38 min und 2 h 28 min. Die Zusage steht damit **auch** dort, wo die Empfänger sie lesen. ADR-008 auf denselben Stand gebracht (E-03). |
| **V-17** | `:1252-1254` (Parameter `From`/`To` in `:1469-1484`) | `openapi.yaml:1613-1633` | Beide Parameterbeschreibungen nennen jetzt das Vorgabefenster: „Ohne `from`/`to` gilt das Fenster der **letzten 7 Tage** bis jetzt (ADR-011, bestätigt am 2026-10-21)“. Zusätzlich in der Beschreibung von `getEndpointMetrics`. |
| **V-18** | `:1149-1218` | `openapi.yaml:1271-1276` | „Ein aufgegebener Zustelllauf kann **einmal** replayed werden — ein **weiterer** Replay desselben Laufs antwortet mit **HTTP 409** `delivery_state_conflict`; es entsteht **kein** weiterer Zustelllauf.“ Die Regel ist **auf Datenbankebene erzwiesen** (`UNIQUE (replay_of)`, ADR-006 §6). E-09. |
| **V-19** | `:751-754`, `:1993-2000` | `openapi.yaml:831-837` (`submitEvent`), `:2203-2220` (`payload_base64`), `:2244-2246` (`x-validation-rules.payload_size_limit`) | Die Grenze von 1.048.576 Byte betrifft die **Nutzlastbytes**; der **Request-Body** darf bis **2 MiB** groß sein (base64 ⇒ rund **1,4×**). An allen drei Stellen gesagt. |
| **V-20** | `:148-151` vs. `:2010-2014` | `openapi.yaml:191-193` | `Content-Type`-Wert: `"<payload_media_type des Ereignisses> (Vorgabe: application/json)"` — die Beschreibung „entspricht `payload_media_type`“ stimmt jetzt mit dem Wert überein. |
| **V-21** | `:1781-1788` | `openapi.yaml:1999-2007` | `null` aus dem Enum gestrichen; `nullable: true` deckt den Fall ab. Die Beschreibung sagt jetzt, was `null` bedeutet („der Lauf wurde **nicht** abgelehnt“). |
| **V-22** | `:1986-1992`, `:2016-2019` | `openapi.yaml:2203-2210`, `:2176-2190` (Beschreibung), `:2238-2240` (`exactly_one_payload`) | `payload` hat jetzt `nullable: false`; die Regel lautet: „Genau **eine** von `payload`/`payload_base64` muss vorhanden sein. `payload: null` gilt als **nicht** gesetzt.“ Auch in der Schema-Beschreibung und im `x-validation-rules` vermerkt. |

**Nachweis:** `python3` — YAML gültig, **17 Operationen** (unverändert), alle
`$ref` auflösbar (keine toten Schemas, keine toten Antwort-Komponenten), jede
Operation mit `operationId`, `x-ac-id` und `x-requirement-ids`;
`x-acceptance-coverage` **101/101** (ohne Dubletten), `x-requirement-coverage`
**24/24**.

---

## 2. S-01 — der Architekturbruch (E-01)

**Das Problem:** `service_admin` soll alle Mandanten sehen; die RLS erlaubt
genau **einen** Mandanten pro Transaktion; `BYPASSRLS` ist ausgeschlossen.
`current_setting('app.organisation_id', true)` liefert genau **einen** Wert
oder `NULL` — „alle Mandanten“ war im vorliegenden Design **nicht
darstellbar**. Betroffen: `listEndpoints`, `listDeadDeliveries`,
`enforceRetention` — und damit FR-004, AC-004-2, AC-016-1, AC-016-5, Z6.

**Entschieden (E-01): Weg (b)** — `SECURITY DEFINER`-Eigentümerfunktionen im
Schema `app`, analog zum bestehenden Muster `app.tenant_ids_for_worker()`.

### 2.1 Umsetzung — Schema (beide Dateien byte-identisch)

`.agent/knowledge/schema.sql` + `docs/architecture/schema.sql`
(Spiegel-Regel, ADR-016) — neuer Abschnitt **„Mandantenübergreifende Sicht“**
(Schema `app`, Eigentümer `echobox_owner`):

| Funktion | Operation | Art |
|---|---|---|
| `app.list_endpoints_cross_tenant(status, limit, after_updated_at, after_endpoint_id)` | `GET /api/v1/endpoints` | **lesend** |
| `app.list_dead_deliveries_cross_tenant(endpoint_id, event_type, status, limit, from, to, after_created_at, after_delivery_run_id)` | `GET /api/v1/dead-deliveries` | **lesend** |
| `app.enforce_retention_cross_tenant(dry_run, event_retention_days, delivery_retention_days, purge_delay_days)` | `POST /api/v1/retention/enforcement` | **schreibend** (nur `deleted_at` + Purge) |

**Sicherheitsregeln (ADR-002 §7):**

- `SECURITY DEFINER`, ausgeführt als `echobox_owner` — NOLOGIN, **kein**
  `BYPASSRLS`; der Eigner umgeht die RLS nur, weil `FORCE ROW LEVEL SECURITY`
  bewusst **nicht** gesetzt ist.
- `search_path` fest verdrahtet (`app, pg_temp`).
- `REVOKE ALL … FROM PUBLIC` (PostgreSQL gibt `EXECUTE` auf Funktionen
  standardmäßig an `PUBLIC`) und `GRANT EXECUTE` **nur** an `echobox_app`.
  Der Worker (`echobox_worker`) ist **nicht** freigegeben. Die Endbenutzerrollen
  (`developer`, `receiver_admin`, `service_admin`) sind keine Datenbankrollen —
  die Freigabe an `echobox_app` bedeutet **nicht**, dass jede Rolle sie benutzen
  darf; die Rollenprüfung bleibt in der Anwendung (ADR-015).
- **Fail-closed:** ohne `app.cross_tenant = 'service_admin'` (transaktionslokal
  gesetzt, **nachdem** die JWT-Rolle geprüft ist) liefern alle drei Funktionen
  **keine** Zeile.
- **Projektionen:** nur die vom Vertrag verlangten Felder — **kein**
  Nutzlastklartext, **keine** Chiffre, **keine** Secrets, **keine** Schlüssel.
- **Grenzen:** die **einzige** schreibende Funktion ist
  `enforce_retention_cross_tenant`; sie schreibt ausschließlich `deleted_at` und
  führt im Purge die dokumentierte Löschung aus. Es gibt **keinen** allgemeinen
  Schreibzugriff mandantenübergreifend.

### 2.2 Umsetzung — ADR-002

`docs/adr/ADR-002-mandantentrennung-rls.md`: neue **Entscheidung §7** (der
Ausnahmefall, der Mechanismus, die Grenzen), neue **Konsequenz** zur Ausnahme
(ehrlich benannt) und **sechs neue Zeilen** in der Verifikationstabelle.

### 2.3 Umsetzung — Vertrag

`docs/api/openapi.yaml`: die drei Operationen beschreiben den
mandantenübergreifenden Blick und die **Rückgabeform** —
`organisation_id` ist in **jedem** Datensatz gefüllt, damit ein Client die
Herkunft erkennt (`:532`, `:1171`, `:1419`); `DeadDelivery.organisation_id` ist
als „**immer** gefüllt“ beschrieben (`:2524`); `x-contract-rules.tenant` nennt
den Mechanismus (`:2946`).

---

## 3. N-01, N-02, N-08 und die Entscheidungen E-02…E-09

| Befund | Umsetzungsort | Wie |
|---|---|---|
| **N-01** (muss) — Mandantenkontext an die *Verbindung* statt an die *Transaktion* gebunden | `ADR-002` §3 · `schema.sql` (Kopfkommentar, `app.set_tenant`) · `docs/architecture/architecture.md` §4 „Mandant und Transaktion“ · `docs/architecture/folder-structure.md` §2.6 + Konvention „Mandant“ | `app.set_tenant(...)` wird **innerhalb** der Transaktion ausgeführt, die auch liest, **immer vor der ersten Abfrage**; kein Zugriff auf `app.*` außerhalb einer solchen Transaktion, kein `REQUIRES_NEW` nach dem Setzen, kein Autocommit. `TenantFilter` liest **nur** das JWT und füllt einen request-scoped `TenantContext` **ohne** DB-Zugriff. Der **Name** bleibt (E-05) — der Kommentar zur Namensfrage steht an der richtigen Stelle (Klassenbeschreibung in `folder-structure.md` §2.6, ADR-002 §„Konsequenzen“). |
| **N-02** (muss) — der Worker beansprucht Läufe ohne Mandantenermittlung | `ADR-006` §3 · `schema.sql` (`app.tenant_ids_for_worker`) · `docs/architecture/architecture.md` §4 „Mandant im Worker“ · `docs/architecture/folder-structure.md` §2.3 | Die Kette steht jetzt ausdrücklich: Scheduler → `app.tenant_ids_for_worker()` → je Mandant `set_tenant` → Claim; **je Mandant eine eigene Transaktion**. Ohne diesen Schritt liefert die Beanspruchung 0 Zeilen. |
| **N-08** (soll) — Schema-Kommentare behaupten den Stand vor F-01/F-02 | `schema.sql`: Kopfkommentar · `app.endpoint_rate_limits` (Tabelle + Spalte) · `app.endpoint_rate_limit_counters` · `app.retention_settings` · `app.set_tenant` · `app.tenant_ids_for_worker` | Alle Kommentare nennen den entschiedenen Stand: `NULL` = **kein Override** ⇒ es gilt die Vorgabe (60/min bzw. 30 Tage). |
| **E-02** — wirksamer Rate-Limit-Grenzwert, zwei Zustände | `schema.sql`: Spalte `enabled` **entfällt** (idempotenter Migrationsblock `ALTER TABLE … DROP COLUMN enabled`), `limit_value` ohne `DEFAULT` (= NULL = kein Override), `CHECK (limit_value IS NULL OR limit_value >= 0)` bleibt und bildet die zwei Zustände ab (`0` = abgeschaltet) · `ADR-010` · Vertrag (V-03) | Die Spalte `enabled` wäre ein **dritter**, widersprüchlicher Zustand gewesen (`DEFAULT FALSE` hätte die Begrenzung dort ausgeschaltet, wo sie an sein soll). |
| **E-03** — ADR-008: Gesamtfenster ≈ 2 h statt 4 | `ADR-008` „Konsequenzen“ · Vertrag `x-delivery-protocol.retry_policy.expected_completion_window` (V-16) | 7 Wartezeiten = 1+2+4+8+16+32+60 = **123 min ≈ 2 h**; mit ± 20 % Streuung 1 h 38 min … 2 h 28 min. Die alte Angabe „rund 4 Stunden“ war um den Faktor 2 falsch. |
| **E-04** — zwei Fristen bleiben, die Kaskade benennen | `ADR-012` §4 · Vertrag `enforceRetention` + `RetentionEnforcementResponse` (V-02) | „Das Löschen eines Ereignisses nimmt seine Zustellläufe und Versuche **mit**; die Protokollfrist gilt nur für Läufe, deren Ereignis noch steht.“ |
| **E-05** — Klassenname bleibt, Semantik im Kommentar | `folder-structure.md` §2.6 (`TenantConnectionInitializer`) · `ADR-002` §„Konsequenzen“ | Kein Umbau: die Traceability-Matrix (Schritt 1) nennt die Klasse so; die **Semantik** ist transaktionsgebunden und an beiden Stellen festgeschrieben. |
| **E-06** — PRD nachziehen | `docs/requirements/PRD.md` | **Erledigt in Schritt 1** (`cc67445`) — in diesem Schritt nicht angefasst. |
| **E-07** — Testmatrix aus den 101 AKs | Schritt 5 | **Nicht Gegenstand dieses Schritts** — als Befund für Schritt 5 notiert. |
| **E-08** — 3xx: kein Wiederholen, `client_error`, Redirects nicht folgen | `ADR-009` (neuer Abschnitt „Behandlung von 3xx“) · `ADR-008` (Zeile in der Wiederholungsregel) · Vertrag: Zeile in der Ergebnismatrix (V-12), `AttemptOutcome` (V-13) | Die Lücke im **eingefrorenen** ausgehenden Vertrag ist damit geschlossen. |
| **E-09** — Replay **einmal**, danach 409 | `schema.sql`: `CONSTRAINT delivery_runs_replay_of_uq UNIQUE (replay_of)` · `ADR-006` §6 (neu) · `ADR-015` §6 (neu) · Vertrag `replayDelivery` (V-18) | Die Regel ist **auf Datenbankebene erzwiesen**, nicht nur behauptet: ein zweites Replay desselben Vorgängerlaufs verstößt gegen die Constraint ⇒ 409. |

---

## 4. PostgreSQL-16-Nachweis

Das Schema wurde gegen eine **echte PostgreSQL-16-Instanz** angewendet
(`initdb` + `pg_ctl` mit Server 16 auf einem Unix-Socket) und das Verhalten
geprüft. Das Prüfprotokoll (4 SQL-Skripte, 0 Fehler) ist ein Laufzeitartefakt
und **nicht** Teil des Commits; die Procedure ist:

```
initdb -D <data> -U postgres --auth=trust -E UTF8
pg_ctl -D <data> -o "-k <socket> -h ''" -l <log> start
psql -f .agent/knowledge/schema.sql            # anwendbar ohne Fehler
psql -f <skript 1..4>                          # RLS, Funktionen, Replay, Rate-Limit, Aufbewahrung
```

Die Ergebnisse:

### 4.1 Schema anwendbar

| Prüfung | Ergebnis |
|---|---|
| `psql -f .agent/knowledge/schema.sql` auf eine frische Datenbank | **ohne Fehler** |
| Tabellen mit RLS | **10 / 10** |
| `echobox_owner` / `echobox_app` / `echobox_worker` | `rolsuper = f`, `rolbypassrls = f` — **keine** Rolle mit `BYPASSRLS` |
| `app.endpoint_rate_limits` | Spalte `enabled` **entfallen** (7 Spalten), `limit_value` nullable ohne `DEFAULT`, `window_seconds DEFAULT 60`, `strategy DEFAULT 'delay'` |
| `app.delivery_runs` | `delivery_runs_replay_of_uq UNIQUE (replay_of)` vorhanden (neben `…_fk`) |
| Die drei Funktionen | `prosecdef = t`, `proconfig = {"search_path=app, pg_temp"}`, `owner = echobox_owner` |
| Rechte | `public_may = f` (REVOKE), `app_may = t` (GRANT), `worker_may = f` |

### 4.2 (a) Fail-closed ohne Mandantenkontext — weiterhin gültig

Als `echobox_app` **ohne** `set_tenant`:

| Tabelle | Zeilen |
|---|---|
| `app.endpoints` | **0** |
| `app.events` | **0** |
| `app.delivery_runs` | **0** |
| `app.delivery_attempts` | **0** |

### 4.3 (b) Ein normaler Mandant sieht keine fremden Zeilen

Als `echobox_app` mit `set_tenant('m-1')` (zwei Mandanten angelegt: `m-1`,
`m-2`):

| Prüfung | Ergebnis |
|---|---|
| `app.endpoints` sichtbar | **1** (nur `m-1`) |
| `organisation_id`-Gruppen | nur `m-1` |
| `app.events` mit `organisation_id = 'm-2'` | **0** |
| `app.delivery_runs` sichtbar | **1** |

### 4.4 (c) `service_admin` bekommt genau die mandantenübergreifende Sicht

| Prüfung | Ergebnis |
|---|---|
| `list_endpoints_cross_tenant()` **ohne** `app.cross_tenant` | **0 Zeilen** (fail-closed) |
| `list_dead_deliveries_cross_tenant()` ohne Freigabe | **0 Zeilen** |
| `enforce_retention_cross_tenant(...)` ohne Freigabe | **0 Zeilen** |
| `list_endpoints_cross_tenant()` **mit** `app.cross_tenant = 'service_admin'` | **2 Zeilen** — `m-1` **und** `m-2`, je mit `organisation_id` |
| `list_dead_deliveries_cross_tenant()` mit Freigabe | **2 Zeilen** — beide Mandanten, je mit `organisation_id` |
| `echobox_worker` ruft eine der Funktionen auf | **`permission denied for function list_endpoints_cross_tenant`** — der Worker ist **nicht** freigegeben |

### 4.5 (d) Kein Nutzlastklartext und kein Secret verlassen die Funktionen

| Prüfung | Ergebnis |
|---|---|
| Spaltennamen der drei Rückgabeprojektionen gegen `secret_(ciphertext\|nonce\|key)\|ciphertext\|payload\|nonce\|fingerprint` geprüft | **kein Treffer** (nur `secret_version` — der Rotationszähler, ein Vertragsfeld) |
| Rückgabe von `list_endpoints_cross_tenant()` gegen die Klartextwerte der Chiffre/Secrets (`deadbeef`, `c0ffee`) | **0** Treffer |
| Rückgabe von `list_dead_deliveries_cross_tenant()` gegen die Chiffre (`cafe`, `f00d`) | **0** Treffer |
| Rückgabe gegen Nutzlastinhalte (`order_id`, `4711`) | **0** Treffer |

### 4.6 Die neuen Regeln

| Prüfung | Ergebnis |
|---|---|
| Erstes Replay auf einen `abandoned`-Lauf | **erlaubt** (1 Zeile) |
| Zweites Replay desselben Laufs | **`unique_violation`** ⇒ HTTP 409 `delivery_state_conflict` |
| `limit_value IS NULL` (kein Override) | **erlaubt** |
| `limit_value = 0` (abgeschaltet) | **erlaubt** |
| `limit_value = -1` | **`check_violation`** |
| Aufbewahrung, `dry_run = true` | zählt, **ändert nichts** (`deleted_at` bleibt `NULL`) |
| Aufbewahrung, 30 Tage: `m-1` (40 Tage alt) | Ereignis **markiert**, **2 Läufe markiert** (Kaskade), **1 Versuch markiert** |
| Aufbewahrung, 30 Tage: `m-2` (5 Tage alt) | **nichts** markiert |
| Purge (zweiter Durchlauf, Karenzzeit 0 Tage) | 1 Ereignis, 2 Läufe, 1 Versuch **physisch gelöscht**; danach je 1 Datensatz von `m-2` übrig |
| Purge mit Karenzzeit 30 Tagen | **nichts** gelöscht |

### 4.7 Weitere Prüfungen

| Prüfung | Ergebnis |
|---|---|
| `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (byte-identisch) |
| `python3 .agent/tools/traceability_check.py` | **grün** — „Keine Luecken …“ (23 Stories, 101 AKs, 24 Anforderungen) |
| Vertrag: YAML gültig | **ok** |
| Vertrag: Operationen | **17** (unverändert — keine neue, keine entfernte) |
| Vertrag: `$ref` | **alle** auflösbar; **kein** ungenutztes Schema, **keine** ungenutzte Antwort-Komponente |
| Vertrag: `x-acceptance-coverage` / `x-requirement-coverage` | **101 / 101** bzw. **24 / 24**, ohne Lücke und ohne Dubletten |
| `docs/requirements/`, `docs/Anforderung.md`, `src/` | **unverändert** |

---

## 5. Neue Befunde — dokumentiert, **nicht** entschieden

Beim Beheben gefunden; sie gehören in **Schritt 4** (der Vertrag wird erst nach
Schritt 4 eingefroren).

> **Stand nach Schritt 3 (2026-10-21):** **R-03, R-04 und R-05 sind dort
> behoben** (rein mechanisch, ohne neue Festlegung — siehe
> `.agent/artifacts/simplify-report.md`). **R-01 bleibt offen**: die Karenzzeit
> hat **keinen** beschlossenen Vorgabewert; der Vertrag behauptet seit Schritt 3
> **keinen** Wert. Owner: **Fachbereich**, Gate **2**. Die Tabelle unten bleibt
> unverändert der historische Befund des Rücksprungs.

| ID | Befund | Fundstelle | Warum nicht entschieden |
|---|---|---|---|
| **R-01** | Die **Karenzzeit** der Aufbewahrung hat keinen entschiedenen Vorgabewert. ADR-012 nennt `echobox.retention.purge-delay` nur als „konfigurierbar“; der Vertrag muss die Karenzzeit aber ausweisen (`purge_delay_days`, V-02). Das Beispiel trägt den Wert **7** — eine Zahl, die **nirgends beschlossen** ist. | `ADR-012` §6 · `openapi.yaml:1466` | Eine Zahl zu erfinden wäre eine Festlegung ohne Beschluss. Der Vertrag nennt den Wert als **Betriebskonfiguration**; ob ein Vorgabewert gebraucht wird, ist eine Entscheidung des Menschen. |
| **R-02** | `payload_size_bytes` misst nach V-04 die **kanonisch materialisierten** Bytes — im Beispiel also **35**, nicht 38 (der Rohtext des Aufrufers). Der Stresstest hatte 38 vorgeschlagen; das ist mit der Kanonisierung (V-04) nicht vereinbar. | `openapi.yaml:887`, `:941` | Kein offener Entscheidungsbedarf — der Wert folgt zwingend aus V-04 und ist dokumentiert. Hier **kein** Befund, sondern eine bewusste Abweichung von der Korrekturidee des Stresstests, damit der Vertrag in sich stimmt. |
| **R-03** | `DeadDelivery.abandoned_at` ist im Vertrag **nicht nullable** und **Pflichtfeld** — der Statusfilter der Totenliste lässt aber auch `rejected` zu, und ein `rejected`-Lauf hat **kein** `abandoned_at` (DB-Constraint `delivery_runs_abandoned_at_ck` erzwingt es nur für `abandoned`). Die Schema-Funktion liefert den Wert ehrlich als `NULL`. | `openapi.yaml:2524` ff. · `schema.sql` (`delivery_runs_abandoned_at_ck`) | Entweder wird `abandoned_at` nullable, oder die Totenliste wird auf `abandoned` beschränkt. Beides ist eine **Semantikentscheidung** — im Stresstest nicht benannt. |
| **R-04** | Die **Cursor-Semantik** der mandantenübergreifenden Funktionen ist eine eigene: die Funktionen erwarten zwei Skalare (`after_updated_at`, `after_endpoint_id`), der **opake, HMAC-signierte Cursor** des Vertrags (`common/CursorCodec`) ist eine Anwendungssache. Das ist im Vertrag nicht beschrieben — die Leser des Vertrags wissen nicht, worüber sie blättern. | `schema.sql` (`*_cross_tenant`) · `folder-structure.md` §2.7 (`CursorCodec`) | Die Cursor-Form ist bewusst **Anwendungssache** (ADR-017); der Vertrag nennt den Cursor nur als opak. Ob die mandantenübergreifende Paginierung im Vertrag anders beschrieben werden muss, ist eine Frage für Schritt 4. |
| **R-05** | `RetentionEnforcementResponse.tenants[]` nennt je Mandant die **Zählerstände**, aber nicht, ob ein Mandant eine **eigene Frist** hat oder die Vorgabe nutzt (`event_retention_days: null` = „kein Override“ — die Vorgabe gilt). Das ist im Beispiel nicht unterscheidbar. | `openapi.yaml:2883` ff. | Die Semantik steht in ADR-012 §6 („`NULL` bedeutet: kein Override — es gilt die Vorgabe“); im Vertrag ist sie nur an einer Stelle genannt. Ob ein zusätzliches Feld nötig ist, entscheidet Schritt 4. |

---

## 6. Liste aller Änderungen dieses Schritts

### 6.1 Vertrag — `docs/api/openapi.yaml`

Geändert: `info.description` (Fehlertabelle, 400/422-Grenze, Pagination,
Vorgabewerte, Sichtbarkeit der Rate-Limit-Felder) · `x-delivery-protocol`
(Content-Type, Body-Byte-Identität, 3xx, Timeout-Trennlinie, Gesamtfenster,
Rate-Limit-Zustände) · alle Operationen (17) · `components.responses`
(`ValidationFailed`, `RateLimitExceeded`, `ServiceUnavailable`) ·
`components.schemas` (`Problem` unverändert; **neu**: `ValidationFailedProblem`,
`TenantParameterRejected`, `TenantRetention`; geändert: `Endpoint`,
`EventIntakeRequest`, `EventIntakeResponse`, `Event`, `DeliveryRun`,
`DeliveryStatus`, `AttemptOutcome`, `RejectionReason`, `RateLimitObservation`,
`RetentionEnforcementResponse`, `DeadDelivery`) · `components.parameters`
(`From`, `To`) · `x-contract-rules` (`defaults`, `tenant`, `errors`).

**Unverändert:** die Zahl der Operationen (**17**), die `x-ac-id`- und
`x-requirement-ids`-Zuordnungen, `x-acceptance-coverage` (**101**) und
`x-requirement-coverage` (**24**).

### 6.2 Schema — `.agent/knowledge/schema.sql` + `docs/architecture/schema.sql`

Geändert: Kopfkommentar (entschiedener Stand, Transaktionsbindung,
Worker-Kette) · `app.delivery_runs` (`UNIQUE (replay_of)`, Kommentar) ·
`app.endpoint_rate_limits` (Spalte `enabled` entfernt + idempotenter
Migrationsblock, Kommentare) · `app.endpoint_rate_limit_counters` (Kommentar) ·
`app.retention_settings` (Kommentar) · `app.set_tenant` (Kommentar) ·
`app.tenant_ids_for_worker` (Kommentar) · **neu**: der Abschnitt
„Mandantenübergreifende Sicht“ mit den drei `SECURITY DEFINER`-Funktionen ·
Rechte (`REVOKE … FROM PUBLIC` für alle Funktionen, `GRANT` nur an
`echobox_app`).

### 6.3 ADRs — `docs/adr/`

Geändert: **ADR-002** (§3, §7, Konsequenzen, Verifikation) · **ADR-006** (§3,
§6 neu, Alternativen) · **ADR-008** (Gesamtfenster, 3xx-Zeile) ·
**ADR-009** (3xx-Abschnitt, Konsequenz) · **ADR-010** (zwei Zustände, Spalte
`enabled`, Auskunft des wirksamen Grenzwerts) · **ADR-012** (§4 Kaskade,
Karenzzeit) · **ADR-015** (Rollenmatrix, Mandantenherkunft, §6 neu) ·
**README.md** (Index, F-01/F-02, Abschnitt „Korrekturen aus dem Rücksprung“).

**Unverändert:** die Zahl der Entscheidungen (**17** + 1 Verweisdatei).

### 6.4 Architekturdokumente — `docs/architecture/`

Geändert: `architecture.md` (Byte-Identität, Ergebnismatrix mit 3xx und
Gesamtfenster, Zeile „Mandantenübergreifende Sicht“, Rate-Limit mit zwei
Zuständen, Aufbewahrung mit Kaskade) · `domain_model.md` (S-07-Vermerk
aufgelöst, I-9/I-10, Replay-Beziehung, 3xx) · `folder-structure.md`
(`CrossTenantScope`, Worker mandantenweise, Replay-einmal, Totenliste und
Aufbewahrung über die Eigentümerfunktionen).

**Unverändert** (Korrekturen aus Schritt 4 nicht zurückgedreht): alle fünf
Mermaid-Diagramme, `GLOSSARY.md`, `schema.sql` (nur die in 6.2 genannten
Stellen).

### 6.5 Artefakte — `.agent/artifacts/`

Geändert: `architecture.md` · `api-review.md`. **Neu**: `ruecksprung-bilanz.md`
(dieses Dokument). `briefing.md` wird vom Orchestrator geschrieben.

---

## 7. Selbstprüfung

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** |
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **grün** (23 Stories / 101 AKs / 24 Anforderungen) |
| YAML gültig | `python3 -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml'))"` | **gültig** |
| 17 Operationen, alle `$ref` auflösbar, kein ungenutztes Schema | Skript | **ok** |
| `x-acceptance-coverage` 101 / `x-requirement-coverage` 24, ohne Dubletten | Skript | **ok** |
| Schema anwendbar | `psql -f .agent/knowledge/schema.sql` (PostgreSQL 16) | **ohne Fehler** |
| RLS-Verhalten | §4.2–4.5 | **belegt** |
| UNIQUE-Regel für den Replay | §4.6 | **belegt** |
| Anforderungen unberührt | `git diff --stat -- docs/requirements/ docs/Anforderung.md` | **leer** |
| Kein Anwendungscode | `git diff --stat -- src/` | **leer** (`src/` existiert nicht) |
| Commit-Scope | `git add -A -- docs/architecture/ docs/api/ docs/adr/ .agent/artifacts/ .agent/knowledge/` | **nur** diese Pfade |
