#!/usr/bin/env bash
# Fails when a screen types a colour or corner radius instead of using the design tokens in
# app/src/main/java/com/arcade/sudoku/ui/theme. Illustrations in ui/art may use raw colours.
set -euo pipefail
cd "$(dirname "$0")/.."
src=app/src/main/java/com/arcade/sudoku
bad=$(grep -rnE 'Color\(0x|RoundedCornerShape\([0-9]+(\.dp)?\)' --include='*.kt' "$src" \
  | grep -v "^$src/ui/theme/" | grep -v "^$src/ui/art/" || true)
if [ -n "$bad" ]; then
  echo "Raw colours or corner radii outside the theme. Use the tokens in ui/theme/Tokens.kt:"
  echo "$bad"
  exit 1
fi
echo "Design tokens: OK"
