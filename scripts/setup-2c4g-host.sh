#!/usr/bin/env bash
# 用法（必须 root 或 sudo）：
#   sudo bash scripts/setup-2c4g-host.sh
#
# 作用：把 2 核 4 GiB 云主机调到能稳定承载 100-150 并发的状态。
#       完成 3 件事：
#         1) 创建 4 GiB swap 文件（持久化）
#         2) 写 /etc/sysctl.d/99-alethicode-2c4g.conf 调内核参数
#         3) 写 /etc/docker/daemon.json 加日志轮转 + ulimit
#
# 此脚本是幂等的：重复执行不会创建多个 swap，不会重复 sysctl 项。
# 适用：Ubuntu 22.04+, Debian 11+, CentOS Stream 9 / Rocky 9。
# WSL2 用户可执行此脚本（共享宿主 Linux 内核），但 swap 通常宿主已经分配。

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "[ERROR] 必须使用 sudo 或 root 运行本脚本" >&2
  exit 1
fi

# ============================================================================
# Step 1: Swap 4 GiB
# ============================================================================
SWAP_FILE="/swapfile"
SWAP_SIZE_GB=4

setup_swap() {
  if swapon --show=NAME --noheadings 2>/dev/null | grep -qx "$SWAP_FILE"; then
    echo "[SKIP] swap already active at $SWAP_FILE"
    return 0
  fi
  if [[ -f "$SWAP_FILE" ]]; then
    echo "[INFO] $SWAP_FILE exists but not active; trying swapon"
    if swapon "$SWAP_FILE" 2>/dev/null; then
      echo "[OK] reactivated existing swap"
      return 0
    fi
    echo "[WARN] cannot swapon existing $SWAP_FILE, will recreate"
    rm -f "$SWAP_FILE"
  fi
  echo "[INFO] creating ${SWAP_SIZE_GB} GiB swap at $SWAP_FILE..."
  if ! fallocate -l "${SWAP_SIZE_GB}G" "$SWAP_FILE" 2>/dev/null; then
    dd if=/dev/zero of="$SWAP_FILE" bs=1M count=$((SWAP_SIZE_GB*1024)) status=progress
  fi
  chmod 600 "$SWAP_FILE"
  mkswap "$SWAP_FILE"
  swapon "$SWAP_FILE"
  if ! grep -q "^${SWAP_FILE} " /etc/fstab; then
    echo "${SWAP_FILE} none swap sw 0 0" >> /etc/fstab
    echo "[OK] swap entry added to /etc/fstab"
  fi
  echo "[OK] swap activated"
}

# ============================================================================
# Step 2: sysctl 内核调优
# ============================================================================
SYSCTL_FILE="/etc/sysctl.d/99-alethicode-2c4g.conf"

setup_sysctl() {
  cat > "$SYSCTL_FILE" <<'EOF'
# Alethicode 2C4G 容量优化（2026-04-30）
# 仅在内存压力大时使用 swap（默认 60 太激进）
vm.swappiness=10
# 允许 Docker 启动时的内存预留过量（关闭后部分容器会偶尔 ENOMEM）
vm.overcommit_memory=1
vm.overcommit_ratio=80
# 写回策略：页脏 5% 后台写、15% 强制写
vm.dirty_background_ratio=5
vm.dirty_ratio=15
# 文件描述符上限：nginx + backend + 多容器都吃 fd
fs.file-max=200000
# 网络队列与 TIME_WAIT 重用：高并发短连接场景
net.core.somaxconn=1024
net.ipv4.tcp_max_syn_backlog=2048
net.ipv4.tcp_fin_timeout=15
net.ipv4.tcp_tw_reuse=1
EOF
  echo "[OK] wrote $SYSCTL_FILE"
  sysctl --system >/dev/null
  echo "[OK] sysctl reloaded"
}

# ============================================================================
# Step 3: Docker daemon 日志轮转 + ulimit
# ============================================================================
DOCKER_DAEMON_FILE="/etc/docker/daemon.json"

setup_docker_daemon() {
  mkdir -p /etc/docker
  if [[ -f "$DOCKER_DAEMON_FILE" ]]; then
    cp "$DOCKER_DAEMON_FILE" "${DOCKER_DAEMON_FILE}.bak.$(date +%s)"
    echo "[INFO] backed up existing $DOCKER_DAEMON_FILE"
  fi
  cat > "$DOCKER_DAEMON_FILE" <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 65536,
      "Soft": 65536
    }
  },
  "live-restore": true
}
EOF
  echo "[OK] wrote $DOCKER_DAEMON_FILE"
  if systemctl is-active --quiet docker 2>/dev/null; then
    echo "[INFO] reloading docker daemon..."
    systemctl reload docker 2>/dev/null || systemctl restart docker
    echo "[OK] docker reloaded"
  else
    echo "[WARN] docker is not active under systemd; please reload it manually:"
    echo "       systemctl restart docker (Linux) 或 service docker restart"
  fi
}

echo "=========================================="
echo "  Alethicode 2C4G host setup"
echo "=========================================="
setup_swap
setup_sysctl
setup_docker_daemon

echo ""
echo "=========================================="
echo "  Verification"
echo "=========================================="
swapon --show
echo ""
sysctl vm.swappiness vm.overcommit_memory net.core.somaxconn
echo ""
echo "[DONE] 2C4G host setup complete. 现在可以:"
echo "       cd deploy && docker compose up -d"
