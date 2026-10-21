# Architektur — EchoBox (Ereignis-Briefkasten mit Zustellgarantie)

> Schritt 2 (Konzept & Architektur) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Quellen: `docs/Anforderung.md`, `docs/requirements/PRD.md`, `docs/requirements/traceability_matrix.json`
> Vertrag: `docs/api/openapi.yaml` (einziger Ort) · Schema: `.agent/knowledge/schema.sql` (kanonisch)
> Stack-Entscheidung: `docs/adr/ADR-001-tech-stack.md` → `.agent/knowledge/stack.md`

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

| # | Entscheidung | Folge |
|---|---|---|
| 1 | **Annahme und Zustellung sind getrennt** (D-10) | Der Annahme-Request antwortet in p95 < 200 ms (NFR-002), unabhängig davon, wie lange der Empfänger braucht. |
| 2 | **Der Zustelllauf ist die Einheit der Sichtbarkeit** | Jedes Ereignis erzeugt je abonniertem Endpunkt genau einen Zustelllauf mit vollständigem Versuchsprotokoll. Auch der Sonderfall „kein Abnehmer" wird als Zustand sichtbar (D-04). |
| 3 | **Der Mandant steckt in der Identität und wird zusätzlich in der Datenbank erzwungen** | D-01 im Vertrag, RLS im Schema (ADR-002). Ein vergessener Anwendungsfilter zeigt nichts. |

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

### 2.1 Fachmodule

Die Module entsprechen den Modulen, die das PRD (Abschnitt 12) für Schritt 2
vorgeschlagen hat; die Zuordnung ist damit bestätigt.

| Modul | Verantwortung | Stories |
|---|---|---|
| `endpoint` | Registrierung, Pause/Reaktivierung, Secret-Rotation, rollenabhängige Sicht | US-001…US-004, US-019 |
| `event` | Annahme, Idempotenz, Verschlüsselung im Ruhezustand, vorverschlüsselte Einlieferung | US-005…US-008 |
| `delivery` | Zustelllauf, Versuch, Backoff, Streuung, Aufgabe, Replay, Ablehnung, Endpunkt-Begrenzung | US-009…US-014, US-018 |
| `crypto` | Signatur, Payload-Verschlüsselung, Secret-Verwaltung | US-003, US-007, US-009 |
| `protocol` | Zustellprotokoll, tote Zustellungen, Kennzahlen, Aufbewahrung | US-015…US-018 |
| `tenant` | Mandantenkontext, Durchsetzung auf Datenbankebene | US-004, US-022 |
| `frontend` | Oberfläche Endpunkte, Protokoll, tote Zustellungen, Barrierefreiheit | US-019…US-021 |

### 2.2 Warum Ports & Adapters (Hexagon)

Der Kern der Anwendung — Zustellzustandsautomat, Backoff, Signatur, Mandanten-
regeln — enthält **keine** Framework-Typen. Er wird über Ports angetrieben:

| Port | Adapter | Warum variabel |
|---|---|---|
| `DeliveryPort` | HTTP-Adapter (Java `HttpClient`) | Testbar ohne Netz; der Empfänger wird simuliert. |
| `SecretPort` | Secret-Store-Adapter | Schlüssel kommen von außen (NFR-004). |
| `ClockPort` | Systemuhr | Backoff und Zeitlimits werden ohne Warten prüfbar (AC-011-5, AC-023-4). |
| `DeliveryRepository` / `EventRepository` / `EndpointRepository` | JDBC-Adapter (JdbcTemplate, **kein** dynamisch zusammengesetztes SQL) | RLS braucht den Transaktionskontext; JPA würde den Mandantenkontext schlechter sichtbar machen. |
| `IdGenerator` | UUID-v4/UUID-v7 | Kennungen sind Teil des öffentlichen Vertrags (D-06). |

**Persistenzentscheidung:** Spring Data **JDBC** (JdbcTemplate), nicht JPA/Hibernate.
Grund: die RLS-Policy hängt an einer transaktionslokalen Sitzungsvariable; mit
JPA muss der Flush- und Clear-Mechanismus zusätzlich mit der Mandantenregel
verträglich bleiben. JdbcTemplate macht den Satz sichtbar, den die Anwendung
absetzt — und die Abfrage lautet immer „Mandant + Filter“, nie „Filter“.

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

### 3.1 Ausgehender Zustellvertrag (eingefroren)

Der Vertrag gegenüber dem Empfänger ist in `docs/api/openapi.yaml` unter
`x-delivery-protocol` **vollständig** beschrieben und eingefroren
(FR-020, D-07, D-08):

| Element | Festlegung |
|---|---|
| Signaturverfahren | `HMAC-SHA256`, Header **`X-EchoBox-Signature`**, Wert `v1=<hex-lowercase>` |
| Signier-Eingabe | `<timestamp>.<payload_bytes>` — der Zeitstempel ist **exakt** der Wert des Headers `X-EchoBox-Timestamp`; die Nutzlastbytes sind die **exakt gesendeten** Bytes |
| Kennungen | `X-EchoBox-Event-Id` (fachlicher Deduplizierungsschlüssel), `X-EchoBox-Delivery-Run-Id` (Lauf), `X-EchoBox-Attempt-Id` (Versuch) — D-06 |
| Request-Body | **exakt die Nutzlastbytes**, keine Hülle. Grund: die zugestellten Bytes müssen byteidentisch mit den eingelieferten sein (AC-007-3), und die Signatur gilt über genau diese Bytes |
| Ablehnungssignal | HTTP **410 Gone**, Header `X-EchoBox-Rejection: permanent`, Body `{rejection, reason, message?, rejected_event_types?}` |
| Wiederholungsregel | 5xx / 408 / 429 / Netzwerkfehler ⇒ wiederholen; 2xx ⇒ fertig; 410 und übrige 4xx ⇒ sofort aufgeben, Status `rejected` (D-08) |
| Dubletten | at-least-once; der Empfänger erkennt Dubletten an gleicher Ereignis-ID und gleicher Zustelllauf-ID bei unterschiedlicher Versuchs-ID |

### 3.2 Warum der Body ohne Hülle ist

Ein Envelope (`{"event_id":…, "payload":{…}}`) wäre bequemer, verletzt aber
AC-007-3: *„die zugestellten Bytes sind identisch mit den eingelieferten
Klartextbytes"*. Bei einem Envelope wären die zugestellten Bytes die
Serialisierung des Envelopes, nicht die eingelieferten Bytes. Alle Metadaten
stehen deshalb in Headern — dort gehören sie ohnehin hin, weil der Empfänger
sie für die Deduplizierung braucht, ohne den Body anfassen zu müssen.

---

## 4. Mandantentrennung

Zwei Ebenen, die einander **nicht** ersetzen, sondern sichern:

| Ebene | Maßnahme | Verhindert |
|---|---|---|
| Vertrag | Der Mandant stammt aus dem JWT; ein Mandantenparameter wird mit 422 abgelehnt (D-01) | Ein Aufrufer wählt seinen Mandanten selbst. |
| Anwendung | Jede Repository-Abfrage trägt den Mandanten; fremde Ressourcen ⇒ 404 (D-02) | Ein bewusster Zugriff auf fremde Daten. |
| **Datenbank** | Row-Level-Security auf **jeder** fachlichen Tabelle; `current_setting('app.organisation_id', true)`; **fail-closed** | Ein **vergessener** `WHERE`-Zweig in der Anwendung (NFR-003). |

Details und die Begründung, warum keine Rolle `BYPASSRLS` bekommt:
`docs/adr/ADR-002-mandantentrennung-rls.md`.

**Verifiziert:** gegen eine echte PostgreSQL-16-Instanz geprüft —
ohne gesetzten Mandanten liefert jede fachliche Tabelle **0 Zeilen**; mit
`set_tenant('m-1')` sieht der Mandant **ausschließlich** seine Zeilen; nach
`COMMIT` ist der Kontext zurückgesetzt.

---

## 5. Verschlüsselung im Ruhezustand

| Aspekt | Festlegung |
|---|---|
| Verfahren | AEAD, **AES-256-GCM** (anerkanntes Verfahren, NFR-004), 12-Byte-Nonce, 128-Bit-Authentikationstag |
| Schlüssel | kommt aus einem **Secret-Store** (Secret-Injektion), **außerhalb der Datenbank**; im Schema, im Container-Image und in der Konfiguration ist **kein** Schlüssel (AC-007-4) |
| Was wird verschlüsselt | die Ereignisnutzlast (`events.payload_ciphertext`) und das Endpunkt-Secret (`endpoints.secret_ciphertext`) |
| Wann wird entschlüsselt | **nur** für den Zustellvorgang; die entschlüsselten Bytes werden nie protokolliert (AC-007-2) |
| Durchsuchbarkeit | die Protokollsuche läuft **ausschließlich über Metadaten** (Ereignis-ID, Typ, Endpunkt, Status, Zeitraum) — keine Volltextsuche über Nutzlasten (D-11, AC-007-5) |
| Idempotenzvergleich | HMAC-SHA256 über die kanonischen Nutzlastbytes (`events.payload_fingerprint`) — erlaubt die Erkennung „gleicher Schlüssel, andere Nutzlast" (409), ohne Klartext oder durchsuchbaren Klartext-Hash abzulegen |
| Vorverschlüsselte Einlieferung | die Bytes werden **unverändert** gespeichert und zugestellt; die Signatur wird über die **Chiffre** gebildet, damit der Empfänger sie über die empfangenen Bytes prüfen kann (FR-013) |

Architekturseite von O-04: `docs/adr/ADR-003-verschluesselung-ruhezustand.md`.
Die **fachliche** Seite (Pflicht? AVV?) bleibt beim Fachbereich.

---

## 6. Betrieb

| Thema | Festlegung | ADR |
|---|---|---|
| Prozessmodell | zwei Container-Rollen: `api` (HTTP) und `worker` (Zustellung) — beide horizontal skalierbar, ohne gemeinsamen Zustand im Speicher | ADR-006 |
| Zustellung nach Absturz | Lease mit Ablaufzeit auf dem Zustelllauf; nach Ablauf übernimmt ein anderer Worker — die Versuchszahl steigt **nicht** sprunghaft (AC-023-4) | ADR-006 |
| Doppelzustellung | `FOR UPDATE SKIP LOCKED` — genau eine Instanz führt einen Versuch aus (AC-023-3) | ADR-006 |
| Mandanteninstanz | **vorbereitet**, nicht Standard: `echobox.tenant.id` schränkt eine Instanz auf einen Mandanten ein (NFR-006, AC-023-2) | ADR-013 |
| Zeitlimit je Versuch | Verbindungs-Timeout **5 s**, Lese-Timeout **10 s** (Summe 15 s < 30 s, wie in Abschnitt 11 gefordert); ein langsamer Empfänger zählt als Fehlversuch | ADR-014 (O-07) |
| Rate-Limit | Mechanismus gebaut und konfigurierbar; **kein** Grenzwert vorbelegt, solange O-01 offen ist; nur Zähler je Endpunkt und Zeitfenster werden erhoben (D-14, AC-018-4) | ADR-010 |
| Aufbewahrung | Frist konfigurierbar; **kein** Wert vorbelegt, solange O-02 offen ist; `deleted_at` markiert, ein Purge löscht endgültig (D-12) | ADR-012 |
| Kennzahlen | Erfolgsquote und p95-Zustelllatenz je Endpunkt; `data_available: false`, wenn keine Daten im Fenster vorliegen (FR-032, AC-017-3) | ADR-011 |
| Staging / Produktion | getrennt bereitgestellt, getrennte Konfigurationen und getrennte Secrets (AC-023-5) | ADR-013 |
| Health | `/health` (Liveness) und `/health/ready` (Readiness) ohne Authentifizierung | Vertrag |

---

## 7. Fehlerbehandlung

### 7.1 Fehlerschema (eingehend)

Ein einziges Schema (`Problem`, RFC-7807-artig) für **alle** Fehlerantworten:

```json
{
  "type": "https://echobox.example/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "code": "validation_failed",
  "detail": "Die Ziel-URL muss das Schema https verwenden.",
  "instance": "/api/v1/endpoints",
  "violations": [{ "field": "target_url", "code": "scheme_not_allowed", "message": "…" }]
}
```

| Status | `code` | Wann |
|---|---|---|
| 400 | `malformed_request` | kein gültiges JSON |
| 401 | `unauthorized` | kein/ungültiges Token |
| 403 | `forbidden` | Rolle darf es nicht (z. B. Totenliste ohne `service_admin`, Replay ohne Empfänger-Admin, Rotation als Entwickler) |
| 404 | `not_found` | Ressource fehlt **oder** gehört einem fremden Mandanten (D-02) |
| 409 | `idempotency_conflict` | gleicher Idempotenzschlüssel, andere Nutzlast (D-05) |
| 409 | `delivery_state_conflict` | Replay einer nicht aufgegebenen Zustellung |
| 413 | `payload_too_large` | Nutzlast > 1.048.576 Byte |
| 422 | `validation_failed` / `tenant_parameter_rejected` | semantisch ungültig, inklusive Mandantenparameter (D-01) |
| 429 | `rate_limit_exceeded` | API-Anfragelimit (nicht das Endpunkt-Limit aus FR-033) |
| 500 | `internal_error` | unerwarteter Fehler — kein Stacktrace, kein internes Detail |
| 503 | `service_unavailable` | Dienst nicht bereit |

**Faustregel:** 500-Antworten enthalten niemals interne Kennungen, SQL-Text,
Dateipfade oder Nutzlastanteile.

### 7.2 Fehlerbehandlung im Zustellprozess

Der Fehler des Empfängers ist **kein** Fehler des Dienstes — er ist ein
Ergebnis, das protokolliert wird. DieMatrix:

| Empfänger-Antwort | Versuchsergebnis | Zustand des Laufs | Weitere Versuche |
|---|---|---|---|
| 2xx | `success` | `sent` | keine |
| 410 + Ablehnungssignal | `permanent_rejection` | `rejected` | keine |
| übriger 4xx | `client_error` | `rejected` | keine |
| 5xx, 408, 429 | `retryable_failure` | `failed` → `scheduled` | ja, nach Backoff |
| Netzwerk-/Timeout-Fehler | `network_error` / `timeout` | `failed` → `scheduled` | ja, nach Backoff |
| Endpunkt pausiert | `cancelled` | `cancelled` | keine (D-03) |

Ein unerwarteter Fehler **im Dienst** (Datenbank nicht erreichbar, Chiffre
fehlerhaft) bricht den Versuch ab, ohne die Versuchszahl zu erhöhen, und wird
als `internal` protokolliert — der Lauf bleibt fällig und wird erneut
aufgenommen. So entsteht kein stiller Verlust (R-05).

---

## 8. Entwurfsoptionen, die verworfen wurden

| Option | Warum verworfen |
|---|---|
| **Message-Broker (Kafka/RabbitMQ) als Zustellweg** | Ausdrücklich ausgeschlossen (Abschnitt 2): zweite Zustandshaltung, die konsistent zu halten wäre. Der Dienst ist ein Wrapper. |
| **Event-Source / Event-Store** | Der Dienst hält nur, was er für die Zustellung braucht, und das so kurz wie möglich (R-10). |
| **Zustellung im Request-Thread** | Bricht das Latenzversprechen (NFR-002) und die Rahmenbedingung aus Abschnitt 11 (ein 30-s-Empfänger darf den Annahme-Request nicht blockieren). |
| **JPA/Hibernate statt JdbcTemplate** | Der transaktionslokale Mandantenkontext und der Flush-Mechanismus stören sich; der SQL-Satz soll sichtbar bleiben. |
| **Suchindex über den Nutzlastinhalt** | Unvereinbar mit FR-012/NFR-004 (D-11). |
| **`BYPASSRLS` für den Worker** | Umgeht die Trennung für eine ganze Laufzeitrolle. Stattdessen: eine schmale `SECURITY DEFINER`-Funktion, die **nur** Mandantenkennungen liefert (ADR-002). |
| **JSON-Envelope als Zustell-Body** | Verletzt AC-007-3 (zugestellte Bytes ≠ eingelieferte Bytes). |
| **Ausgehende Webhooks als `callbacks` im OpenAPI** | OpenAPI 3.0.3 kennt `callbacks` nur für OpenAPI-3.0-kompatible Rückkanäle; der ausgehende Vertrag ist eigenständig und wird deshalb als eigener, eingefrorener Vertragsabschnitt (`x-delivery-protocol`) geführt. |
| **Offset-Paginierung** | Bei laufend wachsendem Protokoll sind Springe unstabil und teuer. Umgestellt auf Cursor-Paginierung (`limit` + `cursor`). |

---

## 9. Bekannte Befunde und offene Punkte

### 9.1 Befunde dieses Schritts

| ID | Befund | Behandlung |
|---|---|---|
| B-01 | Der Roh-Input nennt „Zustellung“ für den einzelnen Versuch und „Lieferung“ für die Kette (Abschnitt 4), FR-021 verlangt aber eine **eigene Kennung je Zustellung** — mit nur einer Kennung je Versuch kann der Empfänger eine Dublette nicht erkennen | D-06: drei Kennungen. Auflösung im Vertrag (`x-delivery-protocol.request.headers`) und im Glossar |
| B-02 | Der Aufbewahrungs- und der Rate-Limit-Wert fehlen (Abschnitt 13) | Kein Wert verdrahtet. Mechanik gebaut, Werte aus der Konfiguration, Vorschläge in ADR-010 und ADR-012 |
| B-03 | FR-024 (Empfänger-Admin) widerspricht der Rollentabelle (Entwickler) | D-15: bis zur Entscheidung nur der Empfänger-Admin; alle anderen ⇒ 403. Vorschlag in ADR-015 |
| B-04 | Ohne das Ablehnungssignal wäre FR-025 nicht implementierbar | Ablehnungssignal im Vertrag festgeschrieben (410 + `X-EchoBox-Rejection` + Body-Format) |
| B-05 | Eine Volltextsuche im Protokoll wäre mit FR-012 unvereinbar | D-11: Suche nur über Metadaten. Die fachliche Frage (O-04) bleibt beim Fachbereich |

### 9.2 Offene Punkte — **nicht** entschieden, nur vorbereitet

Der Fachbereich entscheidet O-01 bis O-07 bei **Gate 1**. Die Architektur hat
jeden Punkt so entworfen, dass **kein Wert verdrahtet** werden muss:

| ID | Thema | Was die Architektur vorbereitet | Vorschlag (nicht entschieden) |
|---|---|---|---|
| O-01 | Rate-Limit je Endpunkt | Mechanismus `delay`/`block`, Zähler je Endpunkt und Fenster, `limit = null` | 60 Versuche/Endpunkt/Minute, **verschieben** — ADR-010 |
| O-02 | Aufbewahrung | `retention_settings` je Mandant, getrennt für Ereignisse und Protokolle, `NULL` = keine Frist | 30 Tage ab letztem Versuch — ADR-012 |
| O-03 | Wer darf replayen | Rollenprüfung an einer Stelle; heute nur Empfänger-Admin | Empfänger-Admin; Entwickler nur mit Mandantenfreigabe; Plattform-Betrieb **nicht** — ADR-015 |
| O-04 | Verschlüsselung | Verfahren, Schlüsselverwaltung und Suche sind **entschieden** (ADR-003); offen bleibt die fachliche Frage „Pflicht? AVV?" | Verschlüsselung bleibt Muss; Suche nur über Metadaten — ADR-003 |
| O-05 | AVV | kein Software-Blocker; technisch vorbereitet (Datenminimierung, Trennung, Löschfristen) | AVV vor Livegang — ADR-012 |
| O-06 | Versuche und Backoff | `max_attempts` je Lauf, Backoff mit Verdopplung und Streuung, alle Werte konfigurierbar | 8 Versuche, Basis 1 min, ×2, Deckel 60 min, ± 20 % — ADR-008 |
| O-07 | Zeitlimit je Versuch | **Architekturentscheidung**: Verbindungs-Timeout 5 s, Lese-Timeout 10 s | festgelegt — ADR-014 |

---

## 10. Abdeckung der 23 Stories

| Epic | Stories | Vertragsabdeckung |
|---|---|---|
| E1 Endpunkte verwalten | US-001…US-004 | `POST/GET/PATCH /endpoints`, `pause`, `resume`, `secret-rotation` |
| E2 Ereignisse einliefern | US-005…US-008 | `POST /events` (202, 409, 413, 422), `GET /events`, `GET /events/{id}` |
| E3 Zustellung und Zustellgarantie | US-009…US-014 | `x-delivery-protocol` (Signatur, Kennungen, Retry, Ablehnung), `POST /deliveries/{id}/replay` |
| E4 Protokoll und Betrieb | US-015…US-018 | `GET /deliveries`, `GET /deliveries/{id}`, `GET /dead-deliveries`, `GET /endpoints/{id}/metrics`, `POST /retention/enforcement` |
| E5 Oberfläche | US-019…US-021 | Datenquellen der drei Seiten; Barrierefreiheit in ADR-018 |
| E6 Mandantentrennung und Betrieb | US-022, US-023 | RLS im Schema (ADR-002), `/health` + `/health/ready`, Mandanten-Sharding (ADR-013) |

**Alle 101 Akzeptanzkriterien** sind im Vertrag auffindbar — als `x-ac-id` an der
Operation, als `x-ac-id` am ausgehenden Zustellvertrag oder als Eintrag in
`x-acceptance-coverage`. Die Zuordnung aller 24 Anforderungen steht in
`x-requirement-coverage`. Maschinenprüfung: siehe
`.agent/artifacts/api-review.md`.

---

## 11. Diagramme

| Datei | Ebene |
|---|---|
| `diagrams/01-context.mmd` | C4 Level 1 — Kontext |
| `diagrams/02-container.mmd` | C4 Level 2 — Container |
| `diagrams/03-component-api.mmd` | C4 Level 3 — Komponenten des API-Layers |
| `diagrams/04-component-worker.mmd` | C4 Level 3 — Komponenten des Zustell-Workers |
| `diagrams/05-sequence-delivery.mmd` | Sequenz — Zustellung mit Wiederholung und Replay |

---

## 12. Artefakte dieses Schritts

| Pfad | Inhalt |
|---|---|
| `docs/api/openapi.yaml` | Der Vertrag (einziger Ort) |
| `docs/architecture/architecture.md` | Dieses Dokument |
| `docs/architecture/domain_model.md` | Fachbegriffe, Entitäten, Zustandsautomat |
| `docs/architecture/GLOSSARY.md` | Glossar nach `aisdlc-glossary-adr` |
| `docs/architecture/folder-structure.md` | Zielstruktur für `src/` |
| `docs/architecture/schema.sql` | Kopie des kanonischen Schemas (byte-identisch) |
| `docs/architecture/diagrams/*.mmd` | C4 und Sequenz |
| `docs/adr/ADR-*.md` | 18 Architecture Decision Records |
| `docs/adr/README.md` | ADR-Index |
| `.agent/knowledge/schema.sql` | Kanonisches Schema |
| `.agent/knowledge/stack.md` | Stack-Entscheidung (aus ADR-001) |
| `.agent/knowledge/openapi.yaml` | **Verweis** auf `docs/api/openapi.yaml` |
| `.agent/artifacts/architecture.md` | Kompakter Katalog |
| `.agent/artifacts/folder-structure.md` | Zielstruktur (Kurzfassung) |
| `.agent/artifacts/api-review.md` | Vertragsreview: Abdeckung und Lücken |
| `.agent/artifacts/adr-index.md` | Eine Zeile je ADR (Gate-Vorlage) |
