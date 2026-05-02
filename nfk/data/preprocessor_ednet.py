"""
EdNet KT1 数据预处理。

EdNet 文件结构 (KT1):
  data_dir/
    KT1/
      u1.csv
      u2.csv
      ...

每个 CSV 包含列: timestamp, solving_id, question_id, user_answer, elapsed_time
另需 questions.csv 映射 question_id → tags (知识点)
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.model_selection import KFold


class EdNetPreprocessor:

    MIN_INTERACTIONS = 5

    def __init__(self, data_dir: str | Path):
        self.data_dir = Path(data_dir)
        self.question_map: dict[Any, int] = {}
        self.skill_map: dict[Any, int] = {}

    def load_and_preprocess(self) -> list[dict]:
        kt1_dir = self.data_dir / "KT1"
        if not kt1_dir.exists():
            kt1_dir = self.data_dir

        questions_path = self.data_dir / "questions.csv"
        question_tags: dict[str, str] = {}
        question_correct_answers: dict[str, str] = {}
        if questions_path.exists():
            q_df = pd.read_csv(questions_path)
            for _, row in q_df.iterrows():
                qid = str(row["question_id"])
                tags = str(row.get("tags", row.get("part", qid)))
                question_tags[qid] = tags.split(";")[0] if ";" in tags else tags
                if "correct_answer" in q_df.columns and pd.notna(row.get("correct_answer")):
                    question_correct_answers[qid] = str(row["correct_answer"])

        user_files = sorted(kt1_dir.glob("u*.csv"))
        if not user_files:
            user_files = sorted(kt1_dir.glob("*.csv"))

        all_question_ids: set = set()
        all_skill_ids: set = set()
        raw_sequences: list[dict] = []

        for user_file in user_files:
            try:
                df = pd.read_csv(user_file)
            except Exception:
                continue

            if len(df) < self.MIN_INTERACTIONS:
                continue

            if "question_id" not in df.columns:
                continue

            df["question_id"] = df["question_id"].astype(str)

            if "user_answer" in df.columns and "correct_answer" in df.columns:
                df["correct"] = (
                    df["user_answer"].astype(str) == df["correct_answer"].astype(str)
                ).astype(int)
            elif "correct" in df.columns:
                df["correct"] = df["correct"].astype(int).clip(0, 1)
            elif "user_answer" in df.columns and question_correct_answers:
                correct_answers = df["question_id"].map(question_correct_answers)
                missing_answers = int(correct_answers.isna().sum())
                if missing_answers:
                    raise ValueError(
                        f"EdNet questions.csv missing correct_answer for "
                        f"{missing_answers} rows in {user_file}"
                    )
                df["correct"] = (
                    df["user_answer"].astype(str) == correct_answers.astype(str)
                ).astype(int)
            else:
                raise ValueError(
                    "EdNet data requires either a correct column, user-file "
                    "correct_answer column, or questions.csv correct_answer mapping"
                )

            df["skill"] = df["question_id"].map(
                lambda qid: question_tags.get(str(qid), str(qid))
            )

            if "timestamp" in df.columns:
                df["ts"] = pd.to_numeric(df["timestamp"], errors="coerce").fillna(0)
            else:
                df["ts"] = range(len(df))

            df = df.sort_values("ts")

            all_question_ids.update(df["question_id"].unique())
            all_skill_ids.update(df["skill"].unique())

            raw_sequences.append({
                "user_id": user_file.stem,
                "question_ids_raw": df["question_id"].values,
                "skill_ids_raw": df["skill"].values,
                "responses": df["correct"].values.astype(np.int64),
                "timestamps": df["ts"].values.astype(np.float64),
            })

        self.question_map = {q: i + 1 for i, q in enumerate(sorted(all_question_ids))}
        self.skill_map = {s: i + 1 for i, s in enumerate(sorted(all_skill_ids))}

        sequences = []
        for raw in raw_sequences:
            sequences.append({
                "user_id": raw["user_id"],
                "question_ids": np.array(
                    [self.question_map[q] for q in raw["question_ids_raw"]], dtype=np.int64
                ),
                "skill_ids": np.array(
                    [self.skill_map[s] for s in raw["skill_ids_raw"]], dtype=np.int64
                ),
                "responses": raw["responses"],
                "timestamps": raw["timestamps"],
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
