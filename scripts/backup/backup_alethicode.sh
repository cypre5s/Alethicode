#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="$ROOT_DIR/db_backups"
CONTAINER_NAME="${PG_CONTAINER:-java-oj-postgres}"
DB_NAME="${PG_DB:-alethicode}"
DB_USER="${PG_USER:-onlinejudge}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_backup_${TIMESTAMP}.sql"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker not found" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
  echo "[ERROR] postgres container not running: $CONTAINER_NAME" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

docker exec "$CONTAINER_NAME" \
  pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges > "$BACKUP_FILE"

if [[ ! -s "$BACKUP_FILE" ]]; then
  echo "[ERROR] backup file is empty: $BACKUP_FILE" >&2
  rm -f "$BACKUP_FILE"
  exit 1
fi

echo "[OK] backup created: $BACKUP_FILE"
