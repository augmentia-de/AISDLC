# Domänenmodell — EchoBox

> Schritt 3 (Simplify & Reduktion), korrigiert in Schritt 4 (Grill-Me-Stresstest)
> · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Grundlage: `docs/requirements/PRD.md` (Abschnitt 4 und 6), `docs/Anforderung.md` (Abschnitt 4)
> Umsetzung: `.agent/knowledge/schema.sql`, `docs/api/openapi.yaml`

---

## 1. Fachbegriffe

Die Fachbegriffe (Endpunkt, Ereignis, Zustellung, Zustelllauf, tote
Zustellung, Replay, Mandant, Rolle …) stehen **an einer Stelle**:
[`GLOSSARY.md`](GLOSSARY.md). Dieses Dokument wiederholt sie nicht — es
beschreibt **Entitäten, Beziehungen und den Zustandsautomaten**.

Die beiden Entscheidungen, die man beim Lesen des Zustandsautomata kennen
muss:

| Begriff | Kurz | Wahrheit |
|---|---|---|
| **Zustellung** | der **einzelne** Versuch | Glossar, FR-021 |
| **Zustelllauf** | die Kette **über alle** Versuche eines Ereignisses an einen Endpunkt | Glossar, D-06 |

Ein Ereignis an drei Endpunkte erzeugt **drei** Zustellläufe mit derselben
Ereignis-ID und verschiedenen Zustelllauf-IDs.

---

## 2. Entitäten

```
                    ┌────────────────────┐
                    │      Endpunkt      │
                    │  endpoint_id  PK   │
                    │  organisation_id    │
                    │  target_url         │
                    │  secret_ciphertext  │
                    │  status (active|    │
                    │    paused)          │
                    └─────────┬──────────┘
                              │ 1:n
              ┌───────────────┼───────────────┐
              │  abonniert    │               │  Zustellläufe
              │  den Ereignis-│               ▼
              │  typ          │      ┌────────────────────┐
              │  typ          │      │    Zustelllauf     │
              ▼               │      │ delivery_run_id PK │
     ┌────────────────────┐   │      │ event_id       FK  │
     │ Endpoint-          │   │      │ endpoint_id    FK  │
     │ Event-Typ         │   │      │ status             │
     │ (endpoint_id,      │   │      │ attempt_count      │
     │  event_type) PK    │   │      │ next_attempt_at    │
     └────────────────────┘   │      │ replay_of      FK  │
                              │      └─────────┬──────────┘
                              │                │ 1:n
                              │                ▼
                              │      ┌────────────────────┐
                              │      │ Zustellversuch     │
                              │      │ attempt_id     PK  │
                              │      │ attempt_number     │
                              │      │ outcome            │
                              │      │ response_status    │
                              │      └────────────────────┘
                              │
                              │ n:1
                              ▼
                    ┌────────────────────┐
                    │       Ereignis     │
                    │  event_id      PK   │
                    │  organisation_id    │
                    │  event_type         │
                    │  idempotency_key    │
                    │  payload_ciphertext │
                    │  payload_fingerprint│
                    │  status             │
                    └────────────────────┘
```

### 2.1 Beziehungen und ihre Regeln

| Beziehung | Regel |
|---|---|
| Endpunkt → Ereignistyp | Viele-zu-viele, normalisiert in `endpoint_event_types`. Ein Endpunkt abonniert mindestens einen Ereignistyp. |
| Ereignis → Endpunkt | Viele-zu-viele, aufgelöst in **Zustellläufe**. Ein Ereignis ohne passenden Endpunkt erzeugt **einen** Zustelllauf mit Status `no_subscriber` und `endpoint_id = null` (D-04). |
| Zustelllauf → Versuch | Eins-zu-viele. Die Versuchsnummer ist je Lauf eindeutig und 1-basiert. |
| Zustelllauf → Zustelllauf | Selbstbezug über `replay_of` — die Historie bleibt erhalten (AC-013-2). **UNIQUE**: ein Vorgängerlauf hat höchstens **einen** Replay (I-9). |
| Ereignis → Zustelllauf | Ein Ereignis kann viele Zustellläufe haben (ein je Endpunkt, plus Replays). |

### 2.2 Attribute, die den Vertrag tragen

| Entität | Attribut | Vertragsrelevanz |
|---|---|---|
| Ereignis | `event_id` | `X-EchoBox-Event-Id` — fachlicher Deduplizierungsschlüssel |
| Ereignis | `idempotency_key` | `POST /events` — 409 bei abweichender Nutzlast (D-05) |
| Ereignis | `payload_fingerprint` | Vergleich „gleiche oder andere Nutzlast" ohne Klartext |
| Ereignis | `payload_is_pre_encrypted` | `payload_encoding` im Request |
| Zustelllauf | `delivery_run_id` | `X-EchoBox-Delivery-Run-Id` — ein Replay erzeugt eine **neue** ID |
| Zustelllauf | `attempt_count`, `max_attempts` | `GET /deliveries` — FR-030 |
| Zustelllauf | `next_attempt_at` | `GET /deliveries` — leer bei endgültigen Zuständen (AC-015-2) |
| Zustelllauf | `last_response_status`, `last_error_code` | `GET /deliveries` — Antwortcode des Empfängers (FR-030) |
| Zustelllauf | `abandoned_at` | Totenliste — Zeitpunkt der Aufgabe (AC-016-1) |
| Zustelllauf | `replay_of` | Replay-Historie (AC-013-2) |
| Versuch | `attempt_id` | `X-EchoBox-Attempt-Id` |
| Versuch | `outcome` | Wiederholungsregel (D-08) |
| Versuch | `event_accepted_at` | p95-Zustelllatenz ohne Join (FR-032) |

---

## 3. Zustandsautomat der Zustellung

### 3.1 Zustände des Zustelllaufs

| Zustand | Bedeutung | Endgültig? | Nächster Versuch? |
|---|---|---|---|
| `pending` | angelegt, noch kein Versuch ausgeführt | nein | ja |
| `scheduled` | ein Versuch ist geplant (`next_attempt_at` gesetzt) | nein | ja |
| `delayed` | der Versuch wurde durch das Endpunkt-Limit verschoben (FR-033) | nein | ja |
| `failed` | der letzte Versuch ist fehlgeschlagen, ein weiterer ist geplant | nein | ja |
| `sent` | erfolgreich zugestellt (2xx) | **ja** | nein |
| `abandoned` | Versuchsgrenze erreicht → **tote Zustellung** (FR-023) | **ja** | nein |
| `rejected` | vom Empfänger endgültig abgelehnt (FR-025, D-08) | **ja** | nein |
| `cancelled` | durch Pause des Endpunkts abgebrochen (D-03) | **ja** | nein |
| `no_subscriber` | Ereignis ohne Abnehmer (D-04) — es gab nie einen Versuch | **ja** | nein |

> **Der Zustand eines *neuen* Laufs:** Ein Zustelllauf entsteht in `pending`
> (angelegt, noch kein Versuch ausgeführt) — so steht es auch im Schema
> (`delivery_runs.status` DEFAULT `'pending'`), und so ist es im Vertrag
> festgelegt (`ReplayResponse.status: pending` bei `attempt_count: 0`).
> `scheduled` heißt: **ein Versuch läuft gerade**. Der Widerspruch zwischen
> Vertrag und Schema (Befund S-07) ist im Rücksprung aus Schritt 4 behoben.

### 3.2 Übergänge

```
                       ┌──────────────────────────────────────────┐
                       │                                          │
   (angelegt)          ▼                                          │
   ─────────► pending ──────────► scheduled ◄────────┐            │
                 │                     │             │            │
                 │ Pause (D-03)        │             │            │
                 ▼                     │             │            │
             cancelled                 │             │            │
          (endgültig)                 │             │            │
                                       │             │            │
                                       │ Versuch    │            │
                                       │ läuft      │            │
                                       ▼             │            │
                                  ┌─────────┐       │            │
                                  │ Versuch │       │            │
                                  └────┬────┘       │            │
                     ┌─────────────────┼─────────────────┐      │
                     ▼                 ▼                 ▼      │
                 2xx │            4xx (410) │        5xx/Timeout│
                     │                 │                 │      │
                     ▼                 ▼                 ▼      │
                   sent            rejected           failed ───┘
                (endgültig)      (endgültig)         │
                                                     │ Versuchsgrenze
                                                     │ erreicht
                                                     ▼
                                                 abandoned
                                                (endgültig,
                                                 tote Zustellung)

   no_subscriber: Sonderfall ohne jeden Versuch (D-04)

   abandoned ──(Replay, FR-024)──► neuer Zustelllauf
                                    (neue Zustelllauf-ID,
                                     Versuchszahl startet bei 1)

   rejected / cancelled: endgültig, aber **nicht** replaybar —
   replaybar ist nur die aufgegebene Zustellung (Vertrag, GLOSSARY)

```

### 3.3 Übergangstabelle

| Von | Ereignis | Nach | Bedingung | Quelle |
|---|---|---|---|---|
| `pending` | erster Versuch beginnt | `scheduled` | Lauf ist fällig | D-10 |
| `pending`/`scheduled` | Endpunkt pausiert | `cancelled` | Pause des Endpunkts | D-03, AC-002-4 |
| `scheduled` | Versuch läuft | `sent` | Antwort 2xx | FR-021, AC-010-3 |
| `scheduled` | Versuch läuft | `rejected` | Antwort 410 mit Ablehnungssignal | FR-025, AC-014-1 |
| `scheduled` | Versuch läuft | `rejected` | Antwort übriger 4xx | D-08, AC-014-4 |
| `scheduled` | Versuch läuft | `failed` | Antwort 5xx / 408 / 429 / Netzwerkfehler | D-08, AC-014-3 |
| `failed` | nächster Versuch geplant | `scheduled` | Backoff abgelaufen, kein Endpunkt-Limit | FR-022, AC-011-1 |
| `failed` | Endpunkt-Limit greift | `delayed` | Zähler je Endpunkt/Fenster über dem Grenzwert | FR-033, AC-018-2 |
| `delayed` | Zeitfenster frei | `scheduled` | — | FR-033 |
| `failed`/`delayed` | Versuchsgrenze erreicht | `abandoned` | `attempt_count >= max_attempts` | FR-023, AC-012-1 |
| `abandoned` | Replay | **neuer** Lauf in `pending` | Empfänger-Admin des Endpunkts, Endpunkt nicht pausiert, Vorgängerlauf ist `abandoned` | FR-024, AC-013-1, AC-013-4 |
| `pending` | Ereignis ohne Abnehmer | `no_subscriber` | kein Endpunkt abonniert den Ereignistyp | D-04 |

### 3.4 Invarianten

| # | Invariante | Prüfung |
|---|---|---|
| I-1 | Ein endgültiger Zustand hat **keinen** `next_attempt_at` | DB-Check `delivery_runs_terminal_no_next_attempt_ck` |
| I-2 | Ein `abandoned`-Lauf hat `abandoned_at` gesetzt | DB-Check `delivery_runs_abandoned_at_ck` |
| I-3 | `no_subscriber` ⇒ `endpoint_id IS NULL`; alle anderen Zustände ⇒ `endpoint_id IS NOT NULL` | DB-Check `delivery_runs_no_subscriber_ck` |
| I-4 | Die Versuchsnummer ist je Lauf eindeutig und beginnt bei 1 | DB-Constraint `delivery_attempts_run_number_uq` + `attempt_number_ck` |
| I-5 | Ein Replay erzeugt einen **neuen** Zustelllauf; der alte bleibt unverändert | `replay_of` zeigt auf den Vorgänger; keine Änderung am alten Datensatz (AC-013-2) |
| I-6 | `attempt_count` steigt nie sprunghaft; ein abgestürzter Worker erhöht sie **nicht** | Lease-Rettung gibt den Lauf frei, ohne einen Versuch zu zählen (AC-023-4) |
| I-7 | Genau **eine** Instanz führt einen Versuch aus | `FOR UPDATE SKIP LOCKED` + Lease (AC-023-3) |
| I-8 | Ein gepausierter Endpunkt erzeugt **keinen** Zustellvorgang und **keine** Nachlieferung | `pending`/`scheduled`-Läufe werden bei Pause auf `cancelled` gesetzt; bei der Einlieferung wird für pausierte Endpunkte **kein** Lauf angelegt (D-03) |
| I-9 | Ein aufgegebener Zustelllauf kann **einmal** replayed werden | `UNIQUE (replay_of)` in `delivery_runs`; ein weiterer Replay ⇒ HTTP 409 `delivery_state_conflict` (AC-013-1…5, ADR-006 §6) |
| I-10 | Ein 3xx beendet den Lauf endgültig, ohne Wiederholung; Redirects werden nicht gefolgt | `rejected` mit `rejection_reason = client_error`; `outcome = client_error` (ADR-009) |

### 3.5 Ergebnis eines einzelnen Versuchs

| `outcome` | Anlass | Folge für den Lauf |
|---|---|---|
| `success` | 2xx | `sent` |
| `permanent_rejection` | 410 + Ablehnungssignal | `rejected`, `rejection_reason = permanent_rejection` |
| `client_error` | übriger 4xx **oder 3xx** (Redirect wird nicht gefolgt) | `rejected`, `rejection_reason = client_error` |
| `retryable_failure` | 5xx, 408, 429 | `failed` → `scheduled` |
| `network_error` | DNS-, Verbindungs-, TLS-Fehler | `failed` → `scheduled` |
| `timeout` | Verbindungs- oder Lese-Timeout (5 s / 10 s) | `failed` → `scheduled` |
| `cancelled` | Endpunkt wurde während des Versuchs pausiert | `cancelled` |

---

## 4. Fachliche Sonderfälle

| Fall | Verhalten | Quelle |
|---|---|---|
| Ereignis **ohne Abnehmer** | angenommen und gespeichert; ein Zustelllauf mit Status `no_subscriber` und `endpoint_id = null` macht es sichtbar. Kein stilles Verschwinden. | D-04 |
| Endpunkt **pausiert**, Ereignis trifft ein | es entsteht **kein** Zustellvorgang und **keine** Warteschlangeneintragung | D-03, AC-002-1 |
| Endpunkt **pausiert**, Replay | es startet **kein** Versuch; der Lauf endet erneut ohne Zustellung | D-03, AC-013-4 |
| Endpunkt **pausiert**, offene Wiederholung | der Wiederholungsvorgang wird abgebrochen; der Lauf endet mit `cancelled` | D-03, AC-002-4 |
| Endpunkt **reaktiviert** | es wird **nichts** nachgeliefert | D-03, AC-002-2 |
| Gleicher Idempotenzschlüssel, **gleiche** Nutzlast | HTTP 202 mit `idempotent_replay: true` und der Kennung des ersten Ereignisses; die erste Nutzlast bleibt unverändert | FR-011, AC-006-1, AC-006-2 |
| Gleicher Idempotenzschlüssel, **andere** Nutzlast | HTTP 409 `idempotency_conflict`; das erste Ereignis bleibt unverändert | D-05, AC-006-4 |
| Gleicher Idempotenzschlüssel in **anderem** Mandanten | eigenes Ereignis; der Gültigkeitsbereich ist (Mandant, Schlüssel) | D-05, AC-006-3 |
| Vorverschlüsselte Einlieferung mit erkennbarer Klartextnutzlast | HTTP 422; es wird kein Ereignis angelegt | FR-013, AC-008-2 |
| Zustellung an **fremden** Endpunkt per ID | HTTP 404 | D-02, AC-002-3, AC-015-3 |
| Replay einer **nicht** aufgegebenen Zustellung | HTTP 409 `delivery_state_conflict` | Vertrag |
| Replay eines **bereits** replayten Laufs | HTTP 409 `delivery_state_conflict` — der Lauf ist **einmal** replaybar | ADR-006 §6, I-9 |
| Replay bei **pausiertem** Endpunkt | HTTP 409 `delivery_state_conflict` — es entsteht **kein** neuer Lauf, der Vorgängerlauf bleibt unverändert | D-03, AC-013-4 |
| Replay durch eine **nicht** berechtigte Rolle | HTTP 403 | D-15, AC-013-3 |
| Totenliste durch einen Empfänger-Admin | HTTP 403 | AC-016-2 |
| Leere Totenliste | HTTP 200 mit leerem `items`-Array | AC-016-3 |
| Kennzahlen ohne Daten im Fenster | `data_available: false`, `success_rate: null` — **nicht** 0 % und **nicht** 100 % | AC-017-3 |
