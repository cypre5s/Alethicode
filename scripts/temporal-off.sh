#!/usr/bin/env bash
# 用法（管理员 SSH 到云主机后执行）：
#   bash scripts/temporal-off.sh             # 安全模式：检测到运行中 workflow 会询问
#   bash scripts/temporal-off.sh --force     # 强制关闭，跳过 workflow 探测
#
# 作用：关闭按需启动的 Temporal 容器，释放约 ~160-512MB 内存。
#       backend 的 Temporal SDK 仍在 lazy 模式等待，下次启动 Temporal 后
#       会在 60 秒内自动重连，无需重启 backend。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.yml"
TEMPORAL_CONTAINER="java-oj-temporal"
FORCE="${1:-}"

if ! docker ps --format '{{.Names}}' | grep -qx "$TEMPORAL_CONTAINER"; then
  echo "[INFO] Temporal not running; nothing to do."
  exit 0
fi

if [[ "$FORCE" != "--force" ]]; then
  echo "[INFO] checking running workflows (timeout 10s)..."
  running_count="$(timeout 10 docker exec "$TEMPORAL_CONTAINER" \
      temporal workflow list --query 'ExecutionStatus="Running"' --output json 2>/dev/null \
      | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin)
    print(len(d) if isinstance(d,list) else 0)
except Exception:
    print("")
' 2>/dev/null || echo "")"
  if [[ -n "$running_count" ]] && [[ "$running_count" =~ ^[0-9]+$ ]] && [[ "$running_count" -gt 0 ]]; then
    echo "[WARN] $running_count running workflow(s) detected."
    read -r -p "Stop anyway? [y/N] " ans
    if [[ ! "$ans" =~ ^[Yy]$ ]]; then
      echo "Aborted."
      exit 1
    fi
  fi
fi

echo "[INFO] stopping Temporal..."
docker compose -f "$COMPOSE_FILE" --profile temporal stop temporal

echo "[OK] Temporal stopped (memory ~160-512MB freed)"
echo "[INFO] backend 的 Temporal SDK 仍在 lazy 模式等待，下次启动 Temporal 后会在 60 秒内自动重连"
