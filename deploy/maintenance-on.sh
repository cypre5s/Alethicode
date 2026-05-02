#!/usr/bin/env bash
# 启用前端维护页：所有 /、/api/、/ws/、/admin/、/public/avatar/ 立即返回 503，
# 重定向到 maintenance.html。flag 文件由 host 写入，nginx worker 实时检测，
# 无需 reload nginx。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLAG_DIR="$SCRIPT_DIR/data/runtime"
FLAG_FILE="$FLAG_DIR/maintenance.flag"

mkdir -p "$FLAG_DIR"
: > "$FLAG_FILE"

REOPEN="${MAINTENANCE_REOPEN:-}"
if [[ -n "$REOPEN" ]]; then
  echo "$REOPEN" > "$FLAG_DIR/reopen.txt"
  echo "[INFO] 预计恢复时间：$REOPEN（保存至 $FLAG_DIR/reopen.txt）"
fi

echo "[OK] 维护页已开启：$FLAG_FILE"
echo "[HINT] 关闭命令：bash deploy/maintenance-off.sh"
