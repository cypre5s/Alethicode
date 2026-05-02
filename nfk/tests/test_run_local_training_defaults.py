import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from nfk import run_local
from nfk.data.dataset import KTCollator, KTDataset


def test_build_config_uses_gpu_friendly_default_batch_size():
    config = run_local.build_config(run_local.VARIANTS[0], n_questions=10, n_skills=5, seed=42)

    assert config.batch_size == 512
    assert config.max_epochs == 300
    assert config.patience == 50


def test_build_data_loader_uses_requested_workers_and_persistent_workers():
    dataset = KTDataset([])
    loader = run_local.build_data_loader(
        dataset,
        batch_size=512,
        shuffle=False,
        collator=KTCollator(),
        num_workers=8,
    )

    assert loader.batch_size == 512
    assert loader.num_workers == 8
    assert loader.persistent_workers is True
