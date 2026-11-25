#!/bin/sh
set -eu
cd "$(git rev-parse --show-toplevel)"
command -v python3 >/dev/null || { echo 'Install Python 3 before enabling Git hooks.' >&2; exit 1; }
chmod +x .githooks/pre-commit .githooks/pre-push
git config --local core.hooksPath .githooks
printf '%s\n' 'Planidroid Git safeguards enabled for this clone.'
