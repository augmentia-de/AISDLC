#!/usr/bin/env bash
# AISDLC3 Projekt-Update
# Aktualisiert Skills in einem bestehenden Projekt
#
# Usage:
#   ./tools/update-project.sh <projekt-dir> add <stack1> [stack2] ...
#   ./tools/update-project.sh <projekt-dir> remove <stack1> [stack2] ...
#   ./tools/update-project.sh <projekt-dir> list
#   ./tools/update-project.sh <projekt-dir> reinit <stack1> [stack2] ...

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."
STACK="$ROOT/stack"
TARGET="$1"
ACTION="$2"
shift 2

if [ -z "$TARGET" ] || [ -z "$ACTION" ]; then
  echo "Usage:"
  echo "  $0 <projekt-dir> add <stack1> [stack2] ..."
  echo "  $0 <projekt-dir> remove <stack1> [stack2] ..."
  echo "  $0 <projekt-dir> list"
  echo "  $0 <projekt-dir> reinit <stack1> [stack2] ..."
  echo ""
  echo "Stacks: java spring quarkus kafka jpa-sql security architecture technologies analysis regulations"
  exit 1
fi

if [ ! -d "$TARGET" ]; then
  echo "Projekt-Verzeichnis nicht gefunden: $TARGET"
  exit 1
fi

SKILLS_DIR="$TARGET/.opencode/skills"
mkdir -p "$SKILLS_DIR"

case "$ACTION" in
  list)
    echo "Installierte Skills in $SKILLS_DIR:"
    echo ""
    ls -1 "$SKILLS_DIR" 2>/dev/null | grep -v "^aisdlc-" | wc -l | xargs echo "Anzahl (ohne aisdlc-*):"
    echo ""
    ls -1 "$SKILLS_DIR" 2>/dev/null | grep -v "^aisdlc-" | head -40
    ;;
  add)
    for s in "$@"; do
      if [ -d "$STACK/$s/skills" ]; then
        echo "Füge Stack hinzu: $s"
        cp -r "$STACK/$s/skills/"* "$SKILLS_DIR/" 2>/dev/null || true
        echo "  -> $(ls -1 $STACK/$s/skills/ | wc -l) Skills kopiert"
      else
        echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
      fi
    done
    echo "Fertig."
    ;;
  remove)
    for s in "$@"; do
      if [ -d "$STACK/$s/skills" ]; then
        echo "Entferne Stack: $s"
        for skill in "$STACK/$s/skills/"*; do
          name=$(basename "$skill")
          if [ -d "$SKILLS_DIR/$name" ]; then
            rm -rf "$SKILLS_DIR/$name"
            echo "  -> entfernt: $name"
          fi
        done
      else
        echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
      fi
    done
    echo "Fertig."
    ;;
  reinit)
    echo "Reinitialisiere Skills (behält nur aisdlc-*)..."
    # Nur aisdlc-* Skills behalten
    for d in "$SKILLS_DIR/"*; do
      name=$(basename "$d")
      if [[ ! "$name" =~ ^aisdlc- ]]; then
        rm -rf "$d"
      fi
    done
    echo "Alte Stack-Skills entfernt."
    # Neue Skills kopieren
    for s in "$@"; do
      if [ -d "$STACK/$s/skills" ]; then
        echo "Kopiere Stack: $s"
        cp -r "$STACK/$s/skills/"* "$SKILLS_DIR/" 2>/dev/null || true
        echo "  -> $(ls -1 $STACK/$s/skills/ | wc -l) Skills kopiert"
      else
        echo "WARNUNG: Stack '$s' nicht gefunden in $STACK"
      fi
    done
    echo "Fertig."
    ;;
  *)
    echo "Unbekannte Aktion: $ACTION"
    exit 1
    ;;
esac
