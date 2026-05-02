"""NFK 训练数据契约校验器。

行级 fail-fast 校验后端 ``NfkDataExportService`` 导出的 CSV 是否满足
``contracts/nfk/training_dataset.schema.json`` 描述的契约：

- CSV 头必须恰好等于 ``CSV_HEADER``
- 每一行必须严格满足 schema（5 字段、整数类型、response ∈ {0,1}、timestamp 形如
  ``YYYY-MM-DDTHH:MM:SS[.fff]Z``）

任一违规立即抛 ``NfkContractError``，附带 1-based 行号 + 第一条 schema 违规消息，
与 Java 侧 ``NfkTrainingRowValidationException`` 的语义对齐。

典型用法（``research/`` 在 sys.path 时，``nfk`` 是顶层包）::

    from nfk.data.contract_validator import validate_csv, NfkContractError

    try:
        rows = validate_csv(Path("export.csv"))
    except NfkContractError as exc:
        sys.exit(f"contract violation: {exc}")
    print(f"validated {rows} rows")
"""

from __future__ import annotations

import csv
import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

CSV_HEADER = "user_id,question_id,skill_id,response,timestamp"
SCHEMA_FILENAME = "training_dataset.schema.json"
INTEGER_FIELDS: tuple[str, ...] = ("user_id", "question_id", "skill_id", "response")
EXPECTED_COLUMNS: frozenset[str] = frozenset(
    ("user_id", "question_id", "skill_id", "response", "timestamp")
)


class NfkContractError(ValueError):
    """NFK 训练数据契约校验失败。"""

    def __init__(self, row_number: int | None, message: str) -> None:
        self.row_number = row_number
        location = f"row {row_number}" if row_number is not None else "header"
        super().__init__(f"NFK training {location} violates contract: {message}")


def locate_schema_file() -> Path:
    """父目录探测 ``contracts/nfk/training_dataset.schema.json``。

    与 ``services/tutor-graph/app/paths.py`` 同模式：从本文件向上找直到匹配，
    兼容 repo 内 (``research/nfk/data/contract_validator.py`` →
    ``<repo>/contracts/nfk/...``）与 docker / CI 内 (``/app/.../contract_validator.py``
    → ``/app/contracts/nfk/...``）两种布局。
    """
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "contracts" / "nfk" / SCHEMA_FILENAME
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        f"contracts/nfk/{SCHEMA_FILENAME} not found searching upward from {here}"
    )


@lru_cache(maxsize=1)
def _load_validator() -> Draft202012Validator:
    schema = json.loads(locate_schema_file().read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema)


def validate_row(row_number: int, row: dict[str, Any]) -> None:
    """校验单行 dict 是否满足契约。违规抛 ``NfkContractError``。"""
    validator = _load_validator()
    errors = sorted(
        validator.iter_errors(row),
        key=lambda e: (list(e.absolute_path), e.message),
    )
    if errors:
        first = errors[0]
        path = ".".join(str(p) for p in first.absolute_path) or "<root>"
        raise NfkContractError(row_number, f"{path}: {first.message}")


def _coerce_csv_row(row_number: int, raw: dict[str, str]) -> dict[str, Any]:
    """把 CSV 解析得到的字符串字段转为 schema 期望的整数 / 字符串。"""
    actual_columns = frozenset(raw.keys())
    if actual_columns != EXPECTED_COLUMNS:
        missing = sorted(EXPECTED_COLUMNS - actual_columns)
        extra = sorted(actual_columns - EXPECTED_COLUMNS)
        details = []
        if missing:
            details.append(f"missing={missing}")
        if extra:
            details.append(f"extra={extra}")
        raise NfkContractError(
            row_number, "CSV columns mismatch contract: " + ", ".join(details)
        )

    coerced: dict[str, Any] = {}
    for field in INTEGER_FIELDS:
        try:
            coerced[field] = int(raw[field])
        except ValueError as exc:
            raise NfkContractError(
                row_number, f"{field} is not a valid integer: {raw[field]!r}"
            ) from exc
    coerced["timestamp"] = raw["timestamp"]
    return coerced


def validate_csv(path: Path) -> int:
    """校验整个 CSV 文件并返回数据行数（不含 header）。

    - 文件不存在 → ``FileNotFoundError``
    - header 不匹配 / 任一行违反 schema → ``NfkContractError``（fail-fast，第一条即抛）
    """
    if not path.is_file():
        raise FileNotFoundError(f"CSV file not found: {path}")

    with path.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.reader(fh)
        try:
            header_row = next(reader)
        except StopIteration as exc:
            raise NfkContractError(None, "CSV is empty (no header line)") from exc

        actual_header = ",".join(header_row)
        if actual_header != CSV_HEADER:
            raise NfkContractError(
                None,
                f"CSV header mismatch: got {actual_header!r}, expected {CSV_HEADER!r}",
            )

        row_count = 0
        for line_number, raw_values in enumerate(reader, start=1):
            row_count += 1
            if len(raw_values) != len(header_row):
                raise NfkContractError(
                    line_number,
                    f"expected {len(header_row)} columns, got {len(raw_values)}",
                )
            raw = dict(zip(header_row, raw_values))
            row_dict = _coerce_csv_row(line_number, raw)
            validate_row(line_number, row_dict)

    return row_count


def main(argv: list[str] | None = None) -> int:
    """CLI: ``python -m nfk.data.contract_validator <csv-path>``。

    需在仓库根 ``cd research && python -m nfk.data.contract_validator ...`` 下运行，
    或显式 ``PYTHONPATH=$(pwd)/research python -m nfk.data.contract_validator ...``。
    """
    import argparse

    parser = argparse.ArgumentParser(
        description=(
            "Validate an NFK training CSV against "
            "contracts/nfk/training_dataset.schema.json"
        ),
    )
    parser.add_argument("csv_path", type=Path, help="Path to the CSV file to validate.")
    args = parser.parse_args(argv)
    rows = validate_csv(args.csv_path)
    print(f"OK: {args.csv_path} validated, {rows} rows pass contract.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
