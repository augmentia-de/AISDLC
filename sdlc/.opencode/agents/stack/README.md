# Stack agents — this is where the stack-specific roles end up

This folder is empty in the **Framework**. The stack-specific agents come with it
the **stack layer** of the project (see `opencode.jsonc` in the project root) and
belong **not** to the framework.

## Convention
```
.opencode/agents/stack/<tech>/<rolle>.md
```
The path determines the **ID** of the agent: `stack/java/backend` → ID
`stack/java/backend`. The folder name is part of the ID — unlike Skills, where
Grouping folders are no longer required.

## Requirements for a stack agent

| field | Value |
|---|---|
| `fashion` | `all` — selectable as primary by humans **and** startable as subagent by orchestrator. With `mode: primary` the start as a subagent fails. |
| `description` | says **when** to choose the agent |
| `permissions` | `edit` to write permissions of the stack; **no** own `shell` catch-all (that controls the global allowlist - `last match wins`) |
| `steps` | Safety net, not done criterion (150 is the value for role agents) |

## Release

The agent must be released in the **stack layer** otherwise it is for that
Model invisible:
```
jsonc
{ "action": "subagent", "resource": "stack/java/backend", "effect": "allow" }
```
The stack layer first blocks ("resource": "*"` → deny) and then allows
targeted — the allow rules of the role agents are further back in the
Framework layer and win.