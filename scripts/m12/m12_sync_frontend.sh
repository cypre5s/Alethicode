#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE_DIR="${ALETHICODE_SOURCE_DIR:-/home/cypress/Alethicode}/frontend/"
TARGET_DIR="$ROOT/frontend/"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "[ERROR] source frontend not found: $SOURCE_DIR" >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
rsync -a --delete \
  --exclude=node_modules \
  --exclude=dist \
  --exclude=.git \
  "$SOURCE_DIR" "$TARGET_DIR"

echo "[OK] frontend synced from Alethicode/frontend to $TARGET_DIR"
