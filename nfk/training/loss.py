"""
知识追踪损失函数。

L_total = L_kt + λ_time · L_time + λ_skill · L_skill

- L_kt:    Binary CE，知识追踪主任务
- L_time:  CE，B 辅助任务——预测当前位置到下一交互的时间间隔类别（仅 Full 变体）
- L_skill: CE，C 辅助任务——预测下一交互的知识点 ID（仅 Full 变体）

辅助损失通过不同的监督信号迫使 B 和 C 学到互补的表征：
B 被训练为时间遗忘专家，C 被训练为内容转移专家。
"""

import torch
import torch.nn as nn
import torch.nn.functional as F

from ..models.nfk_model import N_TIME_BUCKETS


class KTLoss(nn.Module):

    def __init__(
        self,
        lambda_kt: float = 1.0,
        lambda_time: float = 0.15,
        lambda_skill: float = 0.15,
        lambda_ortho: float = 0.1,
        label_smoothing: float = 0.05,
        max_epochs: int = 100,
    ):
        super().__init__()
        self.lambda_kt = lambda_kt
        self.lambda_time_max = lambda_time
        self.lambda_skill_max = lambda_skill
        self.lambda_ortho = lambda_ortho
        self.label_smoothing = label_smoothing
        self.max_epochs = max_epochs
        self._current_epoch = 0

    def set_epoch(self, epoch: int) -> None:
        self._current_epoch = epoch

    def _aux_weight(self) -> float:
        """
        辅助损失权重调度: 前 5 epoch 线性 warmup → 余弦衰减到 0.05×peak。
        在 AUC 达峰前完成组件分工引导，之后快速让主任务主导。
        """
        import math
        warmup_end = 5
        if self._current_epoch < warmup_end:
            return self._current_epoch / warmup_end
        decay_progress = (self._current_epoch - warmup_end) / max(self.max_epochs - warmup_end, 1)
        return 0.05 + 0.95 * 0.5 * (1.0 + math.cos(math.pi * decay_progress))

    def forward(
        self,
        model_output: dict[str, torch.Tensor],
        labels: torch.Tensor,
        delta_t_next: torch.Tensor | None = None,
        next_skill_ids: torch.Tensor | None = None,
        pad_mask: torch.Tensor | None = None,
    ) -> dict[str, torch.Tensor]:
        """
        Args:
            model_output:   模型 forward 输出
            labels:         (B, T) 知识追踪标签，-1 表示忽略
            delta_t_next:   (B, T) 到下一步交互的时间差（秒），-1 表示忽略
            next_skill_ids: (B, T) 下一步交互的知识点 ID，-1 表示忽略
            pad_mask:       (B, T) True=padding
        """
        kt_pred = model_output["kt_pred"]

        valid_mask = labels != -1
        if valid_mask.sum() == 0:
            zero = torch.tensor(0.0, device=kt_pred.device, requires_grad=True)
            return {"loss_total": zero, "loss_kt": zero}

        target = labels[valid_mask].float()
        if self.label_smoothing > 0:
            target = target * (1.0 - self.label_smoothing) + 0.5 * self.label_smoothing

        loss_kt = F.binary_cross_entropy_with_logits(
            kt_pred[valid_mask].float(),
            target,
        )

        loss_total = self.lambda_kt * loss_kt
        losses = {"loss_total": loss_total, "loss_kt": loss_kt}

        aux_scale = self._aux_weight()

        if "time_logits" in model_output and delta_t_next is not None:
            loss_time = self._time_loss(
                model_output["time_logits"], delta_t_next, valid_mask,
            )
            loss_total = loss_total + self.lambda_time_max * aux_scale * loss_time
            losses["loss_time"] = loss_time

        if "skill_logits" in model_output and next_skill_ids is not None:
            loss_skill = self._skill_loss(
                model_output["skill_logits"], next_skill_ids, valid_mask,
            )
            loss_total = loss_total + self.lambda_skill_max * aux_scale * loss_skill
            losses["loss_skill"] = loss_skill

        if self.lambda_ortho > 0 and "h_b" in model_output and "h_c" in model_output:
            loss_ortho = self._ortho_loss(
                model_output["h_b"], model_output["h_c"], pad_mask,
            )
            loss_total = loss_total + self.lambda_ortho * loss_ortho
            losses["loss_ortho"] = loss_ortho

        losses["aux_scale"] = torch.tensor(aux_scale)
        losses["loss_total"] = loss_total
        return losses

    def _ortho_loss(
        self,
        h_b: torch.Tensor,
        h_c: torch.Tensor,
        pad_mask: torch.Tensor | None,
    ) -> torch.Tensor:
        """惩罚 h_b 和 h_c 的余弦相似度，推动互补表征。"""
        if pad_mask is not None:
            valid = ~pad_mask
            h_b_flat = h_b[valid]
            h_c_flat = h_c[valid]
        else:
            h_b_flat = h_b.reshape(-1, h_b.size(-1))
            h_c_flat = h_c.reshape(-1, h_c.size(-1))

        if h_b_flat.size(0) == 0:
            return torch.tensor(0.0, device=h_b.device, requires_grad=True)

        cos_sim = F.cosine_similarity(h_b_flat, h_c_flat, dim=-1)
        return cos_sim.abs().mean()

    def _time_loss(
        self,
        time_logits: torch.Tensor,
        delta_t_next: torch.Tensor,
        valid_mask: torch.Tensor,
    ) -> torch.Tensor:
        """
        B 辅助: 将连续时间差离散化为 N_TIME_BUCKETS 个桶后做分类。
        桶边界（天）: [0, 0.042) = <1h, [0.042, 1) = 1h-1d, [1, 7) = 1d-1w, [7, inf) = >1w
        """
        aux_mask = valid_mask & (delta_t_next >= 0)
        if aux_mask.sum() == 0:
            return torch.tensor(0.0, device=time_logits.device, requires_grad=True)

        dt_days = delta_t_next[aux_mask].float() / 86400.0
        buckets = torch.zeros_like(dt_days, dtype=torch.long)
        buckets[dt_days >= 0.042] = 1   # ≥1 hour
        buckets[dt_days >= 1.0] = 2     # ≥1 day
        buckets[dt_days >= 7.0] = 3     # ≥1 week
        buckets = buckets.clamp(max=N_TIME_BUCKETS - 1)

        return F.cross_entropy(time_logits[aux_mask], buckets)

    def _skill_loss(
        self,
        skill_logits: torch.Tensor,
        next_skill_ids: torch.Tensor,
        valid_mask: torch.Tensor,
    ) -> torch.Tensor:
        """C 辅助: 预测下一步交互的知识点 ID。"""
        aux_mask = valid_mask & (next_skill_ids >= 0)
        if aux_mask.sum() == 0:
            return torch.tensor(0.0, device=skill_logits.device, requires_grad=True)

        return F.cross_entropy(
            skill_logits[aux_mask],
            next_skill_ids[aux_mask],
        )
