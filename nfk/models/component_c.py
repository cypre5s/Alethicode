"""
Component C: simpleKT 风格单层交叉注意力

用当前题目嵌入作为查询向量，在 LSTM 隐状态序列上做单层点积注意力，
选择性检索与当前问题最相关的历史学习经验。

与 Component A (DKTBase) 共享题目嵌入表，通过投影层适配维度差异：
  Q = W_Q · proj(shared_embed(q_t))    # 共享嵌入 + 投影
  K = W_K · [h'_1, ..., h'_t]
  V = W_V · [h'_1, ..., h'_t]
  α = softmax(QK^T / √d)
  context = αV
  output = LayerNorm(context + h)

文献: Zitao Liu et al., "simpleKT", ICLR 2023
"""

from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F


class SimpleKTAttention(nn.Module):
    """
    simpleKT 风格单层交叉注意力。

    接收外部传入的题目嵌入（与 encoder 共享），
    通过投影层适配 embed_dim → hidden_dim。
    """

    def __init__(
        self,
        hidden_dim: int = 256,
        embed_dim: int = 128,
        n_heads: int = 1,
        n_skills: int = 0,
        dropout: float = 0.2,
    ):
        super().__init__()
        self.n_heads = n_heads
        self.head_dim = hidden_dim // n_heads
        self.scale = self.head_dim ** -0.5

        self.embed_proj = nn.Linear(embed_dim, hidden_dim)

        self.skill_embed = None
        if n_skills > 0:
            self.skill_embed = nn.Embedding(n_skills, embed_dim, padding_idx=0)
            nn.init.xavier_uniform_(self.skill_embed.weight[1:])

        self.W_q = nn.Linear(hidden_dim, hidden_dim)
        self.W_k = nn.Linear(hidden_dim, hidden_dim)
        self.W_v = nn.Linear(hidden_dim, hidden_dim)
        self.W_o = nn.Linear(hidden_dim, hidden_dim)

        self.dropout = nn.Dropout(dropout)
        self.layer_norm = nn.LayerNorm(hidden_dim)

    def forward(
        self,
        h: torch.Tensor,
        q_embed: torch.Tensor,
        pad_mask: torch.Tensor | None = None,
        skill_ids: torch.Tensor | None = None,
    ) -> tuple[torch.Tensor, torch.Tensor]:
        """
        Args:
            h:         (B, T, D) 编码器/注意力输出的隐状态序列
            q_embed:   (B, T, embed_dim) 共享的题目嵌入
            pad_mask:  (B, T) True=padding
            skill_ids: (B, T) 知识点 ID（可选，用于增强 query）

        Returns:
            enhanced_h:        (B, T, D) 增强后的隐状态
            attention_weights: (B, T, T) 平均注意力权重
        """
        B, T, D = h.shape

        query_embed = q_embed
        if self.skill_embed is not None and skill_ids is not None:
            query_embed = q_embed + self.skill_embed(skill_ids)
        q_proj = self.embed_proj(query_embed)  # (B, T, hidden_dim)
        Q = self.W_q(q_proj).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)
        K = self.W_k(h).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)
        V = self.W_v(h).view(B, T, self.n_heads, self.head_dim).transpose(1, 2)

        scores = torch.matmul(Q, K.transpose(-2, -1)) * self.scale

        causal_mask = torch.triu(
            torch.ones(T, T, device=h.device, dtype=torch.bool), diagonal=1
        )
        scores = scores.masked_fill(causal_mask.unsqueeze(0).unsqueeze(0), float("-inf"))

        if pad_mask is not None:
            scores = scores.masked_fill(
                pad_mask.unsqueeze(1).unsqueeze(2), float("-inf")
            )

        # 整行 -inf 时 softmax 会产生 NaN；这类行来自 padding 或无效 query，
        # 输出零权重即可（后续 pad_mask 会把结果置零）。
        all_masked = (scores == float("-inf")).all(dim=-1, keepdim=True)
        safe_scores = scores.masked_fill(all_masked, 0.0)
        attn_weights = F.softmax(safe_scores, dim=-1)
        attn_weights = attn_weights.masked_fill(all_masked, 0.0)
        attn_weights = self.dropout(attn_weights)

        context = torch.matmul(attn_weights, V)
        context = context.transpose(1, 2).contiguous().view(B, T, D)
        context = self.W_o(context)

        enhanced_h = self.layer_norm(context + h)
        avg_attn = attn_weights.mean(dim=1)

        return enhanced_h, avg_attn
