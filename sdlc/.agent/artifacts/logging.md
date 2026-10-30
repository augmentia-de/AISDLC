# Logging convention — <project name>

> **Template.** Is populated from the code in step 6 (implementation) — the
> Convention follows code, not the other way around. The security scan will check later
> **against this file**, not against your own expectations.
>
> If this file is still at the template level, that is itself a finding:
> “Logging convention not committed”.

##Principle
```
text
EINE ZEILE JE EREIGNIS
```
No multi-line stack trace in the log entry, no free text blocks, none
Stack traces in the **response** to the caller.

## Mandatory fields per line

| field | Type | Meaning |
|---|---|---|
| `ts` | ISO-8601 | Time of event, UTC |
| `level` | Enum | `INFO`, `WARN`, `ERROR` |
| `event` | String | Event type, stable, dot separated (`delivery.attempt`, `auth.token.rotated`) |
| `correlationId` | UUID | **continuously** via request → database → delivery |
| `tenant` | String | Tenant, **only** if the context is client-limited |
| `outcome` | Enum | `ok`, `rejected`, `failed` |
| `durationMs` | number | Duration of the operation |

**Optional:** `attempt` (attempt count), `status` (HTTP status), `errorCode`
(stable error code from the contract — **no** exception text).

## What is **not** allowed in

| Forbidden | Why |
|---|---|
| Payloads, payloads, request bodies | Data protection, data minimization |
| Passwords, tokens, secrets, API keys | Credential leak |
| complete personal records | Data protection |
| Exception stack traces **in the answer** | internal details to the outside |
| SQL statements with values ​​​​​​| Data minimization, data leak |
| Cross-tenant IDs without context | Client breach |

A stack trace **in the log** is allowed and desired - it is diagnostic. He can only
not in the answer.

## Correlation ID

The ID is created at the **input** (middleware/filter) and is carried by:
```
Request → Service → Datenbank → Zustellprozess → Log
```
**Rules:**
- It is unique per request/operation, not per client.
- It **doesn't** go into the answer unless the contract says so - otherwise
  It provides an attack vector for understanding third-party requests.
- The delivery process creates a **own** ID for each delivery attempt and keeps it
  original as `parentCorrelationId` with. This leaves the path from the event to
  traceable for delivery.

## Health and readiness

The health check reads the same format:

| Endpoint | Answer | Purpose |
|---|---|---|
| `GET /health` | `{"status":"up"}` | the process lives |
| `GET /health/ready` | `{"status":"ready","checks":{…}}` | it works — database, worker, client context |

**The readiness check checks the last heartbeat**, not just whether the process
exists. A process whose delivery is deadlocked is alive - but is not
ready. This is exactly what a pure process check cannot find.

## Retention

| level | Duration | reason |
|---|---|---|
| Operating log | <specification> days | Diagnosis |
| Audit log | <specification> days | Proof |
| Raw telemetry | <specification> days | Evaluation |

After expiration it is **deleted**, not just removed from the evaluation.

## Implementation via stack

| field | Java/Spring Boot | TypeScript/Node |
|---|---|---|
| Format | JSON per line (logback) | JSON per line (pino) |
| `correlationId` | MDC | AsyncLocalStorage |
| `tenant` | MDC, from the JWT | AsyncLocalStorage |
| Health | Actuator | own endpoint |

**The specific library is a stack thing, the convention is not.** This file
describes the format, not the technology.

## Check through the security scan

| # | exam | Heavy |
|---|---|---|
| L-1 | This convention exists and is filled | gap |
| L-2 | No payload, no secret, no personal data in logs | gap |
| L-3 | No stacktrace in response | gap |
| L-4 | Each line has the mandatory fields | gap |
| L-5 | The correlation ID is consistent | Grade |
| L-6 | Retention is fixed and effective | Grade |