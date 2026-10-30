#!/usr/bin/env bash
# AISDLC3 Framework-Sync
# Aktualisiert nur die Framework-Dateien in einem bestehenden Projekt.
# Projektartefakte (docs/, src/, tests/, .agent/artifacts/, state.json,
# .agent/knowledge/) bleiben unangetastet.
#
# Usage:
#   ./tools/sync-framework.sh <projekt-dir>
#
# Kopiert:
#   - .agent/workflows/, .agent/schemas/, .agent/templates/
#   - .agent/PHASES.md
#   - .agent/prompts/
#   - .agent/tools/ (traceability_check.py, skill_log.py)
#   - .opencode/agents/aisdlc-*.md
#   - .opencode/opencode.jsonc (nur Framework-Regeln)
#   - AGENTS.md

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
SDLC="$ROOT/sdlc"
TARGET="$1"

if [ -z "$TARGET" ] || [ ! -d "$TARGET" ]; then
  echo "Usage: $0 <projekt-dir>"
  echo "Projekt-Verzeichnis nicht gefunden: $TARGET"
  exit 1
fi

echo "Sync Framework nach: $TARGET"

# .agent Unterverzeichnisse
mkdir -p "$TARGET/.agent/workflows" "$TARGET/.agent/schemas" "$TARGET/.agent/templates" \
         "$TARGET/.agent/prompts/orchestrator" "$TARGET/.agent/prompts/multi-instance" \
         "$TARGET/.agent/tools"

cp -r "$SDLC/.agent/workflows/"* "$TARGET/.agent/workflows/"
cp -r "$SDLC/.agent/schemas/"* "$TARGET/.agent/schemas/"
cp -r "$SDLC/.agent/templates/"* "$TARGET/.agent/templates/"
cp "$SDLC/.agent/PHASES.md" "$TARGET/.agent/PHASES.md"
cp -r "$SDLC/.agent/prompts/orchestrator/"* "$TARGET/.agent/prompts/orchestrator/"
cp -r "$SDLC/.agent/prompts/multi-instance/"* "$TARGET/.agent/prompts/multi-instance/"
cp -r "$SDLC/.agent/tools/"* "$TARGET/.agent/tools/"

# .opencode Agents (nur aisdlc-*)
mkdir -p "$TARGET/.opencode/agents"
cp "$SDLC/.opencode/agents/aisdlc-"*.md "$TARGET/.opencode/agents/" 2>/dev/null || true
if [ -d "$SDLC/.opencode/agents/stack" ]; then
  mkdir -p "$TARGET/.opencode/agents/stack"
  cp -r "$SDLC/.opencode/agents/stack/"* "$TARGET/.opencode/agents/stack/" 2>/dev/null || true
fi

# Framework-Config (nicht root opencode.jsonc = Stack-Schicht)
if [ -f "$SDLC/.opencode/opencode.jsonc" ]; then
  cp "$SDLC/.opencode/opencode.jsonc" "$TARGET/.opencode/opencode.jsonc"
fi

# AGENTS.md
cp "$SDLC/AGENTS.md" "$TARGET/AGENTS.md"

echo ""
echo "Fertig. Projektartefakte wurden nicht angefasst."
echo "Prüfen: cd $TARGET && git diff --stat"