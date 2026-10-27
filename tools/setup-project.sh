#!/usr/bin/env bash
# AISDLC3 Projekt-Setup
# Erstellt einen neuen Projektordner mit kopiertem Template und gesetztem Profil
#
# Usage: ./tools/setup-project.sh <zielordner> <mvp|enterprise> [name]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$SCRIPT_DIR/../template"
TARGET="$1"
PROFILE="$2"
NAME="${3:-$(basename "$TARGET")}"

if [ -z "$TARGET" ] || [ -z "$PROFILE" ]; then
  echo "Usage: $0 <zielordner> <mvp|enterprise> [name]"
  exit 1
fi

if [ "$PROFILE" != "mvp" ] && [ "$PROFILE" != "enterprise" ]; then
  echo "Profil muss mvp oder enterprise sein"
  exit 1
fi

echo "Erstelle Projekt in: $TARGET"
echo "Profil: $PROFILE"
echo "Name: $NAME"
mkdir -p "$TARGET"

# Kopiere Framework-Kern
echo "Kopiere .opencode..."
cp -r "$SRC/.opencode" "$TARGET/"
echo "Kopiere .agent..."
cp -r "$SRC/.agent" "$TARGET/"
echo "Kopiere AGENTS.md..."
cp "$SRC/AGENTS.md" "$TARGET/"
echo "Kopiere README.md, how-to.md falls vorhanden..."
[ -f "$SRC/README.md" ] && cp "$SRC/README.md" "$TARGET/"
[ -f "$SRC/how-to.md" ] && cp "$SRC/how-to.md" "$TARGET/"

# Root opencode.jsonc (Stack-Schicht) kopieren
echo "Kopiere Root opencode.jsonc (Stack-Schicht)..."
cp "$SRC/opencode.jsonc" "$TARGET/"

# Verzeichnisse anlegen
mkdir -p "$TARGET/src" "$TARGET/tests" "$TARGET/docs/requirements" "$TARGET/docs/architecture" "$TARGET/docs/api" "$TARGET/docs/adr" "$TARGET/docs/qa" "$TARGET/docs/execution" "$TARGET/.agent/artifacts"

# Profil setzen
echo "Setze Profil auf $PROFILE..."
if [ "$PROFILE" = "mvp" ]; then
  cat > "$TARGET/.agent/config.json" << 'JSON'
{
  "$schema": "./schemas/config.schema.json",
  "profile": "mvp",
  "allow_fast_track": true,
  "traceability": "light",
  "gates": {
    "enforce_gate1": true,
    "enforce_gate2": true,
    "traceability_hard_fail": false
  },
  "security": {
    "enforce_minimal_scan": true,
    "block_on_critical_secrets": true
  },
  "context": {
    "briefing_first": true,
    "strict_three_line_reply": false
  }
}
JSON
else
  cat > "$TARGET/.agent/config.json" << 'JSON'
{
  "$schema": "./schemas/config.schema.json",
  "profile": "enterprise",
  "allow_fast_track": false,
  "traceability": "full",
  "gates": {
    "enforce_gate1": true,
    "enforce_gate2": true,
    "traceability_hard_fail": true
  },
  "security": {
    "enforce_minimal_scan": true,
    "block_on_critical_secrets": true
  },
  "context": {
    "briefing_first": true,
    "strict_three_line_reply": false
  }
}
JSON
fi

# Contract-Matrix kopieren
cp "$SRC/docs/architecture/contract-matrix.md" "$TARGET/docs/architecture/"

echo ""
echo "Fertig!"
echo "Projekt: $TARGET"
echo "Profil: $PROFILE"
echo ""
echo "Starte opencode wie folgt:"
echo "  cd $TARGET"
echo "  opencode"
echo ""
echo "Hinweis: .opencode/ und Root opencode.jsonc sind kopiert. OpenCode merged lokal."
echo "Falls du im Elternverzeichnis startest, kann es zu unerwarteter Merge-Reihenfolge kommen - immer im Projektordner starten!"
