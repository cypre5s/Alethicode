"""
Component A: DKT (Deep Knowledge Tracing) 基座模型

基于 LSTM 的序列编码器，将学生交互序列 (question_id, response) 编码为隐状态。

文献: Piech et al., "Deep Knowledge Tracing", NeurIPS 2015
"""

import torch
import torch.nn as nn


class DKTBase(nn.Module):
    """
    DKT LSTM 基座编码器。

    输入:
        question_ids: (B, T) 题目 ID 序列
        responses:    (B, T) 作答正确性 0/1

    输出:
        (B, T, hidden_dim) 隐状态序列
    """

    def __init__(
        self,
        n_questions: int,
        embed_dim: int = 128,
        hidden_dim: int = 256,
        dropout: float = 0.1,
    ):
        super().__init__()
        self.hidden_dim = hidden_dim

        self.q_embed = nn.Embedding(n_questions, embed_dim, padding_idx=0)
        self.r_embed = nn.Embedding(2, embed_dim)
        self._init_embeddings()

        self.lstm = nn.LSTM(
            input_size=embed_dim * 2,
            hidden_size=hidden_dim,
            num_layers=1,
            batch_first=True,
            dropout=0.0,
        )
        self.dropout = nn.Dropout(dropout)

    def _init_embeddings(self):
        nn.init.xavier_uniform_(self.q_embed.weight[1:])
        nn.init.xavier_uniform_(self.r_embed.weight)

    def forward(
        self,
        question_ids: torch.Tensor,
        responses: torch.Tensor,
    ) -> tuple[torch.Tensor, torch.Tensor]:
        q_emb = self.q_embed(question_ids)   # (B, T, embed_dim)
        r_emb = self.r_embed(responses)       # (B, T, embed_dim)
        x = torch.cat([q_emb, r_emb], dim=-1)  # (B, T, embed_dim*2)

        h, _ = self.lstm(x)  # (B, T, hidden_dim)
        h = self.dropout(h)
        return h, q_emb
