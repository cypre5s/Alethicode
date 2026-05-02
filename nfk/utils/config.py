"""
NFK 配置管理。

支持从 YAML 文件加载，也可直接构造。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml


@dataclass
class NFKConfig:
    # 模型
    n_questions: int = 1000
    n_skills: int = 200
    embed_dim: int = 128
    hidden_dim: int = 256
    n_heads: int = 8
    top_k: int = 20
    n_kt_heads: int = 1
    dropout: float = 0.2
    max_seq_len: int = 200

    # 消融
    use_sparse_attention: bool = True
    use_kt_attention: bool = True

    # 训练
    learning_rate: float = 5e-4
    weight_decay: float = 0.01
    batch_size: int = 64
    max_epochs: int = 100
    patience: int = 15
    grad_clip: float = 1.0
    seed: int = 42

    # 损失权重
    lambda_kt: float = 1.0
    lambda_time: float = 0.15
    lambda_skill: float = 0.15
    lambda_ortho: float = 0.1
    label_smoothing: float = 0.05

    # 数据
    data_path: str = ""
    data_format: str = "assistments"
    n_folds: int = 5

    # 输出
    output_dir: str = "outputs"
    experiment_name: str = "nfk_full"

    @classmethod
    def from_yaml(cls, path: str | Path) -> NFKConfig:
        with open(path) as f:
            data = yaml.safe_load(f)
        return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})

    def to_yaml(self, path: str | Path) -> None:
        with open(path, "w") as f:
            yaml.dump(self.__dict__, f, default_flow_style=False, allow_unicode=True)

    def variant_name(self) -> str:
        if self.use_sparse_attention and self.use_kt_attention:
            return "A+B+C"
        elif self.use_sparse_attention:
            return "A+B"
        elif self.use_kt_attention:
            return "A+C"
        else:
            return "A_only"
