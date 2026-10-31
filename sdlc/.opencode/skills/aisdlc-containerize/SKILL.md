---
name: aisdlc-containerize
description: Phase 6/9: Container image (multi-stage, non-root, pinned) + Compose.
metadata:
  origin: own framework procedure; Craft from ECC/docker patterns
---

## When

- **Phase 6**, in the Infra stream.
- **Phase 9**, before the first deployment.

## The iron law
```
text
KEIN SECRET IM IMAGE
```
Not even in a development layer. Secrets come at runtime
in, never in during the build.

## Mandatory properties

1. **Multi-Stage** — Build stage with tool chain, Runtime stage with runtime only.
2. **non-root** — own user, `USER` set, write permissions only for the app.
3. **Pinned base images** — specific tag or digest, never `latest`.
4. **No secrets in layers** — no credentials as build arg, no `.env` in the image.
5. **No dev dependencies in the runtime tier** — no test runner, none
   Source maps, no build cache.
6. **Health Check** — an endpoint that the orchestrator can call.
7. **Layer Order** — Dependencies first, then application code, like this
   Code changes do not invalidate the dependency layer.
8. **Reproducible** — no timestamps, no random values ​​in the image; same
   Input results in the same digest.

## Compose

The local setup mirrors the production: same images, same
Environment variable names, same ports, database from the canonical schema at
first start.

## Logging and health check belong together

The container writes to the format from `.agent/artifacts/logging.md` — one
Row per event, with correlation ID. The health check reads the same format:
it checks not only whether the process is alive, but whether it **works** (last
heartbeat event, timestamp). A health check that only sees the process
does not recognize a stuck delivery.

## Output

- `infra/<service>.Dockerfile`
- `infra/docker-compose.yml`
- `.dockerignore`
- `.agent/artifacts/containerization.md` — base image, stages, users, ports,
  Health check and the list of injected variables (names only)
- `.agent/artifacts/logging.md` — the project's logging convention: event field,
  Level, correlation ID flow, what is **not** logged, retention

## Rules

- An image starts with the same command in CI and in production.
- If the base image needs a package manager at runtime, this is a **finding** —
  the image is repaired, not bypassed.
- The canonical schema is **not** copied into the image; it will be at startup
  recorded.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| Dockerfile patterns, Compose overrides, container security | `docker-patterns` |
| Kubernetes deployment, health checks | `kubernetes-patterns` |
| CI pipeline with image build | `github-actions-templates` |