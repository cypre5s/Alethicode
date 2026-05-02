"""
本地 GPU 一键消融实验脚本。

用法:
  python research/nfk/run_local.py --quick          # 快速验证 (1 fold × 1 seed)
  python research/nfk/run_local.py --full           # 完整消融 (5 fold × 3 seed)
  python research/nfk/run_local.py --dataset ednet  # EdNet 数据集
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import torch

project_root = Path(__file__).resolve().parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

from nfk.data.dataset import KTCollator, KTDataset
from nfk.data.preprocessor import ASSISTmentsPreprocessor, EdNetPreprocessor
from nfk.evaluation.metrics import compute_metrics, statistical_test
from nfk.evaluation.visualizer import AblationVisualizer
from nfk.training.metrics_logger import MetricsLogger
from nfk.training.trainer import NFKTrainer
from nfk.utils.config import NFKConfig
from nfk.utils.seed import set_seed

VARIANTS = [
    {
        "name": "A+B+C (Full)",
        "use_sparse_attention": True,
        "use_kt_attention": True,
    },
    {
        "name": "A+B (w/o KTAttn)",
        "use_sparse_attention": True,
        "use_kt_attention": False,
    },
    {
        "name": "A+C (w/o SparseAttn)",
        "use_sparse_attention": False,
        "use_kt_attention": True,
    },
    {
        "name": "A only (Base)",
        "use_sparse_attention": False,
        "use_kt_attention": False,
    },
]

DEFAULT_BATCH_SIZE = 512
DEFAULT_NUM_WORKERS = 8
DEFAULT_MAX_EPOCHS = 300
DEFAULT_PATIENCE = 50


def build_config(
    variant: dict,
    n_questions: int,
    n_skills: int,
    seed: int,
    batch_size: int = DEFAULT_BATCH_SIZE,
    max_epochs: int = DEFAULT_MAX_EPOCHS,
    patience: int = DEFAULT_PATIENCE,
) -> NFKConfig:
    return NFKConfig(
        n_questions=n_questions,
        n_skills=n_skills,
        embed_dim=128,
        hidden_dim=384,
        n_heads=8,
        top_k=20,
        n_kt_heads=4,
        dropout=0.2,
        max_seq_len=200,
        learning_rate=5e-4,
        weight_decay=0.01,
        batch_size=batch_size,
        max_epochs=max_epochs,
        patience=patience,
        grad_clip=1.0,
        lambda_kt=1.0,
        use_sparse_attention=variant["use_sparse_attention"],
        use_kt_attention=variant["use_kt_attention"],
        seed=seed,
    )


def build_data_loader(
    dataset: KTDataset,
    batch_size: int,
    shuffle: bool,
    collator: KTCollator,
    num_workers: int = DEFAULT_NUM_WORKERS,
) -> torch.utils.data.DataLoader:
    return torch.utils.data.DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=shuffle,
        collate_fn=collator,
        num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
        persistent_workers=num_workers > 0,
    )


def train_single(
    config: NFKConfig,
    train_seqs: list[dict],
    val_seqs: list[dict],
    mlog: MetricsLogger,
    variant_name: str,
    seed: int,
    fold_idx: int,
    tb_log_dir: str | None = None,
    checkpoint_dir: Path | None = None,
    num_workers: int = DEFAULT_NUM_WORKERS,
) -> tuple[dict, list[dict]]:
    train_ds = KTDataset(train_seqs, max_seq_len=config.max_seq_len)
    val_ds = KTDataset(val_seqs, max_seq_len=config.max_seq_len)
    collator = KTCollator()

    train_loader = build_data_loader(
        train_ds,
        batch_size=config.batch_size,
        shuffle=True,
        collator=collator,
        num_workers=num_workers,
    )
    val_loader = build_data_loader(
        val_ds,
        batch_size=config.batch_size,
        shuffle=False,
        collator=collator,
        num_workers=num_workers,
    )

    trainer = NFKTrainer(config, tb_log_dir=tb_log_dir)
    model = trainer.build_model()
    param_count = model.count_parameters()

    mlog.log_event(
        "train_start",
        variant=variant_name, seed=seed, fold=fold_idx,
        params=param_count, device=str(trainer.device),
    )

    t0 = time.time()
    model = trainer.train(model, train_loader, val_loader)
    elapsed = time.time() - t0

    for h in trainer.history:
        mlog.log_epoch({
            "variant": variant_name,
            "seed": seed, "fold": fold_idx,
            "epoch": h.get("epoch", 0),
            "train_loss": round(h.get("train_loss", 0), 6),
            "train_loss_kt": round(h.get("train_loss_kt", 0), 6),
            "val_loss": round(h.get("val_loss", 0), 6),
            "val_loss_kt": round(h.get("val_loss_kt", 0), 6),
            "val_auc": round(h.get("val_auc", 0), 6),
            "val_acc": round(h.get("val_acc", 0), 6),
            "val_f1": round(h.get("val_f1", 0), 6),
            "lr": h.get("lr", 0),
            "epoch_time_sec": h.get("epoch_time_sec", 0),
            "gpu_mem_mb": h.get("gpu_mem_mb", 0),
        })

    if checkpoint_dir:
        ckpt_path = checkpoint_dir / f"{variant_name.replace(' ', '_')}_s{seed}_f{fold_idx}.pt"
        trainer.save_checkpoint(model, ckpt_path)

    best = max(trainer.history, key=lambda r: r.get("val_auc", 0.0))
    result = {
        "auc": best.get("val_auc", 0.0),
        "accuracy": best.get("val_acc", 0.0),
        "f1": best.get("val_f1", 0.0),
        "elapsed_sec": elapsed,
    }

    mlog.log_event(
        "train_end",
        variant=variant_name, seed=seed, fold=fold_idx,
        best_auc=result["auc"], elapsed_sec=round(elapsed, 1),
    )
    trainer.close()

    return result, trainer.history


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        handlers=[
            logging.StreamHandler(sys.stdout),
            logging.FileHandler("nfk_training.log", encoding="utf-8"),
        ],
    )
    logger = logging.getLogger("run_local")

    parser = argparse.ArgumentParser(description="Alethicode-NFK Local GPU Training")
    parser.add_argument("--dataset", type=str, default="assistments",
                        choices=["assistments", "ednet"])
    parser.add_argument("--data-path", type=str, default=None,
                        help="Override data path")
    parser.add_argument("--output", type=str, default="outputs")
    parser.add_argument("--quick", action="store_true",
                        help="Quick mode: 1 fold × 1 seed")
    parser.add_argument("--full", action="store_true",
                        help="Full mode: 5 fold × 3 seed")
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE,
                        help="Training batch size")
    parser.add_argument("--num-workers", type=int, default=DEFAULT_NUM_WORKERS,
                        help="DataLoader worker process count")
    parser.add_argument("--max-epochs", type=int, default=DEFAULT_MAX_EPOCHS,
                        help="Max epochs")
    parser.add_argument("--patience", type=int, default=DEFAULT_PATIENCE,
                        help="Early stopping patience")
    args = parser.parse_args()

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.dataset == "assistments":
        data_path = args.data_path or str(project_root / "datasets" / "assistments2009" / "skill_builder_data_corrected.csv")
        preprocessor = ASSISTmentsPreprocessor(data_path)
    else:
        data_path = args.data_path or str(project_root / "datasets" / "ednet")
        preprocessor = EdNetPreprocessor(data_path)

    logger.info(f"GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'}")
    logger.info(f"Dataset: {args.dataset} ({data_path})")
    logger.info(f"Batch size: {args.batch_size}, DataLoader workers: {args.num_workers}")
    logger.info(f"Epochs: max={args.max_epochs}, patience={args.patience}")

    sequences = preprocessor.load_and_preprocess()
    logger.info(f"Loaded {len(sequences)} students, {preprocessor.n_questions} questions, {preprocessor.n_skills} skills")

    seeds = [42] if args.quick else [42, 3407, 2024]
    n_folds = 1 if args.quick else 5

    mlog = MetricsLogger(output_dir)
    checkpoint_dir = output_dir / "checkpoints"
    checkpoint_dir.mkdir(parents=True, exist_ok=True)

    all_results: dict[str, dict[str, list[float]]] = {}
    all_histories: dict[str, list[list[dict]]] = {}
    total_runs = len(VARIANTS) * len(seeds) * n_folds
    run_idx = 0
    experiment_start = time.time()

    for variant in VARIANTS:
        vname = variant["name"]
        all_results[vname] = {"auc": [], "accuracy": [], "f1": []}
        all_histories[vname] = []

        logger.info(f"\n{'='*60}")
        logger.info(f"  Variant: {vname}")
        logger.info(f"  sparse_attn={variant['use_sparse_attention']}, kt_attn={variant['use_kt_attention']}")
        logger.info(f"{'='*60}")

        for seed in seeds:
            set_seed(seed)
            splits = preprocessor.get_splits(sequences, n_folds=n_folds, seed=seed)

            for fold_idx, (train_seqs, val_seqs) in enumerate(splits[:n_folds]):
                run_idx += 1
                elapsed_total = time.time() - experiment_start
                eta_sec = (elapsed_total / run_idx) * (total_runs - run_idx) if run_idx > 0 else 0

                logger.info(
                    f"  [{run_idx}/{total_runs}] {vname} seed={seed} fold={fold_idx+1}/{n_folds} "
                    f"| ETA: {eta_sec/60:.0f}min"
                )

                config = build_config(
                    variant,
                    preprocessor.n_questions,
                    preprocessor.n_skills,
                    seed,
                    batch_size=args.batch_size,
                    max_epochs=args.max_epochs,
                    patience=args.patience,
                )
                tb_dir = str(output_dir / "tensorboard" / f"{vname.replace(' ', '_')}_s{seed}_f{fold_idx}")

                result, history = train_single(
                    config, train_seqs, val_seqs,
                    mlog, vname, seed, fold_idx,
                    tb_log_dir=tb_dir,
                    checkpoint_dir=checkpoint_dir,
                    num_workers=args.num_workers,
                )

                all_results[vname]["auc"].append(result["auc"])
                all_results[vname]["accuracy"].append(result["accuracy"])
                all_results[vname]["f1"].append(result["f1"])
                all_histories[vname].append(history)

                logger.info(
                    f"    -> AUC={result['auc']:.4f} F1={result['f1']:.4f} ({result['elapsed_sec']:.0f}s)"
                )

    # === 结果汇总 ===
    logger.info(f"\n{'='*60}")
    logger.info("消融实验最终结果")
    logger.info(f"{'='*60}")
    for vname, metrics in all_results.items():
        auc_m = np.mean(metrics["auc"])
        auc_s = np.std(metrics["auc"])
        f1_m = np.mean(metrics["f1"])
        logger.info(f"  {vname:25s}  AUC={auc_m:.4f}±{auc_s:.4f}  F1={f1_m:.4f}")

    # === 统计检验 ===
    logger.info("\n=== Wilcoxon 检验 ===")
    full_name = VARIANTS[0]["name"]
    full_aucs = np.array(all_results[full_name]["auc"])
    for variant in VARIANTS[1:]:
        vname = variant["name"]
        other_aucs = np.array(all_results[vname]["auc"])
        ml = min(len(full_aucs), len(other_aucs))
        if ml < 5:
            logger.info(f"  {full_name} vs {vname}: 样本不足 ({ml}), 跳过检验")
            continue
        tr = statistical_test(full_aucs[:ml], other_aucs[:ml], "wilcoxon")
        sig = (
            "***" if tr["p_value"] < 0.001 else
            "**" if tr["p_value"] < 0.01 else
            "*" if tr["p_value"] < 0.05 else "n.s."
        )
        delta = np.mean(full_aucs) - np.mean(other_aucs)
        logger.info(f"  {full_name} vs {vname}: Δ={delta:+.4f}, p={tr['p_value']:.4f} ({sig})")

    # === 保存结果 ===
    results_path = output_dir / "ablation_results.json"
    with open(results_path, "w") as f:
        json.dump(
            {k: {m: [float(v) for v in vals] for m, vals in metrics.items()}
             for k, metrics in all_results.items()},
            f, indent=2,
        )
    logger.info(f"\n结果保存: {results_path}")

    summary = {
        "completed_at": datetime.now().isoformat(),
        "device": str(torch.device("cuda" if torch.cuda.is_available() else "cpu")),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "N/A",
        "dataset": args.dataset,
        "seeds": seeds,
        "n_folds": n_folds,
        "total_elapsed_sec": round(time.time() - experiment_start, 1),
        "results": {
            v: {"auc_mean": round(float(np.mean(m["auc"])), 4),
                "auc_std": round(float(np.std(m["auc"])), 4),
                "f1_mean": round(float(np.mean(m["f1"])), 4)}
            for v, m in all_results.items()
        },
    }
    with open(output_dir / "summary.json", "w") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    # === 图表生成 ===
    logger.info("\n生成图表...")
    viz = AblationVisualizer(output_dir / "figures")
    viz.plot_ablation_bars(all_results)
    viz.plot_auc_boxplot(all_results)
    viz.plot_component_waterfall(all_results)
    viz.plot_training_curves(all_histories)
    viz.plot_all_variants_auc(all_histories)
    logger.info(f"图表保存: {output_dir / 'figures'}")

    mlog.close()
    total_time = time.time() - experiment_start
    logger.info(f"\n实验完成! 总耗时: {total_time/60:.1f} 分钟")


if __name__ == "__main__":
    main()
