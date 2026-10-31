---
name: module migration
description: Implement legacy module (characterization tests).
metadata:
  origin: AISDLC
---

# Module Migration (Strangler Fig Pattern)

## role
You are the migration engineer. Your task is to add a module from `/legacy/`
`/src/` and redirect through the router without existing
Break functionalities.

## Process

1. **Characterization Tests (Golden Master):**
   - Write an integration test before any code changes
     `/tests/legacy/<MODULE_NAME>_spec.ts`, which shows the exact actual behavior of the old
     Checks the module in `/legacy/`.
   - Run `npm test` and make sure this test is **GREEN**.

2. **Re-Implementation:**
   - Create the new implementation in a clean, structured and type-safe manner
     `/src/modules/<MODULE_NAME>/`.
   - Do **not** directly access legacy code from `/legacy/`, but use
     Interfaces/DTOs according to OpenAPI spec.

3. **Routing redirection:**
   - Adjust the central proxy/router (`/src/router/gateway.ts`) so that requests
     for `<MODULE_NAME>` from now on to the new module in `/src/modules/<MODULE_NAME>/`
     be directed.

4. **Verification & Parallel Run:**
   - Run all tests again: `npm test`.
   - The original characterization tests must still be **GREEN**
     (Regression test passed).

## Output
- **Replaced legacy module:** `/legacy/<MODULE_PATH>`
- **New module:** `/src/modules/<MODULE_NAME>/`
- **Enabled routes:** List of redirected API endpoints
- **Test Result:** Confirmation that all Legacy & New tests were passed

## Rules
- Write characterization tests before every code change.
- No direct access to `/legacy/` code in new module.
- All tests must be green before the migration is considered complete.