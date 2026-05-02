import sys
import warnings
from pathlib import Path

import numpy as np

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from nfk.data.preprocessor import EdNetPreprocessor
from nfk.evaluation.metrics import compute_metrics


def test_ednet_preprocessor_uses_question_correct_answer_mapping(tmp_path):
    data_dir = tmp_path / "ednet"
    kt1_dir = data_dir / "KT1"
    kt1_dir.mkdir(parents=True)

    (data_dir / "questions.csv").write_text(
        "question_id,bundle_id,tags,correct_answer,part\n"
        "q1,1,10,a,1\n"
        "q2,1,20,b,1\n",
        encoding="utf-8",
    )
    (kt1_dir / "u1.csv").write_text(
        "timestamp,solving_id,question_id,user_answer,elapsed_time\n"
        "1,1,q1,a,10\n"
        "2,2,q2,a,10\n"
        "3,3,q1,a,10\n"
        "4,4,q2,b,10\n"
        "5,5,q1,b,10\n",
        encoding="utf-8",
    )

    sequences = EdNetPreprocessor(data_dir).load_and_preprocess()

    assert len(sequences) == 1
    assert sequences[0]["responses"].tolist() == [1, 0, 1, 1, 0]


def test_compute_metrics_returns_nan_auc_without_sklearn_warning_for_single_class():
    with warnings.catch_warnings(record=True) as captured:
        warnings.simplefilter("always")
        metrics = compute_metrics(
            predictions=np.array([0.1, 0.2, 0.3]),
            labels=np.array([0, 0, 0]),
        )

    assert np.isnan(metrics["auc"])
    assert not captured
