#!/usr/bin/env bash
# Import the verified Planidroid 0.5.0.1 project ZIP into this draft PR.
set -euo pipefail
if [[ $# != 1 ]]; then
  echo "Usage: bash scripts/import-planidroid.sh /path/to/Planidroid_0.5.0.1_AndroidStudio.zip" >&2
  exit 2
fi
archive="$(realpath "$1")"
cd "$(git rev-parse --show-toplevel)"
[[ "$(git branch --show-current)" == "feat/planidroid-simple-ui" ]] || { echo "Switch to feat/planidroid-simple-ui first." >&2; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Commit or stash tracked changes first." >&2; exit 1; }
printf '%s  %s\n' '68ee63ac1222c5450184320ac85dd5a8737f710b635e74c6b815cb3dc5c22496' "$archive" | sha256sum -c -
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$archive" -d "$tmp"
src="$tmp/Planidroid"
test -f "$src/app/build.gradle.kts"
test -f "$src/settings.gradle.kts"
shopt -s dotglob nullglob
# Refuse to overwrite existing files other than the placeholder README.
for f in "$src"/*; do
  name="$(basename "$f")"
  if [[ -e "$name" || -L "$name" ]]; then
    [[ "$name" == README.md ]] || { echo "Existing path: $name; import stopped." >&2; exit 1; }
  fi
done
for f in "$src"/*; do
  name="$(basename "$f")"
  cp -a "$f" "$name"
  git add -- "$name"
done
chmod +x gradlew
git add -- gradlew
git diff --cached --stat
echo "Project staged. Review git diff --cached, then commit and push this branch."
