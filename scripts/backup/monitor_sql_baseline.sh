#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT_DIR/docs/baseline"
OUT_FILE="$OUT_DIR/M13-sql-hotspot-baseline.txt"
CONTAINER="${PG_CONTAINER:-java-oj-postgres}"
DB_NAME="${PG_DB:-alethicode}"
DB_USER="${PG_USER:-onlinejudge}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[FAIL] docker 未安装，无法采集 pg_stat_statements 基线" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "[FAIL] 容器未运行: $CONTAINER" >&2
  exit 1
fi

if ! docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -Atqc \
  "select 1 from pg_extension where extname = 'pg_stat_statements'" | grep -qx "1"; then
  echo "[FAIL] pg_stat_statements 未启用，请先按部署配置启动 PostgreSQL" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

{
  echo "# M13 SQL 热点基线"
  echo "generated_at=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo "container=$CONTAINER"
  echo
  echo "## TOP 慢 SQL (按 total_exec_time)"
  docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -P pager=off -c \
    "select queryid, calls, round(total_exec_time::numeric, 3) as total_ms, round(mean_exec_time::numeric, 3) as mean_ms, left(regexp_replace(query, E'\\\\s+', ' ', 'g'), 220) as sample_query from pg_stat_statements order by total_exec_time desc limit 20;"
  echo
  echo "## TOP 高频 SQL (按 calls)"
  docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -P pager=off -c \
    "select queryid, calls, round(total_exec_time::numeric, 3) as total_ms, round(mean_exec_time::numeric, 3) as mean_ms, left(regexp_replace(query, E'\\\\s+', ' ', 'g'), 220) as sample_query from pg_stat_statements order by calls desc limit 20;"
} > "$OUT_FILE"

echo "[OK] 已输出 SQL 热点基线: $OUT_FILE"
