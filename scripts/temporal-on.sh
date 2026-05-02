#!/usr/bin/env bash
# 用法（管理员 SSH 到云主机后执行）：
#   bash scripts/temporal-on.sh
#
# 作用：临时启动 Temporal 容器以跑课件流水线。
#       2C4G 主机日常关闭以省 ~512MB 内存。
#       backend 在 60 秒内自动检测并注册 worker，无需重启 backend。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.yml"
TEMPORAL_CONTAINER="java-oj-temporal"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker not found in PATH" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "[ERROR] docker-compose.yml not found: $COMPOSE_FILE" >&2
  exit 1
fi

echo "[INFO] starting Temporal container (on-demand, profile=temporal)..."
docker compose -f "$COMPOSE_FILE" --profile temporal up -d temporal

echo "[INFO] waiting Temporal healthy (max 120s)..."
for i in $(seq 1 60); do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$TEMPORAL_CONTAINER" 2>/dev/null || true)"
  if [[ "$status" == "healthy" ]] || { [[ "$status" == "running" ]] && [[ $i -gt 30 ]]; }; then
    echo "[OK] Temporal ready: 127.0.0.1:7233 (status=$status)"
    echo ""
    echo "下一步："
    echo "  1) backend 会在 60 秒内自动检测并注册 worker（无需重启 backend）"
    echo "  2) 验证：curl -s http://127.0.0.1:8081/api/admin/temporal/status (需要管理员 cookie)"
    echo "  3) 完成后请记得：bash scripts/temporal-off.sh"
    exit 0
  fi
  sleep 2
done

echo "[ERROR] Temporal did not become healthy within 120s" >&2
docker logs "$TEMPORAL_CONTAINER" --tail 100 >&2 || true
exit 1
