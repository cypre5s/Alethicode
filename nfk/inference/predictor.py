"""
NFK 推理预测器。

支持两种后端：
  1. PyTorch 原生推理（开发/调试）
  2. ONNX Runtime 推理（生产部署，<5ms 延迟）
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path

import numpy as np

logger = logging.getLogger(__name__)


@dataclass
class KTPrediction:
    """知识追踪预测结果。"""
    mastery_prob: np.ndarray           # (T,) 每步知识掌握概率
    attention_weights: np.ndarray | None  # (T, T) 注意力权重（可解释）


class NFKPredictor:
    """ONNX Runtime 推理器。"""

    def __init__(self, onnx_path: str | Path, use_gpu: bool = False):
        import onnxruntime as ort

        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"] if use_gpu else ["CPUExecutionProvider"]
        self.session = ort.InferenceSession(str(onnx_path), providers=providers)
        self.input_names = [inp.name for inp in self.session.get_inputs()]
        self.output_names = [out.name for out in self.session.get_outputs()]
        logger.info(f"Loaded ONNX model from {onnx_path}, inputs={self.input_names}")

    def predict(
        self,
        question_ids: np.ndarray,
        skill_ids: np.ndarray,
        responses: np.ndarray,
        timestamps: np.ndarray,
    ) -> KTPrediction:
        """
        对单个学生的交互序列进行预测。

        所有输入形状: (T,)
        """
        T = len(question_ids)
        question_ids = question_ids.reshape(1, T).astype(np.int64)
        skill_ids = skill_ids.reshape(1, T).astype(np.int64)
        responses = responses.reshape(1, T).astype(np.int64)

        delta_t = self._build_delta_t(timestamps).reshape(1, T, T).astype(np.float32)
        pad_mask = np.zeros((1, T), dtype=bool)

        feeds = {
            "question_ids": question_ids,
            "skill_ids": skill_ids,
            "responses": responses,
            "delta_t": delta_t,
            "pad_mask": pad_mask,
        }

        outputs = self.session.run(self.output_names, feeds)

        mastery = outputs[0][0]  # (T,)
        attn = outputs[1][0] if len(outputs) > 1 else None

        return KTPrediction(
            mastery_prob=1.0 / (1.0 + np.exp(-mastery)),
            attention_weights=attn,
        )

    @staticmethod
    def _build_delta_t(timestamps: np.ndarray) -> np.ndarray:
        T = len(timestamps)
        t_q = timestamps.reshape(T, 1)
        t_k = timestamps.reshape(1, T)
        delta = (t_q - t_k) / 86400.0
        return np.clip(delta, 0.0, None)
