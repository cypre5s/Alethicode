#!/usr/bin/env bash
# ============================================================
# Alethicode-NFK AutoDL 一键训练脚本
#
# 使用方法:
#   1. 在 AutoDL 创建实例（见下方环境要求）
#   2. 上传 nfk/ 目录到 /root/nfk/
#   3. 运行: bash /root/nfk/autodl_setup.sh
#
# 环境要求:
#   - AutoDL 镜像: PyTorch 2.1+ / CUDA 12.1 / Python 3.10+
#   - 推荐 GPU: RTX 4090 (24GB) 或 RTX 3090 (24GB)
#   - 最低 GPU: RTX 3060 (12GB) — 需将 batch_size 降到 32
#   - 显存估算: 约 4-6GB (batch_size=64, max_seq_len=200)
#   - 完整消融实验时间: ~2-4h (RTX 4090) / ~4-8h (RTX 3090)
#   - 磁盘: /root/autodl-tmp 至少 5GB 可用空间
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="/root/nfk"
OUTPUT_DIR="/root/autodl-tmp/nfk_outputs"
DATASETS_DIR="${NFK_DATASETS_DIR:-/root/datasets}"
LOG_FILE="/root/autodl-tmp/nfk_setup.log"
VENV_DIR="/root/autodl-tmp/nfk_venv"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

mkdir -p "$OUTPUT_DIR" "$DATASETS_DIR" "$(dirname "$LOG_FILE")"

# ---- 1. 环境检测 ----
log "=== 环境检测 ==="
log "Python: $(python3 --version 2>&1)"
log "PyTorch: $(python3 -c 'import torch; print(torch.__version__)' 2>/dev/null || echo 'not installed')"
log "CUDA: $(python3 -c 'import torch; print(torch.version.cuda)' 2>/dev/null || echo 'N/A')"
log "GPU: $(python3 -c 'import torch; print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else "N/A")' 2>/dev/null || echo 'N/A')"
log "GPU 显存: $(python3 -c 'import torch; print(f"{torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB") if torch.cuda.is_available() else print("N/A")' 2>/dev/null || echo 'N/A')"

# ---- 2. 安装依赖 ----
log "=== 安装依赖 ==="
pip install --quiet --upgrade pip

TORCH_INSTALLED=$(python3 -c 'import torch; print("yes")' 2>/dev/null || echo "no")
if [ "$TORCH_INSTALLED" = "no" ]; then
    log "安装 PyTorch (CUDA 12.1)..."
    pip install --quiet torch torchvision --index-url https://download.pytorch.org/whl/cu121
fi

pip install --quiet \
    transformers>=4.36.0 \
    onnx>=1.15.0 \
    onnxruntime-gpu>=1.17.0 \
    scikit-learn>=1.3.0 \
    numpy>=1.24.0 \
    pandas>=2.0.0 \
    matplotlib>=3.7.0 \
    seaborn>=0.12.0 \
    tqdm>=4.65.0 \
    pyyaml>=6.0 \
    scipy>=1.10.0

log "依赖安装完成"

# 验证 GPU。
log "=== GPU 验证 ==="
python3 -c "
import torch
assert torch.cuda.is_available(), 'CUDA 不可用!'
print(f'  GPU: {torch.cuda.get_device_name(0)}')
print(f'  显存: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB')
print(f'  CUDA: {torch.version.cuda}')
print(f'  PyTorch: {torch.__version__}')
t = torch.randn(100, 100, device='cuda')
r = torch.mm(t, t)
print(f'  GPU 矩阵运算验证: 通过')
" 2>&1 | tee -a "$LOG_FILE"

# ---- 4. 运行训练 ----
log "=== 开始训练 ==="
log "工作目录: $WORK_DIR"
log "输出目录: $OUTPUT_DIR"
log "数据集目录: $DATASETS_DIR"

TRAIN_MODE="${1:-full}"
DATASET="${2:-assistments}"

cd "$WORK_DIR"

case "$TRAIN_MODE" in
    quick)
        log "模式: 快速验证 (1 fold × 1 seed)"
        python3 -u autodl_train.py \
            --dataset "$DATASET" \
            --datasets "$DATASETS_DIR" \
            --output "$OUTPUT_DIR" \
            --quick \
            2>&1 | tee -a "$LOG_FILE"
        ;;
    full)
        log "模式: 完整消融实验 (5 fold × 3 seed)"
        python3 -u autodl_train.py \
            --dataset "$DATASET" \
            --datasets "$DATASETS_DIR" \
            --output "$OUTPUT_DIR" \
            2>&1 | tee -a "$LOG_FILE"
        ;;
    all)
        log "模式: 全数据集消融 (ASSISTments + EdNet)"
        python3 -u autodl_train.py \
            --dataset all \
            --datasets "$DATASETS_DIR" \
            --output "$OUTPUT_DIR" \
            2>&1 | tee -a "$LOG_FILE"
        ;;
    *)
        log "未知模式: $TRAIN_MODE (可选: quick / full / all)"
        exit 1
        ;;
esac

# ---- 5. 训练完成 ----
log "=== 训练完成 ==="
log "产物目录: $OUTPUT_DIR"
log "产物包: /root/autodl-tmp/nfk_outputs.tar.gz"

if [ -f "/root/autodl-tmp/nfk_outputs.tar.gz" ]; then
    SIZE=$(du -sh /root/autodl-tmp/nfk_outputs.tar.gz | cut -f1)
    log "产物包大小: $SIZE"
fi

log ""
log "=== 产物结构 ==="
if [ -d "$OUTPUT_DIR" ]; then
    find "$OUTPUT_DIR" -type f | head -50 | while read f; do
        SIZE=$(du -sh "$f" 2>/dev/null | cut -f1)
        echo "  $SIZE  $f"
    done | tee -a "$LOG_FILE"
fi

log ""
log "下载产物: 从 AutoDL 文件管理下载 /root/autodl-tmp/nfk_outputs.tar.gz"
log "或使用: scp root@<autodl-ip>:/root/autodl-tmp/nfk_outputs.tar.gz ."
