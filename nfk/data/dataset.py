"""
KT 数据集与 DataLoader 工具。

- KTDataset: 将预处理后的序列包装为 PyTorch Dataset
- KTCollator: 自定义 collate_fn，处理变长序列的填充和时间差矩阵构建
"""

from __future__ import annotations

import numpy as np
import torch
from torch.utils.data import Dataset


class KTDataset(Dataset):
    """
    知识追踪数据集。

    每个样本是一个学生的交互序列，截断到 max_seq_len。
    """

    def __init__(
        self,
        sequences: list[dict],
        max_seq_len: int = 200,
    ):
        self.sequences = sequences
        self.max_seq_len = max_seq_len

    def __len__(self) -> int:
        return len(self.sequences)

    def __getitem__(self, idx: int) -> dict[str, torch.Tensor]:
        seq = self.sequences[idx]
        seq_len = min(len(seq["question_ids"]), self.max_seq_len)

        question_ids = torch.tensor(seq["question_ids"][:seq_len], dtype=torch.long)
        skill_ids = torch.tensor(seq["skill_ids"][:seq_len], dtype=torch.long)
        responses = torch.tensor(seq["responses"][:seq_len], dtype=torch.long)
        timestamps = torch.tensor(seq["timestamps"][:seq_len], dtype=torch.float)

        return {
            "question_ids": question_ids,
            "skill_ids": skill_ids,
            "responses": responses,
            "timestamps": timestamps,
            "seq_len": torch.tensor(seq_len, dtype=torch.long),
        }


class KTCollator:
    """
    变长序列的 collate 函数。

    功能:
      1. 填充到 batch 内最大长度
      2. 生成 padding mask
      3. 构建时间差矩阵 delta_t (归一化到天)
      4. 构建 labels (下一步 response 作为标签)
    """

    SECONDS_PER_DAY = 86400.0

    def __call__(self, batch: list[dict]) -> dict[str, torch.Tensor]:
        max_len = max(item["seq_len"].item() for item in batch)
        B = len(batch)

        question_ids = torch.zeros(B, max_len, dtype=torch.long)
        skill_ids = torch.zeros(B, max_len, dtype=torch.long)
        responses = torch.zeros(B, max_len, dtype=torch.long)
        timestamps = torch.zeros(B, max_len)
        pad_mask = torch.ones(B, max_len, dtype=torch.bool)
        labels = torch.full((B, max_len), -1, dtype=torch.long)
        delta_t_next = torch.full((B, max_len), -1, dtype=torch.float)
        next_skill_ids = torch.full((B, max_len), -1, dtype=torch.long)

        for i, item in enumerate(batch):
            sl = item["seq_len"].item()
            question_ids[i, :sl] = item["question_ids"]
            skill_ids[i, :sl] = item["skill_ids"]
            responses[i, :sl] = item["responses"]
            timestamps[i, :sl] = item["timestamps"]
            pad_mask[i, :sl] = False
            if sl > 1:
                labels[i, :sl - 1] = item["responses"][1:]
                delta_t_next[i, :sl - 1] = item["timestamps"][1:] - item["timestamps"][:sl - 1]
                next_skill_ids[i, :sl - 1] = item["skill_ids"][1:]

        delta_t = self._build_delta_t(timestamps, pad_mask)

        return {
            "question_ids": question_ids,
            "skill_ids": skill_ids,
            "responses": responses,
            "delta_t": delta_t,
            "pad_mask": pad_mask,
            "labels": labels,
            "delta_t_next": delta_t_next,
            "next_skill_ids": next_skill_ids,
        }

    def _build_delta_t(
        self, timestamps: torch.Tensor, pad_mask: torch.Tensor
    ) -> torch.Tensor:
        """
        构建 (B, T, T) 时间差矩阵，归一化为天。

        delta_t[b, i, j] = (t_i - t_j) / SECONDS_PER_DAY，仅对 i > j 有效。
        """
        t_q = timestamps.unsqueeze(2)  # (B, T, 1)
        t_k = timestamps.unsqueeze(1)  # (B, 1, T)
        delta = (t_q - t_k) / self.SECONDS_PER_DAY
        delta = delta.clamp(min=0.0)
        return delta
