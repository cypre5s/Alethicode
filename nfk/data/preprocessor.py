"""
数据预处理器 — 统一入口。

各格式实现已拆分到独立模块：
  - preprocessor_assistments.py
  - preprocessor_ednet.py
  - preprocessor_progsnap2.py
  - preprocessor_alethicode.py（吃后端 NfkDataExportService 导出的 5 字段 CSV）
"""

from .preprocessor_alethicode import AlethicodeCsvPreprocessor
from .preprocessor_assistments import ASSISTmentsPreprocessor
from .preprocessor_ednet import EdNetPreprocessor
from .preprocessor_progsnap2 import ProgSnap2Preprocessor
from .preprocessor_synthetic import SyntheticPythonPreprocessor

__all__ = [
    "AlethicodeCsvPreprocessor",
    "ASSISTmentsPreprocessor",
    "EdNetPreprocessor",
    "ProgSnap2Preprocessor",
    "SyntheticPythonPreprocessor",
]
