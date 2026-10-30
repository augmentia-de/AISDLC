# Operating mode without HITL

> **Status: Draft.** Implementation is open. Point (a) — `.agent/knowledge/`
> as the writing goal of the Architecture Orchestrator — has already been implemented
> (`38d16f1`). Everything else in this document is described, but still
> not built.

## Purpose

An unattended test run of the entire pipeline: 9 steps, 9 commits,
no questions asked, abort only if there is a real error. Purpose is not
Production operation — it is proof that the pipeline is self-sufficient
can go through without depending on people.

Normal operation remains HITL. Without HITL, the release decision no longer applies —
this is a loss of control, not a gain of speed. That's why the mode
Can be removed by switch, not by conversion.

## Two independent switches

Permission requests and gates are two different things and will
switched separately.

| What | Like | File modification |
|---|---|---|
| Permission requests (shell, web, ...) | `--auto` on startup | none |
| Gates / Human-in-the-Loop | `"hitl": false` in `.agent/runtime.json` | a file |

### `--auto`

Approves all permission requests that are not specifically set to `deny`
stand. **The 'deny' rules remain in effect** — folder ownership remains
i.e. technically enforced, even when running unsupervised.

### `.agent/runtime.json` (new)
```
json
{
  "hitl": true,
  "max_step_retries": 2,
  "continue_on_open_points": true
}
`
```
hitl` controls the gates. `max_step_retries` limits how many times a step can be taken
a blocker is restarted before being declared “with open points
"completed" is evaluated. `continue_on_open_points` determines whether the
Pipeline continues to run or breaks if there are open points.

**Why a separate file and no key in `opencode.jsonc`:** that
Config schema has `additionalProperties: false`. A separate top-level key
(`"aisdlc": { … }`) breaks schema validation. A file in
Framework area can also be switched without restarting.

## What changes

Seven digits. The switch logic comes **once** in `AGENTS.md` (which reads
every agent first), the remaining positions each receive a conditional notice,
so that no two documents tell the same thing.

| # | File | Change |
|---|---|---|
| 1 | `.agent/runtime.json` | **new** — the switch |
| 2 | `AGENTS.md` | “Runtime switches” section; HARD GATES, Rule 5 (Delete) and the Agent Change Rule become conditional |
| 3 | `.agent/PHASES.md` | Gate table: column "On `hitl:false`" + section on behavior without humans |
| 4 | `.agent/workflows/01`–`09` | 1 line each in the 9 `## Gate`/`## HARD GATE` sections |
| 5 | `.agent/prompts/` (10 files) | 28 hits, of which 4 were real HARD-GATE formulations → conditional |
| 6 | `.opencode/agents/aisdlc-orchestrator.md` | Gate section conditional + escalation policy + `steps: 300` |
| 7 | `.opencode/agents/aisdlc-requirements.md`, `aisdlc-qa.md` | report on `hitl:false` instead of stopping |

### Behavior with `hitl: false`

- **No stop.** The gate is not waited for.
- **No `question` tool.** The orchestrator doesn't ask - it checks
  Gate condition itself, writes the result to the summary and
  continues with step *n+1*.
- **The test is recorded.** The result ends up in
  `.agent/artifacts/` and in the commit body of the step, so that the run
  remains traceable, even without approval.
- **The agent change happens automatically.** The orchestrator starts it
  Role agents of the next step as subagents — this is the one
  intended way, not a workaround.

## Escalation without HITL — the part that otherwise gets stuck

Without people, a blocker has to find another way out, otherwise it stops
pipeline. Proposed rule:

| Signal | Reaction | border |
|---|---|---|
| `REQUIREMENT_GAP` | Restart step 1, enter the gap as an order | `max_step_retries` |
| `ARCHITECTURAL_BLOCKER` | Step 2 restart | `max_step_retries` |
| Subagent without commit | 1× restart | 1 |
| Gate condition not met | Record findings, continue | — |

After the attempts have been exhausted, the step is considered **with open points
completed**: the commit is created with a 'Note: open points' note
in the body, the pipeline continues to run. The run never ends at a blocker — him
ends at the last step, with a list of open points.

## Boundary conditions — empirically tested, not assumed

| observation | Episode ||---|---|
| `--auto` approves `shell: …` → `ask` | no shell requests in the test run |
| `--auto` **does not** approve the `question` tool | the question is aborted — **not fatal**: the agent makes an assumption and moves on. Therefore, with `hitl:false` the orchestrator doesn't even have to ask. |
| `deny` rules remain active under `--auto` | Folder ownership remains enforced |
| `opencode.jsonc` has `additionalProperties: false` | the switch must be a file |
| No role agent uses `question` | only the orchestrator needs to be adjusted |
| `steps` has no default | 300 as insurance still makes sense |
| The global `rtk` plugin rewrites commands before the permission check | Shell patterns must be tolerant (`*git log*`, not `git log *`) |
| `git commit -- <paths>` aborts if paths are empty or missing | the commit scope uses `--pathspec-from-file` (see `.agent/PHASES.md`) |

## Already done

**`.agent/knowledge/` as write target** (`38d16f1`). The folder ownership pointed
the area to the Architecture Orchestrator, the Agent Frontmatter does not. The
Agent bypassed the lock and added the DB schema
`docs/architecture/schema.sql` (29.7 KB), while
`.agent/knowledge/schema.sql` contained the generic 2.6KB template — this
Drift pattern that has already struck with the artifacts. Now
The rights matrix and folder ownership match.

The schema will be moved to its canonical location once the running
Architecture session has finished its work.

> **Addendum (Phase 2 is over):** The scheme is now in both locations —
> `.agent/knowledge/schema.sql` (canonical) and `docs/architecture/schema.sql`
> (versioned copy in the commit scope of the architecture phase). That's one
> **conscious** decision (ADR-019 §6, finding O-13/S-26), secured by the
> Mirror test `SchemaMirrorIT` (T-SCHEMA-MIRROR-01). The mirror rule is now in place
> in `AGENTS.md` and in the skill `aisdlc-openapi-first`. See also the addendum in
> `.agent/knowledge/stack.md`.

## Test run

Not in the real project: there is a session running and the test would be in the middle
end up in step 2. Instead, a copy.
```
bash
git clone /home/torsten/dev/my-projects/AISDLC /tmp/opencode/aisdlc-autotest
cd /tmp/opencode/aisdlc-autotest
# .agent/runtime.json: "hitl": false
opencode run --standalone --auto --agent aisdlc-orchestrator \
  --model opencode-go/<modell> \
  "Starte die AI-SDLC-Pipeline vollstaendig von Schritt 0 bis Schritt 9. Keine Rueckfragen."
```
**Expectation:** 9 step commits with subject `<type>(sdlc-<n>): …`, none
Demand, abort only if there is a real error.

**Exams after:**
```
bash
git log --oneline --grep="sdlc-"   # 9 Commits, lückenlos 1..9
git status --short                # leer
git diff HEAD~9 --stat            # nur Artefakte, kein Fremdzeug
```
## Return to HITL

`"hitl": true` in `.agent/runtime.json` - then exactly the same applies today
Behavior: Gates hold, `question` tool, human switches agent
by hand. Omit `--auto` and the permission requests will come back.

The mode leaves no trace in the framework: the gates remain in place
Files stand still, only their trigger is switched.