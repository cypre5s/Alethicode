#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOMAIN="${1:-}"

BACKUP_JOB="0 3 * * * $ROOT_DIR/scripts/auto_backup.sh >> $ROOT_DIR/db_backups/backup.log 2>&1"

EXISTING=$(crontab -l 2>/dev/null || true)

if echo "$EXISTING" | grep -q "auto_backup.sh"; then
  echo "[INFO] auto_backup cron already exists, skip"
else
  (echo "$EXISTING"; echo "$BACKUP_JOB") | crontab -
  echo "[OK] added: daily 03:00 auto_backup"
fi

if [[ -n "$DOMAIN" ]]; then
  RENEW_JOB="0 4 * * 1 $ROOT_DIR/scripts/certbot_renew.sh $DOMAIN >> $ROOT_DIR/db_backups/certbot.log 2>&1"
  if echo "$EXISTING" | grep -q "certbot_renew.sh"; then
    echo "[INFO] certbot_renew cron already exists, skip"
  else
    (crontab -l 2>/dev/null; echo "$RENEW_JOB") | crontab -
    echo "[OK] added: weekly Monday 04:00 certbot_renew"
  fi
fi

echo ""
echo "当前 crontab:"
crontab -l
