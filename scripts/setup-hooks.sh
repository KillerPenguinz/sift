#!/usr/bin/env bash
# Point git at the repo's tracked hooks. Run once after cloning.
#   ./scripts/setup-hooks.sh
set -euo pipefail
repo_root="$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
chmod +x "$repo_root/.githooks/"* 2>/dev/null || true
echo "Hooks activated: core.hooksPath -> .githooks"
