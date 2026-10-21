# Architektur — EchoBox (Ereignis-Briefkasten mit Zustellgarantie)

> Schritt 3 (Simplify & Reduktion) **nach dem Rücksprung aus Schritt 4**
> · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Quellen: `docs/Anforderung.md`, `docs/requirements/PRD.md`, `docs/requirements/traceability_matrix.json`
> **Eine Erzählung, keine Doppelführung:** Die Entscheidungen stehen in
> `docs/adr/`, der Vertrag in `docs/api/openapi.yaml`, die Fachbegriffe in
> `docs/architecture/GLOSSARY.md`, das Datenmodell in
> `docs/architecture/domain_model.md`. Dieses Dokument **erzählt** den
> Zusammenhang und **verweist** auf die Wahrheit; es wiederholt sie nicht.

---

## 1. Überblick

EchoBox nimmt Ereignisse entgegen und liefert sie per HTTP an registrierte
Empfänger-URLs weiter. Der Dienst ist **ein Zustell-Wrapper für ausgehende
Webhooks** — bewusst **kein** Message-Broker und **kein** Event-Store. Es gibt
keine zweite Zustandshaltung, die gegen eine erste konsistent gehalten werden
müsste.

Der Leitsatz der Architektur ist der Leitsatz des PRD:

> **Nichts verschwindet still.** Jedes eingelieferte Ereignis ist über seinen
> gesamten Weg sichtbar — ob zugestellt, ob verworfen, ob tot.

Daraus folgen drei tragende Entscheidungen:

| # | Entscheidung | Folge | Wo |
|---|---|---|---|
| 1 | **Annahme und Zustellung sind getrennt** (D-10) | Der Annahme-Request antwortet in p95 < 200 ms (NFR-002), unabhängig davon, wie lange der Empfänger braucht. | ADR-006 |
| 2 | **Der Zustelllauf ist die Einheit der Sichtbarkeit** | Jedes Ereignis erzeugt je abonniertem Endpunkt genau einen Zustelllauf mit vollständigem Versuchsprotokoll. Auch der Sonderfall „kein Abnehmer" wird als Zustand sichtbar (D-04). | ADR-006, ADR-015 |
| 3 | **Der Mandant steckt in der Identität und wird zusätzlich in der Datenbank erzwungen** | D-01 im Vertrag, RLS im Schema. Ein vergessener Anwendungsfilter zeigt nichts. | ADR-002, ADR-015 |

### 1.1 Systemgrenze

| Inside | Outside |
|---|---|
| Endpunkte, Ereignisse, Zustellläufe, Versuche, Zähler, Audit | Benutzer-, Rollen- und Mandantenverwaltung (Identität kommt von außen) |
| Zustellung an registrierte HTTP-URLs | Zustellung an Nicht-HTTP-Ziele (E-Mail, AMQP, Kafka) |
| Zustellprotokoll und Totenliste | Ereignis-Archiv mit Replay an beliebige Empfänger |
| Einmalige Zustellung eines Ereignisses an einen Endpunkt | Historisierung über den Dienst hinaus |

Details: `docs/requirements/PRD.md` Abschnitt 5.3 (Won't have).

---

## 2. Schichten und Komponenten

```
┌──────────────────────────────────────────────────────────────────────┐
│ Oberfläche (React/TypeScript)                                       │
│ Endpunkte · Zustellprotokoll · Tote Zustellungen                    │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ REST/JSON, JWT (Mandant aus dem Token)
┌───────────────────────────────▼──────────────────────────────────────┐
│ API-Layer (Spring Boot)                                             │
│ Security-Adapter · Mandantenkontext · Controller · Fachservices     │
│ Crypto-Service · Fehlerabbildung (einheitliches Problem-Schema)    │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ JDBC, transaktionslokaler Mandantenkontext
┌───────────────────────────────▼──────────────────────────────────────┐
│ Datenbank (PostgreSQL 16, Schema app, RLS)                          │
│ endpoints · events · delivery_runs · delivery_attempts · Zähler    │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ Warteschlange in delivery_runs
┌───────────────────────────────▼──────────────────────────────────────┐
│ Zustell-Worker (Hintergrundprozess)                                 │
│ Scheduler · Claimer (Lease) · Dispatcher · Signatur · Backoff       │
│ Endpunkt-Begrenzung · Protokollschreiber · Lease-Rettung            │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS, signiert, at-least-once
┌───────────────────────────────▼──────────────────────────────────────┐
│ Empfangendes System                                                 │
└──────────────────────────────────────────────────────────────────────┘
```

C4-Diagramme: `diagrams/01-context.mmd` (Kontext), `02-container.mmd`
(Container), `03-component-api.mmd` und `04-component-worker.mmd`
(Komponenten), `05-sequence-delivery.mmd` (Sequenz).

### 2.1 Fachmodule

| Modul | Verantwortung | Stories |
|---|---|---|
| `endpoint` | Registrierung, Pause/Reaktivierung, Secret-Rotation, rollenabhängige Sicht | US-001…US-004, US-019 |
| `event` | Annahme, Idempotenz, Verschlüsselung im Ruhezustand, vorverschlüsselte Einlieferung | US-005…US-008 |
| `delivery` | Zustelllauf, Versuch, Backoff, Streuung, Aufgabe, Replay, Ablehnung, Endpunkt-Begrenzung | US-009…US-014, US-018 |
| `crypto` | Signatur, Payload-Verschlüsselung, Secret-Verwaltung | US-003, US-007, US-009 |
| `protocol` | Zustellprotokoll, tote Zustellungen, Kennzahlen, Aufbewahrung | US-015…US-018 |
| `tenant` | Mandantenkontext, Durchsetzung auf Datenbankebene | US-004, US-022 |
| `frontend` | Oberfläche Endpunkte, Protokoll, tote Zustellungen, Barrierefreiheit | US-019…US-021 |

Die Zielstruktur für `src/` steht in `folder-structure.md`; die Dateien und ihre
Zuständigkeiten sind dort je Modul festgeschrieben.

### 2.2 Warum Ports & Adapters (Hexagon)

Der Kern der Anwendung — Zustellzustandsautomat, Backoff, Signatur,
Mandantenregeln — enthält **keine** Framework-Typen. Er wird über Ports
angetrieben:

| Port | Adapter | Warum variabel |
|---|---|---|
| `DeliveryPort` | HTTP-Adapter (Java `HttpClient`) | Testbar ohne Netz; der Empfänger wird simuliert. |
| `SecretPort` | Secret-Store-Adapter | Schlüssel kommen von außen (NFR-004). |
| `ClockPort` | Systemuhr | Backoff und Zeitlimits werden ohne Warten prüfbar (AC-011-5, AC-023-4). |
| `DeliveryRepository` / `EventRepository` / `EndpointRepository` | JDBC-Adapter (JdbcTemplate, **kein** dynamisch zusammengesetztes SQL) | RLS braucht den Transaktionskontext; JPA würde den Mandantenkontext schlechter sichtbar machen. |
| `IdGenerator` | UUID-v4/UUID-v7 | Kennungen sind Teil des öffentlichen Vertrags (D-06). |

**Persistenzentscheidung:** Spring Data **JDBC** (JdbcTemplate), nicht
JPA/Hibernate — die Begründung steht in ADR-001 und `.agent/knowledge/stack.md`.

---

## 3. Datenfluss: Ereignis → Zustellung

```
Entwickler
   │  POST /api/v1/events   (Typ, Nutzlast, Idempotenzschlüssel)
   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. Annahme (Request-Thread, Ziele p95 < 200 ms)                  │
│    • JWT prüfen → Mandant organisation_id (D-01)                │
│    • Mandantenparameter im Body → 422                            │
│    • Nutzlastbytes messen → > 1.048.576 B ⇒ 413 (NFR-001)       │
│    • Pflichtfelder prüfen → 422                                 │
│    • Idempotenzschlüssel (Mandant, Schlüssel):                  │
│        vorhanden + gleicher Fingerprint ⇒ 202 mit erster ID     │
│        vorhanden + anderer Fingerprint   ⇒ 409 (D-05)           │
│    • Nutzlast verschlüsseln (AEAD) — Klartext nie gespeichert   │
│    • Fingerprint bilden (HMAC über kanonische Bytes)           │
│    • INSERT events (Chiffre) + INSERT delivery_runs je Endpunkt │
│      → EINE Transaktion (transaktionales Outbox)                │
│    • 202 Accepted + event_id                                    │
└─────────────────────────────────────────────────────────────────┘
   │  Antwort ist raus — der Aufrufer ist frei (NFR-002)
   ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Zustellung (Hintergrundprozess, nie im Request-Thread)        │
│    Scheduler → Claimer (FOR UPDATE SKIP LOCKED + Lease)          │
│      → Nutzlast entschlüsseln (oder Chiffre durchreichen)       │
│      → Signatur bilden: HMAC-SHA256(secret, timestamp.payload)  │
│      → POST an die Ziel-URL mit den drei Zustell-Kennungen      │
│      → Antwort auswerten → Zustand + Versuch protokollieren     │
│      → Backoff mit Verdopplung und Streuung, falls wiederholt   │
└─────────────────────────────────────────────────────────────────┘
```

Ablauf in Bildern: `diagrams/05-sequence-delivery.mmd`.

### 3.1 Der ausgehende Zustellvertrag

Der Vertrag gegenüber dem Empfänger ist in `docs/api/openapi.yaml` unter
`x-delivery-protocol` **vollständig** beschrieben und eingefroren
(FR-020, D-07, D-08). Er besteht aus drei Teilen:

| Teil | Festlegung | ADR |
|---|---|---|
| **Signatur** | `HMAC-SHA256`, Header `X-EchoBox-Signature`, Wert `v1=<hex-lowercase>`; Eingabe `<timestamp>.<payload_bytes>` | ADR-004 |
| **Kennungen** | `X-EchoBox-Event-Id` (fachlicher Schlüssel), `X-EchoBox-Delivery-Run-Id` (Lauf), `X-EchoBox-Attempt-Id` + `-Attempt-Number` (Versuch) | ADR-005 |
| **Ablehnungssignal** | HTTP **410**, Header `X-EchoBox-Rejection: permanent`, Body `{rejection, reason, message?, rejected_event_types?}` | ADR-009 |

Zwei Festlegungen, die man leicht übersieht:

- **Der Request-Body sind die Nutzlastbytes**, keine Hülle. Grund: die
  zugestellten Bytes müssen byteidentisch mit dem sein, **was das Ereignis
  trägt** (AC-007-3) — bei Einlieferung als `payload` die **kanonisch
  materialisierten** Bytes (RFC 8785), bei `payload_base64` die exakt
  gelieferten. Die Signatur gilt über genau diese Bytes.
- **at-least-once.** Der Empfänger erkennt Dubletten an gleicher Ereignis-ID
  und gleicher Zustelllauf-ID bei unterschiedlicher Versuchs-ID.

### 3.2 Der Wiederholungsrahmen

| Regel | Vorgabe | Wo |
|---|---|---|
| Wiederholen bei | 5xx, 408, 429, Netzwerkfehler | ADR-008 |
| Aufgeben bei | 2xx (fertig), 410 und übrige 4xx sowie **3xx** (`rejected`, `client_error`; Redirects werden **nicht** gefolgt) | ADR-008, ADR-009 |
| Versuche | **8** (Vorgabe, F-04), je Lauf eingefroren | ADR-008 |
| Abstand | Basis **1 min**, ×2, Deckel **60 min**, Streuung **± 20 %** (F-04) | ADR-008 |
| Gesamtfenster | **≈ 2 Stunden** bis zum letzten Versuch (7 Wartezeiten = 123 min, ± 20 % Streuung) | ADR-008 |
| Zeitlimit je Versuch | 5 s Verbindung + 10 s Lese = 15 s < 30 s; ein Timeout ist ein Fehlversuch | ADR-008 |
| Replay | **einmal** je aufgegebenem Zustelllauf; danach 409 (auf Datenbankebene erzwiesen) | ADR-006, ADR-015 |

---

## 4. Mandant, Schutz, Betrieb

Drei Fragen, die jeweils **eine** Entscheidung haben — Details und
Konsequenzen stehen in der genannten ADR:

| Thema | Festlegung | ADR |
|---|---|---|
| **Mandantentrennung** | Drei Ebenen, die sichern: Vertrag (Mandant aus dem JWT, Fremdes ⇒ 404), Anwendung (jede Abfrage trägt den Mandanten), **Datenbank** (RLS auf jeder fachlichen Tabelle, `fail-closed`, keine Rolle mit `BYPASSRLS`) | ADR-002 |
| **Mandantenübergreifende Sicht** | Der `service_admin` sieht alle Mandanten — über **drei schmale `SECURITY DEFINER`-Eigentümerfunktionen** des Schemas, **nicht** über eine Rolle mit `BYPASSRLS`: nur die benötigte Projektion (kein Nutzlastklartext, keine Chiffre, keine Secrets), `fail-closed` ohne die Freigabe, nur der Applikationsrolle freigegeben. Der Cursor dieser Listen läuft **über die Mandantgrenzen hinweg** — eine globale Reihenfolge, keine Seite je Mandant (Vertrag, Abschnitt „Pagination“). | ADR-002 §7, ADR-015 |
| **Mandant und Transaktion** | `app.set_tenant(...)` wird **innerhalb** der Transaktion ausgeführt, die auch liest — nie in einem Servlet-Filter, nie beim Anlegen einer Pool-Verbindung, nie nach einem Commit. Der Wert ist transaktionslokal (`set_config(…, is_local = true)`); steht er nicht in derselben Transaktion, liefert `current_setting(…, true)` `NULL` und **jede** Abfrage liefert 0 Zeilen (fail-closed). Jeder Repository-Zugriff braucht deshalb eine Transaktion, die den Mandanten gesetzt hat. | ADR-002 §2-3 |
| **Mandant im Worker** | Der Worker ermittelt seine Mandanten über `app.tenant_ids_for_worker()` (nur Kennungen, keine Nutzlast) und arbeitet **mandantenweise**: Scheduler → Mandantenermittlung → je Mandant `set_tenant` → beanspruchen; je Mandant **eine eigene Transaktion**. Ohne diesen Schritt liefert die Beanspruchung 0 Zeilen — es würde nichts zugestellt. | ADR-002 §5, ADR-006 §3 |
| **Verschlüsselung im Ruhezustand** | AEAD **AES-256-GCM** für Nutzlast und Endpunkt-Secret; Schlüssel aus dem Secret-Store, **außerhalb** der Datenbank; entschlüsselt **nur** zur Zustellung; Suche ausschließlich über Metadaten | ADR-003, ADR-017 |
| **Idempotenz** | Je **(Mandant, Schlüssel)**; gleiche Nutzlast ⇒ 202 mit erster ID; abweichende Nutzlast ⇒ 409; Vergleich über gezielten HMAC | ADR-007 |
| **Endpunkt-Begrenzung** | **60 Versuche je Endpunkt und Minute**, Strategie `delay` (F-01) — verschieben, nicht verwerfen; je Endpunkt überschreibbar. **Zwei Zustände:** `limit_value IS NULL` = kein Override (⇒ 60), `limit_value = 0` = abgeschaltet; im Vertrag `rate_limit.limit` (immer der wirksame Wert) + `rate_limit.limited` | ADR-010 |
| **Aufbewahrung** | **30 Tage**, getrennt für Ereignisse und Zustellprotokolle, Fristbeginn **je Datensatz** = letzter Versuch (F-02); **Anker** je Datenart, zwei Stufen (markieren, dann Purge nach der **Karenzzeit** — konfigurierbar, **ohne festgelegten Vorgabewert**, R-01), **Kaskade** (das Löschen eines Ereignisses nimmt seine Läufe und Versuche mit), Geltungsbereich **alle Mandanten**; `dry_run` als Default | ADR-012 |
| **Betrieb** | Zwei Rollen `api` und `worker` aus einem Image, nicht-privilegiert, keine Secrets im Image, Staging/Produktion getrennt; `/health` + `/health/ready` ohne Authentifizierung | ADR-013 |
| **Kennzahlen** | Erfolgsquote und p95-Zustelllatenz je Endpunkt; `data_available: false`, wenn keine Daten im Fenster vorliegen | ADR-011 |
| **Oberfläche** | Next.js 15 App Router; Barrierefreiheit als **Bauregel** (native Bedienelemente, echte `<table>`, Status nie nur über Farbe, axe als CI-Gate) | ADR-018 |

---

## 5. Fehlersemantik

**Ein** Schema (`Problem`, RFC-7807-artig) für alle Fehlerantworten, mit dem
maschinenlesbaren Feld `code`; 500-Antworten enthalten niemals interne
Kennungen, SQL-Text, Dateipfade oder Nutzlastanteile. Die vollständige
Zuordnung Status → `code` steht in **ADR-015** und in
`components.responses` des Vertrags — sie wird hier nicht wiederholt.

Die eine Regel, die man merken muss: **403 = „Rolle"**, **404 = „nicht dein
Mandant"**. Fremde Ressourcen werden nie mit 403 beantwortet, damit die
Existenz fremder Objekte nicht preisgegeben wird (D-02).

---

## 6. Abdeckung der 23 Stories

| Epic | Stories | Vertragsabdeckung |
|---|---|---|
| E1 Endpunkte verwalten | US-001…US-004 | `POST/GET/PATCH /endpoints`, `pause`, `resume`, `secret-rotation` |
| E2 Ereignisse einliefern | US-005…US-008 | `POST /events` (202, 409, 413, 422), `GET /events/{id}` |
| E3 Zustellung und Zustellgarantie | US-009…US-014 | `x-delivery-protocol` (Signatur, Kennungen, Retry, Ablehnung), `POST /deliveries/{id}/replay` |
| E4 Protokoll und Betrieb | US-015…US-018 | `GET /deliveries`, `GET /deliveries/{id}`, `GET /dead-deliveries`, `GET /endpoints/{id}/metrics`, `POST /retention/enforcement` |
| E5 Oberfläche | US-019…US-021 | Datenquellen der drei Seiten; Barrierefreiheit in ADR-018 |
| E6 Mandantentrennung und Betrieb | US-022, US-023 | RLS im Schema (ADR-002), `/health` + `/health/ready`, Mandanten-Sharding (ADR-013) |

**Alle 101 Akzeptanzkriterien** sind im Vertrag auffindbar — als `x-ac-id` an
der Operation, als `x-ac-id` am ausgehenden Zustellvertrag oder als Eintrag in
`x-acceptance-coverage`. Die Zuordnung aller 24 Anforderungen steht in
`x-requirement-coverage`. Maschinenprüfung: `.agent/artifacts/api-review.md`.

---

## 7. Offene Punkte

| ID | Thema | Stand | Owner | Gate |
|---|---|---|---|---|
| O-04 | Verschlüsselung im Ruhezustand — **fachliche** Seite: Ist sie Pflicht? | Architekturseite entschieden (ADR-003, ADR-017) | Fachbereich + Datenschutz | Gate 2 |
| O-05 | Vertrag über die Auftragsverarbeitung (AVV bei Fremddaten) | technisch vorbereitet | Fachbereich + Recht | Gate 2 |
| R-01 | **Karenzzeit** der Aufbewahrung (Abstand „markiert“ → „gelöscht“) — die Fristen selbst sind entschieden (F-02), die **Karenzzeit** hat **keinen** beschlossenen Vorgabewert | konfigurierbar (`echobox.retention.purge-delay`), **ohne Vorbelegung**; der Vertrag behauptet keinen Wert (ADR-012 §6) | Fachbereich | Gate 2 |

O-01, O-02, O-03, O-06 und O-07 sind am 2026-10-21 am Gate 1 entschieden
(F-01…F-04); die Werte und ihre Fundstellen stehen in `docs/adr/README.md`.

---

## 8. Diagramme

| Datei | Ebene |
|---|---|
| `diagrams/01-context.mmd` | C4 Level 1 — Kontext |
| `diagrams/02-container.mmd` | C4 Level 2 — Container |
| `diagrams/03-component-api.mmd` | C4 Level 3 — Komponenten des API-Layers |
| `diagrams/04-component-worker.mmd` | C4 Level 3 — Komponenten des Zustell-Workers |
| `diagrams/05-sequence-delivery.mmd` | Sequenz — Zustellung mit Wiederholung und Replay |

**5 Dateien: 4 C4-Diagramme + 1 Sequenzdiagramm.**

---

## 9. Artefakte

| Pfad | Inhalt |
|---|---|
| `docs/api/openapi.yaml` | Der Vertrag (einziger Ort) |
| `docs/architecture/architecture.md` | Dieses Dokument — die Erzählung |
| `docs/architecture/domain_model.md` | Entitäten, Zustandsautomat, Invarianten |
| `docs/architecture/GLOSSARY.md` | Fachbegriffe — **einziger Ort** je Begriff |
| `docs/architecture/folder-structure.md` | Zielstruktur für `src/` |
| `docs/architecture/schema.sql` | Kopie des kanonischen Schemas (byte-identisch) |
| `docs/architecture/diagrams/*.mmd` | 4 C4-Diagramme + 1 Sequenzdiagramm |
| `docs/adr/ADR-*.md`, `docs/adr/README.md` | 17 Entscheidungen + Index |
| `.agent/knowledge/schema.sql` | Kanonisches Schema |
| `.agent/knowledge/stack.md` | Stack-Entscheidung (aus ADR-001) |
| `.agent/knowledge/openapi.yaml` | **Verweis** auf `docs/api/openapi.yaml` |
| `.agent/artifacts/architecture.md` | Kompakter Katalog |
| `.agent/artifacts/simplify-report.md` | Reduktionsbericht (Schritt 3) |
| `.agent/artifacts/stress-test.md` | **Stresstest-Bericht (Schritt 4)** — Befunde, Vertragsdefekte, offene Entscheidungen |
| `.agent/artifacts/ruecksprung-bilanz.md` | **Rücksprung-Bilanz (Schritt 2)** — Nachweis: 22 Vertragsdefekte, S-01, E-01…E-09, PostgreSQL-Nachweis |
| `.agent/artifacts/folder-structure.md` | Zielstruktur (Kurzfassung) |
| `.agent/artifacts/api-review.md` | Vertragsreview: Abdeckung und Lücken |
| `.agent/artifacts/adr-index.md` | **ADR-Gate-Vorlage** (bestätigt / offen) — der Index selbst steht in `docs/adr/README.md` |
