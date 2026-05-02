#!/usr/bin/env bash
# ============================================================
# 本地打包 NFK 并上传到 AutoDL
#
# 用法:
#   bash research/nfk/upload_autodl.sh <autodl-ssh-address>
#
# 示例:
#   bash research/nfk/upload_autodl.sh root@region-1.autodl.pro:12345
#
# 前提:
#   - 已在 AutoDL 创建实例并获取 SSH 地址
#   - 本地已配置 SSH 密钥或知道密码
#   - AutoDL 镜像选择: PyTorch 2.1+ / CUDA 12.1 / Python 3.10+
#   - 推荐 GPU: RTX 4090 (24GB) 或 RTX 3090 (24GB)
# ============================================================
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "用法: $0 <autodl-ssh-address>"
    echo "示例: $0 root@region-1.autodl.pro:12345"
    echo ""
    echo "AutoDL 创建实例步骤:"
    echo "  1. 登录 https://www.autodl.com"
    echo "  2. 租用实例 → 选择 GPU (推荐 RTX 4090)"
    echo "  3. 镜像选择: PyTorch → 2.1.0 → CUDA 12.1 → Python 3.10"
    echo "  4. 创建后复制 SSH 地址"
    exit 1
fi

AUTODL_SSH="$1"
SSH_HOST="${AUTODL_SSH%%:*}"
SSH_PORT="${AUTODL_SSH##*:}"

if [ "$SSH_PORT" = "$SSH_HOST" ]; then
    SSH_PORT=22
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NFK_DIR="$ROOT_DIR/nfk"
PACK_DIR="/tmp/nfk_autodl_pack"
ARCHIVE="/tmp/nfk_autodl.tar.gz"

echo "[1/4] 打包 NFK 训练代码..."
rm -rf "$PACK_DIR"
mkdir -p "$PACK_DIR/nfk"

cp -r "$NFK_DIR/models" "$PACK_DIR/nfk/"
cp -r "$NFK_DIR/data" "$PACK_DIR/nfk/"
cp -r "$NFK_DIR/training" "$PACK_DIR/nfk/"
cp -r "$NFK_DIR/evaluation" "$PACK_DIR/nfk/"
cp -r "$NFK_DIR/inference" "$PACK_DIR/nfk/"
cp -r "$NFK_DIR/utils" "$PACK_DIR/nfk/"
cp -r "$NFK_DIR/configs" "$PACK_DIR/nfk/"
cp "$NFK_DIR/__init__.py" "$PACK_DIR/nfk/"
cp "$NFK_DIR/train.py" "$PACK_DIR/nfk/"
cp "$NFK_DIR/run_local.py" "$PACK_DIR/nfk/"
cp "$NFK_DIR/autodl_train.py" "$PACK_DIR/nfk/"
cp "$NFK_DIR/demo_gpu.py" "$PACK_DIR/nfk/"
cp "$NFK_DIR/autodl_setup.sh" "$PACK_DIR/nfk/"
cp "$NFK_DIR/autodl_long_sequence.sh" "$PACK_DIR/nfk/"
cp "$NFK_DIR/requirements.txt" "$PACK_DIR/nfk/"

find "$PACK_DIR" -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
find "$PACK_DIR" -name '*.pyc' -delete 2>/dev/null || true

FILE_COUNT=$(find "$PACK_DIR" -type f | wc -l)
echo "  文件数: $FILE_COUNT"

cd /tmp
tar -czf "$ARCHIVE" -C "$PACK_DIR" nfk
ARCHIVE_SIZE=$(du -sh "$ARCHIVE" | cut -f1)
echo "  包大小: $ARCHIVE_SIZE"

echo ""
echo "[2/4] 上传到 AutoDL ($SSH_HOST:$SSH_PORT)..."
scp -P "$SSH_PORT" "$ARCHIVE" "$SSH_HOST":/root/nfk_autodl.tar.gz

echo ""
echo "[3/4] 远程解压..."
ssh -p "$SSH_PORT" "$SSH_HOST" "cd /root && tar -xzf nfk_autodl.tar.gz && rm nfk_autodl.tar.gz && echo '解压完成: /root/nfk/'"

echo ""
echo "[4/4] 开始训练..."
echo ""
echo "=== 可选训练模式 ==="
echo ""
echo "  快速验证 (约 10-30 分钟):"
echo "    ssh -p $SSH_PORT $SSH_HOST 'bash /root/nfk/autodl_setup.sh quick'"
echo ""
echo "  完整消融实验 (约 2-4 小时, RTX 4090):"
echo "    ssh -p $SSH_PORT $SSH_HOST 'bash /root/nfk/autodl_setup.sh full'"
echo ""
echo "  全数据集 (约 6-12 小时):"
echo "    ssh -p $SSH_PORT $SSH_HOST 'bash /root/nfk/autodl_setup.sh all'"
echo ""
echo "  后台运行 (推荐):"
echo "    ssh -p $SSH_PORT $SSH_HOST 'nohup bash /root/nfk/autodl_setup.sh full > /root/autodl-tmp/train.log 2>&1 &'"
echo ""
echo "  长轮数顺序实验 (先 2000 epoch, 后 10000 epoch):"
echo "    ssh -p $SSH_PORT $SSH_HOST 'nohup bash /root/nfk/autodl_long_sequence.sh > /root/autodl-tmp/nfk_long_sequence.nohup.log 2>&1 &'"
echo ""
echo "  查看长轮数实验总日志:"
echo "    ssh -p $SSH_PORT $SSH_HOST 'tail -f /root/autodl-tmp/nfk_long_sequence_master.log'"
echo ""
echo "  查看实时日志:"
echo "    ssh -p $SSH_PORT $SSH_HOST 'tail -f /root/autodl-tmp/nfk_outputs/training_log.jsonl'"
echo ""
echo "  下载产物:"
echo "    scp -P $SSH_PORT $SSH_HOST:/root/autodl-tmp/nfk_outputs.tar.gz ."
echo ""

read -rp "是否立即开始快速验证? (y/N) " answer
if [[ "$answer" =~ ^[Yy] ]]; then
    echo "启动快速验证..."
    ssh -t -p "$SSH_PORT" "$SSH_HOST" "bash /root/nfk/autodl_setup.sh quick"
fi

rm -rf "$PACK_DIR" "$ARCHIVE"
echo "完成!"
