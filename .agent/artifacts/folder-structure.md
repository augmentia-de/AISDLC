# PromptGuard — Geplante Ordnerstruktur (`src/`)

| Feld | Wert |
|---|---|
| **Artefakt** | `folder-structure.md` (Schritt-Artefakt des Architecture Orchestrators) |
| **Version** | 1.0.0 |
| **Stand** | 2026-10-20 |
| **Autor** | Architecture Orchestrator (`aisdlc-architecture`) |
| **Hinweis** | **Planung, keine Implementierung.** In Schritt 5/6 entstehen die Dateien. |

> **Abgrenzung:** Der Architecture Orchestrator schreibt **nicht** in `src/` und **nicht** in
> `tests/` (Folder Ownership in `AGENTS.md`). Dieses Dokument beschreibt die **geplante**
> Struktur, damit der Execution Orchestrator (Schritt 5–7) und OpenCode danach arbeiten
> können.

---

## 1. Gesamtbild

```
/
├── docs/                       # dieses Repository
│   ├── requirements/           # Schritt 1 (Requirements) — read-only für spätere Schritte
│   ├── architecture/           # Schritt 2 (dieser Schritt)
│   │   ├── overview.md
│   │   ├── domain-model.md
│   │   ├── tenant-model.md
│   │   ├── scan-state-machine.md
│   │   ├── error-retry-strategy.md
│   │   ├── security-threat-model.md
│   │   ├── observability.md
│   │   ├── degraded-mode.md
│   │   ├── privacy-retention.md
│   │   ├── accessibility.md
│   │   ├── resilience.md
│   │   ├── schema.sql          # Quelle der Feldtypen für den Vertrag
│   │   └── diagrams/*.mmd
│   ├── api/
│   │   ├── openapi.yaml        # eingefrorener Vertrag
│   │   └── check_contract.py   # Vertragsprüfung (läuft in CI)
│   ├── adr/ADR-001…009*.md
│   └── execution/              # Schritt 5–7
├── src/                        # Anwendungscode — Owner: OpenCode (Schritt 6)
│   ├── backend/                # Spring Boot 3 / Java 21
│   └── frontend/               # React 19 / Next.js 15 / TypeScript strict
├── tests/                      # Owner: OpenCode (Schritt 5) + QA (Schritt 8)
│   ├── backend/
│   ├── frontend/
│   └── e2e/
├── coverage/                   # Owner: CI/Test-Runner
├── infra/                      # Docker, compose, deploy (Schritt 9)
├── .github/workflows/          # CI/CD (Schritt 9)
└── .agent/artifacts/           # Schritt-Artefakte (architecture.md, folder-structure.md, …)
```

**Warum `src/backend` + `src/frontend` statt zweier Top-Level-Projekte:** Backend und Frontend
werden **getrennt** gebaut und **getrennt** deployed (Cloud Run: zwei Services, plus ein
Worker-Service). Ein gemeinsames Monorepo mit zwei Maven-/npm-Wurzeln hält die
CI-Stufen unabhängig, ohne ein Build-Tool-Overhead.

---

## 2. Backend — `src/backend/`

```
src/backend/
├── pom.xml                       # Parent-POM; Module: api, worker, notification, retention
├── mvnw / mvnw.cmd / .mvn/       # Maven Wrapper (Version im Wrapper, nicht im CI)
├── Dockerfile                    # non-root, gepinnte Tags, HEALTHCHECK (NFR-017)
├── .dockerignore
├── src/main/resources/
│   ├── application.yml           # Profile: default, local, test, prod
│   ├── application-local.yml     # docker-compose-Ziele
│   ├── db/migration/
│   │   ├── V1__baseline.sql      # Struktur aus docs/architecture/schema.sql
│   │   ├── V2__rls_policies.sql  # RLS-Policies je mandantenbehafteter Tabelle
│   │   ├── V3__seed_categories.sql
│   │   └── V4__seed_privacy_notice.sql
│   └── logback-spring.xml        # JSON-Format, erlaubte Felder, kein Objekt-toString (FR-062)
├── src/main/java/com/promptguard/
│   ├── PromptGuardApplication.java
│   ├── common/                   # geteilte Bausteine ohne Fachlogik
│   │   ├── error/                # ProblemDetails-Fehlerklassen + Fehlerabbildung (ADR-009)
│   │   │   ├── ErrorCode.java
│   │   │   ├── ApiException.java, ValidationException.java, ConflictException.java …
│   │   │   └── GlobalExceptionHandler.java   # @RestControllerAdvice — die EINE Stelle
│   │   ├── security/             # Mandantenkontext, Korrelations-ID, Security-Konfiguration
│   │   │   ├── TenantContext.java, TenantContextFilter.java
│   │   │   ├── CorrelationIdFilter.java
│   │   │   └── SecurityConfig.java
│   │   ├── persistence/          # Basis-Repositories, Specification-Basis mit Tenant-Filter
│   │   ├── observability/        # Metriken, Log-Konfiguration, Traces (aus)
│   │   └── ratelimit/            # Rate-Limiter (SEC-008)
│   ├── identity/                 # E1 — US-001…US-006
│   │   ├── api/                  # AuthController, AccountController, OrganisationController
│   │   ├── domain/               # User, Membership, Organisation, Invitation, AlertRecipient
│   │   ├── application/          # AuthService, MembershipService, InvitationService
│   │   └── infrastructure/       # UserRepository (mit organisationId-Pflichtparameter), TokenService
│   ├── prompt/                   # E2 — US-007…US-012
│   │   ├── api/                  # PromptController, TaxonomyController
│   │   ├── domain/               # Prompt, PromptVersion, PromptTag, Category, Tag
│   │   ├── application/          # PromptService, VersionService, TagService, CategoryService
│   │   └── infrastructure/       # PromptRepository, PromptVersionRepository, SearchRepository
│   ├── search/                   # E3 — US-013…US-015
│   │   ├── api/                  # (Filter/Sortierung in PromptController; Suche hier)
│   │   ├── application/          # PromptSearchService (Specification, Index-Nutzung)
│   │   └── infrastructure/       # PromptSearchRepository (tsvector + organisation_id)
│   ├── scanning/                 # E4 — US-016…US-023
│   │   ├── api/                  # ScanController
│   │   ├── domain/               # ScanRun, ScanFinding, DuplicateMatch, Waiver
│   │   ├── application/          # ScanOrchestrator, PrecheckService, DuplicateService,
│   │   │                         # AggregationService, WaiverService, ScanResultService
│   │   ├── ports/                # ClassificationPort, EmbeddingPort (K-8, Langchain4j)
│   │   ├── adapters/langchain4j/ # Langchain4j-Implementierung der Ports
│   │   └── infrastructure/       # ScanRunRepository, FindingRepository, JobRepository
│   ├── livestatus/               # E5 — US-024
│   │   ├── api/                  # StreamController (SseEmitter)
│   │   ├── application/          # StatusStreamService (Snapshot + Sequenz)
│   │   └── infrastructure/       # StatusEventRepository (Outbox)
│   ├── audit/                    # E5 — US-025
│   │   ├── domain/               # AuditEvent, StatusTransition
│   │   └── application/          # AuditService (schreibt inhaltsfrei)
│   ├── notification/             # E6 — US-026, US-027
│   │   ├── api/                  # NotificationController
│   │   ├── domain/               # NotificationDelivery
│   │   ├── application/          # NotificationService, AlertMailComposer (nur Metadaten)
│   │   └── infrastructure/       # NotificationRepository, MailPort
│   ├── privacy/                  # E7 — US-028…US-030
│   │   ├── api/                  # PrivacyController, RetentionController
│   │   ├── application/          # RetentionService, ErasureService, ExportService
│   │   └── infrastructure/       # RetentionRepository, PrivacyNoticeRepository
│   ├── provider/                 # E4/US-022 — Provider-Verifikation (ADR-006)
│   │   ├── api/                  # ProviderController
│   │   ├── application/          # ProviderVerificationService (Gate)
│   │   └── domain/               # ProviderVerification, ProviderCheck
│   └── operations/               # E8 — US-031…US-035
│       ├── api/                  # OperationsController (Health, Metriken, Quality-Gate)
│       └── application/          # QualityGateService, MetricsService
├── src/main/resources/messages/  # i18n der Fehlertexte (englisch)
└── src/test/java/com/promptguard/
    ├── architecture/             # ArchUnit-Regeln (Schichten, keine Zyklen, Port-Impl-Regel)
    ├── common/
    ├── identity/                 # Unit + Integration (Testcontainers)
    ├── prompt/
    ├── search/
    ├── scanning/
    │   └── fixtures/             # Stubs/Fakes für ClassificationPort + EmbeddingPort (K-8)
    ├── livestatus/
    ├── audit/
    ├── notification/
    ├── privacy/
    ├── provider/
    └── operations/
```

> **Worker als eigenes Modul:** `scan-worker`, `notification-worker` und `retention-job`
> teilen sich **denselben** Code (`common`, `scanning`, `notification`, `privacy`), haben
> aber eigene Einstiegspunkte und Prozessprofile. Ein Monorepo-Multi-Modul-Bau hält die
> Logik einmalig und die Deployments getrennt (ADR-001, Container-Diagramm).

---

## 3. Frontend — `src/frontend/`

```
src/frontend/
├── package.json                  # React 19, Next.js 15, TypeScript strict
├── next.config.ts
├── tsconfig.json                 # strict: true, noUncheckedIndexedAccess
├── vitest.config.ts              # Unit-/Komponententests + axe
├── playwright.config.ts          # E2E
├── Dockerfile                    # non-root, gepinnte Tags
├── src/
│   ├── app/                      # App Router
│   │   ├── layout.tsx
│   │   ├── providers.tsx         # QueryClient, Theme, Locale, AuthContext
│   │   ├── (auth)/
│   │   │   ├── login/page.tsx
│   │   │   └── register/page.tsx
│   │   ├── prompts/
│   │   │   ├── page.tsx                          # Liste: Suche, Filter, Sortierung, Pagination
│   │   │   ├── [promptId]/page.tsx               # Detail: Inhalt, Status, Fundstellen
│   │   │   ├── [promptId]/edit/page.tsx          # Editor (erzeugt neue Version)
│   │   │   ├── [promptId]/history/page.tsx        # Versionshistorie + Vergleich
│   │   │   └── [promptId]/audit/page.tsx         # Status- und Änderungshistorie
│   │   ├── flagged/page.tsx                      # Warteschlange des Security-Admins
│   │   ├── admin/
│   │   │   ├── members/page.tsx                  # Mitglieder, Rollen, Einladungen
│   │   │   ├── categories/page.tsx
│   │   │   ├── settings/page.tsx                 # Schwellen, Alarmempfänger
│   │   │   └── privacy/page.tsx                  # Datenschutzhinweis DE/EN
│   │   ├── error.tsx / global-error.tsx          # Fehlergrenzen des App Routers
│   │   └── not-found.tsx
│   ├── components/               # wiederverwendbare, barrierefreie Bausteine
│   │   ├── ui/                   # Button, Dialog, Table, Toast, FormField (Label/aria)
│   │   ├── status/               # StatusBadge (Textlabel + Symbol + Farbe), StatusLiveRegion
│   │   ├── prompts/              # PromptList, PromptEditor, VersionHistory, VersionDiff
│   │   ├── findings/            # FindingList (maskierte Ausschnitte), DuplicateWarning
│   │   └── audit/                # AuditTable, StatusTimeline
│   ├── features/                 # fachlich gekapselte Ausschnitte (je Epic)
│   │   ├── auth/ prompts/ search/ scanning/ livestatus/ audit/ notifications/ privacy/ admin/
│   ├── hooks/                    # usePromptList, useScanStatus (SSE), useFilters, usePagination
│   ├── lib/                      # http-Client (aus openapi.yaml generiert), sse.ts, locale.ts
│   ├── i18n/                     # de.json (Quelle), en.json, MessagesProvider
│   ├── types/                    # **generiert** aus docs/api/openapi.yaml (nicht handgeschrieben)
│   └── styles/                   # Design-Tokens mit geprüften Kontrasten
└── tests/
    ├── unit/                     # Vitest — reine Logik, Hooks, Hilfsfunktionen
    ├── components/               # React Testing Library + axe-core
    └── e2e/                      # Playwright: Login → Prompt → Scan → Alarm
```

**Regeln**

* `src/types` wird **generiert** (`openapi-typescript docs/api/openapi.yaml`), nie von Hand
  gepflegt — der Vertrag ist die einzige Quelle.
* `src/lib/http` nutzt den generierten Client; keine ad-hoc-`fetch`-Aufrufe.
* Alle datenabhängigen Ansichten sind **Client Components**; der App Router liefert Routing,
  Layout und Fehlergrenzen (ADR-001).
* Kein `any`, keine unbehandelten Promises, keine ungelösten Lint-Warnungen (AC-035-3).

---

## 4. Tests — `tests/`

> In diesem Repository liegen die **schrittübergreifenden** Tests. Die schnellen
> Unit-/Komponententests liegen **je Modul** beim Code (`src/backend/src/test`,
> `src/frontend/tests`), damit sie beim Modulbuild mitlaufen; hier liegen die
> **übergreifenden** und **e2e**-Tests.

```
tests/
├── backend/
│   ├── contract/                 # Vertragsprüfung gegen docs/api/openapi.yaml
│   │   ├── test_contract_structure.py    # Pflichtangaben je Operation
│   │   ├── test_story_coverage.py        # jede Story hat eine Operation
│   │   └── test_error_format.py          # ein Fehlerformat für alle
│   ├── tenant/                   # SEC-002 — Negativtests „Cross-Tenant liefert keine Daten"
│   │   ├── test_cross_tenant_read.py
│   │   ├── test_cross_tenant_write.py
│   │   ├── test_rls_without_filter.py    # Query ohne Filter liefert leere Menge
│   │   ├── test_pool_context_reset.py    # Mandant A → Mandant B über denselben Pool
│   │   └── test_job_without_tenant.py    # Job ohne Kontext wird verworfen
│   ├── security/                 # SEC-001…SEC-012
│   │   ├── test_rate_limit.py
│   │   ├── test_password_hashing.py
│   │   ├── test_refresh_rotation_replay.py
│   │   ├── test_security_headers.py
│   │   ├── test_no_content_in_logs.py    # AC-032-2
│   │   └── test_error_no_stacktrace.py   # AC-032-3
│   ├── provider/                 # US-022
│   │   ├── test_provider_verification_gate.py
│   │   └── test_no_content_leaves_on_violation.py
│   ├── retention/                # US-028
│   │   ├── test_retention_idempotent.py
│   │   └── test_retention_keeps_prompts.py
│   └── erasure/                  # US-029
│       ├── test_erasure_anonymises_authorship.py
│       └── test_erasure_keeps_audit.py
├── frontend/
│   ├── a11y/                     # axe über alle Kernseiten
│   ├── locale/                   # AC-035-4 — Oberfläche deutsch, API englisch
│   └── live-status/              # AC-024-1…3 — SSE, Reconnect, Snapshot
└── e2e/
    ├── test_login_prompt_scan_alert.py   # NFR-010 — Login → Prompt → Scan → Alarm
    └── test_degraded_mode.py             # US-033 — KI aus: Anlegen funktioniert
```

**Coverage-Gate:** 80 % **Line Coverage** je Komponente (Backend **und** Frontend), harter
Blocker in der CI (F9, FR-073). Generierter Code (DTOs, OpenAPI-Client) ist ausgenommen.
Der Nachweis wird über `POST /ops/quality-gate` an die Plattform gemeldet (US-031).

---

## 5. Zuordnung der Module zu den Artefakten

| Modul | Anforderungen | Vertrag | Schema | ADR | Diagramm |
|---|---|---|---|---|---|
| `common/error` | SEC-009, AC-032-3 | `Problem`, `FieldError` | — | ADR-009 | `03-component-api.mmd` |
| `common/security` | FR-001, FR-009, SEC-001…SEC-004 | `bearerAuth`, `x-tenant-scope` | `app_user`, `membership` | ADR-002 | `08-tenant-isolation.mmd` |
| `identity` | US-001…US-006 | `/auth/**`, `/me*`, `/organisations/**` | `app_user`, `membership`, `invitation`, `alert_recipient` | ADR-002 | `01-context.mmd` |
| `prompt` | US-007…US-012 | `/prompts/**`, `/categories`, `/tags` | `prompt`, `prompt_version`, `category`, `tag`, `prompt_tag` | ADR-003 | `07-domain-model.mmd` |
| `search` | US-013…US-015 | `listPrompts` | `ix_prompt_search` | ADR-002 | — |
| `scanning` | US-016…US-023 | `/prompt-versions/**`, `/provider/**` | `scan_run`, `scan_finding`, `duplicate_match`, `waiver`, `scan_job` | ADR-005…ADR-007 | `04-component-scan-worker.mmd`, `05-sequence-scan-process.mmd` |
| `livestatus` | US-024 | `streamPromptStatus`, `getPromptStatus` | `status_event` | ADR-008 | `05-sequence-scan-process.mmd` |
| `audit` | US-025 | `listAuditEvents`, `listPromptStatusHistory` | `audit_event`, `status_transition` | ADR-003 | — |
| `notification` | US-026, US-027 | `/notifications/**` | `notification_delivery` | ADR-005 | `02-container.mmd` |
| `privacy` | US-028…US-030 | `/ops/retention`, `/privacy-notice`, Löschpfade | `retention_run`, `privacy_notice`, `scan_raw_payload` | ADR-004 | `02-container.mmd` |
| `provider` | US-022, US-034 | `/provider/verification` | `transfer_audit` | ADR-006 | `04-component-scan-worker.mmd` |
| `operations` | US-031…US-035 | `/health`, `/ops/**` | `quality_gate_attestation` | ADR-001, ADR-009 | — |

---

## 6. Ablage der Architektur-Artefakte (unverändert aus Schritt 2)

| Artefakt | Ablage | Bleibt stabil? |
|---|---|---|
| OpenAPI-Vertrag | `docs/api/openapi.yaml` | **eingefroren** nach Gate 1 |
| Vertragsprüfung | `docs/api/check_contract.py` | wird in CI ausgeführt |
| Datenmodell | `docs/architecture/schema.sql` | Referenz für Flyway-Migrationen |
| C4-Diagramme | `docs/architecture/diagrams/*.mmd` | geändert nur bei Architekturänderungen |
| ADRs | `docs/adr/ADR-*.md` | **append-only** — neue Entscheidung ⇒ neue ADR, keine Überschreibung |
| Schritt-Artefakte | `.agent/artifacts/architecture.md`, `folder-structure.md`, `api-review.md` | je Schritt eigene Dateien |

**Regel:** Ändert sich der Vertrag nach dem Gate, braucht es eine **neue Revision** der Datei
**und** eine ADR — keine stille Änderung im Code (openapi-first, ADR-009).

---

## 7. Benennungskonventionen

| Artefakt | Konvention | Beispiel |
|---|---|---|
| Java-Package | `com.promptguard.<bounded-context>.<layer>` | `com.promptguard.scanning.application` |
| Java-Klasse | camelCase, Fachbegriff im Englischen | `PromptVersionService` |
| Java-Schnittstelle (Port) | `<Fachbegriff>Port` | `ClassificationPort` |
| Java-Test | `<Klasse>Test` (Unit), `<Baustein>IT` (Integration, Testcontainers) | `PromptServiceTest`, `TenantIsolationIT` |
| TypeScript-Datei | camelCase (`useScanStatus.ts`), Komponenten PascalCase | `StatusBadge.tsx` |
| API-Pfad | kebab-case, plural | `/prompt-versions/{id}/findings` |
| Datenbanktabelle | Singular, snake_case | `prompt_version` |
| Flyway-Migration | `V<n>__<snake_case>.sql` | `V2__rls_policies.sql` |
| Operation-Id | camelCase, Verb + Objekt | `assignPromptTags` |

---

## 8. Abweichungen und offene Punkte

| # | Punkt | Umgang |
|---|---|---|
| F-1 | Schema liegt in `docs/architecture/schema.sql`, nicht in `.agent/knowledge/schema.sql` | vor Schritt 5 spiegeln (Befund B-01) |
| F-2 | Build-Tool des Frontends: `next build` (kein Vite) | ADR-001 (revidiert in Schritt 2) |
| F-3 | Worker-Ausprägung als Maven-Modul gegen getrennte Services | Festlegung in Schritt 5 (Teststrategie) verbindlich |
| F-4 | Kein `src/types` von Hand | generiert aus dem Vertrag (Single Source of Truth) |

---

## 9. Verweise

* `docs/architecture/overview.md` — Schichten und Module
* `docs/architecture/domain-model.md` — Entitäten und Tabellen
* `docs/api/openapi.yaml` — Vertrag (Quelle der API-Typen)
* `docs/adr/ADR-001…ADR-009` — Entscheidungen
* `AGENTS.md` — Folder Ownership und Rechte
