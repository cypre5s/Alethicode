"""
ASSISTments 2009 数据预处理。

必需列: user_id, skill_name, correct, problem_id
可选列: ms_first_response, hint_count, opportunity
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.model_selection import KFold


class ASSISTmentsPreprocessor:

    MIN_INTERACTIONS = 5

    def __init__(self, data_path: str | Path):
        self.data_path = Path(data_path)
        self.question_map: dict[Any, int] = {}
        self.skill_map: dict[Any, int] = {}

    def load_and_preprocess(self) -> list[dict]:
        df = pd.read_csv(self.data_path, encoding="latin-1", low_memory=False)

        df = df.dropna(subset=["skill_name"])
        df["correct"] = df["correct"].astype(int).clip(0, 1)

        user_counts = df.groupby("user_id").size()
        valid_users = user_counts[user_counts >= self.MIN_INTERACTIONS].index
        df = df[df["user_id"].isin(valid_users)]

        self.skill_map = {s: i + 1 for i, s in enumerate(df["skill_name"].unique())}
        self.question_map = {q: i + 1 for i, q in enumerate(df["problem_id"].unique())}

        df["skill_id"] = df["skill_name"].map(self.skill_map)
        df["question_id"] = df["problem_id"].map(self.question_map)

        if "ms_first_response" in df.columns:
            df["timestamp"] = pd.to_numeric(df["ms_first_response"], errors="coerce").fillna(0)
        else:
            df["timestamp"] = range(len(df))

        df = df.sort_values(["user_id", "timestamp"])

        sequences = []
        for user_id, group in df.groupby("user_id"):
            sequences.append({
                "user_id": user_id,
                "question_ids": group["question_id"].values.astype(np.int64),
                "skill_ids": group["skill_id"].values.astype(np.int64),
                "responses": group["correct"].values.astype(np.int64),
                "timestamps": group["timestamp"].values.astype(np.float64),
            })

        return sequences

    def get_splits(
        self, sequences: list[dict], n_folds: int = 5, seed: int = 42
    ) -> list[tuple[list[dict], list[dict]]]:
        kf = KFold(n_splits=n_folds, shuffle=True, random_state=seed)
        indices = np.arange(len(sequences))
        splits = []
        for train_idx, val_idx in kf.split(indices):
            train_seqs = [sequences[i] for i in train_idx]
            val_seqs = [sequences[i] for i in val_idx]
            splits.append((train_seqs, val_seqs))
        return splits

    @property
    def n_questions(self) -> int:
        return len(self.question_map) + 1

    @property
    def n_skills(self) -> int:
        return len(self.skill_map) + 1
