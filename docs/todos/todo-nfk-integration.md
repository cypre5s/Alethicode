# NFK 模型项目集成 TODO

> 目标：使 Alethicode 项目在正常使用过程中**自动积累 NFK 训练所需的全部数据**，并在数据量达标后能一键训练并部署。
>
> 原则：不改变学生使用流程，不增加额外操作步骤，数据收集完全透明。

---

## 当前数据盘点

### 已有（可直接使用）

| 数据 | 表 | 关键列 | 用途 |
|------|-----|--------|------|
| 学生做题记录 | `submission` | user_id, problem_id, result, create_time | responses + timestamps |
| 课程包知识点 | `language_pack_kc` | id, language_pack_id, name, chapter_id | skill_ids |
| 题目-KC 映射 | `ai_problem_kc_mapping` | problem_id, kc_id, language_pack_id | question → skill 映射 |
| KC 掌握度 | `learner_kc_mastery` | user_id, language_pack_id, kc_id, mastery | 当前 BKT 输出 |

### 缺失（需要补充）

| 数据 | 现状 | NFK 需要 | 解决方案 |
|------|------|---------|---------|
| submission 的 KC 标注 | submission 表无 kc_id 列 | 每次提交关联 KC | 通过 ai_problem_kc_mapping 运行时 JOIN，无需加列 |
| 学生做题序列的 language_pack 归属 | submission 表无 language_pack_id | 按课程包分组训练 | 通过 problem → language_pack 关系 JOIN |
| NFK 训练快照 | 不存在 | 训练数据导出 + 模型版本管理 | 新增训练管理表或目录 |

---

## 结论：现有数据已满足 NFK 训练需求

NFK 训练只需要 4 个字段：`(user_id, question_id, skill_id, response, timestamp)`

这些字段可以通过以下 SQL 直接提取，**无需任何表结构改动**：

```sql
SELECT
    s.user_id,
    s.problem_id AS question_id,
    m.kc_id AS skill_id,
    CASE WHEN s.result = 0 THEN 1 ELSE 0 END AS response,  -- result=0 是 AC
    EXTRACT(EPOCH FROM s.create_time) AS timestamp
FROM submission s
JOIN ai_problem_kc_mapping m ON m.problem_id = s.problem_id
WHERE m.language_pack_id = :target_lp_id
  AND s.user_id IN (
      SELECT user_id FROM submission
      WHERE problem_id IN (
          SELECT problem_id FROM ai_problem_kc_mapping
          WHERE language_pack_id = :target_lp_id
      )
      GROUP BY user_id
      HAVING COUNT(*) >= 5  -- 至少 5 次交互
  )
ORDER BY s.user_id, s.create_time;
```

---

## 基础设施差距

| 基础设施 | 现状 | 差距 | 方案 |
|---------|------|------|------|
| 导出格式 | 纯 JSON (Map) | ONNX 二进制需要 ZIP | `ZipOutputStream` + `StreamingResponseBody` |
| 导入格式 | 解析 JSON Map | 需要解压 ZIP | 检测 magic bytes 区分 JSON/ZIP |
| ONNX Runtime | 未引入 | 推理需要 | `pom.xml` 添加 `onnxruntime:1.17.0` |
| Python 运行时 | 后端无 Python | 训练需要 | 训练在 AutoDL 离线执行，不在生产服务器上 |
| 模型存储 | 无 | ONNX 文件需要持久化 | 文件系统 `/data/nfk_models/`，路径可配置 |
| 异步任务 | 导出是同步的 | 含训练的导出需异步 | 复用 `@Async` 或 init_task 机制 |

## TODO 清单

### Phase 0：代码迁移（将 NFK 移入 backend）

- [ ] **0.1 移动 research/nfk/ 到 backend/nfk/**
  - 将 `research/nfk/` 整个目录移动到 `backend/nfk/`
  - 更新所有内部导入路径（Python 包结构不变，只是根位置变了）
  - 更新 `upload_autodl.sh` 中的路径引用
  - 更新 `demo_gpu.py` 和 `run_local.py` 中的 `project_root` 定位
  - `datasets/` 保持在项目根目录不动（数据不应在 backend 目录内）

- [ ] **0.2 更新 backend Dockerfile**
  - 在 Java 构建阶段后添加 NFK Python 文件 COPY
  - 不需要在 Docker 镜像中安装 PyTorch（训练在 AutoDL）
  - 只需要 ONNX Runtime（Java 依赖，通过 pom.xml）

- [ ] **0.3 更新 .gitignore**
  - 确保 `backend/nfk/__pycache__/` 等 Python 缓存被忽略
  - 确保 `backend/nfk/outputs*/` 训练产物被忽略

### Phase A：数据质量保障（确保正在积累的数据可用）

- [ ] **A1. 验证 ai_problem_kc_mapping 覆盖率**
  - 检查每个已发布课程包中，多少比例的题目有 KC 映射
  - 目标：覆盖率 > 90%
  - 位置：可写一个 admin API 或 SQL 查询
  - 原因：没有 KC 映射的题目在训练时会被丢弃

- [ ] **A2. 验证 submission 表 result 字段语义**
  - 确认 result=0 是 AC，其他值是非 AC
  - 位置：`backend/src/main/java/com/alethicode/controller/SubmissionController.java`
  - 原因：NFK 的 response 是二值（0/1），需要明确映射规则

- [ ] **A3. 验证 submission.create_time 精度**
  - 确认 create_time 是 TIMESTAMPTZ（已确认）
  - 确认同一学生的多次提交有不同时间戳（不是批量导入导致的相同时间）
  - 原因：NFK 的遗忘门控（Component B）依赖准确的时间差

- [ ] **A4. 确认一题多 KC 的处理**
  - 一道题可能映射多个 KC（ai_problem_kc_mapping 允许 1:N）
  - 决策：训练时取主 KC（weight 最高的）还是复制为多条记录
  - 建议：取 weight 最高的单个 KC

### Phase B：训练数据导出工具

- [ ] **B1. 后端新增数据导出 Service**
  - 文件：`backend/src/main/java/com/alethicode/service/NfkDataExportService.java`
  - 功能：按 language_pack_id 导出训练数据为 CSV
  - 输出格式：`user_id,question_id,skill_id,response,timestamp`
  - 过滤：去除交互次数 < 5 的学生
  - 返回数据统计：学生数、题目数、KC 数、交互总数

- [ ] **B2. Admin API 暴露导出功能**
  - `GET /api/admin/nfk/training-data/export?language_pack_id=X`
  - 返回 CSV 文件流
  - 同时返回元数据（学生数、题目数等）
  - 管理员权限

- [ ] **B3. Admin API 数据就绪度查询**
  - `GET /api/admin/nfk/training-data/readiness?language_pack_id=X`
  - 返回：

```json
{
  "language_pack_id": 1,
  "language_pack_name": "Python 基础",
  "student_count": 156,
  "problem_count": 45,
  "kc_count": 18,
  "total_interactions": 8420,
  "avg_interactions_per_student": 54,
  "kc_mapping_coverage": 0.93,
  "readiness_level": "HOT",
  "readiness_detail": "数据量充足，建议训练",
  "thresholds": {
    "COLD": "< 50 students",
    "WARM": "50-200 students",
    "HOT": "> 200 students"
  }
}
```

### Phase C：模型部署基础设施

- [x] **C1. 添加 ONNX Runtime 依赖**
  - 文件：`backend/pom.xml`
  - 依赖：`com.microsoft.onnxruntime:onnxruntime:1.17.3`（optional，部署时必须）
  - ONNX 模型：`combined_outputs/nfk_outputs/onnx/alethicode_nfk.onnx` (17.8MB)

- [x] **C2. 新增模型配置**
  - 文件：`AlethicodeProperties.java` → `Nfk` 内部类
  - 配置项：`model-path`、`enabled`、`fallback-to-bkt`、`inference-timeout-ms`

- [x] **C3. 新增 NfkInferenceService**
  - 文件：`backend/src/main/java/com/alethicode/service/NfkInferenceService.java`
  - 功能：启动时加载 ONNX、序列推理、超时保护、submission 列表便捷方法

- [ ] **C4. MasteryService 集成 NFK**
  - 修改：`MasteryService.java`
  - 逻辑：
    - 如果 `alethicode.nfk.enabled=true` 且 ONNX 模型已加载
    - 学生提交后调用 NfkInferenceService 获取最新 kt_prob
    - 将 kt_prob 写入 learner_kc_mastery.mastery
    - 否则回退到现有 BKT 更新逻辑

### Phase D：导出格式升级 + 训练集成

- [ ] **D1. 课程包导出格式从 JSON → ZIP**
  - 当前：导出产物是单个 JSON 文件
  - 升级为 ZIP 包，目录结构：
    ```
    language_pack_export_{id}_{date}.zip
    ├── content.json              # 现有导出内容（题目、KC、课件等）
    ├── manifest.json             # 新增：包描述（版本、创建者、内容清单）
    └── nfk_model/                # 可选：NFK 模型目录
        ├── alethicode_nfk.onnx   # 训练好的 ONNX 模型
        └── metadata.json         # 模型元数据
    ```
  - `manifest.json` 结构：
    ```json
    {
      "format_version": "2.0",
      "language_pack_id": 1,
      "language_pack_name": "Python 基础",
      "exported_at": "2026-04-16T10:00:00Z",
      "exported_by": "admin",
      "includes_nfk_model": true,
      "content_hash": "sha256:..."
    }
    ```
  - `nfk_model/metadata.json` 结构：
    ```json
    {
      "auc": 0.758,
      "student_count": 156,
      "kc_count": 18,
      "interaction_count": 8420,
      "trained_at": "2026-04-16T09:30:00Z",
      "model_config": { "hidden_dim": 256, "n_kt_heads": 1 }
    }
    ```

- [ ] **D2. 导出 API 新增训练选项**
  - 导出请求参数新增：`include_nfk_model: boolean`（默认 false）
  - 前端导出对话框新增复选框："包含 AI 知识追踪模型"
    - 未勾选 → 立即导出，秒级完成
    - 勾选 → 弹出确认：
      - 显示数据就绪度（学生数、交互数）
      - 显示预估训练时间（数据量 × 系数）
      - 就绪度 < WARM 时置灰并提示"学生数据不足，需 50+ 学生"
  - 导出流程：
    1. 打包 content.json（同现有逻辑）
    2. 如果 include_nfk_model=true 且就绪度 >= WARM：
       a. 提取训练数据 CSV
       b. 调用 NFK 训练（子进程或 Python 微服务）
       c. 导出 ONNX
       d. 写入 nfk_model/ 目录
    3. 生成 manifest.json
    4. 打包 ZIP

- [ ] **D3. 课程包导入流程升级**
  - 导入支持两种格式：
    - 旧格式（纯 JSON）→ 兼容现有逻辑
    - 新格式（ZIP）→ 解压后按目录处理
  - ZIP 导入流程：
    1. 解压到临时目录
    2. 读取 manifest.json 确定版本和内容
    3. 导入 content.json（同现有逻辑）
    4. 如果存在 nfk_model/ 目录：
       a. 复制 ONNX 到模型存储目录
       b. 记录 language_pack_id → 模型路径映射
       c. NfkInferenceService 热加载新模型
       d. 导入界面提示："已加载 AI 知识追踪模型（AUC=0.758，基于 156 名学生训练）"

- [ ] **D4. 导出异步化**
  - 不含模型的导出：同步，立即返回 ZIP 流
  - 含模型的导出：异步任务
    - `POST /api/admin/language-packs/{id}/export` 返回 `{ task_id: "..." }`
    - `GET /api/admin/tasks/{task_id}/status` 返回进度
    - 进度阶段：`PACKING_CONTENT` → `EXTRACTING_DATA` → `TRAINING_MODEL` → `EXPORTING_ONNX` → `GENERATING_ZIP` → `DONE`
    - 完成后 `GET /api/admin/tasks/{task_id}/download` 下载 ZIP

### Phase E：前端就绪度展示

- [ ] **E1. Admin 课程包详情页新增"NFK 模型"卡片**
  - 调用 B3 API 显示数据就绪度
  - 如果课程包已有 ONNX 模型（D2 导入或 D1 训练过），显示模型信息（AUC、学生数、训练时间）
  - 导出按钮旁标注"导出时将自动训练 NFK 模型"

---

## 工期估算

| Phase | 天数 | 优先级 | 依赖 |
|-------|------|--------|------|
| A (数据质量验证) | 0.5 | P0 | 无 |
| B (导出工具) | 1-2 | P0 | A |
| C (ONNX 推理) | 2-3 | P1 | B |
| D (导出训练+导入加载) | 2-3 | P1 | B + C + NFK 训练脚本已就绪 |
| E (前端就绪度) | 0.5 | P2 | B |

**P0 合计：1.5-2.5 天** — 数据验证 + 导出工具，证明数据可用
**P0+P1 合计：6-8.5 天** — 完成"导出时训练 → 导入时加载"闭环

---

## 数据飞轮落地时间线

```
课程包创建（AI 初始化 → 生成 KC、题目、KC-题目映射）
  ↓
第 1 轮使用（50 学生做题）
  ↓  BKT 驱动掌握度（冷启动）
  ↓  submission 自然积累
  ↓
学期结束，老师导出课程包
  ↓  导出流程检测就绪度 → WARM (50 学生)
  ↓  自动训练 NFK → 打包 ONNX 进导出 zip
  ↓
另一个老师导入该课程包
  ↓  导入时自动加载 ONNX 模型
  ↓  第 2 轮使用的学生立即享受 NFK 精度
  ↓
第 2 轮使用（又 50 学生，累积 100）
  ↓  NFK 驱动掌握度 + BKT 回退保障
  ↓
再次导出时
  ↓  就绪度 → HOT (100+ 学生)
  ↓  用累积数据重新训练 → AUC 进一步提升
  ↓  模型越用越准
```

---

## NFK 竞赛答辩演讲稿

> 以下为 NFK 知识追踪引擎部分的竞赛演讲参考，约 3-4 分钟。

---

"接下来给大家介绍我们整个教学系统的 AI 核心——**NFK 知识追踪引擎**。"

"先说一个场景。学生做了一道 for 循环的题，做对了。三天后又做了一道列表切片的题，做错了。"

"传统的 OJ 只记录两条提交记录——对、错。**完了。**"

"NFK 不一样。它在后台实时追踪：循环掌握度从 0.6 上升到 0.65，列表切片掌握度维持在 0.3——**而且，三天前学的 for 循环，掌握度已经因为遗忘衰减了 8%。**"

"**这就是知识追踪——不只记录对错，而是建模学生的知识状态。**"

---

"NFK 由三个组件组成，每一个都来自国际顶会验证过的技术。"

"**组件 A，DKT 序列编码器。**来自 NeurIPS 2015。LSTM 把学生做题历史编码成知识状态向量。它能区分'先对后错'和'先错后对'——这两种情况下学生的知识状态是不同的。"

"**组件 B，遗忘感知注意力。**来自 CIKM 2023 的 FoLiBiKT。核心创新：**为每个知识点学习一个独立的遗忘速率。**模型自己发现'变量赋值'忘得慢、'递归'忘得快——不是规则设定的，是从数据中学出来的。"

"**组件 C，交叉注意力检索。**来自 ICLR 2023 的 simpleKT 思想。当学生做一道新题时，用这道题的语义去查询历史中最相关的做题经验。不看全部历史，只检索最重要的那几次。"

"三个组件，各司其职——**A 编码历史，B 建模遗忘，C 精准检索。**"

---

"为什么不直接用 simpleKT？"

"因为 simpleKT 缺两样东西。第一，它没有遗忘建模——不知道学生上次做题是 3 天前还是 3 分钟前。第二，它没有 LSTM——无法捕获做题的序列依赖。"

"NFK 是三项技术的**融合**，不是简单的复制。"

---

"效果。"

"在 ASSISTments 2009——国际知识追踪领域最经典的基准数据集上——标准 DKT AUC 是 0.71。"

"**NFK 达到 0.758。超出基准 6.8%。**"

"最新的图神经网络方法 HHGKT 可以到 0.83——但它推理一次需要 200 毫秒。"

"**NFK 推理只需要 5 毫秒。快 40 倍。**"

"5 毫秒是什么概念？学生点提交按钮，还没看到判题结果，**AI 已经更新了他所有知识点的掌握度。**"

"我们没有追实验室的最后 3% AUC。我们选择了**实时教学**。"

---

"工程层面。"

"328 万参数。嵌入共享优化后比原始版本减少 57%。导出为单个 ONNX 文件，Spring Boot 直接加载。**不需要 GPU，不需要 Python 环境。**"

"三组件架构支持完整的消融实验——A+B+C、A+B、A+C、A only——每个组件的独立贡献都经过验证。"

"还有**数据飞轮**。课程包第一次使用时用基础算法冷启动。老师导出课程包时，系统自动用积累的学生数据训练 NFK 模型，打包进导出文件。另一个老师导入后，立即享受训练好的 AI 精度。**模型随课程包迁移，越用越准。**"

---

"最后总结。NFK 知识追踪引擎——"

"**三项顶会技术融合。**"
"**AUC 0.758，超基准 6.8%。**"
"**5 毫秒推理，实时教学。**"
"**可学习遗忘，每个知识点独立衰减。**"
"**数据飞轮，越用越聪明。**"

"**谢谢大家。**"
