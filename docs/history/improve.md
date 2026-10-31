# AI-SDLC3: Common best practice improvement version

This document combines criticism, analysis and concrete implementation steps to make AISDLC3 usable for both small projects/MVPs and enterprise projects. The goal is a **profile-based, modular approach** (instead of two separate codebases) that preserves governance but reduces overhead, fragility and security risks.

## 1) Critical Points & Rating

| Criticism | Heavy | Status | Rating |
| :-- | :-- | :-- | :-- |
| **High token costs & overhead** | High | Partially mitigated | Profile (`briefing.md`), short answers, skill loading on demand help, but are not enough for MVPs. |
| **Process rigidity (bureaucracy)** | High | Too strict | 9-phase cycle, contract freezing, mandatory gates slow down exploratory development. “Nachzug” shows breaking the rules. |
| **Fragile Subagent Responses (3 Line Rule)** | Very high | Critical | Orchestrator's parser depends on exact format. Free text breaks the process easily. |
| **Dependence on initial specification** | High | Well addressed | `aisdlc-grill-stress`, `REQUIREMENT_GAP` present, but not foolproof. |
| **Shell permissions (global `allow`)** | Critical | Inadequate | Biggest real security risk. Path protection alone is not enough. |
| **Git commit trick (`git reset -q && git add -A -- <paths>`)** | High | Fragile | Breaks easily (e.g. by tools like `rtk`), pathspec problems with empty folders. |
| **Traceability as a hard gate blocker** | High | Too strict | Regex/naming differences can block deployment unnecessarily. |

## 2) Best practice solution: Profile-based approach

A unified framework with a configurable profile is clearly preferable to forking. Centralize configuration and consistently enforce it from the **orchestrator**.

### 2.1 Mode definition

| Dimension | `profile: mvp` (Small Project/MVP) | `profile: enterprise` (Enterprise/Compliance) |
| :-- | :-- | :-- |
| **Phase Scope** | Reduced to core phases: `Requirements → (minimal architecture) → Code → Test → Deploy` (3-5 steps effective) | Full 9-phase cycle including Simplify, Grill-Me, Security, CI pipeline |
| **Documentation** | Minimal: `docs/requirements/PRD.md` + necessary code artifacts. ADRs/Glossary only if required. | Complete: ADRs, C4, OpenAPI, glossary, traceability, security audit, deploy report |
| **Quality Gates** | Loose/Manual, focus on functionality. Security minimum (secrets scan). Folder ownership & commits remain mandatory. | Strict: GATE 1 & 2, `traceability_check` hard on critical paths, Security 0 Critical/High, coverage targets |
| **Traceability** | `light` (Story→Code) - warning about gaps | `full` (Req→Story→Feature→Test→Code) - Hard fail on contract/critical paths |
| **Contract Freeze** | Only enforce contracts classified as `external`/`public` | Strictly according to step 4, return to step 2 when changing the contract |
| **Fast Track/Patch** | Allowed (with contract review) | Strongly restricted, only with reasons & review |

### 2.2 New central configuration

**Create new:** `.agent/config.json
```
`json
{
  "$schema": "./schemas/config.schema.json",
  "profile": "mvp",
  "allow_fast_track": true,
  "traceability": "light",
  "gates": {
    "enforce_gate1": true,
    "enforce_gate2": true,
    "traceability_hard_fail": false
  },
  "security": {
    "enforce_minimal_scan": true,
    "block_on_critical_secrets": true
  },
  "context": {
    "briefing_first": true,
    "strict_three_line_reply": false
  }
}
```
**Customize:** `.opencode/opencode.jsonc` - add `profile` as default reference (backwards compatible). Primary source remains `.agent/config.json` as subagents can access it more reliably.

## 3) Specific changes per point of criticism

### 3.1 Token Costs & Overhead
| Location | Change | Justification |
| :-- | :-- | :-- |
| `.agent/config.json` | Control profile | Central control |
| `.agent/PHASES.md` | New columns `mvp` (`yes\|no\|optional`), `required_artifacts` per step | Orchestrator only skips truly optional artifacts/steps |
| `.agent/prompts/orchestrator/*` | Profile-dependent checklists | Only list necessary files in the briefing |
| `.opencode/agents/aisdlc-orchestrator.md` | "If `profile: mvp` -> skip optional artifacts (glossary, extensive ADR sets) unless needed" | Less context, fewer tokens |

### 3.2 Process rigidity & bounces
| Location | Change | Justification |
| :-- | :-- | :-- |
| `.opencode/agents/aisdlc-orchestrator.md` | Add **Fast Track/Patch Mode** | Direct correction loop for bug fixes without a full phase tree |
| `AGENTS.md` | Add “patch” to escalation | Clear rules |
| `docs/architecture/contract-matrix.md` (new) | Enter contractual partner/classification (`internal\|external\|public`) | Force strict freeze/return only for `external/public` |

**Rule:** Fast track only allowed if **no** contract files are affected (`docs/api/**`, `docs/architecture/contract*`, `openapi.yaml`). Otherwise light gate or full rebound.

### 3.3 Fragile 3-line answers
| Location | Change | Justification |
| :-- | :-- | :-- |
| `.opencode/agents/aisdlc-orchestrator.md` §3a | Switch parsing to **regex extraction** | More tolerant of free text/Markdown |
| `.opencode/agents/aisdlc-orchestrator.md` | Fallback mechanism | If parsing fails, request correction once, do not cancel immediately |

**Regex suggestion:**
```
regex
/(?m)^STATUS:\s*(done|done_with_open_points|blocked)\b.*$/
/(?m)^COMMIT:\s*(.+)$/
/(?m)^OFFENE PUNKTE:\s*(.+)$/
```
Extract everywhere in the output (not just "first three lines"), ignore introductions/headings.

### 3.4 Shell Permissions (Least Privilege)
| Location | Change | Justification |
| :-- | :-- | :-- |
| `.opencode/opencode.jsonc` | **Default `deny`** for shell, only keep targeted `ask`/`allow` for destructive commands | Fixes biggest security risk |
| `.opencode/agents/*.md` | Per agent explicit `permissions.shell` (Allowlist) | Enforce Least Privilege |
| `AGENTS.md` | Correct comment on shell protection ("Protection via path rights alone is not enough") | More honest documentation |

**Suggestion for allowlists (based on the EchoBox full run):**
| agent | Shell | Remark |
| :-- | :-- | :-- |
| `aisdlc-requirements` | `deny` (or minimal `read-only` tools) | Should primarily read/document |
| `aisdlc-architecture` | Very limited (`git log/status/diff`, read-only) | Mostly documentation |
| `aisdlc-execution` | `allow` for `git`, `mvn`/`npm`/`pnpm`/`yarn`, `node`, `python`, `java`, `bash` (project only) | Build/Test/Run required |
| `aisdlc-qa` | `allow` for `git`, test runner, linter/scanner | QA/Scan |
| `aisdlc-orchestrator` | `deny` for execution, only minimal for Git status/log to check | May never edit yourself, shell only for verification |

**Note:** Before switching to default deny, extract **full list** of actually used commands from `run-log.jsonl`.

### 3.5 Traceability more robust & mode specific
| Location | Change | Justification |
| :-- | :-- | :-- |
| `.agent/tools/traceability_check.py` | Modes `off\|light\|full`, `--mode`, fuzzy-matching, ignore-list | Less fragile |
| `AGENTS.md` / Orchestrator | Soft-Fail/Hard-Fail per profile & contract class | Prevents blocking due to regex errors |
| GATE 2 Logic | Force hard fail with 0 gaps only for `external/public` + `full` | Pragmatic but safe |

### 3.6 Git commits more robust
| Location | Change | Justification |
| :-- | :-- | :-- |
| `AGENTS.md` commit convention | Complement robust approach | Less fragile compared to `rtk` |
| Orchestrator/Workflows | Validation before commit (`git diff --cached --name-only`), existence check | Early detection of errors |
| Suggestion | Keep `git add -A -- <paths>`, but test/validate `git commit -m ... -m ... -- <paths>` as a fallback or `git commit --only -- <paths>` | More compatible with tools |

## 4) Steps that can be implemented immediately (order)

To minimize risks, proceed **incrementally**:

| # | Action | Target | Status |
| :-- | :-- | :-- | :-- |
| **1** | Create `.agent/config.json` | Centralize profile | **TODO** |
| **2** | Create `.agent/schemas/config.schema.json` | Validation | **TODO** |
| **3** | Extend `.agent/PHASES.md` with `mvp` columns | Orchestrator can skip | **TODO** |
| **4** | Create `docs/architecture/contract-matrix.md` | Contract classification for Freeze/Fast-Track | **TODO** |
| **5** | Customize `.opencode/agents/aisdlc-orchestrator.md` | Read Profile, Regex-parsing, fast-track, mode-specific gates | **TODO** |
| **6** | Expand `.agent/tools/traceability_check.py` | `off/light/full`, soft/hard fail | **TODO** |
| **7** | Customize `AGENTS.md` | Add/correct rules for profile, shell, parsing, fast track | **TODO** |
| **8** | **Change shell:** `.opencode/opocolio.jsonc` to default-deny + agent-allowlists | Fix security risk | **TODO** (very carefully) |
| **9** Make the commit convention more robust | Evaluate/secure index trick | Less fragile | **TODO** |

> Note: Correct typo `opocolio.jsonc` to `opencode.jsonc` when implementing.

## 5) Conclusion

The proposed profile-based approach is the **right strategic solution**. It balances perfectly between:

- **Enterprise:** Full governance, auditability, strict gates, full traceability
- **MVP/Agile:** Less overhead, faster iterations, pragmatic gates, more direct work

**Most important immediate fixes:** Tolerant parsing + default deny for shell + central profile switch. With the above-mentioned sequence, risky changes (shell) can only be implemented after the required commands have been completely recorded, thus avoiding breaks.

**Core principles remain non-negotiable:** Delegation instead of execution, folder ownership, atomic commits per step (mode-specifically reduced, not abolished), briefing as an entry point.


## 6) Implementation carried out (status)

Implemented in this iteration:

| Change | Location | Remark |
| :-- | :-- | :-- |
| **Central configuration** | `.agent/config.json`, `.agent/schemas/config.schema.json` | Profile `mvp`/`enterprise`, Gates/Traceability/Security configurable |
| **Contract Classification** | `docs/architecture/contract-matrix.md` | Basis for Freeze/Fast-Track |
| **Phase Mapping** | `.agent/PHASES.md` | MVP/Enterprise marking per step |
| **Orchestrator** | `.opencode/agents/aisdlc-orchestrator.md` | Profile reading, regex-based parsing, fast track with contract checking, profile-dependent gates |
| **Shell Least Privilege** | `.opencode/opencode.jsonc`, Agent-Frontmatter (requirements/architecture/execution/qa/orchestrator) | Default deny, targeted allowlists |
| **Traceability** | `.agent/tools/traceability_check.py` | `--mode off/light/full`, soft fail in MVP/light |
| **AGENTS.md** | Rules added | Profile, Fast Track, Soft/Hard Fail, Shell Notice |

**Test directory:** `test/mvp/` and `test/enterprise/` created for future profile tests.