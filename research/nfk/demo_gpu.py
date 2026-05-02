"""
GPU 快速 demo：在 ASSISTments 2009 上训练 A+B+C (Full) 模型。

仅训练 15 个 epoch，验证替换 TSK→simpleKT 后模型能否正常收敛。

用法:
  python research/nfk/demo_gpu.py
"""

from __future__ import annotations

import logging
import sys
import time
from pathlib import Path

import torch

project_root = Path(__file__).resolve().parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))

from nfk.data.dataset import KTCollator, KTDataset
from nfk.data.preprocessor import ASSISTmentsPreprocessor
from nfk.training.trainer import NFKTrainer
from nfk.utils.config import NFKConfig
from nfk.utils.seed import set_seed

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("demo")


def main():
    set_seed(42)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    if device == "cuda":
        logger.info(f"GPU: {torch.cuda.get_device_name(0)}")
    else:
        logger.info("No GPU detected, running on CPU")

    data_path = project_root / "datasets" / "assistments2009" / "skill_builder_data_corrected.csv"
    logger.info(f"Loading data from {data_path}")
    preprocessor = ASSISTmentsPreprocessor(str(data_path))
    sequences = preprocessor.load_and_preprocess()
    logger.info(
        f"Loaded {len(sequences)} students, "
        f"{preprocessor.n_questions} questions, "
        f"{preprocessor.n_skills} skills"
    )

    splits = preprocessor.get_splits(sequences, n_folds=5, seed=42)
    train_seqs, val_seqs = splits[0]

    collator = KTCollator()
    train_ds = KTDataset(train_seqs, max_seq_len=200)
    val_ds = KTDataset(val_seqs, max_seq_len=200)
    train_loader = torch.utils.data.DataLoader(
        train_ds, batch_size=256, shuffle=True, collate_fn=collator,
        num_workers=4, pin_memory=(device == "cuda"),
    )
    val_loader = torch.utils.data.DataLoader(
        val_ds, batch_size=256, shuffle=False, collate_fn=collator,
        num_workers=4, pin_memory=(device == "cuda"),
    )

    config = NFKConfig(
        n_questions=preprocessor.n_questions,
        n_skills=preprocessor.n_skills,
        embed_dim=128,
        hidden_dim=256,
        n_heads=8,
        top_k=20,
        n_kt_heads=1,
        dropout=0.2,
        max_seq_len=200,
        learning_rate=5e-4,
        weight_decay=0.01,
        batch_size=256,
        max_epochs=50,
        patience=20,
        grad_clip=1.0,
        lambda_kt=1.0,
        use_sparse_attention=True,
        use_kt_attention=True,
        seed=42,
    )

    trainer = NFKTrainer(config, device=torch.device(device))
    model = trainer.build_model()

    logger.info(f"Model: {model.get_variant_name()}")
    params = model.count_parameters()
    logger.info(
        f"Parameters: total={params['total']:,} | "
        f"encoder={params['encoder']:,} | "
        f"sparse_attn={params['sparse_attn']:,} | "
        f"kt_attn={params['kt_attn']:,} | "
        f"kt_head={params['kt_head']:,}"
    )

    logger.info("=" * 60)
    logger.info("Starting training (50 epochs, ReduceLROnPlateau, lr=5e-4)")
    logger.info("=" * 60)

    t0 = time.time()
    model = trainer.train(model, train_loader, val_loader)
    elapsed = time.time() - t0

    logger.info("=" * 60)
    logger.info("Training complete!")
    logger.info(f"Total time: {elapsed:.1f}s")
    logger.info("=" * 60)

    if trainer.history:
        best = max(trainer.history, key=lambda r: r.get("val_auc", 0.0))
        logger.info(
            f"Best epoch {best['epoch']}: "
            f"AUC={best['val_auc']:.4f} "
            f"ACC={best['val_acc']:.4f} "
            f"F1={best['val_f1']:.4f}"
        )

        logger.info("\nEpoch-by-epoch:")
        for h in trainer.history:
            logger.info(
                f"  Epoch {h['epoch']:3d}: "
                f"train_loss={h['train_loss']:.4f}  "
                f"val_auc={h['val_auc']:.4f}  "
                f"val_f1={h['val_f1']:.4f}  "
                f"({h['epoch_time_sec']:.1f}s)"
            )

    if device == "cuda":
        logger.info(f"Peak GPU memory: {torch.cuda.max_memory_allocated() / 1024 / 1024:.1f} MB")

    out = model.predict(
        question_ids=torch.randint(1, preprocessor.n_questions, (1, 20)).to(device),
        skill_ids=torch.randint(1, preprocessor.n_skills, (1, 20)).to(device),
        responses=torch.randint(0, 2, (1, 20)).to(device),
        delta_t=torch.rand(1, 20, 20).to(device),
        pad_mask=torch.zeros(1, 20, dtype=torch.bool).to(device),
    )
    logger.info(f"\nInference test: probabilities = {out['kt_prob'][0, :5].cpu().tolist()}")
    if "attention_weights" in out:
        attn = out["attention_weights"][0]
        nonzero_ratio = (attn > 0.01).float().mean().item()
        logger.info(f"Attention sparsity: {(1-nonzero_ratio)*100:.1f}% near-zero weights")


if __name__ == "__main__":
    main()
