# 语言包初始化改进 TODO（交接版）

## 一、这个项目现在在做什么

Alethicode 是一个面向非计算机专业 Python 初学者的 OJ 与课件 AI 教学系统。当前语言包初始化链路的目标，不是单纯把 PPT 转成文本，而是把一组教学课件稳定地转成以下资产：

- 规范化课件文档与预览 PDF
- 页级文本与页码索引
- 章节级知识组件（KC）
- 课件中的教学单元 / 例题
- 标准 JSON 题包
- 可发布到题库的题目、测试点与教学元数据
- 供课件问答助手使用的页级证据与检索基础

当前主链路是：

1. admin 上传多份课件创建初始化任务
2. 规范化文档
3. 解析页文本
4. 抽取并归并 KC
5. 抽取教学单元 / 例题
6. 生成候选题包
7. 校验候选题
8. 发布语言包

## 二、当前现场快照

- 已停止本轮所有 `LanguagePackAlethicodeReplay/Resume` 手工续跑任务，不再继续推进当前坏结果。
- Alethicode 正式库当前 `python-basic v2` 为：
  - `language_pack_id=7`
  - `task_id=6`
  - `stage=oj_candidates_ready`
- 当前这套 7 份 PPT 已完成规范化，合计 `561` 页。
- 当前结果明显错误：
  - `language_pack_kc` 实际只有 `348` 条有效记录，但 `language_pack.kc_count` 记成了 `349`
  - `language_pack_example=401`
  - `language_pack_example_kc_mapping=628`
  - `language_pack_problem_generation_log=102`
- 根因不是“KC 没抽到”，而是“KC 的抽取粒度错了”，已经被抽成了过细的知识碎片。
- 当前坏例子包括但不限于：
  - `第一台电子计算机ENIAC`
  - `第一种函数调用`
  - `pip工具常用子命令`
  - `局部变量与全局变量的冲突`
  - `__name__变量`
- 这些条目不应该作为语言包内的 canonical KC 独立存在，它们最多只能作为别名、证据或页面细节，不能成为主 KC。

## 三、已确认的现状结论

- 例题与 KC 的绑定链路已经存在：
  - `ExampleExtractionServiceImpl` 会把例题写入 `language_pack_example`
  - 同时写入 `language_pack_example_kc_mapping`
- 题目的判题测试点生成链路已经存在：
  - `ProblemPackageWriteServiceImpl` 会把 `test_cases` 落盘到 `deploy/data/test_case/<test_case_id>`
  - 同时写入 `problem.test_case_id`
- 本轮 `python-basic v2` 任务并没有打开客观题开关：
  - 当前问题不是“误生成了选择题 / 填空题”
  - 当前真正的问题是：例题池把过多教学单元写进了 `language_pack_example`
  - 本轮专项目标仍然是编程题初始化，不把客观题、选择题、填空题纳入本次修复目标
- 当前缺的不是“有没有这两条链路”，而是：
  - 没有把它们作为本次初始化重构后的专项验收项固定下来
  - admin 侧目前也不方便直接检查“某例题到底绑定了哪些 canonical KC”
- 课件教学顺序目前没有被系统建模：
  - `language_pack_document` 没有 `sort_order`
  - `/api/admin/language-packs/init-tasks/{taskId}/documents` 按 `id` 返回
  - KC / 例题抽取阶段读取页面时，本质上仍按 `document_id -> page_no` 推进
- 结论：
  - 课件排序功能不能只做前端拖拽，必须从数据库字段、查询排序、初始化读取顺序一路打通

## 四、后续实现阶段

### 阶段 0：冻结坏现场，先不沿用当前 v2 结果

本阶段目标：

- 当前 `python-basic v2` 的坏 KC 结果不再继续续跑
- 后续所有修复完成前，不允许把当前 `task_id=6` 往 publish 推进
- 当前现场只保留为坏样本和回归对照，不作为最终可发布结果

验收步骤：

1. 确认系统内没有任何 `LanguagePackAlethicodeReplayManualTest` 或 `LanguagePackAlethicodeResumeManualTest` 相关进程在运行。
2. 确认 `language_pack_id=7 / task_id=6` 仍停留在非发布态，不再推进。
3. 将当前错误快照固定记录到文档与后续测试说明中：
   - `348` 个实际 KC
   - `401` 个例题
   - `628` 条例题-KC 映射
   - `102` 个候选题
4. 后续真实专项重跑时，不允许在这个坏状态上继续补丁式修复，必须基于清理后的 `Python -v2` 重新完整跑。

### 阶段 1：重定义 canonical KC 粒度，这是本轮第一优先级

本阶段目标：

- 明确修复方向不是“提高 KC 召回率”，而是“把 KC 的抽取单位改对”
- 将 KC 从“单页事实 / 单条语法点 / 单个示例命名”收敛为“适合非计算机专业 Python 初学者的教学级知识组件”
- 对 `python-basic` 这套 PPT，以现有 Python 语言包约 `46` 个 KC 作为专项校准参考，把 KC 粒度拉回到“教学级概念”这一层，而不是几百个碎片条目
- 对后续其他语言包，不要求机械地落在 `46` 左右；要求的是 canonical KC 的层级与 Python 语言包保持同一粒度，而不是固定数量

必须改的点：

- 改 `KcBatchExtractionAgent` 提示词，不再鼓励“越具体越好”，而是要求抽取“章节内可复用、可教学、可挂载多个例题的 KC”
- 改 `KcReconciliationAgent`，把以下类型统一并入 canonical KC，而不是保留为独立 KC：
  - 同一概念的不同说法
  - “第一种 / 第二种 / 第三种”这类教学展开形式
  - 单页历史事实或单个命令名
  - 示例标题里带出来的临时叫法
- canonical KC 必须优先服务后续两个目标：
  - 例题挂载
  - 题目生成的 `related_kc_ids`
- 别名、证据摘录、页码范围可以保留细节，但 canonical KC 名称必须变粗，不允许继续碎片化

专项验收步骤：

1. 仅重跑 `extract-kcs`，不要先动后续阶段。
2. `python-basic v2` 的 KC 总数必须回落到接近 Python 语言包粒度的区间：
   - Python 基础专项参考值：约 `46`
   - 验收区间：`40-52`
   - 超出该区间直接判定本阶段失败
   - 这个数量区间只用于本次 `python-basic` 专项校准，不推广为其他语言包的通用硬门槛
3. 随机抽查至少 `20` 个 KC，逐条确认它们是“教学级概念”而不是“页面级碎片”。
4. 坏样本清单中的典型碎片条目不得再以独立 canonical KC 形式出现。
5. 每个 canonical KC 都必须保留：
   - `canonical_name`
   - `aliases`
   - `page_numbers`
   - `evidence_excerpt`
6. `language_pack.kc_count` 与 `language_pack_kc` 实际记录数必须一致，不允许再出现 `349` vs `348` 这种计数漂移。

### 阶段 2：基于粗粒度 KC 重新绑定例题和候选题

本阶段目标：

- 让例题与题目都绑定到新的 coarse-grained canonical KC
- 不允许例题继续绑定到“历史坏 KC”或局部批次 KC
- 保留 `1 unit -> 1 problem package`，但所有 `related_kc_ids` 必须来自新的 canonical KC
- 例题抽取口径必须从“教学单元全召回”收紧为“接近题库可出题单元的一题一单元粒度”，以当前题库约 `50` 道题作为 `python-basic` 专项校准参考，不能再产出 `401` 条这种数量级失真的结果
- 对后续其他语言包，不要求机械地落在 `50` 左右；要求的是例题池的层级与当前题库保持同一颗粒度，而不是固定数量

必须改的点：

- 清空并重建 `language_pack_example` 与 `language_pack_example_kc_mapping`
- 重建 `language_pack_problem_generation_log`
- 调整例题抽取提示词与规则：
  - `worked_example / code_snippet / demo` 只有在能稳定承载成一道独立 OJ 题时才允许进入例题池
  - 纯讲解页、纯概念页、纯展示页、单个 API 演示页不得继续作为独立例题写入 `language_pack_example`
  - 明确禁止把客观题、选择题、填空题形态混入本轮编程题例题池
- admin 初始化详情页要能直接看见：
  - 例题的 `source_title`
  - `unit_type`
  - 绑定的 KC 名称或 KC 数量
- 生成阶段继续输出：
  - `problem_packages.json`
  - `problem_packages.md`
  - `coverage_report.json`

专项验收步骤：

1. 在完成阶段 1 后，重新执行 `extract-examples -> generate-problems -> validate-problems`。
2. `python-basic v2` 的例题池规模必须从当前 `401` 条明显收缩到与当前题库约 `50` 道题同量级的范围，目标是“粒度接近”而不是“强行凑数相等”。
3. 验收时要人工抽查至少 `20` 条例题，确认每条都接近“一道可独立出题的编程任务”，而不是课堂讲解片段、代码片段或纯 demo。
4. `language_pack_example_kc_mapping` 必须非空，并且每个例题都至少绑定 `1` 个 canonical KC。
5. `language_pack_problem_generation_log.related_kc_ids_json` 中的每个 KC ID 都必须能在 `language_pack_kc` 中回查到。
6. admin 页面抽查至少 `20` 个例题，确认它们绑定的是粗粒度 KC，而不是页面碎片。
7. `coverage_report.json` 必须继续输出：
   - `missing`
   - `extra`
   - `blocked_candidates`
   - `chapter_stats`
   - `high_risk_chapters`
8. Python 基础专项的 `51` 题问题覆盖回归不能丢：
   - 仍要输出缺失项和新增项报告
   - 不允许因为 KC 变粗就把题目覆盖率回归删掉

### 阶段 3：在正确位置增加并发，但不破坏连贯性和断点续跑

本阶段目标：

- 加速初始化，但不能为了速度破坏章节记忆、批次复用和结果可审计性
- 并发必须加在“天然独立”的位置，不能打乱同一章节内需要保持顺序的归并逻辑

并发设计要求：

- 文档规范化：按文件并发
- 页解析：按文件并发
- KC 抽取：按文档 lane 并发，但单文档内部仍保留当前二进制回退和最终 reconciliation
- 例题抽取：按 segment 并发
- 题包生成：按最终 OJ candidate 并发
- 校验与发布：保持串行

默认并发建议值：

- `document-normalize = 4`
- `document-parse = 4`
- `kc-extract = 3`
- `unit-extract = 6`
- `problem-generate = 6`

必须保证：

- 所有批次复用仍以 `input_hash` 为准
- `language_pack_init_batch_run` 与 `language_pack_init_agent_run` 仍可完整追溯
- 最终 artifact 的顺序仍由“教学顺序 + 页码顺序”决定，不能被并发打乱

专项验收步骤：

1. 用相同输入分别跑 `并发=1` 与 `默认并发` 两组流程。
2. 两次结果的以下集合必须一致：
   - `kc_catalog` 中的 canonical KC 集合
   - `language_pack_example.source_signature` 集合
   - `language_pack_problem_generation_log.source_signature` 集合
3. 不允许出现重复 batch run、重复例题、重复候选题。
4. 人为制造一个中途失败批次后重跑，只允许复用已成功输入，不允许把整阶段全量重算。
5. 在并发打开后，阶段日志和 agent run 日志仍能定位每个失败批次的具体页范围或 segment。

### 阶段 4：支持 admin 在初始化窗口拖动排序课件，并让 AI 按教学顺序读取

本阶段目标：

- 上传完课件后，admin 可以在初始化窗口拖动排序
- 该顺序必须写入数据库，成为初始化后续阶段的唯一教学顺序
- AI 读取文档、生成 KC、抽取例题时都按这个顺序推进

必须改的点：

- 给 `language_pack_document` 新增 `sort_order`
- `listDocuments` 返回 `sort_order`
- 新增资源化接口：
  - `PATCH /api/admin/language-packs/init-tasks/{taskId}/documents/order`
  - 请求体只传有序 `document_ids`
- 初始化页面增加课件顺序面板，支持拖拽排序与保存
- 所有相关查询和读取顺序统一改成：
  - `sort_order asc`
  - `id asc`
  - `page_no asc`
- 排序修改只允许发生在 `extract-kcs` 开始前
  - 允许阶段：`normalizing`、`parsing`
  - 禁止阶段：`kc_ready` 及之后

前端同步修正：

- 初始化页面的阶段显示必须改成真实阶段，不允许继续沿用过时的：
  - `created`
  - `normalizing`
  - `parsing`
  - `kc_ready`
  - `segments_ready`
  - `units_ready`
  - `oj_candidates_ready`
  - `problem_packages_ready`
  - `problems_validated`
  - `published`
  - `failed`
- 不允许继续沿用 `examples_ready` 这类过时阶段名误导操作

专项验收步骤：

1. 新建一个初始化任务并上传 7 个 PPT。
2. 在初始化窗口手动拖动顺序，保存后刷新页面，顺序必须保持不变。
3. `GET /api/admin/language-packs/init-tasks/{taskId}/documents` 返回的顺序必须与 UI 一致。
4. 开始 `extract-kcs` 后再次尝试改顺序，接口必须 fail-fast 拒绝。
5. 抽查 `kc_batch_results.json` 或阶段日志，确认第一批读取的文档顺序与 admin 排序一致，而不是 `document_id` 默认顺序。

### 阶段 5：固定“例题-KC 绑定”和“test_case 生成”为专项回归，并清理后重跑 Python -v2

本阶段目标：

- 不再靠人工印象判断“有没有绑定 KC”“有没有生成测试点”
- 把这两件事固化成自动化回归
- 最终删除当前坏的 `Python -v2` 草稿结果，基于修复后的流程重建一次完整专项结果

必须做的检查：

- 例题-KC 绑定回归
  - `language_pack_example_kc_mapping` 必须存在并随例题重建
  - 抽样例题能在 admin 界面或 SQL 中直接看到绑定的 canonical KC
- `test_case` 回归
  - 发布后的题目 `problem.test_case_id` 必须非空
  - `deploy/data/test_case/<test_case_id>` 目录必须真实存在
  - `info` 文件与输入输出文件必须存在

最终专项重跑步骤：

1. 删除当前坏的 `python-basic v2` 草稿包及其 `task_id=6` 相关初始化数据、artifact、batch run、候选题和孤立测试点目录。
2. 重新以 `Python -v2` 名义创建初始化任务。
3. 上传 7 个 PPT。
4. 在初始化窗口按教学顺序拖动排序。
5. 依次执行：
   - `parse`
   - `extract-kcs`
   - `extract-examples`
   - `generate-problems`
   - `validate-problems`
   - `publish`
6. 发布完成后再验证 QA 与题库侧行为。

最终验收步骤：

1. `python-basic v2` 的 canonical KC 数量必须稳定在 `40-52` 区间内，且人工抽查确认粒度与 Python 语言包接近。
2. `coverage_report.json` 必须满足：
   - `missing=[]`
   - `high_risk_chapters=[]`
3. 发布后的题目必须全部拥有有效 `test_case_id`，并且对应目录真实存在。
4. 例题与 KC 的绑定必须可查、可展示、可回归验证。
5. 语言包发布后，`/api/language-pack-qa/packs` 能正常识别该包，课件问答助手可正常进入可选状态。

## 五、下一窗口的执行顺序建议

不要从“继续跑当前 task 6”开始。正确顺序应当是：

1. 先改 KC 粒度定义与 reconciliation
2. 再重建例题与候选题绑定
3. 再加并发
4. 再做课件排序
5. 最后清理坏 v2 并完整重跑

这一轮的本质不是补超时，也不是继续往后跑，而是先把“KC 的单位抽错了”这个根问题改掉。
