#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="${ALETHICODE_SOURCE_DIR:-/home/cypress/Alethicode}"
BASELINE_FILE="$ROOT/.alethicode_status_baseline.txt"
CURRENT_FILE="$ROOT/.alethicode_status_current.txt"

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "[ERROR] baseline file missing: $BASELINE_FILE"
  exit 1
fi

git -C "$SRC" status --porcelain > "$CURRENT_FILE"

if ! diff -u "$BASELINE_FILE" "$CURRENT_FILE" >/dev/null; then
  echo "[ERROR] Alethicode working tree changed compared to baseline"
  diff -u "$BASELINE_FILE" "$CURRENT_FILE" || true
  exit 2
fi

echo "[OK] Alethicode remains unchanged relative to baseline"
