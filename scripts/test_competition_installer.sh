#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_SCRIPT="$ROOT_DIR/scripts/build_competition_installer.sh"
RELEASE_ROOT="$(mktemp -d)"
INSTALL_ROOT="$(mktemp -d)"

cleanup() {
  rm -rf "$RELEASE_ROOT" "$INSTALL_ROOT"
}
trap cleanup EXIT

assert_file() {
  local target="$1"
  if [[ ! -f "$target" ]]; then
    echo "[ERROR] expected file missing: $target" >&2
    exit 1
  fi
}

assert_executable() {
  local target="$1"
  if [[ ! -x "$target" ]]; then
    echo "[ERROR] expected executable missing: $target" >&2
    exit 1
  fi
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  if ! rg -q "$pattern" "$file"; then
    echo "[ERROR] expected pattern '$pattern' not found in $file" >&2
    exit 1
  fi
}

echo "[INFO] building competition installer into $RELEASE_ROOT"
BUNDLE_IMAGES=0 RELEASE_ROOT="$RELEASE_ROOT" "$BUILD_SCRIPT"

INSTALLER="$RELEASE_ROOT/Alethicode-Installer.run"
assert_executable "$INSTALLER"

echo "[INFO] installing payload into $INSTALL_ROOT"
"$INSTALLER" "$INSTALL_ROOT"

PACKAGE_ROOT="$INSTALL_ROOT/alethicode_competition"
START_SCRIPT="$PACKAGE_ROOT/bin/start.sh"
STOP_SCRIPT="$PACKAGE_ROOT/bin/stop.sh"
STATUS_SCRIPT="$PACKAGE_ROOT/bin/status.sh"
SMOKE_SCRIPT="$PACKAGE_ROOT/bin/smoke.sh"
README_FILE="$PACKAGE_ROOT/README.md"

assert_file "$README_FILE"
assert_executable "$START_SCRIPT"
assert_executable "$STOP_SCRIPT"
assert_executable "$STATUS_SCRIPT"
assert_executable "$SMOKE_SCRIPT"

HELP_OUTPUT="$RELEASE_ROOT/start_help.txt"
"$START_SCRIPT" --help >"$HELP_OUTPUT"
assert_contains "$HELP_OUTPUT" "docker compose"
assert_contains "$HELP_OUTPUT" "127.0.0.1:18080"

echo "[OK] competition installer test passed"
