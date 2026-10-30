---
name: aisdlc-containerize
description: Produces a production container image and a local compose setup - multi-stage, non-root, pinned base images, no secrets in the image. Use this skill in step 6 for the infrastructure stream and in step 9 before the first deploy.
metadata:
  origin: draft for AISDLC
---

##Purpose

A container that runs the same in CI, locally and in production, and that contains no
secret and no unnecessary privilege.

## When

- Step 6, infrastructure stream.
- Step 9, before the first deployment.

##Inputs

- `.agent/knowledge/stack.md` (runtime and version)
- The application's start command and port
- `.env.example` (variable names only — never values)

## Procedure

1. **Multi-stage** — build stage with the toolchain, runtime stage with only the runtime.
2. **Non-root** — a dedicated user, `USER` set, file permissions for the app only.
3. **Pinned base images** — a specific tag or digest, never `latest`.
4. **No secrets in the image** — build args and layers must not contain credentials; use
   runtime environment injection.
5. **No dev dependencies in the runtime stage** — no test runner, no source maps unless
   needed, no build cache.
6. **Health check** — a health endpoint the orchestrator can call.
7. **Layer order** — dependencies first, then application code, so that code changes do not
   invalidate the dependency layer.
8. **Reproducible** — no timestamps or random values ​​baked in; same input gives the same
   image digest.

## Compose

Local orchestration must mirror production: same images, same environment variable names,
same ports, and a database seeded from `.agent/knowledge/schema.sql` on first boot.

## Output

- `infra/<service>.Dockerfile`
- `infra/docker-compose.yml`
- `.dockerignore`
- `.agent/artifacts/containerization.md` — base image, stages, user, exposed ports, health
  check, and the list of injected variables (names only)

##Rules

- Never bake a secret into a layer, not even a developing one.
- The image must start with the same command in CI and in production.
- If the base image needs a package manager at runtime, that is a finding: fix the image,
  don't work around it.