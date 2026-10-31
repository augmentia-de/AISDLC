---
name: aisdlc-glossary-adr
description: Phase 2: Write technical glossary and ADRs.
metadata:
  origin: own framework procedure; Craft from ECC/architecture-decision-records
---

## When

- **Phase 2**, for every decision that has consequences.
- Every time a term is introduced, narrowed down or renamed.
- After a grill-me that clarified a decision.

## Two artifacts

**GLOSSARY.md** — the technical language so that code, documentation and tests use the same words
use. Each term: what it **is**, what it **isn't**, where it comes from.

**ADRs** — why the decision was made so that no later agent would repeat the decision
roll up.

## Glossary

By term:
```
markdown
## <Begriff>

<Definition in einem Satz>

- **Auspraegung**: <was genau dazugehoert>
- **Nicht**: <was ihm aehnlich ist, aber etwas anderes ist>
- **Quelle**: <Anforderung oder ADR>
```
Rules: small and precise; one term per entry; **always** the negation
carry along; refer to the source; Delete terms that are neither in the code nor in
meet the requirements.

## ADR
```
markdown
# ADR-<ID>: <Titel>

## Kontext
<die Lage, ohne die Entscheidung>
## Entscheidung
<was entschieden wurde>
## Konsequenzen
<was das nach sich zieht - auch das Unangenehme>
## Alternativen
<die verworfenen Wege, mit dem Grund>
## Status
Angenommen | Vorgeschlagen | Ersetzt durch ADR-<ID>
```
Rules: record the **decision**, not the discussion; always the one
name rejected alternatives; **never** overwrite an accepted ADR —
but replace it with a new one.

## Output

- `docs/architecture/GLOSSARY.md`
- `docs/adr/ADR-<ID>-<title>.md`
- `.agent/artifacts/adr-index.md` — one line per ADR for the gate template

## Rules

- One decision per ADR.
- If an agent cannot find the reasons for a decision, the ADR is missing.
- Terms that appear in the code are in the glossary; otherwise this is a review finding.
- After writing, `aisdlc-traceability-check` applies to the mapping.

## Stack skills

> The names are those of the project's **stack layer** (`opencode.jsonc`,
> `skills/`). The workflow of this skill applies without them — they just deliver
> the craft, not the process.
| For what | Skill |
|---|---|
| ADR format, index, alternatives | `architecture-decision-records` |
| Differentiation between technical and technical terms | `hexagonal-architecture` |