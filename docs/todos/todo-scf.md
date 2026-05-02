# TODO: 自适应渐退脚手架 (Adaptive Fading Scaffolding)

## 顶刊依据

- Renkl & Atkinson (2003) *"Structuring the Transition From Example Study to Problem Solving..."*, Educational Psychologist
- Kalyuga & Sweller (2018) *"The Expertise Reversal Effect"*, Educational Psychology Review
- Salden et al. (2010) *"Worked Examples and Tutored Problem Solving: Redundant or Synergistic?"*, Computers in Human Behavior

---

## 核心思路

根据学生对当前题目所涉 KC 的 mastery level，在 SCAFFOLDING 阶段动态选择帮助形式，
解决"专家逆转效应"——对新手有用的详细指导，对进步后的学生反而是认知负担。

---

## Mastery 初始值与阶段划分

### 现有 mastery 体系（关键参数）

| 参数 | 值 | 位置 |
|------|------|------|
| p_init（KC 默认初始值） | 0.3 | `ai_knowledge_component.p_init` 列默认值 |
| p_init（数据库实际值） | **0.1**（多数 KC） | V13 迁移脚本 backfill 后的实际数据 |
| 更新公式 | `mastery = 0.7 * prev + 0.3 * outcome` | `MasteryService.java:60` |
| 弱 KC 阈值 | 0.6 | `LearnerProfileProjector.java:38` |
| 历史窗口 | 最近 20 次学习事件 | `MasteryService.java:37` |

### Mastery 增长模拟（p_init = 0.1）

| 连续正确次数 | mastery 值 | 阶段 |
|-------------|-----------|------|
| 0（初始） | 0.100 | 新手 |
| 1 | 0.370 | 新手 |
| 2 | 0.559 | 过渡 |
| 3 | 0.691 | 熟练 |
| 4 | 0.784 | 熟练 |

### Mastery 增长模拟（p_init = 0.3）

| 连续正确次数 | mastery 值 | 阶段 |
|-------------|-----------|------|
| 0（初始） | 0.300 | 新手 |
| 1 | 0.510 | 过渡 |
| 2 | 0.657 | 熟练 |
| 3 | 0.760 | 熟练 |

### 自适应渐退阶段划分

> 综合 p_init 分布（0.1~0.3）和更新公式特性，阶段阈值设计如下：

| 阶段 | mastery 范围 | 脚手架形式 | 设计依据 |
|------|-------------|-----------|---------|
| **新手 (Novice)** | < 0.4 | **完整示例 (Worked Example)** | p_init=0.1 的学生需要至少 2 次正确才能脱离此阶段；p_init=0.3 的学生需要 1 次正确。确保初学者获得充分指导 |
| **过渡 (Transitional)** | 0.4 ≤ m < 0.7 | **渐退示例 (Faded Example)** | 对应 Vygotsky 的"最近发展区"；高于弱 KC 阈值 0.6 才进入熟练，留出渐退缓冲区 |
| **熟练 (Proficient)** | ≥ 0.7 | **最小提示 (Minimal Hint)** | 高于弱 KC 阈值 0.1 个标准单位，确保学生确实掌握后才减少帮助，避免专家逆转效应的反向误判 |

### 多 KC 聚合规则

一道题可能关联多个 KC（通过 `ai_problem_kc_mapping`），聚合策略：

```
scaffoldingMastery = MIN(masteryByKc[kc] for kc in problem_kcs)
```

**取最小值理由**：取决于最薄弱环节。如果学生 for 循环 mastery=0.8 但 list comprehension mastery=0.2，
整体仍应提供完整示例，因为短板 KC 会成为解题障碍。

---

## 三种脚手架形式详细定义

### 1. 完整示例 (Worked Example) — mastery < 0.4

**定义**：展示一道**同类型但不同的题目**的完整解题过程，包含子目标标注和逐步解释。

**输出结构**：
```json
{
  "card_type": "worked_example",
  "scaffold_level": "full",
  "mastery_snapshot": 0.25,
  "analogy_problem": {
    "title": "统计列表中正数的个数",
    "description": "给定一个整数列表，统计其中正数的个数"
  },
  "steps": [
    {
      "subgoal": "初始化计数器",
      "code": "count = 0",
      "explanation": "我们需要一个变量来记录正数的个数，初始为 0"
    },
    {
      "subgoal": "遍历列表中的每个元素",
      "code": "for num in numbers:",
      "explanation": "用 for 循环依次取出列表中的每一个数字"
    },
    {
      "subgoal": "判断是否满足条件",
      "code": "    if num > 0:",
      "explanation": "检查当前数字是否为正数（大于 0）"
    },
    {
      "subgoal": "满足条件时更新计数器",
      "code": "        count += 1",
      "explanation": "如果是正数，计数器加 1"
    }
  ],
  "bridge_to_current": "现在试着用同样的思路解决你的题目：___。想一想，你的题目中'条件判断'部分应该怎么写？"
}
```

**关键设计**：
- 示例题目来自同 KC、相似难度的已有题库（非当前题目，避免直接泄露答案）
- 每一步包含"子目标标签"（Subgoal Label），帮助初学者建立问题分解思维
- 末尾的 `bridge_to_current` 引导学生将示例迁移到当前题目

### 2. 渐退示例 (Faded Example) — 0.4 ≤ mastery < 0.7

**定义**：展示解题步骤框架，但**部分步骤的代码留空**，由学生填写。留空比例随 mastery 动态调整。

**留空策略**：
```
fade_ratio = (mastery - 0.4) / (0.7 - 0.4)   // 0.0 ~ 1.0
faded_step_count = ceil(total_steps * fade_ratio * 0.7)
// mastery=0.4 → 留空 0%（接近完整示例）
// mastery=0.55 → 留空约 35% 的步骤
// mastery=0.69 → 留空约 67% 的步骤
```

**优先留空规则**（哪些步骤先留空）：
1. 学生 mastery 最高的 KC 对应步骤优先留空
2. "初始化"类步骤优先留空（相对简单）
3. "条件判断"类步骤最后留空（对初学者最难）

**输出结构**：
```json
{
  "card_type": "faded_example",
  "scaffold_level": "faded",
  "mastery_snapshot": 0.52,
  "fade_ratio": 0.4,
  "steps": [
    {
      "subgoal": "初始化计数器",
      "code": null,
      "hint": "需要一个变量来记录个数",
      "faded": true
    },
    {
      "subgoal": "遍历列表中的每个元素",
      "code": "for num in numbers:",
      "explanation": "用 for 循环依次取出列表中的每一个数字",
      "faded": false
    },
    {
      "subgoal": "判断是否满足条件",
      "code": "    if num % 2 == 0:",
      "explanation": "检查当前数字是否为偶数",
      "faded": false
    },
    {
      "subgoal": "满足条件时更新计数器",
      "code": null,
      "hint": "满足条件时应该做什么？",
      "faded": true
    }
  ],
  "student_blanks": ["step_0", "step_3"]
}
```

**关键设计**：
- 留空步骤提供 `hint` 而非 `explanation`，降低认知负荷但保留思考空间
- 前端需要提供填空交互（输入框 + 提交验证）
- 学生填写后，LLM 评估正确性并给出反馈

### 3. 最小提示 (Minimal Hint) — mastery ≥ 0.7

**定义**：不提供示例或步骤框架，仅给出**简短的方向性提示**和关键 KC 提醒。

**输出结构**：
```json
{
  "card_type": "minimal_hint",
  "scaffold_level": "minimal",
  "mastery_snapshot": 0.75,
  "hint": "这道题的核心是列表遍历 + 条件计数，你之前在'统计正数'那道题中用过类似思路。",
  "relevant_kcs": ["for循环", "条件判断"],
  "nudge": "试着直接开始写代码吧，遇到问题再来找我。"
}
```

**关键设计**：
- 引用学生之前成功解决的类似题目（从 `ai_learner_notebook` 检索）
- 鼓励学生独立完成，符合自我决定理论
- 跳过 Parsons Problem，直接进入 CODING 阶段

---

## 与现有架构的融合方案

### Phase 与 CardType 变更

```java
// === CardType.java — 新增两个枚举值 ===
WORKED_EXAMPLE("worked_example", "scaffolding"),
FADED_EXAMPLE("faded_example", "scaffolding"),
MINIMAL_HINT("minimal_hint", "scaffolding"),
// 现有 PARSONS_PROBLEM 保留不动

// === Phase.java — 不变 ===
// SCAFFOLDING 阶段不变，内部通过 scaffold_level 区分形式
```

### 决策流程（SCAFFOLDING 阶段入口）

```
┌──────────────────────────────────────────────────────┐
│             进入 SCAFFOLDING 阶段                      │
│                                                      │
│  1. 从 EvidencePack 获取 masteryByKc                  │
│  2. 计算 scaffoldingMastery = MIN(masteryByKc.values) │
│  3. 查 TutorActionPolicy 决定 scaffold_level          │
│                                                      │
│     ┌─────────────────────────────────────┐          │
│     │ scaffoldingMastery < 0.4            │          │
│     │ → CardType.WORKED_EXAMPLE           │          │
│     │ → 生成完整示例 + 子目标标注            │          │
│     ├─────────────────────────────────────┤          │
│     │ 0.4 ≤ scaffoldingMastery < 0.7      │          │
│     │ → CardType.FADED_EXAMPLE            │          │
│     │ → 生成渐退示例（动态留空比例）          │          │
│     ├─────────────────────────────────────┤          │
│     │ scaffoldingMastery ≥ 0.7            │          │
│     │ → CardType.MINIMAL_HINT             │          │
│     │ → 生成最小提示，跳过详细脚手架         │          │
│     └─────────────────────────────────────┘          │
│                                                      │
│  4. ContextualBanditReranker 可探索性地                │
│     override scaffold_level（灰度实验）                │
│  5. ai_tutor_trace 记录 scaffold_level                │
│     用于离线评估哪种 fading 策略最优                    │
└──────────────────────────────────────────────────────┘
```

### 与现有 Parsons Problem 的关系

```
现有 Parsons Problem（拖拽排序）仍然保留，作为 FADED 阶段的一种形式：

mastery < 0.4  → Worked Example（完整示例，阅读为主）
0.4 ≤ m < 0.55 → Faded Example（填空式，输入交互）
0.55 ≤ m < 0.7 → Parsons Problem（拖拽排序，现有功能）
mastery ≥ 0.7  → Minimal Hint（最小提示，直接编码）

即 Parsons Problem 成为"过渡阶段偏高 mastery"时的脚手架形式，
Faded Example 成为"过渡阶段偏低 mastery"时的脚手架形式。
两者共存，渐进过渡。
```

---

## 实现任务清单

### 后端任务

- [ ] **B1. 新增 CardType 枚举值**
  - 文件：`backend/.../contract/CardType.java`
  - 新增：`WORKED_EXAMPLE`, `FADED_EXAMPLE`, `MINIMAL_HINT`
  - 配置 messageType 和 outputKey 映射

- [ ] **B2. 新增 Card Schema 定义**
  - 文件：`backend/.../schema/CardSchemaRegistry.java`
  - 为三种新 CardType 定义 JSON Schema
  - 注册到 `CardSchemaValidator`

- [ ] **B3. 实现 ScaffoldLevelResolver**
  - 新文件：`backend/.../scaffolding/ScaffoldLevelResolver.java`
  - 输入：`Map<String, Double> masteryByKc`，`Long problemId`
  - 输出：`ScaffoldLevel` 枚举（FULL / FADED / PARSONS / MINIMAL）
  - 核心逻辑：
    ```java
    double minMastery = masteryByKc.values().stream()
        .mapToDouble(Double::doubleValue).min().orElse(0.0);
    if (minMastery < 0.4) return FULL;
    if (minMastery < 0.55) return FADED;
    if (minMastery < 0.7) return PARSONS;
    return MINIMAL;
    ```

- [ ] **B4. 实现 WorkedExampleGenerator**
  - 新文件：`backend/.../scaffolding/WorkedExampleGenerator.java`
  - 从题库检索同 KC、相似难度的"类比题目"
  - 调用 LLM 生成子目标标注 + 步骤解释
  - 生成 `bridge_to_current` 引导语

- [ ] **B5. 实现 FadedExampleGenerator**
  - 新文件：`backend/.../scaffolding/FadedExampleGenerator.java`
  - 根据 mastery 计算 fade_ratio
  - 根据 KC mastery 排序决定留空优先级
  - 调用 LLM 生成带留空的步骤框架

- [ ] **B6. 实现 MinimalHintGenerator**
  - 新文件：`backend/.../scaffolding/MinimalHintGenerator.java`
  - 从 `ai_learner_notebook` 检索学生历史成功题目
  - 生成简短方向性提示

- [ ] **B7. 修改 AITutorWorkflowAdminServiceImpl — SCAFFOLDING 分支**
  - 在 `applyPhaseOutput` 的 SCAFFOLDING case 中：
    1. 调用 `ScaffoldLevelResolver` 决定脚手架级别
    2. 根据级别调用对应 Generator
    3. 现有 Parsons 逻辑保持不动，作为 PARSONS level 的实现
  - 在 `ai_tutor_trace` 中记录 `scaffold_level` 字段

- [ ] **B8. 增强 TutorActionPolicy**
  - 在 SCAFFOLDING 阶段的推荐逻辑中加入 mastery 判断
  - 新增推荐动作：`worked_example`、`faded_example`、`minimal_hint`

- [ ] **B9. 增强 ContextualBanditReranker**
  - 新增脚手架级别的探索性 bonus：
    - 当 mastery 在阈值边界（±0.05）时，给相邻级别加 bonus 以探索
  - 记录探索决策到 `ai_tutor_trace`

- [ ] **B10. Faded Example 填空验证 API**
  - 新增 event_data 字段：`student_blanks_answers`
  - 后端接收学生填空内容，调用 LLM 评估正确性
  - 返回逐步反馈（正确/部分正确/需改进）
  - 评估结果写入 `ai_learning_event` 更新 mastery

- [ ] **B11. 数据库迁移**
  - 新增迁移脚本 `V19__scaffold_fading_support.sql`（或根据当前最新版本号）
  - `ai_tutor_trace` 表新增 `scaffold_level VARCHAR(32)` 列
  - `ai_workflow_session.node_outputs` 的 scaffolding key 兼容新 card 结构

### 前端任务

- [ ] **F1. 新增 WorkedExampleCard.vue**
  - 文件：`frontend/.../cards/WorkedExampleCard.vue`
  - 展示子目标标注 + 代码 + 解释，类似教程界面
  - 底部显示 `bridge_to_current` 引导语
  - "我理解了，开始写代码" 按钮 → 进入 CODING

- [ ] **F2. 新增 FadedExampleCard.vue**
  - 文件：`frontend/.../cards/FadedExampleCard.vue`
  - 混合展示：已给代码步骤 + 留空输入框
  - 学生填写后点击"提交"→ 调用验证 API
  - 显示逐步反馈（对勾/叉/提示）
  - 全部正确后 → 进入 CODING

- [ ] **F3. 新增 MinimalHintCard.vue**
  - 文件：`frontend/.../cards/MinimalHintCard.vue`
  - 简洁卡片：方向性提示 + 相关 KC 标签
  - "直接开始写代码" 按钮 → 进入 CODING

- [ ] **F4. 修改 UnifiedAgentPanel.vue**
  - 在消息渲染逻辑中新增三种 card type 的分支
  - 根据 `scaffold_level` 展示对应 Card 组件

- [ ] **F5. 修改 workflowStateMachine.js**
  - SCAFFOLDING 阶段的 quickActions 根据 scaffold_level 动态调整
  - 处理 Faded Example 的填空提交事件

- [ ] **F6. 修改 ParsonsPanel.vue（可选）**
  - 在 Parsons 面板顶部显示当前脚手架级别指示器
  - 如：`当前模式：渐退练习 (mastery: 0.52)`

### 测试任务

- [ ] **T1. 单元测试：ScaffoldLevelResolver**
  - 测试边界值：0.0, 0.39, 0.4, 0.54, 0.55, 0.69, 0.7, 1.0
  - 测试多 KC 最小值聚合
  - 测试空 masteryByKc 的默认行为（应返回 FULL）

- [ ] **T2. 单元测试：FadedExampleGenerator fade_ratio 计算**
  - 验证 mastery=0.4 → fade_ratio=0.0
  - 验证 mastery=0.55 → fade_ratio=1.0（因为 0.55 是 FADED 上界）
  - 验证留空步骤选择逻辑

- [ ] **T3. 集成测试：SCAFFOLDING 阶段完整流程**
  - 模拟 mastery < 0.4 → 验证返回 WORKED_EXAMPLE card
  - 模拟 0.4 ≤ mastery < 0.55 → 验证返回 FADED_EXAMPLE card
  - 模拟 0.55 ≤ mastery < 0.7 → 验证返回 PARSONS_PROBLEM card
  - 模拟 mastery ≥ 0.7 → 验证返回 MINIMAL_HINT card

- [ ] **T4. 集成测试：Faded Example 填空验证**
  - 模拟学生填写正确答案 → mastery 更新
  - 模拟学生填写错误答案 → 反馈 + mastery 不升

- [ ] **T5. 前端合约测试：Card 渲染**
  - WorkedExampleCard 渲染 steps + bridge_to_current
  - FadedExampleCard 渲染输入框 + 提交按钮
  - MinimalHintCard 渲染 hint + KC 标签

- [ ] **T6. Schema 验证测试**
  - 三种新 CardType 的输出均通过 CardSchemaValidator
  - 缺失必填字段时 fail-fast

### 评估与灰度任务

- [ ] **E1. 离线评估指标设计**
  - 从 `ai_tutor_trace` 提取 scaffold_level + 后续 AC 率
  - 对比不同 scaffold_level 下的：
    - 首次 AC 率
    - 平均错误次数
    - 从 SCAFFOLDING → AC 的时间
    - 学生 feedback_label 分布

- [ ] **E2. ContextualBandit 灰度配置**
  - 在 `RolloutPolicyService` 中配置 scaffold fading 的灰度比例
  - 初始建议：90% green（规则决策）+ 10% gray（bandit 探索）

---

## LLM Prompt 设计要点

### Worked Example 生成 Prompt

```
你是 Python 编程教学助手。目标用户是没有编程基础的初学者。

任务：为以下题目生成一个"完整解题示例"，但不能使用当前题目本身，
而是选择一个结构相似但场景不同的类比题目。

当前题目：{problem_description}
涉及知识点：{kc_list}
学生当前 mastery：{mastery_snapshot}（非常薄弱，需要手把手指导）

要求：
1. 选择一个类比题目（相同知识点，不同场景）
2. 将解题过程分解为 3~6 个子目标步骤
3. 每个步骤包含：子目标标签、代码、通俗解释
4. 解释要用生活化的比喻，避免专业术语
5. 最后写一句话，引导学生将示例思路迁移到当前题目

严格返回 JSON，不要输出额外文本。
```

### Faded Example 生成 Prompt

```
你是 Python 编程教学助手。目标用户是有一定基础但尚未熟练的初学者。

任务：为以下题目生成一个"渐退式解题框架"，部分步骤给出代码，
部分步骤留空让学生填写。

当前题目：{problem_description}
参考解代码：{reference_solution}
涉及知识点及 mastery：{kc_mastery_map}
需要留空的步骤数：{faded_step_count}（共 {total_steps} 步）
优先留空的知识点：{high_mastery_kcs}（学生已较熟悉的部分）

要求：
1. 将解题过程分解为 3~6 个子目标步骤
2. 给出代码的步骤：包含子目标标签 + 代码 + 简短解释
3. 留空的步骤：包含子目标标签 + 提示语（不超过 15 字）
4. 留空的步骤应是学生 mastery 较高的知识点
5. 条件判断等核心逻辑步骤最后才留空

严格返回 JSON，不要输出额外文本。
```

---

## 注意事项

1. **不改变 Phase 枚举**：三种脚手架形式都在 SCAFFOLDING 阶段内部区分，不新增 Phase
2. **兼容现有 Parsons**：Parsons Problem 保持原有行为，作为 FADED 到 MINIMAL 之间的过渡形式
3. **答案安全**：Worked Example 必须使用类比题目，绝不能用当前题目的解答
4. **fail-fast**：如果 LLM 输出不符合 schema，直接失败并记录 trace，不 fallback
5. **mastery 冷启动**：如果某 KC 完全没有学习事件，mastery = p_init（通常 0.1），归入新手阶段

---
---

# TODO: 自适应题目推荐引擎 (Adaptive Problem Recommendation)

## 顶刊依据

- Piech et al. (2015) *"Deep Knowledge Tracing"*, NeurIPS
- Vie & Kashima (2019) *"Knowledge Tracing Machines"*, AAAI
- Doroudi et al. (2019) *"Where's the Reward? A Review of RL for Instructional Sequencing"*, IJAIED

---

## 核心思路

当前 ProblemList 页面只有手动筛选（难度/章节/标签/关键词），初学者面对题库不知道该练什么。
利用现有 `ai_knowledge_component` + `ai_problem_kc_mapping` + `MasteryService` 数据，
为每个学生计算候选题目的预测正确率，推荐处于"最近发展区"的题目。

---

## 现有数据基础

| 已有表/服务 | 用途 |
|------------|------|
| `ai_knowledge_component` | KC 定义，含 p_init / p_transit / p_slip / p_guess |
| `ai_problem_kc_mapping` | 题目-KC 映射，含 weight |
| `ai_learning_event` | 学习事件流，含 user_id / problem_id / is_correct |
| `MasteryService.projectMastery()` | 按题目计算 masteryByKc |
| `problem` 表 | 含 difficulty（Easy/Mid/Hard）、tags |
| `submission` 表 | 含 result（AC/WA/TLE...）、user_id、problem_id |

---

## 推荐算法

### 输入

```
userId: Long
currentMasteryByKc: Map<String, Double>  // 从 MasteryService 获取，跨所有题目聚合
```

### 跨题 mastery 聚合

> 现有 `MasteryService.projectMastery(userId, problemId)` 仅计算单题维度的 mastery。
> 推荐引擎需要**跨题目**的全局 KC mastery。

```sql
-- 新增方法 MasteryService.projectGlobalMastery(userId)
SELECT kc.id, kc.name, kc.p_init
FROM ai_knowledge_component kc
ORDER BY kc.id ASC
-- 对每个 KC，取该用户在所有关联题目上的学习事件（按 created_at 排序，限最近 50 条）
SELECT is_correct
FROM ai_learning_event
WHERE user_id = ? AND problem_id IN (
    SELECT problem_id FROM ai_problem_kc_mapping WHERE kc_id = ?
)
ORDER BY created_at ASC
LIMIT 50
-- 使用同样的指数平均公式：mastery = 0.7 * mastery + 0.3 * outcome
```

### 预测正确率计算

对候选题目 p，其关联 KC 集合为 {kc_1, kc_2, ...}：

```
predictedCorrectRate(p) = PRODUCT(masteryByKc[kc_i] for kc_i in p.kcs)
// 联合概率：所有 KC 都掌握才能解对
```

### 推荐策略

```
targetRange = [0.5, 0.8]  // 预测正确率的目标区间（最近发展区）
// 0.5：太低则过难，打击信心
// 0.8：太高则过易，无学习增益

candidateProblems = 所有题目.filter(p ->
    p.id 不在 user 的 AC 列表中
    AND predictedCorrectRate(p) >= 0.5
    AND predictedCorrectRate(p) <= 0.8
)
.sortBy(p -> |predictedCorrectRate(p) - 0.65|)  // 离 0.65 越近越优先
.limit(10)
```

### 冷启动处理

- 如果用户无任何学习事件（纯新用户）→ 所有 KC mastery = p_init（0.1）
- 此时 predictedCorrectRate 极低 → 推荐 Easy 难度、单 KC 题目
- 特殊规则：冷启动用户直接推荐 `difficulty = 'Easy'` 且 KC 数量 = 1 的题目，按 `problem.id ASC` 排序

---

## 实现任务清单

### 后端任务

- [ ] **R-B1. MasteryService 新增 projectGlobalMastery 方法**
  - 文件：`backend/.../profile/MasteryService.java`
  - 方法签名：`Map<String, Double> projectGlobalMastery(Long userId)`
  - 遍历所有 KC，对每个 KC 聚合该用户在所有关联题目上的学习事件
  - 使用现有指数平均公式 `mastery = 0.7 * mastery + 0.3 * outcome`
  - 每 KC 限最近 50 条事件
  - **验收标准**：
    - 入参 userId=null → 返回空 Map
    - 入参 userId 无学习事件 → 每个 KC 返回 p_init 值
    - 入参 userId 有 3 次 for循环 正确 → for循环 mastery = 0.7^3 * 0.1 + ... ≈ 0.691

- [ ] **R-B2. 新建 ProblemRecommendationService**
  - 新文件：`backend/.../aitutor/recommendation/ProblemRecommendationService.java`
  - 依赖：`MasteryService`, `JdbcTemplate`
  - 方法签名：`List<Map<String, Object>> recommend(Long userId, int limit)`
  - 流程：
    1. 调用 `projectGlobalMastery(userId)` 获取全局 mastery
    2. 查询用户已 AC 的 problem_id 集合
    3. 查询所有题目及其 KC 映射
    4. 计算每个候选题的 predictedCorrectRate
    5. 过滤 [0.5, 0.8] 区间，按离 0.65 的距离排序
    6. 冷启动 fallback：如果 mastery 全为 p_init → 直接按 Easy + 单 KC 推荐
  - 返回结构：`[{problem_id, title, difficulty, predicted_rate, target_kcs}]`
  - **验收标准**：
    - 已 AC 的题目不出现在推荐列表中
    - 预测正确率 < 0.5 或 > 0.8 的题目不出现
    - 冷启动用户收到 Easy 题目
    - 返回列表按 |predicted_rate - 0.65| 升序排列
    - limit=10 时最多返回 10 条

- [ ] **R-B3. 新增 API 端点**
  - 文件：`backend/.../controller/ProblemController.java`
  - 新增端点：`GET /api/problems/recommended`
  - 参数：`limit`（可选，默认 10）
  - 需要登录（从 Authentication 获取 userId）
  - 调用 `ProblemRecommendationService.recommend(userId, limit)`
  - **验收标准**：
    - 未登录 → 401
    - 已登录 → 返回 `{ status: "ok", data: { results: [...] } }`
    - 返回的 problem_id 均为有效的题目 ID

- [ ] **R-B4. 数据库索引优化**
  - 新增迁移脚本 `V19__problem_recommendation_indexes.sql`
  - 新增索引：
    ```sql
    CREATE INDEX IF NOT EXISTS idx_ai_learning_event_user_problem_correct
        ON ai_learning_event(user_id, problem_id, is_correct, created_at ASC);
    ```
  - **验收标准**：
    - `EXPLAIN ANALYZE` 验证推荐查询使用索引扫描而非全表扫描

### 前端任务

- [ ] **R-F1. ProblemList.vue 新增"为你推荐"区域**
  - 文件：`frontend/.../oj/views/problem/ProblemList.vue`
  - 在题目列表顶部新增"为你推荐"折叠面板
  - 登录状态下自动调用 `GET /api/problems/recommended`
  - 展示推荐题目卡片：题目名 + 难度标签 + 目标 KC 标签 + 预测掌握度条
  - 未登录时不展示此区域
  - **验收标准**：
    - 已登录用户看到推荐区域
    - 未登录用户看不到推荐区域
    - 点击题目卡片跳转到 `/problem/:problemID`
    - 加载中显示 skeleton 占位
    - 推荐为空时显示"继续做题，推荐会更精准"

- [ ] **R-F2. Home.vue 新增"今日推荐"模块**
  - 文件：`frontend/.../oj/views/Home.vue`
  - 在首页新增"今日推荐练习"卡片区域
  - 调用同一 API，展示 Top 3 推荐题目
  - **验收标准**：
    - 展示最多 3 道推荐题
    - 每道题显示题目名、难度、目标 KC
    - "查看更多" 跳转到 ProblemList 的推荐区域

### 测试任务

- [ ] **R-T1. 单元测试：ProblemRecommendationService**
  - 测试冷启动用户 → 返回 Easy 单 KC 题目
  - 测试已有 mastery 用户 → 返回 [0.5, 0.8] 区间题目
  - 测试已 AC 题目不重复推荐
  - 测试空题库 → 返回空列表
  - 测试 predictedCorrectRate 计算精度（多 KC 联合概率）

- [ ] **R-T2. 单元测试：MasteryService.projectGlobalMastery**
  - 测试无学习事件 → 返回 p_init
  - 测试多题目跨 KC 聚合 → mastery 正确累积
  - 测试事件窗口限制（超过 50 条只取最近 50 条）

- [ ] **R-T3. 集成测试：推荐 API 端点**
  - 调用 `GET /api/problems/recommended` → 验证返回结构
  - 验证推荐列表不含已 AC 题目
  - 验证预测正确率字段在 [0.5, 0.8] 区间内

---

## 注意事项

1. **不引入外部推荐框架**：使用纯 SQL + Java 实现，不引入 TensorFlow/PyTorch 等深度学习框架
2. **fail-fast**：如果 KC 映射缺失（某题目无 KC 关联），该题目直接排除不推荐
3. **性能约束**：推荐计算需在 200ms 内完成，必要时缓存 globalMastery（Redis，TTL 5 分钟）
4. **不做个性化排序之外的额外功能**：不做推荐理由展示、不做 A/B 实验框架

---
---

# TODO: 多维代码评估 (Multi-dimensional Code Assessment)

## 顶刊依据

- Keuning et al. (2018) *"A Systematic Literature Review of Automated Feedback in Programming Education"*, ACM Computing Surveys
- Ala-Mutka (2005) *"A Survey of Automated Assessment Approaches for Programming Assignments"*, Computer Science Education
- Hellas et al. (2018) *"Predicting Academic Performance: A Systematic Literature Review"*, ACM ITiCSE

---

## 核心思路

当前提交结果只有 AC/WA/TLE 等二元判定。
在 AC 提交上新增**代码质量评分**（可读性 + 效率 + 风格），展示在 SubmissionDetails 页面。
对 WA 提交给出**通过测试点比例**作为部分分指标。

---

## 现有数据基础

| 已有表/服务 | 用途 |
|------------|------|
| `submission` 表 | 含 result、code、language、statistic_info（JSON，含 time_cost/memory_cost） |
| `problem` 表 | 含 test_case_id、test_case_score |
| SubmissionDetails.vue | 已展示判题结果、用时、内存 |
| `LlmClient` | 可调用 LLM 进行代码质量分析 |

---

## 评估维度定义

### 1. 部分分 (Partial Score) — 适用于 WA/RE/TLE 提交

```
partialScore = passedTestCaseCount / totalTestCaseCount * 100
// 已有数据：submission.statistic_info 中的 test case 结果
```

**数据来源**：Judge Server 返回的每个测试点结果已经存储在 `submission.statistic_info` 的 JSON 中。
不需要额外 LLM 调用，纯计算。

### 2. 代码质量评分 (Code Quality Score) — 仅适用于 AC 提交

由 LLM 评估，返回三个子维度，每个 1~5 分：

| 子维度 | 评估内容 | 评分锚点 |
|--------|---------|---------|
| **可读性 (readability)** | 变量命名清晰度、代码结构分层 | 1=单字母变量+无缩进；5=语义化命名+清晰分层 |
| **效率 (efficiency)** | 时间/空间复杂度合理性 | 1=暴力嵌套循环；5=最优复杂度 |
| **风格 (style)** | PEP 8 合规、空行/空格规范 | 1=完全不规范；5=完全合规 |

**综合分**：`overallScore = (readability + efficiency + style) / 3`，保留 1 位小数。

### LLM 输出结构

```json
{
  "readability": 3,
  "readability_comment": "变量 a、b 含义不清，建议改为 count、total",
  "efficiency": 4,
  "efficiency_comment": "O(n) 复杂度合理",
  "style": 2,
  "style_comment": "缺少空行分隔，if 后缺空格",
  "overall": 3.0
}
```

---

## 实现任务清单

### 后端任务

- [ ] **A-B1. 新建 CodeQualityAssessmentService**
  - 新文件：`backend/.../aitutor/assessment/CodeQualityAssessmentService.java`
  - 依赖：`LlmClient`, `JdbcTemplate`
  - 方法签名：`Map<String, Object> assess(String code, String language, String problemDescription)`
  - 调用 LLM，prompt 要求返回 readability / efficiency / style 三个 1~5 整数 + comment
  - 对 LLM 返回值做范围校验：score 不在 [1, 5] → fail-fast
  - **验收标准**：
    - 返回的三个 score 均为 [1, 5] 整数
    - 返回的 overall 为三者均值，保留 1 位小数
    - comment 均为非空字符串
    - LLM 返回非法 JSON → 抛出异常

- [ ] **A-B2. 提交时自动触发评估**
  - 文件：`backend/.../submission/SubmissionCommandDomainService.java`（或 judge 回调处理处）
  - 在判题结果回调中：
    - 如果 result == AC → 异步调用 `CodeQualityAssessmentService.assess()`
    - 将评估结果写入 `submission` 表的 `statistic_info` JSON 中新增 `code_quality` key
  - 如果 result != AC → 从判题结果中计算 partialScore，写入 `statistic_info.partial_score`
  - **验收标准**：
    - AC 提交的 `statistic_info` 包含 `code_quality` 对象
    - WA 提交的 `statistic_info` 包含 `partial_score` 数值
    - 评估失败不影响提交结果的正常返回（异步，不阻塞）

- [ ] **A-B3. 新增评估结果查询**
  - 文件：`backend/.../submission/SubmissionQueryDomainService.java`
  - 在 `getSubmission()` 返回中确保 `statistic_info` 包含 `code_quality` / `partial_score`
  - 无需新增端点，复用现有 `GET /api/submission?id=xxx`
  - **验收标准**：
    - 返回结构中 `statistic_info.code_quality` 存在（仅 AC 提交）
    - 返回结构中 `statistic_info.partial_score` 存在（仅非 AC 提交）

### 前端任务

- [ ] **A-F1. SubmissionDetails.vue 新增代码质量展示**
  - 文件：`frontend/.../oj/views/submission/SubmissionDetails.vue`
  - AC 提交时：在判题结果下方展示三维雷达图（readability / efficiency / style）
  - 使用 iView 或内联 SVG 实现简单雷达图（三角形 + 三轴）
  - 每个维度显示分数 + 简短建议（来自 comment）
  - **验收标准**：
    - AC 提交展示雷达图 + 三个维度分数 + overall 分数
    - WA/TLE 等提交不展示雷达图
    - 评估结果尚未返回时（异步延迟）显示"评估中..."

- [ ] **A-F2. SubmissionDetails.vue 新增部分分展示**
  - WA/RE/TLE 提交时：在判题结果下方展示进度条
  - 格式：`通过 X / Y 个测试点 (Z%)`
  - 进度条颜色：0-30% 红色 → 30-70% 橙色 → 70-99% 绿色
  - **验收标准**：
    - 非 AC 提交展示部分分进度条
    - AC 提交不展示部分分
    - partial_score 显示为百分比整数

### 测试任务

- [ ] **A-T1. 单元测试：CodeQualityAssessmentService**
  - 模拟 LLM 返回合法 JSON → 验证 score 在 [1, 5]
  - 模拟 LLM 返回 score=0 → fail-fast 抛异常
  - 模拟 LLM 返回非法 JSON → fail-fast 抛异常

- [ ] **A-T2. 集成测试：提交后评估流程**
  - 提交 AC 代码 → 验证 `statistic_info.code_quality` 存在
  - 提交 WA 代码 → 验证 `statistic_info.partial_score` 存在
  - 评估失败 → 提交结果正常返回，`code_quality` 为 null

- [ ] **A-T3. 前端合约测试：评估展示**
  - 传入含 code_quality 的 statistic_info → 雷达图渲染
  - 传入不含 code_quality → 不渲染雷达图
  - 传入含 partial_score → 进度条渲染

---

## LLM Prompt

```
你是 Python 代码质量评估助手。请严格评估以下 AC 代码的质量。

题目描述：{problem_description}
学生代码：
```python
{code}
```

请从三个维度评分（每项 1~5 整数）：
1. readability（可读性）：变量命名是否清晰，代码结构是否分层合理
2. efficiency（效率）：时间/空间复杂度是否合理，有无冗余计算
3. style（风格）：是否符合 PEP 8 规范，空行/缩进/空格是否规范

每个维度附带一句简短建议（不超过 30 字）。
严格返回 JSON，不要输出额外文本。
```

---

## 注意事项

1. **仅对 AC 提交做质量评估**：WA 提交只算部分分，不调用 LLM
2. **异步不阻塞**：质量评估在 judge 回调之后异步执行，不影响提交响应时间
3. **fail-fast**：LLM 返回不合法 → 直接记录失败日志，不写入 statistic_info
4. **不做历史回填**：只对新提交做评估，不回填已有的 AC 提交

---
---

# TODO: 课堂级预警与干预推荐 (Classroom Early Warning & Intervention)

## 顶刊依据

- Marbouti et al. (2016) *"Models for Early Prediction of At-Risk Students..."*, Computers & Education (SSCI Q1)
- Jayaprakash et al. (2014) *"Early Alert of Academically At-Risk Students..."*, Journal of Learning Analytics
- Ahadi et al. (2015) *"Exploring Machine Learning Methods to Detect At-Risk Students"*, ACM ICER

---

## 核心思路

ClassroomMonitorController 已有 `monitorStats`、`monitorSnapshots`、`monitorInterventionCandidates` 端点。
当前干预候选人的识别依赖静态快照，缺乏**预测性**。
新增基于行为信号的风险评分模型，将"即将出问题的学生"提前标记出来。

---

## 现有数据基础

| 已有表/端点 | 用途 |
|------------|------|
| `student_monitoring_snapshot` | 学生实时监控快照 |
| `ai_learning_event` | 学习事件流（含 is_correct） |
| `submission` | 提交记录（含 result, created_at） |
| `ai_learner_profile_snapshot` | 学习者画像快照（含 frustration_level, mastery_by_kc） |
| `GET .../monitor/intervention-candidates` | 现有干预候选人端点 |
| `GET .../monitor/stats` | 课堂统计端点 |
| `GET .../monitor/error-clusters` | 错误聚类端点 |

---

## 风险评分模型

### 输入特征（每个学生）

从最近 7 天的数据中提取：

| 特征 | 计算方式 | 权重 | 依据 |
|------|---------|------|------|
| `submissionDecline` | 最近 3 天提交数 / 前 4 天提交数（< 1 表示下降） | 0.25 | Marbouti: 提交频率下降是最强预测因子 |
| `consecutiveFailRate` | 最近 10 次提交中连续失败的最长段 / 10 | 0.25 | Ahadi: 连续失败段长度高度相关 |
| `avgMasteryGap` | (0.6 - avgMastery) / 0.6，截断到 [0, 1] | 0.20 | 弱 KC 阈值 0.6，差距越大风险越高 |
| `inactivityDays` | 距最后一次提交的天数 / 7，截断到 [0, 1] | 0.20 | Jayaprakash: 不活跃天数是 dropout 信号 |
| `frustrationSignal` | severe=1.0, high=0.7, medium=0.3, low=0.0 | 0.10 | D'Mello: 持续挫败导致放弃 |

### 风险评分计算

```
riskScore = 0.25 * submissionDecline
          + 0.25 * consecutiveFailRate
          + 0.20 * avgMasteryGap
          + 0.20 * inactivityDays
          + 0.10 * frustrationSignal
// 值域 [0, 1]
```

### 风险等级

| riskScore | 等级 | 含义 |
|-----------|------|------|
| < 0.3 | `normal` | 正常学习状态 |
| 0.3 ~ 0.6 | `warning` | 需关注，可能即将遇到困难 |
| > 0.6 | `critical` | 高风险，建议立即干预 |

### 干预建议生成

不使用 LLM，基于规则生成：

| 最高权重特征 | 干预建议 |
|-------------|---------|
| submissionDecline 最高 | "该学生近 3 天提交量下降 {X}%，建议确认是否遇到困难" |
| consecutiveFailRate 最高 | "该学生最近连续 {N} 次提交失败，建议安排一对一辅导" |
| avgMasteryGap 最高 | "该学生在 {KC} 知识点掌握薄弱（mastery={M}），建议布置专项练习" |
| inactivityDays 最高 | "该学生已 {D} 天未提交，建议主动联系了解情况" |
| frustrationSignal 最高 | "该学生挫败指数较高，建议给予鼓励性反馈" |

---

## 实现任务清单

### 后端任务

- [ ] **W-B1. 新建 StudentRiskAssessor**
  - 新文件：`backend/.../classroom/monitor/StudentRiskAssessor.java`
  - 方法签名：`RiskAssessment assess(Long userId, Long classroomId)`
  - 返回 record：`RiskAssessment(double riskScore, String riskLevel, String primaryFactor, String suggestion)`
  - 内部从 `submission`、`ai_learning_event`、`ai_learner_profile_snapshot` 查询最近 7 天数据
  - 按上述公式计算 riskScore
  - **验收标准**：
    - riskScore 值域 [0.0, 1.0]
    - riskLevel 严格为 normal / warning / critical 之一
    - primaryFactor 为五个特征中权重贡献最大的那个
    - suggestion 非空字符串，包含具体数据（下降百分比/连续失败次数/天数等）
    - 无提交历史的学生 → inactivityDays = 1.0, riskLevel = critical

- [ ] **W-B2. 增强 ClassroomMonitorDomainService**
  - 文件：`backend/.../classroom/ClassroomMonitorDomainService.java`（或其 impl）
  - 修改 `monitorInterventionCandidates()` 方法：
    1. 对课堂所有学生调用 `StudentRiskAssessor.assess()`
    2. 按 riskScore 降序排列
    3. 返回结构中新增 `risk_score`, `risk_level`, `primary_factor`, `suggestion` 字段
  - **验收标准**：
    - 返回列表按 riskScore 降序
    - 每个条目包含完整的 risk 字段
    - normal 等级的学生仍然返回（教师可选择查看全部）

- [ ] **W-B3. 新增预警统计端点**
  - 文件：`backend/.../classroom/ClassroomMonitorController.java`
  - 新增端点：`GET /api/classroom/{classroomId}/monitor/risk-summary`
  - 返回结构：
    ```json
    {
      "total_students": 30,
      "normal_count": 20,
      "warning_count": 7,
      "critical_count": 3,
      "top_risk_factors": ["submissionDecline", "consecutiveFailRate"]
    }
  - **验收标准**：
    - 三个 count 之和 = total_students
    - top_risk_factors 为所有 critical 学生中出现频率最高的 primaryFactor，最多 3 个

### 前端任务

- [ ] **W-F1. 教师监控面板新增"风险预警"Tab**
  - 文件：根据现有课堂监控前端页面位置（ClassroomDetail 或独立监控页）
  - 顶部展示风险摘要卡片：normal / warning / critical 三个计数
  - 下方展示预警学生列表：
    - 每行：学生姓名 + 风险等级标签（绿/黄/红） + riskScore 进度条 + 干预建议
    - 按 riskScore 降序排列
    - 支持按风险等级筛选
  - **验收标准**：
    - 默认只显示 warning + critical 学生
    - "显示全部" 切换后显示所有学生
    - 风险等级标签颜色：normal=绿、warning=橙、critical=红
    - 干预建议完整显示在每行右侧

### 测试任务

- [ ] **W-T1. 单元测试：StudentRiskAssessor**
  - 输入：连续 5 天无提交 → inactivityDays = 5/7 ≈ 0.714 → riskLevel = critical
  - 输入：提交频率稳定 + mastery 全 > 0.6 + 无挫败 → riskScore < 0.3 → normal
  - 输入：最近 10 次提交全部失败 → consecutiveFailRate = 1.0 → critical
  - 边界：提交记录刚好 7 天 → submissionDecline 计算分母不为 0

- [ ] **W-T2. 集成测试：预警端点**
  - 调用 `GET .../monitor/risk-summary` → 验证三个 count 之和 = total
  - 调用 `GET .../monitor/intervention-candidates` → 验证每条包含 risk_score 字段

---

## 注意事项

1. **不使用 LLM**：风险评分纯规则计算，干预建议模板生成，不调用 LLM
2. **不引入 ML 框架**：使用加权线性模型，不引入 sklearn/TensorFlow
3. **权限控制**：仅课堂 instructor 和 TA 角色可访问预警端点
4. **fail-fast**：如果某学生的数据查询失败，该学生 riskScore 标记为 -1（数据异常），不跳过

---
---

# TODO: 跨学生错误模式挖掘 (Cross-Learner Misconception Mining)

## 顶刊依据

- Rivers & Koedinger (2017) *"Data-Driven Hint Generation in Vast Solution Spaces"*, IJAIED
- Glassman et al. (2015) *"OverCode: Visualizing Variation in Student Solutions..."*, ACM TOCHI
- Brown & Altadmri (2017) *"Novice Java Programming Mistakes: Large-Scale Data vs. Educator Beliefs"*, ACM TOCE

---

## 核心思路

现有 ErrorDiagnosis 是**单学生**视角。但同一题目上，大量学生犯相同错误是常见现象。
通过对 WA 提交做错误模式聚类，识别 Top-N 典型错误，服务于两个场景：
1. **学生端**：ErrorDiagnosis 卡片中补充"XX% 的同学也犯了类似错误"（社会比较，降低挫败）
2. **教师端**：MisconceptionManagement 页面展示热力图（哪道题哪个 KC 的共性错误最多）

---

## 现有数据基础

| 已有表 | 用途 |
|--------|------|
| `ai_misconception` | 已有 misconception 定义表（id, kc_id, name, description, evidence_count） |
| `ai_misconception_kc_mapping`（若存在）或 `ai_misconception.kc_id` | KC 关联 |
| `ai_learner_notebook` | 学生错题记录（error_category, root_cause） |
| `submission` | 所有提交记录（code, result, problem_id） |
| `ai_learning_event` | 学习事件（event_type, extra_data 含 detector_name） |
| 前端 `MisconceptionManagement.vue` | 已有教师端 misconception 管理页面 |

---

## 错误模式聚类方案

### 聚类粒度

不做 AST 级聚类（复杂度过高），改为基于 `error_category + root_cause` 的**文本相似度聚类**。

### 流程

```
1. 按 problem_id 聚合所有 WA 提交的错题记录
   FROM ai_learner_notebook
   WHERE problem_id = ? AND is_deleted = false

2. 提取每条记录的 (error_category, root_cause) 作为聚类特征

3. 对 root_cause 做 LLM 批量归类：
   将同一题目的 N 条 root_cause 发给 LLM，要求归为 K 个模式

4. LLM 返回：
   [{
     "pattern_id": "P1",
     "pattern_name": "遗漏边界条件：空列表",
     "root_causes": ["没有处理空列表", "空列表时报错", "忘了判断列表为空"],
     "count": 12,
     "percentage": 40.0
   }]

5. 将聚类结果写入 ai_misconception 表
```

### LLM 归类 Prompt

```
你是编程教育数据分析助手。以下是同一道题目上学生提交的错误原因列表。
请将相似的错误归类为 2~5 个模式。

题目：{problem_title}
错误原因列表：
{root_cause_list}

要求：
1. 每个模式给出简短的名称（不超过 20 字）
2. 列出该模式包含的原始错误原因
3. 统计每个模式的出现次数和占比

严格返回 JSON 数组，不要输出额外文本。
```

### 触发时机

不实时计算，采用**批量离线任务**：
- 当某题目的 WA 提交数达到 10 条新增时，触发一次聚类
- 或由教师在 MisconceptionManagement 页面手动触发

---

## 实现任务清单

### 后端任务

- [ ] **M-B1. 新建 MisconceptionMiningService**
  - 新文件：`backend/.../aitutor/misconception/MisconceptionMiningService.java`
  - 依赖：`LlmClient`, `JdbcTemplate`
  - 方法签名：`List<Map<String, Object>> minePatterns(Long problemId)`
  - 流程：
    1. 查询 `ai_learner_notebook` 中该题目所有非删除记录的 root_cause
    2. 如果记录数 < 5 → 返回空列表（样本不足）
    3. 调用 LLM 归类
    4. 校验 LLM 返回的 pattern 数在 [1, 5] 范围，每个 pattern 的 count > 0
    5. 将结果 upsert 到 `ai_misconception` 表：
       - `id` = `problem_{problemId}_pattern_{patternIndex}`
       - `kc_id` = 该题目关联的 KC（取 weight 最大的）
       - `name` = pattern_name
       - `description` = 包含的 root_cause 列表
       - `evidence_count` = count
       - `source` = 'mining'
       - `status` = 'pending'（待教师审核）
  - **验收标准**：
    - 记录数 < 5 → 返回空列表
    - LLM 返回 pattern_name 为空 → fail-fast
    - upsert 后 `ai_misconception` 表中对应记录存在
    - 重复挖掘同一题目 → 更新已有记录而非新增重复

- [ ] **M-B2. 新增挖掘触发端点**
  - 文件：`backend/.../controller/AdminAITutorController.java`
  - 新增端点：`POST /api/admin/ai/misconception/mine`
  - 请求体：`{ "problem_id": 123 }`
  - 调用 `MisconceptionMiningService.minePatterns(problemId)`
  - **验收标准**：
    - 非 admin 用户 → 403
    - problem_id 不存在 → 404
    - 挖掘完成 → 返回 pattern 列表

- [ ] **M-B3. 新增学生端共性错误查询**
  - 文件：`backend/.../aitutor/misconception/MisconceptionMiningService.java`
  - 新增方法：`Map<String, Object> getCommonPatterns(Long problemId, String currentRootCause)`
  - 流程：
    1. 查询该题目已挖掘的 misconception patterns（status = 'approved'）
    2. 找到与 currentRootCause 最匹配的 pattern（字符串包含匹配）
    3. 返回 `{ "pattern_name": "...", "percentage": 40.0, "message": "40% 的同学也遇到了类似问题" }`
  - **验收标准**：
    - 无匹配 pattern → 返回 null
    - 匹配到 → percentage > 0 且 message 非空
    - 只返回 status = 'approved' 的 pattern

- [ ] **M-B4. 增强 ErrorDiagnosis 卡片输出**
  - 文件：`backend/.../impl/AITutorWorkflowAdminServiceImpl.java`
  - 在 ERROR_FEEDBACK 阶段生成 error_diagnosis 卡片时：
    1. 调用 `getCommonPatterns(problemId, rootCause)`
    2. 如果有匹配，在卡片输出中新增 `common_pattern` 字段
  - **验收标准**：
    - error_diagnosis 卡片中出现 `common_pattern` 字段（当有匹配时）
    - 无匹配时 `common_pattern` 字段不存在
    - `CardSchemaValidator` 中为 error_diagnosis schema 新增 `common_pattern` 为可选字段

### 前端任务

- [ ] **M-F1. ErrorDiagnosisCard.vue 展示共性错误提示**
  - 文件：`frontend/.../cards/ErrorDiagnosisCard.vue`
  - 当 `data.common_pattern` 存在时，在卡片底部展示：
    - 图标 + "{percentage}% 的同学也遇到了类似问题：{pattern_name}"
    - 样式：浅蓝色背景，信息性提示（非警告）
  - **验收标准**：
    - common_pattern 存在 → 提示展示
    - common_pattern 不存在 → 不展示（不留空白）
    - percentage 显示为整数百分比

- [ ] **M-F2. MisconceptionManagement.vue 增强**
  - 文件：`frontend/.../admin/views/general/MisconceptionManagement.vue`
  - 新增"批量挖掘"按钮：输入 problem_id → 调用 `POST .../misconception/mine`
  - 展示挖掘结果列表：pattern_name + count + percentage + 审核状态
  - 审核操作：approve / reject
  - **验收标准**：
    - 点击"批量挖掘" → 调用 API → 刷新列表
    - 列表按 evidence_count 降序
    - approve 后 status 变为 approved
    - reject 后 status 变为 rejected

### 测试任务

- [ ] **M-T1. 单元测试：MisconceptionMiningService**
  - 记录数 < 5 → 返回空列表
  - 模拟 LLM 返回 3 个 pattern → 验证 upsert 成功
  - 重复挖掘同一题目 → 验证更新而非重复

- [ ] **M-T2. 单元测试：getCommonPatterns**
  - 匹配到 approved pattern → 返回包含 percentage 的结果
  - 无 approved pattern → 返回 null
  - 匹配到 pending pattern → 返回 null（只展示已审核的）

- [ ] **M-T3. 集成测试：ErrorDiagnosis 卡片包含 common_pattern**
  - 模拟题目有已审核的 misconception pattern → error_diagnosis 包含 common_pattern
  - 模拟题目无 pattern → error_diagnosis 不含 common_pattern

---

## 注意事项

1. **教师审核前不展示给学生**：挖掘结果 status 默认 pending，只有 approved 才展示给学生
2. **样本量要求**：WA 记录 < 5 不触发挖掘，避免小样本下的误导性统计
3. **不做实时挖掘**：离线批量任务或教师手动触发，不在学生提交流程中同步执行
4. **fail-fast**：LLM 归类失败 → 不写入 ai_misconception，不 fallback

---
---

# TODO: 学习分析可视化仪表板 (Learning Analytics Dashboard)

## 顶刊依据

- Bodily & Verbert (2017) *"Review of Research on Student-Facing Learning Analytics Dashboards..."*, IEEE TLT
- Jivet et al. (2018) *"License to Evaluate: Towards Learning Analytics Dashboard Design..."*, LAK
- Matcha et al. (2019) *"A Systematic Review of Empirical Studies on Learning Analytics Dashboards"*, Education and Information Technologies

---

## 核心思路

当前 UserHome.vue 只有 AC 率环形图和简单统计。学生看不到自己的知识掌握全貌。
基于开放学习者模型（Open Learner Model）理论，将内部 mastery 数据可视化呈现给学生，
促进元认知和自我调节学习。

---

## 现有数据基础

| 已有表 | 可视化用途 |
|--------|-----------|
| `ai_learner_profile_snapshot` | 历史 mastery_by_kc 快照序列 → 掌握度变化趋势 |
| `ai_knowledge_component` | KC 定义（name, chapter） → 知识地图结构 |
| `ai_learner_notebook` | 错题记录（error_category, root_cause） → 错误类型分布 |
| `submission` | 提交记录 → 活跃度时间线 |
| `ai_learning_event` | 学习事件 → 每日学习量统计 |

---

## 仪表板模块定义

### 模块 1：KC 掌握度热力图

```
横轴：KC 名称（按 chapter 分组）
纵轴：无（单行热力图）
颜色：mastery 值映射到红→黄→绿
 < 0.3 → 红色（薄弱）
 0.3 ~ 0.6 → 橙色（发展中）
 0.6 ~ 0.8 → 浅绿（基本掌握）
 ≥ 0.8 → 深绿（熟练）
```

数据来源：`MasteryService.projectGlobalMastery(userId)` — 复用推荐引擎中新增的方法

### 模块 2：Mastery 变化趋势

```
横轴：时间（日期）
纵轴：mastery 值 [0, 1]
折线：每个弱 KC 一条折线
数据点：从 ai_learner_profile_snapshot 按时间排序取 mastery_by_kc
```

数据来源查询：
```sql
SELECT created_at::date AS date, mastery_by_kc
FROM ai_learner_profile_snapshot
WHERE user_id = ?
ORDER BY created_at ASC
LIMIT 100
```

### 模块 3：错误类型分布

```
图表类型：饼图
数据：按 error_category 聚合 ai_learner_notebook 中的记录数
切片：各 error_category + 占比百分比
```

数据来源查询：
```sql
SELECT error_category, COUNT(*) AS count
FROM ai_learner_notebook
WHERE user_id = ? AND is_deleted = false
GROUP BY error_category
ORDER BY count DESC
LIMIT 6
```

### 模块 4：学习活跃度日历

```
图表类型：GitHub 风格的贡献热力图
数据：每日提交次数
绿色深浅：0 次→灰色，1~2→浅绿，3~5→中绿，>5→深绿
时间范围：最近 90 天
```

数据来源查询：
```sql
SELECT created_at::date AS date, COUNT(*) AS count
FROM submission
WHERE user_id = ?
  AND created_at > NOW() - INTERVAL '90 days'
GROUP BY created_at::date
ORDER BY date ASC
```

---

## 实现任务清单

### 后端任务

- [ ] **D-B1. 新建 LearnerAnalyticsService**
  - 新文件：`backend/.../aitutor/analytics/LearnerAnalyticsService.java`
  - 依赖：`MasteryService`, `JdbcTemplate`
  - 方法：
    - `Map<String, Double> getKcMasteryMap(Long userId)` → 全局 mastery 热力图数据
    - `List<Map<String, Object>> getMasteryTrend(Long userId, int days)` → 趋势数据
    - `List<Map<String, Object>> getErrorDistribution(Long userId)` → 错误分布
    - `List<Map<String, Object>> getActivityCalendar(Long userId, int days)` → 活跃度日历
  - **验收标准**：
    - getKcMasteryMap：返回所有 KC 的 mastery 值，key 为 KC name
    - getMasteryTrend：按日期升序，每个日期含 masteryByKc 快照
    - getErrorDistribution：按 count 降序，最多 6 个分类
    - getActivityCalendar：每日一条，含 date + count

- [ ] **D-B2. 新增 API 端点**
  - 文件：`backend/.../controller/AccountController.java`（或新建 LearnerAnalyticsController）
  - 端点：`GET /api/user/analytics`
  - 参数：`days`（可选，默认 90）
  - 返回结构：
    ```json
    {
      "kc_mastery": {"for循环": 0.72, "条件判断": 0.45, ...},
      "mastery_trend": [{"date": "2026-03-25", "mastery_by_kc": {...}}, ...],
      "error_distribution": [{"category": "逻辑错误", "count": 12}, ...],
      "activity_calendar": [{"date": "2026-03-25", "count": 3}, ...]
    }
    ```
  - **验收标准**：
    - 未登录 → 401
    - 已登录但无数据 → 返回空结构（空 Map/空 List），不报错
    - kc_mastery 的 value 在 [0, 1] 范围
    - activity_calendar 的 date 在最近 `days` 天内

### 前端任务

- [ ] **D-F1. UserHome.vue 重构为仪表板布局**
  - 文件：`frontend/.../oj/views/user/UserHome.vue`
  - 保留现有 AC 率环形图
  - 新增四个模块卡片（2x2 网格布局）：
    1. KC 掌握度热力图
    2. Mastery 变化趋势折线图
    3. 错误类型分布饼图
    4. 学习活跃度日历
  - 页面加载时调用 `GET /api/user/analytics`
  - **验收标准**：
    - 四个模块均渲染（即使数据为空，显示"暂无数据"占位）
    - 热力图颜色与 mastery 区间对应
    - 折线图横轴为日期，纵轴为 [0, 1]
    - 饼图切片数 ≤ 6
    - 日历展示最近 90 天

- [ ] **D-F2. KC 掌握度热力图组件**
  - 新文件：`frontend/.../components/KcMasteryHeatmap.vue`
  - Props：`kcMastery: Record<string, number>`
  - 使用 CSS Grid + 内联颜色实现（不引入 ECharts）
  - 每个 KC 一个色块，hover 显示 KC 名 + mastery 值
  - 按 chapter 分组显示
  - **验收标准**：
    - 颜色映射：< 0.3 红 / 0.3~0.6 橙 / 0.6~0.8 浅绿 / ≥ 0.8 深绿
    - hover tooltip 显示 KC 名称和精确 mastery
    - 空数据 → 显示"还没有知识点数据"

- [ ] **D-F3. Mastery 趋势折线图组件**
  - 新文件：`frontend/.../components/MasteryTrendChart.vue`
  - Props：`trendData: Array<{date, mastery_by_kc}>`
  - 使用 Canvas 2D 或 SVG 实现简单折线图（不引入 ECharts）
  - 每个弱 KC 一条折线，颜色区分
  - **验收标准**：
    - 至少展示 mastery < 0.6 的 KC 折线
    - 横轴日期标签不重叠（自动间隔）
    - 数据点 < 2 → 显示"数据积累中"

- [ ] **D-F4. 错误类型饼图组件**
  - 新文件：`frontend/.../components/ErrorDistributionPie.vue`
  - Props：`distribution: Array<{category, count}>`
  - 使用 SVG 弧形实现饼图
  - 每个切片显示分类名 + 百分比
  - **验收标准**：
    - 切片数 ≤ 6，超过部分合并为"其他"
    - 百分比之和 = 100%
    - 空数据 → 显示"还没有错题记录"

- [ ] **D-F5. 活跃度日历组件**
  - 新文件：`frontend/.../components/ActivityCalendar.vue`
  - Props：`calendarData: Array<{date, count}>`, `days: number`
  - GitHub 风格网格：每列一周，每行一天（周一~周日）
  - 颜色：0=灰、1~2=浅绿、3~5=中绿、>5=深绿
  - **验收标准**：
    - 展示最近 `days` 天（默认 90）
    - hover 显示日期 + 提交次数
    - 无提交的日期为灰色方块

### 测试任务

- [ ] **D-T1. 单元测试：LearnerAnalyticsService**
  - getKcMasteryMap：无学习事件 → 返回 p_init 值
  - getMasteryTrend：无快照 → 返回空列表
  - getErrorDistribution：无错题 → 返回空列表
  - getActivityCalendar：无提交 → 返回空列表

- [ ] **D-T2. 集成测试：Analytics API 端点**
  - 调用 `GET /api/user/analytics` → 验证返回结构完整
  - 验证 kc_mastery 键与 ai_knowledge_component 表一致
  - 验证 activity_calendar 日期不超出 `days` 范围

- [ ] **D-T3. 前端合约测试：组件渲染**
  - KcMasteryHeatmap：传入数据 → 验证色块数量 = KC 数量
  - MasteryTrendChart：传入 2+ 数据点 → 折线渲染
  - ErrorDistributionPie：传入数据 → 切片渲染
  - ActivityCalendar：传入数据 → 网格渲染

---

## 注意事项

1. **不引入 ECharts 或 D3**：使用纯 CSS Grid + SVG + Canvas 2D 实现，避免增加打包体积
2. **不缓存前端数据**：每次进入 UserHome 实时查询，数据量小（单用户维度）
3. **隐私**：仅展示当前登录用户自己的数据，不做跨学生对比（避免排名压力）
4. **fail-fast**：后端任何一个模块查询失败 → 该模块返回空，不影响其他模块
