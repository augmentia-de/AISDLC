# Zielstruktur `src/` — Kurzfassung

> Schritt 2 (Konzept & Architektur) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> Volltext: `docs/architecture/folder-structure.md`
> **Noch kein Code** — die Pfade sind die Zielstruktur für Schritt 6.

```
src/
├── main/
│   ├── java/com/echobox/
│   │   ├── endpoint/      EndpointController, EndpointService, Endpoint, EndpointQueryService, EndpointRepository
│   │   ├── event/         EventController, EventService, Event, EventRepository, IdempotencyStore
│   │   ├── delivery/      DeliveryWorker, DeliveryService, DeliveryController, DeliveryStateMachine,
│   │   │                  DeliveryDispatcher, DeliveryHttpClient, DeliveryHeaders, DeliveryOutcome,
│   │   │                  BackoffPolicy, RetryScheduler, PayloadSource, EndpointRateLimiter, DeliveryRepository
│   │   ├── crypto/        PayloadCipher, SignatureService, SecretStore
│   │   ├── protocol/      ProtocolController, ProtocolQueryService, DeadDeliveryQueryService,
│   │   │                  EndpointMetricsService, MetricsController, RetentionService
│   │   ├── tenant/        TenantContext, TenantConnectionInitializer, TenantFilter
│   │   ├── config/        TenantInstanceConfiguration, DeliveryProperties, SecurityConfiguration
│   │   └── common/        Problem, ProblemFactory, GlobalExceptionHandler, PageResponse, CursorCodec,
│   │                      ClockConfig, IdGenerator
│   ├── resources/
│   │   ├── application.yml, application-staging.yml
│   │   └── db/migration/V1__initial_schema.sql   ← Kopie von .agent/knowledge/schema.sql
│   └── frontend/src/
│       ├── app/           layout.tsx, endpoints/page.tsx, deliveries/page.tsx, dead-deliveries/page.tsx
│       ├── features/      endpoints/{EndpointList,EndpointForm}.tsx, protocol/{DeliveryTable,DeliveryFilters}.tsx,
│       │                  dead/DeadDeliveryTable.tsx
│       ├── components/    DataTable.tsx, StatusBadge.tsx, EmptyState.tsx, FieldError.tsx
│       ├── lib/           apiClient.ts, cursor.ts
│       └── types/         api.ts   ← aus dem Vertrag abgeleitet
└── test/                  → Tests liegen unter tests/, nicht unter src/test
```

## Konventionen

| Thema | Regel |
|---|---|
| Schichten | `*Controller` (HTTP) → `*Service` (Fachlogik) → `*Repository` (JDBC). Die Fachlogik kennt keine HTTP- und keine JDBC-Typen. |
| Ports | `DeliveryPort`, `SecretPort`, `ClockPort`, `IdGenerator`, Repositories — alle nach außen gerichteten Zugriffe laufen über Ports. |
| SQL | Nur vorbereitete Statements über `JdbcTemplate` mit benannten Parametern. Kein String-Konkatenieren, kein dynamisches SQL. Jede Abfrage trägt den Mandanten. |
| Fehler | Ausnahmen aus `common`; die Abbildung auf HTTP geschieht **ausschließlich** in `GlobalExceptionHandler`. |
| Kein Klartext | Nutzlast und Secrets erscheinen in **keinem** Log, **keiner** Fehlermeldung, **keinem** `toString()`. |
| Konfiguration | Alle Zahlenwerte kommen aus `DeliveryProperties` (ENV). **Keine** Zahl ist im Code verdrahtet. |
| Tests | Unit ohne Datenbank; Integration gegen **echte** PostgreSQL 16 (Testcontainers); E2E mit Playwright; axe-Scan für die Barrierefreiheit. |

## Planungsauftrag an OpenCode (Schritt 6)

> Lies `docs/api/openapi.yaml` und erstelle einen Schritt-für-Schritt-Plan für
> die Implementierung der neuen API-Stubs, DTOs und Typen.

**Abbildung der Auftragspfade auf die Projektstruktur:**

| Auftrag | Projektpfad |
|---|---|
| `src/types` (DTOs und Typen) | `src/main/java/com/echobox/<modul>/api/dto/` (Java-Spiegel der Schemas aus `components/schemas`) und `src/main/frontend/src/types/api.ts` (aus demselben Vertrag generiert) |
| `src/controllers` (Controller) | `src/main/java/com/echobox/<modul>/<Modul>Controller.java` (ein `operationId` je Operation) und `src/main/frontend/src/features/<bereich>/` |

**Reihenfolge:** gemeinsame Schemas → `endpoint` → `crypto` → `event` → `delivery` → `protocol` → `common` → `tenant` → Oberfläche.

**Regeln:** kein Feld und keine Operation erfinden, die nicht im Vertrag stehen;
je Operation ein Vertragstest (Statuscode, Pflichtfelder, Fehlerformat); der
Vertrag wird in Schritt 6 **nicht** geändert.
