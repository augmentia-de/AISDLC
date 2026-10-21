# Architektur — EchoBox (kompakter Katalog)

> Schritt 3 (**Simplify & Reduktion** nach dem Rücksprung aus Schritt 4) ·
> **Schritt 2 — Rücksprung 5** (letzter Korrekturdurchgang vor dem Einfrieren:
> V-39…V-44 entschieden und umgesetzt) ·
> **Schritt 4 — dritte Runde** (Delta-Prüfung, `2026-10-21`): der Vertrag ist
> **einfrierreif**; die zwei neuen Befunde **V-48** und **V-49** stehen in § 8,
> die drei offenen **V-45…V-47** trägt die Testmatrix (Schritt 5) · Agent:
> `aisdlc-architecture` · Stand: 2026-10-21
> Erzählung: `docs/architecture/architecture.md` · Vertrag: `docs/api/openapi.yaml`
> Schema: `.agent/knowledge/schema.sql` (kanonisch) · **Entscheidungen: `docs/adr/`**
> Reduktionsbericht: `.agent/artifacts/simplify-report.md`
> **Nachweis des Rücksprungs: `.agent/artifacts/ruecksprung-bilanz.md`**
> (ein **Vorgangsprotokoll** — kein lebender Wahrheitsort)
>
> Dieser Katalog ist eine **Übersicht**, keine zweite Wahrheit: die
> Entscheidungen stehen in den ADRs, die Fachbegriffe im Glossar, das
> Datenmodell im Domain-Modell, der Vertrag in `docs/api/openapi.yaml`.

## 1. Das System in einem Satz

Ein zentraler Dienst nimmt Ereignisse entgegen, stellt sie **signiert und
at-least-once** per HTTP an registrierte Empfänger-URLs weiter und hält ein
nachvollziehbares Protokoll über jeden Versuch — bewusst **kein** Broker und
**kein** Event-Store.

## 2. Entscheidungen (17 ADRs — Wahrheit: `docs/adr/`)

| ADR | Entscheidung | Status |
|---|---|---|
| ADR-001 | **Java 21 / Spring Boot 3 / PostgreSQL 16 / Next.js 15 (React 19, TS strict)**; Persistenz über JdbcTemplate, kein JPA; kein LLM | Angenommen |
| ADR-002 | **Mandantentrennung per Row-Level-Security** auf jeder fachlichen Tabelle; `app.set_tenant()` **transaktionsgebunden** (in der Transaktion, die auch liest); **fail-closed**; keine Rolle mit `BYPASSRLS`; **§7:** die mandantenübergreifende Sicht des Service-Admins über **drei schmale `SECURITY DEFINER`-Eigentümerfunktionen** (kein `BYPASSRLS`, keine Mandantenliste) — Totenliste mit **fest verdrahtetem** Status `abandoned`, **keine** logische Löschung durch die Applikationsrolle; **Grenze:** die Applikationsrolle kann `app.cross_tenant` setzen (E-12, Gate 2) | Angenommen (im Rücksprung und im **Rücksprung 2** nachgezogen) |
| ADR-003 | **AES-256-GCM (AEAD)** für Nutzlast und Endpunkt-Secret; Schlüssel aus dem Secret-Store, **außerhalb** der DB; entschlüsselt **nur** zur Zustellung; Suche nur über Metadaten | Angenommen (Architekturseite von O-04) |
| ADR-004 | **HMAC-SHA256**, Header **`X-EchoBox-Signature`** = `v1=<hex>`; Eingabe `<timestamp>.<payload_bytes>`; **eingefroren** | Angenommen |
| ADR-005 | **Drei Kennungen**: `X-EchoBox-Event-Id`, `X-EchoBox-Delivery-Run-Id`, `X-EchoBox-Attempt-Id`; Body = exakt die Nutzlastbytes | Angenommen |
| ADR-006 | Zustelllauf **ist** die Warteschlange (transaktionales Outbox); `FOR UPDATE SKIP LOCKED` + **Lease**; getrennte Rollen `api` und `worker`; Worker arbeitet **mandantenweise**; Replay **einmal** (`UNIQUE (replay_of)`) — `replay_of` ist ein **historischer Verweis** ohne Fremdschlüssel; **23505 ⇒ 409**, `lock_timeout` kürzer als das HTTP-Timeout | Angenommen (im Rücksprung nachgezogen, im **Rücksprung 2** präzisiert) |
| ADR-007 | Idempotenz je **(Mandant, Schlüssel)**; gleiche Nutzlast ⇒ 202 mit erster ID; abweichende Nutzlast ⇒ **409**; Vergleich über gezielten HMAC | Angenommen |
| ADR-008 | Wiederholung bei 5xx/408/429/Netzwerkfehler; **3xx ⇒ kein Wiederholen**; Backoff mit **Verdopplung + voller Streuung**; **8 Versuche, Basis 1 min, ×2, Deckel 60 min, ± 20 %**; **Gesamtfenster ≈ 2 h**; **Zeitlimit je Versuch 5 s + 10 s = 15 s** | Angenommen (**bestätigt: F-04**, O-06/O-07) — enthält die frühere ADR-014 |
| ADR-009 | **Ablehnungssignal: HTTP 410** + `X-EchoBox-Rejection: permanent` + festgeschriebener JSON-Body; übrige 4xx ⇒ ebenfalls Ende, aber `rejection_reason = client_error`; **3xx ⇒ ebenfalls Ende, Redirects werden nicht gefolgt** | Angenommen (im Rücksprung nachgezogen) |
| ADR-010 | Endpunkt-Begrenzung: Zähler **immer** aktiv, Vorgabewert **60 Versuche/Endpunkt/Minute**, Strategie **`delay`**; **kein** Ereignisverlust; **genau zwei Zustände** (`NULL` = kein Override ⇒ 60, `0` = abgeschaltet) — die Spalte `enabled` ist entfallen | Angenommen (**bestätigt: F-01**) |
| ADR-011 | Erfolgsquote = erfolgreiche Versuche / alle Versuche; p95 von Annahme bis erfolgreicher Antwort; `data_available: false` statt 0 % | Angenommen |
| ADR-012 | Aufbewahrung: **zwei** Fristen, Vorgabe **je 30 Tage**, Fristbeginn **je Datensatz** = **letzter Versuch**; markieren (`deleted_at`) dann Purge nach der **Karenzzeit** (ohne gesetzten Wert: Purge im **nächsten** Durchlauf — **kein** erfundener Vorgabewert); **Kaskade** (Ereignis nimmt Läufe und Versuche mit); die **Versuche folgen ihrem Lauf** (sie verlieren die Sichtbarkeit mit ihm); `dry_run` als Default | Angenommen (**bestätigt: F-02**, im **Rücksprung 2** präzisiert) |
| ADR-013 | Ein Image, zwei Rollen; `echobox.tenant.id` bindet eine Instanz an einen Mandanten; non-root, keine Secrets im Image; Staging/Produktion getrennt | Angenommen |
| ADR-014 | — | **Zusammengeführt in ADR-008** (Verweisdatei) |
| ADR-015 | Ein Fehlerschema (`Problem`); 422 bei Mandantenparameter, 404 bei fremden Ressourcen, 403 bei Rollenverstoß; **§3: erst der Mandant, dann die Rolle** (V-39); Rollenmatrix (die mandantenübergreifende Sicht des Service-Admins gilt **nur** für Endpunktliste, Totenliste und Aufbewahrung — das **Zustellprotokoll bleibt mandantengefiltert**, `organisation_id` ist **immer** gefüllt); Replay **nur** für den Empfänger-Admin und **nur einmal** (23505 ⇒ `delivery_state_conflict`) | Angenommen (**bestätigt: F-03**, im **Rücksprung 2** korrigiert, im **Rücksprung 5** an V-26/V-27/V-39 angeglichen) |
| ADR-016 | Ein Ort pro Wahrheit: Vertrag **einmal**, Schema kanonisch + byte-identische Kopie; Freeze nach Schritt 4 | Angenommen |
| ADR-017 | Protokollsuche **nur** über Metadaten; Denormalisierung von `event_type`/`endpoint_id`; Cursor-Paginierung ohne `total`; **§4:** der Zeitraumfilter ist **je Liste benannt** — Protokoll ⇒ `created_at`, Totenliste ⇒ `abandoned_at` (V-40) | Angenommen (im **Rücksprung 5** präzisiert) |
| ADR-018 | Next.js 15 App Router; Barrierefreiheit als **Bauregel**: native Bedienelemente, echte `<table>`, Status nie nur über Farbe, axe als CI-Gate | Angenommen |

**Bestätigte Festlegungen (Gate 1, 2026-10-21):** F-01 (Rate-Limit), F-02
(Aufbewahrung), F-03 (Replay-Berechtigung), F-04 (Versuche/Backoff). Alle vier
sind **Vorgabewerte in der Konfiguration** — kein Wert ist im Code verdrahtet.

## 3. Komponenten

| Schicht | Komponenten | Daten |
|---|---|---|
| **Oberfläche** | Endpunkte, Zustellprotokoll, Tote Zustellungen | — |
| **API-Layer** | Security-Adapter, Mandantenkontext, 5 Controller, 8 Fachservices, Crypto-Service, Fehlerabbildung | `endpoints`, `events`, `delivery_runs`, `delivery_attempts`, `audit_log` |
| **Mandantenübergreifende Sicht** (Ausnahme, ADR-002 §7) | drei schmale `SECURITY DEFINER`-Eigentümerfunktionen — `search_path` verdrahtet, `EXECUTE` nur für `echobox_app`, fail-closed, **nur** Projektionen (kein Klartext, keine Chiffre, keine Secrets); Cursor läuft **über die Mandantgrenzen hinweg** | dieselben Tabellen, ohne RLS (Eigner) |
| **Zustell-Worker** | Scheduler, Claimer (Lease), Dispatcher, Signatur, Backoff, Endpunkt-Begrenzung, Protokollschreiber, Lease-Rettung | dieselben Tabellen |
| **Datenbank** | PostgreSQL 16, Schema `app`, RLS auf **allen** 10 fachlichen Tabellen | Zähler, Rate-Limits, Aufbewahrungseinstellungen |

## 4. Datenfluss (Kurzform)

```
POST /api/v1/events
  → JWT → Mandant → app.set_tenant(mandant)      [fail-closed, ADR-002]
       (innerhalb DESSER Transaktion, die auch liest — transaktionsgebunden)
  → Größe (1 MB) / Pflichtfelder / Idempotenz (Mandant, Schlüssel)
  → Nutzlast verschlüsseln (AES-256-GCM) + Fingerprint (HMAC)
  → INSERT events + delivery_runs  (EINE Transaktion)
  → 202 Accepted
                                    ← die Zustellung startet erst JETZT
Worker: Scheduler → app.tenant_ids_for_worker() → je Mandant:
         BEGIN; app.set_tenant(mandant); Claim (SKIP LOCKED + Lease); COMMIT;
  → entschlüsseln (oder Chiffre durchreichen)
  → signieren (HMAC-SHA256 über timestamp.payload)
  → POST an die Ziel-URL (Body = die Nutzlastbytes des Ereignisses)
  → Antwort → Zustand + Versuch protokollieren → Backoff oder Ende
  → 3xx/4xx ⇒ rejected (kein Wiederholen); Replay ⇒ neuer Lauf (einmal)
```

## 5. Risiken und Gegenmaßnahmen

> **IDs aus der Anforderungserhebung** (Risikoregister, Schritt 1) — **nicht**
> verwechseln mit den **Befund**-IDs R-01…R-05 in §9 (Rücksprung aus Schritt 4).
> Beide heißen „R-xx“, meinen aber **verschiedene** Dinge.

| Risiko | Gegenmaßnahme | Wo |
|---|---|---|
| R-01 Zustellgarantie ohne erkennbare Dubletten | Drei Kennungen, im Vertrag eingefroren | ADR-005 |
| R-02 Mandantenleck trotz sauberer Anwendung | RLS, fail-closed, **gegen echte PostgreSQL geprüft** | ADR-002 |
| R-03 Verschlüsselung nicht nachweisbar | AES-256-GCM, Schlüssel außerhalb, Fingerprint | ADR-003 |
| R-04 Daten wachsen unbegrenzt | Aufbewahrungsmechanik mit **Vorgabe 30 Tage** je Datenart (F-02) | ADR-012 |
| R-05 Ereignisse verschwinden still | `no_subscriber`, `cancelled`, `abandoned`, `rejected` — alle sichtbar | ADR-006, ADR-015 |
| R-06 Signaturverfahren bricht Empfänger | Verfahren + Header im Vertrag eingefroren, Vertragstest | ADR-004 |
| R-08 Rate-Limit ohne Zahl | **Zahl ist entschieden (60/Minute, `delay`)**; Zähler liefen vorher, jetzt Grenzwert aktiv | ADR-010 |
| R-09 Replay-Berechtigung widersprüchlich | **entschieden: nur Empfänger-Admin**, Entwickler und Betrieb ⇒ 403 (F-03) | ADR-015 |
| R-10 Dienst wird zum Event-Store | Zustelllauf ist die einzige Wahrheit; Frist konfigurierbar | ADR-012 |
| R-11 Doppelzustellung durch konkurrierende Worker | `SKIP LOCKED` + Lease | ADR-006 |
| R-13 Mandantenleck über die mandantenübergreifende Sicht | Drei schmale `SECURITY DEFINER`-Eigentümerfunktionen, `REVOKE` von `PUBLIC`, `EXECUTE` nur für die Applikationsrolle, fail-closed, **keine** Nutzlast und **keine** Secrets in der Projektion, Totenliste mit **fest verdrahtetem** Status — **gegen echtes PostgreSQL 16 belegt**. **Verbleibende Lücke (ehrlich benannt, ADR-002):** dieselbe Applikationsrolle kann `app.cross_tenant` setzen; die Härtung ist **offen** (E-12, Gate 2) | ADR-002 §7 |
| R-12 Latenzversprechen gebrochen | Annahme und Zustellung getrennt (D-10) | ADR-006 |

## 6. Vertragsumfang

| | |
|---|---|
| OpenAPI | 3.0.3, `info.version` 1.0.0 |
| Operationen | **17** (7 Endpunkte, 2 Ereignisse, 4 Zustellung, 1 Betrieb, 2 Health/Readiness, 1 Aufbewahrung) — reduziert von 18 (Schritt 3: `GET /api/v1/events` entfallen) |
| Ausgehender Vertrag | `x-delivery-protocol` — eingefroren (Signatur, Kennungen, Retry-Matrix, Ablehnungssignal, Dubletten) |
| Stories abgedeckt | **23 / 23** |
| Akzeptanzkriterien auffindbar | **101 / 101** (`x-acceptance-coverage`) |
| Anforderungen auffindbar | **24 / 24** (`x-requirement-coverage`) |
| Fehlende Operationen | **keine** |

## 7. Abdeckung der 23 Stories

Die Zuordnung Story → Vertragsort steht **einmal** in
`docs/architecture/architecture.md` §6; dieser Katalog nennt nur die Zahlen.

| Epic | Stories | Kurz |
|---|---|---|
| E1 | US-001…US-004 | Endpunkte: anlegen, lesen, ändern, pausieren, reaktivieren, Secret rotieren |
| E2 | US-005…US-008 | Ereignis einliefern (202/409/413/422), Ereignis lesen |
| E3 | US-009…US-014 | ausgehender Zustellvertrag (`x-delivery-protocol`), Replay |
| E4 | US-015…US-018 | Zustellprotokoll, Totenliste, Kennzahlen, Aufbewahrung |
| E5 | US-019…US-021 | Oberfläche: Datenquellen und Barrierefreiheit (ADR-018) |
| E6 | US-022, US-023 | RLS (ADR-002), `/health` + `/health/ready`, Mandanten-Sharding (ADR-013) |

## 8. Offene Punkte — bewusst **nicht** entschieden

| ID | Thema | Zustand in der Architektur | Owner | Gate |
|---|---|---|---|---|
| O-01 | Rate-Limit | **entschieden (F-01)**: 60 Versuche/Endpunkt/Minute, `delay` | — | erledigt |
| O-02 | Aufbewahrung | **entschieden (F-02)**: je 30 Tage, getrennt, ab letztem Versuch | — | erledigt |
| O-03 | Replay-Berechtigung | **entschieden (F-03)**: nur Empfänger-Admin des Endpunkts | — | erledigt |
| O-04 | Verschlüsselung | **Architekturseite entschieden** (ADR-003, ADR-017); offen ist die **fachliche** Pflichtfrage | Fachbereich + Datenschutz | Gate 2 |
| O-05 | AVV | technisch vorbereitet | Fachbereich + Recht | Gate 2 |
| O-06 | Versuche/Backoff | **entschieden (F-04)**: 8 Versuche, 1 min Basis, ×2, 60 min Deckel, ± 20 % | — | erledigt |
| O-07 | Zeitlimit je Versuch | **entschieden** (ADR-008): 5 s + 10 s = 15 s | — | erledigt |
| **R-01** | **Karenzzeit** der Aufbewahrung („markiert“ → „gelöscht“) | **kein beschlossener Vorgabewert**; konfigurierbar (`echobox.retention.purge-delay`), **ohne Vorbelegung**; der Vertrag behauptet keinen Wert (ADR-012 §6). Das **Schema-Verhalten ist explizit**: ohne gesetzten Wert Purge im **nächsten** Durchlauf (E-13). **F-02 bleibt unverändert** (30 Tage je Datenart, Anker letzter Versuch, Kaskade) | Fachbereich | **Gate 2** |
| **E-12** | **Härtung** der mandantenübergreifenden Sicht: eigene Laufzeitrolle `echobox_ops` statt der Allzweckrolle `echobox_app`? | **nicht entschieden** — die Grenze ist in **ADR-002** ehrlich benannt (die Applikationsrolle kann `app.cross_tenant` setzen). Die Härtung wäre die konsequenteste Lösung, verlangt aber eine getrennte Verbindungsrolle im Betrieb (ADR-013) | Fachbereich + Plattform | **Gate 2** |
| **V-26** | `Endpoint.organisation_id`: Beschreibung („nur für `service_admin` gefüllt“) und zwei Beispiele (`pauseEndpoint`, `listEndpoints` mit `organisation_id: m-1`) widersprechen sich | **entschieden** (Rücksprung 3): `organisation_id` benennt **immer** den **besitzenden Mandanten** — im eigenen Mandanten den eigenen, in der mandantenübergreifenden Sicht den Mandanten, zu dem die Ressource gehört. Umsetzung an 10 Stellen (Feldbeschreibungen `Endpoint`/`DeliveryRun`/`DeadDelivery`, Beispiele, `info.description`, `x-contract-rules.tenant`) | — | erledigt |
| **V-27** | `service_admin` bekommt aus der mandantenübergreifenden **Endpunktliste** und **Totenliste** IDs fremder Mandanten, deren **Einzelabruf** 404 ergibt (D-02) | **entschieden** (Rücksprung 4): die mandantenübergreifende Sicht gilt **nur** für die **drei** Listen-/Betriebsoperationen (Endpunktliste, Totenliste, Aufbewahrung — E-01); der **Einzelabruf** einer fremden Ressource ergibt **auch für `service_admin`** **404**; ein **vierter** mandantenübergreifender Leseweg wird **nicht** gebaut. Umsetzung an 10 Vertragsstellen; das Schema bleibt bei **drei** Eigentümerfunktionen (ADR-002 §7) | — | erledigt |
| **V-39** | Reihenfolge der Rollen- **oder** der Mandantenprüfung beim Replay (403 vs. 404) | **entschieden** (Rücksprung 5): **erst der Mandant, dann die Rolle** — ein Zustelllauf **fremden** Mandanten ⇒ **404** (auch für `developer`/`service_admin`), ein Lauf im **eigenen** Mandanten **ohne** `receiver_admin` ⇒ **403**. Umsetzung an 10 Vertragsstellen und in ADR-015 §3 | — | erledigt |
| **V-44** | ADR-015 §1 (Rollenmatrix „Endpunkte verwalten: ja (alle Mandanten)") und §2 („im Zustellprotokoll ist das Feld `null`") widersprechen V-26/V-27 | **entschieden** (Rücksprung 5): **keine Entscheidung, sondern eine Korrektur** — ADR-015 §1/§2/§3 auf den entschiedenen Stand gebracht, `docs/adr/README.md` um einen Korrekturabschnitt ergänzt | — | erledigt |
| **V-40** | Zeitraumfilter des Zustellprotokolls (`from`/`to`) — an welchen Zeitstempel? | **entschieden** (Rücksprung 5): **`created_at`** (der Sortierschlüssel, ADR-017 §4); die Totenliste bleibt bei **`abandoned_at`** — beide Aussagen stehen nebeneinander und sind je benannt. Umsetzung an 5 Vertragsstellen und in ADR-017 §4 | — | erledigt |
| **V-41** | `ReadinessStatus` führt `not_ready`/`unavailable`, die der Vertrag nie erzeugt (503 ⇒ `Problem`, ADR-015 §4) | **entschieden** (Rücksprung 5): Enum auf die **beobachtbaren** Werte (`ready`, `ok`) geschärft; der nicht bereite Zustand kommt als **503 mit `Problem`** | — | erledigt |
| **V-42** | `EndpointMetrics.rate_limit` ist optional, die Beschreibung behandelt den Abschnitt als vorhanden | **entschieden** (Rücksprung 5): `rate_limit` ist **immer** Teil der Antwort und steht in `required` (die Zählung läuft unabhängig vom Grenzwert, AC-018-4) | — | erledigt |
| **V-43** | `Event.deleted_at` ist ein totes Feld (in der Antwort immer `null`, ADR-012 §3) | **entschieden** (Rücksprung 5): **Feld entfernt** — die Spalte `app.events.deleted_at` bleibt **intern**, die API gibt den Marker nicht aus; ein markiertes Ereignis ist ohnehin 404 | — | erledigt |
| **V-45** | AC-013-3 nennt **403 ohne Mandantbezug** — mit V-39 ist ein Zustelllauf **fremden** Mandanten **404**. Die Testmatrix (Schritt 5) muss „eigener Mandant, falsche Rolle ⇒ 403" und „fremder Mandant ⇒ 404" **trennen**; die **Anforderung** wird **nicht** geändert | **dokumentiert, nicht entschieden** (Bilanz § 11.3) | Schritt 5 (Testmatrix) | **vor dem Freeze** |
| **V-46** | Optionale, aber als immer vorhanden beschriebene Antwortfelder (`EndpointMetrics.attempts_total`/`attempts_successful`/`success_rate`/`delivery_latency_p95_ms`, `Event.subscribed_endpoint_count`, `EventIntakeResponse.subscribed_endpoint_count`/`payload_size_bytes`) — dieselbe Musterlage wie V-42 | **dokumentiert, nicht entschieden** (Bilanz § 11.3) | Architektur | nach dem Freeze |
| **V-47** | `updateEndpoint`, `pauseEndpoint`, `resumeEndpoint`, `rotateEndpointSecret` **deklarieren** 404, **nennen** die Fremdmandanten-Regel aber nicht (anders als die vier Lesoperationen) | **dokumentiert, nicht entschieden** (Bilanz § 11.3) | Architektur | nach dem Freeze |
| **V-48** | ADR-002 §7 (`:82`) nennt `organisation_id` nur für die **mandantenübergreifenden** Antworten — die schwächere V-25-Fassung, die V-26 abgelöst hat; der Vertrag sagt es an 12 Stellen richtig | **dokumentiert, nicht entschieden** (Stresstest 3, § 2.6) — **kein** Widerspruch, nur schwächer; Korrektur ist ein Satz in `docs/adr/` | Architektur | **Gate 1** → Rücksprung zu Schritt 2 |
| **V-49** | Die **Rollentabelle des Vertrags** (`info.description`, `:50-54`) nennt dem `service_admin` weder die Endpunktverwaltung noch die Ereigniseinlieferung, die **ADR-015 §1** (`:67-68`) gewährt; PRD und Vertrag sagen es **nicht** | **dokumentiert, nicht entschieden** (Stresstest 3, § 2.6) — **kein** AK/FR betroffen; Empfehlung: ADR-015 §1 auf PRD + Vertrag **verschärfen** | Fachbereich + Architektur | **Gate 1** → Rücksprung zu Schritt 2 |

## 9. Befunde dieses Schritts

> **IDs aus dem Rücksprung (Schritt 2)** — **nicht** verwechseln mit den
> **Risiko**-IDs R-01…R-13 in §5 (Anforderungserhebung).

| ID | Befund | Behandlung |
|---|---|---|
| B-01 | Glossar-Widerspruch „Zustellung“/„Lieferung" vs. FR-021 | D-06: drei Kennungen, im Vertrag eingefroren; Glossar ist der einzige Ort je Begriff |
| B-02 | Offene Zahlenwerte (Aufbewahrung, Rate-Limit, Backoff) | **entschieden (F-01, F-02, F-04)** — als Vorgabewerte in der Konfiguration |
| B-03 | FR-024 gegen Rollentabelle | **entschieden (F-03)**: nur Empfänger-Admin, alle anderen 403 |
| B-04 | FR-025 ohne benanntes Signal nicht umsetzbar | Ablehnungssignal im Vertrag festgeschrieben (ADR-009) |
| B-05 | Volltextsuche unvereinbar mit FR-012 | D-11 / ADR-017: Suche nur über Metadaten |
| B-06 | `abandoned_at` fehlte im ersten Schemaentwurf | beim Anwenden gegen PostgreSQL 16 gefunden und ergänzt (DB-Constraint) |
| B-07 | `GET /api/v1/events` trug keine Story, kein NFR, keinen Akzeptanzpunkt | **entfallen** (Schritt 3) — Metadatensuche läuft über `GET /deliveries` |
| B-08 | Fehlerschema und ADRs doppelt in `architecture.md` | Redundante Tabellen entfernt, Verweise auf ADR-015 und den Vertrag |
| B-09 | ADR-014 und ADR-008 variieren dieselbe Entscheidung (fehlgeschlagener Versuch) | In ADR-008 zusammengeführt; ADR-014 bleibt als Verweis |
| **R-03** | `DeadDelivery.abandoned_at` nicht nullable, der Statusfilter der Totenliste ließ aber `rejected` zu | **behoben** (Schritt 3): die Totenliste enthält **ausschließlich aufgegebene** Zustellungen (`abandoned`) — GLOSSARY „Tote Zustellung“, Domain-Modell §3.1, DB-Check `delivery_runs_abandoned_at_ck`. Der Filter lässt nur `abandoned` zu (`DeadDeliveryStatus`); `abandoned_at` bleibt Pflicht |
| **R-04** | Cursor-Semantik der mandantenübergreifenden Funktionen im Vertrag nicht beschrieben | **behoben** (Schritt 3): Abschnitt „Pagination“ im Vertrag — Cursor **über die Mandantgrenzen hinweg**, globale Reihenfolge, Sortierschlüssel + UUID als Tie-Breaker, Keyset statt Offset, `enforceRetention` **ohne** Paginierung |
| **R-05** | `RetentionEnforcementResponse.tenants[]` unterschied „eigene Frist“ nicht von „Vorgabe“ | **behoben** (Schritt 3): `event_retention_source` / `delivery_retention_source` (`tenant` \| `default`); die falsche Beschreibung „`null` = kein Override“ korrigiert (der Wert ist die **angewandte** Frist) |
| **R-01** | Karenzzeit ohne beschlossenen Wert, im Vertragsbeispiel stand **7 Tage** | **ohne Wert festgelegt**: Beispielwert entfernt, Feld nullable, „kein Vorgabewert“ ausgesprochen; **bleibt offen** (Fachbereich, Gate 2) — siehe §8 |

## 9a. Befunde aus Schritt 4 (Grill-Me-Stresstest) — **alle behandelt**

Vollständig in `.agent/artifacts/stress-test.md`; die Behandlungsbilanz je
Defekt in `.agent/artifacts/ruecksprung-bilanz.md`.

| # | Befund | Behandlung (Rücksprung in Schritt 2) |
|---|---|---|
| S-01 | **„muss“** — `service_admin` soll alle Mandanten sehen, RLS erlaubt genau einen; keine Rolle mit `BYPASSRLS` | **behoben** (E-01): drei schmale `SECURITY DEFINER`-Eigentümerfunktionen im Schema `app` (ADR-002 §7), **gegen PostgreSQL 16 belegt** |
| S-02 | **„muss“** — Vertrag nennt `page.nextCursor`/`hasMore`, das Schema `next_cursor`/`has_more` | **behoben** (V-01) |
| S-03 | **„muss“** — Aufbewahrung: globaler Stichtag statt Anker „letzter Versuch“, Markieren/Purge/Karenzzeit im Vertrag nicht abgebildet | **behoben** (V-02, E-04) |
| S-04 | **„muss“** — wirksamer Grenzwert: `null` heißt im Vertrag „abgeschaltet“, in ADR-010 „kein Override“ (⇒ 60) | **behoben** (V-03, E-02): zwei Zustände, `enabled` entfallen |
| S-05 | **„muss“** — „exakt die Nutzlastbytes“ vs. kanonische Materialisierung (RFC 8785) | **behoben** (V-04) |
| N-01 | **„muss“** — Mandantenkontext an die *Verbindung* statt an die *Transaktion* gebunden | **behoben**: ADR-002 §3, Schema-Kommentare, Zielstruktur |
| N-02 | **„muss“** — Worker beansprucht Läufe ohne Mandantenermittlung | **behoben**: ADR-006 §3, Schema, Worker-Kette in den Diagrammen und der Zielstruktur |
| N-08 | **„soll“** — Schema-Kommentare behaupten den Stand vor F-01/F-02 | **behoben** |
| S-04…S-23 | 18 weitere Vertragsdefekte (Soll/Optional) | **alle 22 Vertragsdefekte behoben** (V-01…V-22) |
| E-01…E-09 | offene Entscheidungen | **alle 9 entschieden und umgesetzt** (E-06 in Schritt 1, E-07 in Schritt 5) |
| R-01…R-05 | beim Beheben gefundene Befunde des Rücksprungs | **R-03, R-04, R-05 in Schritt 3 behoben**; **R-01 bleibt offen** (Fachbereich, Gate 2) — siehe §8 und §9 |

## 9b. Befunde aus dem **zweiten** Stresstest (2026-10-21) — alle behandelt

Vollständig in `.agent/artifacts/stress-test.md` (Commit `c9c8038`); die
Behandlungsbilanz je Defekt in `.agent/artifacts/ruecksprung-bilanz.md`.

| # | Befund | Behandlung (**Rücksprung 2** in Schritt 2) |
|---|---|---|
| **V-23** | **„muss“** — der Abdeckungseintrag zu AC-022-3 nennt den „Connection-Initializer“ | **behoben**: der Eintrag nennt jetzt die entschiedene Semantik — der Mandant wird **innerhalb der Transaktion**, die auch liest, gesetzt (transaktionslokal, **nicht** verbindungsgebunden) |
| **V-24** | **„soll“** — deutsche Worte in Code-Formatierung (`aktiv`, `pausiert`) statt der Werte | **behoben**: `active` / `paused` in Code-Formatierung, die deutsche Übersetzung als Klartext |
| **V-25** | **„soll“** — `DeliveryRun.organisation_id` legt ein mandantenübergreifendes Protokoll nahe | **behoben**: Variante „nur in mandantenübergreifenden Antworten gefüllt; die Protokollliste ist **mandantengefiltert**“ (E-10) — im Feld benannt |
| **N-13** | **„muss“** — markierte Versuche bleiben sichtbar und zählen in den Kennzahlen | **behoben (Schema)**: die Policy auf `delivery_attempts` bezieht `delivery_runs.deleted_at IS NULL` ein — **eine** Wahrheit, keine zweite Spalte |
| **N-14** | **„muss“** — die Totenlisten-Funktion ist fail-open (optionaler Statusfilter) | **behoben (Schema)**: der Status ist **fest verdrahtet** (`AND d.status = 'abandoned'`), die Funktion hat **keinen** Statusparameter mehr; die alte Signatur wird per `DROP FUNCTION` entfernt |
| **N-15** | **„muss“** — die Replay-Historie verschwindet beim Purge des Vorgängers | **behoben (Schema)**: `replay_of` **ohne Fremdschlüssel** (E-11), `UNIQUE (replay_of)` bleibt |
| **N-16** | **„soll“** — logische Löschung ist der Applikationsrolle nicht möglich | **behoben (Dokumentation)**: ADR-002 §7, Zielstruktur (Konvention „Logische Löschung“), Schema-Kopfkommentar — **gegen PostgreSQL 16 belegt** |
| **N-17** | **„soll“** — die mandantenübergreifenden Listen haben keinen stützenden Index | **behoben (Schema)**: zwei partielle Indizes, passend zur **tatsächlichen** Sortierung der Funktionen — **gegen PostgreSQL 16 belegt** (Index-Scan statt Seq Scan + Sort) |
| **N-18** | **„soll“** — ADR-002 nennt die neue Grenze (`app.cross_tenant`) nicht | **behoben**: neuer Absatz „Grenze (ehrlich benannt)“; die Härtung ist als **offene Entscheidung E-12 (Gate 2)** vermerkt |
| **N-19** | **„soll“** — ADR-015 verspricht ein mandantenübergreifendes Protokoll, das es nicht gibt | **behoben**: Rollenmatrix korrigiert — die mandantenübergreifende Sicht gilt **nur** für Endpunktliste, Totenliste und Aufbewahrung (E-10) |
| **N-20** | **„soll“** — der konkurrierende zweite Replay wartet auf die Sperre des ersten | **behoben (ADR)**: **23505 ⇒ `delivery_state_conflict`** und `lock_timeout`/`statement_timeout` **kürzer** als das HTTP-Timeout — als Vorgabe für Schritt 6 |
| **E-13** | Karenzzeit: Schema-Verhalten explizit machen, **keinen** Wert erfinden | **behoben**: Schema-Kommentar, ADR-012 §6, Zielstruktur — ohne gesetzten Wert Purge im **nächsten** Durchlauf; der Vertrag behauptet **weiterhin keinen** Wert |
| **V-26** | **neu** — `Endpoint.organisation_id`: Beschreibung und zwei Beispiele widersprechen sich | **dokumentiert, nicht entschieden** (§ 8) — gehört in die Delta-Prüfung |

## 9c. Befunde aus dem **Rücksprung 4** (2026-10-21) — V-27 und Abschluss-Sweep

Vollständig in `.agent/artifacts/ruecksprung-bilanz.md` § 10; die Behandlungsbilanz
je Widerspruch dort, der Gegenstand ist ausschließlich der **Vertrag** —
`docs/adr/`, `docs/architecture/` und das **Schema** blieben unberührt.

| # | Befund | Behandlung (**Rücksprung 4** in Schritt 2) |
|---|---|---|
| **V-27** | **„muss“** — der `service_admin` bekommt IDs fremder Mandanten, deren Einzelabruf 404 ergibt | **entschieden und umgesetzt** (10 Vertragsstellen): die mandantenübergreifende Sicht gilt **nur** für Endpunktliste, Totenliste und Aufbewahrung (E-01); der Einzelabruf einer fremden Ressource ergibt **auch für `service_admin`** 404 (D-02); ein vierter mandantenübergreifender Leseweg wird **nicht** gebaut. **Das Schema bleibt bei drei Eigentümerfunktionen** (ADR-002 §7) — die Entscheidung und die Architektur decken sich |
| **V-28** | **„muss“** — `createEndpoint` kündigte 409 `idempotency_conflict` an, kann aber keinen Idempotenzschlüssel annehmen | **behoben**: 409 entfernt (D-05/FR-011 betreffen Ereignisse; das Schema erzwingt keine Eindeutigkeit auf `endpoints`) |
| **V-29** | **„muss“** — `replayDelivery` und `enforceRetention` haben einen Request-Body, aber keine 400 | **behoben**: 400 `MalformedRequest` ergänzt (vertragsweite Fehlersemantik) |
| **V-30** | **„muss“** — der Cursor im Beispiel war kein gültiges Base64 und trug den falschen Sortierschlüssel | **behoben**: `created_at` + UUID-Tie-Breaker (ADR-017 §4), korrekt kodiert |
| **V-31** | **„muss“** — das Rotations-Secret im Beispiel verletzte `minLength: 16` | **behoben** |
| **V-32** | **„muss“** — dieselbe `delivery_run_id` in drei Beispielen in drei Zuständen | **behoben**: Beispiele entkoppelt |
| **V-33…V-35** | **„soll“** — `violations` nur bei `validation_failed`; `field` als JSON-Pointer; `failed` im Enum unbeschrieben | **behoben** (Beschreibung an Schema und Beispiele angeglichen; `failed` nach ADR-008) |
| **V-36** | **„muss“** — `X-EchoBox-Rejection` war optional, ADR-009 macht den Header zum festen Teil des Signals | **behoben**: `required: true` |
| **V-37/V-38** | **„soll“** — `Event.deleted_at` versprach einen gesetzten Wert; `payload: null` widersprach `nullable: false` | **behoben** (Beschreibungen an ADR-012 §3 bzw. an das Schema angeglichen; das Schema bleibt unverändert) |
| **V-39…V-44** | **neu** — Rollen-/Mandantenprüfung beim Replay, Zeitraumfilter, `ReadinessStatus`, `EndpointMetrics.rate_limit`, `Event.deleted_at`, ADR-015 | **dokumentiert, nicht entschieden** (§ 8) |

## 9d. Befunde aus dem **Rücksprung 5** (2026-10-21) — V-39…V-44, alle behandelt

Vollständig in `.agent/artifacts/ruecksprung-bilanz.md` § 11; die Behandlungsbilanz
je Befund dort. Der Gegenstand ist der **Vertrag** und **ADR-015/ADR-017** —
`docs/architecture/` und das **Schema** blieben unberührt.

| # | Befund | Behandlung (**Rücksprung 5** in Schritt 2) |
|---|---|---|
| **V-39** | **„muss“** — Reihenfolge der Rollen- und der Mandantenprüfung beim Replay (403 vs. 404) | **entschieden und umgesetzt** (10 Vertrags- + 2 ADR-Stellen): **erst der Mandant, dann die Rolle** — ein Zustelllauf **fremden** Mandanten ⇒ **404** (auch für `developer`/`service_admin`), ein Lauf im **eigenen** Mandanten **ohne** `receiver_admin` ⇒ **403**. ADR-015 §3 sagt es jetzt ausdrücklich |
| **V-40** | **„muss“** — der Zeitraumfilter des Zustellprotokolls war an keinen Zeitstempel gebunden | **entschieden und umgesetzt** (5 Vertrags- + 1 ADR-Stelle): `created_at` (der Sortierschlüssel, ADR-017 §4); die Totenliste bleibt bei `abandoned_at` — beide Aussagen nebeneinander, **je benannt** |
| **V-41** | **„sollte“** — `ReadinessStatus` führte `not_ready`/`unavailable`, die der Vertrag nie erzeugt | **entschieden und umgesetzt** (4 Vertragsstellen): Enum auf `ready`/`ok` geschärft; der nicht bereite Zustand ⇒ **503 mit `Problem`** (ADR-015 §4) |
| **V-42** | **„sollte“** — `EndpointMetrics.rate_limit` optional, aber als vorhanden beschrieben | **entschieden und umgesetzt** (3 Vertragsstellen): `rate_limit` steht in `required` — die Zählung läuft unabhängig vom Grenzwert (AC-018-4) |
| **V-43** | **„sollte“** — `Event.deleted_at` war ein totes Feld (immer `null`) | **entschieden und umgesetzt** (3 Vertragsstellen): **Feld entfernt**. Die Spalte `app.events.deleted_at` bleibt **intern** (ADR-012 §3); die API gibt den Marker nicht aus |
| **V-44** | **„muss“** — ADR-015 §1/§2 widersprachen V-26/V-27 | **entschieden und umgesetzt** (6 ADR-015- + 2 Index-Stellen): Rollenmatrix, `organisation_id` und die Prüfreihenfolge sind an den entschiedenen Stand gebracht |
| **V-45…V-47** | **neu** — AC-013-3 ohne Mandantbezug; optionale, aber als vorhanden beschriebene Felder; vier Operationen ohne Fremdmandanten-404 im Text | **dokumentiert, nicht entschieden** (§ 8) — sie **blockieren den Freeze nicht** |

## 10. Geprüft

| Prüfung | Ergebnis |
|---|---|
| OpenAPI: YAML gültig, 17 Operationen, alle `$ref` auflösbar, kein ungenutztes Schema | **ok** (177 Verweise) |
| OpenAPI: 23 Stories, 101 AKs, 24 Anforderungen auffindbar | **ok** |
| OpenAPI: jedes Beispiel gegen sein Schema geprüft (Pflichtfelder) | **ok** — 0 Abweichungen |
| OpenAPI: jedes Beispiel **nachgerechnet** (Bytes, Quoten, Zeiten, Längen, Base64) | **ok** — 0 Abweichungen (Rücksprung 4) |
| OpenAPI: Fehlercode-Vollständigkeit (Body ⇒ 400, authentifiziert ⇒ 429, Adresse ⇒ 404) | **ok** (Rücksprung 4, V-29) |
| OpenAPI: `x-delivery-protocol` gegen ADR-004/005/008/009 (Signatur, Kennungen, Retry-Matrix, 3xx, Ablehnungssignal) | **ok** (Rücksprung 4, V-36) |
| OpenAPI: je Operation `operationId`, `x-ac-id`, `x-requirement-ids` | **ok** |
| Schema: angewendet gegen **PostgreSQL 16.15** (Rücksprung 2) | **ok** (10 Tabellen, 10 Policies, 7 Funktionen, 3 `SECURITY DEFINER`-Funktionen, 23 ausdrücklich angelegte Indizes) |
| Schema: RLS-Verhalten (ohne Kontext 0 Zeilen, je Mandant nur eigene Zeilen, keine Rolle mit `BYPASSRLS`) | **ok** |
| Schema: mandantenübergreifende Sicht (fail-closed ohne Freigabe, 2 Mandanten über die Funktionen, Worker ohne Recht, kein Klartext/Secret in der Projektion) | **ok** — belegt in `.agent/artifacts/ruecksprung-bilanz.md` § 5.1 und § 5.6 |
| Schema: **N-13** — markierte Versuche unsichtbar, zählen nicht mehr in den Kennzahlen | **ok** (1 → 0; `marked_attempts = 1`) |
| Schema: **N-14** — die Totenliste liefert ausschließlich `abandoned`, auch ohne Statusparameter | **ok** (2 Zeilen, 0 mit fremdem Status, 0 ohne `abandoned_at`) |
| Schema: **N-15** — `replay_of` ohne Fremdschlüssel, `UNIQUE (replay_of)` wirksam, Historie überlebt den Purge | **ok** (23505; Verweis bleibt nach dem Löschen des Vorgängers) |
| Schema: **N-16** — logische Löschung durch die Applikationsrolle nicht möglich | **ok** (`new row violates row-level security policy`) |
| Schema: **N-17** — die Cross-Tenant-Listen nutzen die neuen Teilindizes | **ok** (Index Only Scan / Index Scan, kein `Sort`) |
| Schema: `UNIQUE (replay_of)` — zweites Replay ⇒ `unique_violation` | **ok** |
| Schema-Spiegel: `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** (in diesem Schritt **beide** Dateien geändert — im **Rücksprung 5** **keine** Schema-Änderung) |
| `python3 .agent/tools/traceability_check.py` | **grün** |
| `knowledge/openapi.yaml` ist ein Verweis | **ok** |
| **Rücksprung 5**: YAML gültig, 17 Operationen, 177 `$ref` auflösbar, kein ungenutztes Objekt | **ok** |
| **Rücksprung 5**: Beispiele gegen ihr Schema (Pflichtfelder, `null` bei nicht-`nullable`, unbekannte Felder, Enum, Längen, Bereiche) | **0 Abweichungen** |
| **Rücksprung 5**: `not_ready` / Enum `unavailable` im Vertrag | **0 Treffer** — nur noch `service_unavailable` |
| **Rücksprung 5**: `deleted_at` in einem **Antwortmodell** | **keiner** — 3 Treffer = Markiervorgang der Aufbewahrung |

## 11. Artefakte

| Pfad | Inhalt |
|---|---|
| `docs/api/openapi.yaml` | Der Vertrag |
| `docs/architecture/architecture.md` | Erzählung der Architektur |
| `docs/architecture/domain_model.md` | Entitäten, Zustandsautomat, Invarianten |
| `docs/architecture/GLOSSARY.md` | Glossar — **einziger Ort** je Fachbegriff |
| `docs/architecture/folder-structure.md` | Zielstruktur für `src/` |
| `docs/architecture/schema.sql` | Kopie des kanonischen Schemas |
| `docs/architecture/diagrams/*.mmd` | **4 C4-Diagramme + 1 Sequenzdiagramm = 5 Dateien** |
| `docs/adr/ADR-*.md`, `docs/adr/README.md` | 17 ADRs + Index (ADR-014 = Verweis) |
| `.agent/knowledge/schema.sql` | Kanonisches Schema |
| `.agent/knowledge/stack.md` | Stack-Entscheidung |
| `.agent/knowledge/openapi.yaml` | Verweis auf den Vertrag |
| `.agent/artifacts/folder-structure.md` | Zielstruktur — **Kurzfassung** (Module; die Dateiliste steht in `docs/architecture/folder-structure.md`) |
| `.agent/artifacts/api-review.md` | Vertragsreview |
| `.agent/artifacts/adr-index.md` | **ADR-Gate-Vorlage** (bestätigt / offen) — der Index selbst steht in `docs/adr/README.md` |
| `.agent/artifacts/simplify-report.md` | **Reduktionsbericht (Schritt 3)** |
| `.agent/artifacts/stress-test.md` | **Stresstest-Bericht (Schritt 4)** — 22 Vertragsdefekte (erste Runde), 7 „muss“-Befunde, offene Entscheidungen |
| `.agent/artifacts/ruecksprung-bilanz.md` | **Rücksprung-Bilanz (Schritt 2, Rücksprung 5)** — V-23…V-27, V-28…V-38 (Abschluss-Sweep), V-39…V-44 (entschieden und umgesetzt), V-45…V-47 (offen), N-13…N-20, E-10…E-13, PostgreSQL-16-Nachweis |
