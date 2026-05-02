# PPO Scheme C BASE Ablation 实验日志

**日期**: 2026-04-17  
**阶段目标**: 先验证并修正 PPO 在 Scheme C + BASE inventory 下的“保货过头、收益兑现不足”问题；只有看到 PPO 明显逼近或超过 Myopic，才升级到正式大预算训练。  
**当前约束**: 不改 Scheme C 任务定义，不改 Tight 档，不扩 baseline 家族，不直接上大预算。

---

## 0. 当前仓库核查

本轮在 `/home/cypress/Alethicode` 内完成以下关键词检索：

- `PPO`
- `Myopic`
- `EIB`
- `ent_coef`
- `finetune`
- `stockout_rate`
- `early_sellthrough`
- `high_value_type_service_rate`
- `inventory_utilization_total`
- `scarce_items_avg_stockout_time`
- `Scheme C`
- `EXP-19`

核查结论：

- 当前仓库未发现 PPO / Scheme C / Myopic / EIB 的训练入口、配置文件或结果表。
- 当前仓库未发现 `ent_coef` 基线值。
- 因此本日志先冻结实验结论与下一步 ablation 规程；实际启动训练前，必须回到 EXP-19 原始实验目录或 AutoDL 训练配置确认 `ent_coef` 基线值与训练命令。

未验证前提：

- EXP-19a / 19b / 19c 的原始结果与配置存在于当前仓库以外的实验工作区。
- Scheme C 任务定义已经完成并可复现。
- BASE inventory 配置与 Tight inventory 配置在外部实验代码中可通过显式参数区分。

---

## 1. 冻结 EXP-19a / 19b / 19c 当前结论

本阶段不再改 Scheme C 任务定义。当前结论固定为：

- Scheme C 成功引入 horizon 内多 type 切换。
- PPO 学到了延后售罄 / 保货机制。
- 当前默认训练下存在 **保货过头** 问题：PPO 过度延后库存释放，导致收益兑现不足。
- Tight 库存没有放大 PPO 优势，反而使 PPO 相对 Myopic 的 gap 扩大。

实验解释口径：

- 当前问题不是“PPO 完全没学到策略”，而是学到的保货行为过强。
- 下一步优先修正“策略过集中 + 保货过头”，不是用更大预算掩盖问题。
- 若 BASE 小规模 ablation 不能让 PPO 明显逼近 Myopic，则不升级到 `5 seeds x 200k x 200k`。

---

## 2. BASE 档小规模 ablation 队列

### 2.1 必须先确认的 baseline

| 项目 | 当前状态 | 处理要求 |
|------|----------|----------|
| Scheme | `Scheme C` | 不再修改任务定义 |
| Inventory | `BASE` | 本轮只跑 BASE |
| Seeds | `2 seeds` | 先小样本筛配置 |
| Pretrain baseline | `50k` | 保持为默认 baseline |
| Finetune baseline | `50k` | 作为对照组 |
| `ent_coef` baseline | 未在当前仓库找到 | 启动训练前必须从 EXP-19 原始配置确认 |

Fail-fast 规则：

- 未确认 `ent_coef` 基线值前，不启动 `ent_coef x5` / `ent_coef x10`。
- 未确认 BASE inventory 参数前，不启动任何训练。
- 任何命令若无法同时显式固定 `Scheme C` 与 `BASE inventory`，停止执行，不用默认参数猜测。

### 2.2 Ablation A: 提高熵正则

| 实验 ID | 配置 | Seeds | 状态 | 备注 |
|---------|------|-------|------|------|
| `EXP-20A-BASE-ENT5` | `ent_coef = baseline x 5`, `50k pretrain + 50k finetune` | 2 | 待跑 | 最高优先级 Step 1 |
| `EXP-20A-BASE-ENT10` | `ent_coef = baseline x 10`, `50k pretrain + 50k finetune` | 2 | 待跑 | 若 x5 不稳定，再判断是否跑 |

目的：

- 提高策略熵，缓解策略过集中。
- 观察 PPO 是否减少过度保货，同时保持高价值 type 服务能力。

### 2.3 Ablation B: 只延长 finetune

| 实验 ID | 配置 | Seeds | 状态 | 备注 |
|---------|------|-------|------|------|
| `EXP-20B-BASE-FT100` | `50k pretrain + 100k finetune`, `ent_coef = baseline` | 2 | 待跑 | 最高优先级 Step 2 |
| `EXP-20B-BASE-FT150` | `50k pretrain + 150k finetune`, `ent_coef = baseline` | 2 | 待跑 | FT100 有改善再跑 |

目的：

- 验证默认 `50k finetune` 是否不足以把保货机制转化为收益兑现。
- 不改变熵正则，隔离“训练长度”变量。

### 2.4 Ablation C: 高熵 + 更长 finetune

| 实验 ID | 配置 | Seeds | 状态 | 备注 |
|---------|------|-------|------|------|
| `EXP-20C-BASE-ENT5-FT100` | `ent_coef = baseline x 5`, `50k pretrain + 100k finetune` | 2 | 待跑 | 最高优先级 Step 3 |
| `EXP-20C-BASE-ENT5-FT150` | `ent_coef = baseline x 5`, `50k pretrain + 150k finetune` | 2 | 待跑 | 前三组出现改善后再跑 |

目的：

- 同时缓解策略过集中与收益兑现不足。
- 观察 high-value service 与 utilization 是否能同时回升。

---

## 3. 每组统一输出指标

### 3.1 主结果

| 指标 | 必填 | 说明 |
|------|------|------|
| `mean_revenue` | 是 | PPO / Myopic / EIB / Random 可比口径 |
| `revenue_95_ci` | 是 | 2 seeds 阶段仍记录，3 seeds 后进入正式比较 |
| `app_ratio` | 是 | 与既有 EXP-19 口径保持一致 |

### 3.2 机制指标

| 指标 | 必填 | 解释目标 |
|------|------|----------|
| `high_value_type_service_rate` | 是 | 高价值 type 是否继续被保住 |
| `inventory_utilization_total` | 是 | 是否从过度保货中回升 |
| `scarce_items_avg_stockout_time` | 是 | 售罄时间是否仍然后移，但不过度延后 |
| `early_sellthrough` | 是 | 早期库存释放是否恢复 |
| `stockout_rate` | 是 | 是否出现过早售罄副作用 |

### 3.3 训练行为

每组必须保存：

- train curve
- eval curve
- entropy curve
- best checkpoint 指标
- final checkpoint 指标
- best vs final 的 revenue / mechanism 指标差异

记录要求：

- 若 best checkpoint 明显优于 final checkpoint，正式比较优先报告 best，同时附 final 作为稳定性诊断。
- 若 entropy 提高但 revenue 不升，不能只用“探索更充分”解释，需要回看 utilization 与 high-value service 是否被破坏。
- 若 utilization 回升但 high-value service 掉队，说明不是修复保货过头，而是退化成提前清仓。

---

## 4. 是否升级大预算的门槛

只有满足以下任一条，才进入正式版大预算训练：

- PPO 均值已经接近或超过 Myopic。
- PPO 对 Myopic 的 gap 缩小到 **3% 以内**。
- 机制指标同时显示：
  - 仍然保货；
  - `inventory_utilization_total` 回升；
  - `high_value_type_service_rate` 不再掉队。

不满足以上条件时：

- 不升级到 `5 seeds x 200k x 200k`。
- 不回头改 Tight 档。
- 不继续扩大 baseline 家族。
- 不修改 synthetic 数据主实验。

---

## 5. 建议执行顺序

| 顺序 | 实验 | 决策 |
|------|------|------|
| Step 1 | `EXP-20A-BASE-ENT5` | 判断提高熵正则是否缓解策略集中 |
| Step 2 | `EXP-20B-BASE-FT100` | 判断只延长 finetune 是否改善收益兑现 |
| Step 3 | `EXP-20C-BASE-ENT5-FT100` | 判断组合策略是否同时保货并提高 utilization |
| Step 4 | 从前三组选择最优配置 | 若 PPO 逼近 / 超过 Myopic，升到 3 seeds 正式比 |

暂不执行：

- `EXP-20A-BASE-ENT10`
- `EXP-20B-BASE-FT150`
- `EXP-20C-BASE-ENT5-FT150`

这些配置只有在前三组指标显示改善或需要定位边界时再启动。

---

## 6. 结果记录模板

### 6.1 单组结果表

| 实验 ID | Seed | Checkpoint | Mean Revenue | 95% CI | App Ratio | Entropy | 备注 |
|---------|------|------------|--------------|--------|-----------|---------|------|
| 待填 | 待填 | best | 待填 | 待填 | 待填 | 待填 | 待填 |
| 待填 | 待填 | final | 待填 | 待填 | 待填 | 待填 | 待填 |

### 6.2 机制指标表

| 实验 ID | Seed | `high_value_type_service_rate` | `inventory_utilization_total` | `scarce_items_avg_stockout_time` | `early_sellthrough` | `stockout_rate` |
|---------|------|--------------------------------|-------------------------------|----------------------------------|---------------------|-----------------|
| 待填 | 待填 | 待填 | 待填 | 待填 | 待填 | 待填 |

### 6.3 与 Myopic gap

| 实验 ID | PPO Mean Revenue | Myopic Mean Revenue | Gap | 是否 <= 3% | 决策 |
|---------|------------------|---------------------|-----|------------|------|
| 待填 | 待填 | 待填 | 待填 | 待填 | 待填 |

---

## 7. 当前阻塞项

在启动训练前，需要补齐以下信息：

- EXP-19 原始实验目录路径。
- `ent_coef` baseline 值。
- BASE inventory 的显式配置参数或配置文件。
- Scheme C 的显式任务配置参数或配置文件。
- PPO / Myopic / EIB / Random 的统一评估命令。
- train / eval / entropy curve 的保存路径。

拿到以上信息后，优先执行：

```bash
# 伪命令，仅表达必须显式固定的参数；不得直接复制运行
run_exp \
  --scheme scheme_c \
  --inventory base \
  --algo ppo \
  --pretrain_steps 50000 \
  --finetune_steps 50000 \
  --ent_coef "<baseline_ent_coef * 5>" \
  --seeds "<two_seed_list>" \
  --metrics mean_revenue,revenue_95_ci,app_ratio,high_value_type_service_rate,inventory_utilization_total,scarce_items_avg_stockout_time,early_sellthrough,stockout_rate,entropy \
  --save_best_checkpoint true \
  --save_final_checkpoint true
```

---

## 8. 一句话结论

当前阶段先不加大预算，先在 **Scheme C + BASE inventory** 上用更高熵正则和更长 finetune 修正“保货过头”；只有当 PPO 开始逼近或超过 Myopic，才升级到正式大预算实验。
