# ADR-008 — Live-Transport des Prüfstatus: Server-Sent Events (SSE) mit Polling-Fallback

| Feld | Wert |
|---|---|
| **ID** | ADR-008 |
| **Titel** | Live-Transport des Prüfstatus: SSE mit Polling-Fallback |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch K-9 |
| **Betrifft** | FR-040, FR-041, NFR-003, NFR-014, SEC-003, K-9, R-01 |
| **Supersediert** | — |

## Kontext

**FR-040** verlangt: „Der Prüfstatus wird in der UI **ohne Neuladen** aktualisiert, wenn er
wechselt." Der Transportmechanismus (SSE oder Polling) ist laut PRD ausdrücklich eine
**Architekturentscheidung der Phase 2**; die beobachtbare Anforderung ist fest: Der
Statuswechsel erscheint **≤ 2 s** nach Abschluss des Prüflaufs (NFR-003).

Aus dem PRD bereits festgelegt:

* **K-9** — „Realtime-Status in der UI ↔ stateless Cloud Run": Der Transport muss
  serverloskompatibel sein; SSE (oder Polling) läuft serverloskompatibel.
* **NFR-006** — Verfügbarkeit der Kern-API **unabhängig** vom KI-Dienst; 99,9 %.
* **SEC-003** — jeder Hintergrundjob trägt den Mandantenkontext; ein Kanal darf keine
  mandantenübergreifenden Ereignisse ausliefern (R-01).
* **AC-024-2** — ohne gültiges Token wird die Verbindung **abgelehnt**, es werden keine
  Statusdaten gestreamt.
* **AC-024-3** — nach einem Verbindungsabbruch erhält der Client den **aktuellen** Zustand;
  keine Zwischenaktualisierung wird doppelt und mit widersprüchlichem Zustand zugestellt.

Zu entscheiden ist: Welcher Transport, wie wird der Zustand technisch verteilt, wie bleibt
die Mandantentrennung auf dem Kanal garantiert, und was passiert bei Verbindungsabriss.

## Entscheidung

### 1. Transport: SSE, Polling als Fallback

| Kandidat | Bewertung |
|---|---|
| **Server-Sent Events (SSE)** | **Gewählt.** Unidirektionaler Kanal — genau die Richtung, die gebraucht wird (Server → Client). Läuft über normales HTTP/1.1, braucht keinen eigenen Port, keinen WebSocket-Handshake, funktioniert hinter Cloud Run und jedem HTTP-Proxy ohne Sonderkonfiguration. `Last-Event-ID` und `retry:` sind Bestandteil des Protokolls. Browser bauen die Verbindung automatisch neu auf (`EventSource`), inklusive Reconnect-Backoff. |
| WebSocket | Bidirektional, wird hier aber nicht gebraucht. Braucht eigene Infrastruktur, eigene Auth-Handshake-Regeln und Proxy-Konfiguration (Upgrade-Header) — Betriebsaufwand ohne Nutzen. |
| Polling | Einfach, robust, aber **pollt**: Bei 10 offenen Listenansichten entstehen 10 × frequente Abfragen. Erzeugt Dauerlast auf API und DB und verletzt das Ziel „≤ 2 s" ohne enge Polling-Intervalle. **Bleibt aber als Fallback erhalten** (siehe 3.). |
| Push über Web-Push / FCM | Braucht Client-Zustimmung und Service-Worker; für eine interne Anwendung Overkill. |
| Datenbank-Notify ohne Broker | Nicht clientfähig; nur für Worker→Worker (siehe ADR-005). |

### 2. Verteilung: Outbox-Tabelle + Sequenz, kein In-Memory-Broker

Der Statuswechsel wird **transaktional mit dem Statuswechsel selbst** in eine Outbox
geschrieben (`status_event`). Der SSE-Endpunkt liest die Outbox und verfolgt eine
**monoton steigende Sequenz** (`sequence`, `BIGINT`):

```
Statuswechsel (Transaktion)              SSE-Client
────────────────────────────             ──────────
UPDATE prompt_version SET status=…   →   SELECT … FROM status_event
INSERT status_event (sequence=42)         WHERE sequence > 37
                                          ORDER BY sequence
INSERT audit_event (SCAN_COMPLETED)  →   → event: status-changed
COMMIT
```

* **Warum Outbox statt `LISTEN/NOTIFY`:** `LISTEN/NOTIFY` ist flüchtig — ein Consumer, der
  gerade nicht hört, verpasst das Ereignis. Die Outbox ist persistent; ein Neustart des
  API-Service verliert keinen Statuswechsel. `LISTEN/NOTIFY` darf als **Weckruf**
  optimieren (der Worker weckt den API-Service), die **Quelle der Wahrheit** bleibt die
  Tabelle.
* **Warum Sequenz statt Zeitstempel:** Zeitstempel können kollidieren und sind nicht
  monoton. Die Sequenz ist total geordnet — damit ist AC-024-3 („keine Zwischenaktualisierung
  doppelt und mit widersprüchlichem Zustand") strukturell erfüllt: ein Client verwirft
  Ereignisse mit `sequence <= zuletzt gesehen`.
* **Transaktionale Kopplung:** Der Statuswechsel, der Audit-Eintrag und das Outbox-Ereignis
  entstehen **im selben Commit**. Es gibt keinen Status ohne Historie und keinen Status ohne
  Ereignis.

### 3. Zwei Wege im Vertrag, ein Vertrag

| Weg | Operation | Zweck |
|---|---|---|
| Push | `GET /api/v1/prompts/{promptId}/status-stream` | Live-Kanal für eine Version (Detailansicht) |
| Pull | `GET /api/v1/prompts/{promptId}/status` | Aktueller Zustand + Sequenz — **Snapshot** und Polling-Fallback |

* **Snapshot beim Verbindungsaufbau:** Der SSE-Endpunkt sendet unmittelbar ein
  `snapshot`-Ereignis mit dem aktuellen Zustand. Damit ist der Reconnect-Fall
  (AC-024-3) ohne Sonderlogik gelöst: Der Client weiß nach dem Wiederverbinden sofort den
  Stand und holt danach nur noch die Ereignisse mit `sequence > Stand`.
* **Polling-Fallback bleibt:** Browser ohne `EventSource`-Unterstützung, aggressive Proxies
  oder der Wunsch nach reduzierter Verbindungszahl nutzen `GET …/status` im Intervall.
  Beide Wege liefern **denselben** Zustand und dieselbe Sequenz — sie sind austauschbar.

### 4. Mandantentrennung auf dem Kanal

* Der Kanal ist **authentifiziert** (Bearer-JWT). Ohne gültiges Token wird die Verbindung
  abgelehnt — es werden **keine** Statusdaten gestreamt (AC-024-2).
* Der Kanal ist **organisationsgebunden**: Die Abfrage filtert über `organisation_id` aus
  dem Token; ein Fremd-Prompt liefert `404` und eröffnet **keinen** Kanal.
* Es gibt **keinen** organisationsübergreifenden Broadcast. Ein Kanal liefert ausschließlich
  Ereignisse der eigenen Organisation — auch dann, wenn der Plattform-Betrieb den Kanal
  mitliest (er liest `GET /ops/metrics`, nicht den Kanal).
* Die Outbox trägt `organisation_id` und ist Teil der RLS-Tabellen (ADR-002).

### 5. Betriebsregeln

| Regel | Wert | Grund |
|---|---|---|
| Heartbeat | alle 15 s | Hält Proxies und Load Balancer bei offener Verbindung |
| `retry:` (Reconnect-Backoff) | 2 s (Server-Vorschlag) | Kurze Wiederaufnahme, aber kein Hot-Loop |
| Maximale Verbindungsdauer | 30 min, danach Client-Reconnect | Ressourcenfreigabe, Lastverteilung über Instanzen |
| Ereignis pro Statuswechsel | 1 | Der Zustand ist ein Zustand — keine Ereignisflut |
| Backpressure | Langsame Clients werden mit `snapshot` nachgezogen | Kein unbegrenzter Puffer pro Client |

### 6. Grenzen

* **SSE ist unidirektional.** Kommandos laufen über die regulären REST-Operationen
  (z. B. `POST …/rescan`), nie über den Kanal.
* **Zustandsverlust ist möglich** (Verbindung bricht genau beim Commit ab) — deshalb ist der
  Snapshot beim Verbindungsaufbau **verpflichtend**, nicht optional.
* **Ein Status pro Verbindung:** Der Kanal meldet Statuswechsel, keine beliebigen
  Nutzerereignisse. Erweiterung um weitere Ereignistypen ist später möglich, ändert aber
  den Vertrag (Revision).

## Konsequenzen

### Positiv

* **Latenz:** Der Statuswechsel ist innerhalb der geforderten 2 s sichtbar (NFR-003) —
  ohne Polling-Last.
* **Weniger Dauerlast:** 1 Verbindung je Client statt vieler Abfragen; die DB-Last skaliert
  mit offenen Kanälen, nicht mit der Zahl der Statuswechsel.
* **Robustheit:** Outbox + Sequenz überstehen Neustarts; `EventSource` verbindet sich
  automatisch neu.
* **Rückfall ebene eingebaut:** `GET …/status` funktioniert immer, auch wenn SSE blockiert
  wird.
* **Betrieb:** Kein zusätzlicher Broker, kein zusätzlicher Port, keine Proxy-Sonderkonfiguration.

### Negativ / Kosten

* **Zustandshaltung im API-Service:** Offene SSE-Verbindungen belegen Speicher. Begrenzung
  über Connection-Zeit und maximale Verbindungen je Instanz; Skalierung über mehrere
  Instanzen (jede Instanz liest dieselbe Outbox — die Sequenz ist global monoton).
* **Verbindungsabbrüche:** Der Client muss den Snapshot verarbeiten; das ist eine
  clientseitige Pflicht, die in `AC-024-3` als Akzeptanzkriterium festgeschrieben ist.
* **Kein Mehrbenutzer-Broadcast:** Wer „die Organisation live beobachten" will, öffnet
  je Prompt einen Kanal. Für den MVP (interne Fachbereiche, wenige parallele Ansichten)
  vertretbar; ein organisationsweiter Sammelkanal ist eine spätere Erweiterung.

### Neutral

* Die Outbox ist eine **zusätzliche Tabelle** mit geringem Volumen (ein Zeile je
  Statuswechsel). Sie wird nach 90 Tagen mit der Telemetrie gelöscht (ADR-004).

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| WebSocket | Unidirektionaler Bedarf; zusätzlicher Handshake und Proxy-Konfiguration. |
| Polling als einziger Mechanismus | Pollt, erzeugt Dauerlast und verfehlt das 2-s-Ziel ohne enge Intervalle. |
| `LISTEN/NOTIFY` als einzige Quelle | Flüchtig — verpasste Ereignisse sind nicht rekonstruierbar. |
| Ereignisbus (Kafka/RabbitMQ) | Betriebs- und Compliance-Aufwand ohne Nutzen bei diesem Volumen (ADR-005). |
| Web-Push | Braucht Client-Zustimmung und Service-Worker; für internes Tool Overkill. |
| Long Polling | Hält Threads/Verbindungen gebunden; schlechter als SSE unter Last. |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-040, FR-041), §9 (NFR-003), §15 (K-9), §16 (R-01)
* `docs/architecture/diagrams/05-sequence-scan-process.mmd`, `02-container.mmd`
* `docs/api/openapi.yaml` — `streamPromptStatus`, `getPromptStatus`, `PromptStatus.sequence`
* `docs/adr/ADR-005-async-scan-architecture.md` — Warteschlange und Job-Zustände
* `docs/adr/ADR-002-multi-tenancy-and-authorisation.md` — Mandantengrenze auf dem Kanal
