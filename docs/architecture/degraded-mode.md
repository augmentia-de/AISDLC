# PromptGuard — Degraded Mode

| Feld | Wert |
|---|---|
| **Dokument** | Degraded Mode: Betrieb ohne KI-Dienst |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | US-033, NFR-006, NFR-007, FR-070, FR-071, R-03, K-6, P2 |

---

## 1. Leitentscheidung

> **Der KI-Dienst ist eine Abhängigkeit der Prüfung, nicht der Registry.**

Die Kern-API (Anlegen, Lesen, Suchen, Versionieren) ist **unabhängig** vom KI-Dienst zu
99,9 % verfügbar (NFR-006). Fällt er aus, entsteht **kein Fehler in der Oberfläche**, **kein**
Datenverlust und **kein** Blockieren des Anlegens (NFR-007, US-033).

Das ist die technische Konsequenz aus drei Entscheidungen:

1. **CON-009 / ADR-005** — der Prüflauf ist **nicht** transaktional an den HTTP-Request
   gekoppelt; er läuft über eine **persistente** Warteschlange.
2. **K-1 / P3** — Speichern blockiert nie; die **Freigabe** ist das Gate.
3. **ADR-006** — die Provider-Verifikation ist ein **Gate**, kein Anzeigewert: bei Verstoß
   wird **gar nicht** aufgerufen.

---

## 2. Verhalten im Degraded Mode

| Aspekt | Normalbetrieb | Degraded Mode (KI nicht erreichbar) |
|---|---|---|
| `POST /prompts` | `201`, Version `PENDING`, Job eingereiht | **identisch** — `201`, Version `PENDING`, Job eingereiht |
| Antwortzeit | p95 < 500 ms | **identisch** — der KI-Dienst ist nicht im Request-Pfad |
| Version nach dem Speichern | `SCANNING` → `APPROVED`/`FLAGGED` | `PENDING` (wartet) → ggf. `FAILED` nach 5 Versuchen |
| `GET /prompts` | vollständig | **vollständig** — alle Daten da, nur der Status ist `PENDING`/`FAILED` |
| `GET /prompts/{id}/status` | aktueller Status | aktueller Status + `retryable: true` bei `FAILED` |
| Oberfläche | Status-Update per SSE | Status-Update per SSE; Hinweis „Prüfung verzögert" |
| Health | `UP` | `DEGRADED` (nicht `DOWN`) |
| Metriken | `queueDepth` niedrig | `queueDepth` **steigt**; `errorRate` steigt; Alarm bei > 1 000 Jobs |

**Kein** Request schlägt mit einem KI-bezogenen Fehler fehl (AC-033-1). Ein Fehler im
Scan-Pfad wird **nicht** in den Schreibpfad propagiert.

---

## 3. Drei Ausprägungen

### 3.1 Der KI-Dienst ist langsam

* Der Prüflauf läuft, dauert aber länger. Die Warteschlange wächst.
* Es greift **kein** Timeout für den Aufrufer — der Aufrufer hat längst eine `201` bekommen.
* Der Provider-Timeout (Default 60 s) ist ein **Worker**-Timeout, kein API-Timeout.
* Alarm: `queueDepth` bzw. Alter des ältesten Jobs.

### 3.2 Der KI-Dienst ist nicht erreichbar

* Jeder Job läuft in den Backoff, nach 5 Versuchen in die **Totmannschaltung**.
* Die Version geht auf `FAILED` — **sichtbar**, **benannt** (`PROVIDER_UNAVAILABLE`) und
  **reproduzierbar** (Retry-Option).
* Der Aufrufer sieht keinen Fehler; er sieht einen Prompt mit Status `FAILED`.

### 3.3 Die Provider-Konfiguration verletzt die Auflagen

* Die Verifikation schlägt **vor** dem Aufruf fehl ⇒ **kein** externer Aufruf.
* Die Version geht auf `FAILED` mit `PROVIDER_POLICY_VIOLATION`.
* **Kein Inhalt verlässt das System** (FR-038, AC-022-2).
* Dieser Fall ist **nicht** automatisch wiederholbar — zuerst muss die Konfiguration
  korrigiert werden; danach genügt ein manueller Retry.

> **Unterschied zu 3.2:** Ein Ausfall ist transient (Retry hilft), eine Policy-Verletzung
> ist deterministisch (Retry bringt nichts). Deshalb ist der Fehlercode für den Betrieb
> unterschiedlich — und die Totmannschaltung wird **nicht** mit "nochmal versuchen"
> geflutet.

---

## 4. Zustandsübergänge im Degraded Mode

```
Version angelegt
      │
      ▼
   PENDING ────── Job in Warteschlange ──────▶ (wartet)
      ▲                                              │
      │                                   KI-Dienst nicht erreichbar
      │                                              ▼
      │            Retry (Backoff) ◀────── SCANNING ──▶ FAILED (DEAD_LETTER)
      │                                              │
      └──── manuelle Wiederholung ──────────────────┘
                 (POST /prompt-versions/{id}/rescan)
```

* **Die Reihenfolge bleibt erhalten:** Jobs werden mit `ORDER BY created_at, id` abgearbeitet.
  Nach der Recovery werden die aufgestauten Jobs **in ihrer ursprünglichen Reihenfolge**
  verarbeitet (AC-033-2), und jede Version erreicht einen Endzustand.
* **Kein Job geht verloren:** Der Job und die Version entstehen in derselben Transaktion.
* **Kein Worker-Neustart verliert Jobs:** Die Warteschlange ist eine Tabelle (CON-009).

---

## 5. Nutzererfahrung

| Zustand | Anzeige | Aktion |
|---|---|---|
| `PENDING` (wartet) | „Prüfung eingereiht" + Hinweis auf mögliche Verzögerung | keine |
| `SCANNING` | „Prüfung läuft" (Live-Status) | keine |
| `FAILED` | „Prüfung technisch fehlgeschlagen — <Fehlerklasse>" | **Retry-Button** (immer sichtbar) |
| `FLAGGED` | „Risiko erkannt" + Fundstellen | Prüfen, ggf. Waiver |

* `retryable` ist Teil des Live-Status: Bei `FAILED` zeigt die Oberfläche **immer** eine
  Retry-Option (AC-033-3, P2 — „nicht prüfbar" ≠ „verloren").
* Die Oberfläche zeigt **keine** KI-bezogenen Fehlermeldungen im Anlegepfad — der Nutzer
  erfährt nur, dass die Prüfung aussteht.
* Ein Fehlerbanner erscheint **nur** als Betriebshinweis („Der Prüfdienst ist gerade nicht
  erreichbar — Prüfungen verzögern sich"), nicht als persönlicher Fehler.

---

## 6. Wiederanlauf

| Schritt | Maßnahme |
|---|---|
| 1 | Provider-Verifikation erneut auslösen (`POST /provider/verification`) — sie schlägt fehl, solange die Konfiguration nicht passt |
| 2 | Konfiguration korrigieren (AVV, EU-Region, Trainingsverbot, Löschgarantie) |
| 3 | Verifikation erneut auslösen ⇒ `verified: true` |
| 4 | Totmannschaltung sichten: `GET /notifications` bzw. die Dead-Letter-Jobs im Betrieb |
| 5 | Versionen mit `FAILED` manuell erneut auslösen (`POST /prompt-versions/{id}/rescan`) — oder geordnet nachholen, indem die Warteschlange wieder freigegeben wird |
| 6 | `queueDepth` und `errorRate` beobachten, bis der Rückstand abgearbeitet ist |

> **„Geordnet nachholen“** bedeutet: Die Warteschlange arbeitet die aufgestauten Jobs in
> Erstellungsreihenfolge ab. Es gibt **keinen** Priority-Sprung und **keinen** Burst, der
> den Provider überlastet — die Warteschlangentiefe ist ein **Dämpfer**, kein Stau ohne
> Ausgang.

---

## 7. Grenzen des Degraded Mode

| Grenze | Wirkung |
|---|---|
| Der Status bleibt `PENDING`/`FAILED` — der Prompt ist **nicht nutzbar** (I-3) | gewollt: „nicht prüfbar" ist nicht „freigegeben" |
| Die Warteschlange wächst unbegrenzt | gedeckelt durch Alarm und Betriebs-SOP; ein Backpressure-Mechanismus (Ablehnung neuer Prompts) ist **nicht** vorgesehen — das widerspräche „Anlegen bleibt möglich" |
| `DEGRADED` ist kein Fehler | Health meldet `200` mit `status: DEGRADED` — Monitoring entscheidet, nicht der Client |

---

## 8. Abdeckung der Akzeptanzkriterien

| AK | Erwartung | Umsetzung |
|---|---|---|
| AC-033-1 | KI-Dienst aus, Queue füllt sich ⇒ API antwortet normal, Jobs warten, **kein** Anfragefehler, **kein** Datenverlust | Trennung Request/Job (ADR-005), transaktionale Kopplung |
| AC-033-2 | Nach Recovery werden die Jobs in ursprünglicher Reihenfolge verarbeitet, jede Version erreicht einen Endzustand | `ORDER BY created_at, id`, Watchdog, Retry-Pfad |
| AC-033-3 | `FAILED` ⇒ neuer Job, Version `PENDING` → Endzustand, Retry im Audit | `rescanPromptVersion`, `SCAN_RETRY_REQUESTED` (SEC-010) |

---

## 9. Verweise

* `docs/adr/ADR-005-async-scan-architecture.md` — Warteschlange, Backoff, Totmannschaltung
* `docs/adr/ADR-006-ai-provider-integration-and-verification.md` — Provider-Gate
* `docs/architecture/error-retry-strategy.md` — Fehlerklassen und Wiederholung
* `docs/architecture/observability.md` — `DEGRADED`, Alarme
* `docs/api/openapi.yaml` — `rescanPromptVersion`, `PromptStatus.retryable`, `getHealth`
* `docs/requirements/PRD.md` — NFR-006, NFR-007, FR-070, FR-071, US-033, R-03, K-6
