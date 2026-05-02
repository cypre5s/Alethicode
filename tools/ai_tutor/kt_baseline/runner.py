import json
import math
import random
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List

import torch
from torch import nn


@dataclass(frozen=True)
class Interaction:
    user_id: str
    course_id: str
    problem_id: str
    kc_id: str
    timestamp: str
    correct: int


def load_interactions(path: Path) -> List[Interaction]:
    interactions: List[Interaction] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            raw = json.loads(line)
            interactions.append(
                Interaction(
                    user_id=str(raw["user_id"]),
                    course_id=str(raw["course_id"]),
                    problem_id=str(raw["problem_id"]),
                    kc_id=str(raw["kc_id"]),
                    timestamp=str(raw["timestamp"]),
                    correct=int(raw["correct"]),
                )
            )
    return sorted(interactions, key=lambda item: (item.course_id, item.timestamp, item.user_id, item.problem_id))


def split_by_course_time(
    interactions: List[Interaction],
    train_ratio: float = 0.7,
    val_ratio: float = 0.15,
) -> Dict[str, List[Interaction]]:
    grouped: Dict[str, List[Interaction]] = defaultdict(list)
    for interaction in interactions:
        grouped[interaction.course_id].append(interaction)

    splits = {"train": [], "val": [], "test": []}
    for course_id, rows in grouped.items():
        ordered = sorted(rows, key=lambda item: item.timestamp)
        total = len(ordered)
        train_end = max(1, int(total * train_ratio))
        val_end = max(train_end + 1, int(total * (train_ratio + val_ratio)))
        if val_end >= total:
            val_end = total - 1
        if train_end >= val_end:
            train_end = max(1, val_end - 1)
        splits["train"].extend(ordered[:train_end])
        splits["val"].extend(ordered[train_end:val_end])
        splits["test"].extend(ordered[val_end:])
    return splits


def validate_no_leakage(splits: Dict[str, List[Interaction]]) -> Dict[str, object]:
    violations: List[str] = []
    grouped = {
        split_name: _group_by_course(rows)
        for split_name, rows in splits.items()
    }
    for course_id in sorted(set(grouped["train"]) | set(grouped["val"]) | set(grouped["test"])):
        train_rows = grouped["train"].get(course_id, [])
        val_rows = grouped["val"].get(course_id, [])
        test_rows = grouped["test"].get(course_id, [])
        if train_rows and val_rows and max(item.timestamp for item in train_rows) >= min(item.timestamp for item in val_rows):
            violations.append(f"{course_id}: train overlaps val")
        if val_rows and test_rows and max(item.timestamp for item in val_rows) >= min(item.timestamp for item in test_rows):
            violations.append(f"{course_id}: val overlaps test")
        if train_rows and test_rows and max(item.timestamp for item in train_rows) >= min(item.timestamp for item in test_rows):
            violations.append(f"{course_id}: train overlaps test")
    return {"passed": not violations, "violations": violations}


def run_baselines(
    dataset_path: Path,
    output_dir: Path,
    seed: int = 7,
    epochs: int = 3,
    train_ratio: float = 0.7,
    val_ratio: float = 0.15,
) -> Dict[str, object]:
    random.seed(seed)
    torch.manual_seed(seed)
    interactions = load_interactions(Path(dataset_path))
    splits = split_by_course_time(interactions, train_ratio=train_ratio, val_ratio=val_ratio)
    leakage = validate_no_leakage(splits)
    if not leakage["passed"]:
        raise ValueError(f"label leakage detected: {leakage['violations']}")

    output_dir.mkdir(parents=True, exist_ok=True)
    train_rows = splits["train"]
    val_rows = splits["val"]
    test_rows = splits["test"]
    kc_vocab = _build_kc_vocab(interactions)

    models = {
        "BKT-lite": BktLiteModel(),
        "DKT": DktModel(kc_vocab),
        "AKT": AktModel(kc_vocab),
    }

    leaderboard = {}
    for name, model in models.items():
        model.fit(train_rows, val_rows, epochs=epochs)
        leaderboard[name] = model.evaluate(test_rows)

    split_summary = {
        "train": len(train_rows),
        "val": len(val_rows),
        "test": len(test_rows),
        "leakage_check": leakage,
    }
    (output_dir / "leaderboard.json").write_text(
        json.dumps(leaderboard, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (output_dir / "split_summary.json").write_text(
        json.dumps(split_summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (output_dir / "report.md").write_text(_build_report(leaderboard, split_summary), encoding="utf-8")
    return {"leaderboard": leaderboard, "split_summary": split_summary}


class BktLiteModel:
    def __init__(self) -> None:
        self.mastery: Dict[str, float] = defaultdict(lambda: 0.3)

    def fit(self, train_rows: List[Interaction], _val_rows: List[Interaction], epochs: int = 1) -> None:
        for _ in range(max(1, epochs)):
            for row in train_rows:
                current = self.mastery[row.kc_id]
                self.mastery[row.kc_id] = _bkt_update(current, row.correct)

    def evaluate(self, rows: List[Interaction]) -> Dict[str, float]:
        local_mastery = dict(self.mastery)
        predictions = []
        labels = []
        for row in rows:
            prediction = local_mastery.get(row.kc_id, 0.3)
            predictions.append(prediction)
            labels.append(float(row.correct))
            local_mastery[row.kc_id] = _bkt_update(prediction, row.correct)
        return _summarize_metrics(predictions, labels)


class DktModel:
    def __init__(self, kc_vocab: Dict[str, int]) -> None:
        self.kc_vocab = kc_vocab
        vocab_size = len(kc_vocab) * 2 + 2
        self.target_vocab = len(kc_vocab) + 1
        self.network = _SequencePredictor(vocab_size=vocab_size, target_vocab=self.target_vocab, encoder="gru")

    def fit(self, train_rows: List[Interaction], _val_rows: List[Interaction], epochs: int = 3) -> None:
        samples = _build_sequence_samples(train_rows, self.kc_vocab)
        if not samples:
            return
        self.network.train()
        optimizer = torch.optim.Adam(self.network.parameters(), lr=0.02)
        loss_fn = nn.BCELoss()
        for _ in range(max(1, epochs)):
            for sample in samples:
                history_tensor = torch.tensor(sample["history"], dtype=torch.long).unsqueeze(0)
                target_kc_tensor = torch.tensor([sample["target_kc"]], dtype=torch.long)
                label_tensor = torch.tensor([sample["label"]], dtype=torch.float32)
                prediction = self.network(history_tensor, target_kc_tensor)
                loss = loss_fn(prediction, label_tensor)
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()

    def evaluate(self, rows: List[Interaction]) -> Dict[str, float]:
        samples = _build_sequence_samples(rows, self.kc_vocab)
        if not samples:
            return _summarize_metrics([], [])
        self.network.eval()
        predictions = []
        labels = []
        with torch.no_grad():
            for sample in samples:
                history_tensor = torch.tensor(sample["history"], dtype=torch.long).unsqueeze(0)
                target_kc_tensor = torch.tensor([sample["target_kc"]], dtype=torch.long)
                prediction = self.network(history_tensor, target_kc_tensor).item()
                predictions.append(prediction)
                labels.append(sample["label"])
        return _summarize_metrics(predictions, labels)


class AktModel(DktModel):
    def __init__(self, kc_vocab: Dict[str, int]) -> None:
        self.kc_vocab = kc_vocab
        vocab_size = len(kc_vocab) * 2 + 2
        self.target_vocab = len(kc_vocab) + 1
        self.network = _SequencePredictor(vocab_size=vocab_size, target_vocab=self.target_vocab, encoder="transformer")


class _SequencePredictor(nn.Module):
    def __init__(self, vocab_size: int, target_vocab: int, encoder: str) -> None:
        super().__init__()
        hidden_size = 16
        self.history_embedding = nn.Embedding(vocab_size, hidden_size)
        self.target_embedding = nn.Embedding(target_vocab, hidden_size)
        self.encoder_type = encoder
        if encoder == "gru":
            self.encoder = nn.GRU(hidden_size, hidden_size, batch_first=True)
        else:
            encoder_layer = nn.TransformerEncoderLayer(
                d_model=hidden_size,
                nhead=4,
                dim_feedforward=32,
                batch_first=True,
            )
            self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=1)
        self.output = nn.Sequential(
            nn.Linear(hidden_size * 2, hidden_size),
            nn.ReLU(),
            nn.Linear(hidden_size, 1),
            nn.Sigmoid(),
        )

    def forward(self, history_tensor: torch.Tensor, target_kc_tensor: torch.Tensor) -> torch.Tensor:
        history_emb = self.history_embedding(history_tensor)
        if self.encoder_type == "gru":
            encoded, _ = self.encoder(history_emb)
            pooled = encoded[:, -1, :]
        else:
            encoded = self.encoder(history_emb)
            pooled = encoded.mean(dim=1)
        target_emb = self.target_embedding(target_kc_tensor)
        combined = torch.cat([pooled, target_emb], dim=1)
        return self.output(combined).squeeze(1)


def _group_by_course(rows: List[Interaction]) -> Dict[str, List[Interaction]]:
    grouped: Dict[str, List[Interaction]] = defaultdict(list)
    for row in rows:
        grouped[row.course_id].append(row)
    return grouped


def _build_kc_vocab(rows: List[Interaction]) -> Dict[str, int]:
    return {kc_id: index + 1 for index, kc_id in enumerate(sorted({row.kc_id for row in rows}))}


def _build_sequence_samples(rows: List[Interaction], kc_vocab: Dict[str, int]) -> List[Dict[str, object]]:
    sequences: Dict[str, List[Interaction]] = defaultdict(list)
    for row in rows:
        sequences[f"{row.user_id}:{row.course_id}"].append(row)
    samples: List[Dict[str, object]] = []
    for sequence in sequences.values():
        ordered = sorted(sequence, key=lambda item: item.timestamp)
        history_tokens: List[int] = []
        for row in ordered:
            kc_index = kc_vocab[row.kc_id]
            if history_tokens:
                samples.append(
                    {
                        "history": list(history_tokens),
                        "target_kc": kc_index,
                        "label": float(row.correct),
                    }
                )
            history_tokens.append(kc_index + (len(kc_vocab) if row.correct else 0))
    return samples


def _bkt_update(prior: float, correct: int) -> float:
    slip = 0.1
    guess = 0.2
    transit = 0.15
    if correct:
        posterior = (prior * (1.0 - slip)) / max((prior * (1.0 - slip)) + ((1.0 - prior) * guess), 1e-6)
    else:
        posterior = (prior * slip) / max((prior * slip) + ((1.0 - prior) * (1.0 - guess)), 1e-6)
    return posterior + (1.0 - posterior) * transit


def _summarize_metrics(predictions: List[float], labels: List[float]) -> Dict[str, float]:
    if not predictions:
        return {"accuracy": 0.0, "brier_score": 1.0, "num_predictions": 0}
    rounded = [1.0 if prediction >= 0.5 else 0.0 for prediction in predictions]
    accuracy = sum(1.0 for prediction, label in zip(rounded, labels) if prediction == label) / len(labels)
    brier_score = sum((prediction - label) ** 2 for prediction, label in zip(predictions, labels)) / len(labels)
    return {
        "accuracy": round(accuracy, 4),
        "brier_score": round(brier_score, 4),
        "num_predictions": len(labels),
    }


def _build_report(leaderboard: Dict[str, Dict[str, float]], split_summary: Dict[str, object]) -> str:
    ordered = sorted(leaderboard.items(), key=lambda item: (-item[1]["accuracy"], item[1]["brier_score"]))
    lines = [
        "# KT Baseline Report",
        "",
        "## Split Summary",
        f"- train: {split_summary['train']}",
        f"- val: {split_summary['val']}",
        f"- test: {split_summary['test']}",
        f"- leakage_check: {'pass' if split_summary['leakage_check']['passed'] else 'fail'}",
        "",
        "## Leaderboard",
    ]
    for model_name, metrics in ordered:
        lines.append(
            f"- {model_name}: accuracy={metrics['accuracy']}, brier_score={metrics['brier_score']}, "
            f"num_predictions={metrics['num_predictions']}"
        )
    return "\n".join(lines) + "\n"
