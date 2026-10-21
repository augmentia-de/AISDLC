# ADR-006: Zustellprozess — Zustandsmodell und Hintergrundprozess

## Kontext

NFR-002: *„Das Einliefern eines Ereignisses antwortet in unter 200 ms (p95).
Die Zustellung ist asynchron und darf die Antwort nicht verzögern."*
Abschnitt 11 der Anforderung: *„Die Zustellung erfolgt aus einem
Hintergrundprozess, nicht aus dem Request-Thread. Ein Empfänger, der 30
Sekunden braucht, darf den Annahme-Request nicht blockieren."*
D-10 trennt Annahme und Zustellung.

Offen sind die Betriebsfragen aus dem PRD (Abschnitt 12):
*„Zustellmechanismus des Hintergrundprozesses (Worker, Übernahme nach Absturz)"*.
AC-023-3 verlangt: bei zwei parallelen Instanzen führt **genau eine** Instanz
den Versuch aus. AC-023-4 verlangt: nach einem Absturz geht das Ereignis nicht
verloren, und die Versuchsanzahl steigt **nicht** sprunghaft.

## Entscheidung

### 1. Die Warteschlange ist die Tabelle `delivery_runs`

Es gibt **keine** separate Queue-Tabelle. Ein Zustelllauf ist zugleich
Warteschlangeneintrag und Protokollzeile.

- **Warum:** Ein transaktionales Outbox-Muster mit *einer* Tabelle hat keine
  doppelte Wahrheit. Ereignis und Zustellläufe entstehen in **einer**
  Transaktion: entweder gibt es das Ereignis **und** seine Zustellläufe, oder
  nichts. Eine separate Queue müsste synchron gehalten werden — genau die
  „zweite Zustandshaltung“, die die Anforderung vermeiden will.
- **Kein Message-Broker:** Ausdrücklich ausgeschlossen (Abschnitt 2).

### 2. Zwei Laufzeitrollen, ein Image

| Rolle | Prozess | Aufgabe |
|---|---|---|
| `api` | HTTP-Server | Annahme, Endpunktverwaltung, Protokoll, Totenliste, Kennzahlen |
| `worker` | Hintergrundprozess | Zustellläufe beanspruchen, senden, wiederholen, protokollieren |

Beide Rollen teilen sich Code und Schema, laufen aber als **getrennte
Container**. Die Zustellung ist damit per Deployment trennbar (Skalierung des
Workers unabhängig vom API-Traffic) und per Mandant vorbereitet (ADR-013).

### 3. Beanspruchung: `FOR UPDATE SKIP LOCKED` + Lease

Der Worker arbeitet **mandantenweise** (ADR-002 §5, N-02). Die Kette ist:

```
Scheduler -> app.tenant_ids_for_worker() -> je Mandant:
  BEGIN; SELECT app.set_tenant(:mandant); SELECT … (Claim) …; COMMIT;
```

**Ohne** die Mandantenermittlung und **ohne** `set_tenant` wäre die
Beanspruchung mandantenlos — und liefert unter RLS **0 Zeilen**: es würde
**nichts** zugestellt, ohne Fehler und ohne Logeintrag (AC-023-3, AC-023-4,
Z1). Je Mandant läuft **eine eigene Transaktion**; der Mandant wird
**innerhalb** dieser Transaktion gesetzt, die auch liest (ADR-002 §3).

```sql
-- je Mandant, begrenzte Zahl
SELECT delivery_run_id FROM app.delivery_runs
WHERE organisation_id = :mandant
  AND status IN ('pending','scheduled','failed','delayed')
  AND next_attempt_at <= now()
ORDER BY next_attempt_at
LIMIT :batch
FOR UPDATE SKIP LOCKED;
```

- `SKIP LOCKED` sorgt dafür, dass konkurrierende Instanzen sich **nicht**
  blockieren, sondern aneinander vorbeigehen — und dass **genau eine** Instanz
  einen Lauf beansprucht (AC-023-3).
- Der Lauf wird zusätzlich mit `lease_owner` und `lease_expires_at` markiert.
  Die Lease ist der Grund, warum ein Lauf nach einem **Absturz** wieder
  freigegeben wird: eine Instanz, die mitten im Versuch stirbt, hinterlässt
  eine abgelaufene Lease, und ein anderer Worker nimmt den Lauf danach wieder
  auf (AC-023-4).

### 4. Übernahme nach Absturz

| Situation | Verhalten |
|---|---|
| Worker stirbt **vor** dem Versuch | Lease läuft ab → der Lauf wird wieder fällig; die Versuchszahl bleibt unverändert |
| Worker stirbt **während** des Versuchs | Die tatsächliche Zustellung ist ungewiss (Timeout beim Empfänger) → der Lauf wird nach Lease-Ablauf erneut versucht. Das ist der Preis von at-least-once: der Empfänger **kann** eine Dublette sehen — erkennbar an gleicher Zustelllauf-ID und anderer Versuchs-ID (ADR-005) |
| Lease läuft ab, während der Versuch noch läuft (langsamer Empfänger) | Die Lease ist **länger** als das Zeitlimit je Versuch (15 s, ADR-008); die Lease wird erst **nach** Abschluss des Versuchs freigegeben. Eine abgelaufene Lease während eines laufenden Versuchs ist damit ausgeschlossen. |

**Die Versuchsanzahl steigt nicht sprunghaft** (AC-023-4): sie wird nur erhöht,
wenn ein Versuch **tatsächlich** stattgefunden hat — ein übernommener Lauf
erhöht sie nicht.

### 5. Zustandsautomat

Der Zustandsautomat ist in `docs/architecture/domain_model.md` festgeschrieben
und im Code **an einer Stelle** implementiert (`DeliveryStateMachine`). Kein
anderer Code darf einen Zustand ändern. Jede Änderung wird mit
`updated_at` und — für die zustandsrelevanten Felder — im Protokoll
nachvollziehbar gehalten.

### 6. Der Replay ist **einmal** möglich

Ein aufgegebener Zustelllauf (`abandoned`) kann **einmal** replayed werden
(FR-024, AC-013-1, AC-013-5). Das ist **auf Datenbankebene** erzwungen:

```sql
CONSTRAINT delivery_runs_replay_of_uq UNIQUE (replay_of)
```

Ein zweiter Replay desselben Vorgängerlaufs verstößt gegen die Constraint ⇒
**HTTP 409** `delivery_state_conflict`; es entsteht **kein** weiterer
Zustelllauf. Grund: der Empfänger soll nicht mehrere Zustellläufe derselben
Ereignis-ID unkontrolliert sehen — der Historiegedanke lebt davon, dass jeder
Replay eine begründete, einmalige Aktion ist. Der Vorgängerlauf bleibt
unverändert erhalten (AC-013-2).

**Der Verweis auf den Vorgänger ist historisch, kein Live-Bezug (E-11).**
`replay_of` ist **kein Fremdschlüssel**: die UUID bleibt stehen, auch wenn der
Vorgängerlauf später aufbewahrt (purged) wird. Mit einem Fremdschlüssel
(`ON DELETE SET NULL`) verschwände der Verweis beim Purge — der überlebende
Replay wäre nicht mehr als Replay erkennbar (AC-013-2 verletzt). Die
Einmal-Regel `UNIQUE (replay_of)` bleibt davon unberührt.

**Konkurrenz und Sperrenwartezeit (N-20).** Zwei gleichzeitige Replays
desselben Laufs gewinnen genau **einer**; der Verlierer wartet auf dem
UNIQUE-Index und erhält dann **SQLSTATE 23505** (`unique_violation`), das
**ausschließlich** auf HTTP 409 `delivery_state_conflict` abgebildet wird
(ADR-015 §6). Damit die Wartezeit nicht das HTTP-Timeout übersteigt, setzt
die Replay-Transaktion **ein `lock_timeout` und ein `statement_timeout`, die
kürzer sind als das HTTP-Timeout** — der verlierende Replay antwortet dann mit
409 statt mit einem Timeout. *Vorgabe für Schritt 6:* beide Zeitlimits werden
**vor** dem `INSERT` in derselben Transaktion gesetzt und kommen aus der
Konfiguration; kein Wert ist im Code verdrahtet.

### 7. Endegültige Zustände

`sent`, `abandoned`, `rejected`, `cancelled`, `no_subscriber` sind endgültig:
`next_attempt_at` ist dann **NULL** (DB-Constraint
`delivery_runs_terminal_no_next_attempt_ck`). Es wird kein Zeitpunkt in der
Vergangenheit oder in der Zukunft angezeigt (AC-015-2).

## Konsequenzen

- **Positiv:** Die Annahme antwortet unabhängig von der Empfänger-Geschwindigkeit
  (NFR-002, AC-005-4).
- **Positiv:** Kein Broker, keine zweite Wahrheit, kein Betriebszustand im
  Speicher — ein Neustart verliert nichts (AC-023-4).
- **Positiv:** Der Worker skaliert horizontal, ohne dass die API skaliert werden
  muss.
- **Negativ:** Der Worker pollt die Datenbank. Das ist eine Last, die mit der
  Zustellmenge wächst — abhilfbar über ein kürzeres Pollintervall oder eine
  `LISTEN/NOTIFY`-Anstoßung; die Poll-Variante ist der einfachere und
  robustere Start (AC-023-3 verlangt ohnehin, dass **genau eine** Instanz
  zugreift — das regelt die Datenbank, nicht der Broker).
- **Negativ:** Beim Absturz **während** eines Versuchs ist nicht entscheidbar,
  ob der Empfänger verarbeitet hat. at-least-once heißt: das wird in Kauf
  genommen und durch die Kennungen erkennbar gemacht.
- **Negativ:** Die Zustandslogik liegt an einer Stelle, aber die Versuche
  verteilen sich auf Worker — der Zustand muss deshalb in der Datenbank stehen,
  nicht im Speicher.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Zustellung im Request-Thread** | Bricht NFR-002 und die Rahmenbedingung aus Abschnitt 11. |
| **Eigene Queue-Tabelle neben `delivery_runs`** | Zwei Wahrheiten, die synchron gehalten werden müssen — genau das, was die Anforderung vermeiden will. |
| **Message-Broker (Kafka/RabbitMQ)** | Ausdrücklich ausgeschlossen. |
| **`LISTEN/NOTIFY` statt Polling** | Notify ist nicht dauerhaft — nach einem Neustart des Empfängers gehen Signale verloren. Als **Optimierung** über dem Polling später möglich, nicht als Grundlage. |
| **Verteilte Sperre (Redis/Redlock)** | Eine zusätzliche Abhängigkeit und ein zusätzlicher Ausfallpunkt für ein Problem, das die Datenbank bereits löst. |
| **Beliebig viele Replays desselben Laufs** | Der Empfänger sähe mehrere Zustellläufe derselben Ereignis-ID ohne Begründung; die Historie verlöre ihren Aussagewert. |
| **Verzicht auf die Lease, nur `SKIP LOCKED`** | Die Transaktion endet beim Commit — ein Absturz **während** des HTTP-Versuchs (außerhalb der Transaktion) würde den Lauf freigeben, während der Versuch noch läuft. Die Lease schließt dieses Fenster. |
| **Genau ein Worker (Singleton)** | Widerspricht der horizontalen Skalierbarkeit (NFR-006). |

## Status

**Angenommen** — bei Gate 1 am 2026-10-21 bestätigt.
