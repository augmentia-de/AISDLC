---
name: aisdlc-security-scan
description: Phase 8: Read-only security and compliance audit.
metadata:
  origin: adapted from ECC/security-review + wshobson/security-scanning + wshobson/backend-api-security + superpowers/requesting-code-review
---

## When

After implementation (Phase 6/7), **before** deployment (Phase 9). This audit is
**read-only**: do not change the source file. Findings are reported as findings, not fixed.

## Exams

### 1. Authentication
- All protected routes require a valid token.
- Test without token header expects 401 (not 403, not 500).
- Token expiration, signature verification, target group (audience) and algorithm are checked.
- No home-made cryptography, no `none` algorithm.

### 2. Authorization
- **IDOR:** Each object route checks ownership, not just role.
- Default is deny. No `permitAll` on entire prefixes without justification.
- Tenant/organization scoping at **every** query level, not just in the controller.

### 3. Secrets
```
bash
grep -rInE "(api[_-]?key|secret|password|token|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ infra/ 2>/dev/null
```
- No secrets in the code, in config files, in Dockerfiles or CI/CD.
- `.env` to `.gitignore`; no secrets in the Git history.
- Secrets from environment or secret store, with error message if configuration is missing.

### 4. Injection
- All queries parameterized (ORM or Prepared Statements). No string concatenation,
  no f-string with variables in SQL.
- No `Runtime.exec`/`os.system` with user input; if necessary, with allowlist.
- Path traversal: Normalize file paths from inputs and check against a root directory.

### 5th Edition / XSS
- User input is escaped depending on the context.
- Search for `dangerouslySetInnerHTML`, `v-html`, `| safe`, `markup` without escaping.
- Error messages do not reveal internal details (stack trace, SQL, paths).

### 6. Session and transport
- Secure, HttpOnly and SameSite flags; secure cookies if session based.
- TLS enforced, HSTS set, sensitive security headers.
- CORS list explicit, no `*` along with credentials.

### 7. Rate limiting and resources
- Rate limiting for login, registration and expensive endpoints.
- File uploads: size limit, type allowlist, no unattended storage path.
- Unlimited pagination and body sizes prevented.

### 8. Dependencies and containers
- Known critical/high gaps in dependencies (SCA scan).
- Containers run as non-root, minimal base images with fixed tag (not `latest`),
  no unnecessary ports, secrets not in the image.
- CI/CD: no secrets in logs, OIDC instead of static keys, protected branches.

### 9. Logging and data protection
- **Check against `.agent/artifacts/logging.md`**, not against your own expectation.
  The convention is the benchmark; if it is missing, that itself is a finding.
- One line per event: correlation ID, event type, client, result, duration.
- No passwords, tokens, complete personal data sets, **none
  User data** in logs.
- **No stacktrace in the answer** - it is allowed in the log, not in the answer.
- Retention and deletion concept documented (e.g. 90 days, right to erasure).
- Personal data will not be sent via unnecessary channels.

### 10. OWASP Top 10 (cross-check)
Cross-check the above tests against the top 10 categories: A01 Broken Access Control,
A02 Cryptographic Failures, A03 Injection, A04 Insecure Design, A05 Security Misconfiguration,
A06 Vulnerable Components, A07 Identification/Auth Failures, A08 Data Integrity Failures,
A09 Logging/Monitoring Failures, A10 SSRF.

## Report format

| Heavy | File:Line | Finding | Exploitability | Recommendation |
|---|---|---|---|---|

- **Critical:** Data loss, tenant breach, secrets in repo, RCE
- **High:** Auth bypass, real path injection, missing authorization
- **Medium:** Defense-in-depth missing, header/CORS incorrect, logging incomplete
- **Low:** Hardening, documentation

Each finding states **evidence** (file:line or check carried out) and **exploitability**(how an attacker uses it). Assumptions are marked as open questions, not as findings.

##Rules

- Read only. No file changes, no configurationononon adjustments.
- No “Critical” without proven evidence.
- Mark each discovery with `[SECURITY]` prefix in the report.
- Null Findings are a result - but only if checked.

## Output form

**Each ~20KB report begins with a table**: Findings, Severity, Status, Where
fixed. The derivation is below. A table is the introduction, which is prose
the receipt - without the table someone has to read 100KB to count what's open.

## Output

`docs/qa/security-audit.md` with table of findings, overall rating and release recommendation
(release/block). Phase 9 only starts at 0 Critical and 0 High.