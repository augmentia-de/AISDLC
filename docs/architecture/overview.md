# PromptGuard — Architektur-Überblick

| Feld | Wert |
|---|---|
| **Dokument** | Architektur-Überblick (Schichten, Module, Datenfluss) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Grundlage** | `docs/requirements/PRD.md`, `docs/adr/ADR-001…ADR-009`, `docs/api/openapi.yaml` |

> **Zugehörige Artefakte**
> * Vertrag: `docs/api/openapi.yaml` (60 Operationen, eingefroren nach Gate 1)
> * Schema: `docs/architecture/schema.sql` (PostgreSQL 16, Quelle der Feldtypen)
> * Diagramme: `docs/architecture/diagrams/01-context.mmd` … `08-tenant-isolation.mmd`
> * Entscheidungen: `docs/adr/ADR-001…ADR-009`

---

## 1. Kurzfassung

PromptGuard ist eine **mandantenfähige Registry für LLM-Prompts** mit **asynchronem
Compliance-Scan**. Die zentrale Trennlinie verläuft zwischen

* dem **synchronen Schreibpfad** (Speichern, Versionieren, Suchen) — der immer verfügbar
  sein muss (NFR-006: 99,9 %, unabhängig vom KI-Dienst), und
* dem **asynchronen Prüfpfad** (Prüflauf, Alarmierung) — der nie den Schreibpfad blockiert,
  sondern in einer **persistenten Warteschlange** liegt (CON-009).

Diese Trennung ist der Grund, warum das System ohne Broker, ohne In-Memory-Queue und
ohne verteilte Transaktionen auskommt — und gleichzeitig fail-safe bleibt.

| Baustein | Verantwortung | ADR |
|---|---|---|
| **API-Service** | REST-Vertrag, Auth, Mandantenfilter, Validierung, Fehlerabbildung | ADR-002, ADR-009 |
| **Scan-Worker** | Prüflauf (a) Vorprüfung → (b) Duplikat → (c) LLM → (d) Aggregation | ADR-005, ADR-006, ADR-007 |
| **Notification-Worker** | Zustellung der Warn-E-Mail, Backoff, Totmannschaltung | ADR-005, ADR-004 |
| **Retention-Job** | Löschung von Telemetrie (> 90 d) und Rohpayloads (> 24 h) | ADR-004 |
| **PostgreSQL 16** | Fachdaten, Statusmodell, Audit, **persistente Warteschlangen**, Outbox, RLS | ADR-002, ADR-005 |
| **Web-Frontend** | React 19 in Next.js 15 (App Router), TypeScript strict, SSE-Client | ADR-001, ADR-008 |

---

## 2. Stack (Kurzfassung — Details in ADR-001)

| Schicht | Wahl | Begründung |
|---|---|---|
| Backend | **Spring Boot 3 / Java 21**, Maven, Flyway, Spring Data JPA | Langchain4j ist eine JVM-Bibliothek (CON-001, F10) |
| KI-Zugriff | **Langchain4j** (`ChatModel` + `EmbeddingModel`), Provider per ENV konfigurierbar | CON-004 |
| Frontend | **React 19 / Next.js 15 (App Router) / TypeScript strict** | CON-002 (React 19), Stack-Default `AGENTS.md`; kein SSR dynamischer Fachdaten |
| Datenbank | **PostgreSQL 16** mit RLS | CON-003, SEC-002 |
| Auth | **Custom JWT** (HMAC/RS, Schlüssel aus ENV), `organisation_id` im Claim | FR-001, SEC-001, ADR-002 |
| Betrieb | Docker (non-root, gepinnte Tags) → **Cloud Run europe-west3**; lokal docker-compose | CON-007, FR-065, NFR-017 |
| CI/CD | GitHub Actions: Build → Test → **Coverage-Gate 80 %** → Security-Scan → Deploy | FR-073, F9, SEC-011 |

---

## 3. Schichtenmodell

```
┌──────────────────────────────────────────────────────────────────────┐
│ Präsentationsschicht (React 19 / TypeScript strict)                  │
│  Listen · Detail · Editor · Versionshistorie · Fundstellen · Admin  │
│  Statusanzeige: SSE-Client mit Polling-Fallback (ADR-008)           │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS/JSON + Bearer-JWT + SSE
┌───────────────────────────────▼──────────────────────────────────────┐
│ API-Schicht (Spring MVC) — 60 Operationen aus docs/api/openapi.yaml │
│  Security-Filter → Rollenprüfung → Validierung → Controller         │
│  Mandantenkontext (organisation_id aus dem Token) → Service → Repo   │
│  Fehlerabbildung: ein Problem-Details-Format für alle Operationen   │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ Transaktionen mit SET LOCAL app.organisation_id
┌───────────────────────────────▼──────────────────────────────────────┐
│ Anwendungsschicht (Spring Services)                                   │
│  PromptService · VersionService · ScanService · WaiverService        │
│  DuplikatService · NotificationService · AuditService · RetentionService│
│  ClassificationPort / EmbeddingPort (Langchain4j) — im Test per Stub  │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────────────┐
│ Persistenzschicht (Spring Data JPA + Flyway)                          │
│  Repository je Entität — organisation_id ist Pflichtparameter         │
│  RLS als zweite Verteidigungslinie (ADR-002)                           │
│  scan_job (Warteschlange) · status_event (Outbox)                     │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────────────┐
│ PostgreSQL 16 (europe-west3) — Mandant, Versionen, Scans, Findings,  │
│ Audit, Warteschlangen, Outbox, Telemetrie                             │
└───────────────────────────────────────────────────────────────────────┘
```

**Regeln der Schichten**

1. **Controller enthalten keine Fachlogik.** Validierung am Rand (Request), Regeln im
   Service, Invarianten im Modell (openapi-first, Schritt 5 des Skills).
2. **Kein Repository ohne `organisationId` als Pflichtparameter.** Der Fehler wird beim
   Kompilieren sichtbar (ADR-002, Ebene 1).
3. **Kein SQL-String-Konkatenation.** Alle Queries über Repository/Specification mit
   parametrisierten Werten (SEC-005).
4. **Die Fehlerabbildung liegt an genau einer Stelle** (`@RestControllerAdvice`), damit das
   Format nicht driftet (ADR-009).

---

## 4. Module des Scan-Workers

| Modul | Aufgabe | Grenze |
|---|---|---|
| `JobPoller` | Holt Jobs in Erstellungsreihenfolge, sperrt sie (`FOR UPDATE SKIP LOCKED`) | Kennt keine Fachregeln |
| `ScanOrchestrator` | Führt die vier Stufen in **fixer Reihenfolge** aus | Entscheidet nichts selbst |
| `PrecheckModule` | Deterministische Secret-/PII-Erkennung (Stufe a) | Läuft **immer**, auch wenn der Provider ausfällt |
| `DuplicateModule` | Vektorähnlichkeit + normalisierter Textvergleich (Stufe b) | Nur eigene Organisation, nie blockierend |
| `ProviderVerification` | Gate vor jedem externen Aufruf (FR-038, DP-005) | Bei Verstoß: **kein** Aufruf |
| `ClassificationModule` | Prompt-Injection/Jailbreak via Langchain4j (Stufe c) | Prüfling nur als Datenblock (SEC-007) |
| `Aggregator` | Urteil aus Fundstellen + Schwelle (Stufe d) | `≥ Schwelle ⇒ FLAGGED`, sonst `APPROVED` |
| `StatusManager` | Status, Historie, Outbox — in **einer** Transaktion | I-1 strukturell garantiert |
| `TransferAudit` | Metadaten-Ereignis je Übermittlung (FR-074) | **Kein** Inhalt, **kein** Hash |
| `AlertTrigger` | Bei `FLAGGED`: Zustellaufträge an Security-Admins bzw. ORG_ADMINs | Inhaltfreie E-Mail (FR-051) |
| `Watchdog` | Hängende Läufe nach > 5 min auf `FAILED` (FR-042) | Setzt den Status **und** die Retry-Fähigkeit |

---

## 5. Datenfluss eines Prüflaufs

```
Entwickler:in
   │ POST /api/v1/prompts
   ▼
API-Service ──Transaktion──▶ prompt (PENDING) + prompt_version v1 (PENDING, content_hash)
                            + scan_job (QUEUED) + audit_event
   │ 201 Created (p95 < 500 ms — der Client wartet nicht auf den Scan)
   ▼
Scan-Worker
   ├─ Job übernehmen (locked_by, attempt_count+1)   ── Status: SCANNING
   ├─ (a) Precheck      → Fundstellen SECRET / PII (maskiert)
   ├─ (b) Duplikat      → bis zu 5 ähnliche Prompts der eigenen Org (Score ≥ 0,90)
   ├─ Provider-Verifikation  ── Verstoß? ⇒ FAILED, kein Aufruf, kein Inhalt verlässt
   ├─ (c) LLM          → Fundstellen PROMPT_INJECTION / JAILBREAK
   │                      + transfer_audit (Metadaten ohne Inhalt)
   ├─ (d) Aggregation  → APPROVED | FLAGGED
   └─ Status + Historie + Outbox  (1 Transaktion)  → SSE-Event
                                             └─► Notification-Worker → Warn-E-Mail
```

Details: `docs/architecture/diagrams/05-sequence-scan-process.mmd`,
`docs/architecture/scan-state-machine.md`.

---

## 6. Mandantentrennung — eine Invariante, fünf Ebenen

Die Trennung ist **keine Funktion**, sondern eine Invariante (P1, I-2). Sie wird auf fünf
Ebenen durchgesetzt; Details in `docs/architecture/tenant-model.md` und ADR-002:

| # | Ebene | Maßnahme |
|---|---|---|
| 1 | Identität | JWT mit `sub`, `organisation_id`, `role`, `exp` — Mandant **immer** aus dem Token |
| 2 | Rolle | Serverseitige Rollenprüfung je Operation (`DEVELOPER` / `SECURITY_ADMIN` / `ORG_ADMIN`) |
| 3 | Query | `organisation_id`-Filter auf **jeder** Query; RLS als zweite Verteidigungslinie |
| 4 | Cache/Suche | Cache-Keys enthalten den Mandanten; der Volltextindex wird über den Tenant-Filter abgefragt |
| 5 | Hintergrundjob | Jeder Job trägt `organisation_id` **im Datensatz**; ohne auflösbaren Kontext wird er verworfen |

**Antwortregel:** Ressourcen einer fremden Organisation sind **nicht existent** → `404`,
ohne Titel, Eigentümer, Status, Anzahl oder Vorschau. `403` bedeutet **nur** fehlende Rolle
(dokumentierte Ausnahme: `DELETE /users/{userId}`, `GET /users/{userId}/export` → `403`,
weil AC-029-2 das so fordert — ADR-002, Befund B-02).

---

## 7. Statusmodell und Freigabe-Gate

Der Prüfstatus hängt an der **Version**, nicht am Prompt (ADR-003).

```
PENDING ──▶ SCANNING ──▶ APPROVED | FLAGGED | FAILED
                              │
                              └──▶ WAIVED  (nur SECURITY_ADMIN, mit Begründung + Audit)

FAILED ──▶ PENDING   (manuelle oder automatische Wiederholung)
```

* **I-1** — die Freigabe ist an den `content_hash` der Version gebunden. Jede inhaltliche
  Änderung erzeugt eine **neue** Version mit `PENDING`. Es gibt **keinen** Pfad, auf dem ein
  Prompt mit geändertem Inhalt `APPROVED` bleibt.
* **I-3** — nutzbar sind ausschließlich `APPROVED` und `WAIVED`. Alles andere ist gespeichert,
  aber nicht nutzbar.

Details: `docs/architecture/scan-state-machine.md`, `docs/architecture/diagrams/06-state-machine-scan-status.mmd`.

---

## 8. Verfügbarkeits- und Fehlerverhalten

| Situation | Verhalten | Beleg |
|---|---|---|
| KI-Dienst nicht erreichbar | Anlegen funktioniert, Prüfung bleibt in der Warteschlange / wird `FAILED`, kein Datenverlust | NFR-007, US-033 |
| SMTP nicht erreichbar | Zustellung mit Backoff wiederholen → Totmannschaltung; **Prüfstatus bleibt unabhängig** | FR-052, US-027, AC-027-3 |
| Hängender Prüflauf | nach > 5 min `FAILED`, manuell erneut auslösbar | FR-042 |
| Provider-Konfiguration verletzt | **kein** externer Aufruf; `FAILED` mit `PROVIDER_POLICY_VIOLATION` | FR-038, AC-022-2 |
| Datenbank nicht erreichbar | Schreibpfad antwortet `500`; Queue-Zustand bleibt konsistent (Transaktion) | FR-070 |

Details: `docs/architecture/degraded-mode.md`, `docs/architecture/error-retry-strategy.md`.

---

## 9. Datenschutz und Aufbewahrung

| Klasse | Frist | Bezug |
|---|---|---|
| Prompt-Inhalte, Versionen, maskierte Fundstellen | **unbegrenzt** bis zur expliziten Löschung | A-03, F5 |
| KI-Rohpayloads | **24 h** | FR-061, DP-003 |
| Logs, Zustellprotokolle, Übermittlungsnachweise, Audit | **90 Tage** | FR-060, DP-003 |

> **[SECURITY] F1 — akzeptiertes Restrisiko (R-06):** Der Originalinhalt wird
> **unredigiert** an den externen KI-Dienst übermittelt. Das ist vom Product Owner bewusst
> entschieden. Die Pflichtmaßnahmen (AVV, EU-Region, Trainingsverbot, Löschgarantie,
> Provider-Verifikation mit Abbruch, Metadaten-Audit, lokale Vorprüfung) sind in ADR-006
> festgeschrieben.

Details: `docs/architecture/privacy-retention.md`, ADR-004, ADR-006.

---

## 10. Traceability: Story → Operation

Maschinell geprüft über `x-stories` je Operation; Prüfskript `docs/api/check_contract.py`.
Vollständige Zuordnung: `.agent/artifacts/api-review.md`.

| Epic | Stories | Operations (Auswahl) |
|---|---|---|
| E1 Identität | US-001…US-006 | `loginUser`, `registerUser`, `refreshTokens`, `createInvitation`, `replaceAlertRecipients` |
| E2 Prompt-Verwaltung | US-007…US-012 | `createPrompt`, `updatePrompt`, `comparePromptVersions`, `deleteCategory`, `assignPromptTags`, `deletePrompt` |
| E3 Suche | US-013…US-015 | `listPrompts` (`q`, Filter, Sortierung, Pagination) |
| E4 Prüfung | US-016…US-023 | `getScanResult`, `listFindings`, `rescanPromptVersion`, `createWaiver`, `getProviderVerification` |
| E5 Status | US-024, US-025 | `streamPromptStatus`, `getPromptStatus`, `listPromptStatusHistory` |
| E6 Alarmierung | US-026, US-027 | `listNotificationDeliveries`, `retryNotificationDelivery` |
| E7 Datenschutz | US-028…US-030 | `listRetentionRuns`, `triggerRetentionRun`, `eraseOwnAccount`, `exportOwnData`, `getPrivacyNotice` |
| E8 Betrieb | US-031…US-035 | `getQualityGateAttestation`, `reportQualityGateAttestation`, `getHealth`, `getMetrics`, `updateOwnProfile` |

**Ergebnis:** 35/35 Stories sind durch mindestens eine Operation abgedeckt; 60/60
Operationen haben mindestens eine Story. Keine Lücke.

---

## 11. Bewusste Grenzen des MVP

| Grenze | Folge | Quelle |
|---|---|---|
| Kein Kopieren von Prompts | `FR-017` bleibt ohne Operation | PRD §6.3 (Post-MVP) |
| Kein SSO/MFA | Einbaupunkt ist in ADR-001 festgeschrieben | F7, O6 |
| Kein organisationsweiter Sammel-SSE-Kanal | je Prompt ein Kanal | ADR-008 |
| Kein Zugriff auf KI-Rohpayloads im fachlichen Vertrag | Betriebsfunktion, Befund B-10 | ADR-006 |
| Kein Re-Scan freigegebener Versionen | Inhalt ändern erzeugt neue Version | ADR-003, Befund B-07 |
| Keine Redaktion vor der Übermittlung | akzeptiertes Restrisiko R-06 | F1, ADR-006 |

---

## 12. Verweise

* `docs/requirements/PRD.md` — Anforderungen, Entscheidungen F1–F10, Invarianten I-1…I-3
* `docs/api/openapi.yaml` — eingefrorener Vertrag
* `docs/architecture/schema.sql` — Datenmodell
* `docs/adr/ADR-001…ADR-009` — Entscheidungen
* `.agent/artifacts/api-review.md` — Vertragsprüfung, Befunde, offene Fragen
* `.agent/artifacts/folder-structure.md` — geplante Ablage in `src/`
