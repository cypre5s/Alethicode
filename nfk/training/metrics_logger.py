"""
训练指标实时日志器。

CSV + JSONL 双格式记录，供 run_local.py / autodl_train.py 共用。
"""

from __future__ import annotations

import csv
import json
from datetime import datetime
from pathlib import Path


class MetricsLogger:
    """实时 CSV + JSONL 指标日志。"""

    CSV_FIELDS = [
        "timestamp", "variant", "seed", "fold",
        "epoch", "train_loss", "train_loss_kt", "val_loss", "val_loss_kt",
        "val_auc", "val_acc", "val_f1", "lr", "epoch_time_sec", "gpu_mem_mb",
    ]

    def __init__(self, output_dir: Path):
        self.output_dir = output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.csv_path = output_dir / "metrics.csv"
        self.jsonl_path = output_dir / "training_log.jsonl"

        # 追加模式而非覆盖，避免二次实验清空历史 CSV 数据。
        # 仅当文件不存在或空文件时写表头。
        needs_header = (not self.csv_path.exists()) or self.csv_path.stat().st_size == 0
        self._csv_file = open(self.csv_path, "a", newline="")
        self._csv_writer = csv.DictWriter(self._csv_file, fieldnames=self.CSV_FIELDS)
        if needs_header:
            self._csv_writer.writeheader()
            self._csv_file.flush()

    def log_epoch(self, record: dict):
        record["timestamp"] = datetime.now().isoformat()
        with open(self.jsonl_path, "a") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")

        csv_row = {k: record.get(k, "") for k in self.CSV_FIELDS}
        self._csv_writer.writerow(csv_row)
        self._csv_file.flush()

    def log_event(self, event: str, **kwargs):
        entry = {"timestamp": datetime.now().isoformat(), "event": event, **kwargs}
        with open(self.jsonl_path, "a") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")

    def close(self):
        if self._csv_file:
            self._csv_file.close()
