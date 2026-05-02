"""
消融实验可视化。

图表:
  1. 分组柱状图: 各变体 AUC/Accuracy/F1 对比
  2. 训练曲线图: 每个变体的 loss/AUC 随 epoch 变化
  3. 全变体叠加 AUC 对比曲线 (mean ± std)
  4. 组件贡献瀑布图: B、C 各自的 AUC 增量和协同效应
  5. AUC 分布箱线图: 各变体多次运行的分布
  6. 知识状态演化曲线
  7. 注意力热力图
  8. 规则激活分布
  9. t-SNE 嵌入聚类
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns


class AblationVisualizer:

    def __init__(self, output_dir: str | Path = "outputs/figures"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        plt.rcParams.update({
            "font.size": 12,
            "figure.figsize": (10, 6),
            "figure.dpi": 150,
        })

    # ── 1. 消融柱状图 ──

    def plot_ablation_bars(
        self,
        results: dict[str, dict[str, list[float]]],
        metrics: list[str] | None = None,
    ) -> Path:
        if metrics is None:
            metrics = ["auc", "accuracy", "f1"]

        available_metrics = [m for m in metrics if any(m in v for v in results.values())]
        if not available_metrics:
            available_metrics = ["auc"]

        variants = list(results.keys())
        n_metrics = len(available_metrics)
        x = np.arange(n_metrics)
        width = 0.8 / len(variants)

        fig, ax = plt.subplots(figsize=(12, 6))
        colors = sns.color_palette("husl", len(variants))

        for i, variant in enumerate(variants):
            means = [np.mean(results[variant].get(m, [0])) for m in available_metrics]
            stds = [np.std(results[variant].get(m, [0])) for m in available_metrics]
            offset = (i - len(variants) / 2 + 0.5) * width
            bars = ax.bar(x + offset, means, width, yerr=stds,
                          label=variant, color=colors[i], capsize=3, alpha=0.85)
            for bar, mean in zip(bars, means):
                ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.005,
                        f"{mean:.3f}", ha="center", va="bottom", fontsize=8)

        ax.set_xlabel("Metrics")
        ax.set_ylabel("Score")
        ax.set_title("Ablation Study: Component Contribution (DKT + FoLiBiKT + simpleKT)")
        ax.set_xticks(x)
        ax.set_xticklabels([m.upper() for m in available_metrics])
        ax.legend(loc="lower right")
        ax.set_ylim(0.5, 0.95)
        ax.grid(axis="y", alpha=0.3)

        path = self.output_dir / "ablation_bars.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 2. 训练曲线 (per variant) ──

    def plot_training_curves(
        self,
        history_per_variant: dict[str, list[list[dict]]],
    ) -> list[Path]:
        """
        Args:
            history_per_variant: {variant_name: [run_history, ...]}
                每个 run_history 是一个 list[dict]，包含 epoch, train_loss, val_auc 等
        """
        paths = []
        for variant, runs in history_per_variant.items():
            if not runs:
                continue
            fig, axes = plt.subplots(1, 2, figsize=(14, 5))

            for run_idx, history in enumerate(runs):
                epochs = [h["epoch"] for h in history]
                train_losses = [h.get("train_loss", 0) for h in history]
                val_aucs = [h.get("val_auc", 0) for h in history]

                axes[0].plot(epochs, train_losses, alpha=0.4, linewidth=0.8)
                axes[1].plot(epochs, val_aucs, alpha=0.5, linewidth=0.8)

            axes[0].set_xlabel("Epoch")
            axes[0].set_ylabel("Train Loss")
            axes[0].set_title(f"{variant} — Training Loss")
            axes[0].grid(alpha=0.3)

            axes[1].set_xlabel("Epoch")
            axes[1].set_ylabel("Validation AUC")
            axes[1].set_title(f"{variant} — Validation AUC")
            axes[1].grid(alpha=0.3)

            fig.tight_layout()
            safe_name = variant.replace(" ", "_").replace("/", "_").replace("(", "").replace(")", "")
            path = self.output_dir / f"curves_{safe_name}.png"
            fig.savefig(path)
            plt.close(fig)
            paths.append(path)

        return paths

    # ── 3. 全变体叠加 AUC 曲线 ──

    def plot_all_variants_auc(
        self,
        history_per_variant: dict[str, list[list[dict]]],
    ) -> Path:
        fig, ax = plt.subplots(figsize=(12, 6))
        colors = sns.color_palette("husl", len(history_per_variant))

        for idx, (variant, runs) in enumerate(history_per_variant.items()):
            if not runs:
                continue
            max_epochs = max(len(r) for r in runs)
            auc_matrix = np.full((len(runs), max_epochs), np.nan)
            for r_idx, history in enumerate(runs):
                for h in history:
                    ep = h["epoch"] - 1
                    if ep < max_epochs:
                        auc_matrix[r_idx, ep] = h.get("val_auc", np.nan)

            mean_auc = np.nanmean(auc_matrix, axis=0)
            std_auc = np.nanstd(auc_matrix, axis=0)
            epochs = np.arange(1, max_epochs + 1)

            valid = ~np.isnan(mean_auc)
            ax.plot(epochs[valid], mean_auc[valid], label=variant, linewidth=2, color=colors[idx])
            ax.fill_between(
                epochs[valid],
                (mean_auc - std_auc)[valid],
                (mean_auc + std_auc)[valid],
                alpha=0.15, color=colors[idx],
            )

        ax.set_xlabel("Epoch")
        ax.set_ylabel("Validation AUC")
        ax.set_title("All Variants — Mean Validation AUC (±1σ)")
        ax.legend()
        ax.grid(alpha=0.3)

        path = self.output_dir / "all_variants_auc.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 4. 组件贡献瀑布图 ──

    def plot_component_waterfall(
        self,
        results: dict[str, dict[str, list[float]]],
    ) -> Path:
        variant_order = ["A only (Base)", "A+B (w/o KTAttn)", "A+C (w/o SparseAttn)", "A+B+C (Full)"]
        available = [v for v in variant_order if v in results]
        if len(available) < 2:
            available = list(results.keys())

        means = {v: np.mean(results[v].get("auc", [0])) for v in available}

        fig, ax = plt.subplots(figsize=(10, 6))
        base_key = available[0]
        base_val = means[base_key]

        labels = [base_key]
        values = [base_val]
        bottoms = [0]
        colors_list = ["#4C72B0"]

        for v in available[1:]:
            delta = means[v] - base_val
            labels.append(f"+{v.split('(')[0].strip()}\n(Δ={delta:+.4f})")
            values.append(abs(delta))
            bottoms.append(min(base_val, means[v]))
            colors_list.append("#55A868" if delta > 0 else "#C44E52")
            base_val = means[v]

        bars = ax.bar(range(len(labels)), values, bottom=bottoms, color=colors_list, alpha=0.85, width=0.6)
        for i, (bar, val, bot) in enumerate(zip(bars, values, bottoms)):
            ax.text(bar.get_x() + bar.get_width() / 2, bot + val + 0.002,
                    f"{bot + val:.4f}", ha="center", va="bottom", fontsize=9, fontweight="bold")

        ax.set_xticks(range(len(labels)))
        ax.set_xticklabels(labels, fontsize=9)
        ax.set_ylabel("AUC")
        ax.set_title("Component Contribution Waterfall")
        ax.grid(axis="y", alpha=0.3)

        path = self.output_dir / "waterfall.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 5. AUC 分布箱线图 ──

    def plot_auc_boxplot(
        self,
        results: dict[str, dict[str, list[float]]],
    ) -> Path:
        fig, ax = plt.subplots(figsize=(10, 6))

        data = []
        labels = []
        for variant, metrics in results.items():
            aucs = metrics.get("auc", [])
            if aucs:
                data.append(aucs)
                labels.append(variant)

        if data:
            bp = ax.boxplot(data, labels=labels, patch_artist=True, showmeans=True,
                           meanprops={"marker": "D", "markerfacecolor": "red", "markersize": 6})
            colors = sns.color_palette("husl", len(data))
            for patch, color in zip(bp["boxes"], colors):
                patch.set_facecolor((*color, 0.4))

        ax.set_ylabel("AUC")
        ax.set_title("AUC Distribution Across Folds & Seeds")
        ax.grid(axis="y", alpha=0.3)

        path = self.output_dir / "auc_boxplot.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 6. 知识状态演化曲线 ──

    def plot_mastery_evolution(
        self,
        mastery_probs: np.ndarray,
        skill_names: list[str] | None = None,
        student_id: str = "S001",
    ) -> Path:
        fig, ax = plt.subplots(figsize=(14, 6))
        T, n_skills = mastery_probs.shape

        if skill_names is None:
            skill_names = [f"KC_{i}" for i in range(n_skills)]

        colors = sns.color_palette("tab10", n_skills)
        for i in range(min(n_skills, 10)):
            ax.plot(range(T), mastery_probs[:, i],
                    label=skill_names[i], color=colors[i], linewidth=1.5)

        ax.set_xlabel("Interaction Step")
        ax.set_ylabel("Mastery Probability")
        ax.set_title(f"Knowledge State Evolution — Student {student_id}")
        ax.legend(bbox_to_anchor=(1.05, 1), loc="upper left", fontsize=9)
        ax.set_ylim(0, 1)
        ax.grid(alpha=0.3)

        path = self.output_dir / f"mastery_evolution_{student_id}.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 7. 注意力热力图 ──

    def plot_attention_heatmap(
        self,
        attention_weights: np.ndarray,
        title: str = "FoLiBiKT Forgetting Attention",
    ) -> Path:
        fig, ax = plt.subplots(figsize=(10, 8))
        sns.heatmap(
            attention_weights, cmap="YlOrRd", ax=ax,
            xticklabels=10, yticklabels=10,
            cbar_kws={"label": "Attention Weight"},
        )
        ax.set_xlabel("Key Position (past)")
        ax.set_ylabel("Query Position (current)")
        ax.set_title(title)

        path = self.output_dir / "attention_heatmap.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 8. 规则激活分布 ──

    def plot_rule_activations(
        self,
        activations: np.ndarray,
        rule_descriptions: list[str] | None = None,
    ) -> Path:
        n_rules = activations.shape[1]
        if rule_descriptions is None:
            rule_descriptions = [f"Rule {i+1}" for i in range(n_rules)]

        fig, axes = plt.subplots(1, 2, figsize=(16, 6))

        mean_act = activations.mean(axis=0)
        colors = sns.color_palette("coolwarm", n_rules)
        axes[0].barh(range(n_rules), mean_act, color=colors)
        axes[0].set_yticks(range(n_rules))
        axes[0].set_yticklabels(rule_descriptions, fontsize=9)
        axes[0].set_xlabel("Mean Activation Strength")
        axes[0].set_title("Average Rule Activation")

        sns.boxplot(data=activations, orient="h", ax=axes[1], palette="coolwarm")
        axes[1].set_yticklabels(rule_descriptions, fontsize=9)
        axes[1].set_xlabel("Activation Strength")
        axes[1].set_title("Rule Activation Distribution")

        path = self.output_dir / "rule_activations.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path

    # ── 9. t-SNE 嵌入 ──

    def plot_tsne_embeddings(
        self,
        embeddings: np.ndarray,
        labels: np.ndarray,
        label_names: list[str] | None = None,
    ) -> Path:
        from sklearn.manifold import TSNE

        tsne = TSNE(n_components=2, random_state=42, perplexity=min(30, len(embeddings) - 1))
        coords = tsne.fit_transform(embeddings)

        fig, ax = plt.subplots(figsize=(10, 8))
        unique_labels = np.unique(labels)
        colors = sns.color_palette("husl", len(unique_labels))

        for i, label in enumerate(unique_labels):
            mask = labels == label
            name = label_names[i] if label_names else f"Group {label}"
            ax.scatter(coords[mask, 0], coords[mask, 1],
                       c=[colors[i]], label=name, alpha=0.6, s=20)

        ax.set_xlabel("t-SNE 1")
        ax.set_ylabel("t-SNE 2")
        ax.set_title("Student Representation Clusters")
        ax.legend()

        path = self.output_dir / "tsne_embeddings.png"
        fig.tight_layout()
        fig.savefig(path)
        plt.close(fig)
        return path
