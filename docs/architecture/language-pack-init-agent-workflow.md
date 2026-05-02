# 语言包初始化 Agent 工作流

## 目标

- 在没有人工参考 Markdown 的情况下，尽量稳定地从课件中抽出接近人工整理质量的可 OJ 化题目。
- 将初始化过程拆成可复跑、可审计、可追溯的多阶段 Agent 流水线。
- 让每一阶段都只做一件事，并且只读上一阶段的标准化 JSON 产物。

## 边界

- 本文描述的是通用工作流，不依赖 `PPT_编程例题整理_OJ化_尽量多版_重新生成_20260323.md` 这类人工参考文件。
- `python-basic` 的 51 题基线只作为这次测试 PPT 的专项验收资产，不是通用业务规则。
- 正式初始化输入仍然是课件文档本身；人工参考稿只用于专项回归和抽取质量校准。

## 第一性原理

- 课件里的“可 OJ 化题目”不是页面级字符串匹配结果，而是跨页教学单元。
- 漏题大多不是模型不够强，而是输入切分、单元边界、去重规则和质量门槛设错了。
- 想要接近人工整理质量，核心不是一次 prompt 更长，而是多阶段收束：
  - 先把单元找全
  - 再判断哪些能 OJ 化
  - 再逐单元改写成标准题包
  - 最后做覆盖审计和冲突复核

## 推荐 Agent 流水线

### 1. CoursewareSegmentationAgent

- 输入：
  - 规范化后的文档页数据
  - 每页标题、正文、页号、章节信息
- 输出：
  - `courseware_segments.json`
- 职责：
  - 把页流切成连续教学片段，而不是直接抽题
  - 给每个片段打上 `chapter`、`start_page`、`end_page`、`segment_type`
- 强制规则：
  - 同一题目如果横跨多页，必须合并成一个 segment
  - 片段标题不能只靠页标题，必须结合正文中的“例”“练习”“作业”“编程题”“思考题”等信号
  - 每个 segment 都必须有可回溯页范围

### 2. CoursewareUnitExtractionAgent

- 输入：
  - `courseware_segments.json`
- 输出：
  - `courseware_units.json`
- 职责：
  - 从教学片段中抽出“教学单元”
  - 明确区分：
    - `code_snippet`
    - `worked_example`
    - `exercise`
    - `assignment`
    - `demo`
- 强制规则：
  - 每个 unit 必须有 `source_title`
  - 每个 unit 必须有 `page_range`
  - 每个 unit 必须有 `evidence_excerpt`
  - 不在这个阶段生成 OJ 题

### 3. OjCandidateJudgementAgent

- 输入：
  - `courseware_units.json`
- 输出：
  - `oj_candidates.json`
- 职责：
  - 判断哪些教学单元适合 OJ 化
  - 输出 `oj_convertible` 和 `oj_block_reason`
- 判定维度：
  - 是否存在明确输入输出目标
  - 是否能脱离课堂口头说明独立成立
  - 是否属于纯概念讲解或纯 API 演示
  - 是否只是半成品代码展示，缺少可验证任务目标
- 强制规则：
  - `oj_convertible=false` 时必须写清 `oj_block_reason`
  - 不允许在这一步“顺手生成题目”

### 4. OjProblemPackageAgent

- 输入：
  - `oj_candidates.json` 中 `oj_convertible=true` 的单元
- 输出：
  - `problem_packages.json`
  - `problem_packages.md`
- 职责：
  - 每个可 OJ 化单元产出一个标准 JSON 题包
- 单元到题包的映射规则：
  - 一个 unit 对应一个题包
  - 不按 KC 抽样
  - 不按总题数截断
- 题包必须包含：
  - `display_id`
  - `title`
  - `description`
  - `input_description`
  - `output_description`
  - `samples`
  - `test_cases`
  - `template`
  - `time_limit`
  - `memory_limit`
  - `difficulty`
  - `source_pages`
  - `source_example_ids`
  - `related_kc_ids`
  - `teaching_explanation`
  - `common_mistakes`

### 5. ProblemPackageValidationAgent

- 输入：
  - `problem_packages.json`
- 输出：
  - `validated_problem_packages.json`
  - `validation_report.json`
- 职责：
  - 做结构校验、题源校验、字段完整性校验
- 强制规则：
  - 缺 `samples`、`test_cases`、`template` 任一项直接失败
  - `source_pages` 必须存在且属于原始页范围
  - `source_example_ids` 必须能反查到教学单元
  - 标题、题干、输入输出不能完全脱离原单元语义

### 6. CoverageAuditAgent

- 输入：
  - `courseware_units.json`
  - `oj_candidates.json`
  - `problem_packages.json`
- 输出：
  - `coverage_report.json`
- 职责：
  - 在没有人工参考稿时，做内部覆盖率审计
- 核心指标：
  - `segment_count`
  - `unit_count`
  - `oj_candidate_count`
  - `generated_problem_count`
  - `blocked_unit_count`
  - `deduped_unit_count`
  - `unresolved_low_confidence_count`
- 审计重点：
  - 哪些片段被判成了非题目
  - 哪些单元被挡在 `oj_convertible=false`
  - 哪些章节题目密度异常低
  - 哪些题目标题高度相似，可能误去重

### 7. EscalationReviewAgent

- 输入：
  - `validation_report.json`
  - `coverage_report.json`
  - 低置信度单元集合
- 输出：
  - `escalation_review.json`
- 职责：
  - 只处理“有争议”的单元，不重跑全量
- 触发条件：
  - 某章节候选题数显著低于相邻章节
  - 某单元被多个阶段给出冲突判断
  - 题包标题过泛，例如“循环练习”“列表练习”
  - 题源页包含明显任务指令，但被判成 `oj_convertible=false`

## 无参考场景下如何逼近人工质量

### 1. 用连续页窗口，不用单页独立抽取

- 单页抽取最容易漏掉“题干在上一页、样例在下一页”的题。
- 推荐窗口：
  - 主窗口 2 到 5 页
  - 相邻窗口重叠 1 页
- 如果标题和任务信号跨页，优先合并，不要先切碎再让后续阶段猜。

### 2. 先抽“单元”，再判“能不能 OJ 化”

- 人工整理不是先问“这页能不能变成 OJ 题”，而是先识别这页到底在讲什么。
- 如果把“识别单元”和“OJ 化判断”混在一个 prompt 里，模型会更容易漏掉边界模糊的题。

### 3. 去重要靠题源签名，不要只靠正文归一化

- 推荐签名：
  - `chapter + source_title + page_range + unit_type`
- 只用 `normalized_body` 去重，会把“同一技巧的不同练习题”误合并。

### 4. 对低置信度章节做二次扫描

- 无参考场景下最有价值的补救不是“重跑全量”，而是“只重扫可疑章节”。
- 可疑信号：
  - 章节页数很多，但 `oj_candidate_count` 很低
  - 章节中大量出现“练习/编程/上机”字样，但候选题却很少
  - 单元类型几乎全是 `demo`

### 5. 持久化每一阶段的 artifact 和 hash

- 只有把阶段产物都落盘，才能知道问题出在：
  - 切分阶段漏了
  - 单元阶段误判了
  - OJ 化阶段没生成
  - 还是验证阶段被打回
- 这也是后续做专项回归和 prompt 调优的基础。

### 6. 用“有参考校准结果”反推无参考运行阈值

- 这次 `python-basic` 的 51 题基线，最有价值的不是长期依赖这份文件，而是借它校准无参考时应该盯什么信号。
- 可复用的校准方法：
  - 统计每章 `segment_count / unit_count / oj_candidate_count / generated_problem_count`
  - 找出人工基线里题目密度高、但机器抽取偏低的章节
  - 回看这些章节共同特征，是跨页、标题不规范、还是被误判成 `demo`
  - 把这些特征沉淀成下一轮 Agent 提示词和审计阈值
- 真正上线到没有参考文件的新 PPT 时：
  - 不再要求“必须等于 51”
  - 但必须要求“章节密度异常要被审计出来，低置信度单元要被二次复核”

### 7. 无参考运行时采用“先全量、再收敛”的策略

- 目标不是一开始就把误报压到最低，而是先把可能的题源尽量找全，再在后续阶段收敛。
- 推荐做法：
  - `CoursewareSegmentationAgent` 和 `CoursewareUnitExtractionAgent` 偏召回优先
  - `OjCandidateJudgementAgent` 负责第一次过滤
  - `CoverageAuditAgent` 专门盯漏题风险而不是只盯误报
  - `EscalationReviewAgent` 只处理可疑章节和冲突单元
- 如果一开始就把抽取阶段卡得太紧，会直接复现这次旧流程“总题量偏少、漏题很多”的问题。

## 标准产物

- `courseware_segments.json`
- `courseware_units.json`
- `oj_candidates.json`
- `problem_packages.json`
- `problem_packages.md`
- `validated_problem_packages.json`
- `validation_report.json`
- `coverage_report.json`
- `escalation_review.json`

## 推荐失败策略

- 任一阶段 schema 不合法，任务直接失败。
- 任一题包无法回溯到 `source_pages` 或 `source_example_ids`，直接失败。
- 覆盖审计发现某章节题目密度异常低时，不发布，先进入 `EscalationReviewAgent`。

## 与这次 Python 基础专项回归的关系

- `python-basic` 的 51 题基线用于回答两个问题：
  - 当前流程到底漏了哪些题
  - 哪些漏题属于系统性问题
- 基线帮助我们校准了这套工作流，但工作流本身不能依赖基线存在。
- 真正可复用的做法是：
  - 把人工基线里体现出的判断逻辑，沉淀为上述多阶段 Agent 约束
  - 让系统在没有参考文件时，也能通过多阶段审计逼近人工整理结果

## 最终验收建议

- 通用初始化任务验收：
  - 所有阶段 artifact 都存在
  - 所有发布题都有来源页和来源单元
  - `coverage_report.json` 中不存在未处理的高风险章节
- Python 基础专项验收：
  - 基线题目数固定为 51
  - 新流程生成结果与基线基本对齐
  - 缺失项和新增项必须输出报告
- 无参考常规任务验收：
  - 不要求预先存在人工 Markdown 或基线 JSON
  - 但必须输出章节级覆盖率信号、低置信度清单和可追溯 artifact
  - 若某章节题源密度异常低，必须进入复核，而不是直接发布
