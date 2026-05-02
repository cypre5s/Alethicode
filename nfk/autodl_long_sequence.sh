#!/usr/bin/env bash
# ============================================================
# NFK AutoDL 长轮数顺序实验
#
# 默认流程:
#   1. 先跑 2000 epoch / patience 200 / lr 1e-4
#   2. 再跑 10000 epoch / patience 500 / lr 5e-5
#
# 用法:
#   bash /root/nfk/autodl_long_sequence.sh
#
# 可选覆盖:
#   NFK_LONG_DATASET=assistments|ednet|all
#   NFK_LONG_DATASETS_DIR=/root/datasets
#   NFK_LONG_OUTPUT_BASE=/root/autodl-tmp
#   NFK_LONG_BATCH_SIZE=1024
# ============================================================
set -euo pipefail

WORK_DIR="${NFK_WORK_DIR:-/root/nfk}"
DATASETS_DIR="${NFK_LONG_DATASETS_DIR:-${NFK_DATASETS_DIR:-/root/datasets}}"
OUTPUT_BASE="${NFK_LONG_OUTPUT_BASE:-/root/autodl-tmp}"
DATASET="${NFK_LONG_DATASET:-assistments}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

BATCH_SIZE="${NFK_LONG_BATCH_SIZE:-1024}"
HIDDEN_DIM="${NFK_LONG_HIDDEN_DIM:-384}"
N_KT_HEADS="${NFK_LONG_N_KT_HEADS:-4}"
NUM_WORKERS="${NFK_LONG_NUM_WORKERS:-8}"

RUN_1_NAME="${NFK_LONG_RUN_1_NAME:-e2000_p200_lr1e4}"
RUN_1_MAX_EPOCHS="${NFK_LONG_RUN_1_MAX_EPOCHS:-2000}"
RUN_1_PATIENCE="${NFK_LONG_RUN_1_PATIENCE:-200}"
RUN_1_LR="${NFK_LONG_RUN_1_LR:-0.0001}"

RUN_2_NAME="${NFK_LONG_RUN_2_NAME:-e10000_p500_lr5e5}"
RUN_2_MAX_EPOCHS="${NFK_LONG_RUN_2_MAX_EPOCHS:-10000}"
RUN_2_PATIENCE="${NFK_LONG_RUN_2_PATIENCE:-500}"
RUN_2_LR="${NFK_LONG_RUN_2_LR:-0.00005}"

MASTER_LOG="$OUTPUT_BASE/nfk_long_sequence_master.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$MASTER_LOG"
}

require_path() {
    local path="$1"
    local label="$2"
    if [ ! -e "$path" ]; then
        log "缺少 $label: $path"
        exit 1
    fi
}

prepare_environment() {
    mkdir -p "$OUTPUT_BASE" "$DATASETS_DIR"

    require_path "$WORK_DIR/autodl_train.py" "训练入口"

    log "=== NFK 长轮数顺序实验 ==="
    log "工作目录: $WORK_DIR"
    log "数据集目录: $DATASETS_DIR"
    log "输出根目录: $OUTPUT_BASE"
    log "数据集: $DATASET"
    log "Python: $($PYTHON_BIN --version 2>&1)"
    log "当前训练器 LR warmup 为 max_epochs//20；默认两段约为 100 epoch 和 500 epoch"

    cd "$WORK_DIR"

    "$PYTHON_BIN" - <<'PY'
import torch

assert torch.cuda.is_available(), "CUDA 不可用，长轮数实验必须在 GPU 上运行"
print(f"GPU: {torch.cuda.get_device_name(0)}")
print(f"CUDA: {torch.version.cuda}")
print(f"PyTorch: {torch.__version__}")
PY
}

run_experiment() {
    local run_name="$1"
    local max_epochs="$2"
    local patience="$3"
    local lr="$4"
    local output_dir="$OUTPUT_BASE/nfk_outputs_$run_name"
    local archive_path="$OUTPUT_BASE/nfk_outputs_$run_name.tar.gz"
    local run_log="$OUTPUT_BASE/nfk_$run_name.log"

    if [ -e "$output_dir" ]; then
        log "输出目录已存在，为避免混入旧结果，停止: $output_dir"
        exit 1
    fi
    if [ -e "$archive_path" ]; then
        log "产物包已存在，为避免覆盖旧结果，停止: $archive_path"
        exit 1
    fi

    log ""
    log "=== 开始 run: $run_name ==="
    log "max_epochs=$max_epochs patience=$patience lr=$lr batch_size=$BATCH_SIZE hidden_dim=$HIDDEN_DIM n_kt_heads=$N_KT_HEADS"
    log "输出目录: $output_dir"
    log "日志文件: $run_log"

    "$PYTHON_BIN" -u autodl_train.py \
        --dataset "$DATASET" \
        --datasets "$DATASETS_DIR" \
        --output "$output_dir" \
        --archive "$archive_path" \
        --max-epochs "$max_epochs" \
        --patience "$patience" \
        --lr "$lr" \
        --batch-size "$BATCH_SIZE" \
        --hidden-dim "$HIDDEN_DIM" \
        --n-kt-heads "$N_KT_HEADS" \
        --num-workers "$NUM_WORKERS" \
        2>&1 | tee "$run_log"

    log "=== 完成 run: $run_name ==="
    log "输出目录: $output_dir"
    log "产物包: $archive_path"
}

prepare_environment
run_experiment "$RUN_1_NAME" "$RUN_1_MAX_EPOCHS" "$RUN_1_PATIENCE" "$RUN_1_LR"
run_experiment "$RUN_2_NAME" "$RUN_2_MAX_EPOCHS" "$RUN_2_PATIENCE" "$RUN_2_LR"

log ""
log "=== 全部长轮数实验完成 ==="
log "第一段产物: $OUTPUT_BASE/nfk_outputs_$RUN_1_NAME.tar.gz"
log "第二段产物: $OUTPUT_BASE/nfk_outputs_$RUN_2_NAME.tar.gz"
