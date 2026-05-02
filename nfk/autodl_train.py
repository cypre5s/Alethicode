"""
AutoDL 一键训练脚本。

功能:
  1. 自动下载数据集（ASSISTments 2009 + EdNet KT1）
  2. 运行完整消融实验（4 变体 × 5 fold × 3 seed = 60 次训练）
  3. 实时 JSONL + CSV 日志
  4. 训练结束自动生成全套图表
  5. ONNX 导出最优模型
  6. 打包所有产物到 /root/autodl-tmp/nfk_outputs.tar.gz

用法:
  python autodl_train.py                    # 完整消融实验
  python autodl_train.py --quick            # 快速验证（1 fold × 1 seed）
  python autodl_train.py --dataset ednet    # 仅 EdNet 数据集
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import tarfile
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import torch

project_root = Path(__file__).resolve().parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

from nfk.data.dataset import KTCollator, KTDataset
from nfk.data.download import download_assistments, download_ednet
from nfk.data.preprocessor import ASSISTmentsPreprocessor, EdNetPreprocessor
from nfk.evaluation.metrics import statistical_test
from nfk.evaluation.visualizer import AblationVisualizer
from nfk.inference.exporter import ONNXExporter
from nfk.training.metrics_logger import MetricsLogger
from nfk.training.trainer import NFKTrainer
from nfk.utils.config import NFKConfig
from nfk.utils.seed import set_seed

VARIANTS = [
    {"name": "A+B+C_Full", "use_sparse_attention": True, "use_kt_attention": True},
    {"name": "A+B_wo_KTAttn", "use_sparse_attention": True, "use_kt_attention": False},
    {"name": "A+C_wo_SparseAttn", "use_sparse_attention": False, "use_kt_attention": True},
    {"name": "A_only_Base", "use_sparse_attention": False, "use_kt_attention": False},
]


def build_config(
    variant: dict,
    n_questions: int,
    n_skills: int,
    seed: int,
    hidden_dim: int = 384,
    n_kt_heads: int = 4,
    batch_size: int = 1024,
    max_epochs: int = 200,
    patience: int = 8,
    learning_rate: float = 2e-4,
) -> NFKConfig:
    return NFKConfig(
        n_questions=n_questions,
        n_skills=n_skills,
        embed_dim=128,
        hidden_dim=hidden_dim,
        n_heads=8,
        top_k=20,
        n_kt_heads=n_kt_heads,
        dropout=0.2,
        max_seq_len=200,
        learning_rate=learning_rate,
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


def train_single(
    config: NFKConfig,
    train_seqs: list[dict],
    val_seqs: list[dict],
    mlog: MetricsLogger,
    dataset_name: str,
    variant_name: str,
    seed: int,
    fold_idx: int,
    checkpoint_dir: Path,
) -> tuple[dict, object, NFKTrainer]:
    train_ds = KTDataset(train_seqs, max_seq_len=config.max_seq_len)
    val_ds = KTDataset(val_seqs, max_seq_len=config.max_seq_len)
    collator = KTCollator()

    import os as _os
    num_workers = max(1, min(8, (_os.cpu_count() or 2)))
    persistent = num_workers > 0
    train_loader = torch.utils.data.DataLoader(
        train_ds, batch_size=config.batch_size, shuffle=True,
        collate_fn=collator, num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
        persistent_workers=persistent,
    )
    val_loader = torch.utils.data.DataLoader(
        val_ds, batch_size=config.batch_size, shuffle=False,
        collate_fn=collator, num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
        persistent_workers=persistent,
    )

    trainer = NFKTrainer(config)
    model = trainer.build_model()

    mlog.log_event(
        "train_start",
        dataset=dataset_name, variant=variant_name,
        seed=seed, fold=fold_idx,
        params=model.count_parameters(),
        device=str(trainer.device),
    )

    t0 = time.time()
    model = trainer.train(model, train_loader, val_loader)
    elapsed = time.time() - t0

    for h in trainer.history:
        mlog.log_epoch({
            "variant": variant_name, "seed": seed, "fold": fold_idx,
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

    ckpt_path = checkpoint_dir / f"{variant_name}_s{seed}_f{fold_idx}.pt"
    trainer.save_checkpoint(model, ckpt_path)

    best = max(trainer.history, key=lambda r: r.get("val_auc", 0.0))
    result = {
        "auc": best.get("val_auc", 0.0),
        "acc": best.get("val_acc", 0.0),
        "elapsed_sec": elapsed,
    }

    mlog.log_event(
        "train_end",
        dataset=dataset_name, variant=variant_name,
        seed=seed, fold=fold_idx,
        best_auc=result["auc"],
        elapsed_sec=round(elapsed, 1),
    )

    return result, model, trainer


def select_onnx_export_candidate(run_records: list[dict]) -> dict | None:
    if not run_records:
        return None

    grouped_runs: dict[tuple[str, str], list[dict]] = {}
    for record in run_records:
        key = (record["dataset"], record["variant"])
        grouped_runs.setdefault(key, []).append(record)

    candidates = []
    for (dataset_name, variant_name), records in grouped_runs.items():
        aucs = np.array([r["auc"] for r in records], dtype=float)
        best_run = max(records, key=lambda r: r["auc"])
        candidates.append({
            "dataset": dataset_name,
            "variant": variant_name,
            "variant_mean_auc": round(float(np.mean(aucs)), 6),
            "variant_std_auc": round(float(np.std(aucs)), 6),
            "best_run_auc": round(float(best_run["auc"]), 6),
            "seed": best_run["seed"],
            "fold": best_run["fold"],
            "checkpoint": best_run["checkpoint"],
            "config": best_run["config"],
        })

    # 选择规则:
    # 1) 先选平均 AUC 更高的模型变体
    # 2) 若均值相同, 再选该变体内单次 AUC 更高者
    # 3) 再按方差更低者优先, 其余字段用于稳定排序
    candidates.sort(
        key=lambda c: (
            -c["variant_mean_auc"],
            -c["best_run_auc"],
            c["variant_std_auc"],
            c["dataset"],
            c["variant"],
            c["seed"],
            c["fold"],
        )
    )
    return candidates[0]


def run_experiment(args):
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    mlog = MetricsLogger(output_dir)
    logger = logging.getLogger("autodl_train")

    datasets_dir = Path(args.datasets)
    datasets_to_run = []

    if args.dataset in ("assistments", "all"):
        logger.info("下载 ASSISTments 2009 数据集...")
        result = download_assistments(datasets_dir)
        if result:
            datasets_to_run.append(("assistments", str(result)))

    if args.dataset in ("ednet", "all"):
        logger.info("下载 EdNet KT1 数据集...")
        result = download_ednet(datasets_dir, max_users=args.max_ednet_users)
        if result:
            datasets_to_run.append(("ednet", str(result)))

    seeds = [42] if args.quick else [42, 3407, 2024]
    n_folds = 1 if args.quick else 5

    grand_results = {}
    run_records = []

    for ds_name, ds_path in datasets_to_run:
        logger.info(f"\n{'#'*70}\n  数据集: {ds_name} ({ds_path})\n{'#'*70}")

        preprocessor = (
            ASSISTmentsPreprocessor(ds_path) if ds_name == "assistments"
            else EdNetPreprocessor(ds_path)
        )
        sequences = preprocessor.load_and_preprocess()
        logger.info(f"  {len(sequences)} 学生, {preprocessor.n_questions} 题, {preprocessor.n_skills} 知识点")

        ds_results: dict[str, dict[str, list[float]]] = {}
        checkpoint_dir = output_dir / "checkpoints" / ds_name
        checkpoint_dir.mkdir(parents=True, exist_ok=True)

        for variant in VARIANTS:
            vname = variant["name"]
            ds_results[vname] = {"auc": [], "accuracy": []}

            logger.info(f"\n{'='*60}\n  变体: {vname}\n{'='*60}")

            for seed in seeds:
                set_seed(seed)
                splits = preprocessor.get_splits(sequences, n_folds=n_folds, seed=seed)

                for fold_idx, (train_seqs, val_seqs) in enumerate(splits):
                    config = build_config(
                        variant, preprocessor.n_questions, preprocessor.n_skills, seed,
                        hidden_dim=args.hidden_dim, n_kt_heads=args.n_kt_heads,
                        batch_size=args.batch_size, max_epochs=args.max_epochs,
                        patience=args.patience, learning_rate=args.lr,
                    )

                    result, model, trainer = train_single(
                        config, train_seqs, val_seqs,
                        mlog, ds_name, vname, seed, fold_idx, checkpoint_dir,
                    )

                    ds_results[vname]["auc"].append(result["auc"])
                    ds_results[vname]["accuracy"].append(result["acc"])
                    logger.info(f"    -> AUC={result['auc']:.4f} ({result['elapsed_sec']:.0f}s)")
                    run_records.append({
                        "dataset": ds_name,
                        "variant": vname,
                        "seed": seed,
                        "fold": fold_idx,
                        "auc": result["auc"],
                        "checkpoint": checkpoint_dir / f"{vname}_s{seed}_f{fold_idx}.pt",
                        "config": config,
                    })

        _print_results(logger, ds_name, ds_results)
        grand_results[ds_name] = ds_results

    onnx_candidate = select_onnx_export_candidate(run_records)
    _generate_outputs(output_dir, grand_results, onnx_candidate, mlog, logger, seeds, n_folds, args)


def _print_results(logger, ds_name: str, ds_results: dict):
    logger.info(f"\n{'='*60}\n  {ds_name} 消融结果\n{'='*60}")
    for vname, metrics in ds_results.items():
        auc_m = np.mean(metrics["auc"])
        auc_s = np.std(metrics["auc"])
        logger.info(f"  {vname:25s}  AUC={auc_m:.4f}±{auc_s:.4f}")

    if len(ds_results) >= 2:
        full_name = VARIANTS[0]["name"]
        full_aucs = np.array(ds_results[full_name]["auc"])
        for v in VARIANTS[1:]:
            vn = v["name"]
            other = np.array(ds_results[vn]["auc"])
            ml = min(len(full_aucs), len(other))
            if ml < 5:
                continue
            tr = statistical_test(full_aucs[:ml], other[:ml], "wilcoxon")
            sig = "***" if tr["p_value"] < 0.001 else "**" if tr["p_value"] < 0.01 else "*" if tr["p_value"] < 0.05 else "n.s."
            delta = np.mean(full_aucs) - np.mean(other)
            logger.info(f"  {full_name} vs {vn}: Δ={delta:+.4f}, p={tr['p_value']:.4f} ({sig})")


def _generate_outputs(output_dir, grand_results, onnx_candidate, mlog, logger, seeds, n_folds, args):
    for ds_name, ds_results in grand_results.items():
        viz = AblationVisualizer(output_dir / ds_name / "figures")
        viz.plot_ablation_bars(ds_results)

    if onnx_candidate:
        logger.info("\n导出 ONNX: 先按平均AUC选变体，再在该变体内选最佳run")
        logger.info(
            "  选中: dataset=%s variant=%s meanAUC=%.4f runAUC=%.4f seed=%s fold=%s",
            onnx_candidate["dataset"],
            onnx_candidate["variant"],
            onnx_candidate["variant_mean_auc"],
            onnx_candidate["best_run_auc"],
            onnx_candidate["seed"],
            onnx_candidate["fold"],
        )
        trainer = NFKTrainer(onnx_candidate["config"])
        model, _ = trainer.load_checkpoint(onnx_candidate["checkpoint"])
        model.eval()
        onnx_dir = output_dir / "onnx"
        onnx_dir.mkdir(parents=True, exist_ok=True)
        exporter = ONNXExporter(model, max_seq_len=onnx_candidate["config"].max_seq_len)
        onnx_path = exporter.export(onnx_dir / "alethicode_nfk.onnx")
        exporter.verify(onnx_path)

    selected_best_auc = onnx_candidate["best_run_auc"] if onnx_candidate else None
    summary = {
        "completed_at": datetime.now().isoformat(),
        "device": str(torch.device("cuda" if torch.cuda.is_available() else "cpu")),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "N/A",
        "datasets": list(grand_results.keys()),
        "seeds": seeds, "n_folds": n_folds,
        "best_auc": selected_best_auc,
        "onnx_selection_policy": "best_variant_by_mean_auc_then_best_run_within_variant",
        "onnx_selected_run": {
            "dataset": onnx_candidate["dataset"],
            "variant": onnx_candidate["variant"],
            "seed": onnx_candidate["seed"],
            "fold": onnx_candidate["fold"],
            "variant_mean_auc": onnx_candidate["variant_mean_auc"],
            "variant_std_auc": onnx_candidate["variant_std_auc"],
            "run_auc": onnx_candidate["best_run_auc"],
            "checkpoint": str(onnx_candidate["checkpoint"]),
        } if onnx_candidate else None,
        "results": {
            ds: {v: {"mean": round(float(np.mean(m["auc"])), 4), "std": round(float(np.std(m["auc"])), 4)}
                 for v, m in vr.items()}
            for ds, vr in grand_results.items()
        },
    }
    with open(output_dir / "summary.json", "w") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    mlog.close()

    archive_path = Path(args.archive)
    logger.info(f"\n打包产物 -> {archive_path}")
    with tarfile.open(archive_path, "w:gz") as tar:
        tar.add(output_dir, arcname="nfk_outputs")
    logger.info(f"完成! 产物包: {archive_path} ({archive_path.stat().st_size / 1024 / 1024:.1f} MB)")


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        handlers=[
            logging.StreamHandler(sys.stdout),
            logging.FileHandler("autodl_train.log", encoding="utf-8"),
        ],
    )

    parser = argparse.ArgumentParser(description="Alethicode-NFK AutoDL Training")
    parser.add_argument("--dataset", type=str, default="assistments", choices=["assistments", "ednet", "all"])
    parser.add_argument("--datasets", type=str, default="datasets", help="数据集存储目录")
    parser.add_argument("--output", type=str, default="/root/autodl-tmp/nfk_outputs", help="输出目录")
    parser.add_argument("--archive", type=str, default="/root/autodl-tmp/nfk_outputs.tar.gz", help="打包产物路径")
    parser.add_argument("--quick", action="store_true", help="快速验证模式 (1 fold × 1 seed)")
    parser.add_argument("--hidden-dim", type=int, default=384, help="LSTM 隐层维度")
    parser.add_argument("--n-kt-heads", type=int, default=4, help="simpleKT 注意力头数")
    parser.add_argument("--batch-size", type=int, default=1024, help="训练 batch size")
    parser.add_argument("--max-epochs", type=int, default=200, help="最大 epoch 数")
    parser.add_argument("--patience", type=int, default=8, help="Early stopping patience")
    parser.add_argument("--lr", type=float, default=2e-4, help="学习率")
    parser.add_argument("--num-workers", type=int, default=8, help="DataLoader 工作进程数")
    parser.add_argument("--max-ednet-users", type=int, default=5000)
    args = parser.parse_args()

    run_experiment(args)


if __name__ == "__main__":
    main()
