"""
AlethicodeNFK 完整模型

组合三个组件：
  A: DKTBase (LSTM 序列编码)
  B: SparseForgetAttention (FoLiBiKT 遗忘线性偏置注意力) — 时间遗忘维度
  C: SimpleKTAttention (simpleKT 风格交叉注意力，共享嵌入) — 内容相关性维度

Full 变体采用:
  - 并行融合架构: B 和 C 从同一个 encoder 输出并行计算，FusionGate 门控融合
  - 组件专用辅助损失: B 预测时间间隔类别 (时间专家)，C 预测下一知识点 (内容专家)
    通过不同的监督信号迫使两个组件捕获互补信息

消融变体:
  - A+B+C (Full):  use_sparse_attention=True,  use_kt_attention=True
  - A+B:           use_sparse_attention=True,  use_kt_attention=False
  - A+C:           use_sparse_attention=False, use_kt_attention=True
  - A only:        use_sparse_attention=False, use_kt_attention=False
"""

from __future__ import annotations

import torch
import torch.nn as nn

from .component_a import DKTBase
from .component_b import SparseForgetAttention
from .component_c import SimpleKTAttention

N_TIME_BUCKETS = 4


class _GradScale(torch.autograd.Function):
    """反向传播时对梯度乘以缩放因子，前向传播保持不变。"""

    @staticmethod
    def forward(ctx, x: torch.Tensor, scale: float) -> torch.Tensor:
        ctx.scale = scale
        return x

    @staticmethod
    def backward(ctx, grad_output: torch.Tensor):
        return grad_output * ctx.scale, None


def _scale_grad(x: torch.Tensor, scale: float) -> torch.Tensor:
    """缩放反向传播梯度，不影响前向传播值。"""
    if scale == 1.0:
        return x
    return _GradScale.apply(x, scale)


class FusionGate(nn.Module):
    """
    通道级门控融合模块。

    对 hidden_dim 的每个通道独立计算 B/C 的混合权重，
    比标量门控有更精细的信息选择能力。

      gates = sigmoid(MLP([h, h_b, h_c]))   # (B, T, D*2)
      g_b, g_c = gates[:D], gates[D:]       # 通道级
      output = g_b * h_b + g_c * h_c + (1 - g_b - g_c).clamp(0) * h
    """

    def __init__(self, hidden_dim: int, dropout: float = 0.2):
        super().__init__()
        self.hidden_dim = hidden_dim
        self.gate_proj = nn.Sequential(
            nn.Linear(hidden_dim * 3, hidden_dim),
            nn.GELU(),
            nn.Linear(hidden_dim, hidden_dim * 2),
        )
        self.layer_norm = nn.LayerNorm(hidden_dim)
        self.dropout = nn.Dropout(dropout)

    def forward(
        self, h: torch.Tensor, h_b: torch.Tensor, h_c: torch.Tensor
    ) -> torch.Tensor:
        gate_input = torch.cat([h, h_b, h_c], dim=-1)
        gates = torch.sigmoid(self.gate_proj(gate_input))  # (B, T, D*2)
        g_b = gates[..., :self.hidden_dim]
        g_c = gates[..., self.hidden_dim:]
        g_residual = (1.0 - g_b - g_c).clamp(min=0.0)

        fused = g_b * h_b + g_c * h_c + g_residual * h
        return self.layer_norm(self.dropout(fused))


class AlethicodeNFK(nn.Module):

    def __init__(
        self,
        n_questions: int,
        n_skills: int,
        embed_dim: int = 128,
        hidden_dim: int = 256,
        n_heads: int = 8,
        top_k: int = 20,
        n_kt_heads: int = 1,
        dropout: float = 0.2,
        max_seq_len: int = 200,
        use_sparse_attention: bool = True,
        use_kt_attention: bool = True,
    ):
        super().__init__()
        self.use_sparse_attention = use_sparse_attention
        self.use_kt_attention = use_kt_attention
        self.hidden_dim = hidden_dim
        self.n_skills = n_skills

        self.encoder = DKTBase(
            n_questions=n_questions,
            embed_dim=embed_dim,
            hidden_dim=hidden_dim,
            dropout=dropout,
        )

        if use_sparse_attention:
            self.sparse_attn = SparseForgetAttention(
                hidden_dim=hidden_dim,
                n_heads=n_heads,
                top_k=top_k,
                n_skills=n_skills,
                dropout=dropout,
            )

        if use_kt_attention:
            self.kt_attn = SimpleKTAttention(
                hidden_dim=hidden_dim,
                embed_dim=embed_dim,
                n_heads=n_kt_heads,
                n_skills=n_skills,
                dropout=dropout,
            )

        if use_sparse_attention and use_kt_attention:
            self.fusion_gate = FusionGate(hidden_dim, dropout=dropout)
            self.time_head = nn.Linear(hidden_dim, N_TIME_BUCKETS)
            self.skill_head = nn.Linear(hidden_dim, n_skills)

        self.kt_head = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim // 2, 1),
        )

    def forward(
        self,
        question_ids: torch.Tensor,
        skill_ids: torch.Tensor,
        responses: torch.Tensor,
        delta_t: torch.Tensor | None = None,
        pad_mask: torch.Tensor | None = None,
    ) -> dict[str, torch.Tensor]:
        h, q_embed = self.encoder(question_ids, responses)

        result: dict[str, torch.Tensor] = {}

        if self.use_sparse_attention and self.use_kt_attention:
            h_b = self.sparse_attn(h, delta_t, skill_ids, pad_mask)
            h_c, attn_weights = self.kt_attn(h, q_embed, pad_mask, skill_ids)
            result["attention_weights"] = attn_weights
            result["h_b"] = h_b
            result["h_c"] = h_c
            result["time_logits"] = self.time_head(_scale_grad(h_b, 0.1))
            result["skill_logits"] = self.skill_head(_scale_grad(h_c, 0.1))
            h = self.fusion_gate(h, h_b, h_c)
        elif self.use_sparse_attention:
            h = self.sparse_attn(h, delta_t, skill_ids, pad_mask)
        elif self.use_kt_attention:
            h, attn_weights = self.kt_attn(h, q_embed, pad_mask, skill_ids)
            result["attention_weights"] = attn_weights

        result["kt_pred"] = self.kt_head(h).squeeze(-1)

        return result

    def predict(
        self,
        question_ids: torch.Tensor,
        skill_ids: torch.Tensor,
        responses: torch.Tensor,
        delta_t: torch.Tensor | None = None,
        pad_mask: torch.Tensor | None = None,
    ) -> dict[str, torch.Tensor]:
        self.eval()
        with torch.no_grad():
            out = self.forward(question_ids, skill_ids, responses, delta_t, pad_mask)
            out["kt_prob"] = torch.sigmoid(out["kt_pred"])
        return out

    def get_variant_name(self) -> str:
        if self.use_sparse_attention and self.use_kt_attention:
            return "A+B+C (Full)"
        elif self.use_sparse_attention:
            return "A+B (w/o KTAttn)"
        elif self.use_kt_attention:
            return "A+C (w/o SparseAttn)"
        else:
            return "A only (Base)"

    def count_parameters(self) -> dict[str, int]:
        stats = {
            "encoder": 0, "sparse_attn": 0,
            "kt_attn": 0, "fusion_gate": 0, "kt_head": 0, "total": 0,
        }
        for name, param in self.named_parameters():
            n = param.numel()
            stats["total"] += n
            if name.startswith("encoder"):
                stats["encoder"] += n
            elif name.startswith("sparse_attn"):
                stats["sparse_attn"] += n
            elif name.startswith("kt_attn"):
                stats["kt_attn"] += n
            elif name.startswith("fusion_gate"):
                stats["fusion_gate"] += n
            elif name.startswith("kt_head"):
                stats["kt_head"] += n
        return stats
