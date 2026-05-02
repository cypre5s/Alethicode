#!/usr/bin/env bash
# 关闭前端维护页：删除 flag 文件，nginx worker 在下一次请求里立即恢复正常。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLAG_DIR="$SCRIPT_DIR/data/runtime"
FLAG_FILE="$FLAG_DIR/maintenance.flag"

if [[ -f "$FLAG_FILE" ]]; then
  rm -f "$FLAG_FILE"
  echo "[OK] 维护页已关闭"
else
  echo "[INFO] 维护页本来就未启用：$FLAG_FILE 不存在"
fi
