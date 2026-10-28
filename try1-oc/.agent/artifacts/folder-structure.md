# Ziel-Folder-Struktur — mini-ticket

```
mini-ticket/
├── .agent/
│   ├── artifacts/            # run-log, architecture.md, folder-structure.md, ...
│   └── knowledge/            # schema.sql (kanonisch), stack.md, openapi.yaml (Verweis)
├── docs/
│   ├── api/
│   │   └── openapi.yaml      # einziger Vertrag
│   ├── architecture/
│   │   ├── diagrams/         # context.mmd, container.mmd, component.mmd
│   │   ├── domain_model.md
│   │   ├── GLOSSARY.md
│   │   ├── contract-matrix.md
│   │   └── schema.sql        # Kopie (Spiegel-Regel)
│   ├── adr/                  # ADR-001..ADR-009
│   └── requirements/         # PRD, stories, features, traceability
├── src/
│   └── main/java/com/example/miniticket/
│       ├── MiniticketApplication.java
│       ├── config/            # SecurityConfig, CorsConfig, OpenApiConfig
│       ├── controller/        # HealthController, TicketController, CommentController
│       ├── service/           # TicketService, CommentService
│       ├── repository/        # TicketRepository, CommentRepository
│       ├── domain/            # Ticket, Comment (entities)
│       ├── dto/               # TicketDto, TicketCreateDto, TicketUpdateDto, CommentDto, ...
│       ├── mapper/            # TicketMapper, CommentMapper
│       ├── exception/         # GlobalExceptionHandler, ErrorResponse
│       └── validation/        # (falls noetig)
├── src/test/java/...         # Unit + Integration Tests
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml   # SQLite
│   ├── application-prod.yml  # Postgres
│   └── db/migration/         # Flyway V1__init.sql, ...
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

Hinweise:
- src/ und tests/ werden in Schritt 5/6 befuellt (read-only in Schritt 2).
- Schema-Mirror: .agent/knowledge/schema.sql == docs/architecture/schema.sql.
- Vertrag: nur docs/api/openapi.yaml.