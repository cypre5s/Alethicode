"""
NFK 训练器

功能:
  - 标准训练（所有组件端到端联合优化）
  - Early stopping（基于 validation AUC，patience 可配置）
  - 梯度裁剪
  - TensorBoard 实时日志
  - GPU 显存与 epoch 耗时监控
"""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import Any

import numpy as np
import torch
import torch.nn as nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import CosineAnnealingWarmRestarts, LambdaLR
from torch.utils.data import DataLoader
from torch.utils.tensorboard import SummaryWriter

from ..evaluation.metrics import compute_metrics
from ..models.nfk_model import AlethicodeNFK
from ..utils.config import NFKConfig
from .loss import KTLoss

logger = logging.getLogger(__name__)


class NFKTrainer:

    def __init__(
        self,
        config: NFKConfig,
        device: torch.device | None = None,
        tb_log_dir: str | None = None,
    ):
        self.config = config
        self.device = device or torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.history: list[dict[str, Any]] = []
        self.global_step = 0

        if tb_log_dir:
            self.writer = SummaryWriter(log_dir=tb_log_dir)
        else:
            self.writer = None

    def build_model(self) -> AlethicodeNFK:
        model = AlethicodeNFK(
            n_questions=self.config.n_questions,
            n_skills=self.config.n_skills,
            embed_dim=self.config.embed_dim,
            hidden_dim=self.config.hidden_dim,
            n_heads=self.config.n_heads,
            top_k=self.config.top_k,
            n_kt_heads=self.config.n_kt_heads,
            dropout=self.config.dropout,
            max_seq_len=self.config.max_seq_len,
            use_sparse_attention=self.config.use_sparse_attention,
            use_kt_attention=self.config.use_kt_attention,
        )
        return model.to(self.device)

    def train(
        self,
        model: AlethicodeNFK,
        train_loader: DataLoader,
        val_loader: DataLoader,
    ) -> AlethicodeNFK:
        optimizer = AdamW(
            model.parameters(),
            lr=self.config.learning_rate,
            weight_decay=self.config.weight_decay,
        )
        warmup_epochs = max(1, self.config.max_epochs // 20)
        warmup_scheduler = LambdaLR(
            optimizer,
            lr_lambda=lambda ep: min(1.0, (ep + 1) / warmup_epochs),
        )
        cosine_scheduler = CosineAnnealingWarmRestarts(
            optimizer, T_0=20, T_mult=2, eta_min=1e-6,
        )
        criterion = KTLoss(
            lambda_kt=self.config.lambda_kt,
            lambda_time=self.config.lambda_time,
            lambda_skill=self.config.lambda_skill,
            lambda_ortho=self.config.lambda_ortho,
            label_smoothing=self.config.label_smoothing,
            max_epochs=self.config.max_epochs,
        )

        best_auc = 0.0
        best_state = None
        patience_counter = 0

        for epoch in range(self.config.max_epochs):
            criterion.set_epoch(epoch)
            t0 = time.time()
            train_metrics = self._train_epoch(model, train_loader, optimizer, criterion)
            val_metrics = self._validate(model, val_loader, criterion)
            epoch_time = time.time() - t0

            if epoch < warmup_epochs:
                warmup_scheduler.step()
            else:
                cosine_scheduler.step(epoch - warmup_epochs)

            lr = optimizer.param_groups[0]["lr"]
            gpu_mem = self._gpu_mem_mb()

            record = {
                "epoch": epoch + 1,
                "train_loss": train_metrics["loss"],
                "train_loss_kt": train_metrics["loss_kt"],
                "val_loss": val_metrics["loss"],
                "val_loss_kt": val_metrics["loss_kt"],
                "val_auc": val_metrics["auc"],
                "val_acc": val_metrics["accuracy"],
                "val_f1": val_metrics["f1"],
                "lr": lr,
                "epoch_time_sec": round(epoch_time, 1),
                "gpu_mem_mb": gpu_mem,
            }
            self.history.append(record)
            self._tb_log(record)

            logger.info(
                f"Epoch {epoch+1}: loss_kt={train_metrics['loss_kt']:.4f} "
                f"val_loss_kt={val_metrics['loss_kt']:.4f} val_auc={val_metrics['auc']:.4f} "
                f"val_f1={val_metrics['f1']:.4f} lr={lr:.6f} "
                f"time={epoch_time:.1f}s gpu={gpu_mem}MB"
            )

            if val_metrics["auc"] > best_auc:
                best_auc = val_metrics["auc"]
                best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}
                patience_counter = 0
            else:
                patience_counter += 1
                if patience_counter >= self.config.patience:
                    logger.info(f"Early stopping (patience) at epoch {epoch+1}, best AUC={best_auc:.4f}")
                    break

        if best_state is not None:
            model.load_state_dict(best_state)
        return model

    def _train_epoch(
        self,
        model: AlethicodeNFK,
        loader: DataLoader,
        optimizer: torch.optim.Optimizer,
        criterion: KTLoss,
    ) -> dict[str, float]:
        model.train()
        total_loss = 0.0
        total_loss_kt = 0.0
        n_batches = 0

        for batch_idx, batch in enumerate(loader):
            batch = {k: v.to(self.device) for k, v in batch.items()}
            optimizer.zero_grad()

            output = model(
                question_ids=batch["question_ids"],
                skill_ids=batch["skill_ids"],
                responses=batch["responses"],
                delta_t=batch.get("delta_t"),
                pad_mask=batch.get("pad_mask"),
            )

            losses = criterion(
                output, batch["labels"],
                delta_t_next=batch.get("delta_t_next"),
                next_skill_ids=batch.get("next_skill_ids"),
                pad_mask=batch.get("pad_mask"),
            )
            losses["loss_total"].backward()

            nn.utils.clip_grad_norm_(model.parameters(), self.config.grad_clip)
            optimizer.step()

            total_loss += losses["loss_total"].item()
            total_loss_kt += losses["loss_kt"].item()
            n_batches += 1
            self.global_step += 1

            if self.writer and batch_idx % 50 == 0:
                self.writer.add_scalar("batch/train_loss", losses["loss_total"].item(), self.global_step)
                self.writer.add_scalar("batch/train_loss_kt", losses["loss_kt"].item(), self.global_step)
                for aux_key in ("loss_time", "loss_skill", "loss_ortho"):
                    if aux_key in losses:
                        self.writer.add_scalar(f"batch/{aux_key}", losses[aux_key].item(), self.global_step)

        denom = max(n_batches, 1)
        return {"loss": total_loss / denom, "loss_kt": total_loss_kt / denom}

    @torch.no_grad()
    def _validate(
        self,
        model: AlethicodeNFK,
        loader: DataLoader,
        criterion: KTLoss,
    ) -> dict[str, float]:
        model.eval()
        all_preds = []
        all_labels = []
        total_loss = 0.0
        total_loss_kt = 0.0
        n_batches = 0

        for batch in loader:
            batch = {k: v.to(self.device) for k, v in batch.items()}

            output = model(
                question_ids=batch["question_ids"],
                skill_ids=batch["skill_ids"],
                responses=batch["responses"],
                delta_t=batch.get("delta_t"),
                pad_mask=batch.get("pad_mask"),
            )

            losses = criterion(
                output, batch["labels"],
                delta_t_next=batch.get("delta_t_next"),
                next_skill_ids=batch.get("next_skill_ids"),
                pad_mask=batch.get("pad_mask"),
            )
            total_loss += losses["loss_total"].item()
            total_loss_kt += losses["loss_kt"].item()
            n_batches += 1

            labels = batch["labels"]
            valid_mask = labels != -1
            if valid_mask.sum() > 0:
                preds = torch.sigmoid(output["kt_pred"][valid_mask]).cpu().numpy()
                lbls = labels[valid_mask].cpu().numpy()
                all_preds.append(preds)
                all_labels.append(lbls)

        if all_preds:
            all_preds_np = np.concatenate(all_preds)
            all_labels_np = np.concatenate(all_labels)
            metrics = compute_metrics(all_preds_np, all_labels_np)
        else:
            metrics = {"auc": 0.0, "accuracy": 0.0, "f1": 0.0, "rmse": 1.0}

        denom = max(n_batches, 1)
        metrics["loss"] = total_loss / denom
        metrics["loss_kt"] = total_loss_kt / denom
        return metrics

    def _tb_log(self, record: dict, prefix: str = ""):
        if not self.writer:
            return
        step = self.global_step
        tag_prefix = f"{prefix}/" if prefix else ""
        for key in ["train_loss", "train_loss_kt", "val_loss", "val_loss_kt", "val_auc", "val_acc", "val_f1", "lr"]:
            if key in record:
                self.writer.add_scalar(f"{tag_prefix}{key}", record[key], step)
        if "gpu_mem_mb" in record and record["gpu_mem_mb"] > 0:
            self.writer.add_scalar(f"{tag_prefix}gpu_mem_mb", record["gpu_mem_mb"], step)
        self.writer.flush()

    def _gpu_mem_mb(self) -> float:
        if torch.cuda.is_available():
            return round(torch.cuda.max_memory_allocated() / 1024 / 1024, 1)
        return 0.0

    def save_checkpoint(self, model: AlethicodeNFK, path: str | Path) -> None:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        torch.save(
            {
                "model_state_dict": model.state_dict(),
                "config": self.config.__dict__,
                "history": self.history,
                "variant": model.get_variant_name(),
            },
            path,
        )
        logger.info(f"Checkpoint saved to {path}")

    def load_checkpoint(
        self, path: str | Path
    ) -> tuple[AlethicodeNFK, dict]:
        # weights_only=False 是本项目历史行为，但 PyTorch 2.6+ 默认 weights_only=True，
        # 这里我们主动先以 weights_only=True 尝试（仅加载纯 tensor），失败时回退到 False
        # 以兼容包含 NFKConfig dataclass 的旧 checkpoint；显式降级会在日志中提示。
        try:
            ckpt = torch.load(path, map_location=self.device, weights_only=True)
        except Exception as e:
            logger.warning(
                "Falling back to weights_only=False for %s (%s). "
                "Only load trusted checkpoints produced by this project.",
                path, e,
            )
            ckpt = torch.load(path, map_location=self.device, weights_only=False)
        self.config = NFKConfig(**ckpt["config"])
        model = self.build_model()
        model.load_state_dict(ckpt["model_state_dict"])
        self.history = ckpt.get("history", [])
        return model, ckpt

    def close(self):
        if self.writer:
            self.writer.close()
