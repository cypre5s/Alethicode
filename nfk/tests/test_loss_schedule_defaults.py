import math
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from nfk.training.loss import KTLoss
from nfk.utils.config import NFKConfig


def test_nfk_config_default_aux_lambdas_are_moderate():
    config = NFKConfig()
    assert config.lambda_time == 0.15
    assert config.lambda_skill == 0.15


def test_kt_loss_aux_weight_warmup_is_5_epochs():
    loss = KTLoss(max_epochs=100)

    loss.set_epoch(0)
    assert loss._aux_weight() == 0.0

    loss.set_epoch(4)
    assert math.isclose(loss._aux_weight(), 0.8, rel_tol=0, abs_tol=1e-8)

    loss.set_epoch(5)
    assert math.isclose(loss._aux_weight(), 1.0, rel_tol=0, abs_tol=1e-8)
