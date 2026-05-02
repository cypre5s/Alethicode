#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

matches="$(
  rg -n --fixed-strings "api/v1" \
    "$ROOT_DIR/backend/src" \
    "$ROOT_DIR/frontend/src" \
    "$ROOT_DIR/frontend/src" \
    "$ROOT_DIR/scripts" \
    "$ROOT_DIR/start.sh" || true
)"

matches="$(echo "$matches" | grep -v "/scripts/m12/guard_no_api_v1.sh:" || true)"

if [[ -n "$matches" ]]; then
  echo "[FAIL] Deprecated api/v1 reference detected:"
  echo "$matches"
  exit 1
fi

echo "[OK] No deprecated api/v1 references found in source paths."
