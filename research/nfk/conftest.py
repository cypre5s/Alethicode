"""Pytest 启动钩子：把 ``research/`` 加入 sys.path，让 ``import nfk.X`` 可用。

与 ``research/nfk/run_local.py`` / ``research/nfk/train.py`` 同一约定：``nfk`` 是顶层
包，从 ``research/nfk/__init__.py`` 起始；测试在 ``research/nfk/tests/`` 下运行，
但 ``import nfk.data.contract_validator`` 需要 ``research/`` 在 sys.path。

无论 pytest 在哪个工作目录调用、是否带 ``-p`` 配置，本钩子都保证一致。
"""

from __future__ import annotations

import sys
from pathlib import Path

_RESEARCH_DIR = Path(__file__).resolve().parent.parent
if str(_RESEARCH_DIR) not in sys.path:
    sys.path.insert(0, str(_RESEARCH_DIR))
