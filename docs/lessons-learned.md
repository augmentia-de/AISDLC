# Lessons Learned — AISDLC Run 1 (PromptGuard)

> Evaluation of the first complete AISDLC run, October 20-21, 2026.
> Basis: OpenCode log (43 MB, October 17th - 21st), session database
> (`opencode.db`: `session_v2`, `session_message`, `part`, `permission`),
> Git history (25 commits) and the created artifact set.
> Not an ideal image, but what actually happened.

## 1. The numbers

| | |
|---|---|
| Total running time | **54.6 h** (as of October 21st 12:00, 32 sessions) |
| Of which productive commit intervals | ~10 h |
| Total input tokens | **23,306,743** |
| Total output tokens | **1,100,415** |
| Ratio input: output | **21 : 1** |
| Model turns, hardest step | 49 (Step 2, Architecture) |
| Message load, heaviest session | 315 KB (38 Assistant messages) |
| Handover payload to the orchestrator | 37,367 characters ≈ 9,341 tokens |
| Compactions | 7 (2 in the pipeline window) |
| Rate Limit Events | 12 (every October 17th - 19th, **before** the pipeline) |
| Generated Artifact Set | Contract 5,523 lines/199 KB · Schema 1,414 lines · 20 ADRs · 11 architecture documents · 35 stories · 35 Gherkin features · 168 Java files · 41 test files |

**Step sequence:** 1 → 2 → 3 → 4 → ⟨2⟩ → 5 → ⟨2⟩ → 6 → ⟨2⟩ → 6.
Three jumps back to step 2, three contract revisions (1.0 → 1.1 → 1.1.1).

*The run had not ended at the time of evaluation - the numbers are still growing.
A side finding of the evaluation: the running agent has `.agent/knowledge/schema.sql`
and `docs/architecture/schema.sql` touched **at the same time** — the mirror rule
(L12) is lived, not just documented.*

## 2. The findings

### L1 — The rights matrix hit the workflow file

**Symptom.** `.agent/artifacts/` was not written; the artifacts were buried
`docs/requirements/artifacts/`.

**Cause.** 23 framework files (9 workflows, 10 prompts, `PHASES.md`, `AGENTS.md`,
`README.md`, `how-to.md` - 85 references) name `.agent/artifacts/` as the target. The
Agent Frontmatter prohibits exactly this path. The folder ownership table in
`AGENTS.md` didn't recognize the folder at all.

**Rule.** Every goal that a workflow names needs three things: Write permission in the
Agent-Frontmatter, a row in the Folder Ownership table and the same path in
`PHASES.md`. If one is missing, the agent writes in the place he is allowed to - and the place
is not the one meant.

**Fixed** in `38d16f1` and `687fe5d`.

### L2 — The agent bypasses blocks instead of reporting them

**Symptom.** The DB schema was in `docs/architecture/schema.sql`, not in
`.agent/knowledge/schema.sql`. After that, `domain-model.md:47` documented the **wrong**
Location as “canonical” — 32 files with conflicting references.

**Cause.** The Architecture agent did not have write permission to `.agent/knowledge/`
(see L1) and wrote to the next best permitted location. The bypass was local
correct and globally incorrect.

**Rule.** A lock that is not visible at the location where it applies will
bypassed. Locks need eThere is no visible justification **in the target folder itself**
only in the framework.

### L3 — The `rtk` plugin breaks any Git workflow that relies on parsed output

**Symptom.** "the first commit attempt failed" — the step 2 commit was not created.

**Cause.** `rtk` rewrites **each part of a pipeline individually**. Off
`git diff --cached --name-only` becomes `rtk git diff --cached --name-only`, and that hangs
a "Changes:" block with blank lines. As a path specification this leads to
`fatal: empty string is not a valid pathspec` — the commit does not happen. Independent
Of these, `git commit -- <paths>` aborts as soon as a scope path does not contain any tracked files
has (empty destination folder).

**Rule.** No shell workflow may rely on parsed command output as long as a
Rewrite plugin is active. The scope should be set via the **index**:
`mkdir -p` → `git reset -q` → `git add -A -- <scope>` → `git commit`. No pipe,
no path specification list.

**Fixed** in `6e69c4c`, verified in end-to-end testing.

### L4 — `last-match-wins`: an agent rule can override the entire global allowlist

**Symptom.** `git status`, `git diff` and `git log` asked despite global allowlist.

**Cause.** Agent rules are appended **last**. The `shell: *` → `ask` in
Frontmatter of each role agent overwrote any global allows that preceded it.

**Rule.** A rule that is global does not belong in the agent front matter.
Frontmatter rules are additions, not repetitions — they always win otherwise.

### L5 — `mode: primary` prevents subagent usage

**Symptom.** "Agent aisdlc-architecture cannot run as a subagent".

**Rule.** Role agents that an orchestrator should start need `mode: all`
(primary **and** subagent).

### L6 — The nesting depth is default 1 — and the model finds a detour

**Symptom.** "Subagent depth limit reached". In the test, the model started the grandson
**himself**, rather than letting the role agent start it — a silent one
Role softening.

**Rule.** Set `experimental.subagent_depth` explicitly. And: a barrier that only
Saying “no” provokes a detour. Every depth barrier needs a permitted one
Alternative - otherwise the model solves it on its own.

### L7 — The delivery payload was 9,341 tokens

**Measurement.** The final message of the Architecture session: 37,367 characters. None
Summary, but a reasoning dump. With nine steps, that would be ~84,000 tokens alone
of returns in the Orchestrator context — the documented failure (“Agent forgets
Context from previous step") would have occurred.

**Rule.** The return is a **pointer**, never Content: three routing facts
(`STATUS` / `COMMIT` / `OPEN POINTS`), plus optional artifact paths and
Test result. The next agent reads the files itself; the orchestrator is enough
Paths continue, never content.

**Measured after the change: 895 characters ≈ 223 tokens — factor 42.**

Side finding: “just done” is already the built-in normal casel. Has a subagent
no final text, OpenCode returns `Subagent completed without a text response.`
You don't have to force it — you just have to prevent a dump from happening instead
is delivered.

### L8 — The problem is the session, not the return

**Measurement.** Input tokens across steps: 173k → 632k → 2.02M → 5.47M → **14.08M** —
80× over four steps. Ratio input:output = 21:1. Seven Compactions, of these
two in the pipeline window.

**Cause.** Each step starts a fresh session but takes up the **entire**
Stock with (315 KB message load at step 2). The agents read 21 times more than they do
writing — that’s the real burden.

**Rule.** Step-specific subcontexts instead of full session. The contract, that
The schema and the artifacts are versioned — this creates an entry point
build instead of dragging along the whole history. **Not yet implemented.**

### L9 — Jumps are the most expensive item

**Measurement.** Step sequence 1 → 2 → 3 → 4 → ⟨2⟩ → 5 → ⟨2⟩ → 6 → ⟨2⟩ → 6. Three
Contract revisions. There were **7 h 11 min** between step 5 and step 6. This
Artifact set counts 25 stress test findings (S-1…S-26), 44 open points (O-01…O-44)
and 17 assignments (A-1…A-17).

**Rule.** Each contract revision invalidates schema, code, and tests simultaneously. Gate 1
is therefore the cheapest insurance in the entire pipeline - and was
most time-consuming point of the run. The contract comes **before** the implementation
stable, not after.

### L10 — Time is in waiting time, not in computing time

**Measurement.** Three sessions with 12-15 h wall clock at 440-520 output tokens/min
(≈ 8.7 tokens/s). That's not computing, that's waiting for unanswered ones
Permission prompts or an idle session.

**Rule.** Evaluate runtime separately according to waiting time and calculation time. A session with
a lot of wall clock and little output is **not** a computing problem, but a
Waiting problem — usually an unanswered prompt.

### L11 — An "interrupted" without a retry path is a dead end

**Measurement.** 8 of the 12 longer sessions end with `interrupted`.

**Rule.** Every escalation needs a retry path **with budget**
(`REQUIREMENT_GAP` → Step 1 new, maximum N attempts, then as an open point
continue). Without a retry budget, the pipeline dies at the first blocker.

### L12 — Every file in two locations needs four things

**Observed.**

| Double shelf | Status |
|---|---|
| `.agent/knowledge/openapi.yaml` (2KB template) ↔ `docs/api/openapi.yaml` (199KB contract) | **real error** — three workflows read the template |
| `.agent/knowledge/stack.md` (template) ↔ ADR-001 (decision) | **real mistake** — the template was called FastAPI, decided was Spring Boot |
| `.agent/knowledge/schema.sql` ↔ `docs/architecture/schema.sql` | **conscious** (ADR-019 §6, O-13/S-26), enforced via mirror test |

**Rule.** Each double file requires: (a) a named owner,
(b) a canonical vs. copy rule, (c) a test or script that
EqCalibration enforces (d) a reference in the framework. The only thing missing from the scheme was (d) — the
OpenAPI was missing (b), (c) and (d) at the same time.

### L13 — What has been formally decided can still be wrong

**Observed.** F2 (client model) was recorded as decided. This
Risk-Register has the same scope conflict under B-01: “central register” ↔
“Departments work in departmental workspaces” — with the result “data model and
API rework in phase 2".

**Rule.** A gate not only checks “is there a decision in it”, but “is it
Ensechid *correct*”. A contradiction between the decision list and the risk register
is a gate finding, not a formal error.

### L14 — The test run beat the theory — three times

| expectation | Finding |
|---|---|
| The orchestrator needs a fixed return | The model placed the three lines at the **end**; Only the additional rule “three lines first” brought determinism. |
| The Architecture Agent does not disclose the commit error | He **reported** it ("the first commit attempt failed") and investigated the cause — that's why it stood out. A silent detour would have covered up the bug. |
| The first Orchestrator run fails due to subagent startup | He **blocked correctly** instead of working with a generalist. This was exactly the desired behavior — folder ownership was not bypassed. |

**Rule.** These are agents who report a blocker instead of bypassing it
most important feature of the entire system. Any design decision that has a blocker
*technically* prevented (rights, paths, formats), is worth more than any prose-based
Rule.

## 3. What is still open

| # | Open | Risk |
|---|---|---|
| 1 | Input tokens per step (L8) | The context continues to grow; the 21:1 read load is the root |
| 2 | 44 open points from step 6 | Become jumps back (L9) | in later steps
| 3 | 107 uncommitted files from an `interrupted` run | Next commit scope can collect foreign |
| 4 | 12 commits not pushed | The remote is on step 2 |
| 5 | `git reset -q` unstaged someone else's work | Doesn't delete it, but it changes the staging state of the human |

## 4. The rules that follow from it

1. **A workflow name is a commitment.** It needs write permission, folder ownership line
   and SSoT entry — otherwise the agent writes somewhere else.
2. **Blocks need a visible justification at the destination.** Otherwise they will
   bypassed, and the detour becomes documentation.
3. **No shell workflow on parsed output.** With active rewrite plugin: Scope over
   set the index, not via a path specification.
4. **Do not repeat global rules into front matter.** Agent rules win
   (`last match wins`).
5. **`mode: all` for each agent that an orchestrator should start.** And
   Set `experimental.subagent_depth` explicitly — with allowed alternative.
6. **The return is a pointer.** Three routing facts, nothing else. The next one
   Agent reads the files itself.
7. **The Agent decides when he is finished; the framework checks it.**
   `steps` is the safety net, not the criterion.
8. **Each escalation has a retry budget.** Otherwise the pipeline dies on the first one
   Blockers.
9. **The contract belongs before implementation.** Gate 1 is the cheapest insurance
   the pipeline.
10. **Evaluate runtime separately after waiting and computing.** A lot of wall clock with little
    Output is a prompt problem, not a model problem.
11. **Each double file needs owner, rule, test and framework reference.**
12. **Reporting blockers is the core feature.** Technically prevented detours are worth more
    than any prose-based rule.

## 5. Reproduction
```
bash
# Laufzeit je Session, inkl. Output-Rate
python3 - <<'PY'
import sqlite3, os, time
db = os.path.expanduser("~/.local/share/opencode/opencode.db")
c = sqlite3.connect(f"file:{db}?mode=ro", uri=True); c.row_factory = sqlite3.Row
for r in c.execute("SELECT agent, title, time_created, time_updated, tokens_output "
                   "FROM session_v2 WHERE directory LIKE '%AISDLC%' ORDER BY time_created"):
    d = dict(r); dur = round((d['time_updated']-d['time_created'])/60000) or 1
    print(f"{d['agent']:22s} {dur:5d} min  {round((d['tokens_output'] or 0)/dur):5d} T/min  {d['title'][:40]}")
PY

# Payload-Groesse einer Subagent-Session
opencode api session.message.list --param sessionID=<id> \
  | python3 -c "import sys,json; m=json.load(sys.stdin)['data']; \
      t=[x for x in m if x.get('type')=='assistant'][-1]; \
      print(len(''.join(p.get('text','') for p in t.get('content',[]))))"

# Fehler im Log
grep -a "level=ERROR" ~/.local/share/opencode/log/opencode.log | sed 's/.*message="//' | cut -c1-90 | sort | uniq -c | sort -rn
```
Expected for the next run: **Input tokens per step** (L8) and the
**44 open points** (L9) — both decide whether step 7 jumps back.


---

# Part 2 — The evaluation of the log files (October 21, 2026)

## Tool balance

1,715 tool calls in the pipeline sessions:

| Tool | Views | Share |
|---|---|---|
| `shell` | 1,093 | 63.8% |
| `read` | 202 | 11.8% |
| `write` / `edit` | 149 / 136 | 16.6% |
| `bash` (classic) | 51 | 3.0% |
| `glob` / `grep` | 19/11 | 1.7% |
| `subagent` | 21 | 1.2% |

The 1,094 `shell` calls by purpose:

| Purpose | Views | Share |
|---|---|---|
| navigate/output (`cd`, `echo`, `pwd`) | 591 | 54.0% |
| **Python script** | **426** | **38.9%** |
| Read file (`cat`/`wc`/`diff`) | 55 | 5.0% |
| search/list | 13 | 1.2% |
| git/Build/Test | 4/1 | 0.5% |

## Why Python scripts instead of file tools?

**The cause is a tool deficiency, not a model error.** `read` shows a file,
`grep` finds — but “which request IDs are missing from the stories?” over 70 files
is a **quantity comparison**. There is no tool for this, so the agent closes it
Gap with a script: 1 model turn instead of 70. Ecologically correct.

The 426 scripts are exactly two patterns:

| Pattern | Purpose | Rating |
|---|---|---|
| `python3 …/generate_artifacts.py` | Generator: Stories, Features, Traceability Matrix from the PRD | correct — but as **inline reuse** rather than as a versioned tool |
| `python3 - <<'PY' …` | Consistency checks: ID comparison PRD ↔ Stories ↔ Features ↔ Matrix | correct — but **reinvented each time** instead of as a skill |

**Can it be avoided immediately?** Differentiated:

- The **calculation** scripts: no. They are correct and efficient.
- The **Generator**: yes, by a place and a convention — it was in
  `docs/requirements/tools/`, i.e. in the middle of the specification that a human reads. There is no such generator in the framework itself — the tools convention (`.agent/tools/README.md`) prevents this from happening again.
  Now in `.agent/tools/`.
- The **consistency check**: yes, as a skill. Now `aisdlc-traceability-check` with that
  Tool `.agent/tools/traceability_check.py`.
- The **591 output commands**: yes, via prompt. They each cost one model turn and
  a permission check and do not provide any results.

**Side finding that only became visible during the check:** the traceability matrix leads
**0 of 89 requirements** with a test (`requirements_with_test: 0`), although 41
Test files and 35 Gherkin features exist. The chain requirement → test → code
was never proven - just claimed. 178 gaps, now machine checkable.

## Why is the subagent still hanging?

**The finding: three independent sessions stand still for ~6 hours at the same time.**

| gap | session | window | After that |
|---|---|---|---|
| 436 mins | architecture | 10/21 00:42 → 07:59 | `shell(completed)` |
| 345 mins | execution | 10/21 00:34 → 06:19 | `shell(completed)` |
| 333 mins | Orchestrator | 10/21 00:44 → 06:17 | `question(error)` |

This can't be a model problem — three sessions can't be on the same thing at the same time
Hang model. It is the night, and the night belongs to man.

**The cause was in the permission table:** 29 saved individual permissions, one
each command prefix (`awk *`, `cat *`, `cp *`, `echo *`, `find *`, `git status *`,
`python3 *`, `rtk *`, `sed *`, `sort *`, …). Any command outside of these prefixes
opened a permission query — and **an open query freezes the session**,
until the person answers. The first commit of the day was 07:54.

**The breakdown:** 45% of the runtime are idle gaps > 60 s. The absolute number
overcounted because the sessions ran in parallel - the ratio is correct.

**The lever:** `shell: *` → `ask` as a basic rule is for an unattended run
the killer. The protection of the pipeline is not in the shell anyway, but in the
**Path rights** (`edit` deny/allow per agent) — they remain untouched.

## Why so many rounds?

Three levels that have to be separated.

**(a) Model quality — yes, but limited.** The entire pipeline ran up
`minimax-m3.1-flash-free`, a **free** flash model. Evidence: 3 jumps,
44 open points, 25 stress findings, 11 tool errors in the architecture session. This
But the result is objectively substantial — 5,523-line contract, 1,414-line schema,
20 ADRs, 168 Java files. “Cheap model produces waste” is not true; “cheap
Model already produces more rework.

**(b) The contract was frozen too late — structurally.** The stress test (Step 4)
found 25 findings, of which **13 were on the contract** (S-5, S-6, S-7, S-12, S-13, S-17, S-18 ...) —
and the contract was frozen after step 2. Each revision invalidates schema, code
and tests. That's not model-related, that's the order.

**(c) The gate is a human circle.** There are 25 findings and 44 open points at the gate
and 3 contract revisions. These are human rounds, not model rounds.

**The number of rounds:** one jump back = 1 model round (fix contract) + 1 human round
(Gate) + 1 model round (tighten code). Three jumps → 9 extra laps. Plus the
Idle rounds from the permission matrix.

---

# Part 3 — The Seven Improvements

| P | Measure | Finding | Benefit | Status |
|---|---|---|---|---|
| **P1** | `shell: *` → `allow`, exceptions `ask`/`deny` | 45% idle time due to frozen sessions | ends hanging | **implemented** |
| **P2** | Model selectable per step | everything ran on a free Flash model | fewer bounces | **documented** (precedence: call > agent > session) |
| **P3** | Consistency check as a skill | 426 inline scripts, 178 undetected holes | versioned, reusable | **implemented** |
| **P4** | Tool Convention | Generator was located in `docs/requirements/tools/` | does not belong in the specification | **implemented** |
| **P5** | No output commands | 591 of 1,094 shell views (54%) | ~590 turnss over 9 steps | **implemented** |
| **P6** | Freeze contract after step 4 | 13 contract defects after the freeze | the return loop takes the cause | **implemented** |
| **P7** | Profile between the steps | Input grew 80× over four steps | makes the entry point explicit | **implemented** |

## What P1 to P7 specifically changed

| File | Change |
|---|---|
| `.opencode/opencode.jsonc` | `shell: *` → `allow`; Exceptions: `git push`, `git reset --hard`, `git clean`, `rm -rf` → `ask`; `sudo`, `chmod 777` → `deny` |
| `AGENTS.md` | Rules 11 (no output commands) and 12 (briefing first); freezing point after step 4; Briefing section |
| `.agent/PHASES.md` | Gate definition with freezing point; Step 5 with `aisdlc-traceability-check`; Sections “Profile” and “Tools” |
| 9 Workflows | `## Rules` with issuance ban and contract freezing (from step 5); Briefing in the prerequisites |
| 10 prompts | Profile in the entries; `## RULES FOR THIS STEP` |
| `.opencode/agents/aisdlc-orchestrator.md` | Section “3b — Write a profile”; Issue ban |
| 4 role agents | “Read first” begins with the profile |
| `.opencode/skills/aisdlc-openapi-first/SKILL.md` | freezing point after step 4; Section “Comparison with Traceability” |
| `.opencode/skills/aisdlc-traceability-check/SKILL.md` | **new** — the consistency check as a procedural step |
| `.agent/tools/traceability_check.py` | **new** — checks Requirement → Story → Feature → Test → Code |
| *(project specific)* | a project-specific generator belongs in `src/<modul>/tools/`, not in the framework |
| `.agent/tools/README.md` | **new** — the tool convention |
| `.agent/artifacts/briefing.md` | **new** — the profile as a living example |
| `how-to.md` | “Select model” section (P2) |

## What P1-P7 do not solve

- **The input tokens per step** remain high. The profile (P7) sets that
  entry point, but an agent that needs 70 files continues to read them.
- **The 44 open points** from step 6 are unchanged and will be added to step 7
  either closed or to recesses.
- **The quality of the model** remains as long as P2 is not implemented — the
  The documented path to get there is now clear.