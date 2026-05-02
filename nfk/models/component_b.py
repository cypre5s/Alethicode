"""
Component B: 遗忘感知稀疏注意力模块

- Top-K 稀疏注意力门控：仅保留最相关的 K 个历史交互（sparseKT 启发）
- 遗忘线性偏置：按时间衰减调制注意力权重（FoLiBiKT / LefoKT 启发）
  衰减函数: exp(-gamma * delta_t)，gamma 为可学习参数
"""

import torch
import torch.nn as nn
import torch.nn.functional as F


class ForgettingBias(nn.Module):
    """
    可学习的时间衰减偏置。

    对每个知识点学习独立的衰减速率 gamma，
    用 softplus 保证 gamma > 0。
    """

    def __init__(self, n_skills: int):
        super().__init__()
        self.gamma_raw = nn.Parameter(torch.zeros(n_skills))

    @property
    def gamma(self) -> torch.Tensor:
        return F.softplus(self.gamma_raw)

    def forward(self, delta_t: torch.Tensor, skill_ids: torch.Tensor) -> torch.Tensor:
        """
        Args:
            delta_t:   (B, T_q, T_kv) 时间差矩阵（秒，已归一化到天）
            skill_ids: (B, T_kv) 知识点 ID

        Returns:
            (B, T_q, T_kv) 遗忘偏置（负值，加到注意力 logits 上）
        """
        g = self.gamma[skill_ids]  # (B, T_kv)
        g = g.unsqueeze(1)  # (B, 1, T_kv)
        return -g * delta_t


class SparseForgetAttention(nn.Module):
    """
    稀疏注意力 + 遗忘偏置。

    流程:
        1. 计算标准缩放点积注意力 logits
        2. 叠加遗忘线性偏置
        3. Top-K 门控：仅保留每个 query 位置的前 K 个 KV 位置
        4. Softmax + 加权求和
    """

    def __init__(
        self,
        hidden_dim: int,
        n_heads: int = 8,
        top_k: int = 20,
        n_skills: int = 1,
        dropout: float = 0.1,
    ):
        super().__init__()
        self.n_heads = n_heads
        self.top_k = top_k
        self.head_dim = hidden_dim // n_heads

        self.q_proj = nn.Linear(hidden_dim, hidden_dim)
        self.k_proj = nn.Linear(hidden_dim, hidden_dim)
        self.v_proj = nn.Linear(hidden_dim, hidden_dim)
        self.o_proj = nn.Linear(hidden_dim, hidden_dim)

        self.forgetting = ForgettingBias(n_skills)
        self.attn_dropout = nn.Dropout(dropout)
        self.layer_norm = nn.LayerNorm(hidden_dim)
        self.scale = self.head_dim ** -0.5

    def forward(
        self,
        h: torch.Tensor,
        delta_t: torch.Tensor | None = None,
        skill_ids: torch.Tensor | None = None,
        pad_mask: torch.Tensor | None = None,
    ) -> torch.Tensor:
        """
        Args:
            h:         (B, T, D) DKT 编码器输出
            delta_t:   (B, T, T) 时间差矩阵
            skill_ids: (B, T)    知识点 ID
            pad_mask:  (B, T)    True 表示 padding

        Returns:
            (B, T, D) 经过稀疏遗忘注意力精炼的隐状态
        """
        residual = h
        B, T, D = h.shape

        Q = self.q_proj(h).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)
        K = self.k_proj(h).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)
        V = self.v_proj(h).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)

        attn_logits = torch.matmul(Q, K.transpose(-2, -1)) * self.scale  # (B, H, T, T)

        if delta_t is not None and skill_ids is not None:
            forget_bias = self.forgetting(delta_t, skill_ids)  # (B, T, T)
            attn_logits = attn_logits + forget_bias.unsqueeze(1)

        causal_mask = torch.triu(
            torch.ones(T, T, device=h.device, dtype=torch.bool), diagonal=1
        )
        attn_logits = attn_logits.masked_fill(causal_mask.unsqueeze(0).unsqueeze(0), float("-inf"))

        if pad_mask is not None:
            attn_logits = attn_logits.masked_fill(
                pad_mask.unsqueeze(1).unsqueeze(2), float("-inf")
            )

        attn_logits = self._sparse_topk(attn_logits)

        # 防止整行 -inf（所有 kv 位置被 mask）导致 softmax 产生 NaN。
        # 这类行实际对应 padding 或序列起始，注意力结果无意义，输出 0 向量即可。
        all_masked = (attn_logits == float("-inf")).all(dim=-1, keepdim=True)
        safe_logits = attn_logits.masked_fill(all_masked, 0.0)
        attn_weights = F.softmax(safe_logits, dim=-1)
        attn_weights = attn_weights.masked_fill(all_masked, 0.0)
        attn_weights = self.attn_dropout(attn_weights)

        out = torch.matmul(attn_weights, V)  # (B, H, T, head_dim)
        out = out.transpose(1, 2).contiguous().view(B, T, D)
        out = self.o_proj(out)

        return self.layer_norm(out + residual)

    def _sparse_topk(self, logits: torch.Tensor) -> torch.Tensor:
        """对每个 query 位置仅保留 top-K 个 KV 位置的注意力。"""
        T = logits.size(-1)
        k = min(self.top_k, T)
        topk_vals, _ = logits.topk(k, dim=-1)
        threshold = topk_vals[..., -1:].detach()
        mask = logits < threshold
        return logits.masked_fill(mask, float("-inf"))
