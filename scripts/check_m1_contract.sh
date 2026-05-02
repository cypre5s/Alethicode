#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"

check() {
  local path="$1"
  echo "==> GET $BASE_URL$path"
  curl -sS "$BASE_URL$path" | jq -e '.error == null and has("data")' >/dev/null
}

check "/api/website"
check "/api/languages"
check "/api/csrf"

echo "[OK] M1 contract checks passed"
