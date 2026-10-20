# PromptGuard — Fehler- und Retry-Strategie

| Feld | Wert |
|---|---|
| **Dokument** | Fehlerbehandlung, Wiederholung, Totmannschaltung |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | ADR-005 (Warteschlange), ADR-009 (Fehlermodell), FR-070, FR-052, US-027, US-033 |

---

## 1. Grundsatz

Es gibt **drei** Fehlerklassen, und sie werden **nicht** gleich behandelt:

| Klasse | Beispiel | Wirkung auf den Nutzer | Wiederholung |
|---|---|---|---|
| **A — Fachlicher Fehler** | Kategorie im Konflikt, E-Mail belegt, Waiver ohne Begründung | verständliche `4xx`-Antwort | nein (der Nutzer korrigiert) |
| **B — Technischer Fehler (transient)** | Provider-Timeout, SMTP nicht erreichbar, DB-Timeout | `FAILED` bzw. `RETRYING` — **reproduzierbar** | **ja**, mit Backoff und Totmannschaltung |
| **C — Technischer Fehler (permanent / Policy)** | Provider-Konfiguration verletzt, Inhalt ungültig | `FAILED` mit Fehlerklasse | **nein** — der Grund muss behoben werden |

**Leitsatz:** Ein Fehler, der einen Zustand erzeugt, muss **sichtbar, benannt und
reproduzierbar** sein. „Versteckt im Log" ist keine Fehlerbehandlung (P2, K-6).

---

## 2. Fehlerformat (ADR-009)

Alle Fehlerantworten nutzen **ein** Format (RFC 9457, erweitert):

```json
{
  "type": "https://api.promptguard.example/errors/provider-unavailable",
  "title": "Provider unavailable",
  "status": 500,
  "detail": "The classification provider is temporarily unavailable. The prompt is saved; retry the scan later.",
  "instance": "/api/v1/prompts",
  "correlationId": "01J8Z6M2QK9Y3F7V0C4T5N6P7R"
}
```

| Feld | Pflicht | Regel |
|---|---|---|
| `type` | ja | stabile URI der Fehlerklasse |
| `title` | ja | englische, maschinenlesbare Fehlerklasse |
| `status` | ja | identisch mit dem HTTP-Status |
| `detail` | ja | sagt, **was zu tun ist** |
| `instance` | ja | Pfad der Anfrage |
| `correlationId` | ja | verknüpft Antwort mit den Logs |
| `errors` | bei `400`/`422` | Feld, stabiler Code, Meldung |

**Verboten in Fehlerantworten:** Prompt-Inhalt, Secrets, Tokens, personenbezogene Daten,
Stacktraces, SQL-Fragmente, Existenzhinweise auf fremde Objekte.

---

## 3. Fehlerklassifikation im Scan-Worker

| Fehlerklasse | Erkennung | Behandlung | Endzustand |
|---|---|---|---|
| `PROVIDER_POLICY_VIOLATION` | Provider-Verifikation schlägt fehl (kein AVV, falsche Region, Training aktiv, dauerhafte Speicherung) | **kein** externer Aufruf | `FAILED`, **nicht** automatisch wiederholbar |
| `PROVIDER_UNAVAILABLE` | Timeout, 5xx, Verbindung verweigert | Backoff, max. 5 Versuche | `FAILED` + `DEAD_LETTER` |
| `PROVIDER_TIMEOUT` | Zeitüberschreitung | Backoff | wie oben |
| `PROVIDER_INVALID_RESPONSE` | Antwort nicht parsebar / Schema-Verstoß | Backoff, danach `FAILED` | wie oben |
| `EMBEDDING_UNAVAILABLE` | Duplikatvergleich nicht möglich | **Stufe (b) wird übersprungen**, Stufe (c)/(d) laufen weiter | `APPROVED`/`FLAGGED` — der Duplikathinweis ist **beratend** |
| `SMTP_UNAVAILABLE` | Zustellung schlägt fehl | Backoff, max. 6 Versuche | `DEAD_LETTER`, **Prüfstatus unberührt** |
| `DATABASE_ERROR` | DB-Fehler | Transaktion wird zurückgerollt, Job bleibt `RUNNING` → Watchdog | `FAILED` nach Timeout |
| `INTERNAL_ERROR` | alles andere | keine Teilzustände speichern | `FAILED` |

> **Wichtig (K-8 / ADR-005):** Schlägt eine Stufe technisch fehl, wird **kein**
> unvollständiges Ergebnis gespeichert (AC-022-3). Die lokale Vorprüfung (a) ist die
> Ausnahme: Sie läuft **immer** und liefert auch dann Secret-/PII-Fundstellen, wenn der
> Provider nicht erreichbar ist — sie ist die Grundlage für `FLAGGED` und die einzige
> **lokale** Erkennungsspur.

---

## 4. Wiederholung — Prüflauf (US-016, US-033, FR-070)

| Parameter | Wert | Begründung |
|---|---|---|
| Maximale Versuche je Job | **5** | genug für kurze Störungen, wenig genug für schnelles `FAILED` |
| Backoff | `min(base × 2^(attempt-1), max) + jitter` | exponentiell, mit Jitter gegen Häufung |
| `base` | 10 s | |
| `max` | 15 min | Deckel, damit ein Ausfall nicht stundenlang blockiert |
| Jitter | 0…30 % des aktuellen Intervalls | verhindert, dass viele Jobs gleichzeitig erneut laufen |
| Reihenfolge | `ORDER BY created_at, id` | „in ursprünglicher Reihenfolge" (AC-016-3) |

**Zustände des Jobs**

```
QUEUED ──▶ RUNNING ──▶ COMPLETED
   ▲          │
   │          ├── Fehler, Versuche < max ──▶ QUEUED (available_at = jetzt + Backoff)
   │          └── Fehler, Versuche = max ──▶ DEAD_LETTER
   └── Watchdog: RUNNING mit veraltetem locked_at ──▶ QUEUED
```

* **Kein automatischer Übergang aus `DEAD_LETTER`.** Tote Jobs warten auf einen menschlichen
  oder betrieblichen Eingriff. Ein Endlos-Retry würde den Provider dauerbelasten und die
  Fehlerquote verschleiern.
* **Manuelle Wiederholung:** `POST /prompt-versions/{id}/rescan` erzeugt einen **neuen** Job
  und setzt die Version `FAILED → PENDING` (FR-071). Der Vorgang wird im Audit protokolliert
  (`SCAN_RETRY_REQUESTED`, SEC-010).
* **Watchdog:** Ein Job im Status `RUNNING`, dessen `locked_at` älter als `scan_timeout`
  (5 min) ist, wird zurückgesetzt; parallel setzt der Watchdog die Version auf `FAILED`
  (FR-042).

---

## 5. Wiederholung — Zustellung der Warn-E-Mail (US-027, FR-052)

**Grundsatz: Der Prüfstatus ist vom Zustellerfolg unabhängig** (AC-027-3, FR-052). Ein
Mail-Server-Ausfall ändert **niemals** `APPROVED`/`FLAGGED`.

| Parameter | Wert |
|---|---|
| Maximale Versuche | **6** |
| Backoff | exponentiell mit Jitter, `base` = 1 min, `max` = 30 min |
| Zustände | `PENDING` → `SENDING` → `SENT` \| `RETRYING` → `DEAD_LETTER` |
| Sichtbarkeit | `GET /notifications?status=DEAD_LETTER` — in der Oberfläche sichtbar (AC-027-2) |
| Manuelle Wiederholung | `POST /notifications/{id}/retry` — setzt einen neuen Zustellauftrag an |
| Audit | `NOTIFICATION_RETRIED` (SEC-010) |

**Inhalt der E-Mail** (FR-051, K-4): Organisation, Prompt-ID/Titel, Version,
Fundkategorien, Schweregrade, Zeitpunkt, Deep-Link. **Kein** Prompt-Inhalt, **kein** Secret,
**keine** Fundstellen-Ausschnitte, **keine** Rohpayload.

**Empfängerwahl** (FR-053, AC-006-3): konfigurierte Security-Admins der Organisation; ist
keiner hinterlegt, die `ORG_ADMIN`s. Der tatsächliche Empfänger wird im Zustellversuch
protokolliert.

---

## 6. Wiederholung — Aufbewahrungsjob (US-028, FR-060)

| Parameter | Wert |
|---|---|
| Takt | täglich 03:00 UTC (+ stündlicher Job für KI-Rohpayloads) |
| Idempotenz | ein zweiter Lauf löscht `0` und meldet `0`, ohne Fehler (AC-028-2) |
| Fehler | endet mit `FAILED`, wird erneut versucht, löst einen Alarm aus; **kein** teilweiser Zustand (AC-028-3) |
| Nachweis | Löschmengen je Objektklasse in Logs und Audit (`RETENTION_RUN`), ohne Inhalt |

Die Löschungen laufen **je Objektgruppe in einer eigenen Transaktion** — ein Fehler in einer
Gruppe rollt nur diese zurück, nicht den ganzen Lauf.

---

## 7. API-Fehler (Synchroner Pfad)

| Code | Wann | Retry durch den Client? |
|---|---|---|
| `400` | syntaktisch fehlerhafte Anfrage (`size > 200`) | nein, korrigieren |
| `401` | nicht angemeldet / Token abgelaufen | ja, mit Refresh-Token |
| `403` | Rolle fehlt | nein |
| `404` | nicht existent oder fremde Organisation | nein |
| `409` | Zustandskonflikt | nein, Zustand ändern |
| `422` | semantisch ungültig | nein, Feld korrigieren |
| `429` | Rate-Limit | ja, nach `Retry-After` |
| `500` | unerwarteter Fehler | ja, mit Backoff; `correlationId` mitgeben |

**Rate-Limits** (SEC-008):

| Bereich | Limit | Reaktion |
|---|---|---|
| Login | 10 Versuche/Min/IP | `429` + temporäre Sperre des Kontos (AC-001-3) |
| API | 1 000 Anfragen/Min/Nutzer | `429` + `Retry-After` |

---

## 8. Was **nicht** passieren darf

| Regel | Grund |
|---|---|
| Keine halbfertigen Ergebnisse speichern | AC-022-3, K-6 |
| Kein `FAILED` ohne sichtbaren Grund und ohne `retryable` | P2, US-033 AC-033-3 |
| Kein Zustandswechsel des Scans durch die Zustellung | AC-027-3 |
| Keine Wiederholung ohne Backoff | Provider-/SMTP-Häufung |
| Kein automatierter Wiederanlauf aus `DEAD_LETTER` | Fehlerquote verschleiern, Provider belasten |
| Keine Exception mit Inhalt in Logs oder Antworten | FR-062, SEC-009 |

---

## 9. Observierbarkeit der Fehler

| Signal | Quelle | Alarmgrenze |
|---|---|---|
| Fehlerquote der Prüfläufe | `MetricsSnapshot.errorRate` | > 5 % über 24 h |
| Warteschlangentiefe | `MetricsSnapshot.queueDepth` | > 1 000 Jobs |
| Alter des ältesten Jobs | `MetricsSnapshot.oldestQueuedJobAgeSeconds` | > 15 min |
| Zustellquote | `MetricsSnapshot.deliveryRate` | < 95 % über 24 h |
| `DEAD_LETTER`-Einträge | `GET /notifications?status=DEAD_LETTER` | jeder Eintrag wird gesichtet |
| Retention-Fehler | `RetentionRun.status = FAILED` | jeder Fehler |
| Provider-Verifikation | `ProviderVerification.verified = false` | sofort |

---

## 10. Verweise

* `docs/adr/ADR-005-async-scan-architecture.md` — Warteschlange, Backoff, Totmannschaltung
* `docs/adr/ADR-009-error-model.md` — Fehlerformat und Statuscode-Bedeutung
* `docs/architecture/degraded-mode.md` — Verhalten bei KI-Ausfall
* `docs/architecture/observability.md` — Signale und Alarme
* `docs/api/openapi.yaml` — `rescanPromptVersion`, `retryNotificationDelivery`, `Problem`
* `docs/requirements/PRD.md` — FR-042, FR-052, FR-070, FR-071, US-027, US-033
