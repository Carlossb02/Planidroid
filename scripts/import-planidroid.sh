#!/usr/bin/env bash
# Import the Planidroid 0.1.0.0 source archive, without credentials.
set -euo pipefail
if [[ $# != 1 ]]; then
  echo "Usage: bash scripts/import-planidroid.sh /path/to/Planidroid_0.1.0.0_AndroidStudio.zip" >&2
  exit 2
fi
archive="$(realpath "$1")"
cd "$(git rev-parse --show-toplevel)"
[[ "$(git branch --show-current)" == "chore/secure-planidroid-import" ]] || { echo "Switch to chore/secure-planidroid-import first." >&2; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Commit or stash tracked changes first." >&2; exit 1; }
printf '%s  %s\n' '7edb886a5b44c2d20170cb8daa908e0dfaf207d82259fb376c6283863cc1b4a7' "$archive" | sha256sum -c -
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$archive" -d "$tmp"
src="$tmp/Planidroid"
test -f "$src/app/build.gradle.kts"
test -f "$src/settings.gradle.kts"
command -v python3 >/dev/null
shopt -s dotglob nullglob
# Only allow identical pre-existing protection files and the placeholder README.
for f in "$src"/*; do
  name="$(basename "$f")"
  if [[ -e "$name" || -L "$name" ]]; then
    if [[ "$name" == .gitignore ]]; then
      cmp -s "$f" "$name" || { echo "Existing .gitignore differs; review first." >&2; exit 1; }
    elif [[ "$name" != README.md ]]; then
      echo "Existing path: $name; import stopped." >&2; exit 1
    fi
  fi
done
cp -a "$src/.gitignore" .gitignore
for f in "$src"/*; do
  name="$(basename "$f")"
  cp -a "$f" "$name"
done
bash tools/install-git-hooks.sh
chmod +x gradlew
for f in "$src"/*; do git add -- "$(basename "$f")"; done
python3 tools/check_secrets.py --staged
git diff --cached --stat
echo "Project staged and checked. Review staged changes, then commit and push."
