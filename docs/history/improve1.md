# improve1 — what needs to be improved

> **Status: implemented in AISDLC3.** This document was the specification; the
> Implementation is in the commit `feat(sdlc): A-H from improve1.md` in AISDLC3.
> The references below are from **AISDLC2** (EchoBox run) — that's where it is
> Defect occurred and that is where it is measured.
>
> | Section | Measure | Implemented in |
> |---|---|---|
> | 3 | A — Chain as a gate condition | `PHASES.md` (GATE 2), `AGENTS.md` (HARD GATES), Workflows 08/09 |
> | 3 | B — Matrix static, check dynamic | `PHASES.md` (traceability), `aisdlc-traceability-check` |
> | 3 | C — Check becomes the fill tool | `.agent/tools/traceability_check.py --fill` |
> | 3 | D — Commit rule learns the follow-up | `PHASES.md` (commit per step), `AGENTS.md` (commit convention) |
> | 3 | E — Stable AC identifiers | `aisdlc-prd-to-stories`, `aisdlc-test-matrix`, Workflow 01 |
> | 9 | F — Logging Convention | `AGENTS.md` (Rule 13), `.agent/artifacts/logging.md`, Workflow 06, `aisdlc-security-scan` |
> | 9 | G — Run Log | `AGENTS.md` (Rule 14), `PHASES.md`, `aisdlc-orchestrator`, `.agent/artifacts/run-log.jsonl` |
> | 9 | H — Tabulate Large Artifacts | `AGENTS.md` (rule 15), `aisdlc-grill-stress`, `aisdlc-security-scan` |
>
> **Part 1** — Traceability: the `sdlc-1` commits at the end of the history. Basis:
> the analysis of the PromptGuard run in AISDLC (October 20-21, 2026) and the confirmation in
> EchoBox run in AISDLC2 (October 21-22, 2026).
> **Part 2** — Logging and run log: the pipeline does not have a machine-readable one
> trace of what it is doing and the framework does not write anything to the generated application
> Logging before.

---

## 1. The findings

The AISDLC run ends up with **three `sdlc-1` commits**, all after steps 6 and 7:
```
ea9c4fc  18:37  sdlc-1  Akzeptanzkriterien mit eingefrorenem Vertrag abgeglichen
60d891c  18:24  sdlc-1  Traceability-Matrix gegen Endstand nachgezogen
835900d  17:15  sdlc-1  Traceability-Kette Anforderung→Story→Test→Code vervollständigt
```
The agents documented the trigger themselves
(`.agent/artifacts/traceability-fill.md`):

> **Step:** 1 (Return) · **Agent:** `aisdlc-requirements`
> **Status:** HEAD `c227e1c` (Step 7 completed)
> **Trigger:** The traceability check reported 89 gaps: 89 for **all**
> Matrix entries `tests` **and** `code` were empty.

The state of the matrix, measured:

| Stand | Requirements with test reference | with code reference |
|---|---|---|
| before `835900d` | **0 / 89** | **0 / 89** |
| to `835900d` | 89 / 89 | 89 / 89 |

**The Traceability Matrix was a framework throughout the entire run.** It was first
filled when tests and code existed — three steps too late.

### 1.2 The second run reproduces the same pattern

The EchoBox run in AISDLC2 (October 21-22, 2026) shows the same flow:

| time | Commit | step |
|---|---|---|
| 10/21 5:39 p.m. | `6538d9e` docs(sdlc-1): PRD, user stories and traceability matrix | 1 — regular |
| 10/21 6:55 p.m. | `cc67445` docs(sdlc-1): PRD and traceability updated to the final status | **Follow-up 1** |
| 10/21 10:20 p.m. | `test(sdlc-5)` Test strategy and failing tests | 5 |
| 10/22 01:41 | `feat(sdlc-6)` implementation of the stories | 6 |
| 10/22 02:18 | `refactor(sdlc-7)` Refactoring and clean-up | 7 |
| 10/22 02:28 | `825656a` docs(sdlc-1): Traceability matrix updated to the implemented status | **Follow-up 2** |

The second follow-up is **ten minutes after step 7** — exactly at the point where
Tests and code exist. Same cause, same effect.

**And this is the rule from D (“at most one follow-up per step”) in the EchoBox run
injured twice** — both times in step 1. This confirms the diagnosis: the cause
lies in the process, not in the matrix. A rule against the symptom would have nothing
changed.

## 2. The four causes

### 2.1 The matrix is a cross-step artifact that the commit scope cannot resolve

`docs/requirements/traceability_matrix.json` has `tests` and `code` fields. The
can **first complete step 5/6** — this is where the tests and code are created. The file
but is in the writing range of `aisdlc-requirements`; the `aisdlc-execution` is allowed
don't write there.

Result: If step 5/6 sees the gap, the requirements agent must be **reloaded**
become → an additional `sdlc-1` commit. Structurally unavoidable.

### 2.2 Gate 1 does not check the chain
```
GATE 1 | 2 (Architektur) | PRD vollständig, Architektur und Entscheidungen ok
```
Not a word about traceability. Gate 1 could pass even though **0 of 89**
requirements had a test. If the chain had been gate condition, the gap would be
Noticed **before** steps 5/6 — and the follow-up would have been part of step 1.

### 2.3 The commit rule does not recognize the return

“Exactly one commit per step” — step 1 has three. Formally against the rules, but the rule
has no case for it.

### 2.4 The check came in the middle of the run

The `aisdlc-traceability-check` was installed on October 21st 15:00, the PromptGuard run
started on October 20th 6:26 p.m. The check reacted **correctly** (jump back, gap closed)
— only 15 hours late and via a return jump instead of a gate.

**In AISDLC2 the check is there from the beginning.** The gap must therefore be *structural*
eliminated, not just detected earlier.

## 3. What to do

### A — The chain becomes the checked gate condition

**Problem:** The gap is only noticeable once steps 5/6 have been completed - too late for that
a gate.

**Change:**

| File | Change |
|---|---|
| `.agent/PHASES.md` | Gate definition: GATE 2 gets the traceability condition. See wording below. |
| `AGENTS.md` | “HARD GATES” section: same condition, plus the note that the check runs **before** the gate. |
| `.agent/workflows/08-security.md` | Add the test condition to `## Gate` |
| `.agent/workflows/09-deploy.md` | ditto (GATE 2 is in front of it) |
| `.opencode/skills/aisdlc-traceability-check/SKILL.md` | “Record result” section: the check is a **gate requirement**, not just a hint |

**Condition text (GATE 2):**

> Traceability proven: a test case exists for every requirement with a story **and**
> a code reference; the check reports 0 gaps. Story without acceptance criteria,
> Acceptance criterion without test case or test case without code reference blocks the gate.

**Important:** The test is carried out **mechanically** (tool), not by eye. A
Gate that people “judge” is not a gate.

### B — The matrix becomes static, the check becomes dynamic

**Problem:** `tests` and `code` in the matrix are values that only step 5/6 knows.
Filling them forces you to go back to step 1.

**Change: the matrix only describes the static chain.**

| field | today | future |
|---|---|---|
| `requirement_id` | yes | yes |
| `story_ids` | yes | yes |
| `acceptance_criteria` | yes | yes |
| `features` | yes | yes |
| `tests` | **empty until step 6** | **not applicable** |
| `code` | **empty until step 6** | **not applicable** |
| `coverage_state` | manually maintained | **not applicable** — results from the check |

**The static chain** (Requirement → Story → Feature) is complete in step 1
known and committed there. **The Dynamic Chain** (Requirement → Test → Code)
is a property of the file system and is determined from it **at any time**.

**Implemented in:**

| File | Change |
|---|---|
| `.opencode/skills/aisdlc-traceability-check/SKILL.md` | Describe static/dynamic separationen; Tests 6 and 7 (matrix without test/code) **removed**, instead: **test without matrix reference** and **code without test** are the new gaps |
| `.agent/tools/traceability_check.py` | Tests 6/7 revolve around: not "does the matrix have a test?" but "is there a test that points to no requirement?" and "is there any code that doesn't belong to any test?" — plus: **Output of the matrix** (see C) |
| `.agent/workflows/01-requirements.md` | Output list: the matrix now only contains Requirement → Story → Feature |
| `.agent/workflows/05-tdd.md` | The check runs here and creates the receipt (see C) |
| `.agent/workflows/08-security.md` | ditto, result is gate requirement |

### C — The check becomes a fill tool

**Problem:** The check **reports** gaps. Someone has to close it by hand — im
PromptGuard run the requirements agent, three steps too late.

**Change:** `traceability_check.py` gets a `--fill` mode that the matrix
**mechanically** fills from the file system:
```
python3 .agent/tools/traceability_check.py --fill
```
**Basic rule:** The tool **never writes a path that it cannot occupy.**
For each test case it is checked whether it contains a story or requirements reference in the test
carries (tag, file name or comment). Without a receipt, the field remains empty and the
Entry ends up in the gap list. **An invented path is worse than one
Gap** — it looks busy and it's not.

**Goal:** The tool delivers a **proven result** instead of a list of gaps:

| File | Content |
|---|---|
| `docs/requirements/traceability_matrix.json` | static chain (requirement → story → feature) + `tests`/`code`, **mechanically filled** |
| `.agent/artifacts/traceability.md` | the receipt: requirement → test → code, with test note |
| `.agent/artifacts/traceability-fill.md` | what was filled, what was not verifiable and why |

**Write scope:** The tool writes `docs/requirements/traceability_matrix.json`
— that depends on the `aisdlc-requirements`. Two ways:

- **C1 (recommended):** The filling is an **explicit order to the
  `aisdlc-requirements`** as part of step 5: the execution agent reports the
  open references, the requirements agent runs the tool and commits
  the result. One order, one commit, no return.
- **C2:** The `aisdlc-execution` gets an exception for exactly this one file.
  Breaks folder ownership — only for a tool that creates the matrix.

**Open:** C1 or C2. C1 keeps the separation clean, but costs an additional one
Agent change. C2 is one step shorter and needs an exception in the rights matrix.

### D — The commit rule learns the follow-up

**Problem:** "Exactly one commit per step" does not recognize the case that a later
Step the artifacts of a previous one changed.

**Change** in `.agent/PHASES.md` and `AGENTS.md`:

> Exactly one commit per step. **Exception:** a step that contains the artifacts of a
> corrected from the previous step (e.g. the traceability matrix after the tests).
> an additional commit with the subject of the **corrected** step and the
> Addition `(subsequent migration)`. The follow-up is named in the commit body: from which step, from
> what occasion.

Example subject: `docs(sdlc-1): Traceability matrix added (added from step 6)`

**Rule:** a maximum of **one** draw per step. A second follow-up is a
Sign that the cause lies in the process, not in the matrix.

### E — The contract gets the traceability references

**Problem:** The test cases did not know the acceptance criteria of the features
(`ea9c4fc` had to add AC tags to them afterwards).

**Change:** When creating the features in step 1, each gets
Acceptance criterion is a **stable identifier** that returns later in the test:
```
# features/US-003-token-lifecycle.feature
  Szenario: Token wird beim Refresh rotiert  # AC-003-1
```
And the test case has the same identifier:
```
java
@Test void ac003_1_tokenWirdBeimRefreshRotiert() { … }
```
**Result:** The check can **mechanically** assign test ↔ acceptance criterion, without
Guess. The assignment via file names remains as a fallback.

**Change** in `.opencode/skills/aisdlc-prd-to-stories/SKILL.md` (schema of stories
and features), `.opencode/skills/aisdlc-test-matrix/SKILL.md` (naming convention of the
test cases) and `.agent/workflows/01-requirements.md`.

## 4. Order of implementation

| # | step | Depending on |
|---|---|---|
| 1 | **E** — AC identifiers in features and test cases | none |
| 2 | **B** — Make matrix static, rotate checks 6/7 | 1 |
| 3 | **C** — `--fill` in tool, receipt artifacts | 2 |
| 4 | **A** — Chain as gate condition | 3 |
| 5 | **D** — Follow-up rule in the commit convention | 4 |

E before B because the mechanical assignment does not work without stable AC identifiers.
C before A because a gate can only test something that it can also create.

## 5. What can be measured

| Key figure | today (PromptGuard) | Target |
|---|---|---|
| Requirements without test reference | 89 of 89 | 0 |
| `sdlc-1` commits | 3 | 1 (plus a maximum of 1 follow-up) |
| Time of first detected gap | after step 7 | after step 5 |
| Gate checks that people do by eye | 2 of 2 | 0 of 2 |

## 6. To decide

| Question | Options | Recommendation |
|---|---|---|
| Who writes the filled matrix? | C1 (Requirements Agent as Contractor) · C2 (Execution with Exception) | **C1** — the folder ownership remains clean |
| Where is the receipt? | only in the matrix · additionally as an artifact | **both** — the matrix is ​​the working format, the artifact of the gate template |
| Are `coverage_state` and `manual_criteria` left? | delete · keep as derivation | **derive** — they are hand curated twin data |
| Does the follow-up rule only apply to traceability? | formulate generally · only here | **general** — the case is not matrix specific |

## 7. Not the target of this improvement

- **No** change to step order. The 9 steps remain.
- **No** additional role. The requirements agent remains the owner of
  `docs/requirements/`.
- **Not** a replacement for `aisdlc-traceability-check`. The check remains, it just becomes
  more complete and can fill.
- **No** backwards compatibility with the PromptGuard release. That's one
  completed run.

---

## Appendix 1: Document locations

### A1 — PromptGuard Run (AISDLC)

| receipt | Location |
|---|---|
| Trigger of the return | `.agent/artifacts/traceability-fill.md`, head |
| Matrix before relocation | Commit `835900d^` — 0/89 with `tests`, 0/89 with `code` |
| Matrix after migration | Commit `835900d` — 89/89 with `tests`, 89/89 with `code` |
| AC tags subsequently | Commit `ea9c4fc` — 35 feature files, +6 lines each |
| Matrix Recheck | Commit `60d891c` — 61 lines, SEC-009 occupied |
| Step 7 as a stand | Commit `c227e1c` |
| Check was from then on | `aisdlc-traceability-check` installed in `0d1ee9a` (21.10 15:00) |

### A2 — EchoBox Run (AISDLC2)

| receipt |Location |
|---|---|
| Subsequent migration 1 (to the decided status) | Commit `cc67445`, Oct 21 18:55 |
| Follow-up 2 (to the implemented status) | Commit `825656a`, Oct 22 02:28 — 10 min after `refactor(sdlc-7)` |
| Step 7 as a prerequisite | Commit `825656a^` = `refactor(sdlc-7)` |
| Not a JSON artifact | `.agent/artifacts/` — 23 files, 0 of which are `.json` |
| Logging defect as a chance discovery | `.agent/artifacts/security-audit.md`, Finding **S-36** |
| 30+ Machine Readable Errors | `docs/qa/bugs/BUG-*.json` |
| Operational runbook from the deploy skill | `docs/execution/runbook.md` — Health/Readiness, SLI/SLO, Alerting, Rollback |


---

# Part 2 — Logging and Run Log

> Second improvement area, from the EchoBox run. This isn't about them
> application, but about **visibility**: what the pipeline does — and what the operation does
> logged.

## 8. The findings

### 8.1 Logging: in the framework a test, not a concept

The framework mentions logging in **three places** — all as a *check*, none as a *default*:

| position | What it says there |
|---|---|
| `aisdlc-security-scan` §9 “Logging and data protection” | no passwords, tokens, complete personal data in logs; Rating level “Logging incomplete” → Medium |
| `aisdlc-deploy` | Smoke criterion: “Logs show no unexpected errors” |
| `stack/java/backend` (stack agent, **not** framework) | "Structured logging with correlation ID; no secrets or PII in logs" |

**What's missing:**

- **No logging artifact.** No step produces one — the 23 artifacts of the
  EchoBox runs do not include one.
- **No rule in `AGENTS.md`/`PHASES.md`** what the generated application must log:
  Format, correlation ID, level, what is *not* allowed in, retention.
- **No gate condition** for logging.
- `coverage/` is the only runtime evidence convention — and that's a **number**,
  no log.

**Proof that the vulnerability is real:** The security scan found **S-36** — one didn't
supported method produces **500 with stack trace in operation log**, evidenced by
a probe across 15 method/path combinations. A pure logging defect, found
**only** because the security check is looking for him. Without this test he would be
slipped through.

What there is in the EchoBox run (from `aisdlc-deploy`): `docs/execution/runbook.md`
with health/readiness, SLIs/SLOs, alerting, rollback, purge, secret rotation. That's the one
**Application** — originated from the deploy skill, not from a logging rule.

### 8.2 The pipeline has no run log

The entire process lies only in the Git history and prose artifacts. There is
**not** machine-readable list: which step ran, which test passed, which one
Gate was reached, how long, how many bounces.

Specifically: `.agent/artifacts/ruecksprung-balance.md` is **102 KB prose** (845 lines,
including 352 table rows) via **Return 6** from step 2. Valuable knowledge — but
cannot be evaluated, cannot be counted, cannot be compared.

There are **zero** JSON artifacts in the projecte at `.agent/artifacts/`. The only ones
Machine-readable files are the bug JSONs (`docs/qa/bugs/BUG-*.json`, 30+) and the
Traceability matrix. **The errors of the operation are data — the flow of the operation
not.**

### 8.3 Two additional costs

| | in EchoBox Run |
|---|---|
| Total artifacts | 23 files, 1.5 MB |
| of which prose without machine-readable form | 21 files |
| Running time in which one step followed the other | not measurable (can only be reconstructed from the commit timestamps) |
| Jumps | 6 in step 2 alone, 2 of them in step 1 |

## 9. What to do

### Q — A logging convention into the framework

**Problem:** What gets logged is decided by the stack agent — or no one. A
Logging defects like S-36 are only found when the security check is looking for them.

**Change:**

| File | Change |
|---|---|
| `AGENTS.md` | New rule: the created application logs **structured** - one event per line with correlation ID, event type, client, result, duration. **Never** payloads, secrets, tokens, complete personal data. |
| `.agent/PHASES.md` | New artifact in step 6 list: `.agent/artifacts/logging.md` — Log schema, prohibited content, retention, correlation ID flow |
| `.agent/workflows/06-implementation.md` | Add `logging.md` to output list; the feature slice skill points to |
| `.opencode/skills/aisdlc-security-scan/SKILL.md` | §9 from "test" to "tested convention": checks **against** `.agent/artifacts/logging.md`, not against an invented expectation |
| `.opencode/skills/aisdlc-containerize/SKILL.md` | Health check and log output belong together - the container writes in a format that the health check reads

**Result:** S-36 would be a **systematic class** (logging convention violated),
not a chance find. And the convention is written once instead of in every stack
reinvented.

### G — A run log as data

**Problem:** The flow can only be reconstructed from prose and commit timestamps.
Without data, there can be no measurement — and the final improvements came from feeling
decided, not from history.

**Change:** a file `.agent/run-log.jsonl` — **one line per event**, append-only:
```
json
{"ts":"2026-10-22T01:41:03Z","event":"step_start","step":6,"agent":"aisdlc-execution"}
{"ts":"2026-10-22T02:18:41Z","event":"step_end","step":6,"commit":"825656a","duration_s":2258}
{"ts":"2026-10-22T02:28:11Z","event":"jump_back","from":7,"to":1,"reason":"Traceability-Matrix unvollstaendig"}
{"ts":"2026-10-22T02:29:02Z","event":"gate","gate":1,"result":"approved","by":"human"}
{"ts":"2026-10-22T02:30:00Z","event":"check","name":"traceability_check","result":"fail","gaps":178}
```
**Who writes:** the orchestrator (`aisdlc-orchestrator`). He is the only one
Step boundaries and gates sees. A step agent knows his step, but not that one
Process.

**Change:**

| File | Change |
|---|---|
| `.opencode/agents/aisdlc-orchestrator.md` | Mandatory: write a line after `.agent/run-log.jsonl` for each step start/end, gate and test result. The artifact belongs in every step commit (`.agent/artifacts/` is in scope everywhere). |
| `.agent/artifacts/` | `run-log.jsonl` as its **own** artifact, not in `.agent/` — it is an artifact of the run, not part of the framework |
| `AGENTS.md` | Rule: “Each step begins and ends with a line in the run log” |

**Result:** Idle, bounce cycles and step durations become measurable. And those
Next round of improvements will be decided **by data**, not by feeling.

### H — Tabulate large artifacts

**Problem:** `ruecksprung-balance.md` (102 KB prose) can be found, but not
evaluable. Who “how many relapses were there and which findings remained open”
asks, needs to read 102 KB.

**Change:** Every artifact over ~20 KB gets **a table at the top**:
Findings, severity, status, where resolved. The derivation remains below. The table is
the introduction, the derivation of the evidence.

**Change** in `AGENTS.md` (rule "Output") and in the skills that have large reports
generate (`aisdlc-grill-stress`, `aisdlc-security-scan`).

## 10. Sequence of implementation (part 2)

| # | step | Depending on |
|---|---|---|
| 1 | **G** — Run Log | none — provides the data used to evaluate F and H |
| 2 | **F** — Logging Convention | G (the operation shows where logs are missing) |
| 3 | **H** — Tabulate Artifacts | G (the table shows which artifacts are affected) |

**G first:** Without data, the order of F and H is guessed. With the running protocol
It is possible to measure which artifacts are the most expensive and where logging is missing.

## 11. What can be measured

| Key figure | today | Target |
|---|---|---|
| Machine-readable artifacts | 3 of 23 | 23 of 23 |
| Step durations measurable | no (commit timestamp only) | yes, from the run log |
| Returns countable | no (only from prose) | yes, from the run log |
| Logging specifications in the framework | 0 | 1 (rule + artifact) |
| Logging defects as a systematic class | 0 (S-36 by chance discovery) | 1 (Test against the Convention) |

## 12. To decide

| Question | Options | Recommendation |
|---|---|---|
| Where is the running log? | `.agent/run-log.jsonl` · `.agent/artifacts/run-log.jsonl` | **`.agent/artifacts/`** — it is an artifact of the run, not part of the framework. Otherwise it would be present when copying the framework and would cause confusion in foreign runs. |
| Does the orchestrator write or a tool? | Orchestrator · Tool with Hook | **Orchestrator** — he sees step boundaries and gates; a tool would have to be built into every step |
| How long does the log last? | one run · 30 days · unlimited | **a run** (in the run artifact) — the history is in Git | anyway
| Will the logging convention be part of the framework or the stack? | Framework · Stack | **Framework** — it is stack independent (correlation ID, no payload). The stack only provides the *implementation* (logback, JSON format). |