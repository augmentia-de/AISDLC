#!/usr/bin/env bash
# AISDLC3 Projekt-Setup
# Erstellt oder initialisiert ein Projekt: Framework + (optional) Stack-Skills.
#
# Usage:
#   ./tools/setup-project.sh <name> <stack1> [stack2] ... [--mode greenfield|migration] [--legacy <dir>]
#
# Modi:
#   greenfield (Default): neues Projekt-Verzeichnis <name> anlegen.
#   migration: bestehendes Verzeichnis (Legacy-Repo) initialisieren; --legacy <dir>
#              legt das Framework flach in <dir> ab. <name> wird ignoriert.
#
# Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
SDLC="$ROOT/sdlc"
STACK="$ROOT/stack"

MODE="greenfield"
LEGACY=""
NAME=""
STACKS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --legacy) LEGACY="$2"; shift 2 ;;
    *) if [ -z "$NAME" ]; then NAME="$1"; else STACKS+=("$1"); fi; shift ;;
  esac
done

if [ -n "$LEGACY" ]; then
  TARGET="$LEGACY"
else
  TARGET="$NAME"
fi

if [ -z "$TARGET" ]; then
  echo "Usage: $0 <name> <stack1> [stack2] ... [--mode greenfield|migration] [--legacy <dir>]"
  exit 1
fi

if [ "$MODE" = "migration" ] && [ -n "$LEGACY" ]; then
  if [ ! -d "$TARGET" ]; then
    echo "Legacy-Verzeichnis nicht gefunden: $TARGET"
    exit 1
  fi
fi

echo "Erstelle/Initialisiere Projekt in: $TARGET (mode=$MODE)"
mkdir -p "$TARGET"

# Kopiere Framework aus sdlc/ (flat - alles in TARGET)
echo "Kopiere Framework aus sdlc/..."
cp -r "$SDLC/.agent" "$TARGET/"
cp -r "$SDLC/.opencode" "$TARGET/"
cp "$SDLC/AGENTS.md" "$TARGET/"
cp "$SDLC/opencode.jsonc" "$TARGET/"

# Verzeichnisse anlegen
mkdir -p "$TARGET/src" "$TARGET/tests" "$TARGET/docs/requirements" "$TARGET/docs/architecture" \
  "$TARGET/docs/api" "$TARGET/docs/adr" "$TARGET/docs/qa" "$TARGET/docs/execution"

if [ "$MODE" = "migration" ]; then
  mkdir -p "$TARGET/docs/migration"
fi

# Stack-Skills kopieren + Stack-Ordner als Referenz
mkdir -p "$TARGET/stack"
for s in "${STACKS[@]}"; do
  if [ -d "$STACK/$s/skills" ]; then
    echo "Kopiere Stack: $s"
    mkdir -p "$TARGET/.opencode/skills"
    cp -r "$STACK/$s/skills/"* "$TARGET/.opencode/skills/" 2>/dev/null || true
  fi
  if [ -d "$STACK/$s" ]; then
    echo "Kopiere Stack-Referenz: $s"
    cp -r "$STACK/$s" "$TARGET/stack/" 2>/dev/null || true
  else
    echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
  fi
done
cp "$STACK/SKILLS_OVERVIEW.md" "$TARGET/stack/" 2>/dev/null || true

# config.json mode setzen
python3 - "$TARGET/.agent/config.json" "$MODE" <<'PY'
import json, sys
p, mode = sys.argv[1], sys.argv[2]
try:
    d = json.load(open(p))
except Exception:
    d = {}
d["mode"] = mode
if mode == "migration":
    d.setdefault("migration", {})
    d["migration"].setdefault("strategy", "strangler-fig")
    d["migration"].setdefault("cutover_approval_required", True)
    d["migration"].setdefault("dual_write", False)
    d["migration"].setdefault("parallel_run_required", True)
json.dump(d, open(p, "w"), indent=2)
PY

# Git-Repository initialisieren (nur wenn noch keins)
cd "$TARGET"
if [ ! -d .git ]; then
  echo "Initialisiere Git-Repository..."
  git init -q 2>/dev/null || true
  git config user.email "aisdlc@local" 2>/dev/null || true
  git config user.name "AISDLC" 2>/dev/null || true
fi

# state.json sicherstellen
if [ ! -f ".agent/state.json" ]; then
  PROFILE=$(python3 -c "import json; print(json.load(open('.agent/config.json')).get('profile','enterprise'))" 2>/dev/null || echo "enterprise")
  cat > .agent/state.json << JSON
{
  "\$schema": "./schemas/state.schema.json",
  "run_id": null,
  "profile": "$PROFILE",
  "current_step": 0,
  "status": "not_started",
  "agent": null,
  "last_commit": null,
  "started_at": null,
  "updated_at": null,
  "history": [],
  "gates": {"gate1": {"status": "pending"}, "gate2": {"status": "pending"}}
}
JSON
fi

git add -A 2>/dev/null || true
git commit -q -m "chore: initial project setup" 2>/dev/null || true
cd - > /dev/null

echo ""
echo "Fertig!"
echo "Projekt: $TARGET"
echo "Modus: $MODE"
echo "Stacks: ${STACKS[*]}"
echo ""
echo "Starte opencode wie folgt:"
echo "  cd $TARGET"
echo "  opencode"