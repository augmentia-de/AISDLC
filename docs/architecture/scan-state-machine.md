# PromptGuard — Zustandsautomat des Scans

| Feld | Wert |
|---|---|
| **Dokument** | Zustandsautomat des Prüfstatus (PENDING → APPROVED / FLAGGED / FAILED) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Bezug** | ADR-003 (Statusmodell & Freigabe-Gate), ADR-005 (Warteschlange), ADR-006 (Provider-Gate) |

> Diagramm: `docs/architecture/diagrams/06-state-machine-scan-status.mmd`

---

## 1. Wo der Status liegt

Der Prüfstatus ist ein Zustand der **Version** (`prompt_version.status`), nicht des Prompts.
`prompt.status` ist ein **denormalisiertes Abbild** des Status der aktuellen Version — es
gibt keinen zweiten, unabhängigen Zustand, der auseinanderlaufen könnte.

* **Warum:** Ein Status am Prompt wäre nicht durchsetzbar — eine inhaltliche Änderung
  müsste den Prompt-Status zurücksetzen, und jede Nebenroute, die den Inhalt ändert, würde
  das vergessen (ADR-003, Alternative „Status am Prompt" verworfen).
* **Folge für die API:** `GET /prompts/{promptId}` liefert den Status der aktuellen
  Version; `GET /prompt-versions/{versionId}/scan` liefert den Lauf mit seinen Details.

---

## 2. Zustände

| Status | Bedeutung | Nutzbar (I-3) | Endzustand |
|---|---|---|---|
| `PENDING` | angelegt, Prüfung eingereiht, noch nicht gestartet | nein | nein |
| `SCANNING` | Prüfung läuft gerade | nein | nein |
| `APPROVED` | Prüfung abgeschlossen, kein Fund ≥ Schwelle | **ja** | ja |
| `FLAGGED` | mindestens ein Fund ≥ Schwelle | nein | nein (→ `WAIVED`) |
| `WAIVED` | von `SECURITY_ADMIN` bewusst freigegeben (Begründung + Audit) | **ja** | ja |
| `FAILED` | technisch nicht durchführbar nach allen Wiederholversuchen | nein | nein (→ `PENDING`) |

---

## 3. Übergänge

| Von | Nach | Bedingung | Auslöser im System | Beleg |
|---|---|---|---|---|
| — | `PENDING` | Prompt/Version angelegt | `POST /prompts`, `PATCH /prompts/{id}` | FR-011, FR-012 |
| `PENDING` | `SCANNING` | Worker übernimmt den Job | `scan_job` → `RUNNING`, Statuswechsel im selben Commit | FR-030 |
| `PENDING` | `FAILED` | Provider-Verifikation verletzt (kein Aufruf ausgeführt) | `PROVIDER_POLICY_VIOLATION` | FR-038, AC-022-2 |
| `SCANNING` | `APPROVED` | kein Fund ≥ Schwelle | Aggregation (Stufe d) | FR-036 |
| `SCANNING` | `FLAGGED` | mindestens ein Fund ≥ Schwelle | Aggregation (Stufe d) | FR-036 |
| `SCANNING` | `FAILED` | alle Wiederholversuche erschöpft | Job → `DEAD_LETTER` | FR-070 |
| `SCANNING` | `FAILED` | hängender Lauf > 5 min ohne Abschluss | Watchdog | FR-042 |
| `FAILED` | `PENDING` | manuelle Wiederholung | `POST /prompt-versions/{id}/rescan` | FR-071, US-033 |
| `FAILED` | `PENDING` | automatische Wiederholung nach Backoff | Worker, `available_at` erreicht | FR-070 |
| `FLAGGED` | `WAIVED` | nur `SECURITY_ADMIN`, Pflicht-Begründung, Audit-Eintrag | `POST /prompt-versions/{id}/waiver` | FR-039, SEC-010 |
| `APPROVED`/`FLAGGED`/`WAIVED` | `PENDING` | **jede inhaltliche Änderung** erzeugt eine **neue Version** mit `PENDING` | `PATCH /prompts/{id}` | FR-012, I-1 |

```
        ┌───────────── inhaltliche Änderung → NEUE Version ─────────────┐
        │                                                                │
        ▼                                                                │
   ┌─────────┐   Worker übernimmt    ┌──────────┐   Urteil    ┌───────────┐
   │ PENDING │──────────────────────▶│ SCANNING │────────────▶│ APPROVED  │
   └─────────┘                       └──────────┘             └───────────┘
        ▲                                   │                   (Endzustand)
        │                                   │ kein Fund ≥ Schwelle
        │ Provider-Verifikation             │ mind. 1 Fund ≥ Schwelle
        │ verletzt                          ▼
        │                            ┌──────────┐   Waiver (SECURITY_ADMIN)
        └────────────────────────────│ FLAGGED  │──────────────────────┐
                                     └──────────┘                      ▼
                                          │                       ┌──────────┐
                                          │ alle Versuche         │  WAIVED  │
                                          │ erschöpft /           │ (Endzust.)│
                                          │ Watchdog              └──────────┘
                                          ▼
                                     ┌──────────┐
                                     │  FAILED  │──▶ manuelle/automatische Wiederholung ──▶ PENDING
                                     └──────────┘
```

---

## 4. Regeln, die den Automaten tragen

### 4.1 I-1 — die Freigabe ist an den Inhalt gebunden

* Der `content_hash` (SHA-256 über den Inhalt) wird **bei der Erstellung der Version**
  berechnet und ist unveränderlich.
* Der Status wird **nie** direkt am Prompt gepflegt, sondern ausschließlich über den
  Worker am Versionsdatensatz.
* Es existiert **genau ein** Schreibpfad, der eine Version erzeugt (`updatePrompt` /
  `assignPromptTags`). Jeder andere Pfad, der den Inhalt ändern könnte, existiert nicht.
* **Konsequenz:** Ein Prompt mit geändertem Inhalt **kann nicht** `APPROVED` bleiben —
  nicht durch Konvention, sondern weil der Zustandsautomat keinen Übergang
  `APPROVED → APPROVED` kennt.

### 4.2 I-3 — nur `APPROVED` und `WAIVED` sind nutzbar

* Nutzbarkeit wird an **einer** Stelle geprüft (Service-Schicht), nicht in der UI.
* `PENDING`, `SCANNING`, `FLAGGED` und `FAILED` sind gespeichert, aber **nicht** nutzbar und
  **nicht** exportierbar (FR-030, F4).
* Die Oberfläche zeigt den Zustand **mit Textlabel**, nicht nur über Farbe (Barrierefreiheit,
  US-035 AC-035-2).

### 4.3 Kein Re-Scan freigegebener Versionen

* Aus `APPROVED` oder `WAIVED` gibt es **keinen** Übergang in einen Scan.
* Wer eine ältere Freigabe neu bewerten lassen will, ändert den Inhalt — das erzeugt eine
  neue Version und damit einen neuen Prüflauf (ADR-003, Befund B-07).
* **Ausnahme:** `FAILED` ist ausdrücklich wiederholbar (FR-071) — ein technischer Fehler
  darf nicht zu dauerhaftem „nie geprüft" führen (P2, K-6).

### 4.4 Der Schwellenwert entscheidet, nicht der Scanner allein

* `FLAGGED` entsteht, wenn **mindestens eine** Fundstelle die Schwellen-Schweregrad der
  Organisation erreicht (`organisation.scan_threshold`, Default `MEDIUM`, FR-036).
* Der **Duplikathinweis blockiert nie** — er ist beratend (FR-032).
* Der Schwellenwert ist **organisationsweit** konfigurierbar (R-02: Fehlalarme).

### 4.5 Jeder Übergang ist ein Audit-Eintrag

* Jeder Übergang schreibt eine Zeile in `status_transition` (alter Status, neuer Status,
  Zeitpunkt, auslösender Vorgang, Actor) — FR-041, SEC-010.
* Statuswechsel, Historie und Outbox-Ereignis entstehen **in derselben Transaktion**.
* Der Audit-Eintrag enthält **niemals** Inhalt (FR-062).

---

## 5. Randfälle und ihre Behandlung

| Randfall | Verhalten | Beleg |
|---|---|---|
| Worker stirbt mitten im Lauf | Der Job bleibt `RUNNING` mit altem `locked_at`; nach `scan_timeout` (> 5 min) setzt der Watchdog die Version auf `FAILED` und der Job wird wieder auf `QUEUED` gestellt. **Kein** Verlust, **kein** Doppel-Urteil (Statuswechsel ist idempotent über den Versionsstatus). | FR-042, AC-016-3 |
| Zwei Worker übernehmen denselben Job | `FOR UPDATE SKIP LOCKED` verhindert Doppelverarbeitung | ADR-005 |
| Stufe (c) schlägt fehl | Kein unvollständiges Ergebnis wird gespeichert: die Fundstellen der Zwischenstufen werden verworfen, die Version geht auf `FAILED`. **Die lokale Vorprüfung (a) läuft trotzdem** und fließt in das Urteil ein, wenn sie allein completes. | FR-031, AC-022-3 |
| Provider-Konfiguration verletzt | Kein externer Aufruf. `FAILED` mit `PROVIDER_POLICY_VIOLATION`. Kein Inhalt verlässt das System. | FR-038, AC-022-2 |
| Waiver ohne Begründung | `422`, Status bleibt `FLAGGED`, **kein** Audit-Eintrag | AC-023-3 |
| Waiver durch `DEVELOPER` | `403`, Status bleibt `FLAGGED`; der Versuch wird auditiert | AC-023-2, AC-004-3 |
| Versionsinhalt nachträglich geändert | Nicht möglich: `prompt_version.content` ist unveränderlich; der einzige Weg ist eine neue Version | FR-013, I-1 |

---

## 6. Status im Vertrag

| Element | Wert |
|---|---|
| Enum | `components.schemas.ScanStatus` = `PENDING`, `SCANNING`, `APPROVED`, `FLAGGED`, `WAIVED`, `FAILED` |
| Laufstatus | `components.schemas.ScanRunStatus` = `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `ABORTED` |
| Jobstatus | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `DEAD_LETTER` (Tabelle `scan_job`) |
| Live-Status | `PromptStatus` = `{ promptId, currentVersionId, status, sequence, findingCount, occurredAt, retryable }` |
| Historie | `GET /prompts/{promptId}/history` → `StatusTransition` |

> `retryable` ist Teil des Live-Status: Die Oberfläche zeigt bei `FAILED` **immer** eine
> Retry-Option — „reproduzierbar", nicht „verloren" (P2, K-6).

---

## 7. Verweise

* `docs/adr/ADR-003-status-model-and-release-gate.md` — I-1, I-3, keine Re-Scans
* `docs/adr/ADR-005-async-scan-architecture.md` — Jobzustände, Backoff, Watchdog
* `docs/adr/ADR-006-ai-provider-integration-and-verification.md` — Provider-Gate
* `docs/architecture/diagrams/06-state-machine-scan-status.mmd` — Zustandsdiagramm
* `docs/architecture/diagrams/05-sequence-scan-process.mmd` — Sequenz
* `docs/api/openapi.yaml` — `ScanStatus`, `PromptStatus`, `StatusTransition`, `Waiver`
