#!/usr/bin/env bash
# AISDLC3 Projekt-Setup
# Erstellt eine Struktur: <name>-oc/ (OpenCode-Umgebung) mit <name>/ (Projekt) darin
#
# Usage: ./tools/setup-project.sh <name> <stack1> [stack2] [stack3] ...
# Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations
#
# Beispiel: ./tools/setup-project.sh try1 java quarkus jpa-sql
# Ergebnis: try1-oc/ (OpenCode-Umgebung) mit try1/ (Projekt) darin

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

OC_DIR="${NAME}-oc"
PROJECT_DIR="$OC_DIR/$NAME"

echo "Erstelle OpenCode-Umgebung in: $OC_DIR"
echo "Projektverzeichnis: $PROJECT_DIR"
mkdir -p "$OC_DIR" "$PROJECT_DIR"

# Kopiere Framework aus sdlc/ in OC_DIR
echo "Kopiere Framework aus sdlc/..."
cp -r "$SDLC/.agent" "$OC_DIR/"
cp -r "$SDLC/.opencode" "$OC_DIR/"
cp "$SDLC/AGENTS.md" "$OC_DIR/"
cp "$SDLC/opencode.jsonc" "$OC_DIR/"

# Stack-Skills kopieren in OC_DIR/.opencode/skills/
for s in "$@"; do
  if [ -d "$STACK/$s/skills" ]; then
    echo "Kopiere Stack: $s"
    mkdir -p "$OC_DIR/.opencode/skills"
    cp -r "$STACK/$s/skills/"* "$OC_DIR/.opencode/skills/" 2>/dev/null || true
  else
    echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
  fi
done

# Projektverzeichnis anlegen (nur Code, keine Framework-Dateien)
mkdir -p "$PROJECT_DIR/src" "$PROJECT_DIR/tests" "$PROJECT_DIR/docs/requirements" "$PROJECT_DIR/docs/architecture" "$PROJECT_DIR/docs/api" "$PROJECT_DIR/docs/adr" "$PROJECT_DIR/docs/qa" "$PROJECT_DIR/docs/execution"

# Stack-Ordner als Referenz in OC_DIR
mkdir -p "$OC_DIR/stack"
for s in "$@"; do
  if [ -d "$STACK/$s" ]; then
    cp -r "$STACK/$s" "$OC_DIR/stack/" 2>/dev/null || true
  fi
done

echo ""
echo "Fertig!"
echo "OpenCode-Umgebung: $OC_DIR"
echo "Projekt: $PROJECT_DIR"
echo "Stacks: $*"
echo ""
echo "Starte opencode wie folgt:"
echo "  cd $OC_DIR"
echo "  opencode"
echo ""
echo "Hinweis: OpenCode liest hierarchisch. Starte in $OC_DIR, nicht in $PROJECT_DIR."
echo "Die Framework-Dateien liegen in $OC_DIR, das Projekt in $OC_DIR/$NAME."
