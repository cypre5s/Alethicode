import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from nfk import autodl_train


def test_build_config_uses_autodl_defaults_for_patience_and_lr():
    variant = autodl_train.VARIANTS[0]

    config = autodl_train.build_config(
        variant=variant,
        n_questions=100,
        n_skills=20,
        seed=42,
    )

    assert config.patience == 8
    assert config.learning_rate == 2e-4
