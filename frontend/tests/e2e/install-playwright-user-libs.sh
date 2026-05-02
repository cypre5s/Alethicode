#!/usr/bin/env bash
set -euo pipefail

LIBROOT="${HOME}/.local/pw-libs"
DEB_DIR="${LIBROOT}/debs"
ROOT_DIR="${LIBROOT}/root"

mkdir -p "${DEB_DIR}" "${ROOT_DIR}"
cd "${DEB_DIR}"

PACKAGES=(
  libnss3
  libnspr4
  libatk1.0-0
  libatk-bridge2.0-0
  libcups2
  libatspi2.0-0
  libxdamage1
  libgbm1
  libxkbcommon0
  libpango-1.0-0
  libcairo2
  libasound2
  libavahi-common3
  libavahi-client3
  libwayland-server0
  libthai0
  libharfbuzz0b
  libpixman-1-0
  libxcb-render0
  libdatrie1
  libgraphite2-3
)

echo "[playwright-user-libs] downloading apt packages..."
apt download "${PACKAGES[@]}"

echo "[playwright-user-libs] extracting packages..."
for deb in ./*.deb; do
  dpkg-deb -x "${deb}" "${ROOT_DIR}"
done

LIB_PATH="${ROOT_DIR}/usr/lib/x86_64-linux-gnu"
echo "[playwright-user-libs] done"
echo "export LD_LIBRARY_PATH=\"${LIB_PATH}:\${LD_LIBRARY_PATH}\""
