---
description: Phase 4 — Grill-Me stress test for architecture validation
---

# Grill-Me Stress Test Workflow

## Prerequisites

- Read `AGENTS.md` and `.agent/artifacts/briefing.json` first.
- Read your phase prompt: `.agent/prompts/orchestrator/02_orchestrator_architecture.md`.
- Read the canonical contract `docs/api/openapi.yaml` (not `.agent/knowledge/` — that is a reference),
  `docs/architecture/`, `docs/adr/`, `docs/requirements/features/*.feature`.
- Runs after step 3 (Simplify). The contract is **not yet frozen** — it freezes at the end of this step.

## Agent & Skill

- **Agent:** `aisdlc-architecture` · **Skill:** `aisdlc-grill-stress`

## Steps

1. **Build the design tree.** Root = the intended effect from the PRD. Every node is one
   decision with a status: **proven** (requirement → decision → mechanism → evidence) or
   **gap → S-<n>**. Visit every branch; nothing is assumed silently.
2. **Interrogate the frontier.** All decisions whose preconditions are settled, in one round,
   numbered, each with your recommendation. Facts (filesystem, tooling, existing code) are
   your job — do not ask the human for them. In HITL mode the frontier goes to the human;
   without HITL you answer everything you can prove and record the rest as open decisions
   `F-<n>` in the report.
3. **Review on two independent axes** — one must not mask the other:
   - **Spec:** `docs/api/openapi.yaml`, `docs/requirements/features/*.feature`, ADRs, schema.
   - **Standards:** repo conventions, naming, structure, test coverage, stack rules.
4. **Check the developer interface on nine dimensions** — contract *and* module/seam
   structure, from the point of view of the developer who uses it. Each dimension gets a
   finding and a rating:

   | # | Dimension | Question |
   |---|---|---|
   | 1 | Contractual / self-describing | Can a developer understand an operation without asking? |
   | 2 | Error clarity | Does an error say what to do — and is it distinguishable? |
   | 3 | Predictability | Same input → same result; stable ordering? |
   | 4 | Idempotency | Do repeated writes avoid silent duplicates? |
   | 5 | Tenant safety | Is isolation enforced in the contract **and** the implementation? |
   | 6 | Discoverability | Is the right operation findable? |
   | 7 | Consistency | One thing, one spelling, one catalogue? |
   | 8 | Extensibility | Do new cases avoid breaking the existing set? |
   | 9 | Testability | Can the behaviour be tested in isolation? |

5. **Classify every finding** as Critical / High / Medium / Low, with file:line, problem,
   effect, fix and whether it has **architectural impact**.
6. **Decide the verdict** (rule below) and write the report.
7. **Freeze or return.** `PASS`/`PASS_WITH_FINDINGS`: record the contract hash — the contract
   is frozen from this commit. `FAIL`: the report is the return ticket to step 2.

## Verdict rule

| Verdict | Condition | Consequence |
|---|---|---|
| `FAIL` | ≥ 1 Critical **or** ≥ 2 High **with architectural impact** | return to step 2 |
| `PASS_WITH_FINDINGS` | only Medium/Low open | contract frozen; findings go to the implementation backlog |
| `PASS` | nothing ≥ Medium | contract frozen |

## What this step must not do

- **No application code** in `src/` or `tests/`.
- **No contract edits.** A contract defect is a finding (`S-<n>`) and a return to step 2 —
  the contract is frozen after this step, never changed in passing.
- **No ADR rewrites.** An ADR conflict is a finding and an open decision `F-<n>`.
- **No silent fixes.** A provable documentation correction in `docs/architecture/` is allowed
  (typo, impossible statement). Anything semantic is a finding.

## Output

`.agent/artifacts/stress-test-report.md`, in this order:

1. **Header table:** step, agent, skill, reviewed commit, verdict, verdict history.
2. **Verdict** with the rule applied and counts per axis × severity.
3. **Design tree:** root, branches, every node `proven` or `gap → S-<n>`.
4. **Findings:** `S-<n>` (spec), `N-<n>` (standards) — file:line, problem, effect, fix,
   severity, status.
5. **Developer interface:** the nine dimensions with finding and rating.
6. **Open decisions** `F-<n>` for the human.
7. **What was not changed** and why (contract, ADRs, schema, requirements).
8. **Freeze recommendation:** contract hash and the explicit sentence that it is frozen.

Reports above ~20 KB start with a table (AGENTS.md rule 13).

## Rules

See `AGENTS.md`: no output commands. Commit scope below.

## Commit
```
bash
mkdir -p docs/architecture/ .agent/artifacts/
git reset -q
git add -A -- docs/architecture/ .agent/artifacts/
git commit -m "docs(sdlc-4): Stresstest-Bericht und offene Entscheidungen" \
  -m "Schritt 4 - Grill-Me-Stresstest | Agent: aisdlc-architecture | Artefakte: docs/architecture/, .agent/artifacts/"
```
- Formula, scope and justification: `AGENTS.md` -> Commit Convention. Here just the paths.
- If nothing has changed: do not create a commit (no error).

## Gate

**Contract freeze.** Approve when the verdict is `PASS` or `PASS_WITH_FINDINGS` and the report
names the frozen contract hash. On `FAIL` → return to step 2 with the report as the ticket.
Open decisions `F-<n>` are answered by the human here and belong in the next commit.

After the freeze, changes to the contract are governed by
`docs/architecture/contract-matrix.md`: `external`/`public` → jump back to step 2;
`internal` → justified change with impact analysis, no jump back.
