"""Alethicode 自家 NFK 训练 CSV 预处理。

输入：``backend/.../NfkDataExportService`` 导出的 5 字段 CSV，header 必须严格等于
``user_id,question_id,skill_id,response,timestamp``，每行字段类型与
``contracts/nfk/training_dataset.schema.json`` 一致。

输出：``KTDataset`` 期望的 ``sequences = list[dict]``，每个 dict 含
``user_id / question_ids / skill_ids / responses / timestamps``（``timestamps``
为浮点 unix epoch 秒）。

设计考虑：
- 直接吃后端导出的 CSV，避免与 ASSISTments / EdNet 这类外部数据集格式混用；
- 在加载前用 ``contracts/nfk/training_dataset.schema.json`` 做行级校验，
  违规直接 ``ValueError``，不做"宽容兜底"；
- 做 ``question_id`` / ``skill_id`` 重映射到从 1 开始的紧凑 int，
  避免 PyTorch ``nn.Embedding`` 用稀疏 id 浪费显存。
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from jsonschema import Draft202012Validator
from sklearn.model_selection import KFold


CSV_HEADER = "user_id,question_id,skill_id,response,timestamp"
SCHEMA_FILENAME = "training_dataset.schema.json"
EXPECTED_COLUMNS = {"user_id", "question_id", "skill_id", "response", "timestamp"}


def _locate_schema() -> Path:
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "contracts" / "nfk" / SCHEMA_FILENAME
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        f"contracts/nfk/{SCHEMA_FILENAME} not found searching upward from {here}"
    )


@lru_cache(maxsize=1)
def _validator() -> Draft202012Validator:
    schema = json.loads(_locate_schema().read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema)


def _parse_timestamp(raw: str) -> float:
    """ISO-8601 → unix epoch seconds (float)。

    schema 强制末尾 ``Z``（UTC），``datetime.fromisoformat`` 在 Python 3.11+ 才接受
    ``Z`` 后缀，这里先把 ``Z`` 替换为 ``+00:00`` 以兼容更早版本。
    """
    text = raw.strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    dt = datetime.fromisoformat(text)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).timestamp()


class AlethicodeCsvPreprocessor:
    """读 alethicode 后端导出的 NFK 训练 CSV。

    用法::

        prep = AlethicodeCsvPreprocessor("nfk_training_pack_1.csv")
        sequences = prep.load_and_preprocess()
        splits = prep.get_splits(sequences, n_folds=5, seed=42)
    """

    MIN_INTERACTIONS = 3

    def __init__(self, data_path: str | Path, *, min_interactions: int | None = None):
        self.data_path = Path(data_path)
        self.question_map: dict[int, int] = {}
        self.skill_map: dict[int, int] = {}
        if min_interactions is not None:
            self.MIN_INTERACTIONS = max(1, int(min_interactions))

    def load_and_preprocess(self) -> list[dict[str, Any]]:
        if not self.data_path.is_file():
            raise FileNotFoundError(f"alethicode NFK CSV not found: {self.data_path}")

        df = pd.read_csv(self.data_path, dtype=str)
        actual_columns = set(df.columns)
        if actual_columns != EXPECTED_COLUMNS:
            missing = sorted(EXPECTED_COLUMNS - actual_columns)
            extra = sorted(actual_columns - EXPECTED_COLUMNS)
            raise ValueError(
                "alethicode NFK CSV columns mismatch contract: "
                f"missing={missing}, extra={extra}"
            )

        validator = _validator()
        for line_number, raw_row in enumerate(df.to_dict(orient="records"), start=1):
            normalized: dict[str, Any] = {
                "user_id": int(raw_row["user_id"]),
                "question_id": int(raw_row["question_id"]),
                "skill_id": int(raw_row["skill_id"]),
                "response": int(raw_row["response"]),
                "timestamp": raw_row["timestamp"],
            }
            errors = sorted(
                validator.iter_errors(normalized),
                key=lambda e: (list(e.absolute_path), e.message),
            )
            if errors:
                first = errors[0]
                path = ".".join(str(p) for p in first.absolute_path) or "<root>"
                raise ValueError(
                    f"alethicode NFK CSV row {line_number} violates contract: "
                    f"{path}: {first.message}"
                )

        df["user_id"] = df["user_id"].astype(np.int64)
        df["question_id"] = df["question_id"].astype(np.int64)
        df["skill_id"] = df["skill_id"].astype(np.int64)
        df["response"] = df["response"].astype(np.int64)
        df["timestamp_unix"] = df["timestamp"].map(_parse_timestamp).astype(np.float64)

        user_counts = df.groupby("user_id").size()
        valid_users = user_counts[user_counts >= self.MIN_INTERACTIONS].index
        df = df[df["user_id"].isin(valid_users)].copy()
        if df.empty:
            return []

        unique_questions = sorted(df["question_id"].unique().tolist())
        unique_skills = sorted(df["skill_id"].unique().tolist())
        self.question_map = {int(qid): idx + 1 for idx, qid in enumerate(unique_questions)}
        self.skill_map = {int(sid): idx + 1 for idx, sid in enumerate(unique_skills)}

        df["question_local"] = df["question_id"].map(self.question_map).astype(np.int64)
        df["skill_local"] = df["skill_id"].map(self.skill_map).astype(np.int64)
        df = df.sort_values(["user_id", "timestamp_unix"])

        sequences: list[dict[str, Any]] = []
        for user_id, group in df.groupby("user_id"):
            sequences.append(
                {
                    "user_id": int(user_id),
                    "question_ids": group["question_local"].values.astype(np.int64),
                    "skill_ids": group["skill_local"].values.astype(np.int64),
                    "responses": group["response"].values.astype(np.int64),
                    "timestamps": group["timestamp_unix"].values.astype(np.float64),
                }
            )
        return sequences

    def get_splits(
        self, sequences: list[dict[str, Any]], n_folds: int = 5, seed: int = 42
    ) -> list[tuple[list[dict[str, Any]], list[dict[str, Any]]]]:
        if not sequences:
            return []
        n_folds = max(1, min(int(n_folds), len(sequences)))
        if n_folds < 2:
            return [(sequences, sequences[:1])]
        kf = KFold(n_splits=n_folds, shuffle=True, random_state=seed)
        indices = np.arange(len(sequences))
        splits: list[tuple[list[dict[str, Any]], list[dict[str, Any]]]] = []
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
