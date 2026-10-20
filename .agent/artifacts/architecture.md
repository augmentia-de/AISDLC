# PromptGuard — Architektur (Schritt 2: Konzept & Architektur)

| Feld | Wert |
|---|---|
| **Produkt** | PromptGuard — Zentrale Prompt-Verwaltung & Compliance-Prüfung |
| **Artefakt** | `architecture.md` (Schritt-Artefakt des Architecture Orchestrators) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Gate** | **GATE 1** — vor jedem Anwendungscode |

## 1. Ergebnis in einem Satz

PromptGuard ist eine **mandantenfähige Prompt-Registry auf Spring Boot 3 / Java 21 mit
React 19 (Next.js 15) und PostgreSQL 16**, in der ein **asynchroner Prüflauf über eine
persistente Datenbank-Warteschlange** die Einhaltung von Compliance-Regeln prüft, während
der **Schreibpfad immer verfügbar bleibt** — abgesichert durch eine **fünfstufige
Mandantentrennung** (Token → Rolle → Query/RLS → Cache/Suche → Hintergrundjob).

---

## 2. Stack-Entscheidung

| Schicht | Entscheidung | Begründung | ADR |
|---|---|---|---|
| Backend | **Spring Boot 3 / Java 21**, Maven, Flyway, Spring Data JPA | **Langchain4j** ist eine JVM-Bibliothek (CON-001, F10) — die Stack-Vorgabe „FastAPI **oder** Spring Boot" löst sich damit faktisch auf (K-7) | ADR-001 |
| KI-Zugriff | **Langchain4j** (`ChatModel` + `EmbeddingModel`), Provider per ENV konfigurierbar | CON-004; im Test durch Stubs ersetzt (K-8) | ADR-001, ADR-006 |
| Frontend | **React 19 in Next.js 15 (App Router)**, TypeScript `strict` | CON-002 (React 19) + Stack-Default `AGENTS.md`; kein SSR dynamischer Fachdaten | ADR-001 |
| Datenbank | **PostgreSQL 16** mit Row-Level-Security | CON-003, SEC-002 | ADR-001, ADR-002 |
| Auth | **Custom JWT** (`sub`, `organisation_id`, `role`, `exp`), Refresh-Token als `HttpOnly`-Cookie | FR-001, SEC-001, F2 | ADR-002 |
| Betrieb | Docker (non-root, gepinnte Tags) → **Cloud Run europe-west3**; lokal docker-compose | CON-007, FR-065, NFR-017 | ADR-001 |
| CI/CD | GitHub Actions: Build → Test → **Coverage-Gate 80 %** → Security-Scan → Image-Scan → Deploy | FR-073, F9, SEC-011 | ADR-001 |

**Bewertete Alternativen** (Details in ADR-001): FastAPI (JVM-Zwang), Node/NestJS (JVM-Zwang),
Spring Boot 2 / Java 17 (kein LTS), PostgreSQL-Volltext gegen Elasticsearch (zweiter Speicher =
zweite Isolationsfläche), React-SPA ohne Framework-Routing (Routing/Fehlergrenzen/Deployment
selbst zu bauen).

---

## 3. Die drei wichtigsten Architekturentscheidungen

### 3.1 Mandantentrennung als Invariante, nicht als Feature — ADR-002

`organisation_id` ist **Token-Claim**; es gibt keinen Client-Parameter, der den Mandanten
setzen könnte. Fünf Ebenen müssen **jeweils** bestehen: Identität (JWT) → Rolle
(serverseitig je Operation) → Query (`organisation_id`-Filter **plus** RLS als zweite
Verteidigungslinie) → Cache/Suche/Stream → Hintergrundjob (`organisation_id` im
Job-Datensatz; ohne auflösbaren Kontext wird der Job **verworfen und protokolliert**).
Antwortregel: Ressourcen fremder Organisationen sind **nicht existent** → `404` ohne
Existenzinformation; `403` bedeutet **nur** fehlende Rolle.
*Ein vergessener Filter führt zu leeren Ergebnissen, nicht zu einem Leck.*

### 3.2 Asynchroner Prüflauf mit persistenter Warteschlange — ADR-005 (+ ADR-008)

Version und Job entstehen **in derselben Transaktion** — kein Prompt ohne Job, kein Job
ohne Version. Das ist die strukturelle Garantie für „kein Datenverlust" (FR-070, CON-009):
keine In-Memory-Queue, kein Broker. Der Worker arbeitet die Jobs in Erstellungsreihenfolge
ab (`ORDER BY created_at, id`, `FOR UPDATE SKIP LOCKED`), setzt `PENDING → SCANNING` im
selben Commit und liefert über die **Outbox mit monotoner Sequenz** den Live-Status an die
Oberfläche (SSE, Polling als Fallback).

### 3.3 Mandantenbewusste Provider-Übergabe mit Verifikations-Gate — ADR-006

Der Originalinhalt wird **unredigiert** übermittelt (Entscheidung **F1**, vom Product Owner
bewusst gegen die Empfehlung entschieden). Weil das akzeptierte Restrisiko **R-06** ist, sind
Pflichtmaßnahmen festgeschrieben: AVV mit dem KI-Anbieter, EU-Region, Trainingsverbot,
Löschgarantie, **Provider-Verifikation als Gate vor jedem Aufruf** (bei Verstoß: *kein*
Aufruf, `FAILED` mit `PROVIDER_POLICY_VIOLATION`, kein Inhalt verlässt das System),
**Metadaten-Audit** jeder Übermittlung (ohne Inhalt, ohne ableitbaren Hash) und die
**lokale deterministische Vorprüfung** als Pflichtschritt. Die Maskierung betrifft nur die
**Anzeige**.

---

## 4. Modulaufteilung

### 4.1 API-Service (Spring Boot)

| Modul | Verantwortung | Vertrag |
|---|---|---|
| `security` | JWT-Verifikation, Claims, Security-Header, CORS, Rate-Limiting | — |
| `tenant` | Mandantenkontext je Request **und** je Job; verwirft Jobs ohne Kontext | `x-tenant-scope` |
| `auth` | Registrierung, Login, Refresh-Rotation, Logout | `registerUser` … `logout` |
| `account` | Profil, Passwort, Export, eigene Löschung | `/me*` |
| `organisation` | Einstellungen, Mitglieder, Rollen, Einladungen, Alarmempfänger | `/organisations/**` |
| `prompt` | CRUD, Versionen, Diff, Suche/Filter/Sortierung, Historie, Status | `/prompts/**` |
| `taxonomy` | Kategorien und Tags | `/categories`, `/tags` |
| `scan` | Scan-Ergebnis, Fundstellen, Duplikate, Waiver, Retry, Provider-Verifikation | `/prompt-versions/**`, `/provider/**` |
| `audit` | Audit-Ereignisse, Zustellversuche, Datenschutzhinweis | `/audit-events`, `/notifications/**`, `/privacy-notice` |
| `operations` | Health, Metriken, Retention, Qualitätsnachweis | `/health`, `/ops/**` |
| `service` | Fachregeln: Versionierung (I-1), Freigabe-Gate (I-3), Schwellen, Tag-Normalisierung | — |
| `queue` | Job-Erzeugung (persistent, kein In-Memory) | — |
| `sse` | Snapshot + Ereignisse aus der Outbox | `streamPromptStatus` |
| `error` | **eine** Fehlerabbildung für alle Operationen (Problem Details) | `Problem` |

### 4.2 Scan-Worker

`JobPoller` → `ScanOrchestrator` → (`Precheck` → `Duplicate` → `ProviderVerification` →
`Classification` → `Aggregator`) → `StatusManager` → `TransferAudit` → `AlertTrigger`,
daneben `Watchdog`.

### 4.3 Notification-Worker, Retention-Job

Zustellung mit Backoff/Totmannschaltung (eigene Warteschlange, organisatorisch getrennt vom
Scan) · tägliche, idempotente Löschung von Telemetrie und Rohpayloads.

---

## 5. Datenfluss

```
Entwickler:in → POST /prompts
   → [Transaktion] prompt + prompt_version(PENDING, content_hash) + scan_job(QUEUED) + audit
   → 201 (p95 < 500 ms, ohne Scan zu blockieren)
Scan-Worker: übernehmen → SCANNING
   → (a) lokale Vorprüfung (SECRET/PII)   [immer, Pflicht]
   → (b) Duplikatvergleich (nur eigene Org, nie blockierend)
   → Provider-Verifikation ── Verstoß ⇒ FAILED, kein Aufruf
   → (c) LLM-Klassifikation (INJECTION/JAILBREAK) + transfer_audit (Metadaten)
   → (d) Aggregation ⇒ APPROVED | FLAGGED
   → Status + status_transition + status_event (1 Transaktion) → SSE an die Oberfläche
   → bei FLAGGED: Zustellauftrag → Notification-Worker → Warn-E-Mail (nur Metadaten)
```

---

## 6. Traceability Story → Operation (Vollständigkeit)

**35/35 Stories** sind durch mindestens eine Operation abgedeckt, **60/60 Operationen** haben
mindestens eine Story. Maschinell geprüft über `x-stories` je Operation
(`docs/api/check_contract.py`). Die vollständige Zuordnung steht in
`.agent/artifacts/api-review.md`, Abschnitt 4.

| Epic | Stories | Kern-Operationen |
|---|---|---|
| E1 Identität & Zugriff | US-001…US-006 | `loginUser`, `registerUser`, `refreshTokens`, `createInvitation`, `replaceAlertRecipients` |
| E2 Prompt-Verwaltung | US-007…US-012 | `createPrompt`, `updatePrompt`, `comparePromptVersions`, `assignPromptTags`, `deletePrompt` |
| E3 Suche & Filterung | US-013…US-015 | `listPrompts` (`q`, Filter, Sortierung, Pagination) |
| E4 Asynchrone KI-Prüfung | US-016…US-023 | `getScanResult`, `listFindings`, `rescanPromptVersion`, `createWaiver`, `getProviderVerification` |
| E5 Status & Live-Aktualisierung | US-024, US-025 | `streamPromptStatus`, `getPromptStatus`, `listPromptStatusHistory` |
| E6 Alarmierung | US-026, US-027 | `listNotificationDeliveries`, `retryNotificationDelivery` |
| E7 Datenschutz & Aufbewahrung | US-028…US-030 | `listRetentionRuns`, `triggerRetentionRun`, `eraseOwnAccount`, `exportOwnData`, `getPrivacyNotice` |
| E8 Qualität & Betrieb | US-031…US-035 | `getQualityGateAttestation`, `reportQualityGateAttestation`, `getHealth`, `getMetrics`, `updateOwnProfile` |

---

## 7. Erzeugte Artefakte dieses Schritts

| Artefakt | Pfad | Zweck |
|---|---|---|
| API-Vertrag | `docs/api/openapi.yaml` | eingefrorene Single Source of Truth, 60 Operationen, 77 Schemata |
| Vertragsprüfung | `docs/api/check_contract.py` | maschinelle Prüfung (Pflichtangaben, Fehlerkatalog, Story-Zuordnung) |
| Datenmodell | `docs/architecture/schema.sql` | PostgreSQL 16, RLS, Indizes — Quelle der Feldtypen |
| Architektur-Überblick | `docs/architecture/overview.md` | Schichten, Module, Datenfluss, Traceability |
| Domain-Modell | `docs/architecture/domain-model.md` | Entitäten, Beziehungen, Invarianten |
| Mandantentrennung | `docs/architecture/tenant-model.md` | fünf Ebenen, Antwortregel, Nachweis |
| Zustandsautomat | `docs/architecture/scan-state-machine.md` | Zustände, Übergänge, Randfälle |
| Fehler/Retry | `docs/architecture/error-retry-strategy.md` | Fehlerklassen, Backoff, Totmannschaltung |
| Bedrohungsmodell | `docs/architecture/security-threat-model.md` | Angreifer, vier Scan-Arten, Kontrollen |
| Observability | `docs/architecture/observability.md` | Health, Logs, Metriken, Alarme |
| Degraded Mode | `docs/architecture/degraded-mode.md` | Betrieb ohne KI-Dienst |
| Datenschutz | `docs/architecture/privacy-retention.md` | Aufbewahrung, Löschung, F1-Maßnahmen |
| Barrierefreiheit | `docs/architecture/accessibility.md` | WCAG 2.1 AA, Typsicherheit, i18n |
| Notfall/Recovery | `docs/architecture/resilience.md` | Failover, Backup, Runbook, Übungen |
| C4-Diagramme | `docs/architecture/diagrams/01-context.mmd` … `08-tenant-isolation.mmd` | Kontext, Container, Komponenten, Sequenz, Zustand, Domain, Trennung |
| ADRs | `docs/adr/ADR-001…ADR-009` | Stack, Mandant, Statusmodell, Retention, Async-Scan, Provider, Duplikat, Live-Transport, Fehlermodell |
| Vertragsprüfung | `.agent/artifacts/api-review.md` | Befunde, Lücken, offene Fragen |
| Ordnerstruktur | `.agent/artifacts/folder-structure.md` | geplante `src/`-Struktur |

---

## 8. Offene Punkte (Inhalt von GATE 1)

| # | Punkt | Auswirkung | Empfehlung |
|---|---|---|---|
| O-1 | **Schema-Pfadabweichung:** das projektspezifische Schema liegt unter `docs/architecture/schema.sql`, weil `.agent/knowledge/` in diesem Lauf schreibgeschützt war (Workflow nennt `knowledge/schema.sql`) | Folge-Läufe könnten an der falschen Stelle suchen | Spiegelung nach `.agent/knowledge/schema.sql` im nächsten Schritt |
| O-2 | **B-02:** `403` statt `404` bei Löschung/Export eines Nutzers einer fremden Organisation (AC-029-2 fordert `403`) | schwache Existenzinformation über User-UUIDs | in Phase 8 prüfen; UUIDv4 entschärft |
| O-3 | **B-07:** kein Re-Scan freigegebener Versionen | ältere Freigaben sind nur über eine Inhaltsänderung neu bewertbar | bewusst; Product Owner bestätigen |
| O-4 | **B-08 / B-09:** Auslegung „Audit-Einträge mit Inhaltsbezug" und Anonymisierung statt Löschung der Autorschaft | rechtliche Auslegung, nicht technisch | Bestätigung durch DPO |
| O-5 | **B-10:** Zugriff auf KI-Rohpayloads ist **nicht** Teil des fachlichen Vertrags | Debugging eines Providerfehlers > 24 h nicht möglich | bewusste Betriebsgrenze; Alternative in Phase 8 |
| O-6 | **SSE im Vertrag:** `GET /prompts/{promptId}/status-stream` nutzt `text/event-stream`; in OAS 3.0.3 gibt es dafür keinen nativen Typ | Generierung des Clients muss `text/event-stream` gesondert behandeln | in Phase 6 im Client-Generator berücksichtigen |
| O-11 | **B-15:** `Prompt.currentVersionId` ist im Vertrag `required`, in der Datenbank nullable (zirkulärer FK zwischen `prompt` und `prompt_version`) | Kein beobachtbarer Zustand — Version und Job entstehen in derselben Transaktion (CON-009) | dokumentierte Ausnahme; im Schreibpfad als Invariante absichern |
| O-12 | **B-16 / Nullabilität:** `ScanRun.threshold`, `ScanRun.duplicateThreshold` und `Waiver.waivedBy` waren im Vertrag `required`, in der Datenbank nullable | **bereits angeglichen** (im Vertrag nullable, mit Begründung im Schema) | erledigt |
| O-7 | **`GET /prompts/{promptId}/status-stream`** ist ein **SSE**-Endpunkt; in OAS 3.0.3 gibt es dafür keinen nativen Typ | Generierung des Clients muss `text/event-stream` gesondert behandeln | in Phase 6 im Client-Generator berücksichtigen |
| O-8 | **Ein Provider, eine Konfiguration** — kein Failover auf einen zweiten Anbieter | Bei Provider-Ausfall bleibt der Prüfpfad still | bewusst (AVV-/EU-Auflagen); Notfallschalter = Verifikation absichtlich fehlschlagen lassen |
| O-9 | **JWT-Schlüssel-Symmetrie** (HMAC vs. asymmetrisch) ist nicht festgelegt | unkritisch, aber vertragsrelevant (Signaturprüfung) | Festlegung in Phase 6 im `AuthenticationPort` |
| O-10 | **Mandantenbezogene Metriken** fehlen bewusst | keine Kennzahlen je Organisation | bewusst (R-01); Bedarf nach K7-Bewertung neu prüfen |

---

## 9. Validierung (in Schritt 2 ausgeführt)

| Prüfung | Werkzeug | Ergebnis |
|---|---|---|
| YAML-Parse | `python3 -c "import yaml; yaml.safe_load(...)"` | **OK** |
| OpenAPI-Spezifikation | `openapi-spec-validator` | **`docs/api/openapi.yaml: OK`** |
| Vertragsregeln | `python3 docs/api/check_contract.py` | **PASS** — 60 Operationen, 60 eindeutige `operationId`s, 35/35 Stories abgedeckt, 60/60 Operationen mit Story, 11 `Paged*`-Schemas; Exitcode 0 |
| SQL-Syntax | `pglast.parse_sql` | **OK** — 56 Aussagen |
| SQL-Anwendbarkeit | `psql -v ON_ERROR_STOP=1 -f docs/architecture/schema.sql` (PostgreSQL 16) | **OK** — 0 Fehler |
| Schema ↔ Vertrag | `schema_check.py` (140 Feld-Mappings gegen `information_schema`) | **1 dokumentierte Abweichung** (B-15), 0 ungeklärte |
| Diagramme | `npx @mermaid-js/mermaid-cli` (12.0.0) | **8/8 PASS** — drei Syntaxfehler gefunden und behoben |

---

## 10. Empfehlung an das Gate

Der Vertrag ist **strukturell vollständig** (60 Operationen, alle Pflichtangaben, ein
Fehlerformat, `organisation_id`-Scope auf jedem Pfad, Pagination/Filter/Sortierung im
Vertrag, Story-Zuordnung lückenlos) und die Entscheidungen sind dokumentiert. Die offenen
Punkte O-2 bis O-12 sind **bewusste** Festlegungen oder Auslegungen, keine offenen
Implementierungslücken.

**Empfehlung:** Freigabe des Vertrags, damit Schritt 3 (Simplify) und Schritt 4 (Grill-Me)
darauf aufsetzen können. O-1 wird vor Schritt 5 (TDD) erledigt, damit die Teststrategie
gegen das finale Schema läuft.
