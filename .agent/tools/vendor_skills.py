#!/usr/bin/env python3
"""Vendoriert die Stack-Skills in das Projekt.

Warum: `skills.paths` mit absoluten Pfaden auf die Bibliotheken ist
maschinenabhaengig - auf einem anderen Rechner oder in einem anderen Klon
brechen die Pfade. Vendoriert liegt der Skill im Projekt selbst.

Das Skript ist reproduzierbar: gleiche Auswahl -> gleicher Inhalt. Es schreibt
die Herkunft in `skills/PROVENANCE.md` fest.

Aufruf:
    python3 .agent/tools/vendor_skills.py --libs <bibliotheks-wurzel> [--out skills]
    python3 .agent/tools/vendor_skills.py --libs <wurzel> --list

Die Auswahl steht in `SELECTION` — pro Skill der Schritt, fuer den er gedacht
ist. Das bestimmt, welche Skills der Orchestrator einem Agenten freigibt
(siehe `opencode.jsonc` des Projekts, `agents.<id>.permissions`).
"""

from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import shutil
import sys

# Skill -> Schritt ("*" = durchgehend). Reihenfolge = Auswahlreihenfolge.
SELECTION: dict[str, str] = {
    # Schritt 1 - Anforderungen
    "intent-driven-development": "1",
    "story-lifecycle": "1",
    # Schritt 2 - Architektur, Vertrag, Daten
    "api-design": "2",
    "api-design-principles": "2",
    "openapi-spec-generation": "2",
    "contract-first": "2",
    "database-migrations": "2",
    "postgresql-table-design": "2",
    "postgres-patterns": "2",
    "architecture-decision-records": "2",
    "hexagonal-architecture": "2",
    "security-requirement-extraction": "2",
    "stride-analysis-patterns": "2",
    "threat-mitigation-mapping": "2",
    # Schritt 4 - Stresstest
    "code-review-excellence": "4",
    # Schritt 5 - Test
    "springboot-tdd": "5",
    "e2e-testing": "5",
    "react-testing": "5",
    "javascript-testing-patterns": "5",
    "test-collaboration": "5",
    "verification-loop": "5",
    # Schritt 6 - Backend
    "springboot-patterns": "6",
    "springboot-security": "6",
    "java-coding-standards": "6",
    "jpa-patterns": "6",
    "error-handling": "6",
    "sql-optimization-patterns": "6",
    # Schritt 6 - Frontend
    "react-patterns": "6",
    "react-state-management": "6",
    "react-performance": "6",
    "nextjs-app-router-patterns": "6",
    "typescript-advanced-types": "6",
    "tailwind-design-system": "6",
    "frontend-a11y": "6",
    "frontend-patterns": "6",
    "modern-javascript-patterns": "6",
    # Schritt 6/9 - Infrastruktur und LLM
    "docker-patterns": "6",
    "langchain-architecture": "6",
    "prompt-engineering-patterns": "6",
    "llm-evaluation": "6",
    # Schritt 9 - Deployment
    "deployment-patterns": "9",
    "github-actions-templates": "9",
    "incident-runbook-templates": "9",
    "slo-implementation": "9",
    "canary-watch": "9",
    "prometheus-configuration": "9",
    "kubernetes-patterns": "9",
    # Durchgehend
    "security-review": "*",
    "secrets-management": "*",
    "conventional-commit-review": "*",
    "git-workflow": "*",
}

# Schritt -> zustaendiger Rollen-Agent. "*" = durchgehend, alle Agenten.
STEP_AGENT = {
    "1": "aisdlc-requirements",
    "2": "aisdlc-architecture",
    "3": "aisdlc-architecture",
    "4": "aisdlc-architecture",
    "5": "aisdlc-execution",
    "6": "aisdlc-execution",
    "7": "aisdlc-execution",
    "8": "aisdlc-qa",
    "9": "aisdlc-qa",
}
ALL_AGENTS = ["aisdlc-requirements", "aisdlc-architecture", "aisdlc-execution", "aisdlc-qa"]
JAVA_AGENTS = ["aisdlc-execution", "aisdlc-qa", "stack/java/backend"]
REACT_AGENTS = ["aisdlc-execution", "aisdlc-qa", "stack/react/frontend"]

# Bibliotheks-Unterordner, die durchsucht werden (relativ zur Wurzel).
LIB_DIRS = [
    "ECC/skills",
    "wshobson-agents/plugins/backend-development/skills",
    "wshobson-agents/plugins/frontend-mobile-development/skills",
    "wshobson-agents/plugins/javascript-typescript/skills",
    "wshobson-agents/plugins/api-scaffolding/skills",
    "wshobson-agents/plugins/database-design/skills",
    "wshobson-agents/plugins/security-scanning/skills",
    "wshobson-agents/plugins/observability-monitoring/skills",
    "wshobson-agents/plugins/cicd-automation/skills",
    "wshobson-agents/plugins/documentation-generation/skills",
    "wshobson-agents/plugins/incident-response/skills",
    "wshobson-agents/plugins/llm-application-dev/skills",
    "wshobson-agents/plugins/developer-essentials/skills",
]

# Cap auf die SKILL.md-Groesse: nur die landet im Kontext des Modells.
# Die Begleitdateien werden nur angelesen, wenn der Skill sie anleitet.
MAX_SKILL_BYTES = 16_000


def find_skills(root: pathlib.Path) -> dict[str, list[tuple[str, pathlib.Path]]]:
    found: dict[str, list[tuple[str, pathlib.Path]]] = {}
    for rel in LIB_DIRS:
        base = root / rel
        if not base.is_dir():
            continue
        for sk in sorted(base.rglob("SKILL.md")):
            found.setdefault(sk.parent.name, []).append((rel, sk))
    return found


def dir_size(p: pathlib.Path) -> int:
    return sum(f.stat().st_size for f in p.rglob("*") if f.is_file())


def config_block(rows) -> str:
    """Erzeugt den Permission-Block der Stack-Schicht.

    Die Sichtbarkeit wird pro Agent geschnitten: der Architecture-Agent sieht
    die Vertrags- und Datenbank-Skills, der Execution-Agent die
    Implementierungs-Skills. Das haelt die Beschreibungskosten pro Schritt
    niedrig - im Referenzlauf waren 12 von 32 sichtbaren Skills falscher Stack.
    """
    by_agent: dict[str, list[str]] = {}
    for name, step, *_ in rows:
        agents = ALL_AGENTS if step == "*" else [STEP_AGENT.get(step, "aisdlc-execution")]
        for a in agents:
            by_agent.setdefault(a, []).append(name)
        low = name.lower()
        if any(k in low for k in ("spring", "java", "jpa", "sql-", "postgres", "database-", "migration", "hexagonal", "contract", "api-", "openapi", "security-requirement", "stride", "threat", "llm", "langchain", "prompt-", "docker", "kubernetes")):
            by_agent.setdefault("stack/java/backend", []).append(name)
        if any(k in low for k in ("react", "nextjs", "typescript", "tailwind", "frontend", "modern-javascript", "javascript-testing", "e2e")):
            by_agent.setdefault("stack/react/frontend", []).append(name)
    out = ['',
           '  "permissions": [',
           '    // Skills: erst sperren, dann freigeben. Der Framework-Teil',
           '    // (aisdlc-*) bleibt ueber .opencode/opencode.jsonc sichtbar, weil',
           '    // dessen Allow-Regel weiter hinten steht.',
           '    { "action": "skill", "resource": "*", "effect": "deny" },',
           '    { "action": "skill", "resource": "aisdlc-*", "effect": "allow" },',
           '',
           '    // Subagenten: erst sperren, dann die Stack-Agenten freigeben.',
           '    { "action": "subagent", "resource": "*", "effect": "deny" },',
           '    { "action": "subagent", "resource": "stack/java/backend", "effect": "allow" },',
           '    { "action": "subagent", "resource": "stack/react/frontend", "effect": "allow" }',
           '  ],',
           '',
           '  // Sichtbarkeit pro Agent: der Architecture-Agent sieht die',
           '  // Vertrags- und Datenbank-Skills, der Execution-Agent die',
           '  // Implementierungs-Skills. Erzeugt von .agent/tools/vendor_skills.py.',
           '  "agents": {']
    for a in sorted(by_agent):
        if a not in ALL_AGENTS and not a.startswith("stack/"):
            continue
        out.append(f'    "{a}": {{')
        out.append('      "permissions": [')
        for i, s in enumerate(sorted(set(by_agent[a]))):
            comma = "," if i < len(set(by_agent[a])) - 1 else ""
            out.append(f'        {{ "action": "skill", "resource": "{s}", "effect": "allow" }}{comma}')
        out.append('      ]')
        out.append('    },')
    out[-1] = out[-1].rstrip(",")
    out.append("  }")
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description="Stack-Skills vendoriieren")
    ap.add_argument("--libs", required=True, help="Wurzel der Bibliotheksklone")
    ap.add_argument("--out", default="skills", help="Zielverzeichnis im Projekt")
    ap.add_argument("--list", action="store_true", help="nur anzeigen, nicht kopieren")
    ap.add_argument("--config", action="store_true", help="Permission-Block der Stack-Schicht ausgeben")
    args = ap.parse_args()

    root = pathlib.Path(args.libs).resolve()
    if not root.is_dir():
        print(f"FEHLT: {root}", file=sys.stderr)
        return 2

    found = find_skills(root)
    out = pathlib.Path(args.out)
    rows, skipped, missing = [], [], []

    for name, step in SELECTION.items():
        cands = found.get(name)
        if not cands:
            missing.append(name)
            continue
        # Bei Namenskollision gewinnt die erste Bibliothek in LIB_DIRS-Reihenfolge.
        rel, src = cands[0]
        size = dir_size(src.parent)
        skill_bytes = src.stat().st_size
        if skill_bytes > MAX_SKILL_BYTES:
            skipped.append((name, skill_bytes, rel))
            continue
        desc = ""
        m = re.search(r'^description:\s*["\']?(.*?)["\']?\s*$', (src).read_text(encoding="utf-8", errors="ignore"), re.M)
        if m:
            desc = m.group(1)[:90]
        rows.append((name, step, rel, size, skill_bytes, desc, len(cands)))

    print(f"Auswahl: {len(rows)} Skills")
    if missing:
        print(f"  NICHT GEFUNDEN: {', '.join(missing)}")
    if skipped:
        print("  UEBERSPRUNGEN (zu gross):")
        for n, s, r in skipped:
            print(f"    {n} ({s//1024} KB) aus {r}")
    print(f"  Nutzlast: {sum(r[3] for r in rows)//1024} KB | SKILL.md gesamt: {sum(r[4] for r in rows)//1024} KB")

    if args.config:
        print(config_block(rows))
        return 0

    if args.list:
        print()
        for name, step, rel, size, sb, desc, nc in rows:
            print(f"  {step:>2}  {name:36s} dir={size//1024:3d} KB skill={sb//1024:3d} KB  {desc}")
        return 0

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    prov = ["# Provenienz der vendorierten Skills", "",
            "Spalte **KB** = Groesse der `SKILL.md` (das, was in den Kontext geladen wird).", "",
            "Erzeugt von `.agent/tools/vendor_skills.py`. Jeder Skill stammt aus einer der",
            "Agent-Bibliotheken; der Inhalt ist unverändert. Die Spalte **Schritt** sagt,",
            "für welchen SDLC-Schritt der Skill gedacht ist — sie bestimmt, welche Skills",
            "der Orchestrator einem Agenten freigibt.", "",
            "| Skill | Schritt | Quelle | KB | Herkunft |", "|---|---|---|---:|---|"]
    for name, step, rel, size, sb, desc, nc in rows:
        prov.append(f"| `{name}` | {step} | `{rel}` | {sb//1024} | {'1 Quelle' if nc==1 else f'{nc} Quellen, erste gewaehlt'} |")
    (out / "PROVENANCE.md").write_text("\n".join(prov) + "\n", encoding="utf-8")

    for name, step, rel, size, sb, desc, nc in rows:
        src = found[name][0][1].parent
        shutil.copytree(src, out / name, ignore=shutil.ignore_patterns(".git", "__pycache__"))

    print(f"\n  {len(rows)} Skills nach {out}/ kopiert")
    print(f"  Herkunft: {out}/PROVENANCE.md")
    block = config_block(rows)
    (out / "STACK_CONFIG.jsonc.txt").write_text(block + "\n", encoding="utf-8")
    print(f"  Allowlist: {out}/STACK_CONFIG.jsonc.txt (in die Stack-Schicht einkopieren)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
