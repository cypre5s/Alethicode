"""Alethicode 判题机 Python 包。

Fork 自上游 QingdaoU/JudgeServer 的 ``server/`` 目录（commit b28aa56）。
原仓库 license 是 SATA，复制保留在 ``services/judge-server/judger/LICENSE``。

Phase 0 不修改任何业务逻辑，仅做工程化迁入；后续 Phase 在本包内追加
``worker_pool`` / ``diagnosis`` / ``explain`` / ``streaming`` / ``metrics`` /
``trace`` / ``safety`` 子模块。
"""

__all__ = ["__version__"]

__version__ = "0.0.1"
