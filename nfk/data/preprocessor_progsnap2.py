"""
ProgSnap2 格式预处理（CodeWorkout 等编程教育数据集）。

预期文件结构:
  data_dir/
    MainTable.csv       # 主事件表
    CodeState.csv       # 代码快照
    DatasetMetadata.csv # 元数据
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.model_selection import KFold


class ProgSnap2Preprocessor:

    MIN_INTERACTIONS = 3

    def __init__(self, data_dir: str | Path):
        self.data_dir = Path(data_dir)
        self.question_map: dict[Any, int] = {}
        self.skill_map: dict[Any, int] = {}

    def load_and_preprocess(self) -> list[dict]:
        main_table = pd.read_csv(self.data_dir / "MainTable.csv")

        submit_events = main_table[main_table["EventType"] == "Submit"].copy()
        submit_events = submit_events.dropna(subset=["ProblemID"])

        if "Score" in submit_events.columns:
            submit_events["correct"] = (submit_events["Score"] >= 1.0).astype(int)
        else:
            submit_events["correct"] = 0

        if "KnowledgeComponent" in submit_events.columns:
            submit_events = submit_events.dropna(subset=["KnowledgeComponent"])
            kc_col = "KnowledgeComponent"
        else:
            kc_col = "ProblemID"

        user_counts = submit_events.groupby("SubjectID").size()
        valid_users = user_counts[user_counts >= self.MIN_INTERACTIONS].index
        submit_events = submit_events[submit_events["SubjectID"].isin(valid_users)]

        self.question_map = {q: i + 1 for i, q in enumerate(submit_events["ProblemID"].unique())}
        self.skill_map = {s: i + 1 for i, s in enumerate(submit_events[kc_col].unique())}

        submit_events["question_id"] = submit_events["ProblemID"].map(self.question_map)
        submit_events["skill_id"] = submit_events[kc_col].map(self.skill_map)

        if "ServerTimestamp" in submit_events.columns:
            submit_events["timestamp"] = pd.to_datetime(
                submit_events["ServerTimestamp"]
            ).astype(np.int64) // 10**9
        elif "ClientTimestamp" in submit_events.columns:
            submit_events["timestamp"] = pd.to_datetime(
                submit_events["ClientTimestamp"]
            ).astype(np.int64) // 10**9
        else:
            submit_events["timestamp"] = range(len(submit_events))

        submit_events = submit_events.sort_values(["SubjectID", "timestamp"])

        code_states: dict[str, str] = {}
        code_state_path = self.data_dir / "CodeState.csv"
        if code_state_path.exists():
            cs_df = pd.read_csv(code_state_path)
            code_states = dict(zip(cs_df["CodeStateID"].astype(str), cs_df["Code"].fillna("")))

        sequences = []
        for user_id, group in submit_events.groupby("SubjectID"):
            code_texts = []
            if code_states and "CodeStateID" in group.columns:
                code_texts = [
                    code_states.get(str(csid), "") for csid in group["CodeStateID"]
                ]

            seq: dict[str, Any] = {
                "user_id": user_id,
                "question_ids": group["question_id"].values.astype(np.int64),
                "skill_ids": group["skill_id"].values.astype(np.int64),
                "responses": group["correct"].values.astype(np.int64),
                "timestamps": group["timestamp"].values.astype(np.float64),
            }
            if code_texts:
                seq["code_texts"] = code_texts
            sequences.append(seq)

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
