#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
TARGET_FILE="$BACKEND_DIR/target/classroom-monitor-200-baseline.json"
BASELINE_FILE="$ROOT_DIR/docs/baseline/M13-classroom-monitor-200-baseline.json"

cd "$BACKEND_DIR"
mvn -q -Dtest=ClassroomMonitorScaleIntegrationTest#monitorEndpointsPerformanceShouldMeetGateFor200Students test

if [[ ! -f "$TARGET_FILE" ]]; then
  echo "[FAIL] 未生成基线文件: $TARGET_FILE" >&2
  exit 1
fi

cp "$TARGET_FILE" "$BASELINE_FILE"
echo "[OK] 已写入课堂监控 200 学生基线: $BASELINE_FILE"
cat "$BASELINE_FILE"
