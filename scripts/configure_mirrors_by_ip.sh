#!/usr/bin/env bash
set -euo pipefail

PASSWORD="${1:-}"
if [[ -z "$PASSWORD" ]]; then
  echo "Usage: $0 <sudo_password>" >&2
  exit 1
fi

DOCKER_MIRRORS=(
  "https://docker.m.daocloud.io"
  "https://dockerproxy.com"
)

# 配置 Docker daemon 镜像源。
TMP_DAEMON=$(mktemp)
cat > "$TMP_DAEMON" <<JSON
{
  "registry-mirrors": [
    "${DOCKER_MIRRORS[0]}",
    "${DOCKER_MIRRORS[1]}"
  ],
  "dns": ["223.5.5.5", "119.29.29.29", "8.8.8.8"]
}
JSON

echo "$PASSWORD" | sudo -S mkdir -p /etc/docker
echo "$PASSWORD" | sudo -S cp "$TMP_DAEMON" /etc/docker/daemon.json
echo "$PASSWORD" | sudo -S systemctl daemon-reload
echo "$PASSWORD" | sudo -S systemctl restart docker

# 配置 npm 镜像源。
npm config set registry https://registry.npmmirror.com

# 配置用户级 Maven 镜像。
mkdir -p "$HOME/.m2"
cat > "$HOME/.m2/settings.xml" <<'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <name>Aliyun Maven Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
XML

rm -f "$TMP_DAEMON"

echo "[OK] Mirrors configured: docker(daocloud+dockerproxy), npm(npmmirror), maven(aliyun)"
