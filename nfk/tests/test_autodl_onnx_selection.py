import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from nfk import autodl_train


def test_select_onnx_export_candidate_prefers_best_mean_variant():
    config_a = object()
    config_b = object()
    run_records = [
        {
            "dataset": "assistments",
            "variant": "A+B+C_Full",
            "seed": 42,
            "fold": 0,
            "auc": 0.75,
            "checkpoint": Path("full_s42_f0.pt"),
            "config": config_a,
        },
        {
            "dataset": "assistments",
            "variant": "A+B+C_Full",
            "seed": 3407,
            "fold": 0,
            "auc": 0.74,
            "checkpoint": Path("full_s3407_f0.pt"),
            "config": config_a,
        },
        {
            "dataset": "assistments",
            "variant": "A+C_wo_SparseAttn",
            "seed": 42,
            "fold": 0,
            "auc": 0.78,
            "checkpoint": Path("ac_s42_f0.pt"),
            "config": config_b,
        },
        {
            "dataset": "assistments",
            "variant": "A+C_wo_SparseAttn",
            "seed": 3407,
            "fold": 0,
            "auc": 0.70,
            "checkpoint": Path("ac_s3407_f0.pt"),
            "config": config_b,
        },
    ]

    selected = autodl_train.select_onnx_export_candidate(run_records)

    assert selected is not None
    assert selected["dataset"] == "assistments"
    assert selected["variant"] == "A+B+C_Full"
    assert selected["seed"] == 42
    assert selected["fold"] == 0
    assert selected["checkpoint"] == Path("full_s42_f0.pt")
    assert selected["best_run_auc"] == 0.75
    assert selected["variant_mean_auc"] == 0.745


def test_select_onnx_export_candidate_returns_none_when_no_runs():
    assert autodl_train.select_onnx_export_candidate([]) is None
