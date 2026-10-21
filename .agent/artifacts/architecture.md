# Architektur — EchoBox (kompakter Katalog)

> Schritt 2 (**Rücksprung** aus Schritt 4) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Erzählung: `docs/architecture/architecture.md` · Vertrag: `docs/api/openapi.yaml`
> Schema: `.agent/knowledge/schema.sql` (kanonisch) · **Entscheidungen: `docs/adr/`**
> Reduktionsbericht: `.agent/artifacts/simplify-report.md`
> **Nachweis des Rücksprungs: `.agent/artifacts/ruecksprung-bilanz.md`**
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
| ADR-002 | **Mandantentrennung per Row-Level-Security** auf jeder fachlichen Tabelle; `app.set_tenant()` **transaktionsgebunden** (in der Transaktion, die auch liest); **fail-closed**; keine Rolle mit `BYPASSRLS`; **§7:** die mandantenübergreifende Sicht des Service-Admins über **drei schmale `SECURITY DEFINER`-Eigentümerfunktionen** (kein `BYPASSRLS`, keine Mandantenliste) | Angenommen (im Rücksprung aus Schritt 4 nachgezogen) |
| ADR-003 | **AES-256-GCM (AEAD)** für Nutzlast und Endpunkt-Secret; Schlüssel aus dem Secret-Store, **außerhalb** der DB; entschlüsselt **nur** zur Zustellung; Suche nur über Metadaten | Angenommen (Architekturseite von O-04) |
| ADR-004 | **HMAC-SHA256**, Header **`X-EchoBox-Signature`** = `v1=<hex>`; Eingabe `<timestamp>.<payload_bytes>`; **eingefroren** | Angenommen |
| ADR-005 | **Drei Kennungen**: `X-EchoBox-Event-Id`, `X-EchoBox-Delivery-Run-Id`, `X-EchoBox-Attempt-Id`; Body = exakt die Nutzlastbytes | Angenommen |
| ADR-006 | Zustelllauf **ist** die Warteschlange (transaktionales Outbox); `FOR UPDATE SKIP LOCKED` + **Lease**; getrennte Rollen `api` und `worker`; Worker arbeitet **mandantenweise**; Replay **einmal** (`UNIQUE (replay_of)`) | Angenommen (im Rücksprung nachgezogen) |
| ADR-007 | Idempotenz je **(Mandant, Schlüssel)**; gleiche Nutzlast ⇒ 202 mit erster ID; abweichende Nutzlast ⇒ **409**; Vergleich über gezielten HMAC | Angenommen |
| ADR-008 | Wiederholung bei 5xx/408/429/Netzwerkfehler; **3xx ⇒ kein Wiederholen**; Backoff mit **Verdopplung + voller Streuung**; **8 Versuche, Basis 1 min, ×2, Deckel 60 min, ± 20 %**; **Gesamtfenster ≈ 2 h**; **Zeitlimit je Versuch 5 s + 10 s = 15 s** | Angenommen (**bestätigt: F-04**, O-06/O-07) — enthält die frühere ADR-014 |
| ADR-009 | **Ablehnungssignal: HTTP 410** + `X-EchoBox-Rejection: permanent` + festgeschriebener JSON-Body; übrige 4xx ⇒ ebenfalls Ende, aber `rejection_reason = client_error`; **3xx ⇒ ebenfalls Ende, Redirects werden nicht gefolgt** | Angenommen (im Rücksprung nachgezogen) |
| ADR-010 | Endpunkt-Begrenzung: Zähler **immer** aktiv, Vorgabewert **60 Versuche/Endpunkt/Minute**, Strategie **`delay`**; **kein** Ereignisverlust; **genau zwei Zustände** (`NULL` = kein Override ⇒ 60, `0` = abgeschaltet) — die Spalte `enabled` ist entfallen | Angenommen (**bestätigt: F-01**) |
| ADR-011 | Erfolgsquote = erfolgreiche Versuche / alle Versuche; p95 von Annahme bis erfolgreicher Antwort; `data_available: false` statt 0 % | Angenommen |
| ADR-012 | Aufbewahrung: **zwei** Fristen, Vorgabe **je 30 Tage**, Fristbeginn **je Datensatz** = **letzter Versuch**; markieren (`deleted_at`) dann Purge nach der **Karenzzeit**; **Kaskade** (Ereignis nimmt Läufe und Versuche mit); `dry_run` als Default | Angenommen (**bestätigt: F-02**) |
| ADR-013 | Ein Image, zwei Rollen; `echobox.tenant.id` bindet eine Instanz an einen Mandanten; non-root, keine Secrets im Image; Staging/Produktion getrennt | Angenommen |
| ADR-014 | — | **Zusammengeführt in ADR-008** (Verweisdatei) |
| ADR-015 | Ein Fehlerschema (`Problem`); 422 bei Mandantenparameter, 404 bei fremden Ressourcen, 403 bei Rollenverstoß; Rollenmatrix (Service-Admin mandantenübergreifend über die Eigentümerfunktionen des Schemas); Replay **nur** für den Empfänger-Admin und **nur einmal** | Angenommen (**bestätigt: F-03**) |
| ADR-016 | Ein Ort pro Wahrheit: Vertrag **einmal**, Schema kanonisch + byte-identische Kopie; Freeze nach Schritt 4 | Angenommen |
| ADR-017 | Protokollsuche **nur** über Metadaten; Denormalisierung von `event_type`/`endpoint_id`; Cursor-Paginierung ohne `total` | Angenommen |
| ADR-018 | Next.js 15 App Router; Barrierefreiheit als **Bauregel**: native Bedienelemente, echte `<table>`, Status nie nur über Farbe, axe als CI-Gate | Angenommen |

**Bestätigte Festlegungen (Gate 1, 2026-10-21):** F-01 (Rate-Limit), F-02
(Aufbewahrung), F-03 (Replay-Berechtigung), F-04 (Versuche/Backoff). Alle vier
sind **Vorgabewerte in der Konfiguration** — kein Wert ist im Code verdrahtet.

## 3. Komponenten

| Schicht | Komponenten | Daten |
|---|---|---|
| **Oberfläche** | Endpunkte, Zustellprotokoll, Tote Zustellungen | — |
| **API-Layer** | Security-Adapter, Mandantenkontext, 5 Controller, 8 Fachservices, Crypto-Service, Fehlerabbildung | `endpoints`, `events`, `delivery_runs`, `delivery_attempts`, `audit_log` |
| **Mandantenübergreifende Sicht** (Ausnahme, ADR-002 §7) | `app.list_endpoints_cross_tenant`, `app.list_dead_deliveries_cross_tenant`, `app.enforce_retention_cross_tenant` — `SECURITY DEFINER`, `search_path` verdrahtet, `EXECUTE` nur für `echobox_app`, fail-closed ohne `app.cross_tenant = 'service_admin'`, **nur** Projektionen (kein Klartext, keine Chiffre, keine Secrets) | dieselben Tabellen, ohne RLS (Eigner) |
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
| R-13 Mandantenleck über die mandantenübergreifende Sicht | Drei schmale `SECURITY DEFINER`-Eigentümerfunktionen, `REVOKE` von `PUBLIC`, `EXECUTE` nur für die Applikationsrolle, fail-closed, **keine** Nutzlast und **keine** Secrets in der Projektion — **gegen echtes PostgreSQL 16 belegt** | ADR-002 §7 |
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

| Epic | Stories | Wo im Vertrag |
|---|---|---|
| E1 | US-001…US-004 | `POST/GET/PATCH /endpoints`, `pause`, `resume`, `secret-rotation` |
| E2 | US-005…US-008 | `POST /events` (202/409/413/422), `GET /events/{id}` |
| E3 | US-009…US-014 | `x-delivery-protocol` (Signatur, Kennungen, Retry, Ablehnung), `POST /deliveries/{id}/replay` |
| E4 | US-015…US-018 | `GET /deliveries`, `GET /deliveries/{id}`, `GET /dead-deliveries`, `GET /endpoints/{id}/metrics`, `POST /retention/enforcement` |
| E5 | US-019…US-021 | Datenquellen der drei Seiten; ADR-018 |
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

## 9. Befunde dieses Schritts

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

## 10. Geprüft

| Prüfung | Ergebnis |
|---|---|
| OpenAPI: YAML gültig, 17 Operationen, alle `$ref` auflösbar, kein ungenutztes Schema | **ok** |
| OpenAPI: 23 Stories, 101 AKs, 24 Anforderungen auffindbar | **ok** |
| Schema: angewendet gegen **PostgreSQL 16** (Rücksprung aus Schritt 4) | **ok** (10 Tabellen, 10 Policies, 33 Indizes, 3 `SECURITY DEFINER`-Funktionen) |
| Schema: RLS-Verhalten (ohne Kontext 0 Zeilen, je Mandant nur eigene Zeilen) | **ok** |
| Schema: mandantenübergreifende Sicht (fail-closed ohne Freigabe, 2 Mandanten über die Funktionen, Worker ohne Recht, kein Klartext/Secret in der Projektion) | **ok** — belegt in `.agent/artifacts/ruecksprung-bilanz.md` §4 |
| Schema: `UNIQUE (replay_of)` — zweites Replay ⇒ `unique_violation` | **ok** |
| Schema-Spiegel: `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | **leer** |
| `python3 .agent/tools/traceability_check.py` | **grün** |
| `knowledge/openapi.yaml` ist ein Verweis | **ok** |

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
| `.agent/artifacts/folder-structure.md` | Zielstruktur (Kurzfassung) |
| `.agent/artifacts/api-review.md` | Vertragsreview |
| `.agent/artifacts/adr-index.md` | ADR-Index (Gate-Vorlage) |
| `.agent/artifacts/simplify-report.md` | **Reduktionsbericht (Schritt 3)** |
| `.agent/artifacts/stress-test.md` | **Stresstest-Bericht (Schritt 4)** — 22 Vertragsdefekte, 7 „muss“-Befunde, offene Entscheidungen |
| `.agent/artifacts/ruecksprung-bilanz.md` | **Rücksprung-Bilanz (Schritt 2)** — Defektbilanz V-01…V-22, S-01, N-01/N-02/N-08, E-01…E-09, PostgreSQL-Nachweis |
