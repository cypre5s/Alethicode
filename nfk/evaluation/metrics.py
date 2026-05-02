"""
评估指标与统计检验。

指标:
  - AUC (ROC)
  - Accuracy
  - F1 Score
  - RMSE

统计检验:
  - Wilcoxon 符号秩检验（非参数，p < 0.05）
  - McNemar 检验（配对样本）
"""

from __future__ import annotations

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    mean_squared_error,
    roc_auc_score,
)


def compute_metrics(
    predictions: np.ndarray,
    labels: np.ndarray,
    threshold: float = 0.5,
) -> dict[str, float]:
    """
    计算知识追踪评估指标。

    Args:
        predictions: (N,) 预测概率
        labels:      (N,) 真实标签 0/1

    Returns:
        dict: auc, accuracy, f1, rmse
    """
    labels = labels.astype(int)
    binary_preds = (predictions >= threshold).astype(int)

    if np.unique(labels).size < 2:
        auc = np.nan
    else:
        auc = roc_auc_score(labels, predictions)

    return {
        "auc": float(auc),
        "accuracy": float(accuracy_score(labels, binary_preds)),
        "f1": float(f1_score(labels, binary_preds, zero_division=0)),
        "rmse": float(np.sqrt(mean_squared_error(labels, predictions))),
    }


def statistical_test(
    scores_a: np.ndarray,
    scores_b: np.ndarray,
    test_type: str = "wilcoxon",
) -> dict[str, float]:
    """
    配对统计检验。

    Args:
        scores_a: (N,) 变体 A 的每折指标
        scores_b: (N,) 变体 B 的每折指标
        test_type: "wilcoxon" 或 "mcnemar"

    Returns:
        dict: statistic, p_value, significant (p < 0.05)
    """
    from scipy import stats

    if test_type == "wilcoxon":
        diff = scores_a - scores_b
        if np.all(diff == 0):
            return {"statistic": 0.0, "p_value": 1.0, "significant": False}
        stat, p = stats.wilcoxon(scores_a, scores_b)
    elif test_type == "mcnemar":
        from statsmodels.stats.contingency_tables import mcnemar as mc_test
        table = _build_mcnemar_table(scores_a, scores_b)
        result = mc_test(table, exact=True)
        stat, p = result.statistic, result.pvalue
    else:
        raise ValueError(f"Unknown test type: {test_type}")

    return {
        "statistic": float(stat),
        "p_value": float(p),
        "significant": p < 0.05,
    }


def _build_mcnemar_table(
    preds_a: np.ndarray, preds_b: np.ndarray
) -> np.ndarray:
    """构建 McNemar 列联表。"""
    a_correct = preds_a.astype(bool)
    b_correct = preds_b.astype(bool)
    return np.array([
        [np.sum(a_correct & b_correct), np.sum(a_correct & ~b_correct)],
        [np.sum(~a_correct & b_correct), np.sum(~a_correct & ~b_correct)],
    ])
