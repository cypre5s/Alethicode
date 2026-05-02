"""
NFK 训练入口脚本。

用法:
  # 单次训练
  python -m nfk.train --config nfk/configs/ablation.yaml --data path/to/data.csv

  # 完整消融实验
  python -m nfk.train --config nfk/configs/ablation.yaml --data path/to/data.csv --ablation
"""

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

import numpy as np
import yaml
from torch.utils.data import DataLoader

from .data.dataset import KTCollator, KTDataset
from .data.preprocessor import ASSISTmentsPreprocessor, EdNetPreprocessor, ProgSnap2Preprocessor
from .evaluation.metrics import compute_metrics, statistical_test
from .evaluation.visualizer import AblationVisualizer
from .training.trainer import NFKTrainer
from .utils.config import NFKConfig
from .utils.seed import set_seed

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


def load_data(config: NFKConfig):
    if config.data_format == "assistments":
        preprocessor = ASSISTmentsPreprocessor(config.data_path)
    elif config.data_format == "ednet":
        preprocessor = EdNetPreprocessor(config.data_path)
    elif config.data_format == "progsnap2":
        preprocessor = ProgSnap2Preprocessor(config.data_path)
    else:
        raise ValueError(f"Unknown data format: {config.data_format}")

    sequences = preprocessor.load_and_preprocess()
    config.n_questions = preprocessor.n_questions
    config.n_skills = preprocessor.n_skills

    logger.info(
        f"Loaded {len(sequences)} students, "
        f"{config.n_questions} questions, {config.n_skills} skills"
    )
    return sequences, preprocessor


def run_single_experiment(
    config: NFKConfig,
    sequences: list[dict],
    preprocessor,
    fold_idx: int = 0,
) -> dict[str, float]:
    splits = preprocessor.get_splits(sequences, n_folds=config.n_folds, seed=config.seed)
    train_seqs, val_seqs = splits[fold_idx]

    train_dataset = KTDataset(train_seqs, max_seq_len=config.max_seq_len)
    val_dataset = KTDataset(val_seqs, max_seq_len=config.max_seq_len)

    collator = KTCollator()
    train_loader = DataLoader(
        train_dataset, batch_size=config.batch_size, shuffle=True, collate_fn=collator
    )
    val_loader = DataLoader(
        val_dataset, batch_size=config.batch_size, shuffle=False, collate_fn=collator
    )

    trainer = NFKTrainer(config)
    model = trainer.build_model()

    logger.info(
        f"Model variant: {model.get_variant_name()}, "
        f"params: {model.count_parameters()}"
    )

    model = trainer.train(model, train_loader, val_loader)

    output_dir = Path(config.output_dir) / config.experiment_name
    output_dir.mkdir(parents=True, exist_ok=True)
    trainer.save_checkpoint(model, output_dir / f"fold{fold_idx}_seed{config.seed}.pt")

    final_metrics = trainer.history[-1] if trainer.history else {}
    return {
        "val_auc": final_metrics.get("val_auc", 0.0),
        "val_acc": final_metrics.get("val_acc", 0.0),
        "variant": model.get_variant_name(),
    }


def run_ablation(config_path: str, data_path: str, output_dir: str = "outputs"):
    with open(config_path) as f:
        ablation_config = yaml.safe_load(f)

    seeds = ablation_config["seeds"]
    n_folds = ablation_config["n_folds"]
    shared = ablation_config["shared"]
    variants = ablation_config["variants"]

    base_config = NFKConfig(**shared)
    base_config.data_path = data_path
    base_config.output_dir = output_dir
    base_config.n_folds = n_folds

    sequences, preprocessor = load_data(base_config)

    all_results: dict[str, dict[str, list[float]]] = {}

    for variant_key, variant_overrides in variants.items():
        config = NFKConfig(**{**shared, **variant_overrides})
        config.data_path = data_path
        config.output_dir = output_dir
        config.n_folds = n_folds
        config.n_questions = base_config.n_questions
        config.n_skills = base_config.n_skills

        variant_name = variant_overrides["experiment_name"]
        all_results[variant_name] = {"auc": [], "accuracy": [], "f1": []}

        logger.info(f"\n{'='*60}\n  Variant: {variant_name}\n{'='*60}")

        for seed in seeds:
            for fold in range(n_folds):
                config.seed = seed
                set_seed(seed)

                logger.info(f"  Seed={seed}, Fold={fold+1}/{n_folds}")
                result = run_single_experiment(config, sequences, preprocessor, fold)
                all_results[variant_name]["auc"].append(result["val_auc"])

    results_path = Path(output_dir) / "ablation_results.json"
    with open(results_path, "w") as f:
        json.dump(
            {k: {m: [float(v) for v in vals] for m, vals in v.items()}
             for k, v in all_results.items()},
            f, indent=2,
        )
    logger.info(f"Ablation results saved to {results_path}")

    logger.info("\n=== Statistical Significance ===")
    variant_names = list(all_results.keys())
    if len(variant_names) >= 2:
        full_scores = np.array(all_results[variant_names[0]]["auc"])
        for vn in variant_names[1:]:
            other_scores = np.array(all_results[vn]["auc"])
            min_len = min(len(full_scores), len(other_scores))
            if min_len < 5:
                continue
            test_result = statistical_test(full_scores[:min_len], other_scores[:min_len], "wilcoxon")
            sig = "***" if test_result["p_value"] < 0.001 else "**" if test_result["p_value"] < 0.01 else "*" if test_result["p_value"] < 0.05 else "n.s."
            logger.info(f"  {variant_names[0]} vs {vn}: p={test_result['p_value']:.4f} ({sig})")

    visualizer = AblationVisualizer(Path(output_dir) / "figures")
    visualizer.plot_ablation_bars(all_results)
    logger.info("Visualization saved to outputs/figures/")


def main():
    parser = argparse.ArgumentParser(description="Alethicode-NFK Training")
    parser.add_argument("--config", type=str, required=True, help="YAML config path")
    parser.add_argument("--data", type=str, required=True, help="Data path")
    parser.add_argument("--data-format", type=str, default="assistments",
                        choices=["assistments", "ednet", "progsnap2"])
    parser.add_argument("--output", type=str, default="outputs")
    parser.add_argument("--ablation", action="store_true", help="Run full ablation study")
    parser.add_argument("--fold", type=int, default=0, help="Fold index for single run")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    if args.ablation:
        run_ablation(args.config, args.data, args.output)
    else:
        config = NFKConfig.from_yaml(args.config)
        config.data_path = args.data
        config.data_format = args.data_format
        config.output_dir = args.output
        config.seed = args.seed
        set_seed(config.seed)

        sequences, preprocessor = load_data(config)
        result = run_single_experiment(config, sequences, preprocessor, args.fold)
        logger.info(f"Result: {result}")


if __name__ == "__main__":
    main()
