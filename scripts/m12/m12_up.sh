#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

docker compose -f deploy/docker-compose.yml up -d --build

echo "[OK] M12 stack started with frontend. Frontend: http://127.0.0.1:18080"
