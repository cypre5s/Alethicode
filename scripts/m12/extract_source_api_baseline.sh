#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="${ALETHICODE_SOURCE_DIR:-/home/cypress/Alethicode}"
OUT_DIR="$ROOT/docs/baseline"
mkdir -p "$OUT_DIR"

rg -n "re_path\(|path\(|router\.register" \
  "$SRC/backend/account/urls/"*.py \
  "$SRC/backend/announcement/urls/"*.py \
  "$SRC/backend/conf/urls/"*.py \
  "$SRC/backend/problem/urls/"*.py \
  "$SRC/backend/submission/urls/"*.py \
  "$SRC/backend/ai_tutor/urls/"*.py \
  "$SRC/backend/classroom/urls/"*.py \
  "$SRC/backend/utils/urls.py" \
  "$SRC/backend/oj/urls.py" \
  > "$OUT_DIR/http-routes.txt"

sed -n '1,220p' "$SRC/backend/oj/asgi.py" > "$OUT_DIR/ws-routes.txt"

echo "[OK] baseline extracted to $OUT_DIR"
