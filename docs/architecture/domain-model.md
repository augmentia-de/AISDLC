# PromptGuard — Fachliches Domain-Modell

| Feld | Wert |
|---|---|
| **Dokument** | Fachliches Domain-Modell (Entitäten, Beziehungen, Invarianten) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Quelle** | `docs/requirements/PRD.md` §7.1/§7.2, `docs/architecture/schema.sql`, `docs/api/openapi.yaml` |

> Das klassenbasierte Modell ist als Mermaid in
> `docs/architecture/diagrams/07-domain-model.mmd` hinterlegt. Dieses Dokument beschreibt
> die **Bedeutung**, die Beziehungen und die Invarianten.

---

## 1. Entitäten

| Entität | Zweck | Schlüsselattribute | Tabelle |
|---|---|---|---|
| **Organisation** | Mandant (Abteilung) | `id`, `name`, `scanThreshold`, `duplicateThreshold`, `maxTagsPerPrompt`, `maxTagsPerOrganisation`, `providerVerified` | `organisation` |
| **User** | Konto | `id`, `email`, `passwordHash`, `displayName`, `locale` | `app_user` |
| **Membership** | Rolle eines Nutzers in einer Organisation | `userId`, `organisationId`, `role`, `status`, `lastLoginAt` | `membership` |
| **Invitation** | einmalige, zeitlich begrenzte Einladung | `id`, `organisationId`, `email`, `role`, `tokenHash`, `status`, `expiresAt` | `invitation` |
| **AlertRecipient** | Empfänger der Warn-E-Mail | `id`, `organisationId`, `email` | `alert_recipient` |
| **Category** | organisationsspezifische Klassifikation | `id`, `organisationId`, `name`, `isDefault`, `isActive` | `category` |
| **Tag** | Freitext-Etikett | `id`, `organisationId`, `name`, `normalisedName` | `tag` |
| **Prompt** | logischer Prompt | `id`, `organisationId`, `title`, `description`, `categoryId`, `status`, `currentVersionId`, `createdBy` | `prompt` |
| **PromptVersion** | **unveränderlicher** Inhalts-Snapshot | `id`, `promptId`, `organisationId`, `versionNo`, `content`, `contentHash`, `status`, `scannedAt` | `prompt_version` |
| **PromptTag** | n:m zwischen Prompt und Tag | `promptId`, `tagId` | `prompt_tag` |
| **ScanRun** | Prüflauf über **genau eine** Version | `id`, `organisationId`, `promptVersionId`, `status`, `attemptCount`, `threshold`, `duplicateThreshold`, `lastError` | `scan_run` |
| **ScanFinding** | konkreter Befund | `id`, `scanId`, `category`, `severity`, `redactedExcerpt`, `startOffset`, `endOffset`, `line`, `column`, `explanation`, `detector`, `stage` | `scan_finding` |
| **DuplicateMatch** | beratender Duplikathinweis | `id`, `scanId`, `matchedPromptId`, `matchedVersionNo`, `title`, `score`, `method` | `duplicate_match` |
| **Waiver** | dokumentierte Ausnahme-Freigabe | `id`, `organisationId`, `promptVersionId`, `reason`, `waivedBy`, `waivedAt` | `waiver` |
| **StatusTransition** | ein Statuswechsel | `id`, `organisationId`, `promptId`, `promptVersionId`, `fromStatus`, `toStatus`, `cause`, `actorId`, `at` | `status_transition` |
| **AuditEvent** | sicherheitsrelevante Aktion, **ohne Inhalt** | `id`, `organisationId`, `actorId`, `actorEmail`, `action`, `entityType`, `entityId`, `metadata`, `at` | `audit_event` |
| **StatusEvent** | Outbox für den Live-Kanal | `id`, `organisationId`, `promptId`, `promptVersionId`, `status`, `sequence`, `occurredAt` | `status_event` |
| **ScanJob** | **persistente** Warteschlange | `id`, `organisationId`, `jobType`, `promptVersionId`, `notificationId`, `status`, `attemptCount`, `maxAttempts`, `availableAt`, `lockedAt`, `lockedBy` | `scan_job` |
| **NotificationDelivery** | Zustellversuch der Warn-E-Mail | `id`, `organisationId`, `promptId`, `promptVersionId`, `recipient`, `status`, `attempts`, `nextRetryAt`, `sentAt` | `notification_delivery` |
| **TransferAudit** | Übermittlungsnachweis (Metadaten, **ohne Inhalt**) | `id`, `organisationId`, `scanId`, `provider`, `model`, `contentLength`, `transferredAt`, `resultId`, `correlationId` | `transfer_audit` |
| **ScanRawPayload** | Rohantwort des Providers, **≤ 24 h** | `id`, `organisationId`, `scanId`, `payload`, `expiresAt` | `scan_raw_payload` |
| **RetentionRun** | Lauf des Aufbewahrungsjobs | `id`, `status`, `startedAt`, `finishedAt`, `deletedCounts`, `error`, `triggeredBy` | `retention_run` |
| **QualityGateAttestation** | Qualitätsnachweis der CI | `id`, `commitSha`, `threshold`, `backendCoverage`, `frontendCoverage`, `status` | `quality_gate_attestation` |

---

## 2. Beziehungen

```
Organisation 1──n Membership      User 1──1 Membership   (F2: ein Nutzer : eine Organisation)
Organisation 1──n Invitation
Organisation 1──n AlertRecipient
Organisation 1──n Category        (Seed-Liste je Organisation, FR-014)
Organisation 1──n Tag
Organisation 1──n Prompt
Organisation 1──n AuditEvent
Organisation 1──n NotificationDelivery
Prompt       1──n PromptVersion    (unveränderlich, I-1)
Prompt       n──n Tag              (über PromptTag)
Prompt       1──1 Category
Prompt       1──1 PromptVersion    (currentVersionId)
PromptVersion 1──1 ScanRun
ScanRun      1──n ScanFinding
ScanRun      1──n DuplicateMatch
PromptVersion 1──0..1 Waiver
PromptVersion 1──n StatusTransition
PromptVersion 1──n ScanJob
PromptVersion 1──n NotificationDelivery
ScanJob      0..1──1 NotificationDelivery
```

**Warum `Membership` und nicht „Rolle direkt am Nutzer"?** F2 legt fest: ein Nutzer gehört
**genau einer** Organisation. Das Modell bildet das als eigene Entität ab, damit
Mitgliedschaft, Rolle und Status **unabhängig** vom Kontostatus geführt werden können
(z. B. Rechteentzug ohne Kontolöschung) und die Mandantengrenze über genau eine Join-Tabelle
läuft.

---

## 3. Statusmodell (Kern der Fachlogik)

Der Prüfstatus hängt **ausschließlich an einer Version**. Der Prompt zeigt den Status
seiner **aktuellen** Version (`prompt.status` ist ein denormalisiertes Abbild, kein
eigenständiger Zustand).

| Status | Bedeutung | Nutzbar (I-3) |
|---|---|---|
| `PENDING` | angelegt, Prüfung eingereiht | nein |
| `SCANNING` | Prüfung läuft | nein |
| `APPROVED` | Prüfung abgeschlossen, kein Fund ≥ Schwelle | **ja** |
| `FLAGGED` | mindestens ein Fund ≥ Schwelle | nein |
| `WAIVED` | von `SECURITY_ADMIN` bewusst freigegeben (mit Begründung + Audit) | **ja** |
| `FAILED` | technisch nicht durchführbar nach allen Versuchen | nein |

Zustandsübergänge und ihre Bedingungen: `docs/architecture/scan-state-machine.md`.

---

## 4. Invarianten

| ID | Invariante | Strukturell gesichert durch | Verifiziert durch |
|---|---|---|---|
| **I-1** | Eine Freigabe ist an den `content_hash` der Version gebunden; jede inhaltliche Änderung erzeugt eine neue Version mit `PENDING`. Es gibt **keinen** Pfad, auf dem ein Prompt mit geändertem Inhalt `APPROVED` bleibt. | Ein einziger Schreibpfad (`updatePrompt`) erzeugt Versionen; der Prüfstatus wird **nie** direkt am Prompt gepflegt. | US-008 (AC-008-1, AC-008-3), US-033 |
| **I-2** | Jede Lese- und Schreiboperation ist auf `organisation_id` beschränkt — ohne Ausnahme, auch für Jobs, Suche, Cache, Fehlermeldungen und Existenzprüfungen. | Token als Mandantengrenze + Pflichtparameter im Repository + RLS + Job-Kontext. | US-005 (AC-005-1…3), US-031 (AC-031-3) |
| **I-3** | Nur `APPROVED` und `WAIVED` gelten als „freigegeben". Alles andere ist gespeichert, aber nicht nutzbar. | Statussemantik an genau einem Enum + Freigabeprüfung in der Service-Schicht. | US-016, US-023, US-033 |

**Zusätzliche fachliche Invariante (aus F4 / I-1):** Es gibt **keinen** Re-Scan einer
freigegebenen Version. Wer eine ältere Freigabe neu bewerten lassen will, muss den Inhalt
ändern — das erzeugt eine neue Version. (ADR-003, Befund B-07.)

---

## 5. Lebenszyklus eines Prompts

```
Anlegen ──▶ Version 1 (PENDING) ──▶ Scan ──▶ APPROVED | FLAGGED | FAILED
                │                                     │
                │                                     ├─▶ WAIVED (SECURITY_ADMIN)
                │                                     └─▶ PENDING (Retry)
                │
                └──▶ Bearbeiten ──▶ Version 2 (PENDING) ──▶ …

Löschen (ORG_ADMIN oder Ersteller) ──▶ Prompt, Versionen, Scans, Findings entfernt
                                        Audit-Eintrag bleibt (ohne Inhalt)
```

* **Neue Version** bei jeder Änderung von Titel, Beschreibung, Inhalt, Kategorie **oder**
  Tags (FR-012).
* **Keine Änderung** an Titel, Inhalt, Kategorie oder Tags ⇒ dieselbe Version, kein Scan
  (AC-008-3).
* **Nach Löschung eines Kontos** wird die Autorschaft **anonymisiert** (`created_by = NULL`),
  der Prompt-Bestand bleibt bestehen und wird erneut zur Prüfung eingereiht (FR-063,
  ADR-004, Befund B-09).

---

## 6. Abgrenzung: Was **kein** Bestandteil des Modells ist

| Nicht im Modell | Begründung |
|---|---|
| Prompt-Runtime / Ausführung von Prompts | O1 — die Plattform bleibt Registry |
| Prompt-Marketplace / organisationsübergreifender Austausch | O2, P1 — es existiert kein globaler Prompt-Index |
| Platzhalter-/Template-Syntax | O3 — erhöht die Scan-Angriffsfläche, nicht gefordert |
| Freigabe-Workflow mit mehrstufiger Abnahme | O9 — MVP: eine Security-Admin-Freigabe genügt |
| Globaler Plattform-Admin mit Fachdatenzugriff | O7, F3 — Betrieb ohne Fachdaten |

---

## 7. Verweise

* `docs/architecture/schema.sql` — SQL-Umsetzung dieses Modells (RLS-Policies, Indizes)
* `docs/api/openapi.yaml` — `components.schemas` (DTO-Projektion der Entitäten)
* `docs/architecture/diagrams/07-domain-model.mmd` — Klassendiagramm
* `docs/adr/ADR-003-status-model-and-release-gate.md` — Statusmodell und I-1/I-3
* `docs/adr/ADR-004-storage-retention-and-privacy.md` — Löschung und Anonymisierung
