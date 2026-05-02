"""NFK 训练数据契约校验器单元测试。

与 backend ``NfkTrainingRowValidatorTest`` 形成跨语言对照：
两侧加载同一份 ``contracts/nfk/training_dataset.schema.json``，对相同的合法 / 非法
样本必须给出一致的通过 / 拒绝结果。
"""

from __future__ import annotations

import textwrap
from pathlib import Path

import pytest

from nfk.data.contract_validator import (
    CSV_HEADER,
    NfkContractError,
    locate_schema_file,
    validate_csv,
    validate_row,
)


def _valid_row() -> dict:
    return {
        "user_id": 1,
        "question_id": 100,
        "skill_id": 7,
        "response": 1,
        "timestamp": "2026-04-10T10:00:00Z",
    }


def test_locate_schema_file_returns_existing_path():
    schema_path = locate_schema_file()
    assert schema_path.is_file()
    assert schema_path.name == "training_dataset.schema.json"


def test_validate_row_accepts_canonical_row():
    validate_row(1, _valid_row())


def test_validate_row_accepts_fractional_seconds():
    row = _valid_row()
    row["timestamp"] = "2026-04-10T10:00:00.123456789Z"
    validate_row(1, row)


def test_validate_row_rejects_response_two():
    row = _valid_row()
    row["response"] = 2
    with pytest.raises(NfkContractError) as exc:
        validate_row(3, row)
    assert "row 3" in str(exc.value)
    assert "response" in str(exc.value)


def test_validate_row_rejects_zero_user_id():
    row = _valid_row()
    row["user_id"] = 0
    with pytest.raises(NfkContractError) as exc:
        validate_row(4, row)
    assert "user_id" in str(exc.value)


def test_validate_row_rejects_negative_skill_id():
    row = _valid_row()
    row["skill_id"] = -1
    with pytest.raises(NfkContractError) as exc:
        validate_row(5, row)
    assert "skill_id" in str(exc.value)


def test_validate_row_rejects_local_time_with_space_separator():
    row = _valid_row()
    row["timestamp"] = "2026-04-10 10:00:00.0"
    with pytest.raises(NfkContractError) as exc:
        validate_row(6, row)
    assert "timestamp" in str(exc.value)


def test_validate_row_rejects_timestamp_with_offset_instead_of_z():
    row = _valid_row()
    row["timestamp"] = "2026-04-10T10:00:00+08:00"
    with pytest.raises(NfkContractError):
        validate_row(7, row)


def test_validate_row_rejects_missing_required_field():
    row = _valid_row()
    del row["timestamp"]
    with pytest.raises(NfkContractError) as exc:
        validate_row(8, row)
    assert "row 8" in str(exc.value)
    assert "timestamp" in str(exc.value)


def test_validate_row_rejects_extra_property():
    row = _valid_row()
    row["extra"] = "not-allowed"
    with pytest.raises(NfkContractError) as exc:
        validate_row(9, row)
    assert "row 9" in str(exc.value)


def test_validate_csv_accepts_two_valid_rows(tmp_path: Path):
    csv_path = tmp_path / "ok.csv"
    csv_path.write_text(
        textwrap.dedent(
            """\
            user_id,question_id,skill_id,response,timestamp
            1,100,7,1,2026-04-10T10:00:00Z
            2,101,8,0,2026-04-10T10:05:00.123Z
            """
        ),
        encoding="utf-8",
    )

    assert validate_csv(csv_path) == 2


def test_validate_csv_rejects_wrong_header(tmp_path: Path):
    csv_path = tmp_path / "bad-header.csv"
    csv_path.write_text(
        "uid,question_id,skill_id,response,timestamp\n1,100,7,1,2026-04-10T10:00:00Z\n",
        encoding="utf-8",
    )
    with pytest.raises(NfkContractError) as exc:
        validate_csv(csv_path)
    assert "header" in str(exc.value)


def test_validate_csv_rejects_first_bad_row_with_row_number(tmp_path: Path):
    csv_path = tmp_path / "bad-row.csv"
    csv_path.write_text(
        textwrap.dedent(
            f"""\
            {CSV_HEADER}
            1,100,7,1,2026-04-10T10:00:00Z
            2,101,8,2,2026-04-10T10:05:00Z
            """
        ),
        encoding="utf-8",
    )
    with pytest.raises(NfkContractError) as exc:
        validate_csv(csv_path)
    assert exc.value.row_number == 2
    assert "response" in str(exc.value)


def test_validate_csv_rejects_non_integer_field(tmp_path: Path):
    csv_path = tmp_path / "non-int.csv"
    csv_path.write_text(
        textwrap.dedent(
            f"""\
            {CSV_HEADER}
            abc,100,7,1,2026-04-10T10:00:00Z
            """
        ),
        encoding="utf-8",
    )
    with pytest.raises(NfkContractError) as exc:
        validate_csv(csv_path)
    assert "user_id" in str(exc.value)


def test_validate_csv_rejects_empty_file(tmp_path: Path):
    csv_path = tmp_path / "empty.csv"
    csv_path.write_text("", encoding="utf-8")
    with pytest.raises(NfkContractError) as exc:
        validate_csv(csv_path)
    assert "empty" in str(exc.value)


def test_validate_csv_rejects_column_count_mismatch(tmp_path: Path):
    csv_path = tmp_path / "short-row.csv"
    csv_path.write_text(
        textwrap.dedent(
            f"""\
            {CSV_HEADER}
            1,100,7,1
            """
        ),
        encoding="utf-8",
    )
    with pytest.raises(NfkContractError) as exc:
        validate_csv(csv_path)
    assert "expected 5 columns" in str(exc.value)


def test_validate_csv_rejects_missing_file(tmp_path: Path):
    with pytest.raises(FileNotFoundError):
        validate_csv(tmp_path / "does-not-exist.csv")


def test_round_trip_fixture_passes_python_validation():
    """Round-trip 锚点：Java NfkDataExportServiceTest 已断言导出器输出 byte-for-byte
    等于 ``contracts/nfk/fixtures/exporter_output_sample.csv``；本测试断言 Python
    侧加载同一份 fixture 也能完整通过 schema 校验。

    任一侧改变 CSV 格式都会同时打破两侧测试，强制契约对齐。
    """
    schema_path = locate_schema_file()
    repo_root = schema_path.parents[2]
    fixture = repo_root / "contracts" / "nfk" / "fixtures" / "exporter_output_sample.csv"
    assert fixture.is_file(), (
        f"contracts/nfk/fixtures/exporter_output_sample.csv missing at {fixture}; "
        "this fixture is the cross-language round-trip anchor."
    )

    rows = validate_csv(fixture)
    assert rows == 3
