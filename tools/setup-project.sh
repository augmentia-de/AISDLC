#!/usr/bin/env bash
# AISDLC3 Projekt-Setup
# Erstellt einen neuen Projektordner mit Framework (aus sdlc/) und gewählten Stack-Skills
#
# Usage: ./tools/setup-project.sh <zielordner> <stack1> [stack2] [stack3] ...
# Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations
#
# Beispiel: ./tools/setup-project.sh /tmp/mein-projekt java quarkus jpa-sql

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
SDLC="$ROOT/sdlc"
STACK="$ROOT/stack"
TARGET="$1"
shift

if [ -z "$TARGET" ]; then
  echo "Usage: $0 <zielordner> <stack1> [stack2] ..."
  echo "Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations"
  exit 1
fi

echo "Erstelle Projekt in: $TARGET"
mkdir -p "$TARGET"

# Kopiere Framework aus sdlc/
echo "Kopiere Framework aus sdlc/..."
cp -r "$SDLC/.agent" "$TARGET/"
cp -r "$SDLC/.opencode" "$TARGET/"
cp "$SDLC/AGENTS.md" "$TARGET/"
cp "$SDLC/opencode.jsonc" "$TARGET/"

# Verzeichnisse anlegen
mkdir -p "$TARGET/src" "$TARGET/tests" "$TARGET/docs/requirements" "$TARGET/docs/architecture" "$TARGET/docs/api" "$TARGET/docs/adr" "$TARGET/docs/qa" "$TARGET/docs/execution" "$TARGET/.agent/artifacts" "$TARGET/stack"

# Stack-Skills kopieren
for s in "$@"; do
  if [ -d "$STACK/$s/skills" ]; then
    echo "Kopiere Stack: $s"
    mkdir -p "$TARGET/.opencode/skills"
    cp -r "$STACK/$s/skills/"* "$TARGET/.opencode/skills/" 2>/dev/null || true
    # Auch in stack/ kopieren für Referenz
    cp -r "$STACK/$s" "$TARGET/stack/" 2>/dev/null || true
  else
    echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
  fi
done

echo ""
echo "Fertig!"
echo "Projekt: $TARGET"
echo "Stacks: $*"
echo ""
echo "Starte opencode wie folgt:"
echo "  cd $TARGET"
echo "  opencode"
