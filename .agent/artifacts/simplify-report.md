# Reduktionsbericht — Schritt 3 (Simplify & Reduktion)

> Schritt 3 · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> **Eingang:** Schritt 2 **nach dem Rücksprung aus Schritt 4** (Commit `9bb9bf4`),
> Schritt 1 nachgezogen in `cc67445`, Stresstest in `bdb03d3`.
> Skill: `aisdlc-simplify` (Schritt 3 der AI-SDLC).
> **Vertrag:** `docs/api/openapi.yaml` — 17 Operationen, 23 Stories / 101 AKs /
> 24 Anforderungen — **Entwurf, noch nicht eingefroren** (Freeze nach Schritt 4).
> **Schema:** unverändert — `.agent/knowledge/schema.sql` und
> `docs/architecture/schema.sql` sind **byte-identisch** (Spiegel-Regel, ADR-016).

Dieser Bericht **ersetzt** den Bericht aus dem ersten Schritt 3 (vor dem
Rücksprung). Grund: er beschrieb den Stand von `7422cae`, nicht den von
`9bb9bf4`.

---

## 0. Kurzfassung

| Kennzahl | vorher | nachher | Bemerkung |
|---|---|---|---|
| Vertragsoperationen | 17 | **17** | keine neue, keine entfernte |
| Stories / AKs / Anforderungen | 23 / 101 / 24 | **23 / 101 / 24** | **unverändert** — Abdeckung intakt, maschinell geprüft |
| ADRs mit eigener Entscheidung | 17 | **17** | keine Entscheidung neu aufgerollt; **ADR-002…ADR-018 unverändert** |
| Schema (Tabellen / Policies / Indizes / Eigentümerfunktionen) | 10 / 10 / 33 / 3 | **10 / 10 / 33 / 3** | in diesem Schritt **nicht angefasst** |
| Offene Punkte | O-04, O-05 | **O-04, O-05, R-01** | R-01 ist **neu** (Karenzzeit, Gate 2) |
| Doppelte Artefakte mit belegter Abweichung | 2 | **0** | Kurzfassung `folder-structure.md`, `adr-index.md` |
| Vertragszeilen | 3.194 | 3.304 | **+110** — davon ~80 **neue** Regeln (R-03/R-04/R-05), ~−8 entfernte Doppelungen; Details in §3 |

**Nichts von der Abdeckung ist entfallen.** Jede Story, jedes
Akzeptanzkriterium und jede Anforderung ist weiterhin im Vertrag auffindbar —
maschinell geprüft (§7).

---

## 1. Was überdimensioniert war

Der Deletion-Test des Skills: *Weglassen, was keine Story, kein NFR und keine
ADR-Bindung hat.* Der Rücksprung hat **470 Zeilen Schema** und **664 Zeilen
Vertrag** hinzugefügt. Ergebnis der Prüfung — **sechs** Fundstellen, alle im
Dokumentations- und Vertragsbereich, **keine** im Datenmodell:

| # | Fundstelle | Diagnose | Beleg |
|---|---|---|---|
| 1 | `x-contract-rules.defaults` (Vertrag) | wiederholte die **Werte** F-01…F-04, die dieselbe Datei in der Tabelle „Vorgabewerte“ schon nennt — zwei Stellen für eine Zahl | die Tabelle steht in `info.description`; die Vorgabewerte stehen zusätzlich an der jeweiligen Operation und im ausgehenden Vertrag |
| 2 | `x-contract-rules.errors` + `.pagination` | wiederholten die **400/422-Grenze** bzw. die Paginierungsregel, die der Fließtext (`info.description`) bereits festschreibt | die Grenze stand in **drei** Blöcken desselben Vertrags (Tabelle, Absatz, Regelblock) |
| 3 | Vertrag, drei Operationsbeschreibungen | nannten die **SQL-Funktionsnamen** (`app.list_endpoints_cross_tenant` …) — Implementierungsdetail in einem API-Vertrag, an drei Stellen | die Namen stehen in ADR-002 §7; der Vertrag braucht nur die **client-sichtbare** Tatsache (`organisation_id` im Datensatz) |
| 4 | `.agent/artifacts/folder-structure.md` | hielt eine **zweite Dateiliste** und eine **zweite Konventionstabelle** — **beide nach dem Rücksprung veraltet**: `CrossTenantScope` fehlte (ADR-002 §7), die Konvention „Mandant ist transaktionsgebunden“ fehlte (N-01) | Volltext `docs/architecture/folder-structure.md` §2.6, §4 |
| 5 | `.agent/artifacts/adr-index.md` | hielt einen **zweiten ADR-Index** — **nach dem Rücksprung veraltet**: ADR-002 §7 fehlte in der Zeile | Index in `docs/adr/README.md` (dort war ADR-002 §7 eingetragen) |
| 6 | `.agent/artifacts/api-review.md` §6 | eine **überholte Prüfliste** (8 Punkte) für einen Stresstest, der **gelaufen** ist | der Bericht ist auf 3 Zeilen zusammengeführt; die Befunde V-01…V-22 sind einzeln belegt |

Zwei weitere Verdachtsstellen wurden geprüft und **nicht** angetastet:

| Verdacht | Prüfung | Ergebnis |
|---|---|---|
| Die **drei** `SECURITY DEFINER`-Eigentümerfunktionen zusammenlegen | E-01 verlangt **drei schmale** Funktionen mit je eigener Projektion und je eigener Schreibberechtigung | **nein** — eine gemeinsame Funktion mit Modus-Parameter müsste alle drei Projektionen in einem Aufruf erreichbar machen (die Projektionsgrenze ginge verloren) und die Schreibberechtigung der Aufbewahrung in den Lesepfad hineinreichen. **Die Eigentümerschaft je Abfrage geht genau dadurch verloren.** Sie bleiben drei (ADR-002 §7) |
| Die Guard-Zeile (`fail-closed`) und die Limit-Klammer in den Listenfunktionen sind zweimal gleich | echte Wiederholung von je 3 Zeilen | **bewusst behalten**: eine gemeinsame Hilfsfunktion wäre ein **viertes** aufrufbares Objekt, das selbst gesichert werden müsste (`REVOKE … FROM PUBLIC`) — mehr Angriffsfläche für weniger Code. Das Schema ist in diesem Schritt ohnehin **außerhalb** des Commit-Scopes (§8) |

**Ein Namenskonflikt wurde gefunden und aufgelöst:** „R-01…R-05“ ist zweimal
belegt — als **Risiken** (Anforderungserhebung, Schritt 1) und als **Befunde**
(Rücksprung, Schritt 2). Umbenannt wurde **nichts**: die Risiko-IDs stehen in
den ADRs und in `docs/requirements/` (schreibgeschützt), die Befund-IDs in
Briefing, Rücksprung-Bilanz und Auftrag. Aufgelöst durch **Kennzeichnung**:
beide Tabellen im Katalog weisen jetzt aus, welcher ID-Raum gemeint ist.

---

## 2. Was **behoben** wurde — die drei mechanischen Restbefunde

Der Rücksprung hat R-03, R-04 und R-05 bewusst offen gelassen, weil sie
rein mechanisch sind. Sie sind hier behandelt — **ohne eine neue Festlegung**.

### 2.1 R-03 — `abandoned_at` vs. Statusfilter der Totenliste

| | |
|---|---|
| **Befund** | `DeadDelivery.abandoned_at` war nicht nullable und Pflicht, der Statusfilter der Totenliste ließ aber `rejected` zu — ein `rejected`-Lauf hat **kein** `abandoned_at` (DB-Check `delivery_runs_abandoned_at_ck` erzwingt es nur für `abandoned`) |
| **Entscheidung der Wahrheit** | Der **Glossar** ist der einzige Ort je Fachbegriff (ADR-016): „Tote Zustellung = **ein Zustelllauf mit Status `abandoned`, gesetztem `abandoned_at`** — **nicht** ein abgelehnter Lauf (`rejected`) und kein pausiert abgebrochener Lauf (`cancelled`)“. Das Domain-Modell §3.1 sagt dasselbe (`abandoned` = „tote Zustellung“, FR-023); die PRD-Glossarzeile ebenso |
| **Behandlung** | Die **Filter-Aussage** wurde korrigiert, nicht das Feld: der Statusfilter der Totenliste lässt **nur `abandoned`** zu (neues Schema `DeadDeliveryStatus`), und `DeadDelivery.status` ist auf denselben Wert eingeengt. `abandoned_at` bleibt **Pflicht und nicht nullable** — jetzt widerspruchsfrei, weil die Liste keine Zustände mehr enthält, für die der Wert fehlen könnte |
| **Warum nicht der andere Weg** | `abandoned_at` nullable zu machen hätte die Liste um `rejected`/`cancelled` erweitert — im Widerspruch zum Glossar und zu AC-016-1 („dem Zeitpunkt der Aufgabe“). Der Filter wäre dann nicht mehr filterbar gewesen (nur ein möglicher Wert) und AC-016-4 („mit Endpunkt, **Status** und Zeitraum gefiltert“) wäre gegenstandslos |
| **Folge für das Schema** | keine — die Eigentümerfunktion filtert weiterhin über `p_status`; die Anwendung setzt den Wert **`abandoned` immer** (die Funktion liefert ohne Filter **alle** Zustände). **Auflage für Schritt 6**, im Bericht vermerkt |

### 2.2 R-04 — Cursor-Semantik der mandantenübergreifenden Funktionen

| | |
|---|---|
| **Befund** | Die Funktionen erwarten zwei Skalare (`after_updated_at`, `after_endpoint_id`), der **opake, HMAC-signierte** Cursor des Vertrags ist eine Anwendungssache — im Vertrag stand **nichts** darüber, worüber geblättert wird |
| **Behandlung** | Ein **Abschnitt „Pagination“** in `info.description` — der einzige Ort für die Cursor-Regeln: Cursor **über die Mandantgrenzen hinweg** (eine globale Reihenfolge, **keine** Seite je Mandant), Sortierung **absteigend** nach Zeitstempel mit der **UUID als Tie-Breaker** (dadurch **total** und stabil), **Keyset** statt Offset (keine Dubletten, keine Übersprünge), Gültigkeit nur für dieselbe Operation mit denselben Filtern. `enforceRetention` ist **nicht** paginiert (ein Eintrag je Mandant in `tenants[]`, ohne `page`) — auch das steht jetzt dort. Der Parameter `Cursor` **verweist** auf den Abschnitt |
| **Beleg** | Schema `app.list_endpoints_cross_tenant` (Sortierung `updated_at DESC, endpoint_id DESC`) und `app.list_dead_deliveries_cross_tenant` (`created_at DESC, delivery_run_id DESC`); Keyset-Vergleich `(a, b) < (after_a, after_b)`; `common/CursorCodec` (HMAC-signiert, ADR-017) |

### 2.3 R-05 — „eigene Frist“ vs. „Vorgabe“

| | |
|---|---|
| **Befund** | `RetentionEnforcementResponse.tenants[]` nannte je Mandant nur die **Zählerstände** und die Fristen — nicht, ob der Mandant eine **eigene** Frist hat oder die **Vorgabe** nutzt. Die alte Beschreibung („`null` = kein Override; es gilt die Vorgabe“) war zudem **falsch**: die Funktion liefert den **angewandten** Wert (`COALESCE(Override, Vorgabe)`), also **nie** `null` für eine wirksame Frist |
| **Behandlung** | Zwei Felder je Mandant: `event_retention_source` und `delivery_retention_source` (`tenant` = eigene Frist, `default` = Vorgabe, `null` = keine Frist wirksam). Die Frist-Felder beschreiben jetzt den **angewandten** Wert; `null` bedeutet dort **„keine Frist wirksam“**. Das Beispiel zeigt **zwei** Mandanten — einen mit Vorgabe, einen mit eigener Frist — und die Top-Level-Frist ist die **kürzeste** (14 Tage), wie in der Beschreibung steht |

---

## 3. R-01 — die Karenzzeit: **kein** Wert, **kein** neuer offener Punkt

Die Karenzzeit (Abstand „markiert“ → „gelöscht“) hat **keinen beschlossenen
Wert**; der Vertrag nannte im Beispiel **7 Tage** — eine Zahl, die nirgends
beschlossen ist.

| | |
|---|---|
| **Behandlung** | Der Vertrag **behauptet keinen Wert**: `purge_delay_days` ist **nullable**, die Beschreibung sagt ausdrücklich „**Ein Vorgabewert ist nicht festgelegt**“, und das Beispiel trägt `purge_delay_days: null` statt `7`. Das Muster ist das der Konfigurationswerte vor Gate 1: **konfigurierbar; ohne Wert kein vorbelegter Grenzwert** (D-12, D-14) |
| **Was **nicht** geändert wurde** | **F-02** bleibt unverändert: 30 Tage je Datenart, Fristbeginn je Datensatz = letzter Versuch, Kaskade (Ereignis nimmt Läufe und Versuche mit), zwei Stufen markieren/Purge. Die **Fristen** sind entschieden — nur die **Karenzzeit** zwischen den Stufen nicht |
| **Kein neuer offener Punkt** | R-01 war bereits als offener Punkt erfasst (Rücksprung-Bilanz §5) — er wird **fortgeschrieben**, nicht neu eröffnet: Owner **Fachbereich**, Gate **2** |
| **Wo R-01 jetzt sichtbar ist** | `.agent/artifacts/briefing.md` · `.agent/artifacts/architecture.md` §8 · `docs/architecture/architecture.md` §7 · `docs/adr/README.md` („Offene Punkte“) · `docs/adr/ADR-012-aufbewahrung.md` §6 · `.agent/artifacts/api-review.md` §7 · `.agent/artifacts/adr-index.md` · `.agent/artifacts/simplify-report.md` (hier) |
| **Hinweis an den Fachbereich (ohne Festlegung)** | Das **Schema** behandelt „kein Wert konfiguriert“ als **0 Tage** (`COALESCE(p_purge_delay_days, 0)`); der Purge erfolgt dann im **nächsten** Durchlauf. Ob die Karenzzeit einen **Vorgabewert** bekommen soll (und welchen), ist die offene Frage R-01 — sie ist eine **Betriebsregel**, keine Vertragsänderung |

**Warum nicht in der PRD:** `docs/requirements/` gehört Schritt 1 und ist in
diesem Schritt **schreibgeschützt**. R-01 ist dort deshalb **nicht** eingetragen;
er steht in den Architekturartefakten und im Briefing. Das PRD wird R-01
**nicht** widersprechen — es enthält zur Karenzzeit keine Zahl.

---

## 4. Was entfernt oder zusammengefasst wurde

| Was | Vorher | Nachher | Warum |
|---|---|---|---|
| **Vertrag** — `x-contract-rules.defaults` | 4 Zeilen mit den **Werten** F-01…F-04 | 5 Zeilen mit den **Regeln** (zwei Zustände, Anker, Stufen, Kaskade) + **Verweis** auf die Wertetabelle | ein Wert steht jetzt an **einer** Stelle in der Datei; die Drift-Gefahr zwischen Tabelle und Regelblock entfällt |
| **Vertrag** — `x-contract-rules.errors`, `.pagination` | wiederholte die 400/422-Grenze bzw. die Paginierungsregel | **Verweis** auf `info.description` | dieselbe Grenze stand in drei Blöcken desselben Vertrags |
| **Vertrag** — Operationsbeschreibungen | nannten drei SQL-Funktionsnamen | verweisen auf **ADR-002 §7** | Implementierungsdetail im API-Vertrag; die Namen stehen in der ADR |
| **Kurzfassung `folder-structure.md`** | 27 Zeilen Dateiliste + 8 Zeilen Konventionstabelle (beide **veraltet**) | 10 Zeilen **Modul**-Übersicht + Verweis | die Dateiliste war eine zweite Wahrheit — und **driftete bereits** |
| **`adr-index.md`** | 18 Zeilen zweiter ADR-Index (**veraltet**) + F-Tabelle + offene Punkte | **Gate-Vorlage** (bestätigt / offen) + Verweis auf `docs/adr/README.md` | der Index existiert bereits dort —ADR-016 verlangt **eine** Stelle |
| **`api-review.md` §6** | 8 überholte Prüfpunkte | 3 Zeilen: was offen ist | der Stresstest ist gelaufen |
| **`architecture.md` (Katalog) §7** | 6 Zeilen Story→Operation-Tabelle (Dublette zu `docs/architecture/architecture.md` §6) | 6 Zeilen **Epic → Kurz** + Verweis | die Zuordnung steht einmal |
| **Katalog + `architecture.md` §4** | sehr lange Tabellenzellen zur mandantenübergreifenden Sicht (Funktionsnamen, Freigabe-Variablen) | eine Zeile + Verweis auf ADR-002 §7 | der Mechanismus steht in der ADR |
| **`ruecksprung-bilanz.md`** | ohne Kennzeichnung | als **Vorgangsprotokoll** gekennzeichnet (kein lebender Wahrheitsort) + Stand der R-01…R-05 | ein Protokoll, das als Wahrheit gelesen wird, ist eine Falle |

**Nicht entfernt** (bewusst): keine Operation, kein Schema-Objekt, kein
Akzeptanzkriterium, keine Anforderung, keine ADR.

---

## 5. Was geblieben ist — und warum

| Element | Bleibt | Begründung |
|---|---|---|
| 17 ADRs (ADR-001…ADR-013, ADR-015…ADR-018) | ja | 17 **unabhängige** Entscheidungen; eine weitere Zusammenfassung (z. B. ADR-003 + ADR-017) würde fachfremde Themen in eine ADR ziehen |
| **drei** `SECURITY DEFINER`-Eigentümerfunktionen | ja | **je eine** Projektion, **je eine** Berechtigung; eine gemeinsame Funktion würde die Projektionsgrenze aufweichen und die Schreibberechtigung in den Lesepfad ziehen (E-01, ADR-002 §7) — siehe §1 |
| 10 Tabellen, 10 RLS-Policies, 33 Indizes | ja | jede Tabelle trägt ein fachliches Attribut; das Schema ist in diesem Schritt **nicht** angefasst |
| `x-delivery-protocol` | ja | eingefrorener Fremdvertrag (FR-020, D-07, D-08) — eine Stelle, nicht drei |
| `x-acceptance-coverage` (101) und `x-requirement-coverage` (24) | ja | Nachweis der Abdeckung; jeder Eintrag zeigt **wo** |
| 5 Diagramme (4 C4 + 1 Sequenz) | ja | jede Ebene beantwortet eine andere Frage; die Korrekturen aus Schritt 4 und dem Rücksprung sind **nicht** zurückgedreht |
| `GLOSSARY.md`, `domain_model.md` | ja | einziger Ort je Fachbegriff bzw. der Zustandsautomat — in Schritt 3 **unverändert**; sie waren die **Entscheidungsgrundlage** für R-03 |
| Der Vertrag wird **nicht** eingefroren | ja | der Einfrierpunkt kommt **nach Schritt 4** (AGENTS.md) |

---

## 6. Berührte Story / AK / Anforderung

**Die Abdeckung ist intakt.** Die Reduktion hat **kein** Akzeptanzkriterium,
**keine** Story und **keine** Anforderung entfernt; **berührt** wurden vier
Stellen, an denen die **Aussage** präzisiert wurde.

| ID | Vorher | Nachher | Warum die Abdeckung intakt bleibt |
|---|---|---|---|
| **AC-016-4** (Totenliste mit Endpunkt, **Status** und Zeitraum gefiltert, seitenweise) | Filter über alle `DeliveryStatus` | Filter über **nur `abandoned`**; die **Operation** und ihre Filter bleiben unverändert | Das AK verlangt einen **filterbaren Status** und Seitenweise Abrufbarkeit — beides bleibt. Der Filter ist **scharfer**, nicht weg: der Vertrag sagt jetzt ausdrücklich, dass die Liste ausschließlich aufgegebene Zustellungen enthält (Glossar, FR-023). Der Zeitraumfilter ist jetzt zusätzlich als „Aufgabezeitpunkt“ präzisiert |
| **AC-016-1** (Totenliste mit … **dem Zeitpunkt der Aufgabe**) | `abandoned_at` Pflicht, aber ohne Herkunftsaussage | unverändert Pflicht — jetzt **begründet**: die Liste enthält nur `abandoned`, und die Datenbank erzwingt `abandoned_at` für jeden solchen Lauf | Das AK ist unverändert und genau an dieser Operation realisiert; es wird jetzt **wahr** ausgeliefert statt widersprüchlich |
| **AC-016-5** (Aufbewahrung, Frist **konfigurierbar**) | Antwort nannte Fristen und Stichtage | zusätzlich `event_retention_source` / `delivery_retention_source` je Mandant | Das AK verlangt eine **konfigurierbare** Frist — die Antwort weist jetzt **ausdrücklich** aus, ob die Frist des Mandanten oder die Vorgabe gilt. Das AK selbst bleibt unverändert; **F-02** bleibt unverändert |
| **AC-007-3** (zugestellte Bytes = Nutzlastbytes) | Aussage an drei Stellen im Vertrag | Aussage **an einer** Stelle (`x-delivery-protocol.request.body`); die Feld-Beschreibungen nennen nur noch **ihren** Fall | Der Vertragstext der ausgehenden Zustellung ist unverändert; die **Feld-Beschreibungen** bleiben je für sich richtig. Kein AK verliert seinen Ort |
| **FR-004, FR-031** (mandantenübergreifende Sicht) | Vertrag nannte die SQL-Funktionen | Vertrag nennt den **Mechanismus** (ADR-002 §7) und die client-sichtbare Tatsache (`organisation_id` im Datensatz) | Die Anforderung ist eine **fachliche**; die Funktionsnamen waren nie ihr Träger. `x-requirement-coverage` bleibt unverändert |
| **23 Stories, 101 AKs, 24 Anforderungen** | — | **unverändert** | maschinell geprüft: alle 101 AK-IDs und 24 Anforderungs-IDs sind auffindbar, ohne Dubletten (§7) |

---

## 7. Wo die bestätigten Entscheidungen jetzt stehen

**Keine bestätigte Festlegung wurde neu aufgerollt.** F-01…F-04, E-01…E-09,
D-01…D-15 und ADR-001…ADR-018 gelten unverändert.

| Festlegung | Wert | ADR (Wahrheit) | Vertrag | Architektur |
|---|---|---|---|---|
| **F-01** Rate-Limit | 60/Endpunkt/Minute, `delay`, **zwei** Zustände | ADR-010 | `info.description` (Tabelle) + `x-delivery-protocol.rate_limit` + `RateLimitObservation` | Katalog §2; `architecture.md` §4 |
| **F-02** Aufbewahrung | je 30 Tage, getrennt, ab letztem Versuch, **Kaskade** | ADR-012 §1-§6 | `info.description` (Tabelle) + `enforceRetention` + `RetentionEnforcementResponse` + `TenantRetention` | `architecture.md` §4; Katalog §2 |
| **F-03** Replay | nur Empfänger-Admin | ADR-015 | `replayDelivery` | `architecture.md` §4 |
| **F-04** Versuche/Backoff | 8 / 1 min / ×2 / 60 min / ± 20 % | ADR-008 §7 | `info.description` (Tabelle) + `x-delivery-protocol.retry_policy` | `architecture.md` §3.2 |
| **E-01** mandantenübergreifende Sicht | drei schmale Eigentümerfunktionen | ADR-002 §7 | die drei Operationen (Mechanismus verwiesen, `organisation_id` im Datensatz) | `architecture.md` §4; Katalog §3 |
| **R-01** Karenzzeit | **kein** Vorgabewert — **offen** | ADR-012 §6 | `purge_delay_days` nullable, „kein Vorgabewert“, Beispiel `null` | `architecture.md` §4, §7; Katalog §8; `docs/adr/README.md` |

---

## 8. Bewusst **nicht** geändert

| Bereich | Warum nicht |
|---|---|
| `.agent/knowledge/schema.sql` und `docs/architecture/schema.sql` | Das Schema liegt **außerhalb** des Commit-Scopes dieses Schritts und wird durch die Spiegel-Regel **byte-identisch** gepflegt (ADR-016). Jede Schema-Vereinfachung wäre ein **Befund**, keine Änderung. Zwei Befunde sind in §9 festgehalten |
| `docs/requirements/**`, `docs/Anforderung.md` | Ergebnisbereich von Schritt 1 — schreibgeschützt in diesem Schritt |
| `src/`, `tests/` | noch kein Anwendungscode (Schritt 6) |
| Diagramme, `domain_model.md`, `GLOSSARY.md` | die Korrekturen aus Schritt 4 und dem Rücksprung sind **nicht** zurückgedreht; das Glossar war die **Entscheidungsgrundlage** für R-03 |
| ADR-002, ADR-006, ADR-008, ADR-009, ADR-010, ADR-012, ADR-015 | die Korrekturen aus dem Rücksprung bleiben unverändert; in ADR-012 wurde **nur** die Fundstelle von R-01 ergänzt, in ADR-016 **nur** die Dateibeziehung (§4) |
| Der Vertrag wird **nicht** eingefroren | der Freeze kommt **nach Schritt 4** (AGENTS.md) |
| ADR-Nummerierung | nicht umgestellt — eine Umnummerierung bricht jeden Verweis, ohne eine Entscheidung zu gewinnen |

---

## 9. Befunde für Schritt 6 (nicht in diesem Schritt änderbar)

| ID | Befund | Wirkung |
|---|---|---|
| **S-06** | Die Eigentümerfunktion `app.list_dead_deliveries_cross_tenant` filtert **nur**, wenn `p_status` gesetzt ist; ohne Wert liefert sie **alle** Zustände (auch `sent`) | Die Anwendung muss den Wert **`abandoned` immer** setzen. Der Vertrag lässt seit Schritt 3 nur `abandoned` zu — die Funktion ist weiterhin flexibler als der Vertrag (bewusst: das Schema bleibt unverändert) |
| **S-07** | Das Schema behandelt „kein Karenzzeitwert konfiguriert“ als **0 Tage** (`COALESCE(p_purge_delay_days, 0)`) ⇒ Purge im **nächsten** Durchlauf | Für die Betriebsregel relevant (R-01); der Vertrag behauptet **keinen** Wert |
| **S-08** | `enforce_retention_cross_tenant` liefert **keine** Karenzzeit und **keine** Herkunft der Fristen zurück (`event_retention_days` ist der **angewandte** Wert) | Die Anwendung ergänzt `purge_delay_days` und `*_retention_source` aus der Konfiguration bzw. aus `retention_settings` — beides ist in Schritt 6 zu belegen |

---

## 10. Prüfungen

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | **grün** — keine Lücke |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** |
| Vertrag: YAML | `python3 -c "import yaml; yaml.safe_load(...)"` | **gültig** |
| Vertrag: Operationen | Zählung über `paths` | **17** — alle mit `operationId`, `summary`, `description`, `responses`, `x-ac-id`, `x-requirement-ids` |
| Vertrag: `$ref` | Auflösung aller **176** Verweise | **alle auflösbar** |
| Vertrag: ungenutzte Objekte | `schemas`, `responses`, `parameters`, `headers` | **keine** |
| Vertrag: Abdeckung | `x-acceptance-coverage` **101**, `x-requirement-coverage` **24** | **vollständig, keine Dubletten** |
| Vertrag: Beispiele | jedes Antwortbeispiel gegen sein Schema geprüft (Pflichtfelder) | **ok** — auch die neuen Felder `DeadDeliveryStatus`, `*_retention_source` |
| Anforderungen unberührt | `git diff --stat -- docs/requirements/ docs/Anforderung.md` | **leer** |
| Kein Anwendungscode | `git diff --stat -- src/` | **leer** (`src/` existiert nicht) |

---

## 11. Offen für Schritt 4 (Stresstest)

1. **R-01** — die **Karenzzeit**: kein Wert, keine Vorbelegung; der Vertrag
   behauptet keinen. **Gate 2, Fachbereich.**
2. **Die drei `SECURITY DEFINER`-Funktionen** — ihre Projektionen, ihre
   Fail-closed-Regel und der Cursor über Mandantgrenzen hinweg: hält die
   Grenze „drei schmale Eigentümerfunktionen“ auch unter Last?
3. **Die Totenliste** — Filter auf **nur `abandoned`**: genügt dem Betrieb, der
   auch endgültig abgelehnte Läufe sehen will? (Die Antwort lautet heute
   „nein“ — bewusst, nach dem Glossar.)
4. **Der Vertrag wird nach Schritt 4 eingefroren** — die letzten Änderungen
   müssen jetzt stehen.
