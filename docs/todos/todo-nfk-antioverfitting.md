# NFK 抗过拟合增强 TODO

> 基于 AutoDL 全量消融实验（ASSISTments 2009, 5 fold × seed 42）的过拟合诊断。
> 当前 best AUC 在不同折间波动大（0.731-0.758），需要正则化增强。

---

## 诊断

| 指标 | Fold 3 | Fold 4 | 说明 |
|------|--------|--------|------|
| Best AUC | 0.7312 | 0.7582 | 折间差 0.027 |
| Best Epoch | 12 | 11 | 合理 |
| Epoch 30 AUC | 0.6783 | ~0.68 | 峰值后持续下降 |
| train_loss 下降幅度 | 0.55→0.43 | 类似 | 训练端过于容易 |
| val_loss 趋势 | 0.563→0.700+ | 类似 | 验证端持续恶化 |

**根因**：模型在 best epoch 后继续记忆训练数据，val_loss 持续上涨。Early stopping 已捕获最优点，但最优点本身可以通过更强正则化提升。

---

## 改动清单

### 1. Label Smoothing（1 行改动，预期 +0.3-0.5% AUC）

- [x] 文件: `research/nfk/training/loss.py`
- [x] 改动: `KTLoss.__init__` 新增 `label_smoothing` 参数

```python
class KTLoss(nn.Module):
    def __init__(self, lambda_kt: float = 1.0, label_smoothing: float = 0.05):
        super().__init__()
        self.lambda_kt = lambda_kt
        self.label_smoothing = label_smoothing

    def forward(self, model_output, labels):
        kt_pred = model_output["kt_pred"]
        valid_mask = labels != -1
        if valid_mask.sum() == 0:
            zero = torch.tensor(0.0, device=kt_pred.device, requires_grad=True)
            return {"loss_total": zero, "loss_kt": zero}

        valid_labels = labels[valid_mask].float()
        if self.label_smoothing > 0:
            valid_labels = valid_labels * (1 - self.label_smoothing) + self.label_smoothing / 2

        loss_kt = F.binary_cross_entropy_with_logits(
            kt_pred[valid_mask].float(), valid_labels,
        )
        return {"loss_total": self.lambda_kt * loss_kt, "loss_kt": loss_kt}
```

- [x] 配置: `NFKConfig` 新增 `label_smoothing: float = 0.05`

### 2. Sequence Augmentation（训练时随机丢弃交互步）

- [ ] 文件: `research/nfk/models/component_a.py`
- [ ] 改动: `DKTBase.forward` 训练时随机 mask 10% 的时间步

```python
def forward(self, question_ids, responses):
    q_emb = self.q_embed(question_ids)
    r_emb = self.r_embed(responses)
    x = torch.cat([q_emb, r_emb], dim=-1)

    h, _ = self.lstm(x)
    h = self.dropout(h)

    if self.training:
        seq_mask = (torch.rand(h.shape[0], h.shape[1], 1, device=h.device) > 0.1).float()
        h = h * seq_mask

    return h, q_emb
```

### 3. Weight Decay 网格搜索

- [ ] 文件: `research/nfk/run_local.py` 或 `research/nfk/autodl_train.py`
- [ ] 在 AutoDL 上对比：

| weight_decay | 预期效果 |
|-------------|---------|
| 0.01 (当前) | 基线 |
| 0.03 | 更强约束 |
| 0.05 | 最强约束 |

- [ ] 只需跑 1 fold × 1 seed 的 A+B+C 变体，对比 val_auc 峰值

### 4. 增加 Embedding Dropout（可选）

- [ ] 文件: `research/nfk/models/component_a.py`
- [ ] 对题目嵌入和响应嵌入施加独立 dropout

```python
q_emb = self.embed_dropout(self.q_embed(question_ids))
r_emb = self.embed_dropout(self.r_embed(responses))
```

---

## 实施优先级

| 改动 | 代码量 | 风险 | 预期收益 | 优先级 |
|------|--------|------|---------|--------|
| Label Smoothing | 5 行 | 零 | +0.3-0.5% AUC | P0 |
| Sequence Augmentation | 3 行 | 低 | +0.2-0.5% AUC | P0 |
| Weight Decay 搜索 | 0 行（参数调整） | 零 | +0.1-0.3% AUC | P1 |
| Embedding Dropout | 3 行 | 低 | +0.1-0.2% AUC | P2 |

**P0 合计改动：~8 行代码，预期 +0.5-1.0% AUC（将最佳 AUC 从 ~0.758 推向 ~0.765-0.770）**

---

## 验证方式

改动后在 AutoDL 上重跑 quick 消融（1 fold × 1 seed），对比：

```bash
# 改动前
python research/nfk/run_local.py --quick --max-epochs 100 --patience 30

# 改动后
python research/nfk/run_local.py --quick --max-epochs 100 --patience 30
```

对比 A+B+C 变体的 best AUC 是否提升。
