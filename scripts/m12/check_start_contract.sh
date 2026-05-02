#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
START_SCRIPT="$ROOT_DIR/start.sh"

if rg -q 'prefer_local_runtime "\.tools/' "$START_SCRIPT"; then
  echo "[ERROR] start.sh should not force .tools runtime injection anymore" >&2
  exit 1
fi

grep -Fq 'require_cmd node' "$START_SCRIPT"
grep -Fq 'require_cmd npm' "$START_SCRIPT"
grep -Fq 'require_cmd java' "$START_SCRIPT"
grep -Fq 'require_cmd mvn' "$START_SCRIPT"
grep -Fq 'ensure_node_version' "$START_SCRIPT"

echo "[OK] start.sh runtime contract satisfied (PATH-first mode)"
