#!/usr/bin/env bash
# AISDLC3 Projekt-Setup
# Erstellt ein neues Projekt: Framework + Projekt in einem Verzeichnis (flat)
#
# Usage: ./tools/setup-project.sh <name> <stack1> [stack2] [stack3] ...
# Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations
#
# Beispiel: ./tools/setup-project.sh try1 java quarkus jpa-sql
# Ergebnis: try1/ (alles in einem Verzeichnis, Git-Repo)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
SDLC="$ROOT/sdlc"
STACK="$ROOT/stack"
NAME="$1"
shift

if [ -z "$NAME" ]; then
  echo "Usage: $0 <name> <stack1> [stack2] ..."
  echo "Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations"
  exit 1
fi

TARGET="$NAME"

echo "Erstelle Projekt in: $TARGET"
mkdir -p "$TARGET"

# Kopiere Framework aus sdlc/ (flat - alles in TARGET)
echo "Kopiere Framework aus sdlc/..."
cp -r "$SDLC/.agent" "$TARGET/"
cp -r "$SDLC/.opencode" "$TARGET/"
cp "$SDLC/AGENTS.md" "$TARGET/"
cp "$SDLC/opencode.jsonc" "$TARGET/"

# Verzeichnisse anlegen
mkdir -p "$TARGET/src" "$TARGET/tests" "$TARGET/docs/requirements" "$TARGET/docs/architecture" "$TARGET/docs/api" "$TARGET/docs/adr" "$TARGET/docs/qa" "$TARGET/docs/execution"

# Stack-Skills kopieren
for s in "$@"; do
  if [ -d "$STACK/$s/skills" ]; then
    echo "Kopiere Stack: $s"
    mkdir -p "$TARGET/.opencode/skills"
    cp -r "$STACK/$s/skills/"* "$TARGET/.opencode/skills/" 2>/dev/null || true
  else
    echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
  fi
done

# Git-Repository initialisieren
echo "Initialisiere Git-Repository..."
cd "$TARGET"
git init -q 2>/dev/null || true
git config user.email "aisdlc@local" 2>/dev/null || true
git config user.name "AISDLC" 2>/dev/null || true
git add -A 2>/dev/null || true
git commit -q -m "chore: initial project setup" 2>/dev/null || true
cd - > /dev/null

echo ""
echo "Fertig!"
echo "Projekt: $TARGET"
echo "Stacks: $*"
echo ""
echo "Starte opencode wie folgt:"
echo "  cd $TARGET"
echo "  opencode"
