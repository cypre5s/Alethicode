#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:18080}"

echo "[INFO] smoke against $BASE_URL"

assert_contains() {
  local pattern="$1"
  local file="$2"

  if command -v rg >/dev/null 2>&1; then
    rg -q "$pattern" "$file"
    return
  fi

  grep -q "$pattern" "$file"
}

curl -fsS "$BASE_URL/" >/dev/null
curl -fsS "$BASE_URL/api/website" >/tmp/m12_website.json
curl -fsS "$BASE_URL/api/languages" >/tmp/m12_languages.json
curl -fsS -c /tmp/m12_cookie.txt "$BASE_URL/api/csrf" >/tmp/m12_csrf.json

if ! assert_contains '"error"' /tmp/m12_website.json; then
  echo "[ERROR] /api/website response is not wrapped by {error,data}" >&2
  exit 1
fi

if ! assert_contains '"data"' /tmp/m12_languages.json; then
  echo "[ERROR] /api/languages missing data field" >&2
  exit 1
fi

if ! assert_contains 'csrftoken' /tmp/m12_cookie.txt; then
  echo "[ERROR] csrftoken cookie not found" >&2
  exit 1
fi

echo "[OK] M12 smoke passed"
