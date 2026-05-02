"""AlethicodeCsvPreprocessor 数据格式正确性 smoke test。

确保后端 ``NfkDataExportService`` 写出的 CSV → ``AlethicodeCsvPreprocessor`` →
``KTDataset`` 这条链上字段命名 / 类型 / 时间格式严格对齐。
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from nfk.data.preprocessor_alethicode import (
    CSV_HEADER,
    AlethicodeCsvPreprocessor,
    _parse_timestamp,
)


def _write_csv(path: Path, rows: list[str]) -> None:
    body = "\n".join([CSV_HEADER, *rows])
    path.write_text(body + "\n", encoding="utf-8")


def test_parse_timestamp_returns_unix_seconds_for_iso_z():
    ts = _parse_timestamp("2026-04-29T07:13:45Z")
    assert ts == pytest.approx(1777446825.0, abs=1.0)


def test_parse_timestamp_supports_fractional_seconds():
    ts = _parse_timestamp("2026-04-29T07:13:45.123Z")
    assert ts == pytest.approx(1777446825.123, abs=1e-3)


def test_load_and_preprocess_groups_by_user_and_remaps_ids(tmp_path: Path):
    csv_path = tmp_path / "alethicode_nfk.csv"
    _write_csv(
        csv_path,
        [
            "1,200,7,1,2026-04-29T01:00:00Z",
            "1,201,8,0,2026-04-29T01:05:00Z",
            "1,202,9,1,2026-04-29T01:10:00Z",
            "2,200,7,0,2026-04-30T03:00:00Z",
            "2,202,9,1,2026-04-30T03:30:00Z",
            "2,203,8,1,2026-04-30T03:45:00Z",
        ],
    )

    pre = AlethicodeCsvPreprocessor(csv_path, min_interactions=3)
    sequences = pre.load_and_preprocess()

    assert len(sequences) == 2
    user_ids = sorted(seq["user_id"] for seq in sequences)
    assert user_ids == [1, 2]
    seq_user1 = next(seq for seq in sequences if seq["user_id"] == 1)
    seq_user2 = next(seq for seq in sequences if seq["user_id"] == 2)
    # 紧凑重映射：每个原始 question_id / skill_id 都被映射到 1..N
    assert pre.n_questions == len(set([200, 201, 202, 203])) + 1
    assert pre.n_skills == len(set([7, 8, 9])) + 1
    # 时间排序后的最后一项 timestamp 应大于第一项
    assert seq_user1["timestamps"][-1] > seq_user1["timestamps"][0]
    # responses 类型与编码与契约一致
    assert seq_user1["responses"].dtype == np.int64
    assert set(np.unique(seq_user1["responses"]).tolist()) <= {0, 1}
    # delta_t collator 期望 timestamps 是浮点
    assert seq_user1["timestamps"].dtype == np.float64
    assert seq_user2["question_ids"].dtype == np.int64


def test_min_interactions_filters_short_users(tmp_path: Path):
    csv_path = tmp_path / "short.csv"
    _write_csv(
        csv_path,
        [
            "5,200,7,1,2026-04-29T01:00:00Z",
            "5,201,8,0,2026-04-29T01:05:00Z",
            "6,200,7,1,2026-04-29T01:00:00Z",
            "6,201,8,0,2026-04-29T01:05:00Z",
            "6,202,9,1,2026-04-29T01:10:00Z",
        ],
    )

    pre = AlethicodeCsvPreprocessor(csv_path, min_interactions=3)
    sequences = pre.load_and_preprocess()
    assert len(sequences) == 1
    assert sequences[0]["user_id"] == 6


def test_violation_in_csv_raises_value_error(tmp_path: Path):
    csv_path = tmp_path / "bad.csv"
    _write_csv(
        csv_path,
        [
            "1,200,7,2,2026-04-29T01:00:00Z",  # response = 2 violates schema enum
        ],
    )

    pre = AlethicodeCsvPreprocessor(csv_path, min_interactions=1)
    with pytest.raises(ValueError, match="violates contract"):
        pre.load_and_preprocess()


def test_missing_columns_failfast(tmp_path: Path):
    csv_path = tmp_path / "missing.csv"
    csv_path.write_text("user_id,question_id,response,timestamp\n1,2,1,2026-04-29T01:00:00Z\n", encoding="utf-8")
    pre = AlethicodeCsvPreprocessor(csv_path)
    with pytest.raises(ValueError, match="columns mismatch contract"):
        pre.load_and_preprocess()


def test_get_splits_when_only_one_user_returns_single_split(tmp_path: Path):
    csv_path = tmp_path / "one_user.csv"
    _write_csv(
        csv_path,
        [
            "1,200,7,1,2026-04-29T01:00:00Z",
            "1,201,8,0,2026-04-29T01:05:00Z",
            "1,202,9,1,2026-04-29T01:10:00Z",
        ],
    )
    pre = AlethicodeCsvPreprocessor(csv_path, min_interactions=3)
    sequences = pre.load_and_preprocess()
    splits = pre.get_splits(sequences, n_folds=5, seed=42)
    assert len(splits) == 1
    train_seqs, val_seqs = splits[0]
    assert train_seqs and val_seqs
