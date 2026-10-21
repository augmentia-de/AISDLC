# Stresstest-Bericht — Schritt 4 (Grill-Me-Stresstest), **dritte Runde**

> **Delta-Prüfung und Einfrierempfehlung.**
> Schritt 4 · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Skill: `aisdlc-grill-stress` · Eingang: Schritt 2 (Rücksprung 5), Commit `1a3d6a8`
> (davor Rücksprung 4 `5a1c421`, Rücksprung 3 `d6c4e85`, Rücksprung 2 `0954ada`,
> Schritt 4 (2. Runde) `c9c8038`, Schritt 3 `05bceca`, Rücksprung 1 `9bb9bf4`,
> Schritt 1 `cc67445`)
> Gelesen: `.agent/artifacts/briefing.md`, `.agent/artifacts/ruecksprung-bilanz.md`,
> `.agent/artifacts/stress-test.md` (2. Runde), `docs/api/openapi.yaml` (3.453 Zeilen,
> **im ganzen**), `.agent/knowledge/schema.sql` (1.122 Zeilen), `docs/adr/ADR-*`,
> `docs/architecture/`, `docs/requirements/stories/*.json`
>
> **Dieser Bericht ersetzt den zweiten** (Commit `c9c8038`). Dessen Befunde sind
> hier **nachgeprüft** (§ 1), die **neuen** stehen in § 2 und § 6.

---

## 0. Kurzfassung

### Ampel: 🟢 **einfrierreif**

**Der Vertrag kann eingefroren werden.** Alle **44** entschiedenen Befunde des
ersten und zweiten Stresstests sowie der drei Rücksprünge (V-01…V-44) sind am
**aktuellen** Stand nachweisbar behoben; der Architekturbruch **S-01** und die
drei Standards-Befunde **N-01/N-02/N-08** sind es ebenfalls — S-01 **erneut gegen
eine echte PostgreSQL-16.15-Instanz** gemessen (§ 1.6). Die neue Runde findet
**zwei** weitere Befunde (V-48, V-49) — beide **„sollte“**, keiner davon ein
Widerspruch gegen eine bestätigte Entscheidung, keiner davon am Vertrag, am
Schema oder an einer ADR.

| Umfang | Befunde | Behoben | Offen |
|---|---|---|---|
| **Nachprüfung** V-01…V-44 (entschieden) | 44 | **44** | 0 |
| **Nachprüfung** S-01, N-01, N-02, N-08 | 4 | **4** | 0 |
| **Dokumentiert, nicht entschieden** (V-45, V-46, V-47) | 3 | 0 | **3** — „sollte“, **nicht** freeze-blockierend |
| **Neu in dieser Runde** (V-48, V-49) | 2 | 0 | **2** — „sollte“, **nicht** freeze-blockierend |
| **Korrekturen in `docs/architecture/`** | 0 | — | **keine** nötig (§ 3.2) |

### Freeze-Empfehlung — **ausdrücklich**

> ### **Der Vertrag `docs/api/openapi.yaml` kann eingefroren werden.**
>
> **Ab welchem Commit:** der Vertrag gilt **ab dem Commit dieses Schritts**
> (`docs(sdlc-4): Stresstest-Bericht, Nachpruefung und Einfrierempfehlung`) als
> **eingefroren** — ab da ist jede Änderung am Vertrag ein **Rücksprung zu
> Schritt 2** (AGENTS.md, `.agent/PHASES.md` → „Einfrierpunkt des Vertrags: nach
> Schritt 4“). Der Hash steht in der Antwort an den Orchestrator.
>
> **Begründung in Zahlen:** 48 von 48 entschiedenen Nachprüfungen sind am
> aktuellen Stand belegt (44 Vertrags-/ADR-Befunde, der Architekturbruch S-01,
> die drei Standards-Befunde) — S-01 **erneut live gegen PostgreSQL 16.15**
> gemessen (§ 1.6). Die Delta-Runde über die **berührten** Bereiche der drei
> Rücksprünge findet **zwei** neue Befunde; **keiner** davon ist ein „muss“
> gegen Vertrag, Schema oder ADRs, **keiner** ist ein Widerspruch gegen eine
> bestätigte Entscheidung.
>
> **Prüfungen der Freeze-Bedingung (ausdrücklich geprüft):**
>
> | Bedingung | Ergebnis |
> |---|---|
> | Decken Vertrag, Schema und ADRs **alle 23 Stories** ab? | ✅ **23/23** — jede Story hat AKs, jeder AK einen Ort im Vertrag |
> | ... **alle 101 Akzeptanzkriterien**? | ✅ **101/101** in `x-acceptance-coverage`; **82** zusätzlich maschinenlesbar verankert (`x-ac-id` an 57 Operationen bzw. am `x-delivery-protocol`), **19** als `non_api` (Oberfläche/Datenbank/Betrieb) — die im Vertrag selbst definierte Verankerungsart |
> | ... **alle 24 Anforderungen**? | ✅ **24/24** in `x-requirement-coverage`, **keine** Dubletten, **keine** Lücke gegen `docs/requirements/PRD.md` |
> | Ist die Abdeckung **je Akzeptanzkriterium** nachweisbar? | ✅ `x-ac-id` je Operation (17/17) + `x-acceptance-coverage` je AK (101/101) + `x-requirement-ids` je Operation (17/17) |
> | Ist das **Schema gegen den Vertrag vollständig** (Felder, Wertebereiche, Pflicht/N nullable, Statuswerte, Zeiträume, Aufbewahrungsmarker)? | ✅ **ja** — maschinell geprüft (§ 4.1): alle Entitätsfelder 1:1, alle Statuswerte **identisch** mit den CHECK-Constraints, `abandoned_at`/`replay_of`/`organisation_id` konsistent, **kein** Aufbewahrungsmarker in einem Antwortmodell |
> | Sind die **3 Eigentümerfunktionen** vorhanden? | ✅ **ja**, live belegt: `list_endpoints_cross_tenant`, `list_dead_deliveries_cross_tenant`, `enforce_retention_cross_tenant` — alle `SECURITY DEFINER`, Eigentümer `echobox_owner`, `search_path` verdrahtet, `REVOKE`/`GRANT` korrekt, **keine** Rolle mit `BYPASSRLS` |
> | Ist die **UNIQUE-Regel** im Schema vorhanden? | ✅ `delivery_runs_replay_of_uq UNIQUE (replay_of)` — live belegt: zweiter Replay ⇒ **SQLSTATE 23505** ⇒ HTTP 409 |
> | Trifft die Empfehlung **keinen** offenen „muss“-Befund? | ✅ **ja** — kein offener „muss“-Befund gegen Vertrag, Schema oder ADRs |
>
> **Auflagen (kein Freeze-Blocker):**
> 1. **V-48** (ADR-002 §7) und **V-49** (Rollentabelle ↔ ADR-015 §1) — zwei
>    Ein-Satz-Korrekturen in `docs/adr/`. `docs/adr/` ist in diesem Schritt
>    **nicht** änderbar → **Rücksprung zu Schritt 2**, sobald der Mensch
>    entscheidet (Gate 1).
> 2. **V-45**, **V-46**, **V-47** — gehen als **offene Punkte** in **Schritt 5
>    (Testmatrix)** und **Schritt 8 (Security)** ein. Kein Eingriff in den
>    Vertrag.
> 3. **O-04**, **O-05**, **E-12**, **R-01/E-13** — unverändert offen, Gate 2.

**Was sich seit dem zweiten Bericht geändert hat:** Drei Rücksprünge
(`d6c4e85`, `5a1c421`, `1a3d6a8`) haben **V-26** (mandantenübergreifende Sicht),
**V-27** (Fremd-IDs ⇒ 404), **V-28…V-38** (Abschluss-Sweep, 11 Widersprüche) und
**V-39…V-44** (6 Entscheidungen, 35 Stellen) umgesetzt. Offen bleiben **O-04**,
**O-05**, **E-12**, **R-01/E-13** sowie die drei dokumentierten Befunde
**V-45/V-46/V-47**.

---

## 1. Nachprüfung — sind die Korrekturen **wirklich** drin?

Eine Korrektur, die nicht nachweisbar ist, ist kein Fix. Geprüft wurde am
**aktuellen** Stand (`docs/api/openapi.yaml`, `.agent/knowledge/schema.sql`,
`docs/adr/`, `docs/architecture/`), nicht am Protokoll des Rücksprungs.
**Fundstelle = Zeile im aktuellen Stand.**

### 1.1 Die 22 Vertragsdefekte des ersten Stresstests (V-01…V-22)

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **V-01** | 🟢 behoben | `:99` (`page.next_cursor` / `page.has_more`), `:3257-3270` (`Page`) | Antwortfelder und Schema tragen denselben Namen. |
| **V-02** | 🟢 behoben | `:1543-1564` (Anker, zwei Stufen, Karenzzeit, Kaskade, Geltungsbereich), `:3009-3022` (`purge_delay_days` nullable, „Ein Vorgabewert ist nicht festgelegt“), `:3058-3091` (`marked_*` / `purged_*`), `:3104-3199` (`TenantRetention` mit `*_retention_source`) | Alle fünf Teile getrennt ausgewiesen; „gelöscht“ ist über `marked_*`/`purged_*` entscheidbar. |
| **V-03** | 🟢 behoben | `:165-168` (zweistufige Auskunft), `:487-503` (`states: kein_override` / `abgeschaltet`), `:2896-2940` (`RateLimitObservation`), Beispiel `:1511-1517` | „Kein Override“ (⇒ 60) und „abgeschaltet“ (⇒ 0) sind unterscheidbar; `enabled` kommt im Repository nicht mehr vor. |
| **V-04** | 🟢 behoben | `:297-311` (Body = Nutzlastbytes, kanonisch materialisiert bzw. exakt geliefert), `:2379-2410` (`payload`) | Die Zusage lautet „identisch mit dem, **was das Ereignis trägt**“. |
| **V-05** | 🟢 behoben | `:2557-2564` (`replay_of`, nullable), Beispiele `:1140`, `:1206` | Feld vorhanden und beschrieben („`null`, wenn der Lauf **kein** Replay ist“). |
| **V-06** | 🟢 behoben | `:2136-2148` (`DeliveryStatus`: `pending` = „angelegt, **noch kein Versuch**“), `:1429` (Beispiel `status: pending`) | Deckt sich mit `schema.sql:208` (DEFAULT `'pending'`). |
| **V-07** | 🟢 behoben | `:1053-1055` (Endgültigkeitsliste), `:2611-2614` (`next_attempt_at`) | `no_subscriber` steht in **beiden** Listen; deckt sich mit `delivery_runs_terminal_no_next_attempt_ck`. |
| **V-08** | 🟢 behoben | `:2494` (`payload_media_type` in `required`), `:2508-2513` („immer gefüllt“) | Deckt sich mit `schema.sql:166` (`NOT NULL DEFAULT 'application/json'`). |
| **V-09** | 🟢 behoben | **15/15** authentifizierte Operationen mit `429` (mechanisch geprüft), `:1704-1705` (503 über die Komponente), `:1997-2013` (`ServiceUnavailable`) | Keine tote Komponente; alle 15 nennen die 429. |
| **V-10** | 🟢 behoben | `:1921-1967` (422 mit **zwei** Beispielen), `:2091-2120` (`ValidationFailedProblem`, `TenantParameterRejected`) | `tenant_parameter_rejected` ist erzeugbar; die Fehlerform bleibt **ein** Schema (`oneOf` über zwei `allOf` auf `Problem`). |
| **V-11** | 🟢 behoben | `:2241`, `:2265`, `:2347`, `:2446`, `:2811`, `:2954` | `no_tenant_parameter` in **allen 6** Request-Bodies. |
| **V-12** | 🟢 behoben | `:389-396` (Zweig `3xx` im Ergebnisraster), `:2180-2181` (`client_error`) | „Kein Wiederholen, `rejected`, `rejection_reason = client_error`, Redirects werden nicht befolgt“. |
| **V-13** | 🟢 behoben | `:407-412` (Trennlinie im Transportfehler-Block), `:2174-2181` (`AttemptOutcome`) | `timeout` = Connect-/Lese-Timeout, `network_error` = DNS/TLS/Verbindung **ohne** Timeout. |
| **V-14** | 🟢 behoben | `:1397-1400` (Replay bei pausiertem Endpunkt ⇒ 409, **kein** neuer Lauf) | Festgelegt. |
| **V-15** | 🟢 behoben | `:1130-1147` und `:1197-1234` (beide `attempt_count: 2`, Backoff 1 min → 2 min, `next_attempt_at` = letzter Versuch + 2 min), `:912`/`:966`/`:2424`/`:2520` (`payload_size_bytes: 35`) | Die Beispiele widersprechen sich nicht; der Backoff ist rechnerisch konsistent. |
| **V-16** | 🟢 behoben | `:450-457` (`expected_completion_window`: 7 Wartezeiten = 123 min ≈ 2 h, ± 20 %) | Zusage steht dort, wo die Empfänger sie lesen; `ADR-008` nennt dieselbe Zahl. |
| **V-17** | 🟢 behoben | `:1767-1770`, `:1786-1789` (Parameter `From`/`To`), `:1475-1476` (Kennzahlen) | „Ohne `from`/`to` gilt das Fenster der **letzten 7 Tage** bis jetzt“ — an allen drei Stellen. |
| **V-18** | 🟢 behoben | `:1380-1384` | „Ein aufgegebener Zustelllauf kann **einmal** replayed werden … ein **weiterer** Replay ⇒ HTTP 409“; auf DB-Ebene erzwiesen (live belegt, § 1.6). |
| **V-19** | 🟢 behoben | `:915-919` (Body bis 2 MiB), `:2444-2445` (Validierungsregel), `:2414-2424` (`payload_base64`, ~1,4×) | An allen drei Stellen gesagt. |
| **V-20** | 🟢 behoben | `:229` | Wert und Beschreibung stimmen überein. |
| **V-21** | 🟢 behoben | `:2191-2199` (`RejectionReason`) | `enum: [permanent_rejection, client_error]`, `nullable: true` — kein Nullwert im Enum. |
| **V-22** | 🟢 behoben | `:2374-2377` (Beschreibung), `:2403-2410` (`payload`), `:2440-2441` (`x-validation-rules.exactly_one_payload`) | *Anmerkung:* `nullable: false` ohne `type` ist in OpenAPI 3.0 formal ein No-Op; die Entscheidbarkeit trägt der ausformulierte Regeltext — für die Implementierung eindeutig, für strenge Validatoren auffällig (kein Mangel). |

### 1.2 Die drei Vertragssätze des zweiten Stresstests (V-23, V-24, V-25)

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **V-23** | 🟢 behoben | `:3381` (Abdeckungseintrag **AC-022-3**) | Der Eintrag nennt jetzt die entschiedene Semantik („innerhalb der Transaktion, die auch liest … transaktionslokal — **nicht** verbindungsgebunden“). `grep -n "Connection-Initializer" docs/api/openapi.yaml` ⇒ **kein Treffer**. |
| **V-24** | 🟢 behoben | `:522-523` („mit dem Status **`active`** (aktiv)“), `:754` („auf **`paused`** (pausiert)“) | Die **Werte** stehen in Code-Formatierung, die Übersetzung als Klartext. Die alten Formen „`(aktiv)`“ / „`(pausiert)`“ kommen nicht mehr vor. |
| **V-25** | 🟢 behoben — **überholt durch V-26** | `:2573-2582` (`DeliveryRun.organisation_id`) | Die V-25-Fassung („nur in mandantenübergreifenden Antworten gefüllt“) ist **ersetzt**: das Feld ist jetzt **immer** gefüllt und benennt den **besitzenden Mandanten** (V-26). Der ursprüngliche Zweck des Befunds (Widerspruch zur Rollenzeile) ist damit erledigt. |

### 1.3 Die Semantikentscheidungen V-26 und V-27 (Rücksprung 3 und 4)

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **V-26** | 🟢 behoben | `:42-46` (`info.description`), `:2276` + `:2287-2293` (`Endpoint`), `:2546` + `:2573-2582` (`DeliveryRun`), `:2738` + `:2751-2757` (`DeadDelivery`), `:562`/`:634`/`:777`/`:1135`/`:1201`/`:1335` (Beispiele), `:120-121`, `:3439` (`x-contract-rules.tenant`) | `organisation_id` ist an **allen** drei Stellen **Pflichtfeld** und **nicht** `nullable`; kein Beispiel zeigt mehr `null`; die alte Zusage „Nur für `service_admin` gefüllt“ kommt **nicht mehr** vor. |
| **V-27** | 🟢 behoben | `:29-30`, `:56-60`, `:102-108` (`info.description`), `:604-610` (`listEndpoints`), `:1276-1279` (`listDeadDeliveries`), `:666-671` (`getEndpoint`), `:1178-1183` (`getDeliveryRun`), `:996-1000` (`getEvent`), `:1478-1481` (`getEndpointMetrics`), `:3437-3439` (`x-contract-rules.tenant`) | `V-27` steht **11-mal** im Vertrag; die Klausel „**auch** für `service_admin`“ an **7** Stellen; „Ein **vierter** mandantenübergreifender Leseweg wird **nicht** gebaut“ an **3** Stellen. Die Zahl der Operationen bleibt **17** — es ist **kein** vierter Leseweg entstanden. |

### 1.4 Der Abschluss-Sweep des Rücksprungs 4 (V-28…V-38)

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **V-28** | 🟢 behoben | `:546-582` (Antworten `createEndpoint`) | **Kein 409** mehr deklariert — die Zusage war unerfüllbar; kein neuer Idempotenzschlüssel eingeführt. |
| **V-29** | 🟢 behoben | `:1433-1434` (`replayDelivery`), `:1630-1631` (`enforceRetention`) | Beide Operationen mit Request-Body haben jetzt **400 `MalformedRequest`**. |
| **V-30** | 🟢 behoben | `:1151` (Cursor-Beispiel) | Dekodiert zu `{"created_at":"2026-10-21T09:15:22.481Z","delivery_run_id":"7d2b1c9a-…"}` — Sortierschlüssel **+** UUID-Tie-Breaker, gültiges Base64 (**132** Zeichen, ≤ 512, padding-richtig). |
| **V-31** | 🟢 behoben | `:864` (Beispiel), `:2345` (`SecretRotationRequest.secret.example`) | Beide auf `"neues-s3cr3t-rotationswert"` (**26** Zeichen ≥ `minLength: 16`). |
| **V-32** | 🟢 behoben | `:1132` (`7d2b1c9a` = `failed`, 2 Versuche), `:1198` (dieselbe ID, gleicher Zustand), `:1333` (`b4c3d2e1` = `abandoned`, 8 Versuche), `:1426-1427` (Vorgänger `b4c3d2e1` → neu `9a8b7c6d`) | Jede `delivery_run_id` steht jetzt in **genau einem** Zustand. |
| **V-33** | 🟢 behoben | `:2065-2072` (`Problem.violations`) | Beschreibung nennt **beide** 422-Codes. |
| **V-34** | 🟢 behoben | `:2078-2081` (`ValidationViolation.field`) | „Das betroffene Feld (Feldname, z. B. `target_url`)“ — kein JSON-Pointer mehr. |
| **V-35** | 🟢 behoben | `:2136-2148` (`DeliveryStatus`), `:2142-2143` | `failed` ist jetzt beschrieben („der letzte Versuch ist fehlgeschlagen, ein weiterer ist nach dem Backoff geplant (ADR-008)“). |
| **V-36** | 🟢 behoben | `:358-362` (`X-EchoBox-Rejection`) | `required: true` + „— **Pflicht** beim Signal (ADR-009)“. |
| **V-37** | 🟢 behoben — **überholt durch V-43** | `:2486-2534` (`Event`) | Das Feld `deleted_at` existiert **nicht mehr**; die Ersatzbeschreibung steht in `:2488-2493`. |
| **V-38** | 🟢 behoben | `:2374-2377`, `:2403-2410`, `:2440-2441` | Alle drei Stellen sagen jetzt „Ein `payload: null` verletzt das Schema (HTTP 422 `validation_failed`) und gilt **nicht** als gesetzt.“ |

### 1.5 Die sechs Entscheidungen des Rücksprungs 5 (V-39…V-44) — **Durchziehung geprüft**

| ID | Status | Fundstelle **jetzt** | Begründung der Durchziehung |
|---|---|---|---|
| **V-39** | 🟢 behoben | Vertrag: `:32-40` (Mandant-Absatz), `:71-72` (Fehlersemantik 403), `:72`/`:89-94` (Grenze 403/404), `:157` (F-03), `:1390-1395` (`replayDelivery`), `:1848-1852` (`responses.Forbidden`), `:1864-1868` (`responses.NotFound`), `:3432` (`x-contract-rules.defaults`), `:3438` (`x-contract-rules.tenant`) — ADR: `ADR-015:115-123` (§3), `:237-244` (Status) | `V-39` steht an **8** Vertragsstellen und **3-mal** in ADR-015; die Formulierung „**erst der Mandant, dann die Rolle**“ an **3** Vertragsstellen und **2-mal** in der ADR. **Durchziehung an allen geprüften Stellen:** jede 403-Aussage nennt den **eigenen** Mandanten, jede 404-Aussage den **fremden**; `replayDelivery` und `responses.Forbidden`/`NotFound` nennen die Reihenfolge ausdrücklich. |
| **V-40** | 🟢 behoben | `:139-144` (Suchbarkeit), `:1061-1064` (`listDeliveries`), `:1266-1268` (`listDeadDeliveries`), `:1772-1778` (`From`), `:1791-1797` (`To`) — ADR: `ADR-017:72-81` | `created_at` an **5** Vertragsstellen, `abandoned_at` an **4**; beide Aussagen stehen **nebeneinander** und sind **je benannt**. Der shared-Parameter nennt **alle drei** Bindungen, jede Operation ihre **eigene** zusätzlich. Sortierschlüssel und Filter des Protokolls sind **ein** Schlüssel. |
| **V-41** | 🟢 behoben | `:3219-3254` (`ReadinessStatus`: `status.enum [ready]`, `checks.*.enum [ok]`), `:1681-1685` (`getReadiness`), `:1997-2002` (`responses.ServiceUnavailable`), `:80` (Fehlersemantik 503) | `not_ready` ⇒ **0 Treffer** im gesamten Vertrag; `unavailable` ⇒ **0 Enum-Werte** (die verbleibenden Treffer sind ausschließlich `service_unavailable`). Das Schema beschreibt **genau** den Zustand, den der Vertrag liefert. |
| **V-42** | 🟢 behoben | `:2846` (`required: [endpoint_id, window, data_available, rate_limit]`), `:2896-2905` (`RateLimitObservation`), `:1465-1473` (`getEndpointMetrics`), Beispiel `:1511-1517` | `required` nennt jetzt `rate_limit`; das Beispiel **enthält** den Abschnitt; der Abdeckungseintrag **AC-018-4** (`:3366`) nennt dieselbe Aussage. |
| **V-43** | 🟢 behoben | `:2486-2534` (`Event` — Feld **entfernt**), `:2488-2493` (neue Beschreibung), `:1018-1029` (Beispiel `getEvent` — `deleted_at` entfernt) | `deleted_at` kommt im Vertrag **3-mal** vor — alle drei gehören zum **Markiervorgang** der Aufbewahrung (`:1548`, `:2966`, `:3062`). **Kein** Vorkommen in einem **Antwortmodell**. `Event.required` enthielt `deleted_at` nie. |
| **V-44** | 🟢 behoben | `ADR-015:35` (Matrixzelle), `:37-52` (zwei Korrekturhinweise), `:59-60` (§1 `organisation_id`), `:87-92` (§2), `:71` (Replay-Zeile), `:115-123` (§3), `:237-244` (Status) — `docs/adr/README.md:112` (Indexzeile) + Korrekturabschnitt | `grep "nur in der Liste"` ⇒ **1** Treffer (die Matrixzelle); `grep "dort ist das Feld \`null\`"` ⇒ **kein** Treffer; `grep "im Datensatz gefüllt"` ⇒ **kein** Treffer. Vertrag und ADR-015 sagen jetzt **dasselbe**. |

### 1.6 S-01, N-01, N-02, N-08 — **erneut** gegen eine echte PostgreSQL-16-Instanz

**Instanz:** PostgreSQL **16.15** (Ubuntu 16.15-0ubuntu0.24.04.1), frische
Datenbank, Unix-Socket, Trust-Auth. Das kanonische Schema wurde mit
`psql -f .agent/knowledge/schema.sql` **ohne Fehler** angewendet.

#### Die vier Fragen zu S-01 (vertraglich gefordert)

| Frage | Prüfung | Ergebnis |
|---|---|---|
| **Existieren die drei `SECURITY DEFINER`-Eigentümerfunktionen?** | `pg_proc`/`pg_namespace` | ✅ **3 von 3**: `list_endpoints_cross_tenant`, `list_dead_deliveries_cross_tenant`, `enforce_retention_cross_tenant` — alle `prosecdef = t`, Eigentümer `echobox_owner` |
| **Ist `search_path` verdrahtet?** | `proconfig` | ✅ **ja** — bei allen drei `{"search_path=app, pg_temp"}` (fest verdrahtet, **nicht** per Aufrufparameter) |
| **Sind `REVOKE`/`GRANT` gesetzt?** | `has_function_privilege` | ✅ `public_may = **f**` (REVOKE), `app_may = **t**` (GRANT), `worker_may = **f**` — der Worker ist **nicht** freigegeben |
| **Gibt es `BYPASSRLS` irgendwo?** | `pg_roles` | ✅ **nein**: `echobox_owner`, `echobox_app`, `echobox_worker` — je `rolsuper = f`, `rolbypassrls = f`, `rolcanlogin = f`. 10/10 Tabellen mit RLS, je 1 Policy, `FORCE ROW LEVEL SECURITY` bewusst nicht gesetzt |

#### Das Verhalten (live gemessen, zwei Mandanten)

| Zusicherung | Messung | Ergebnis |
|---|---|---|
| **Fail-closed: 0 Zeilen ohne Kontext** | `echobox_app` **ohne** `set_tenant` | ✅ `endpoints` **0**, `events` **0**, `delivery_runs` **0**, `delivery_attempts` **0** |
| **Fail-closed: 0 Zeilen ohne Freigabe** | `echobox_app` **ohne** `app.cross_tenant` | ✅ alle drei Eigentümerfunktionen **0 Zeilen** |
| **Normaler Mandant sieht keine fremden Zeilen** | `set_tenant('m-1')`, zwei Mandanten | ✅ `endpoints` **1**, `events` **1**, `delivery_runs` **1**, `delivery_attempts` **1** (nur m-1); `events`/`delivery_runs`/`delivery_attempts` von m-2: je **0** |
| **`service_admin` sieht mandantenübergreifend — aber nur über die drei Listen-/Betriebsoperationen** | `set_config('app.cross_tenant','service_admin', true)` | ✅ Endpunktliste **2** Zeilen (m-1 **und** m-2), je mit `organisation_id`; Totenliste **1** Zeile (ausschließlich `abandoned`); Aufbewahrung **2** Zeilen (je Mandant). **Und:** der **direkte** Tabellenzugriff liefert dabei **0** Zeilen — es gibt **keinen** mandantenübergreifenden Einzelabruf (V-27) |
| **Projektionen ohne Klartext und ohne Secrets** | Rückgabe gegen die Klartextwerte geprüft (`deadbeef`, `cafe`, `f00d`, `4711`, `order_id`) | ✅ **0 Treffer** in allen drei Projektionen (2 + 1 + 2 Zeilen) |
| **N-14 — die Totenliste liefert ausschließlich `abandoned`** | `list_dead_deliveries_cross_tenant()` **ohne jeden Parameter** | ✅ **1** Zeile, `status = abandoned`, `abandoned_at` gesetzt; der `sent`-Lauf des anderen Mandanten ist **nicht** enthalten; die Signatur hat **keinen** `p_status` |
| **N-13 — markierte Versuche verlieren die Sichtbarkeit** | `enforce_retention_cross_tenant(false, 1, 1, 30)` | ✅ `marked_attempts = 1`; danach: Läufe **0**, Versuche **0** für die Applikationsrolle — die Zeilen sind physisch da (als Eigner **1**), nur unsichtbar |
| **N-15 — die Replay-Historie überlebt den Purge** | Vorgänger gelöscht | ✅ `replay_of` des überlebenden Replays **unverändert** = ID des gelöschten Laufs; in `pg_constraint` **nur** `delivery_runs_replay_of_uq` (Typ `u`), **kein** Fremdschlüssel |
| **N-16 — logische Löschung durch die Applikationsrolle** | `SET ROLE echobox_app; UPDATE … SET deleted_at = now()` | ✅ `ERROR: new row violates row-level security policy for table "delivery_runs"` |
| **Die UNIQUE-Regel** | zweiter Replay desselben Laufs | ✅ `ERROR: duplicate key value violates unique constraint "delivery_runs_replay_of_uq"` ⇒ **SQLSTATE 23505** ⇒ HTTP 409 `delivery_state_conflict` |

#### Die drei Standards-Befunde

| ID | Status | Fundstelle **jetzt** | Begründung |
|---|---|---|---|
| **N-01** (Mandantenkontext an die **Transaktion**, nicht an die Verbindung) | 🟢 behoben | `ADR-002:27-38` (§3, Ziffer 3), `schema.sql:25-32` (Kopf), `:445-460` (`app.set_tenant` + Kommentar), `folder-structure.md:123`, `:190`, `diagrams/04`, `architecture.md:197` | Der Kontext ist **überall** transaktionsgebunden: „innerhalb der Transaktion, die auch liest, immer vor der ersten Abfrage; kein Autocommit, kein `REQUIRES_NEW`“. **Kein Rest** auf „Connection-Initializer“-Semantik — der Klassenname bleibt nur historisch (ADR-002:41-44). |
| **N-02** (Worker beansprucht ohne Mandantenermittlung) | 🟢 behoben | `ADR-006:43-56` (§3 mit der Kette und dem SQL), `schema.sql:41-43`, `:462-485` (`app.tenant_ids_for_worker`), `architecture.md:198`, `folder-structure.md:125`, `diagrams/04:27`, `diagrams/05:35` | Die Kette steht **überall gleich**: Scheduler → `app.tenant_ids_for_worker()` → je Mandant `set_tenant` → Claim; **je Mandant eine eigene Transaktion**. |
| **N-08** (Schema-Kommentare vor F-01/F-02) | 🟢 behoben | `schema.sql:15-19`, `:290-295`, `:335-341`, `:361-380`, `:433-444`, `:462-471` | Alle Kommentare nennen den **entschiedenen** Stand: `NULL` = **kein Override** ⇒ es gilt die Vorgabe (60/min bzw. 30 Tage). |

**Nachprüfung gesamt: 48 von 48 entschiedenen Befunden am aktuellen Stand belegt
behoben** (44 Vertrags-/ADR-Befunde, der Architekturbruch S-01, die drei
Standards-Befunde N-01/N-02/N-08).

---

## 2. Delta-Runde — was ist durch die letzten drei Rücksprünge **neu** entstanden?

Geprüft wurden **nur die von V-26…V-44 berührten Bereiche** — **kein** neuer
Voll-Sweep (der ist gelaufen: 26 Stellen, V-28…V-38, plus 35 Stellen in
Rücksprung 5).

### 2.1 Rollen-/Mandantensemantik (V-26 / V-27 / V-39) — an allen Stellen gleich?

**Ja — mit einer Ausnahme, die keine Semantik betrifft (V-49).**

| Ort | Aussage | gleich? |
|---|---|---|
| `info.description` § „Mandant“ (`:22-46`) | 404 für alle Rollen; **Reihenfolge: erst der Mandant, dann die Rolle**; `organisation_id` immer gefüllt | ✅ |
| `info.description` § „Rollen“ (`:48-60`) | Rollentabelle + V-27-Absatz | ⚠️ Tabelle **unvollständig** gegenüber ADR-015 §1 → **V-49** |
| `info.description` § „Fehlersemantik“ (`:62-94`) | 403 = „eigener Mandant, falsche Rolle“; 404 = „fremder Mandant“; eigene Grenzabschnitt | ✅ |
| `info.description` § „Pagination“ (`:96-130`) | Fremd-IDs ⇒ Detailabruf 404 | ✅ |
| `info.description` § „Vorgabewerte“ F-03 (`:157`) | 403 **innerhalb** des eigenen Mandanten, 404 bei fremdem | ✅ |
| Fehlerschema-Komponenten (`:1848-1868`) | `Forbidden` = eigener Mandant; `NotFound` = fremd/fehlend, „zuerst der Mandant, danach die Rolle“ | ✅ |
| `x-contract-rules.defaults` / `.tenant` (`:3432`, `:3438-3439`) | dieselbe Reihenfolge, dieselbe Drei-Operationen-Regel | ✅ |
| `replayDelivery` (`:1386-1395`) | „Zuerst wird der **Mandant** geprüft … 404 — **auch** für `developer` und `service_admin`“ | ✅ |
| Die vier Lesoperationen (`:666-671`, `:1178-1183`, `:996-1000`, `:1478-1481`) | nennen die Fremdmandanten-404 **auch** für `service_admin` | ✅ |
| ADR-015 §1/§2/§3 (`:35`, `:87-92`, `:115-123`) | V-26, V-27, V-39 wortgleich | ✅ |
| ADR-002 §7 (`:82`) | „In den mandantenübergreifenden Antworten ist `organisation_id` **im Datensatz** gefüllt“ | ⚠️ **schwächere** Fassung als V-26 → **V-48** |
| **Schema** | drei Eigentümerfunktionen, **kein** vierter Leseweg; der direkte Tabellenzugriff bleibt mandantengefiltert (live: **0** Zeilen mit `app.cross_tenant` gesetzt) | ✅ |

**Befund:** Die Semantik ist an **allen** Vertrags- und ADR-Stellen **gleich**.
Die beiden Ausnahmen (V-48, V-49) betreffen **Formulierungen**, nicht die
Semantik.

### 2.2 Zeitraumfilter (V-40) — Protokoll (`created_at`) und Totenliste (`abandoned_at`)

| Ort | Aussage | gleich? |
|---|---|---|
| `info.description` § „Suchbarkeit“ (`:139-144`) | Protokoll ⇒ `created_at`; Totenliste ⇒ `abandoned_at`; „beide Aussagen stehen **nebeneinander** und sind je Liste benannt“ | ✅ |
| `listDeliveries` (`:1061-1064`) | `from`/`to` ⇒ **Anlegezeitpunkt** (`created_at`) — derselbe Schlüssel wie die Sortierung; Querverweis auf die Totenliste | ✅ |
| `listDeadDeliveries` (`:1266-1268`) | ⇒ **Aufgabezeitpunkt** (`abandoned_at`) — „**nicht** auf `created_at`; das **Zustellprotokoll** filtert nach `created_at`“ | ✅ |
| `From` / `To` (`:1772-1778`, `:1791-1797`) | nennen **alle drei** Bindungen (Protokoll, Totenliste, Kennzahlen/Auswertungsfenster) | ✅ |
| ADR-017 §4 (`:72-81`) | dieselbe Aussage; `accepted_at`/`last_attempt_at` bleiben suchbare Metadaten, aber **kein** Vertragsfilter | ✅ |
| **Schema** | Protokoll-Index `delivery_runs_tenant_created_at_idx (organisation_id, created_at DESC)`; Totenlisten-Index `delivery_runs_dead_letter_idx (organisation_id, abandoned_at DESC) WHERE status='abandoned'`; die Eigentümerfunktion filtert `d.abandoned_at` und sortiert `d.created_at DESC` | ✅ |

**Befund:** Beide Zeitraum-Aussagen sind **widerspruchsfrei** und **je benannt**;
Filter und Sortierung des Protokolls sind **ein** Schlüssel. *Randnotiz (kein
Defekt):* die Cross-Tenant-Totenliste **filtert** nach `abandoned_at` und
**sortiert** nach `created_at` — genau die Entscheidung V-40; der stützende Index
(N-17) trägt die **Sortierung**, die Filterung greift als Heaps-Bedingung.

### 2.3 Antwortformen (V-41 / V-42 / V-43)

| ID | Erwartet | Ist | Status |
|---|---|---|---|
| **V-41** | `ReadinessStatus` beschreibt **nur** beobachtbare Werte; nicht bereit ⇒ 503 `Problem` | `status.enum [ready]`, `checks.*.enum [ok]`; `not_ready`/`unavailable` **0** Enum-Treffer | ✅ |
| **V-42** | `rate_limit` **immer** Teil der Antwort | in `required` (`:2846`); Beispiel enthält den Abschnitt | ✅ |
| **V-43** | `Event.deleted_at` entfernt | Feld existiert nicht mehr; `deleted_at` nur noch im Markiervorgang | ✅ |
| **V-46** (offen) | optionale, aber als vorhanden beschriebene Felder | siehe § 2.5 | 🟡 offen |

### 2.4 ADR-015 und ADR-017 nach dem Angleichen

* **ADR-015** — wortgleich mit dem Vertrag zu V-26, V-27 und V-39; die beiden
  V-44-Sätze sind **ersetzt** (`:35`, `:87-92`); §3 trägt die Reihenfolge
  (`:115-123`); der Statusvermerk nennt den Rücksprung (`:237-244`). Der
  ADR-Index (`docs/adr/README.md:112` + Korrekturabschnitt) deckt alle **18**
  Dateien ab.
* **ADR-017** — §4 (`:72-81`) trägt den V-40-Satz; die Filterliste (`:65-66`)
  und die Sortierung (`:68-70`) bleiben unverändert und stimmen mit dem Vertrag
  überein.
* **ADR-002** — §7 (`:82`) ist die **einzige** verbliebene Stelle mit der
  schwächeren V-26-Fassung → **V-48**.

### 2.5 V-45, V-46, V-47 — echte Widersprüche oder Dokumentationslücken?

> **Kriterium:** Ein echter Widerspruch liegt vor, wenn eine Stelle einer
> **bestätigten Entscheidung** (D-01…D-15, F-01…F-04, E-01…E-13, ADR-001…ADR-018,
> V-26, V-27, V-39) **widerspricht**. Eine Dokumentationslücke liegt vor, wenn
> die Semantik **entschieden und im Vertrag eindeutig** ist, eine Stelle sie nur
> **nicht ausspricht**.

#### **V-45** — AC-013-3 nennt 403 ohne Mandantbezug → 🟢 **Dokumentationslücke (Schritt 5)**

* **Was der Vertrag sagt:** `replayDelivery` (`:1390-1395`) — „Zuerst wird der
  **Mandant** geprüft. Ein Zustelllauf eines **fremden** Mandanten ergibt
  **HTTP 404** — **auch** für `developer` und `service_admin`“. Dieselbe Regel in
  `info.description` (`:32-40`), in der Fehlersemantik (`:89-94`), in
  `responses.Forbidden`/`NotFound` (`:1848-1868`), in `x-contract-rules` (`:3438`)
  und in **ADR-015 §3** (`:115-123`) — **sechs** Stellen, **einsinnig**.
* **Was die Anforderung sagt:** `US-013.json` AC-013-3 — „given: eine Rolle,
  die nach O-03 nicht zum erneuten Auslösen berechtigt ist … then: antwortet der
  Dienst mit HTTP 403“. Der „given“-Fall sagt **nichts** über den Mandanten.
  Dasselbe Muster im Gherkin: `US-013.feature:16-19` („Given eine tote
  Zustellung / When eine nicht berechtigte Rolle sie erneut auslöst / Then … HTTP
  403“).
* **Bewertung:** **kein** Widerspruch gegen V-39 — die Entscheidung ist
  entschieden und im Vertrag **eindeutig**; die Lücke liegt in der
  **Anforderungsebene** (`docs/requirements/` — Schritt-1-Eigentum, in diesem
  Schritt **nicht** änderbar). Zum Vergleich AC-003-3: „ein Entwickler
  **desselben Mandanten**“ ⇒ 403 — dort ist die Trennung sauber.
* **Konsequenz:** Die **Testmatrix** (Schritt 5) muss **zwei** Fälle führen:
  *eigener Mandant + falsche Rolle* ⇒ **403** · *fremder Mandant* ⇒ **404**.
* **Empfehlung:** (a) — die Anforderung wird **nicht** geändert; die Testmatrix
  bildet beide Zweige ab. **Kein** Eingriff in den Vertrag, **kein** Rücksprung.

#### **V-46** — optionale, aber als „immer vorhanden“ beschriebene Antwortfelder → 🟢 **Dokumentationslücke (Schritt 5), Präzisierung nötig**

* **Was der Vertrag sagt (Beschreibung):** `EndpointMetrics.data_available`
  (`:2853-2858`) — „Dann sind `success_rate` und `delivery_latency_p95_ms`
  `null` — **nicht** 0 % und **nicht** 100 %“; `Page.next_cursor` (`:3267-3270`)
  — „`null` bei der letzten Seite“; `DeliveryRun.endpoint_id` (`:2568-2572`) —
  „`null` bei `no_subscriber`“.
* **Was der Vertrag sagt (Schema):** alle drei sind `nullable: true`, aber
  **nicht** in `required` — sie **dürfen fehlen**. Weitere Felder derselben
  Musterlage: `EndpointMetrics.attempts_total` / `attempts_successful`
  (`:2859-2868`, im Beispiel immer vorhanden), `Event.subscribed_endpoint_count`
  (`:2523-2525`), `EventIntakeResponse.subscribed_endpoint_count` /
  `payload_size_bytes` (`:2469-2481`).
* **Bewertung:** **kein** Widerspruch gegen eine bestätigte Entscheidung —
  V-42 hat **nur** `rate_limit` entschieden, nicht das allgemeine Muster; die
  **Schema-Aussage ist eindeutig implementierbar** (nullable, darf fehlen), und
  das Schema ist die normative Ebene. Die **Beschreibung** ist eine Stufe
  stärker als das Schema — dieselbe Klasse wie **V-37** (Beschreibung ↔ Schema),
  die im Rücksprung 4 als „**sollte**“ eingestuft und behoben wurde.
* **Auswirkung, wenn es so bleibt:** ein streng schema-getriebener Client
  behandelt „fehlend“ und „`null`“ als **zwei** Fälle; die Implementierung wird
  voraussichtlich nur „`null`“ liefern (die Beschreibung ist erfüllbar), der
  Client muss beide kennen. Kein AK ist betroffen (AC-017-3 verlangt
  `data_available: false`, **nicht** die Anwesenheit der Zähler).
* **Empfehlung:** **(a)** — dieselbe Musterentscheidung wie V-42: alle Felder in
  `required` aufnehmen, `null` über `nullable` ausdrücken. Das ist eine
  **neue Zusage in der Antwortform** und damit eine Vertragsänderung — **nach
  dem Freeze ein Rücksprung**. **Kein** Freeze-Blocker: der Vertrag ist in
  seinem jetzigen Stand **eindeutig** (das Schema sagt, was erlaubt ist).

#### **V-47** — vier endpunktändernde Operationen schweigen zur Fremdmandanten-404 → 🟢 **Dokumentationslücke (Schritt 5)**

* **Was der Vertrag sagt:** `getEndpoint` (`:666-671`), `getDeliveryRun`
  (`:1178-1183`), `getEvent` (`:996-1000`), `getEndpointMetrics` (`:1478-1481`)
  nennen die Regel **ausdrücklich**.
* **Was er nicht sagt:** `updateEndpoint` (`:698-711`), `pauseEndpoint`
  (`:750-759`), `resumeEndpoint` (`:803-809`), `rotateEndpointSecret`
  (`:840-848`) **adressieren** dieselbe Ressource, **deklarieren** eine 404
  (maschinell geprüft: alle vier haben `404`) — **nennen** die Regel aber nicht.
* **Bewertung:** **kein** Widerspruch — die vertragsweite Regel (erst der
  Mandant, dann die Rolle) trägt sie; die 404 ist deklariert; die AKs decken es
  ab (**AC-002-3**: „ein Endpunkt des Mandanten M2 ⇒ HTTP 404“). Es ist eine
  **Lücke in der operationseigenen Beschreibung** — nicht in der Semantik.
* **Empfehlung:** **(a)** — den Satz an den vier Operationen ergänzen; reine
  Textergänzung **ohne** neue Zusage, aber nach dem Freeze nur mit Rücksprung.
  **Kein** Freeze-Blocker.

### 2.6 Neue Befunde dieser Runde

#### **V-48** — ADR-002 §7 nennt `organisation_id` nur für die mandantenübergreifenden Antworten — **„sollte“** (ADR ↔ Vertrag)

| | |
|---|---|
| **Fundstelle** | `docs/adr/ADR-002-mandantentrennung-rls.md:82` — „In den mandantenübergreifenden Antworten ist `organisation_id` **im Datensatz** gefüllt, damit der Client die Herkunft erkennt.“ |
| **Erwartet (V-26)** | `organisation_id` ist **immer** gefüllt und benennt den **besitzenden Mandanten** — im eigenen Mandanten den eigenen. |
| **Tatsächlich** | Die ADR nennt die Füllung **nur** für die mandantenübergreifenden Antworten — die **V-25-Fassung**, die V-26 abgelöst hat. |
| **Auswirkung** | **Kein** Widerspruch: der Satz behauptet das Gegenteil nicht, er ist nur **schwächer** als die entschiedene Fassung. Wer ADR-002 §7 allein liest, kann „für andere Rollen `null`“ ableiten — genau die Lesart, die **V-44** in ADR-015 §2 korrigieren musste. |
| **Korrektur** | Den Bullet auf die V-26-Fassung bringen: „`organisation_id` ist **immer** gefüllt und benennt den **besitzenden Mandanten** der Ressource — im eigenen Mandanten den eigenen, in den mandantenübergreifenden Antworten den Mandanten, zu dem der Datensatz gehört (V-26).“ |
| **Priorität** | **sollte** — `docs/adr/` ist in diesem Schritt **nicht** änderbar → **Rücksprung zu Schritt 2** (Gate 1). |

#### **V-49** — die Rollentabelle des Vertrags ist gegenüber ADR-015 §1 unvollständig — **„sollte“** (Vertrag ↔ ADR)

| | |
|---|---|
| **Fundstelle** | Vertrag `docs/api/openapi.yaml:50-54` (Rollentabelle) gegen `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:67-68` („Zusätzliche Rollenprüfungen“) und `docs/requirements/PRD.md:74` (Rollenzeile) |
| **Erwartet** | Eine einzige, widerspruchsfreie Aussage, wer welche Operation ausführen darf. |
| **Tatsächlich** | ADR-015 §1 gewährt dem `service_admin` **zwei** Rechte, die weder die Rollentabelle des Vertrags noch die Rollenzeile des PRD nennen: **„Endpunkt anlegen / ändern / pausieren \| nur `receiver_admin` und `service_admin`“** (`:67`) und **„Ereignis einliefern \| `developer` (und `service_admin` für Migrations-/Testzwecke)“** (`:68`). Umgekehrt ist die Matrixzelle `service_admin` / „Endpunkte verwalten“ = „**ja** (alle Mandanten — **nur in der Liste** …)“ in der Form mehrdeutig: die Spalte sagt „verwalten“, die Klammer sagt „nur sehen“. |
| **Auswirkung** | Ein Implementer, der dem **Vertrag** folgt (Single Source of Truth, ADR-016), antwortet dem `service_admin` bei `createEndpoint`/`updateEndpoint`/`pauseEndpoint`/`resumeEndpoint` und `submitEvent` mit **403**; wer **ADR-015** folgt, mit **201/200/202**. **Kein** AK und **kein** FR ist betroffen (FR-004 nennt nur „**sehen**“ aller Endpunkte, FR-010 nur den Entwickler, FR-031 nur die Totenliste) — die Testmatrix übt diese Rechte nicht aus. |
| **Korrektur** | **Zwei Wege, einer ist richtig:** (a) ADR-015 §1 auf PRD + Vertrag **verschärfen** — der Service-Admin verwaltet Endpunkte **nur im eigenen Mandanten** (wie `receiver_admin`) und liefert **keine** Ereignisse ein; oder (b) die Rollentabelle des Vertrags um beide Rechte ergänzen. **(a) ist die Empfehlung:** die Anforderung (PRD) fordert sie nicht, und ein Recht des Plattformbetriebs auf die Endpunktverwaltung **aller** Mandanten ist eine Rechteausweitung, die niemand verlangt hat. |
| **Priorität** | **sollte** — `docs/adr/` ist in diesem Schritt **nicht** änderbar, der Vertrag wird eingefroren → **Rücksprung zu Schritt 2** (Gate 1). **Nicht** freeze-blockierend: kein AK, kein FR und kein Schema hängen daran. |

---

## 3. Achse Standards

*Axe: Repo-Konventionen, Benennung, Struktur, Konsistenz zwischen
`docs/architecture/` ↔ `docs/adr/` ↔ `.agent/artifacts/` ↔ `.agent/knowledge/`,
tote Artefakte, Implementierbarkeit.*

### 3.1 Ergebnis — kein neuer Standards-Befund

| Geprüft | Ergebnis |
|---|---|
| **Ein Ort pro Wahrheit** (ADR-016) | ✅ `.agent/knowledge/openapi.yaml` ist ein **reiner Verweis** (`paths: {}`, kein Schema, kein Beispiel); der Schema-Spiegel ist **byte-identisch** (`diff` leer, 55.2 KB / 1.122 Zeilen); die **17 Entscheidungen + 1 Verweisdatei** sind im ADR-Index abgedeckt (alle 18 Dateien geprüft) |
| **Namenskonventionen** | ✅ `operationId` camelCase, `snake_case` in der DB, Pfade `kebab-case` (`secret-rotation`, `dead-deliveries`, `retention/enforcement`) — durchgängig |
| **`docs/architecture/` ↔ Vertrag** | ✅ geprüft an allen von V-39…V-44 berührten Stellen: `architecture.md:196` (mandantenübergreifende Sicht **nur** für die drei Operationen, fest verdrahteter Totenlisten-Status), `:197` (transaktionsgebundener Mandant), `:198` (Mandantenkette im Worker), `:201` (zwei Rate-Limit-Zustände), `:202` (Aufbewahrung: Versuche folgen ihrem Lauf), `:217-219` (403 = Rolle, 404 = fremder Mandant); `folder-structure.md:113` (fest verdrahteter Status, `status` wird **nicht** weitergereicht), `:114` (markierte Versuche zählen nicht mit), `:116` (logische Löschung nur über die Eigentümerfunktion), `:125` (`CrossTenantScope`); `domain_model.md:208` (I-11); `GLOSSARY.md:205-206` |
| **Diagramme** | ✅ `03-component-api.mmd:24` (`CrossTenantScope`) + `:51-54` (die drei Beziehungen); `04-component-worker.mmd:27` (`tenant_ids_for_worker`); `05-sequence-delivery.mmd:35` (Mandantenkette) — **kein** Widerspruch zum Vertrag; der N-21-Fix aus der zweiten Runde steht |
| **Tote Artefakte** | ✅ keine: `src/` existiert nicht (Neuaufbau), `docs/api/` enthält **eine** Datei, `docs/adr/` 18 (17 + 1), `docs/architecture/` 7 Dateien + 5 Diagramme |
| **Implementierbarkeit** | ✅ die Zielstruktur benennt für jede Vertragsoperation eine Klasse (`EndpointController`, `DeliveryController`, `ProtocolController`, `MetricsController` …) und für jede fachliche Regel einen Port (`ClockPort`, `IdGenerator`, `SecretPort`, `CursorCodec`, `ProblemFactory`) |
| **Meldungen an den Menschen** | ✅ die Skill-Vorgabe „Fakten sind deine Aufgabe“ ist eingehalten: der PostgreSQL-Nachweis wurde **erneut selbst geführt** (§ 1.6), statt nachgefragt zu werden |

### 3.2 Korrekturen in `docs/architecture/` — **keine nötig**

> Geprüft: `architecture.md`, `domain_model.md`, `folder-structure.md`,
> `GLOSSARY.md`, `schema.sql` (Spiegel), alle fünf Diagramme.
> **Ergebnis: kein echter Fehler gefunden** — insbesondere nennt
> `docs/architecture/` `Event.deleted_at` **nirgends** (V-43 bleibt konsistent),
> `not_ready`/`unavailable` **nirgends** (V-41) und die Fremdmandanten-404 nur an
> den Stellen, an denen der Vertrag sie nennt. Die beiden Ausnahmen aus § 2
> (V-48, V-49) liegen in `docs/adr/` bzw. im **Vertrag** — beide außerhalb des
> Schreibbereichs dieses Schritts.

---

## 4. Achse Spezifikation

*Axe: `docs/api/openapi.yaml` ↔ `docs/requirements/` ↔ `docs/adr/` ↔ Schema.*

### 4.1 Die Konsistenzmatrix — geprüft, nicht behauptet

| Gegenstand | Vertrag | Schema | ADR | Ergebnis |
|---|---|---|---|---|
| **Abdeckung** | 101/101 AKs, 24/24 Anforderungen | — | — | ✅ **lückenlos** gegen `docs/requirements/stories/*.json` (101 AKs) und `PRD.md` (24 Anforderungen); **keine** Dubletten |
| **Vertragsform** | 17 Operationen, alle mit `operationId`, `summary`, `description`, `security`, `x-ac-id`, `x-requirement-ids` | — | — | ✅ 177 `$ref`, **alle** auflösbar; **kein** ungenutztes Schema (37), **keine** ungenutzte Antwort-Komponente (11), **kein** ungenutzter Parameter (7) oder Header (1) |
| **Felder 1:1** | `Endpoint` (10), `DeliveryRun` (16), `DeliveryAttempt` (10), `DeadDelivery` (12), `Event` (11) | dieselben Spalten (+ `endpoint_url`/`event_types`/`payload_is_pre_encrypted`/`subscribed_endpoint_count` als **abgeleitete** Größen) | ADR-002, ADR-011 | ✅ **vollständig** — kein Feld fehlt, kein Feld ohne Spalte |
| **Statuswerte** | `EndpointStatus`, `EventStatus`, `DeliveryStatus` (9), `DeadDeliveryStatus` (1), `AttemptOutcome` (7), `RejectionReason` (2) | CHECK-Constraints `endpoints_status_ck`, `events_status_ck`, `delivery_runs_status_ck`, `delivery_attempts_outcome_ck`, `delivery_runs_rejection_reason_ck` | ADR-006, ADR-008 | ✅ **exakt identisch** (maschinell verglichen) |
| **Wertebereiche** | `minLength`/`maxLength`/`minimum`/`maximum`/`enum` | CHECK-Constraints (Längen, `>= 1`, `>= 0`) | — | ✅ **0 Abweichungen** in **56** Beispielobjekten (20 Top-Level + 16 Array-Elemente + 20 Property-Beispiele) |
| **Zeiträume** | V-40: Protokoll ⇒ `created_at`, Totenliste ⇒ `abandoned_at` | Indizes `delivery_runs_tenant_created_at_idx`, `delivery_runs_dead_letter_idx`; Eigentümerfunktion filtert `d.abandoned_at` | ADR-017 §4 | ✅ **identisch** |
| **Aufbewahrungsmarker** | `deleted_at` **nur** im Markiervorgang (3 Stellen) | `app.events.deleted_at`, `app.delivery_runs.deleted_at` (intern), `delivery_attempts` **ohne** eigene Spalte | ADR-012 §3 | ✅ **kein** Marker in einem Antwortmodell; die Versuche folgen ihrem Lauf |
| **Pflicht / N nullable** | `organisation_id` **immer** Pflicht (4 Modelle), `Event.deleted_at` **entfallen**, `rate_limit` in `required` | `NOT NULL` + `DEFAULT` je Spalte | ADR-002, ADR-012 | ✅ **widerspruchsfrei** |
| **Fehlersemantik** | 403/404/409/413/422/429/503 je Operation | 23505 ⇒ 409 (`delivery_runs_replay_of_uq`) | ADR-015 §3, §5, §6 | ✅ **vollständig**: alle 12 `code`-Werte erzeugbar, 15/15 mit 429, alle 8 per ID adressierten Operationen mit 404, alle 5 mit Request-Body mit 400 |
| **Mandant** | 404 für fremd, **erst der Mandant, dann die Rolle** | RLS fail-closed, **kein** vierter Leseweg (live: 0 Zeilen) | ADR-002 §7, ADR-015 §3 | ✅ **identisch** |
| **Rollenmatrix** | `info.description` § Rollen | — | ADR-015 §1 | ⚠️ **V-49** (Unvollständigkeit, keine Umkehrung) |
| **`organisation_id`-Semantik** | immer gefüllt | — | ADR-015 §1/§2 | ⚠️ **V-48** (ADR-002 §7 schwächer) |
| **Vorgabewerte** | F-01 (60/min, `delay`), F-02 (30 Tage je Datenart, Anker letzter Versuch), F-03 (nur Empfänger-Admin, fremd ⇒ 404), F-04 (8 Versuche, 1 min, ×2, 60 min, ± 20 %) | `endpoint_rate_limits.limit_value NULL`/0, `retention_settings` NULL, `max_attempts` je Lauf | ADR-010, ADR-012, ADR-015 | ✅ **identisch** an allen sechs Stellen |

### 4.2 Abdeckung **je Akzeptanzkriterium** — nachgewiesen

| Nachweis | Ergebnis |
|---|---|
| `x-ac-id` je **Operation** | **57** AKs an **17/17** Operationen (jede Operation trägt mindestens einen) |
| `x-ac-id` am **ausgehenden Zustellvertrag** (`x-delivery-protocol`) | **25** weitere AKs (US-009…US-014) |
| `x-acceptance-coverage` | **101/101**, je Eintrag mit `ac_id`, `story`, `realized_in` — **keine** Dubletten, **keine** Lücke |
| davon **nur** in der Coverage-Tabelle verankert (**19**) | Oberfläche (ADR-018), Datenbank (ADR-002), Betrieb (ADR-013/ADR-006) — die im Vertrag selbst definierte Verankerungsart `non_api` |
| `x-requirement-coverage` | **24/24**, je Eintrag mit `requirement_id`, `stories`, `contract` — **keine** Dubletten, **keine** Lücke gegen `PRD.md` |
| `python3 .agent/tools/traceability_check.py` | ✅ **grün** — „Keine Luecken — die Kette Anforderung → Story → Feature → Test → Code ist vollstaendig.“ |

---

## 5. Developer-Interface — 9 Dimensionen

| # | Dimension | Ergebnis | Begründung / Fundstelle |
|---|---|---|---|
| 1 | **Auffindbarkeit** | 🟢 gut | 17 Operationen, alle mit `operationId`, `summary`, `description`, Tag, `security`, `x-ac-id` **und** `x-requirement-ids`; `x-acceptance-coverage` (101) und `x-requirement-coverage` (24) verweisen jeden Punkt auf seinen Ort; `x-contract-rules` bündelt die vertragsweiten Regeln (`:3427-3453`). *Anmerkung:* die **Rollen** stehen nur im Prosa-Text (`:50-54`), nicht maschinenlesbar — unverändert gegenüber dem ersten Lauf; die Unvollständigkeit der Tabelle ist **V-49**. |
| 2 | **Konsistenz** | 🟢 gut | Die 22 Widersprüche des ersten Laufs und die 11 des Sweeps (V-28…V-38) sind weg. Die beiden verbleibenden Restbefunde betreffen **Formulierungen** (V-48 in ADR-002, V-49 in der Rollentabelle), nicht die Semantik. Beschreibungen ↔ Beispiele: **0 Abweichungen** in 56 Beispielobjekten. |
| 3 | **Vollständigkeit** | 🟢 gut | Kein unerreichbarer Fehlercode (alle 12 `code`-Werte erzeugbar, `tenant_parameter_rejected` mit eigener 422-Komponente), `replay_of` vorhanden, 3xx definiert, das Vorgabefenster (7 Tage) und das Gesamtfenster (≈ 2 h) genannt, die Karenzzeit als nullable ausgewiesen. *Rest:* die optionalen, aber als vorhanden beschriebenen Felder → **V-46**. |
| 4 | **Fehlerbehandlung** | 🟢 strukturiert | Ein Schema (`Problem`), 400/422-Grenze ausgeschrieben (`:82-87`), `violations` je Feld (`:2065-2072`), 409 für alle drei Replay-Fälle (`:1380-1400`), 429 an allen 15 authentifizierten Operationen, 503 mit `Problem` (`:1997-2013`), 400 an allen 5 Operationen mit Body. Die Grenze 403/404 ist **zweimal** ausgeschrieben (`:71-72`, `:89-94`) und in `responses.Forbidden`/`NotFound` **wiederholt** — die V-39-Reihenfolge steht dort, wo der Client sie liest. |
| 5 | **Sicherheit & Mandantentrennung** | 🟢 solide | JWT-only, Mandant ausschließlich aus dem Token, Fremdes ⇒ 404, Secret nie in der Antwort (`writeOnly`), `additionalProperties: false`, RLS **live** fail-closed gemessen, **keine** Rolle mit `BYPASSRLS`, drei schmale Eigentümerfunktionen mit verdrahtetem `search_path`, `REVOKE`/`GRANT` korrekt, Projektionen **ohne** Klartext und **ohne** Secrets (0 Treffer). Offen: die **Härtung** (E-12) — ehrlich in ADR-002 benannt. |
| 6 | **Performanz & Grenzen** | 🟢 brauchbar | Cursor-Paginierung (kein `total`), `limit` ≤ 200, Body-Obergrenze 2 MiB, Nutzlastgrenze 1 MB, Zustellfenster ≈ 2 h, **23** tenant-skalierte Indizes **plus** zwei partielle Cross-Tenant-Indizes (N-17). Die mandantenübergreifenden Listen tragen ihre Sortierung jetzt **indexgestützt**. |
| 7 | **Idempotenz & Wiederholbarkeit** | 🟢 gut | Idempotenz je (Mandant, Schlüssel) mit gezieltem HMAC-Vergleich, 202 mit der **ersten** ID, 409 bei abweichender Nutzlast, at-least-once mit drei Kennungen, Replay als **neuer** Lauf und **einmal** (23505 ⇒ 409, live belegt). Die Sperrenwartezeit des Verlierers ist in ADR-015 §6 benannt (N-20) und **nicht** im Vertrag — bewusst (Implementierungsdetail). |
| 8 | **Testbarkeit** | 🟢 gut | Die AKs sind entscheidbar; Zustandsautomat, `ClockPort`, `IdGenerator` und die Ports machen Backoff, Signatur und Replay unit-testbar; RLS, Cross-Tenant-Funktionen, Replay-Constraint und Aufbewahrung sind **live gegen PostgreSQL 16.15** belegt (§ 1.6). Offen (Schritt 5, **kein** Vertragsbefund): 32 AKs ohne Gherkin-Szenario, Lasttest (NFR-002), Parallelbetriebstest (AC-023-3/4) und die **zwei** Zweige aus **V-45**. |
| 9 | **Verständlichkeit & Dokumentation** | 🟢 gut | Eine Erzählung, ein Glossar, ADRs mit Begründung und Alternativen, Beispiele je Operation, Abschnitte „Pagination“, „Suchbarkeit“, „Vorgabewerte“ und „Fehlersemantik“ in `info.description`. Die Beispiele widersprechen sich **nicht** (56 Objektbeispiele, 0 Abweichungen). Zwei Textlücken bleiben: **V-48** (ADR-002) und **V-49** (Rollentabelle). |

---

## 6. Vertragsdefekte

**Der Vertrag wurde nicht verändert.** Grund: ein Eigentümer, eine Wahrheit
(ADR-016); er wird **nach** diesem Schritt eingefroren. Jeder Defekt hier ist
**Dokumentation**, kein Fix.

Die **44** entschiedenen Vertrags-/ADR-Befunde sind behoben (§ 1). Neu
gefunden:

| ID | Pfad:Zeile | erwartet | tatsächlich | Auswirkung auf Story/AK/Anforderung | konkrete Korrektur | Priorität |
|---|---|---|---|---|---|---|
| **V-49** | `docs/api/openapi.yaml:50-54` (Rollentabelle) vs. `docs/adr/ADR-015-fehlersemantik-tenant-rollen.md:67-68` und `docs/requirements/PRD.md:74` | **eine** widerspruchsfreie Aussage, wer welche Operation darf | ADR-015 §1 gewährt dem `service_admin` zwei Rechte (Endpunkt anlegen/ändern/pausieren; Ereignis einliefern), die Vertrag und PRD **nicht** nennen; die Matrixzelle „Endpunkte verwalten: **ja** (alle Mandanten — **nur in der Liste** …)“ ist in der Form mehrdeutig | **kein** AK und **kein** FR betroffen (FR-004 nennt nur „**sehen**“, FR-010 nur den Entwickler, FR-031 nur die Totenliste); die Testmatrix übt die strittigen Rechte nicht aus. Ein Implementer nach dem Vertrag antwortet dem `service_admin` mit **403**, ein Implementer nach ADR-015 mit **201/202** | **Empfehlung (a):** ADR-015 §1 auf PRD + Vertrag **verschärfen** — der Service-Admin verwaltet Endpunkte **nur im eigenen Mandanten** und liefert **keine** Ereignisse ein; alternativ (b) die Rollentabelle des Vertrags ergänzen. `docs/adr/` ist in diesem Schritt nicht änderbar ⇒ Rücksprung zu Schritt 2 | **sollte** |
| **V-48** | `docs/adr/ADR-002-mandantentrennung-rls.md:82` | V-26: `organisation_id` ist **immer** gefüllt und benennt den **besitzenden Mandanten** | „In den mandantenübergreifenden Antworten ist `organisation_id` **im Datensatz** gefüllt“ — die schwächere V-25-Fassung | **kein** AK/FR betroffen; der Vertrag sagt es an 12 Stellen richtig. Ein Leser von ADR-002 §7 allein kann „für andere Rollen `null`“ ableiten — die Lesart, die **V-44** in ADR-015 §2 korrigieren musste | Den Bullet auf die V-26-Fassung bringen (Text siehe § 2.6). Rücksprung zu Schritt 2 | **sollte** |
| **V-46** *(verschärft)* | `docs/api/openapi.yaml:2853-2858`, `:2859-2881`, `:3267-3270`, `:2568-2572`, `:2523-2525`, `:2469-2481` | Die Beschreibung sagt „immer vorhanden (`null` = kein Wert)“ — das **Schema** (nullable, **nicht** in `required`) sagt: das Feld **darf fehlen** | **kein** AK betroffen (AC-017-3 verlangt `data_available: false`, nicht die Anwesenheit der Zähler). Ein streng schema-getriebener Client muss „fehlend“ und „`null`“ **beide** behandeln | Dieselbe Musterentscheidung wie V-42: alle Felder in `required` aufnehmen, `null` über `nullable` ausdrücken — **neue Zusage**, nach dem Freeze ein Rücksprung | **sollte** |
| **V-47** *(verschärft)* | `docs/api/openapi.yaml:698-711`, `:750-759`, `:803-809`, `:840-848` | Die Fremdmandanten-404 steht **an jeder** per ID adressierten Operation (wie bei den vier Lesoperationen) | Die vier Operationen **deklarieren** die 404, **nennen** die Regel aber nicht | **kein** AK betroffen (AC-002-3 deckt den Fall inhaltlich ab); die vertragsweite Regel trägt sie | Einen Satz je Operation ergänzen — reine Textergänzung ohne neue Zusage | **sollte** |
| **V-45** *(verschärft)* | `docs/requirements/stories/US-013.json` (AC-013-3) und `docs/requirements/features/US-013.feature:16-19` | Der „given“ nennt den Mandanten (wie AC-003-3: „ein Entwickler **desselben Mandanten**“) | Der „given“ sagt **nichts** über den Mandanten | Betrifft **US-013** (AC-013-3) und dieselbe Musterlage in **AC-004-1** (der „given“ nennt die Rolle nicht; aufgelöst durch das Paar AC-004-1/AC-004-2 und den Vertragstext) | **Keine Änderung an der Anforderung.** Die Testmatrix (Schritt 5) führt **zwei** Fälle: eigener Mandant + falsche Rolle ⇒ 403 · fremder Mandant ⇒ 404 | **sollte** |

**Kein** Befund dieses Abschnitts ist ein „**muss**“; keiner ist ein Widerspruch
gegen eine bestätigte Entscheidung; keiner invalidiert Schema, Code oder Tests.

---

## 7. Offene Entscheidungen — mit Empfehlung

Der Skill verlangt, an dieser Stelle zu **fragen** statt zu entscheiden. Als
Subagent frage ich nicht nach — ich lege die Entscheidung samt Empfehlung vor.
**Keine** dieser Entscheidungen blockiert den **Freeze** des Vertrags.

| ID | Frage | Optionen | **Empfehlung** | Warum | Gate |
|---|---|---|---|---|---|
| **V-48** | Soll ADR-002 §7 an V-26 angeglichen werden? | (a) ja — den Bullet auf „`organisation_id` ist **immer** gefüllt“ umstellen; (b) nein | **(a)** | Der Satz ist die **einzige** verbliebene Stelle mit der überholten V-25-Fassung; die Korrektur ist ein Satz und schließt genau die Lesart aus, die V-44 in ADR-015 §2 korrigieren musste. Kein AK/FR betroffen | Gate 1 → Rücksprung zu Schritt 2 |
| **V-49** | Darf der `service_admin` Endpunkte verwalten und Ereignisse einliefern? | (a) **nein** — ADR-015 §1 auf PRD + Vertrag verschärfen (Service-Admin verwaltet Endpunkte **nur im eigenen Mandanten**, liefert **keine** Ereignisse ein); (b) ja — die Rollentabelle des Vertrags um beide Rechte ergänzen | **(a)** | Die Anforderung verlangt sie nicht (PRD: „Endpunkte aller Mandanten **sehen**“). Ein Recht des Plattformbetriebs auf die Endpunktverwaltung **aller** Mandanten ist eine Rechteausweitung, die niemand beantragt hat. (b) würde zusätzlich eine **neue** Zusage in den Vertrag bringen (nach dem Freeze ⇒ Rücksprung) | Gate 1 → Rücksprung zu Schritt 2 |
| **V-46** | Sind die optionalen, aber als vorhanden beschriebenen Antwortfelder Pflicht? | (a) alle in `required` (`null` bleibt über `nullable`); (b) die Beschreibungen auf „kann fehlen“ umstellen; (c) so belassen | **(a)** | Dieselbe Musterentscheidung wie V-42; ein Client, der das Feld „immer erwartet“, ist sonst auf eine Absenzentscheidung angewiesen. **Wenn (a), dann vor Schritt 6** — nach dem Freeze ein Rücksprung | Gate 1 (optional) → sonst Schritt 5/6 |
| **V-47** | Steht die Fremdmandanten-404 auch an den vier endpunktändernden Operationen? | (a) ja — Text ergänzen; (b) bewusst nur global | **(a)** | Die Regel soll an der Stelle stehen, an der der Client sie liest; die vier Lesoperationen machen es vor. Reine Textergänzung ohne neue Zusage | Schritt 5 (Text) |
| **V-45** | Wie wird AC-013-3 in der Testmatrix abgebildet? | (a) **zwei** Fälle (eigener Mandant + falsche Rolle ⇒ 403; fremder Mandant ⇒ 404); (b) AC-013-3 in Schritt 1 um den Mandantbezug ergänzen | **(a)** | Die Anforderung wird **nicht** geändert; V-39 ist bereits im Vertrag und in ADR-015 §3 gefasst — die Testmatrix muss sie nur **abbilden** | **Schritt 5** |
| **E-12** | Soll die mandantenübergreifende Sicht **härter** abgesichert werden (eigene Laufzeitrolle `echobox_ops`)? | (a) nein — die Grenze bleibt in ADR-002 ehrlich benannt; (b) ja — eigene Rolle für die drei Funktionen | **(a) jetzt, (b) als Risiko vormerken** | (b) ist die konsequenteste Härtung, verlangt aber eine **getrennte** Verbindungsrolle im Betrieb (ADR-013: zwei Rollen, ein Image) — eine echte Architekturentscheidung. Die Grenze ist bereits benannt | **Gate 2** (Fachbereich + Plattform) |
| **E-13 / R-01** | Bekommt die **Karenzzeit** der Aufbewahrung einen Vorgabewert? | (a) **0 Tage** (Purge im nächsten Durchlauf) als Betriebsdefault; (b) ein fachlicher Wert (z. B. 7 oder 30 Tage); (c) **kein** Wert — der Betreiber muss ihn setzen | **(a) als Default, mit (c) als Vertragslage** | Der Vertrag behauptet **keinen** Wert (`purge_delay_days: null`, `:3009-3022`); das Schema rechnet bereits mit `COALESCE(p_purge_delay_days, 0)` ⇒ Purge im nächsten Durchlauf. (a) macht das **explizit** statt zufällig; (b) ist eine **fachliche** Zusage | **Gate 2** (Fachbereich) |

**Weiterhin offen (unverändert, nicht von diesem Schritt zu entscheiden):**
**O-04** (Verschlüsselung im Ruhezustand — fachliche Pflichtfrage),
**O-05** (AVV bei Fremddaten) — beide Fachbereich/Recht, Gate 2.

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
| **Vertragsform** | OpenAPI 3.0.3, YAML gültig, **17** Operationen, alle mit eindeutigem `operationId`, `summary`, `description`, `security`, `x-ac-id` **und** `x-requirement-ids` |
| **Erreichbarkeit der Fehlercodes** | Alle **12** Codes der Fehlersemantik (`:67-80`) sind erzeugbar; `tenant_parameter_rejected` über die 422-`oneOf` (`:1938-1940`); 429 an **15/15** authentifizierten Operationen; 503 über die Komponente (`:1704-1705`) — **kein** toter Bestandteil |
| **`$ref` und ungenutzte Objekte** | **177** Verweise, **alle** auflösbar; **kein** ungenutztes Schema (37), **keine** ungenutzte Antwort-Komponente (11), **kein** ungenutzter Parameter (7) oder Header (1) |
| **Beispiele** | **56** Beispielobjekte maschinell gegen ihr Schema geprüft (Pflichtfelder, `null` bei nicht-`nullable`, unbekannte Felder, Enum, Längen, Bereiche) — **0 Abweichung** |
| **Beispiele nachgerechnet** | Nutzlastbytes (35), Erfolgsquote (100/120 = 0,8333), Backoff (1 min → 2 min), Gesamtfenster (123 min ± 20 %), Base64-Länge des Cursors (132 ≤ 512), Secret-Längen (20 / 26 ≥ 16), UUID-Form, Fristen/Stichtage, Zählersummen je Mandant (128 + 12 = 140) — **0 Abweichung** |
| **Cursor** | dekodiert zu `{"created_at": …, "delivery_run_id": …}` — Sortierschlüssel **+** UUID-Tie-Breaker, gültiges Base64; die Totenliste sortiert `created_at`, die Endpunktliste `updated_at` — der Vertrag, die Funktionen und die Indizes sagen dasselbe |
| **Zustandswerte ↔ Schema** | `EndpointStatus`, `EventStatus`, `DeliveryStatus`, `DeadDeliveryStatus`, `AttemptOutcome`, `RejectionReason` stimmen **exakt** mit den CHECK-Constraints überein; die Totenliste ist im Schema **fest verdrahtet** auf `abandoned` |
| **Größengrenze** | 1.048.576 Byte steht **gleich** in NFR-001, Vertrag (Regel + Beschreibung), DB-CHECK, Fehlercode 413 und der Fehlersemantik — inkl. Chiffre-Reserve (1.048.592) |
| **Idempotenz** | (Mandant, Schlüssel) ↔ `UNIQUE (organisation_id, idempotency_key)` ↔ gezielter HMAC-Fingerprint ↔ 202/409 — in Vertrag, Schema, ADR-007, Glossar und Domain-Modell **identisch** |
| **„Kein Abnehmer“** | Ereignis-Status, Lauf-Status, `endpoint_id IS NULL`, DB-CHECK, Glossar, Zustandsautomat, Vertrag — **konsistent** |
| **RLS** | fail-closed (live: 0 Zeilen ohne Kontext), **keine** Rolle mit `BYPASSRLS`, `FORCE ROW LEVEL SECURITY` bewusst nicht gesetzt, `SECURITY DEFINER` nur für die schmalen Eigentümerfunktionen, `search_path` verdrahtet, `REVOKE`/`GRANT` korrekt, Worker **nicht** freigegeben, **kein** mandantenübergreifender Einzelabruf (live: 0 Zeilen) |
| **Projektionen** | **0** Treffer auf Klartextwerte und Secrets in allen drei mandantenübergreifenden Projektionen |
| **Replay-Kette** | `replay_of` (Vorgänger, ohne Fremdschlüssel) → neuer Lauf mit **neuer** ID, **gleicher** Ereignis-ID, zurückgesetzter Versuchszahl, `attempt_number` beginnt bei 1; `UNIQUE (replay_of)` erzwungen (23505 ⇒ 409); die Historie **überlebt** den Purge des Vorgängers — in Vertrag, Schema, ADR-006 §6, ADR-015 §6, Domain-Modell und Sequenzdiagramm **identisch** |
| **3xx-Kette** | kein Wiederholen, `rejected`, `rejection_reason = client_error`, `outcome = client_error`, Redirects werden **nicht** befolgt — an **fünf** Stellen gleich |
| **Rate-Limit-Zustände** | `kein_override` (⇒ 60) und `abgeschaltet` (⇒ 0) — an **sechs** Stellen gleich; der entfernte `enabled`-Zustand kommt im Repository **nicht mehr** vor |
| **403 auf den Listen** | Die deklarierte 403 auf `listEndpoints`/`listDeliveries`/`getDeliveryRun`/`getEndpointMetrics` ist **defensiv**: die Rollenmatrix gewährt allen Rollen den lesenden Zugriff (mandantengefiltert); auslösbar ist sie für ein Token **ohne** bekannte Rolle. Kein Mangel |
| **Reduktion aus Schritt 3** | Die drei Eigentümerfunktionen sind **nicht** zusammengelegt (die Projektionsgrenze wäre verloren gegangen), `GET /api/v1/events` ist weiterhin nicht da, keine Story/AK/Anforderung entfallen, 101/101 und 24/24 Abdeckung |
| **Ein Ort pro Wahrheit** | `.agent/knowledge/openapi.yaml` ist ein **reiner Verweis**; das Schema-Spiegel ist **byte-identisch** (`diff` leer); `docs/adr/README.md` deckt alle 18 Dateien ab |
| **Traceability** | `python3 .agent/tools/traceability_check.py` → **grün** (23 Stories / 101 AKs / 24 Anforderungen) |
| **p95 < 200 ms (NFR-002)** | Der Annahmepfad ist eine begrenzte Folge von Statements, die Nutzlast ist auf 1 MB gedeckelt, die Zustellung läuft im Hintergrundprozessor — der Zielwert ist **plausibel**; die Messung selbst fehlt (Schritt 5) |
| **1xx** | Die Ergebnismatrix des ausgehenden Vertrags nennt **kein** 1xx. Kein Mangel: ein HTTP-Client liefert 1xx nie als Endstatus; 101 (Upgrade) ist für Webhooks nicht vorgesehen |

---

## 9. Selbstprüfung

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | ✅ **grün** — „Keine Luecken …“ (23 Stories / 101 AKs / 24 Anforderungen) |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | ✅ **leer** (byte-identisch) |
| **Vertrag unverändert** | `git diff --stat -- docs/api/openapi.yaml` | ✅ **leer** |
| **ADRs unverändert** | `git diff --stat -- docs/adr/` | ✅ **leer** |
| **`.agent/knowledge/` unverändert** | `git diff --stat -- .agent/knowledge/` | ✅ **leer** |
| **Anforderungen unverändert** | `git diff --stat -- docs/requirements/ docs/Anforderung.md` | ✅ **leer** |
| **Kein Anwendungscode** | `git diff --stat -- src/` | ✅ **leer** (`src/` existiert nicht) |
| YAML gültig | `python3 -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml'))"` | ✅ **gültig** |
| Vertragsform | Skript | ✅ 17 Operationen; 15/15 authentifizierte Operationen mit 429; **alle** 8 per ID adressierten Operationen mit 404; alle 5 mit Request-Body mit 400; **alle** `$ref` auflösbar; **kein** ungenutztes Schema/Response/Parameter/Header |
| Abdeckung | Skript | ✅ `x-acceptance-coverage` **101/101**, `x-requirement-coverage` **24/24**, keine Dubletten; **82** AKs zusätzlich maschinenlesbar verankert (`x-ac-id`), **19** als `non_api` |
| Beispiele | Skript (Pflichtfelder, Enum, unbekannte Felder, Längen, Bereiche, `null`-Regeln) | ✅ **0 Abweichungen** (56 Beispielobjekte) |
| Schema ↔ Vertrag | Skript | ✅ alle Entitätsfelder 1:1; alle Statuswerte **identisch** mit den CHECK-Constraints; **kein** `deleted_at` in einem Antwortmodell |
| **Schema anwendbar** | `psql -f .agent/knowledge/schema.sql` (PostgreSQL **16.15**, frische DB) | ✅ **ohne Fehler** |
| **RLS-Verhalten (erneut)** | § 1.6 | ✅ **belegt** — fail-closed (0 Zeilen ohne Kontext), Mandantentrennung, Cross-Tenant nur über die drei Funktionen, kein Einzelabruf, keine `BYPASSRLS`, Rechte korrekt |
| **Projektionen ohne Klartext/Secrets** | § 1.6 | ✅ **0 Treffer** |
| **N-13 / N-14 / N-15 / N-16** | § 1.6 | ✅ **belegt** (markierte Versuche unsichtbar; Totenliste nur `abandoned`; `replay_of` überlebt den Purge; logische Löschung durch die Applikationsrolle unmöglich) |
| **UNIQUE-Regel** | § 1.6 | ✅ **belegt** (zweiter Replay ⇒ SQLSTATE 23505 ⇒ 409) |
| Markdown | Struktur der geänderten Dateien | ✅ **ok** |
| Commit-Scope | `git add -A -- docs/architecture/ .agent/artifacts/` | ✅ **nur** diese beiden Pfade |

---

**Fazit für den Orchestrator:** Der Vertrag ist **einfrierreif**. 48 von 48
entschiedenen Nachprüfungen sind am aktuellen Stand belegt (44 Vertrags-/ADR-
Befunde, der Architekturbruch S-01 **live gegen PostgreSQL 16.15**, die drei
Standards-Befunde N-01/N-02/N-08). Die Delta-Runde findet **zwei** neue
Befunde (V-48, V-49) — beide **„sollte“**, beide **Dokumentationslücken**, keiner
ein Widerspruch gegen eine bestätigte Entscheidung. **V-45**, **V-46** und
**V-47** bleiben offen und gehen als **Testmatrix-Aufgabe** (Schritt 5) und als
**Sicherheitsaufgabe** (Schritt 8) in die Pipeline. **Kein** offener
„muss“-Befund gegen Vertrag, Schema oder ADRs.
**Freeze ab dem Commit dieses Schritts** — danach ist jede Vertragsänderung ein
Rücksprung zu Schritt 2.
