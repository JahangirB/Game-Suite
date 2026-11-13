#!/usr/bin/env bash
# Fails when a screen types a colour, corner radius or text size instead of using the design tokens in
# app/src/main/java/com/pippop/ui/theme. Illustrations in ui/art may use raw colours.
set -euo pipefail
cd "$(dirname "$0")/.."
src=app/src/main/java/com/pippop
bad=$(grep -rnE 'Color\(0x|RoundedCornerShape\([0-9]+(\.dp)?\)|(^|[^A-Za-z0-9_.)])[0-9]+(\.[0-9]+)?\.sp\b' --include='*.kt' "$src" \
  | grep -v "^$src/ui/theme/" | grep -v "^$src/ui/art/" || true)
if [ -n "$bad" ]; then
  echo "Raw colours, corner radii or text sizes outside the theme. Use the tokens in ui/theme/Tokens.kt:"
  echo "$bad"
  exit 1
fi
echo "Design tokens: OK"

# The app's own parts (ui/screens/Toy.kt) stand in for the stock Material ones. Fail when a stock one comes back.
# A name only counts when whatever is before it is not part of another name (ToyAlertDialog( is fine,
# androidx.compose.material3.Switch( is not). Comment-only lines are skipped.
code=$(grep -rn --include='*.kt' '' "$src" | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|/?\*)' || true)
stock=$(printf '%s\n' "$code" | grep -E '(^|[^A-Za-z0-9_])(OutlinedTextField|FilterChip|CircularProgressIndicator|SnackbarHost|Switch)\(' || true)
# ModalBottomSheet may appear only in ToySheet.
sheets=$(printf '%s\n' "$code" | grep -E '(^|[^A-Za-z0-9_])ModalBottomSheet\(' | grep -v "^$src/ui/screens/Toy.kt" || true)
# AlertDialog is allowed only inside LevelDialog (HomeScreen.kt) and RulesDialog (GameFrame.kt).
# TODO: steps 11 and 12 replace those two with ToyDialog; delete this exception then.
dialogs=$(find "$src" -name '*.kt' -print0 | xargs -0 awk '
  FNR == 1 { fn = "" }
  /^[[:space:]]*(\/\/|\*|\/\*)/ { next }
  /^[[:space:]]*([A-Za-z@]+[[:space:]]+)*fun[[:space:]]/ { fn = $0; sub(/\(.*/, "", fn); sub(/.*fun[[:space:]]+/, "", fn) }
  /(^|[^A-Za-z0-9_])AlertDialog\(/ {
    n = split(FILENAME, parts, "/"); file = parts[n]
    if (!((file == "HomeScreen.kt" && fn == "LevelDialog") || (file == "GameFrame.kt" && fn == "RulesDialog"))) print FILENAME ":" FNR ":" $0
  }' || true)
bad="$stock
$sheets
$dialogs"
if [ -n "$(printf '%s' "$bad" | tr -d '[:space:]')" ]; then
  echo "Stock Material parts are back. Use ToyDialog, ToySheet, ToyField, CodeField, Choices, ToySwitch, PipPopLoader or Toast (ui/screens/Toy.kt):"
  printf '%s\n' "$bad" | grep -v '^$'
  exit 1
fi
echo "Stock parts: OK"
