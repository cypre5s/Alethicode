#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_PORT="${BACKEND_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-8080}"
JUDGE_PORT="${JUDGE_PORT:-12358}"
JUDGE_IMAGE="${JUDGE_IMAGE:-registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1}"
JUDGE_CONTAINER_NAME="${JUDGE_CONTAINER_NAME:-java-oj-judge-local}"
TUTOR_GRAPH_PORT="${TUTOR_GRAPH_PORT:-8100}"
TUTOR_GRAPH_IMAGE="${TUTOR_GRAPH_IMAGE:-alethicode/tutor-graph:local}"
TUTOR_GRAPH_CONTAINER_NAME="${TUTOR_GRAPH_CONTAINER_NAME:-java-oj-tutor-graph-local}"
TUTOR_GRAPH_CHECKPOINTER="${TUTOR_GRAPH_CHECKPOINTER:-postgres}"
TUTOR_GRAPH_FORCE_REBUILD="${TUTOR_GRAPH_FORCE_REBUILD:-0}"
TUTOR_GRAPH_DEV_MOUNT="${TUTOR_GRAPH_DEV_MOUNT:-1}"
TUTOR_GRAPH_SOURCE_HASH_LABEL="com.alethicode.tutor-graph-source-hash"
POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-5436}"
POSTGRES_DB_NAME="${POSTGRES_DB_NAME:-alethicode}"
NATS_PORT="${NATS_PORT:-4222}"
NATS_MONITOR_PORT="${NATS_MONITOR_PORT:-8222}"
NATS_CONTAINER_NAME="${NATS_CONTAINER_NAME:-java-oj-nats}"
TEMPORAL_PORT="${TEMPORAL_PORT:-7233}"
TEMPORAL_CONTAINER_NAME="${TEMPORAL_CONTAINER_NAME:-java-oj-temporal}"
MEMGRAPH_PORT="${MEMGRAPH_PORT:-7687}"
MEMGRAPH_CONTAINER_NAME="${MEMGRAPH_CONTAINER_NAME:-java-oj-memgraph}"
ALETHICODE_RAG_PORT="${ALETHICODE_RAG_PORT:-8200}"
ALETHICODE_RAG_CONTAINER_NAME="${ALETHICODE_RAG_CONTAINER_NAME:-java-oj-alethicode-rag}"
COMPOSE_NETWORK_NAME="${COMPOSE_NETWORK_NAME:-alethicode-java_default}"
JAEGER_CONTAINER_NAME="${JAEGER_CONTAINER_NAME:-java-oj-jaeger}"
JAEGER_UI_PORT="${JAEGER_UI_PORT:-16686}"
ENABLE_GRAFANA="${ENABLE_GRAFANA:-1}"
GRAFANA_RUNTIME="${GRAFANA_RUNTIME:-auto}"
GRAFANA_PORT="${GRAFANA_PORT:-3000}"
GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.1.0}"
GRAFANA_CONTAINER_NAME="${GRAFANA_CONTAINER_NAME:-java-oj-grafana-local}"
GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin@localhost}"
GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-admin}"
GRAFANA_VERSION="${GRAFANA_VERSION:-11.1.0}"
GRAFANA_DIST_URL="${GRAFANA_DIST_URL:-https://dl.grafana.com/oss/release/grafana-${GRAFANA_VERSION}.linux-amd64.tar.gz}"
GRAFANA_RUNTIME_DIR="$ROOT_DIR/.runtime/grafana/runtime-v${GRAFANA_VERSION}"
GRAFANA_DATA_DIR="${GRAFANA_DATA_DIR:-$ROOT_DIR/deploy/data/grafana}"
GRAFANA_LOG_DIR="${GRAFANA_LOG_DIR:-$ROOT_DIR/deploy/data/grafana/log}"
GRAFANA_PID_FILE="$ROOT_DIR/deploy/data/grafana/.grafana.pid"
GRAFANA_BINARY_LOG="$ROOT_DIR/deploy/data/grafana/grafana-server.log"
GRAFANA_ACTIVE_RUNTIME=""
OBSERVABILITY_CONFIG_DIR="${OBSERVABILITY_CONFIG_DIR:-$ROOT_DIR/deploy/observability}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v2.53.3}"
PROMETHEUS_CONTAINER_NAME="${PROMETHEUS_CONTAINER_NAME:-java-oj-prometheus-local}"
OBSERVABILITY_NETWORK_NAME="${OBSERVABILITY_NETWORK_NAME:-java-oj-observability}"
PROMETHEUS_CONFIG_DIR="$ROOT_DIR/deploy/data/prometheus/config"
PROMETHEUS_LOCAL_CONFIG="$PROMETHEUS_CONFIG_DIR/prometheus.yml"
SKIP_FRONTEND="${SKIP_FRONTEND:-0}"
DEFAULT_MAVEN_OPTS="${MAVEN_OPTS:--Xms128m -Xmx384m}"
DEFAULT_NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=768}"
BACKEND_LOG="$ROOT_DIR/backend/.start-backend.log"
BACKEND_PID_FILE="$ROOT_DIR/backend/.start-backend.pid"
REQUIRED_NODE_VERSION="20.19.0"
ORIGINAL_HTTP_PROXY=""
ORIGINAL_HTTPS_PROXY=""
ORIGINAL_ALL_PROXY=""
ORIGINAL_NO_PROXY=""

prepend_path_dir() {
  local dir="$1"
  if [[ -d "$dir" ]] && [[ ":$PATH:" != *":$dir:"* ]]; then
    PATH="$dir:$PATH"
  fi
}

bootstrap_runtime_path() {
  prepend_path_dir "$HOME/java/jdk-21.0.2/bin"
  prepend_path_dir "$HOME/java/apache-maven-3.9.6/bin"

  if [[ -d "$HOME/.nvm/versions/node" ]]; then
    local latest_nvm_bin
    latest_nvm_bin="$(find "$HOME/.nvm/versions/node" -maxdepth 2 -type d -name bin | sort -V | tail -n 1 || true)"
    if [[ -n "$latest_nvm_bin" ]]; then
      prepend_path_dir "$latest_nvm_bin"
    fi
  fi

  export PATH
}

ensure_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]]; then
    return
  fi
  local java_cmd resolved
  java_cmd="$(command -v java || true)"
  if [[ -z "$java_cmd" ]]; then
    return
  fi
  resolved="$(readlink -f "$java_cmd" 2>/dev/null || echo "$java_cmd")"
  if [[ -n "$resolved" ]]; then
    JAVA_HOME="$(cd "$(dirname "$resolved")/.." && pwd)"
    export JAVA_HOME
  fi
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] missing command: $1" >&2
    exit 1
  fi
}

version_ge() {
  local current="$1"
  local required="$2"
  local current_major current_minor current_patch
  local required_major required_minor required_patch

  IFS='.' read -r current_major current_minor current_patch <<<"$current"
  IFS='.' read -r required_major required_minor required_patch <<<"$required"

  current_minor="${current_minor:-0}"
  current_patch="${current_patch:-0}"
  required_minor="${required_minor:-0}"
  required_patch="${required_patch:-0}"

  if (( current_major > required_major )); then
    return 0
  fi
  if (( current_major < required_major )); then
    return 1
  fi
  if (( current_minor > required_minor )); then
    return 0
  fi
  if (( current_minor < required_minor )); then
    return 1
  fi
  (( current_patch >= required_patch ))
}

ensure_node_version() {
  local current_version
  current_version="$(node -p 'process.versions.node')"
  if ! version_ge "$current_version" "$REQUIRED_NODE_VERSION"; then
    echo "[ERROR] frontend requires Node >= ${REQUIRED_NODE_VERSION}, current: ${current_version}" >&2
    exit 1
  fi
}

load_env_file() {
  local env_file="$1"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
    echo "[INFO] loaded env: $env_file"
  fi
}

check_port_free() {
  local port="$1"
  if ss -ltn | awk '{print $4}' | rg -q ":${port}$"; then
    echo "[ERROR] port ${port} already in use" >&2
    exit 1
  fi
}

wait_http_ok() {
  local url="$1"
  local retries="${2:-60}"
  local interval="${3:-1}"
  for _ in $(seq 1 "$retries"); do
    code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "$url" || true)
    if [[ "$code" == "200" ]]; then
      return 0
    fi
    sleep "$interval"
  done
  return 1
}

wait_nats_ready() {
  wait_http_ok "http://127.0.0.1:${NATS_MONITOR_PORT}/healthz" 60 1
}

wait_temporal_ready() {
  for _ in $(seq 1 90); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$TEMPORAL_CONTAINER_NAME" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

ensure_compose_network() {
  if docker network inspect "$COMPOSE_NETWORK_NAME" >/dev/null 2>&1; then
    return 0
  fi
  docker network create "$COMPOSE_NETWORK_NAME" >/dev/null
}

ensure_compose_network_alias() {
  local service_name="$1"
  local container_name="$2"
  ensure_compose_network
  if docker inspect -f '{{json .NetworkSettings.Networks}}' "$container_name" 2>/dev/null | rg -q "\"${COMPOSE_NETWORK_NAME}\""; then
    return 0
  fi
  docker network connect --alias "$service_name" --alias "$container_name" "$COMPOSE_NETWORK_NAME" "$container_name"
}

ensure_compose_service_running() {
  local service_name="$1"
  local container_name="$2"
  local exists
  exists=$(docker ps -a --format '{{.Names}}' | rg -x "$container_name" || true)
  if [[ -n "$exists" ]]; then
    docker start "$container_name" >/dev/null
    ensure_compose_network_alias "$service_name" "$container_name"
    return 0
  fi
  docker compose -f "$ROOT_DIR/deploy/docker-compose.yml" up -d --no-deps "$service_name"
}

remove_temporal_with_stale_config() {
  if ! docker ps -a --format '{{.Names}}' | rg -qx "$TEMPORAL_CONTAINER_NAME"; then
    return 0
  fi
  local db_driver dynamic_config http_proxy healthcheck
  db_driver="$(
    docker inspect "$TEMPORAL_CONTAINER_NAME" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
      | awk -F= '$1=="DB"{print substr($0, index($0,"=")+1); exit}'
  )"
  dynamic_config="$(
    docker inspect "$TEMPORAL_CONTAINER_NAME" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
      | awk -F= '$1=="DYNAMIC_CONFIG_FILE_PATH"{print substr($0, index($0,"=")+1); exit}'
  )"
  http_proxy="$(
    docker inspect "$TEMPORAL_CONTAINER_NAME" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
      | awk -F= '$1=="HTTP_PROXY"{print substr($0, index($0,"=")+1); exit}'
  )"
  healthcheck="$(docker inspect "$TEMPORAL_CONTAINER_NAME" --format '{{json .Config.Healthcheck.Test}}' 2>/dev/null || true)"
  if [[ "$db_driver" != "postgres12" || "$dynamic_config" != "config/dynamicconfig/docker.yaml" || -n "$http_proxy" || "$healthcheck" != *"hostname -i"* ]]; then
    echo "[INFO] removing temporal container with stale config"
    docker rm -f "$TEMPORAL_CONTAINER_NAME" >/dev/null
  fi
}

remove_nats_with_stale_healthcheck() {
  if ! docker ps -a --format '{{.Names}}' | rg -qx "$NATS_CONTAINER_NAME"; then
    return 0
  fi
  local healthcheck
  healthcheck="$(docker inspect "$NATS_CONTAINER_NAME" --format '{{json .Config.Healthcheck.Test}}' 2>/dev/null || true)"
  if [[ "$healthcheck" != *"nc -z 127.0.0.1 4222"* ]]; then
    echo "[INFO] removing nats container with stale healthcheck"
    docker rm -f "$NATS_CONTAINER_NAME" >/dev/null
  fi
}

start_infra() {
  ensure_compose_service_running postgres java-oj-postgres
  ensure_compose_service_running redis java-oj-redis
  remove_nats_with_stale_healthcheck
  ensure_compose_service_running nats "$NATS_CONTAINER_NAME"
  remove_temporal_with_stale_config
  ensure_compose_service_running temporal "$TEMPORAL_CONTAINER_NAME"
  ensure_compose_service_running memgraph "$MEMGRAPH_CONTAINER_NAME"
}

# alethicode-rag 单独启动：必须在 resolve_postgres_credentials 之后，否则 docker
# compose 会从 deploy/.env 读 DB_PASSWORD（一个 stale 占位值），与 java-oj-postgres
# 容器内真实 POSTGRES_PASSWORD 不一致，alethicode-rag 启动时 `password
# authentication failed for user "onlinejudge"`。镜像首次启动会触发 docker build
# （pip install LightRAG + 依赖，约 2-4 分钟）；后续重启复用已构建镜像。
start_alethicode_rag() {
  : "${OPENAI_API_KEY:?OPENAI_API_KEY is empty; check backend/.env (LLM calls will 401 silently)}"
  : "${INIT_LLM_API_KEY:?INIT_LLM_API_KEY is empty; check backend/.env (题目生成路由会 401)}"
  : "${EMBEDDING_API_KEY:?EMBEDDING_API_KEY is empty; check backend/.env (embedding 调用会 401)}"
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    echo "[ERROR] DB_PASSWORD not resolved; resolve_postgres_credentials must run first" >&2
    exit 1
  fi

  if docker ps -a --format '{{.Names}}' | rg -qx "$ALETHICODE_RAG_CONTAINER_NAME"; then
    echo "[INFO] removing existing alethicode-rag container so it picks up the resolved DB_PASSWORD"
    docker rm -f "$ALETHICODE_RAG_CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
  docker compose -f "$ROOT_DIR/deploy/docker-compose.yml" up -d --no-deps alethicode-rag
}

wait_memgraph_ready() {
  for _ in $(seq 1 60); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$MEMGRAPH_CONTAINER_NAME" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_alethicode_rag_ready() {
  wait_http_ok "http://127.0.0.1:${ALETHICODE_RAG_PORT}/health" 180 1
}

has_shell_proxy() {
  [[ -n "${HTTP_PROXY:-}" || -n "${HTTPS_PROXY:-}" || -n "${ALL_PROXY:-}" || -n "${http_proxy:-}" || -n "${https_proxy:-}" || -n "${all_proxy:-}" ]]
}

normalize_proxy_for_container() {
  local raw_value="$1"
  if [[ -z "$raw_value" ]]; then
    printf ''
    return 0
  fi
  if docker_runtime_supports_host_network; then
    printf '%s' "$raw_value"
    return 0
  fi
  printf '%s' "$raw_value" | sed -E 's#([a-zA-Z0-9+.-]+://)(127\.0\.0\.1|localhost)(:[0-9]+)#\1host.docker.internal\3#g'
}

append_no_proxy_defaults() {
  local raw_value="$1"
  local defaults="127.0.0.1,localhost,host.docker.internal"
  local value="${raw_value:-}"
  if [[ -z "$value" ]]; then
    printf '%s' "$defaults"
    return 0
  fi
  for host in 127.0.0.1 localhost host.docker.internal; do
    if [[ ",$value," != *",$host,"* ]]; then
      value="${value},${host}"
    fi
  done
  printf '%s' "$value"
}

build_proxy_env_args() {
  local -n out_ref=$1
  local http_value="${ORIGINAL_HTTP_PROXY:-}"
  local https_value="${ORIGINAL_HTTPS_PROXY:-}"
  local all_value="${ORIGINAL_ALL_PROXY:-}"
  local no_value="${ORIGINAL_NO_PROXY:-}"

  http_value="$(normalize_proxy_for_container "$http_value")"
  https_value="$(normalize_proxy_for_container "$https_value")"
  all_value="$(normalize_proxy_for_container "$all_value")"
  no_value="$(append_no_proxy_defaults "$no_value")"

  out_ref+=(-e "HTTP_PROXY=${http_value}" -e "http_proxy=${http_value}")
  out_ref+=(-e "HTTPS_PROXY=${https_value}" -e "https_proxy=${https_value}")
  out_ref+=(-e "ALL_PROXY=${all_value}" -e "all_proxy=${all_value}")
  out_ref+=(-e "NO_PROXY=${no_value}" -e "no_proxy=${no_value}")
}

parse_proxy_host_port() {
  local raw_value="$1"
  local -n out_host_ref="$2"
  local -n out_port_ref="$3"
  out_host_ref=""
  out_port_ref=""
  if [[ -z "$raw_value" ]]; then
    return 0
  fi

  local stripped="$raw_value"
  stripped="${stripped#*://}"
  stripped="${stripped#*@}"
  stripped="${stripped%%/*}"

  if [[ "$stripped" =~ ^\[([0-9a-fA-F:]+)\]:(.+)$ ]]; then
    out_host_ref="${BASH_REMATCH[1]}"
    out_port_ref="${BASH_REMATCH[2]}"
    return 0
  fi
  if [[ "$stripped" == *:* ]]; then
    out_host_ref="${stripped%:*}"
    out_port_ref="${stripped##*:}"
  else
    out_host_ref="$stripped"
  fi
}

proxy_endpoint_reachable() {
  local raw_value="$1"
  local proxy_host=""
  local proxy_port=""
  parse_proxy_host_port "$raw_value" proxy_host proxy_port
  if [[ -z "$proxy_host" || -z "$proxy_port" || ! "$proxy_port" =~ ^[0-9]+$ ]]; then
    return 1
  fi

  local check_host="$proxy_host"
  if [[ "$check_host" == "localhost" || "$check_host" == "host.docker.internal" ]]; then
    check_host="127.0.0.1"
  fi
  timeout 1 bash -c ':</dev/tcp/$1/$2' _ "$check_host" "$proxy_port" >/dev/null 2>&1
}

filter_reachable_proxy_value() {
  local name="$1"
  local raw_value="$2"
  if [[ -z "$raw_value" ]]; then
    printf ''
    return 0
  fi

  if proxy_endpoint_reachable "$raw_value"; then
    printf '%s' "$raw_value"
    return 0
  fi

  local display_value
  display_value="$(printf '%s' "$raw_value" | sed -E 's#(://)[^/@]+@#\1***@#')"
  echo "[WARN] ignoring ${name}=${display_value}: proxy endpoint is not reachable; using direct network for this value" >&2
  printf ''
}

sanitize_proxy_environment() {
  ORIGINAL_HTTP_PROXY="$(filter_reachable_proxy_value "HTTP_PROXY" "${ORIGINAL_HTTP_PROXY:-}")"
  ORIGINAL_HTTPS_PROXY="$(filter_reachable_proxy_value "HTTPS_PROXY" "${ORIGINAL_HTTPS_PROXY:-}")"
  ORIGINAL_ALL_PROXY="$(filter_reachable_proxy_value "ALL_PROXY" "${ORIGINAL_ALL_PROXY:-}")"
  HTTP_PROXY="$ORIGINAL_HTTP_PROXY"
  http_proxy="$ORIGINAL_HTTP_PROXY"
  HTTPS_PROXY="$ORIGINAL_HTTPS_PROXY"
  https_proxy="$ORIGINAL_HTTPS_PROXY"
  ALL_PROXY="$ORIGINAL_ALL_PROXY"
  all_proxy="$ORIGINAL_ALL_PROXY"
  export HTTP_PROXY http_proxy HTTPS_PROXY https_proxy ALL_PROXY all_proxy
}

docker_daemon_has_proxy() {
  docker info 2>/dev/null | rg -q 'HTTP Proxy:|HTTPS Proxy:|No Proxy:'
}

prefer_grafana_binary_runtime() {
  if has_shell_proxy && ! docker_daemon_has_proxy; then
    return 0
  fi
  return 1
}

is_grafana_healthy() {
  local code
  code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${GRAFANA_PORT}/api/health" || true)
  [[ "$code" == "200" ]]
}

is_prometheus_healthy() {
  local code
  code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PROMETHEUS_PORT}/-/healthy" || true)
  [[ "$code" == "200" ]]
}

ensure_observability_network() {
  docker network inspect "$OBSERVABILITY_NETWORK_NAME" >/dev/null 2>&1 || \
    docker network create "$OBSERVABILITY_NETWORK_NAME" >/dev/null
}

render_prometheus_local_config() {
  mkdir -p "$PROMETHEUS_CONFIG_DIR"
  local template="$OBSERVABILITY_CONFIG_DIR/prometheus/prometheus.local.yml.template"
  if [[ ! -f "$template" ]]; then
    echo "[ERROR] prometheus template missing: $template" >&2
    return 1
  fi
  local target="host.docker.internal:${BACKEND_PORT}"
  sed "s|__BACKEND_TARGET__|${target}|g" "$template" > "$PROMETHEUS_LOCAL_CONFIG"
}

start_local_prometheus() {
  if [[ "$ENABLE_GRAFANA" != "1" ]]; then
    return 0
  fi

  if ! render_prometheus_local_config; then
    return 1
  fi

  if docker ps -a --format '{{.Names}}' | rg -qx "$PROMETHEUS_CONTAINER_NAME"; then
    docker rm -f "$PROMETHEUS_CONTAINER_NAME" >/dev/null 2>&1 || true
  fi

  docker volume create java-oj-prometheus-data >/dev/null
  ensure_observability_network

  if ! docker run -d \
    --name "$PROMETHEUS_CONTAINER_NAME" \
    --restart unless-stopped \
    --network "$OBSERVABILITY_NETWORK_NAME" \
    -p "127.0.0.1:${PROMETHEUS_PORT}:9090" \
    --add-host=host.docker.internal:host-gateway \
    -v "${PROMETHEUS_LOCAL_CONFIG}:/etc/prometheus/prometheus.yml:ro" \
    -v "java-oj-prometheus-data:/prometheus" \
    -e "HTTP_PROXY=" \
    -e "HTTPS_PROXY=" \
    -e "ALL_PROXY=" \
    -e "http_proxy=" \
    -e "https_proxy=" \
    -e "all_proxy=" \
    "$PROMETHEUS_IMAGE" \
    --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/prometheus \
    --storage.tsdb.retention.time=15d \
    --web.enable-lifecycle >/dev/null; then
    return 1
  fi
  return 0
}

start_local_jaeger() {
  if [[ "$ENABLE_GRAFANA" != "1" ]]; then
    return 0
  fi
  ensure_compose_service_running jaeger "$JAEGER_CONTAINER_NAME"
}

wait_jaeger_ready() {
  if [[ "$ENABLE_GRAFANA" != "1" ]]; then
    return 0
  fi
  wait_http_ok "http://127.0.0.1:${JAEGER_UI_PORT}/" 60 1
}

wait_prometheus_ready() {
  if [[ "$ENABLE_GRAFANA" != "1" ]]; then
    return 0
  fi
  wait_http_ok "http://127.0.0.1:${PROMETHEUS_PORT}/-/healthy" 60 1
}

grafana_prometheus_url_for_runtime() {
  case "$1" in
    docker) echo "http://${PROMETHEUS_CONTAINER_NAME}:9090" ;;
    binary) echo "http://127.0.0.1:${PROMETHEUS_PORT}" ;;
    *) echo "" ;;
  esac
}

start_grafana_docker() {
  if docker ps -a --format '{{.Names}}' | rg -qx "$GRAFANA_CONTAINER_NAME"; then
    docker rm -f "$GRAFANA_CONTAINER_NAME" >/dev/null 2>&1 || true
  fi

  docker volume create java-oj-grafana-data >/dev/null
  ensure_observability_network
  if ! docker run -d \
    --name "$GRAFANA_CONTAINER_NAME" \
    --restart unless-stopped \
    --network "$OBSERVABILITY_NETWORK_NAME" \
    -p "${GRAFANA_PORT}:3000" \
    --add-host=host.docker.internal:host-gateway \
    -v "java-oj-grafana-data:/var/lib/grafana" \
    -v "${OBSERVABILITY_CONFIG_DIR}/grafana/provisioning:/etc/grafana/provisioning:ro" \
    -v "${OBSERVABILITY_CONFIG_DIR}/grafana/dashboards:/var/lib/grafana/dashboards:ro" \
    -e "GF_SECURITY_ADMIN_USER=${GRAFANA_ADMIN_USER}" \
    -e "GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}" \
    -e "GF_SECURITY_ALLOW_EMBEDDING=true" \
    -e "GF_USERS_ALLOW_SIGN_UP=false" \
    -e "GF_AUTH_ANONYMOUS_ENABLED=true" \
    -e "GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer" \
    -e "GF_SERVER_ROOT_URL=http://localhost:${GRAFANA_PORT}/" \
    -e "GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/var/lib/grafana/dashboards/alethicode-overview.json" \
    -e "PROMETHEUS_URL=$(grafana_prometheus_url_for_runtime docker)" \
    -e "ALETHICODE_DASHBOARDS_PATH=/var/lib/grafana/dashboards" \
    -e "HTTP_PROXY=" \
    -e "HTTPS_PROXY=" \
    -e "ALL_PROXY=" \
    -e "http_proxy=" \
    -e "https_proxy=" \
    -e "all_proxy=" \
    -e "NO_PROXY=localhost,127.0.0.1,host.docker.internal,prometheus" \
    -e "no_proxy=localhost,127.0.0.1,host.docker.internal,prometheus" \
    "$GRAFANA_IMAGE" >/dev/null; then
    return 1
  fi
  GRAFANA_ACTIVE_RUNTIME="docker"
  return 0
}

ensure_grafana_binary() {
  local archive_path extract_dir unpacked_dir
  archive_path="$ROOT_DIR/.runtime/grafana/grafana-v${GRAFANA_VERSION}.tar.gz"
  extract_dir="$ROOT_DIR/.runtime/grafana"
  unpacked_dir="$extract_dir/grafana-v${GRAFANA_VERSION}"
  mkdir -p "$extract_dir"

  if [[ -x "$GRAFANA_RUNTIME_DIR/bin/grafana-server" ]]; then
    return 0
  fi

  echo "[INFO] downloading grafana binary: $GRAFANA_DIST_URL"
  curl -fL --retry 3 --retry-all-errors --connect-timeout 10 --max-time 600 \
    -o "$archive_path" "$GRAFANA_DIST_URL"
  rm -rf "$unpacked_dir"
  rm -rf "$GRAFANA_RUNTIME_DIR"
  tar -xzf "$archive_path" -C "$extract_dir"
  mv "$unpacked_dir" "$GRAFANA_RUNTIME_DIR"
}

start_grafana_binary() {
  mkdir -p "$GRAFANA_DATA_DIR" "$GRAFANA_LOG_DIR" "$(dirname "$GRAFANA_PID_FILE")"

  if [[ -f "$GRAFANA_PID_FILE" ]]; then
    local existing_pid
    existing_pid=$(cat "$GRAFANA_PID_FILE" 2>/dev/null || true)
    if [[ -n "${existing_pid:-}" ]] && ps -p "$existing_pid" >/dev/null 2>&1 && is_grafana_healthy; then
      GRAFANA_ACTIVE_RUNTIME="binary"
      return 0
    fi
    rm -f "$GRAFANA_PID_FILE"
  fi

  ensure_grafana_binary

  GF_PATHS_DATA="$GRAFANA_DATA_DIR" \
  GF_PATHS_LOGS="$GRAFANA_LOG_DIR" \
  GF_PATHS_PLUGINS="$GRAFANA_DATA_DIR/plugins" \
  GF_PATHS_PROVISIONING="$OBSERVABILITY_CONFIG_DIR/grafana/provisioning" \
  GF_SERVER_HTTP_ADDR="127.0.0.1" \
  GF_SERVER_HTTP_PORT="$GRAFANA_PORT" \
  GF_SERVER_ROOT_URL="http://localhost:${GRAFANA_PORT}/" \
  GF_SECURITY_ADMIN_USER="$GRAFANA_ADMIN_USER" \
  GF_SECURITY_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" \
  GF_SECURITY_ALLOW_EMBEDDING="true" \
  GF_USERS_ALLOW_SIGN_UP="false" \
  GF_AUTH_ANONYMOUS_ENABLED="true" \
  GF_AUTH_ANONYMOUS_ORG_ROLE="Viewer" \
  GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH="$OBSERVABILITY_CONFIG_DIR/grafana/dashboards/alethicode-overview.json" \
  PROMETHEUS_URL="$(grafana_prometheus_url_for_runtime binary)" \
  ALETHICODE_DASHBOARDS_PATH="$OBSERVABILITY_CONFIG_DIR/grafana/dashboards" \
  nohup "$GRAFANA_RUNTIME_DIR/bin/grafana-server" \
    --homepath "$GRAFANA_RUNTIME_DIR" >"$GRAFANA_BINARY_LOG" 2>&1 &

  echo "$!" > "$GRAFANA_PID_FILE"
  GRAFANA_ACTIVE_RUNTIME="binary"
  return 0
}

start_local_grafana() {
  if [[ "$ENABLE_GRAFANA" != "1" ]]; then
    echo "[INFO] ENABLE_GRAFANA=0, skip Grafana startup"
    return 0
  fi

  if [[ "$GRAFANA_RUNTIME" == "docker" ]]; then
    start_grafana_docker
    return $?
  fi

  if [[ "$GRAFANA_RUNTIME" == "binary" ]]; then
    start_grafana_binary
    return $?
  fi

  if prefer_grafana_binary_runtime; then
    echo "[INFO] detected proxy mismatch (shell has proxy, docker daemon has none), prefer grafana binary runtime"
    if start_grafana_binary; then
      return 0
    fi
    echo "[WARN] grafana binary runtime failed, fallback to docker runtime"
  fi

  if start_grafana_docker; then
    return 0
  fi
  echo "[WARN] docker runtime unavailable, fallback to grafana binary runtime"
  start_grafana_binary
}

wait_grafana_ready() {
  if [[ "$ENABLE_GRAFANA" != "1" ]]; then
    return 0
  fi
  wait_http_ok "http://127.0.0.1:${GRAFANA_PORT}/api/health" 90 1
}

docker_runtime_supports_host_network() {
  [[ "$(uname -s)" == "Linux" ]]
}

resolve_postgres_credentials() {
  if docker ps -a --format '{{.Names}}' | rg -qx 'java-oj-postgres'; then
    local postgres_user_from_container postgres_password_from_container
    postgres_user_from_container="$(
      docker inspect java-oj-postgres --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
        | awk -F= '$1=="POSTGRES_USER"{print substr($0, index($0,"=")+1); exit}'
    )"
    postgres_password_from_container="$(
      docker inspect java-oj-postgres --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
        | awk -F= '$1=="POSTGRES_PASSWORD"{print substr($0, index($0,"=")+1); exit}'
    )"
    POSTGRES_USER_ACTUAL="${postgres_user_from_container:-${POSTGRES_USER:-onlinejudge}}"
    DB_PASSWORD="${postgres_password_from_container:-${DB_PASSWORD:-}}"
  else
    POSTGRES_USER_ACTUAL="${POSTGRES_USER:-onlinejudge}"
  fi
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    echo "[ERROR] DB_PASSWORD missing: neither local env nor java-oj-postgres container exposes POSTGRES_PASSWORD" >&2
    exit 1
  fi
  export POSTGRES_USER_ACTUAL
  export POSTGRES_USER="${POSTGRES_USER_ACTUAL}"
  export DB_PASSWORD
}

hash_with_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
    return 0
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
    return 0
  fi
  echo "[ERROR] missing command: sha256sum or shasum" >&2
  exit 1
}

compute_tutor_graph_source_hash() {
  local file
  local -a files=()
  while IFS= read -r -d '' file; do
    files+=("$file")
  done < <(
    cd "$ROOT_DIR" && find services/tutor-graph contracts -type f \
      ! -path 'services/tutor-graph/.venv/*' \
      ! -path 'services/tutor-graph/**/__pycache__/*' \
      ! -path 'services/tutor-graph/**/.pytest_cache/*' \
      ! -path 'services/tutor-graph/**/.mypy_cache/*' \
      -print0 | sort -z
  )
  if [[ -f "$ROOT_DIR/.dockerignore" ]]; then
    files+=(".dockerignore")
  fi

  {
    for file in "${files[@]}"; do
      printf '%s\0' "$file"
      cat "$ROOT_DIR/$file"
      printf '\0'
    done
  } | hash_with_sha256
}

read_tutor_graph_image_source_hash() {
  docker image inspect "$TUTOR_GRAPH_IMAGE" \
    --format "{{ index .Config.Labels \"$TUTOR_GRAPH_SOURCE_HASH_LABEL\" }}" 2>/dev/null || true
}

ensure_tutor_graph_image() {
  local current_source_hash existing_source_hash
  current_source_hash="$(compute_tutor_graph_source_hash)"

  if [[ "$TUTOR_GRAPH_FORCE_REBUILD" != "1" ]] && docker image inspect "$TUTOR_GRAPH_IMAGE" >/dev/null 2>&1; then
    if [[ "$TUTOR_GRAPH_DEV_MOUNT" == "1" ]]; then
      echo "[INFO] tutor-graph dev mount enabled, reusing image and mounting local source: $TUTOR_GRAPH_IMAGE"
      return 0
    fi
    existing_source_hash="$(read_tutor_graph_image_source_hash)"
    if [[ "$existing_source_hash" == "$current_source_hash" ]]; then
      return 0
    fi
    if [[ -z "$existing_source_hash" ]]; then
      echo "[INFO] tutor-graph image missing source hash label, rebuilding: $TUTOR_GRAPH_IMAGE"
    else
      echo "[INFO] tutor-graph sources changed, rebuilding: $TUTOR_GRAPH_IMAGE"
    fi
  fi
  if [[ "$TUTOR_GRAPH_FORCE_REBUILD" == "1" ]]; then
    echo "[INFO] forcing tutor-graph image rebuild: $TUTOR_GRAPH_IMAGE"
  elif ! docker image inspect "$TUTOR_GRAPH_IMAGE" >/dev/null 2>&1; then
    echo "[INFO] tutor-graph image not found, building: $TUTOR_GRAPH_IMAGE (first build may take 2-4 minutes)"
  fi
  # `--network=host` 让 build 容器内 127.0.0.1 指向宿主机 lo，这样
  # buildkit 自动注入的 HTTP_PROXY=http://127.0.0.1:* 在 apt-get / pip 里
  # 能解析到宿主机上的代理；否则 RUN 里的网络会尝试容器自身 127.0.0.1
  # 而该端口无服务，apt update 必然 fail。
  SOURCE_HASH="$current_source_hash" docker build \
    --network=host \
    -f "$ROOT_DIR/services/tutor-graph/Dockerfile" \
    --label "${TUTOR_GRAPH_SOURCE_HASH_LABEL}=${current_source_hash}" \
    -t "$TUTOR_GRAPH_IMAGE" \
    "$ROOT_DIR"
}

start_local_tutor_graph() {
  resolve_postgres_credentials
  ensure_tutor_graph_image

  if docker ps -a --format '{{.Names}}' | rg -qx "$TUTOR_GRAPH_CONTAINER_NAME"; then
    docker rm -f "$TUTOR_GRAPH_CONTAINER_NAME" >/dev/null 2>&1 || true
  fi

  # libpq URI 的 userinfo 段对 `@`, `:`, `/`, `?`, `#` 敏感；本地开发不做 percent-
  # encoding，遇到这几类字符直接 fail-fast 要求使用 libpq key=value 格式或换密码，
  # 避免把 `pass@foo` 里的 `@` 误解析成 host 分隔符导致 tutor-graph 静默失败。
  if printf '%s' "${DB_PASSWORD}" | grep -q '[@:/?#]'; then
    echo "[ERROR] DB_PASSWORD contains URI-reserved characters (@, :, /, ?, #); current start.sh wires it into a URI without percent-encoding. Rotate the password or extend the script to use libpq key=value form." >&2
    exit 1
  fi
  local db_host java_tool_base
  local -a runtime_network_args=()
  local -a runtime_endpoint_args=()
  local -a runtime_proxy_args=()
  local -a runtime_mount_args=()
  if docker_runtime_supports_host_network; then
    db_host="127.0.0.1"
    java_tool_base="http://127.0.0.1:${BACKEND_PORT}"
    runtime_network_args+=(--network=host)
  else
    db_host="host.docker.internal"
    java_tool_base="http://host.docker.internal:${BACKEND_PORT}"
    runtime_endpoint_args+=(--add-host=host.docker.internal:host-gateway)
    runtime_endpoint_args+=(-p "127.0.0.1:${TUTOR_GRAPH_PORT}:8100")
  fi
  build_proxy_env_args runtime_proxy_args
  if [[ "$TUTOR_GRAPH_DEV_MOUNT" == "1" ]]; then
    runtime_mount_args+=(-v "${ROOT_DIR}/services/tutor-graph/app:/app/app:ro")
    runtime_mount_args+=(-v "${ROOT_DIR}/contracts:/app/contracts:ro")
  fi
  local db_uri
  db_uri="postgresql://${POSTGRES_USER_ACTUAL}:${DB_PASSWORD}@${db_host}:${POSTGRES_HOST_PORT}/${POSTGRES_DB_NAME}"

  docker run -d \
    --name "$TUTOR_GRAPH_CONTAINER_NAME" \
    --restart unless-stopped \
    "${runtime_network_args[@]}" \
    "${runtime_endpoint_args[@]}" \
    "${runtime_mount_args[@]}" \
    -e "TUTOR_GRAPH_CHECKPOINTER=${TUTOR_GRAPH_CHECKPOINTER}" \
    -e "TUTOR_GRAPH_DATABASE_URI=${db_uri}" \
    -e "TUTOR_GRAPH_JAVA_TOOL_BASE_URL=${java_tool_base}" \
    -e "TUTOR_GRAPH_INTERNAL_SERVICE_KEY=${INTERNAL_SERVICE_KEY:-dev-internal-key}" \
    -e "TUTOR_GRAPH_LLM_PROVIDER=${TUTOR_GRAPH_LLM_PROVIDER:-openai}" \
    -e "TUTOR_GRAPH_LLM_MODEL=${TUTOR_GRAPH_LLM_MODEL:-${LLM_MODEL:-deepseek-v4-flash}}" \
    -e "TUTOR_GRAPH_LLM_API_KEY=${TUTOR_GRAPH_LLM_API_KEY:-${OPENAI_API_KEY:-}}" \
    -e "TUTOR_GRAPH_LLM_BASE_URL=${TUTOR_GRAPH_LLM_BASE_URL:-${LLM_BASE_URL:-https://api.deepseek.com}}" \
    -e "TUTOR_GRAPH_LLM_TEMPERATURE=${TUTOR_GRAPH_LLM_TEMPERATURE:-0.3}" \
    -e "TUTOR_GRAPH_VISUALIZE_TIMEOUT_SECONDS=${TUTOR_GRAPH_VISUALIZE_TIMEOUT_SECONDS:-${LLM_API_TIMEOUT_SECONDS:-150}}" \
    -e "TUTOR_GRAPH_REACT_ENABLED=${TUTOR_GRAPH_REACT_ENABLED:-false}" \
    -e "LANGFUSE_BASE_URL=${LANGFUSE_BASE_URL:-}" \
    -e "LANGFUSE_PUBLIC_KEY=${LANGFUSE_PUBLIC_KEY:-}" \
    -e "LANGFUSE_SECRET_KEY=${LANGFUSE_SECRET_KEY:-}" \
    -e "LANGFUSE_TRACING_ENVIRONMENT=${LANGFUSE_TRACING_ENVIRONMENT:-production}" \
    -e "OTEL_EXPORTER_OTLP_ENDPOINT=" \
    "${runtime_proxy_args[@]}" \
    "$TUTOR_GRAPH_IMAGE" >/dev/null
}

wait_tutor_graph_ready() {
  wait_http_ok "http://127.0.0.1:${TUTOR_GRAPH_PORT}/health" 90 1
}

start_local_judge() {
  local test_case_dir log_dir run_dir token
  test_case_dir="${TEST_CASE_DIR:-$ROOT_DIR/deploy/data/test_case}"
  log_dir="$ROOT_DIR/deploy/data/judge_server/log"
  run_dir="$ROOT_DIR/deploy/data/judge_server/run"
  token="${JUDGE_SERVER_TOKEN:-dev-judge-token-change-me}"

  mkdir -p "$test_case_dir" "$log_dir" "$run_dir"

  if docker ps -a --format '{{.Names}}' | rg -qx "$JUDGE_CONTAINER_NAME"; then
    docker rm -f "$JUDGE_CONTAINER_NAME" >/dev/null 2>&1 || true
  fi

  docker run -d \
    --name "$JUDGE_CONTAINER_NAME" \
    --restart unless-stopped \
    --add-host=host.docker.internal:host-gateway \
    -p "${JUDGE_PORT}:8080" \
    -v "${test_case_dir}:/test_case:ro" \
    -v "${log_dir}:/log" \
    -v "${run_dir}:/judger" \
    -e "SERVICE_URL=http://127.0.0.1:${JUDGE_PORT}" \
    -e "BACKEND_URL=http://host.docker.internal:${BACKEND_PORT}/api/judge-server-heartbeat/" \
    -e "TOKEN=${token}" \
    -e "HTTP_PROXY=" \
    -e "HTTPS_PROXY=" \
    -e "ALL_PROXY=" \
    -e "http_proxy=" \
    -e "https_proxy=" \
    -e "all_proxy=" \
    "$JUDGE_IMAGE" >/dev/null
}

wait_judge_heartbeat() {
  local retries="${1:-45}" interval="${2:-1}" count
  for _ in $(seq 1 "$retries"); do
    count=$(
      docker exec java-oj-postgres \
        psql -U onlinejudge -d alethicode -tAc \
        "select count(*) from judge_server where service_url = 'http://127.0.0.1:${JUDGE_PORT}' and last_heartbeat > now() - interval '30 seconds'" \
        2>/dev/null | tr -d '[:space:]'
    ) || true
    if [[ "$count" =~ ^[0-9]+$ ]] && [[ "$count" -ge 1 ]]; then
      return 0
    fi
    sleep "$interval"
  done
  return 1
}

wait_judge_container_healthy() {
  local retries="${1:-60}" interval="${2:-1}" state
  for _ in $(seq 1 "$retries"); do
    state=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$JUDGE_CONTAINER_NAME" 2>/dev/null || true)
    if [[ "$state" == "healthy" || "$state" == "running" ]]; then
      return 0
    fi
    sleep "$interval"
  done
  return 1
}

cleanup() {
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    local pid
    pid=$(cat "$BACKEND_PID_FILE")
    if ps -p "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      pkill -P "$pid" >/dev/null 2>&1 || true
    fi
    rm -f "$BACKEND_PID_FILE"
  fi
}

clear_stale_backend_pid() {
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    local pid
    pid=$(cat "$BACKEND_PID_FILE" 2>/dev/null || true)
    if [[ -n "$pid" ]] && ps -p "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      pkill -P "$pid" >/dev/null 2>&1 || true
      sleep 1
    fi
    rm -f "$BACKEND_PID_FILE"
  fi
}

bootstrap_runtime_path
ensure_java_home

require_cmd docker
require_cmd java
require_cmd mvn
require_cmd node
require_cmd npm
require_cmd curl
require_cmd ss
require_cmd rg

ensure_node_version

load_env_file "$ROOT_DIR/deploy/.env"
load_env_file "$ROOT_DIR/backend/.env"
ORIGINAL_HTTP_PROXY="${HTTP_PROXY:-${http_proxy:-}}"
ORIGINAL_HTTPS_PROXY="${HTTPS_PROXY:-${https_proxy:-}}"
ORIGINAL_ALL_PROXY="${ALL_PROXY:-${all_proxy:-}}"
ORIGINAL_NO_PROXY="${NO_PROXY:-${no_proxy:-}}"
sanitize_proxy_environment

# 安全提示：检测是否仍在使用开发环境的占位凭证，如果是则明确警告，
# 避免未来误把这些默认值带到生产环境（参见 deploy/.env.example）。
warn_default_secret() {
  local var_name="$1" default_value="$2" label="$3"
  if [[ "${!var_name:-}" == "$default_value" ]] || [[ -z "${!var_name:-}" ]]; then
    echo "[WARN] ${label} 正在使用开发占位值，生产环境务必在 deploy/.env 中覆盖 ${var_name}" >&2
  fi
}
warn_default_secret GRAFANA_ADMIN_PASSWORD "admin" "Grafana 管理员密码"
warn_default_secret JUDGE_SERVER_TOKEN "dev-judge-token-change-me" "Judge Server Token"

echo "[INFO] LLM model: ${LLM_MODEL:-deepseek-v4-flash}"
echo "[INFO] LLM base: ${LLM_BASE_URL:-https://api.deepseek.com}"
echo "[INFO] MAVEN_OPTS: ${DEFAULT_MAVEN_OPTS}"
echo "[INFO] NODE_OPTIONS: ${DEFAULT_NODE_OPTIONS}"

clear_stale_backend_pid

check_port_free "$FRONTEND_PORT"
check_port_free "$BACKEND_PORT"
if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx "$TUTOR_GRAPH_CONTAINER_NAME"; then
  echo "[INFO] removing existing tutor-graph container to refresh local runtime"
  docker rm -f "$TUTOR_GRAPH_CONTAINER_NAME" >/dev/null 2>&1 || true
fi
check_port_free "$TUTOR_GRAPH_PORT"

echo "[INFO] starting infra (postgres + redis + nats + temporal)..."
start_infra
resolve_postgres_credentials
start_alethicode_rag
if ! wait_nats_ready; then
  echo "[ERROR] nats did not become ready on ${NATS_MONITOR_PORT}" >&2
  docker logs "$NATS_CONTAINER_NAME" --tail 100 >&2 || true
  cleanup
  exit 1
fi
echo "[OK] nats ready: nats://127.0.0.1:${NATS_PORT} (monitor http://127.0.0.1:${NATS_MONITOR_PORT})"
if ! wait_temporal_ready; then
  echo "[ERROR] temporal did not become ready on ${TEMPORAL_PORT}" >&2
  docker logs "$TEMPORAL_CONTAINER_NAME" --tail 100 >&2 || true
  cleanup
  exit 1
fi
echo "[OK] temporal ready: 127.0.0.1:${TEMPORAL_PORT}"

if ! wait_memgraph_ready; then
  echo "[ERROR] memgraph did not become ready on ${MEMGRAPH_PORT}" >&2
  docker logs "$MEMGRAPH_CONTAINER_NAME" --tail 100 >&2 || true
  cleanup
  exit 1
fi
echo "[OK] memgraph ready: bolt://127.0.0.1:${MEMGRAPH_PORT}"

if ! wait_alethicode_rag_ready; then
  echo "[ERROR] alethicode-rag did not become ready on ${ALETHICODE_RAG_PORT}" >&2
  docker logs "$ALETHICODE_RAG_CONTAINER_NAME" --tail 120 >&2 || true
  cleanup
  exit 1
fi
echo "[OK] alethicode-rag ready: http://127.0.0.1:${ALETHICODE_RAG_PORT}"

if [[ "$ENABLE_GRAFANA" == "1" ]]; then
  echo "[INFO] starting local jaeger..."
  if ! start_local_jaeger; then
    echo "[ERROR] jaeger failed to start: ${JAEGER_CONTAINER_NAME}" >&2
    docker logs "$JAEGER_CONTAINER_NAME" --tail 100 >&2 || true
    cleanup
    exit 1
  fi
  if ! wait_jaeger_ready; then
    echo "[ERROR] jaeger did not become ready on ${JAEGER_UI_PORT}" >&2
    docker logs "$JAEGER_CONTAINER_NAME" --tail 100 >&2 || true
    cleanup
    exit 1
  fi
  echo "[OK] jaeger ready: http://127.0.0.1:${JAEGER_UI_PORT}"

  echo "[INFO] starting local prometheus..."
  if ! start_local_prometheus; then
    echo "[ERROR] prometheus failed to start: ${PROMETHEUS_CONTAINER_NAME}" >&2
    docker logs "$PROMETHEUS_CONTAINER_NAME" --tail 100 >&2 || true
    cleanup
    exit 1
  fi
  if ! wait_prometheus_ready; then
    echo "[ERROR] prometheus did not become ready on ${PROMETHEUS_PORT}" >&2
    docker logs "$PROMETHEUS_CONTAINER_NAME" --tail 100 >&2 || true
    cleanup
    exit 1
  fi
  echo "[OK] prometheus ready: http://127.0.0.1:${PROMETHEUS_PORT} (scraping host.docker.internal:${BACKEND_PORT})"
fi

echo "[INFO] starting local grafana..."
start_local_grafana
if ! wait_grafana_ready; then
  echo "[ERROR] grafana did not become ready on ${GRAFANA_PORT}" >&2
  if [[ "$GRAFANA_ACTIVE_RUNTIME" == "docker" ]] && docker ps -a --format '{{.Names}}' | rg -qx "$GRAFANA_CONTAINER_NAME"; then
    docker logs "$GRAFANA_CONTAINER_NAME" --tail 100 >&2 || true
  elif [[ "$GRAFANA_ACTIVE_RUNTIME" == "binary" ]] && [[ -f "$GRAFANA_BINARY_LOG" ]]; then
    tail -n 100 "$GRAFANA_BINARY_LOG" >&2 || true
  fi
  cleanup
  exit 1
fi
if [[ "$ENABLE_GRAFANA" == "1" ]]; then
  echo "[OK] grafana ready: http://127.0.0.1:${GRAFANA_PORT} (runtime=${GRAFANA_ACTIVE_RUNTIME:-unknown}, user=${GRAFANA_ADMIN_USER})"
  echo "[OK] dashboard: http://127.0.0.1:${GRAFANA_PORT}/d/alethicode-overview"
fi

cd "$ROOT_DIR/backend"
: > "$BACKEND_LOG"
echo "[INFO] starting backend on ${BACKEND_PORT}..."
# 注意：生产部署必须使用 `prod` profile，
# 它会强制 alethicode.system.force-https=true（SEC-5）。
# 本地 start.sh 走 dev profile，仅用于开发自测。
BACKEND_HTTP_PROXY="${ORIGINAL_HTTP_PROXY:-}"
BACKEND_HTTPS_PROXY="${ORIGINAL_HTTPS_PROXY:-}"
BACKEND_ALL_PROXY="${ORIGINAL_ALL_PROXY:-}"
BACKEND_NO_PROXY="$(append_no_proxy_defaults "${ORIGINAL_NO_PROXY:-}")"
BACKEND_PROXY_URL="${BACKEND_HTTPS_PROXY:-$BACKEND_HTTP_PROXY}"
BACKEND_PROXY_HOST=""
BACKEND_PROXY_PORT=""
parse_proxy_host_port "$BACKEND_PROXY_URL" BACKEND_PROXY_HOST BACKEND_PROXY_PORT
BACKEND_JVM_PROXY_ARGS="-Dhttp.nonProxyHosts=localhost|127.*|[::1]|host.docker.internal"
if [[ -n "$BACKEND_PROXY_HOST" && -n "$BACKEND_PROXY_PORT" ]]; then
  BACKEND_JVM_PROXY_ARGS="${BACKEND_JVM_PROXY_ARGS} -Dhttp.proxyHost=${BACKEND_PROXY_HOST} -Dhttp.proxyPort=${BACKEND_PROXY_PORT} -Dhttps.proxyHost=${BACKEND_PROXY_HOST} -Dhttps.proxyPort=${BACKEND_PROXY_PORT}"
  echo "[INFO] backend outbound proxy: ${BACKEND_PROXY_HOST}:${BACKEND_PROXY_PORT} (NO_PROXY=${BACKEND_NO_PROXY})"
else
  echo "[INFO] backend outbound proxy: direct (NO_PROXY=${BACKEND_NO_PROXY})"
fi
nohup env MAVEN_OPTS="$DEFAULT_MAVEN_OPTS" \
HTTP_PROXY="${BACKEND_HTTP_PROXY}" \
http_proxy="${BACKEND_HTTP_PROXY}" \
HTTPS_PROXY="${BACKEND_HTTPS_PROXY}" \
https_proxy="${BACKEND_HTTPS_PROXY}" \
ALL_PROXY="${BACKEND_ALL_PROXY}" \
all_proxy="${BACKEND_ALL_PROXY}" \
NO_PROXY="${BACKEND_NO_PROXY}" \
no_proxy="${BACKEND_NO_PROXY}" \
NATS_URL="nats://127.0.0.1:${NATS_PORT}" \
RAG_SERVICE_URL="http://127.0.0.1:${ALETHICODE_RAG_PORT}" \
JUDGE_DISPATCH_TRANSPORT="${JUDGE_DISPATCH_TRANSPORT:-nats}" \
TEMPORAL_ENABLED="${TEMPORAL_ENABLED:-true}" \
TEMPORAL_TARGET="${TEMPORAL_TARGET:-127.0.0.1:${TEMPORAL_PORT}}" \
TEMPORAL_NAMESPACE="${TEMPORAL_NAMESPACE:-default}" \
TEMPORAL_TASK_QUEUE="${TEMPORAL_TASK_QUEUE:-language-pack-pipeline}" \
OTEL_EXPORTER_OTLP_ENDPOINT="${LOCAL_OTEL_EXPORTER_OTLP_ENDPOINT:-http://127.0.0.1:4318/v1/traces}" \
LANGFUSE_BASE_URL="${LANGFUSE_BASE_URL:-}" \
LANGFUSE_PUBLIC_KEY="${LANGFUSE_PUBLIC_KEY:-}" \
LANGFUSE_SECRET_KEY="${LANGFUSE_SECRET_KEY:-}" \
LANGFUSE_TRACING_ENVIRONMENT="${LANGFUSE_TRACING_ENVIRONMENT:-production}" \
mvn -q spring-boot:run -Dmaven.test.skip=true -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="${BACKEND_JVM_PROXY_ARGS}" \
  -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}" >"$BACKEND_LOG" 2>&1 < /dev/null &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$BACKEND_PID_FILE"

if ! wait_http_ok "http://127.0.0.1:${BACKEND_PORT}/api/website" 90 1; then
  echo "[ERROR] backend did not become ready on ${BACKEND_PORT}" >&2
  echo "[INFO] backend log: $BACKEND_LOG" >&2
  cleanup
  exit 1
fi

echo "[OK] backend ready: http://127.0.0.1:${BACKEND_PORT}"
echo "[INFO] backend log: $BACKEND_LOG"

# Register EXIT/INT/TERM trap right after the backend is confirmed ready. Any
# later failure (judge / tutor-graph / frontend bootstrap) then still cleans up
# the mvn child process instead of leaving a stray Java listener on BACKEND_PORT.
trap cleanup EXIT INT TERM

echo "[INFO] starting local judge container..."
start_local_judge
if ! wait_judge_container_healthy 60 1; then
  echo "[ERROR] judge container did not become healthy: ${JUDGE_CONTAINER_NAME}" >&2
  docker logs "$JUDGE_CONTAINER_NAME" --tail 100 >&2 || true
  cleanup
  exit 1
fi
if ! wait_judge_heartbeat 45 1; then
  echo "[ERROR] judge heartbeat not received by backend (container: ${JUDGE_CONTAINER_NAME})" >&2
  docker logs "$JUDGE_CONTAINER_NAME" --tail 100 >&2 || true
  cleanup
  exit 1
fi
echo "[OK] judge ready: http://127.0.0.1:${JUDGE_PORT} (heartbeat alive)"

echo "[INFO] starting local tutor-graph container..."
start_local_tutor_graph
if ! wait_tutor_graph_ready; then
  echo "[ERROR] tutor-graph did not become ready on ${TUTOR_GRAPH_PORT}" >&2
  docker logs "$TUTOR_GRAPH_CONTAINER_NAME" --tail 120 >&2 || true
  cleanup
  exit 1
fi
echo "[OK] tutor-graph ready: http://127.0.0.1:${TUTOR_GRAPH_PORT} (checkpointer=${TUTOR_GRAPH_CHECKPOINTER})"

cd "$ROOT_DIR/frontend"
if [[ "$SKIP_FRONTEND" == "1" ]]; then
  echo "[OK] SKIP_FRONTEND=1, backend is running on http://127.0.0.1:${BACKEND_PORT}"
  wait
fi

if [[ ! -d node_modules ]]; then
  npm ci
fi

echo "[OK] frontend dev will listen on http://127.0.0.1:${FRONTEND_PORT}"
echo "[INFO] proxy target: http://127.0.0.1:${BACKEND_PORT}"

PORT="$FRONTEND_PORT" \
API_TARGET="http://127.0.0.1:${BACKEND_PORT}" \
NODE_OPTIONS="$DEFAULT_NODE_OPTIONS" \
npm run dev
