#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/backend/.env"
LANGUAGE_PACK_ROOT="${LANGUAGE_PACK_ROOT:-$ROOT_DIR/deploy/data/language_pack}"
TASKS_DIR="$LANGUAGE_PACK_ROOT/tasks"
PREVIEW_TASKS_DIR="$LANGUAGE_PACK_ROOT/preview/tasks"
BACKUP_BASE_DIR="${BACKUP_BASE_DIR:-$ROOT_DIR/deploy/data/language_pack_backups}"
DRY_RUN=0
ENABLE_BACKUP=1
BACKUP_RETAIN_DAYS="${BACKUP_RETAIN_DAYS:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --no-backup)
      ENABLE_BACKUP=0
      shift
      ;;
    --backup-dir)
      BACKUP_BASE_DIR="$2"
      shift 2
      ;;
    --backup-retain-days)
      BACKUP_RETAIN_DAYS="$2"
      shift 2
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      echo "Usage: $0 [--dry-run] [--no-backup] [--backup-dir <dir>] [--backup-retain-days <days>]" >&2
      exit 1
      ;;
  esac
done

if [[ -n "$BACKUP_RETAIN_DAYS" && ! "$BACKUP_RETAIN_DAYS" =~ ^[0-9]+$ ]]; then
  echo "[ERROR] --backup-retain-days must be a non-negative integer" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] Missing env file: $ENV_FILE" >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "[ERROR] psql is required but not found" >&2
  exit 1
fi

if [[ ! -d "$TASKS_DIR" ]]; then
  echo "[ERROR] Missing tasks directory: $TASKS_DIR" >&2
  exit 1
fi

if [[ ! -d "$PREVIEW_TASKS_DIR" ]]; then
  echo "[ERROR] Missing preview tasks directory: $PREVIEW_TASKS_DIR" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5436}"
DB_NAME="${DB_NAME:-alethicode}"
DB_USER="${DB_USER:-onlinejudge}"
if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "[ERROR] DB_PASSWORD is empty in $ENV_FILE" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

PGPASSWORD="$DB_PASSWORD" psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER" \
  -Atc "SELECT id FROM language_pack_init_task ORDER BY id" \
  | sort > "$TMP_DIR/db_task_ids.txt"

find "$TASKS_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort > "$TMP_DIR/fs_task_ids.txt"
find "$PREVIEW_TASKS_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort > "$TMP_DIR/fs_preview_ids.txt"

comm -23 "$TMP_DIR/fs_task_ids.txt" "$TMP_DIR/db_task_ids.txt" > "$TMP_DIR/orphan_task_ids.txt"
comm -23 "$TMP_DIR/fs_preview_ids.txt" "$TMP_DIR/db_task_ids.txt" > "$TMP_DIR/orphan_preview_ids.txt"

ORPHAN_TASK_COUNT="$(wc -l < "$TMP_DIR/orphan_task_ids.txt")"
ORPHAN_PREVIEW_COUNT="$(wc -l < "$TMP_DIR/orphan_preview_ids.txt")"
TOTAL_ORPHAN_COUNT=$((ORPHAN_TASK_COUNT + ORPHAN_PREVIEW_COUNT))

echo "[INFO] live_task_count=$(wc -l < "$TMP_DIR/db_task_ids.txt")"
echo "[INFO] orphan_task_dir_count=$ORPHAN_TASK_COUNT"
echo "[INFO] orphan_preview_dir_count=$ORPHAN_PREVIEW_COUNT"

prune_backups() {
  if [[ -z "$BACKUP_RETAIN_DAYS" ]]; then
    return 0
  fi
  echo "[INFO] Pruning backup archives older than $BACKUP_RETAIN_DAYS days in $BACKUP_BASE_DIR"
  find "$BACKUP_BASE_DIR" -type f -name 'language_pack_orphans_*.tar.gz' -mtime +"$BACKUP_RETAIN_DAYS" -print -delete 2>/dev/null | sed 's/^/[PRUNE] /' || true
}

if [[ "$TOTAL_ORPHAN_COUNT" -eq 0 ]]; then
  if [[ "$DRY_RUN" -eq 1 && -n "$BACKUP_RETAIN_DAYS" ]]; then
    echo "[INFO] backup_prune_preview:"
    find "$BACKUP_BASE_DIR" -type f -name 'language_pack_orphans_*.tar.gz' -mtime +"$BACKUP_RETAIN_DAYS" -print 2>/dev/null | sed 's/^/  - /' || true
  elif [[ "$DRY_RUN" -eq 0 ]]; then
    prune_backups
  fi
  echo "[OK] No orphan language pack directories found"
  exit 0
fi

{
  while IFS= read -r task_id; do
    [[ -n "$task_id" ]] && echo "tasks/$task_id"
  done < "$TMP_DIR/orphan_task_ids.txt"
  while IFS= read -r task_id; do
    [[ -n "$task_id" ]] && echo "preview/tasks/$task_id"
  done < "$TMP_DIR/orphan_preview_ids.txt"
} > "$TMP_DIR/orphan_paths.txt"

echo "[INFO] sample_orphans:"
head -n 20 "$TMP_DIR/orphan_paths.txt" | sed 's/^/  - /'

if [[ "$DRY_RUN" -eq 1 ]]; then
  if [[ -n "$BACKUP_RETAIN_DAYS" ]]; then
    echo "[INFO] backup_prune_preview:"
    find "$BACKUP_BASE_DIR" -type f -name 'language_pack_orphans_*.tar.gz' -mtime +"$BACKUP_RETAIN_DAYS" -print 2>/dev/null | sed 's/^/  - /' || true
  fi
  echo "[OK] Dry run mode: no backup created, no directory deleted"
  exit 0
fi

if [[ "$ENABLE_BACKUP" -eq 1 ]]; then
  mkdir -p "$BACKUP_BASE_DIR"
  BACKUP_FILE="$BACKUP_BASE_DIR/language_pack_orphans_$(date +%Y%m%d_%H%M%S).tar.gz"
  tar -czf "$BACKUP_FILE" -C "$LANGUAGE_PACK_ROOT" -T "$TMP_DIR/orphan_paths.txt"
  echo "[OK] Backup created: $BACKUP_FILE"
fi

prune_backups

while IFS= read -r relative_path; do
  [[ -z "$relative_path" ]] && continue
  target="$LANGUAGE_PACK_ROOT/$relative_path"
  if [[ -d "$target" ]]; then
    rm -rf "$target"
    echo "[DEL] $target"
  fi
done < "$TMP_DIR/orphan_paths.txt"

echo "[OK] Cleanup completed"
