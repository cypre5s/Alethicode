#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACKAGE_NAME="alethicode_competition"
RELEASE_ROOT="${RELEASE_ROOT:-$ROOT_DIR/release/competition_installer}"
PAYLOAD_ROOT="$RELEASE_ROOT/payload"
PACKAGE_ROOT="$PAYLOAD_ROOT/$PACKAGE_NAME"
PAYLOAD_ARCHIVE="$RELEASE_ROOT/${PACKAGE_NAME}.tar.gz"
INSTALLER_PATH="$RELEASE_ROOT/Alethicode-Installer.run"
BUNDLE_IMAGES="${BUNDLE_IMAGES:-0}"
IMAGE_ARCHIVE="$PACKAGE_ROOT/offline_images/alethicode-images.tar"

show_help() {
  cat <<'EOF'
Usage: scripts/competition/build_competition_installer.sh

Build the Ubuntu/WSL2 competition installer as a self-extracting .run file.

Environment variables:
  RELEASE_ROOT   Output directory. Default: release/competition_installer
  BUNDLE_IMAGES  When set to 1, export docker images into offline_images/alethicode-images.tar
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] missing command: $1" >&2
    exit 1
  fi
}

assert_image_exists() {
  local image="$1"
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "[ERROR] expected docker image missing after build: $image" >&2
    exit 1
  fi
}

prepare_dirs() {
  rm -rf "$RELEASE_ROOT"
  mkdir -p "$PACKAGE_ROOT/bin" "$PACKAGE_ROOT/installer" "$PACKAGE_ROOT/offline_images"
  mkdir -p "$PACKAGE_ROOT/project/backend" "$PACKAGE_ROOT/project/frontend"
  mkdir -p "$PACKAGE_ROOT/project/deploy/nginx" "$PACKAGE_ROOT/project/scripts"
}

copy_tree_filtered() {
  local src="$1"
  local dest="$2"
  shift 2

  mkdir -p "$dest"
  tar -C "$src" "$@" -cf - . | tar -C "$dest" -xf -
}

copy_backend_project() {
  copy_tree_filtered \
    "$ROOT_DIR/backend" \
    "$PACKAGE_ROOT/project/backend" \
    --exclude='target' \
    --exclude='data' \
    --exclude='.env' \
    --exclude='.start-backend.log' \
    --exclude='.restart-backend.log'
}

copy_frontend_project() {
  copy_tree_filtered \
    "$ROOT_DIR/frontend" \
    "$PACKAGE_ROOT/project/frontend" \
    --exclude='node_modules' \
    --exclude='dist' \
    --exclude='test-results' \
    --exclude='.start-frontend.log' \
    --exclude='.restart-frontend.log' \
    --exclude='.vite-dev.log' \
    --exclude='.vite-dev.pid'
}

copy_deploy_project() {
  cp "$ROOT_DIR/deploy/docker-compose.yml" "$PACKAGE_ROOT/project/deploy/docker-compose.yml"
  cp "$ROOT_DIR/deploy/frontend.Dockerfile" "$PACKAGE_ROOT/project/deploy/frontend.Dockerfile"
  cp "$ROOT_DIR/deploy/frontend-nginx.conf" "$PACKAGE_ROOT/project/deploy/frontend-nginx.conf"
  cp "$ROOT_DIR/deploy/.env.example" "$PACKAGE_ROOT/project/deploy/.env.example"
  cp "$ROOT_DIR/deploy/nginx/nginx.conf" "$PACKAGE_ROOT/project/deploy/nginx/nginx.conf"
  cp "$ROOT_DIR/scripts/m12/m12_smoke.sh" "$PACKAGE_ROOT/project/scripts/m12_smoke.sh"
}

copy_runtime_assets() {
  cp "$ROOT_DIR/packaging/competition_installer/README.md" "$PACKAGE_ROOT/README.md"
  cp "$ROOT_DIR/packaging/competition_installer/post_install.sh" "$PACKAGE_ROOT/installer/post_install.sh"
  cp "$ROOT_DIR/packaging/competition_installer/start.sh" "$PACKAGE_ROOT/bin/start.sh"
  cp "$ROOT_DIR/packaging/competition_installer/stop.sh" "$PACKAGE_ROOT/bin/stop.sh"
  cp "$ROOT_DIR/packaging/competition_installer/status.sh" "$PACKAGE_ROOT/bin/status.sh"
  cp "$ROOT_DIR/packaging/competition_installer/smoke.sh" "$PACKAGE_ROOT/bin/smoke.sh"
}

bundle_offline_images() {
  if [[ "$BUNDLE_IMAGES" != "1" ]]; then
    return 0
  fi

  require_cmd docker
  echo "[INFO] building backend/frontend images for offline bundle..."
  docker compose --env-file "$ROOT_DIR/deploy/.env.example" -f "$ROOT_DIR/deploy/docker-compose.yml" build backend frontend
  assert_image_exists "alethicode-java-backend"
  assert_image_exists "alethicode-java-frontend"

  echo "[INFO] pulling base images for offline bundle..."
  docker pull pgvector/pgvector:pg16
  docker pull redis:7
  docker pull registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1

  echo "[INFO] exporting offline images to $IMAGE_ARCHIVE"
  docker save -o "$IMAGE_ARCHIVE" \
    pgvector/pgvector:pg16 \
    redis:7 \
    registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1 \
    alethicode-java-backend \
    alethicode-java-frontend
}

create_payload_archive() {
  tar -C "$PAYLOAD_ROOT" -czf "$PAYLOAD_ARCHIVE" "$PACKAGE_NAME"
}

write_installer_stub() {
  cat >"$INSTALLER_PATH" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

show_help() {
  cat <<'HELP'
Usage: Alethicode-Installer.run [target_dir]

Install Alethicode into the target directory.
Default target directory:
  ~/.local/share/alethicode-competition

After installation, start the system with:
  ./alethicode_competition/bin/start.sh

The running service will be available at:
  http://127.0.0.1:18080
HELP
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

PACKAGE_NAME="alethicode_competition"
TARGET_DIR="${1:-$HOME/.local/share/alethicode-competition}"
PAYLOAD_LINE="$(awk '/^__ARCHIVE_BELOW__$/ { print NR + 1; exit 0 }' "$0")"

if [[ -z "$PAYLOAD_LINE" ]]; then
  echo "[ERROR] installer payload marker not found" >&2
  exit 1
fi

if [[ -e "$TARGET_DIR" ]] && [[ -n "$(find "$TARGET_DIR" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
  echo "[ERROR] target directory is not empty: $TARGET_DIR" >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
tail -n +"$PAYLOAD_LINE" "$0" | tar -xz -C "$TARGET_DIR"
"$TARGET_DIR/$PACKAGE_NAME/installer/post_install.sh" "$TARGET_DIR/$PACKAGE_NAME"

echo "[OK] Alethicode installed to $TARGET_DIR/$PACKAGE_NAME"
echo "[NEXT] Start with: $TARGET_DIR/$PACKAGE_NAME/bin/start.sh"
exit 0
__ARCHIVE_BELOW__
EOF
}

append_payload_to_installer() {
  cat "$PAYLOAD_ARCHIVE" >>"$INSTALLER_PATH"
  chmod +x "$INSTALLER_PATH"
}

main() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    show_help
    exit 0
  fi

  require_cmd tar
  require_cmd cp
  require_cmd awk
  require_cmd tail

  prepare_dirs
  copy_backend_project
  copy_frontend_project
  copy_deploy_project
  copy_runtime_assets
  bundle_offline_images
  create_payload_archive
  write_installer_stub
  append_payload_to_installer

  echo "[OK] installer created: $INSTALLER_PATH"
}

main "$@"
