# ADR-005 — Asynchroner Prüflauf: persistente Datenbank-Warteschlange mit Backoff und Totmannschaltung

| Feld | Wert |
|---|---|
| **ID** | ADR-005 |
| **Titel** | Asynchroner Prüflauf: persistente Warteschlange mit Backoff und Totmannschaltung |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch A-04/K-6 |
| **Betrifft** | FR-030, FR-031, FR-042, FR-070, FR-071, FR-074, NFR-001, NFR-002, NFR-006, NFR-007, NFR-008, SEC-003, CON-009, K-1, K-6, R-03 |
| **Supersediert** | — |

## Kontext

Der Prüflauf darf die Anfrage nicht blockieren: `POST /prompts` antwortet in p95 < 500 ms,
der Prüflauf dauert p95 < 30 s (NFR-001, NFR-002). Gleichzeitig muss der Betrieb den Ausfall
oder die extreme Verlangsamung des KI-Dienstes verkraften: „Anfragen werden **geordnet**
nachgeholt, das System stürzt nicht ab, kein Datenverlust" (FR-070).

Aus dem PRD bereits festgelegt:

* **CON-009** — der Prüflauf ist **nicht** transaktional an den HTTP-Request gekoppelt;
  Trennung über eine **persistente Warteschlange** (z. B. Datenbank-Queue); **keine
  In-Memory-Queue**, damit Neustarts nichts verlieren.
* **AC-016-3** — nach einem Worker-Neustart werden alle eingereihten Jobs in ihrer
  **ursprünglichen Reihenfolge** verarbeitet; kein Job geht verloren, keine Version bleibt
  ewig in `SCANNING`.
* **K-6** — ein technischer Fehler führt zu `FAILED` (blockiert) und ist reproduzierbar.
* **NFR-008** — Auslegungsziel 5 000 Prüfläufe/Minute, 1 000 000 Prompts.

Zu entscheiden ist: Welche Queue-Technik, welches Zustandsmodell, welche Wiederholungs-
und Totmannschaltungslogik, und wie der Auftrag der Mandantentrennung in den Job-Kontext
kommt.

## Entscheidung

### 1. Warteschlange: PostgreSQL-Tabelle, kein Broker

| Kandidat | Bewertung |
|---|---|
| **PostgreSQL-Tabelle (`scan_job`)** | **Gewählt.** Persistent, transaktional mit dem Anlegen der Version (ein Commit), `FOR UPDATE SKIP LOCKED` für konfliktfreie Mehr-Worker, keine zusätzliche Infrastruktur, `organisation_id` von Natur aus im Datensatz, `ORDER BY created_at` gibt die geforderte Reihenfolge. |
| In-Memory-Queue | Verboten (CON-009) — Datenverlust bei Neustart. |
| RabbitMQ / Kafka | Starker Betriebs- und Compliance-Aufwand (eigener Broker, TLS, Backups, Monitoring) für ein Volumen, das PostgreSQL trägt. Rechtfertigbar erst deutlich über 5 000 Jobs/min. |
| Cloud Tasks / SQS | Bringt die Auftragsverarbeitung aus der Transaktion heraus; die Statusänderung „Version angelegt **und** Job erzeugt“ wäre nicht mehr atomar. Vermeidet zusätzlich die fehlende Nachvollziehbarkeit des Auftrags. |
| Spring `@Async` / Reactive Streams | Nicht persistent, nicht ordnungserhaltend, keine Wiederholungslogik. |

**Transaktionale Kopplung:** Das Anlegen einer Version und das Einreihen des Jobs
geschehen **in derselben Transaktion**. Damit kann kein Prompt ohne Job existieren und kein
Job ohne Version. Das ist die strukturelle Garantie für „kein Datenverlust" (FR-070).

### 2. Zustandsmodell des Jobs

```
scan_job
  id, organisation_id, prompt_version_id,
  status: QUEUED | RUNNING | COMPLETED | FAILED | DEAD_LETTER,
  attempt_count, max_attempts,
  available_at,            -- Zeitpunkt der nächsten Ausführung (Backoff)
  locked_at, locked_by,    -- Worker-Sperre (SKIP LOCKED)
  last_error,              -- technisch, ohne Inhalt
  created_at, updated_at
```

| Übergang | Auslöser |
|---|---|
| `QUEUED` | Anlegen der Version (im selben Commit) |
| `QUEUED → RUNNING` | Worker übernimmt den Job und setzt die Sperre |
| `RUNNING → COMPLETED` | Urteil gesetzt (`APPROVED` / `FLAGGED` / `WAIVED`) |
| `RUNNING → QUEUED` | Fehlgeschlagener Versuch, `available_at = jetzt + Backoff` |
| `RUNNING → DEAD_LETTER` | `attempt_count` erreicht `max_attempts` (Default 5) |
| `QUEUED/RUNNING → FAILED` | `DEAD_LETTER` bzw. erschöpfte Versuche ⇒ Version `FAILED` |

**Reihenfolge:** Jobs werden mit `ORDER BY created_at, id` abgeholt. Bei gleicher
Erstellungszeit entscheidet die ID (stabil, deterministisch) — „in ursprünglicher Reihenfolge"
(AC-016-3) wird damit eindeutig.

**Mehr-Worker:** `SELECT ... FOR UPDATE SKIP LOCKED` erlaubt beliebig viele Worker ohne
gegenseitige Blockade. Jede Instanz zieht Jobs mit `locked_by = <Instanz-Kennung>`.

**Übernahme hängender Jobs:** Ein Job im Status `RUNNING`, dessen `locked_at` älter als
`scan_timeout` (Default 5 min, FR-042) ist, wird zurückgesetzt (`QUEUED`) — der Watchdog
setzt parallel die Version auf `FAILED`, damit die Oberfläche einen Zustand sieht (FR-042).

### 3. Wiederholung (Backoff) und Totmannschaltung

* **Exponentieller Backoff mit Jitter:** `delay = min(base × 2^(attempt-1), max) + jitter`,
  Default `base = 10 s`, `max = 15 min`. Jitter verhindert, dass ein Provider-Ausfall eine
  **Häufung** auslöst, wenn viele Jobs gleichzeitig in die Retry-Schleife laufen.
* **Max. 5 Versuche** je Job (konfigurierbar). Danach `DEAD_LETTER`.
* **Totmannschaltung ist sichtbar:** Einträge in `DEAD_LETTER` erscheinen in der
  Oberfläche (`GET /api/v1/prompts/{id}/status` meldet `retryable: true`; der
  Scan-Status steht auf `FAILED`).
* **Manuelle Wiederholung:** `POST /api/v1/prompt-versions/{versionId}/rescan` erzeugt einen
  **neuen** Job und setzt die Version `FAILED → PENDING` (FR-071). Der Vorgang wird im
  Audit protokolliert (`SCAN_RETRY_REQUESTED`, SEC-010).
* **Kein automatischer Übergang aus `DEAD_LETTER`:** Tote Jobs warten auf einen menschlichen
  oder betrieblichen Eingriff. Der Watchdog setzt die Version auf `FAILED`; ein automatischer
  Endlos-Retry würde den Provider dauerbelasten und die Fehlerquote verschleiern.

### 4. Stufen des Prüflaufs (FR-031)

```
Job übernommen
  └─ (a) Lokale Vorprüfung      Secrets/PII, deterministisch, ohne Provider
  └─ (b) Duplikatvergleich       nur eigene Organisation, nie blockierend
  └─ (c) LLM-Klassifikation      Provider-Verifikation VOR dem Aufruf
  └─ (d) Aggregation             Fundstellen >= Schwelle => FLAGGED, sonst APPROVED
```

* Die Reihenfolge ist **fix**: (a) und (b) laufen **vor** (c); ihre Fundstellen fließen in
  das Gesamturteil ein.
* (a) ist **Pflicht** und läuft immer, auch wenn (c) ausfällt. Erkannte Secrets werden also
  **auch dann** als Fundstelle gemeldet, wenn der Provider nicht erreichbar ist.
* (c) sendet den **Originalinhalt unverändert** (F1, → ADR-006).
* Schlägt eine Stufe technisch fehl, wird **kein** unvollständiges Ergebnis gespeichert
  (AC-022-3): die Fundstellen der Zwischenstufen werden verworfen, die Version geht auf
  `FAILED`.

### 5. Mandant im Job-Kontext (SEC-003)

* Jeder Job trägt `organisation_id` **im Datensatz** — nicht nur im Speicher.
* Beim Start setzt der Worker den Mandantenkontext der Transaktion auf diesen Wert; alle
  Queries des Laufs (Fundstellen, Version, Prompt) laufen innerhalb dieser Organisation.
* Ein Job **ohne** auflösbaren Tenant-Kontext (z. B. gelöschter Mandant) wird **verworfen
  und protokolliert** — er wird niemals „irgendwohin" ausgeführt.
* Der Prüfstatus `PENDING → SCANNING` wird im selben Commit geschrieben wie die
  Übernahme des Jobs.

### 6. Unabhängigkeit der Zustellung

Die Warn-E-Mail hat eine **eigene** Warteschlange und **eigene** Wiederholungslogik
(ADR-004, FR-052). Ein Zustellfehler blockiert den Prüfstatus **nicht** (AC-027-3). Der
Prüflauf erzeugt bei `FLAGGED` lediglich einen Zustellauftrag.

### 7. Skalierung (NFR-008)

| Maßnahme | Wirkung |
|---|---|
| `scan_job`-Index auf `(status, available_at, created_at)` | Job-Abholung ohne Seq-Scan |
| Worker als **eigene Deployable** (Container) | unabhängige Skalierung von API und Verarbeitung |
| Horizontal skalierbar durch `SKIP LOCKED` | keine Leader-Wahl nötig |
| `LISTEN/NOTIFY` als Wake-up, Polling (1 s) als Rückfall | geringe Latenz bei geringer Last |
| Batch-Verarbeitung von Fundstellen | `INSERT … ON CONFLICT` statt Einzel-Inserts |

**Kostenziel:** p95 Prüflaufdauer < 30 s, p99 < 120 s (NFR-002). Der Engpass ist in der
Regel der Provider, nicht die Queue.

## Konsequenzen

### Positiv

* **Kein Datenverlust** durch Transaktionskopplung: Version und Job entstehen oder gar nicht.
* **Neustartfest** — Jobs überleben Prozess- und Containerneustarts; `SKIP LOCKED` verhindert
  Doppelverarbeitung.
* **Geordnetes Nachholen** nach Provider-Ausfall (FR-070) mit Backoff und Jitter.
* **Sichtbarkeit** — Warteschlangentiefe, Jobalter und Fehlerquote sind Metriken
  (FR-072); tote Jobs sind in der Oberfläche sichtbar und manuell wiederholbar.
* **Keine zusätzliche Infrastruktur** — die Queue ist eine Tabelle im ohnehin vorhandenen
  PostgreSQL.

### Negativ / Kosten

* **Polling statt Push:** Der Worker fragt die Queue ab. Mit `LISTEN/NOTIFY` plus
  Polling-Fallback liegt die zusätzliche Latenz bei < 1 s. Der Polling-Anteil kostet
  dauerhaft eine kleine konstante DB-Last.
* **`FOR UPDATE SKIP LOCKED` erfordert eine Transaktion** um die Job-Abholung — kurze
  Transaktionen, sonst blockiert ein langer Lauf die Queue. Der Job wird **nicht** in der
  Abhol-Transaktion verarbeitet, sondern nur markiert.
* **Kein Re-Scan freigegebener Versionen** (siehe ADR-003) — der Job-Status `COMPLETED`
  beendet den Lebenszyklus des Jobs.
* **Dead-Letter ohne Auto-Recovery** verlangt ein Betriebs-SOP („wer schaut täglich in die
  Totmannschaltung?"). Der Alarm muss das abdecken (FR-072, NFR-014).

### Neutral

* Ein separater Job-Typ (`notification_delivery`) folgt derselben Technik; die beiden
  Warteschlangen sind organisatorisch getrennt, technisch identisch implementiert.

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| In-Memory-Queue | Verboten (CON-009) — Datenverlust bei Neustart. |
| Kafka / RabbitMQ | Betriebs- und Compliance-Aufwand steht nicht im Verhältnis zum Volumen; die Transaktionskopplung ginge verloren. |
| Cloud Tasks / SQS | Kein atomarer Commit mit dem Anlegen der Version; zusätzliche Provider-Abhängigkeit in der EU-/Souveränitätsfrage. |
| `SELECT … FOR UPDATE` ohne `SKIP LOCKED` | Worker blockieren sich gegenseitig; kein Durchsatz. |
| `ORDER BY random()` o. ä. | Reihenfolge nicht garantiert (AC-016-3). |
| Unbegrenztes Retry ohne Backoff | Provider-Häufung; verschleiert die Fehlerquote. |
| Status „PROCESSING" in `prompt_version` statt in `scan_job` | Zustand des Laufs und Zustand der fachlichen Version würden vermischt. |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-030, FR-031, FR-042, FR-070, FR-071), §12 (CON-009), §15 (K-1, K-6), §16 (R-03)
* `docs/architecture/diagrams/05-sequence-scan-process.mmd`, `04-component-scan-worker.mmd`
* `docs/api/openapi.yaml` — `rescanPromptVersion`, `PromptStatus.retryable`
