# ADR-003 — Statusmodell und Freigabe-Gate: Status an der Version, Freigabe an den Inhalts-Hash gebunden

| Feld | Wert |
|---|---|
| **ID** | ADR-003 |
| **Titel** | Statusmodell und Freigabe-Gate |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch F4 |
| **Betrifft** | FR-011, FR-012, FR-013, FR-036, FR-039, FR-041, FR-042, FR-070, FR-071, I-1, I-3, A-04, F4, K-1, K-6 |
| **Supersediert** | — |

## Kontext

Der Prüfstatus muss an einem Objekt hängen, das sich **verändert** — sonst behält ein
Prompt mit neuem Inhalt eine alte Freigabe. Gleichzeitig muss die Oberfläche schnell
arbeiten: Speichern ist sofort, Prüfung folgt (P3, FR-030).

Daraus ergibt sich die zentrale Modellierungsfrage: **Trägt der Prompt oder die Version den
Status?**

Zusätzlich sind drei Regeln zu verschränken:

* **I-1** — eine Freigabe ist an den `content_hash` der Version gebunden; jede Inhalts­
  änderung erzeugt eine neue Version mit `PENDING`.
* **I-3** — nur `APPROVED` und `WAIVED` gelten als freigegeben; alles andere ist gespeichert,
  aber nicht nutzbar.
* **K-6** — ein technischer Fehler führt zu `FAILED` (blockiert), ist aber reproduzierbar
  und sichtbar; kein Absturz, kein Datenverlust.

Die ACs legen zusätzlich zwei Randbedingungen fest, die nicht aus dem PRD-Text allein
ablesbar sind:

* **AC-008-3** — ein Save **ohne** Änderung erzeugt **keine** neue Version und **keinen**
  neuen Scan; die Versionsanzahl bleibt gleich.
* **AC-033-3 / FR-071** — ein `FAILED`er Lauf ist manuell erneut auslösbar; die Version
  wechselt dabei `FAILED → PENDING`.

## Entscheidung

### 1. Der Status hängt an der Version

```
Prompt (logisch)                PromptVersion (unveränderlicher Snapshot)
  title, description     1──n    content, content_hash, version_no
  category_id, tags             status: PENDING|SCANNING|APPROVED|FLAGGED|WAIVED|FAILED
  current_version_id ─────────► current_version_no
  status  = Status der aktuellen Version (Projektion, kein eigener Zustand)
```

* `Prompt.status` ist eine **Projektion** von `PromptVersion.status` der aktuellen Version —
  kein zweiter, unabhängiger Zustand. Damit gibt es konstruktiv keine Stelle, an der ein
  Prompt einen anderen Status haben kann als seine aktuelle Version.
* Der Prompt zeigt in der Liste `status` der aktuellen Version; Filter und Sortierung auf
  `status` beziehen sich darauf.

### 2. Der Prüfstatus folgt dem Zustandsautomaten

```
PENDING ──► SCANNING ──► APPROVED | FLAGGED | FAILED
   ▲            │
   │            └──► FAILED  (alle Wiederholversuche fehlgeschlagen, FR-070)
   │            └──► FAILED  (Watchdog: > 5 min ohne Abschluss, FR-042)
   │
   └── FAILED ──► PENDING   (manuelle Wiederholung FR-071 / automatischer Retry FR-070)

FLAGGED ──► WAIVED   (nur SECURITY_ADMIN, Pflicht-Begründung, Audit-Eintrag FR-039)
```

* **Kein Pfad `APPROVED → FLAGGED`, `APPROVED → PENDING` oder `WAIVED → …`** ohne
  Inhaltsänderung. Ein neuer Inhalt erzeugt eine **neue Version** (`PENDING`); die alte
  Version behält ihren Status (FR-012).
* **Neustart eines Laufs ist ausschließlich aus `FAILED` erlaubt.** `POST
  /api/v1/prompt-versions/{versionId}/rescan` antwortet für jeden anderen Status mit
  `409 Conflict` und nennt den aktuellen Status. Begründung: eine erneute Prüfung einer
  freigegebenen Version würde eine bestehende Freigabe auflösen, ohne dass sich der Inhalt
  geändert hat — das verletzt die Absicht von I-1/I-3.

### 3. Das Freigabe-Gate (I-3)

`usable = status ∈ {APPROVED, WAIVED}`.

* Der API-Vertrag stellt `status` und `findingCount` bereit; die Ableitung „nutzbar" ist
  eine reine Funktion des Status und wird **nicht** als eigenes boolean-Feld im Vertrag
  geführt (ein Feld ohne Zweck wird entfernt).
* `WAIVED` entsteht **ausschließlich** durch `POST /prompt-versions/{versionId}/waiver`
  mit `reason` (min. 10 Zeichen). Fehlt die Begründung: `422`, der Status bleibt `FLAGGED`
  und es wird **kein** Audit-Eintrag geschrieben (AC-023-3).
* `DEVELOPER` erhält beim Waiver `403`; der Status bleibt `FLAGGED` (AC-023-2). **Beide**
  Versuche werden im Audit protokolliert (AC-004-3).

### 4. Der `content_hash` ist die Bindung

* `PromptVersion.content_hash` = SHA-256 über den **exakten** Inhalt.
* Beim Anlegen/Bearbeiten wird der Hash der neuen Version berechnet. Ist er identisch mit
  dem Hash der aktuellen Version **und** Titel/Beschreibung/Kategorie/Tags sind unverändert,
  wird **keine** neue Version erzeugt (AC-008-3) und **kein** Scan eingereiht.
* Andernfalls: neue Version, Status `PENDING`, Scan-Job, Audit-Eintrag.

### 5. Historie statt Zustands

Jeder Übergang schreibt eine Zeile in `status_transition` (von, nach, Zeitpunkt, Ursache,
Actor) (FR-041). Die Historie ist **an den Audit** angebunden, aber getrennt: die
Historie ist fachlich (Status der Version), das Audit ist sicherheitsrelevant (Aktionen von
Akteuren). Beide enthalten **niemals** Inhalt.

### 6. `FAILED` ist ein erstes Ergebnis, kein Endzustand

* `FAILED` blockiert die Nutzung (I-3) und ist **sichtbar**.
* `FAILED` ist **reproduzierbar**: `rescanPromptVersion` erzeugt einen neuen Job, die
  Version wechselt `FAILED → PENDING`, der manuelle Versuch erscheint im Audit
  (`SCAN_RETRY_REQUESTED`) (FR-071, SEC-010).
* Ein `FAILED`er Lauf hinterlässt **kein** unvollständiges Ergebnis: es werden keine
  halben Fundstellen gespeichert (AC-022-3).

## Konsequenzen

### Positiv

* **I-1 ist strukturell garantiert**, nicht nur konventionell: Es gibt nur einen Pfad, auf
  dem ein Prompt mit geändertem Inhalt freigegeben bleibt — namely keinen.
* Der Prompt-Status kann nicht „veralteten“, weil er keine eigene Größe ist.
* Die Oberfläche kann den Zustand direkt aus der Liste ableiten, ohne die Version zu laden.
* `FAILED` ist vom Anwender reparierbar, ohne einen Ingenieur zu brauchen (K-6).

### Negativ / Kosten

* **Speicher:** Versionen sind unveränderlich und werden nicht überschrieben. Ein
  Prompt mit 20 Bearbeitungen hält 20 Versionen (inkl. Inhalt). Das ist die Preisgabe der
  Nachvollziehbarkeit (FR-013) und der Korrektheit von I-1.
* **Vergleich:** Der Inhaltsvergleich beim Speichern kostet einen zusätzlichen Lesevorgang
  der aktuellen Version — unvermeidbar, um AC-008-3 korrekt zu bedienen.
* **Kein Re-Scan freigegebener Versionen:** Wer eine ältere Freigabe bewusst neu bewerten
  lassen will, muss den Inhalt ändern (neue Version) — bewusste Einschränkung, in
  `.agent/artifacts/api-review.md` als Befund B-07 geführt.

### Neutral

* `WAIVED` ist ein **Freigabe-**, kein Prüfstatus im engeren Sinn; es steht in derselben
  Enumeration, weil es dieselbe Nutzbarkeitssemantik hat.

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| Status am **Prompt** | Ein geänderter Prompt behielte `APPROVED` — I-1 wäre nicht durchsetzbar. |
| Status am Prompt **und** an der Version | Zwei Zustände, die auseinanderlaufen können; klassische Fehlerquelle. |
| Freigabe am Inhalts-Hash, Status am Prompt | Freigabe-Logik und Status-Logik an verschiedenen Objekten — Umbrella-Problem. |
| Re-Scan aus jedem Status erlauben | Löst bestehende Freigaben ohne Inhaltsänderung auf; verletzt die Absicht von I-1/I-3. |
| `WAIVED` als eigene Freigabe-Tabelle mit eigenem Lebenszyklus | Doppelt modelliert: die Freigabe **ist** ein Status der Version, plus ein Audit-Eintrag. |

## Verweise

* `docs/requirements/PRD.md` §7.2 (Statusmodell, I-1…I-3), §14 (F4), §15 (K-1, K-6)
* `docs/architecture/diagrams/06-state-machine-scan-status.mmd`
* `docs/api/openapi.yaml` — `ScanStatus`, `rescanPromptVersion`, `createWaiver`, `updatePrompt`
