#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/db_backups}"
CONTAINER_NAME="${PG_CONTAINER:-java-oj-postgres}"
DB_NAME="${PG_DB:-alethicode}"
DB_USER="${PG_USER:-onlinejudge}"
KEEP_DAYS="${KEEP_DAYS:-7}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

if ! docker ps --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
  echo "[ERROR] $(date): postgres container not running: $CONTAINER_NAME" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

docker exec "$CONTAINER_NAME" \
  pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges | gzip > "$BACKUP_FILE"

if [[ ! -s "$BACKUP_FILE" ]]; then
  echo "[ERROR] $(date): backup file is empty: $BACKUP_FILE" >&2
  rm -f "$BACKUP_FILE"
  exit 1
fi

BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "[OK] $(date): backup created: $BACKUP_FILE ($BACKUP_SIZE)"

DELETED=$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +"$KEEP_DAYS" -print -delete | wc -l)
if [[ "$DELETED" -gt 0 ]]; then
  echo "[OK] cleaned $DELETED backup(s) older than ${KEEP_DAYS} days"
fi
