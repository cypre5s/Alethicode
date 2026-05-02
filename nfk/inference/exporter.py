"""
ONNX 导出器。

将训练好的 AlethicodeNFK 模型导出为 ONNX 格式，
供 Spring Boot (ONNX Runtime Java) 加载推理。
"""

from __future__ import annotations

import logging
from pathlib import Path

import torch

from ..models.nfk_model import AlethicodeNFK

logger = logging.getLogger(__name__)


class ONNXExporter:

    def __init__(self, model: AlethicodeNFK, max_seq_len: int = 200):
        self.model = model
        self.max_seq_len = max_seq_len

    def export(self, output_path: str | Path, opset_version: int = 17) -> Path:
        output_path = Path(output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        self.model.eval()
        device = next(self.model.parameters()).device

        B, T = 1, self.max_seq_len
        dummy_inputs = {
            "question_ids": torch.randint(0, 100, (B, T), device=device),
            "skill_ids": torch.randint(0, 50, (B, T), device=device),
            "responses": torch.randint(0, 2, (B, T), device=device),
            "delta_t": torch.rand(B, T, T, device=device),
            "pad_mask": torch.zeros(B, T, dtype=torch.bool, device=device),
        }

        output_names = ["kt_pred"]
        dynamic_axes = {
            "question_ids": {0: "batch", 1: "seq_len"},
            "skill_ids": {0: "batch", 1: "seq_len"},
            "responses": {0: "batch", 1: "seq_len"},
            "delta_t": {0: "batch", 1: "seq_len", 2: "seq_len"},
            "pad_mask": {0: "batch", 1: "seq_len"},
            "kt_pred": {0: "batch", 1: "seq_len"},
        }

        if self.model.use_kt_attention:
            output_names.append("attention_weights")
            dynamic_axes["attention_weights"] = {0: "batch", 1: "seq_len", 2: "seq_len"}

        class ExportWrapper(torch.nn.Module):
            def __init__(self, model: AlethicodeNFK):
                super().__init__()
                self.model = model

            def forward(self, question_ids, skill_ids, responses, delta_t, pad_mask):
                out = self.model(question_ids, skill_ids, responses, delta_t, pad_mask)
                results = (torch.sigmoid(out["kt_pred"]),)
                if "attention_weights" in out:
                    results = results + (out["attention_weights"],)
                return results

        wrapper = ExportWrapper(self.model)

        torch.onnx.export(
            wrapper,
            (
                dummy_inputs["question_ids"],
                dummy_inputs["skill_ids"],
                dummy_inputs["responses"],
                dummy_inputs["delta_t"],
                dummy_inputs["pad_mask"],
            ),
            str(output_path),
            input_names=list(dummy_inputs.keys()),
            output_names=output_names,
            dynamic_axes=dynamic_axes,
            opset_version=opset_version,
            do_constant_folding=True,
        )

        logger.info(f"ONNX model exported to {output_path}")
        return output_path

    def verify(self, onnx_path: str | Path) -> bool:
        import onnx
        model = onnx.load(str(onnx_path))
        onnx.checker.check_model(model)
        logger.info("ONNX model verification passed")
        return True
