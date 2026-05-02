#!/usr/bin/env bash
# 用法: bash scripts/loadtest-2c4g.sh
# 简单的并发压测：模拟 100 并发用户连续 30 秒访问题目列表 API。
#
# 期望（2C4G + Phase 0-5 全部应用后）：
#   - p95 latency  < 200ms（命中 Nginx proxy_cache）
#   - p99 latency  < 500ms
#   - error rate   = 0
#   - backend CPU 期间 < 50%（缓存挡住 80%+ 请求）
#
# 依赖：docker（拉取 alpine/bombardier:latest 跑测试，无需本地装）
set -e

ENDPOINT="${ENDPOINT:-http://127.0.0.1:18080}"
CONCURRENT="${CONCURRENT:-100}"
DURATION="${DURATION:-30}"
TARGET_PATH="${TARGET_PATH:-/api/problem?limit=20&offset=0}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker not found in PATH" >&2
  exit 1
fi

echo "[INFO] loadtest: ${CONCURRENT} conn x ${DURATION}s -> ${ENDPOINT}${TARGET_PATH}"
echo ""

docker run --rm --network host alpine/bombardier:latest \
  -c "$CONCURRENT" -d "${DURATION}s" -l \
  "${ENDPOINT}${TARGET_PATH}"
