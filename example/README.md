# Example: TaskFlow — the reference application

This folder documents the **reference application** that proves the AISDLC pipeline works
end to end, and lists the skills that are still missing for it.

| File | Content |
|---|---|
| `taskflow.md` | The application itself: scope, users, features, data model, stack |
| `steps.md` | Per SDLC step: inputs, artifacts produced, skills used — and skills missing |
| `skills-to-create/` | Example `SKILL.md` drafts for the missing skills (ready to drop into `.opencode/skills/`) |

## How to use this folder

1. **Read `taskflow.md`** to understand what the reference app is.
2. **Walk `steps.md`** — it is the concrete runbook for building TaskFlow with the AISDLC
   framework: which artifact lands where after each step, which skill does the work.
3. **Copy the missing skills** from `skills-to-create/` into `.opencode/skills/` and add their
   prefix to the `skill` allowlist in `.opencode/opencode.jsonc`:

   ```jsonc
   { "action": "skill", "resource": "aisdlc-prd-to-stories", "effect": "allow" }
   ```

## Scope note

`skills-to-create/` contains **drafts**, not installed skills. They are deliberately
stack-agnostic: they describe the procedure, the artifacts and the acceptance criteria, and
leave the language-specific parts to the stack skills that come from the libraries
(`springboot-*`, `react-*`, …).

The phase-level skills that already exist stay authoritative for the phase procedure:

| Existing skill | Phase |
|---|---|
| `aisdlc-grill-stress` | 1a, 4 |
| `aisdlc-simplify` | 3, 7 |
| `aisdlc-tdd` | 5 |
| `aisdlc-security-scan` | 8 |
| `aisdlc-deploy` | 9 |
