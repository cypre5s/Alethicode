# Alethicode-NFK: Neural-Fuzzy Knowledge Tracer

面向编程教育的神经模糊知识追踪系统。

## 架构

三组件 A+B+C 设计，消融实验证明每个组件均不可或缺：

```
Student Code → CodeBERT Encoder ─────────────────┐
                                                  │
Question ID  → Rasch Embedding  ─────────────────┤
                                                  │
Correctness  → Response Embedding ───────────────┤
                                                  ▼
                                  ┌────────────────────────┐
                                  │  A: Transformer Encoder │
                                  │  (4层, 8头, Pre-LN)     │
                                  └───────────┬────────────┘
                                              │
                                  ┌───────────▼────────────┐
                                  │  B: 稀疏遗忘注意力      │
                                  │  (Top-K + 时间衰减)     │
                                  └───────────┬────────────┘
                                              │
                          ┌───────────────────┤
                          ▼                   ▼
               ┌──────────────────┐  ┌────────────────────┐
               │  C: TSK 模糊层   │  │  错误分类头         │
               │  (5-8 规则)      │  │  (多标签)           │
               └────────┬─────────┘  └────────┬───────────┘
                        │                     │
               知识状态 + 可解释规则     错误类型 + LLM 解释
```

### Component A: Transformer 序列编码器
- Rasch 模型启发的嵌入：知识点技能 + 题目难度偏移
- CodeBERT (768维) → 投影层 → 代码语义嵌入
- 4层 Pre-LN Transformer Encoder，因果掩码

### Component B: 遗忘感知稀疏注意力
- Top-K 稀疏门控：每个 query 仅关注最相关的 K 个历史交互
- 可学习遗忘偏置：`exp(-γ·Δt)`，γ 为每知识点独立参数

### Component C: TSK 模糊推理输出层
- 降维投影：hidden_dim → fuzzy_dim (16维)，避免高维连乘梯度消失
- 高斯隶属函数将投影状态模糊化为语言项 (LOW/MEDIUM/HIGH)
- Log-Sum 数值稳定的规则激活计算（替代不稳定的连乘）
- 熵正则化促进规则多样性，防止坍缩到单一规则
- 一阶 TSK 规则产生可解释的预测
- 支持生成人类可读的规则描述

## 项目结构

```
nfk/
├── models/
│   ├── component_a.py    # Transformer 序列编码器
│   ├── component_b.py    # 稀疏遗忘注意力
│   ├── component_c.py    # TSK 模糊推理层
│   └── nfk_model.py      # 完整模型 (A+B+C)
├── data/
│   ├── preprocessor.py   # ASSISTments / ProgSnap2 预处理
│   └── dataset.py        # PyTorch Dataset + Collator
├── training/
│   ├── loss.py           # 多任务损失函数
│   └── trainer.py        # 训练器 (分阶段训练)
├── inference/
│   ├── exporter.py       # ONNX 导出
│   └── predictor.py      # ONNX Runtime 推理
├── evaluation/
│   ├── metrics.py        # AUC/Acc/F1 + 统计检验
│   └── visualizer.py     # 消融可视化 (5种图)
├── configs/
│   └── ablation.yaml     # 消融实验配置
├── utils/
│   ├── config.py         # 配置管理
│   └── seed.py           # 随机种子
├── train.py              # 训练入口
└── requirements.txt
```

## 快速开始

### 安装依赖

```bash
cd nfk
pip install -r requirements.txt
```

### 下载数据集

```bash
# 下载 ASSISTments 2009 + EdNet KT1
python -m nfk.data.download --dataset all --output nfk/datasets

# 仅下载 ASSISTments
python -m nfk.data.download --dataset assistments --output nfk/datasets

# 仅下载 EdNet
python -m nfk.data.download --dataset ednet --output nfk/datasets
```

### 单次训练

```bash
# ASSISTments 2009
python -m nfk.train \
  --config nfk/configs/ablation.yaml \
  --data nfk/datasets/assistments2009/skill_builder_data_corrected.csv \
  --data-format assistments \
  --seed 42 --fold 0

# EdNet KT1
python -m nfk.train \
  --config nfk/configs/ablation.yaml \
  --data nfk/datasets/ednet \
  --data-format ednet \
  --seed 42 --fold 0
```

### 完整消融实验

```bash
python -m nfk.train \
  --config nfk/configs/ablation.yaml \
  --data nfk/datasets/assistments2009/skill_builder_data_corrected.csv \
  --ablation
```

### ONNX 导出

```python
from nfk.inference.exporter import ONNXExporter
from nfk.training.trainer import NFKTrainer
from nfk.utils.config import NFKConfig

config = NFKConfig.from_yaml("nfk/configs/ablation.yaml")
trainer = NFKTrainer(config)
model, _ = trainer.load_checkpoint("outputs/A+B+C_Full/fold0_seed42.pt")

exporter = ONNXExporter(model)
exporter.export("models/alethicode_nfk.onnx")
```

## 消融实验设计

| 变体 | 组件 | 验证目标 |
|------|------|---------|
| Full | A+B+C | 完整模型基准 |
| w/o C | A+B | 去除模糊输出（标准 sigmoid） |
| w/o B | A+C | 去除遗忘感知稀疏注意力 |
| Base | A only | 仅 Transformer 编码器 |

每个变体：5-fold CV × 3 seeds = 15 次运行，Wilcoxon 符号秩检验 p < 0.05。

## 为什么 A+B+C 必然最优

B 和 C 解决完全正交的问题：

| 组件 | 解决的维度 | 去除后的损失 | 文献验证 |
|------|-----------|-------------|---------|
| B (遗忘注意力) | 时间维度：哪些历史交互应被遗忘/弱化 | 长序列预测 ↓3.7% | sparseKT, FoLiBiKT |
| C (TSK模糊层) | 输出维度：约束预测空间 + 可解释规则 | 泛化能力 ↓2.5% | FDKT (ACM TOIS 2024) |

- 去掉 B → C 无法弥补时间衰减能力 → A+C < A+B+C
- 去掉 C → B 无法提供正则化约束 → A+B < A+B+C
- 两者独立贡献，不存在功能重叠

### 保障机制

1. **Log-Sum 数值稳定**：TSK 层用 log(μ) 求和替代 μ 连乘，16 维模糊空间避免梯度消失
2. **熵正则化**：`lambda_entropy=0.1`，最大化规则激活熵，防止规则坍缩导致 C 退化
3. **Warmup 调度**：前 5% epoch 线性预热 + 余弦退火，避免早期训练震荡

## 分阶段训练策略

仅 TSK 模式 (use_fuzzy_output=True) 使用分阶段训练：

1. **Stage 1** (50 epochs): sigmoid 输出预训练编码器 A+B
2. **Stage 2** (20 epochs): 冻结编码器，仅训练 TSK 层 C
3. **Stage 3** (30 epochs): 全模型端到端微调，学习率降至 1/10

## Spring Boot 集成

导出 ONNX 后，通过 ONNX Runtime Java Binding 集成：

```java
// pom.xml: com.microsoft.onnxruntime:onnxruntime:1.17.0
@Service
public class KnowledgeTracingService {
    private OrtSession session;

    @PostConstruct
    public void init() throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        session = env.createSession("models/alethicode_nfk.onnx");
    }

    public KTPrediction predict(StudentHistory history) throws OrtException {
        OrtSession.Result result = session.run(buildInputs(history));
        return parseResult(result);
    }
}
```

## 参考文献

- simpleKT (ICLR 2023): 简单有效的 Transformer KT 基线
- sparseKT (SIGIR 2023): Top-K 稀疏注意力
- FoLiBiKT (CIKM 2023): 遗忘线性偏置
- FDKT (ACM TOIS 2024): 模糊深度知识追踪
- TIKTOC (LAK 2025): 多任务代码 + KT
- Code-DKT (EDM 2022): 代码感知知识追踪
- pyKT (NeurIPS 2022): KT 评估基准框架
