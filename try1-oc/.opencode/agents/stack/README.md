# Stack-Agenten — hier landen die stack-spezifischen Rollen

Dieser Ordner ist im **Framework** leer. Die stack-spezifischen Agenten kommen mit
der **Stack-Schicht** des Projekts (siehe `opencode.jsonc` im Projekt-Root) und
gehören **nicht** zum Framework.

## Konvention

```
.opencode/agents/stack/<tech>/<rolle>.md
```

Der Pfad bestimmt die **ID** des Agenten: `stack/java/backend` → ID
`stack/java/backend`. Der Ordnername ist Teil der ID — anders als bei Skills, wo
Gruppierungsordner entfallen.

## Anforderungen an einen Stack-Agenten

| Feld | Wert |
|---|---|
| `mode` | `all` — als Primary vom Menschen wählbar **und** als Subagent vom Orchestrator startbar. Mit `mode: primary` scheitert der Start als Subagent. |
| `description` | sagt, **wann** der Agent zu wählen ist |
| `permissions` | `edit` auf die Schreibrechte des Stacks; **kein** eigener `shell`-Catch-all (der stuert die globale Allowlist aus — `last match wins`) |
| `steps` | Sicherheitsnetz, nicht Fertig-Kriterium (150 ist der Wert für Rollen-Agenten) |

## Freigabe

Der Agent muss in der **Stack-Schicht** freigegeben werden, sonst ist er für das
Modell unsichtbar:

```jsonc
{ "action": "subagent", "resource": "stack/java/backend", "effect": "allow" }
```

Die Stack-Schicht sperrt zuerst (`"resource": "*"` → deny) und erlaubt danach
gezielt — die Allow-Regeln der Rollen-Agenten stehen weiter hinten in der
Framework-Schicht und gewinnen.
