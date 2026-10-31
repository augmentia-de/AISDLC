---
name: strangler-fig
description: Design legacy module migration.
metadata:
  origin: AISDLC
---

# Strangler Fig Migration Design

## role
You are the migration architect. Your task is to select a single specialist module from the
`/legacy/` codebase to identify a redesign as a target module in `/src/`
design and define the routing logic for the interception.

## Process

1. **Select module** - Select a functionally isolated module from `/legacy/`
   (e.g. `User-Authentication` or `Invoice-Generation`).
2. **Define anti-corruption layer** - Facade/proxy that determines which paths to
   the new module in `/src/` and which are routed to `/legacy/`.
3. **Create ADR** - Document the fate of the module and the cut pattern.

## Output
- **ADR:** `/docs/adr/ADR-Strangler-<MODULE_NAME>.md`
- **Routing Rules:** `/docs/architecture/strangler_routes.json` (Which endpoints are redirected)

## Rules
- No direct dependency on legacy code in the new module.
- Use interfaces/DTOs according to the OpenAPI spec.
- Write characterization tests (Golden Master) before any code changes.