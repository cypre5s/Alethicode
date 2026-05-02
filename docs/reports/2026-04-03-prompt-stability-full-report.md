# Prompt 稳定性全链路实验报告（2026-04-03）

## 1. 实验目标与 Gating 规则

### 1.1 目标
- 验证 `方案1`（仅强化 `ProblemGenerationServiceImpl` prompt 约束）是否提升题目生成稳定性。
- 在不通过“减题”换稳定的前提下，评估全链路一致性。

### 1.2 本次最终采用的重判口径
- 两章实验仍保留原始统计口径，便于和历史结果直接对比。
- 全量 7 章三轮实验的历史结果，先按新的主标准重判：**只要核心任务和输入输出目标相同，就认为是同一题**。
- 从 2026-04-03 本轮实现开始，后续多轮实验的主锚点进一步固定为：`document_title + source_pages/page_range`。
- 在同一源页位点内，允许最小必要的 OJ 化改造，例如：
  - 固定上界改成变量输入，如 `1..10000 -> 1..N`
  - 固定数据改成从 `stdin` 读取同结构数据
  - 固定轮数改成输入控制
- 只有发生以下情况，才记为“越界改题”：
  - 核心计算目标变化
  - 输入/输出契约变化到已经不是同一道题
  - 从当前源页位点跳到了同章其他练习
- 明确忽略：
  - `candidate_title`
  - `source_signature`
  - `unit_type`
  - 背景故事和措辞差异
- 固定链路：`parse -> extract-kcs -> extract-examples -> generate-problems -> validate-problems -> publish`。

## 2. 本次代码与脚本变更

### 2.1 Prompt 改动（方案1 + 强化版）
文件：`backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemGenerationServiceImpl.java`

- 方案1（已落地）：
  - 标题锚定 `source_title`，禁止换题意。
  - 禁止不同 source unit 收敛成泛化重名题。
  - 多任务冲突以 `source_title + normalized_body + evidence_excerpt` 为主锚点。
  - 转 OJ `stdin/stdout` 时保持任务不变。
- 二次强化（本轮新增）：
  - 明确“保持同一计算目标与输出语义，不得替换为邻近变体”。
  - 对 output-only 课件题，允许且要求做最小参数化 OJ 化改造，不再保留“无输入”最终形态。
  - 固定上界/固定数据/固定轮数任务，优先改造成 `stdin/stdout` 版本，例如 `1..10000 -> 1..N`。
  - 要求测试用例输入/输出非空，拒绝占位内容。
  - 要求 `reference_solution_code` 必须满足全部测试用例。
  - 在 user prompt 加入返回前自检：`sample==first testcase`、`3-5` 用例、解法覆盖全部用例。

### 2.2 测试回归
文件：`backend/src/test/java/com/alethicode/integration/LanguagePackInitIntegrationTest.java`

- 补充/更新 prompt 断言，确保新增约束真正进入 LLM prompt。
- 新增提取层回归：
  - `extractExamplesShouldFilterNonConvertibleFixedOutputCandidate`
  - `extractExamplesShouldKeepParameterizableOutputOnlyCandidateAsOjConvertible`
- 新增发布层回归：
  - `publishShouldAllowFrontendTestingWhenCoverageGateIsSkipped`
- 已执行并通过：
  - `generateProblemsPromptShouldAnchorTitleAndTaskSelection`
  - `generateProblemsShouldIncludeChapterMemoryNeighborUnitsAndCanonicalKcsInPrompt`

### 2.3 提取 / 校验 / 发布标准修正（已落地，待下一轮复测）
涉及文件：
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ExampleExtractionServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemValidationServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackCoverageBaselineSupport.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackPublishServiceImpl.java`
- `backend/src/main/java/com/alethicode/config/AlethicodeProperties.java`
- `backend/src/main/resources/application.yml`

- 提取层：
  - 新增 `stdin/stdout` 可转化性硬判定。
  - 如果编程题不能稳定改造成 OJ `stdin/stdout`，则直接过滤，并记录 `oj_block_reason=not_stdin_stdout_convertible`。
  - 对可参数化的 output-only 题，保留为 OJ 候选，交给生成层做最小改写。
- 校验层：
  - 删除 output-only 的豁免；所有题统一要求 `test_cases >= 3`。
  - `input_description` 若仍声明“无输入/无需输入/no input”，直接判失败。
  - `samples[0] == test_cases[0]` 继续保持强校验。
  - `reference_solution_code` 必须同时“读 stdin + 写 stdout”。
- 发布层：
  - 正式 high-risk 判定收敛为“章节存在任务信号且理论上应能 OJ 化，但最终没有产出候选题”。
  - 第一章这类“本来没有 OJ 例题”的章节，不再仅因页数多且 `oj_candidate_count=0` 就阻断发布。
  - 为前端联调新增配置开关：`alethicode.language-pack.publish.skip-coverage-gate`。
  - 该开关默认 `false`；联调时显式设为 `true` 仅跳过 coverage gate，不跳过 `problems_validated` 与 `validation_status='passed'` 要求。

### 2.4 实验脚本修正
文件：`/tmp/run_language_pack_repeat_generic.sh`

- 已修正：创建任务时 `403`（移除 `eval`，改为 curl 参数数组）。
- 本轮新增：修复两章复测时快照 SQL 路径转义问题（`ppt_files` 字段改为固定 `[]`，避免中断）。

## 3. 实验结果总览

## 3.1 两章（改前）3 次
输入：第二章 + 第六章  
目录：`/tmp/two_ppt_repeat3`

| Run | Task | Generated | Published | 备注 |
|---|---:|---:|---:|---|
| 1 | 10 | 7 | 5 | 词频类重复标题 |
| 2 | 11 | 6 | 5 | 词频类重复标题 |
| 3 | 12 | 8 | 6 | 波动明显 |

结论：`published=5/5/6`，稳定性一般。

## 3.2 两章（方案1改后）3 次
输入：第二章 + 第六章  
目录：`/tmp/two_ppt_repeat3_after_prompt_baseline`

| Run | Task | Generated | Published |
|---|---:|---:|---:|
| 1 | 13 | 7 | 6 |
| 2 | 14 | 7 | 6 |
| 3 | 15 | 6 | 6 |

结论：`published=6/6/6`，相对改前明显提升。

## 3.3 全量 7 章（方案1）3 次
输入：第一章~第七章  
目录：`/tmp/full7_after_prompt_run3`  
任务：`16/17/18`

| Run | Task | Stage终态 | Generated | Published |
|---|---:|---|---:|---:|
| 1 | 16 | failed | 51 | 0 |
| 2 | 17 | failed | 51 | 0 |
| 3 | 18 | failed | 52 | 0 |

关键现象：
- 三轮 `generate-problems` API 均返回过 `HTTP 500`，但任务实际都推进到 `problem_packages_ready`，后续仍可 `validate`。
- 三轮 `publish` 全部被 coverage gate 拦截。
- 拦截原因不是“第一章应该生成 OJ 题但没有生成”，而是**第一章本来就没有 OJ 例题**，却在无 baseline 场景下被 gate 误判为 `high-risk chapter`。

## 3.4 全量失败后的“强化 prompt -> 两章3次重跑”
输入：第二章 + 第六章  
目录：`/tmp/two_ppt_repeat3_after_prompt_tuned_v2`  
任务：`20/21/22`

| Run | Task | Generated | Published |
|---|---:|---:|---:|
| 1 | 20 | 9 | 8 |
| 2 | 21 | 7 | 7 |
| 3 | 22 | 7 | 7 |

结论：发布题量上限提升到 `8`，整体较改前更好，但仍有轻微波动。

## 4. 全量 7 章三轮重判：按“源页位点 + 题意不越界”

## 4.1 重判方法
- 第一层主锚点：`document_title + source_pages/page_range`。
- 在本次 full7 历史快照中，逐题可直接取到 `chapter + pages`；由于该数据集是一章一份 PPT，因此这里把 `chapter + pages` 视为与 `document_title + pages` 等价的历史代理键。
- 同一源页位点内允许的变化：
  - 最小参数化 OJ 化改造
  - 标题与措辞改写
  - 样例与测试用例补全
- 同一源页位点内不允许的变化：
  - 核心任务切换
  - 从一个子任务漂移到另一个子任务
  - 借邻近页面的题意替换当前页面题意
- 每道题只看以下信息是否表达同一个任务：
  - `problem_package_json.description`
  - `input_description`
  - `output_description`
  - `samples/test_cases` 体现出的输入输出目标
- 人工归并标准：
  - 核心计算任务或教学目标相同，或高度相近
  - 输入数据类型与读取目标相同、等价，或只有轻微扩展
  - 输出目标允许轻微扩展，但不能换成另一道题
- 不归并的情况：
  - 虽然同属一个章节主题，但实际解决的是不同任务
  - 输入/输出契约明显变化，已经导致求解目标发生改变
  - 一个题只是另一个题的子步骤或辅助步骤

说明：
- 本节采用的是比 `source_signature` 和标题更宽松的“题意相近”口径。
- 例如：`圆面积计算` 与 `圆周长和面积计算` 虽然输出项不同，但核心都围绕“给定半径做圆的基础计算”，因此按本报告的新口径视为同一语义题组。

## 4.2 源页位点锚点结果摘要
- 按 `chapter + pages` 这个历史代理锚点统计：
  - Run1 锚点数：`47`
  - Run2 锚点数：`46`
  - Run3 锚点数：`51`
  - 三轮并集：`81`
  - 三轮交集：`22`
  - 至少两轮出现：`41`
- 两两锚点 Jaccard：
  - Run1 vs Run2: `28 / 65 = 0.431`
  - Run1 vs Run3: `30 / 68 = 0.441`
  - Run2 vs Run3: `27 / 70 = 0.386`
- 解释：
  - 如果只看“是不是来自同一个文件/同一页段”，三轮稳定性属于中等，不如“按题意归并”后的结果高。
  - 这说明模型在一些长尾题型上会从相邻页段或同章邻近位点取材，但并不一定意味着核心题意漂移严重。

## 4.3 重判结果摘要（按题意归并）
- 三轮共归并出 `59` 个语义题组。
- 其中：
  - `28` 个题组三轮都出现
  - `9` 个题组出现在两轮
  - `22` 个题组只出现在单轮
- 换句话说：
  - `37 / 59 = 62.7%` 的语义题组至少在两轮出现
  - 三轮共同题组 `28` 个，覆盖了各轮题组总数的：
    - Run1: `28 / 40 = 70.0%`
    - Run2: `28 / 40 = 70.0%`
    - Run3: `28 / 44 = 63.6%`
- 按语义题组计算的两两 Jaccard：
  - Run1 vs Run2: `29 / 51 = 0.569`
  - Run1 vs Run3: `32 / 52 = 0.615`
  - Run2 vs Run3: `32 / 52 = 0.615`

重判结论：
- 如果以“题意相同即可视为同一题”为标准，则全量 3 次的 `generate` 结果**总体是好的**。
- 之前“交集很低”的结论主要来自 `source_signature` 口径过严，它把“同题不同标题/不同来源单元”也算成了漂移。
- 如果改用“文件/页码位点”作为主锚点，则稳定性结论会更严格，但仍可接受，不属于“明显失控”。
- 仍然存在一定题组扩散：三轮并不完全收敛为同一批题，但核心主干题型已经较稳定。

## 4.4 三轮共同出现的核心题组

| semantic_task_name | Run1 | Run2 | Run3 | 出现情况 |
|---|---|---|---|---|
| 按指令序列计算c值 | 小游戏：模拟程序 | 小游戏：模拟程序 | 小游戏：模拟程序 | 3/3 |
| 提取四位数各位数字 | 3.37 举例：个十百千获取 / 举例：个十百千获取 / 整数各位数字提取 | 举例：个十百千获取 | 举例：个十百千获取 / 四位数各位数字提取 | 3/3 |
| 数字转星期字符串 | 获取星期字符串 | 获取星期字符串 | 获取星期字符串 | 3/3 |
| 1到10000偶数和 | 计算偶数之和 | 自然数偶数之和 | 4.19 举例：自然数偶数之和 | 3/3 |
| 九九乘法表 | 九九乘法表 / 4.18 九九乘法表 | 4.18 九九乘法表 / 举例：九九乘法表 | 九九乘法表 / 举例：九九乘法表 | 3/3 |
| 圆的基础计算（面积 / 周长与面积） | 圆面积计算 / 圆周长和面积计算 | 举例：圆周长和面积计算 | 圆面积计算 / 圆周长和面积计算 | 3/3 |
| 三科平均成绩 | 平均成绩计算 | 4.1 顺序结构：计算平均成绩 | 举例：平均成绩计算 | 3/3 |
| 三边能否构成三角形 | 三角形判断（多条件判断） / 三角形判断 | 三角形判断 / 三角形判断（多条件判断） | 三角形判断（多条件判断） | 3/3 |
| 1到10猜数字 | 举例：猜数字（random+分支） | 举例：猜数字（random+分支） | 举例：猜数字（random+分支） | 3/3 |
| 用户登录校验 | 用户登录 | 举例：用户登录 | 举例：用户登录 | 3/3 |
| 1到10000自然数求和 | 自然数之和 | 自然数之和 / 举例：自然数之和 | 自然数之和 / 计算1到10000的自然数之和 | 3/3 |
| 莱布尼茨公式近似π | 举例：计算π的近似值 | 举例：计算π的近似值 | 举例：计算π的近似值 | 3/3 |
| 集合差集 | 5.1 差集 / 差集 | 5.1 差集 / 差集 | 5.5 差集 / 差集 | 3/3 |
| 集合交集 | 5.2 交集 | 5.2 交集 | 交集 | 3/3 |
| 集合并集 | 并集 | 并集 | 5.4 并集 | 3/3 |
| 集合对称差集 | 集合类型的复合赋值操作符 / 5.3 补集 / 集合的对称差集更新操作 | 5.3 对称差集 / 集合的对称差集更新 / 集合类型的复合赋值操作符 | 补集（对称差集） / 集合类型的对称差集更新运算 | 3/3 |
| A/B/F成绩统计 | 举例：成绩统计 / 成绩统计 | 举例：成绩统计 | 举例：成绩统计 | 3/3 |
| 手机通讯录管理 | 手机通讯录管理系统 | 手机通讯录管理 | 手机通讯录管理 | 3/3 |
| 快递收货人名单去重 | 统计需要取快递人员的名单 | 统计需要取快递人员的名单 | 统计收货人名单 | 3/3 |
| 循环运算器 | 运算器 | 简单计算器 | 运算器 | 3/3 |
| 英文文本词频Top10 | 词频统计代码实现 | 词频统计代码实现 | 词频统计代码实现 | 3/3 |
| 学科等级统计（严格规则） | 举例：统计学科等级水平 | 统计学科等级水平 / 举例：统计学科等级水平 | 统计学科等级水平 | 3/3 |
| 车辆区域筛选 | 统计某区域出现的车辆信息 | 统计某区域出现的车辆信息 | 统计指定区域内的车辆信息 | 3/3 |
| 全文词频统计 | 词频统计 | 6.1 词频统计 | 词频统计 | 3/3 |
| 类属性/实例属性创建与访问 | 7.1.2 创建属性并访问 | 创建属性并访问 | 创建属性并访问 | 3/3 |
| PERSON实例方法开门 | 创建实例方法并调用 | 创建实例方法并调用 | 创建实例方法并调用 | 3/3 |
| kwargs打印 | 可变长字典参数 (**kwargs) | 可变长字典参数 (**kwargs) | 7.4 可变长字典参数 (**kwargs) | 3/3 |
| 面向过程函数场景模拟 | 面向过程的编程：函数定义与调用 | 面向过程的场景模拟 | 7.19 面向过程的编程 | 3/3 |

## 4.5 只在两轮出现的题组

| semantic_task_name | Run1 | Run2 | Run3 | 出现情况 |
|---|---|---|---|---|
| 单字母凯撒加密 | 凯撒密码加密实现 / 凯撒密码加密（单个大写字母） | 凯撒密码加密 / 凯撒密码：单个字母加密 | - | 2/3 |
| 循环密码验证 | 密码验证程序 | - | 重复输入密码 | 2/3 |
| 考试成绩等级判断 | 4.2 分支结构：考试成绩判断 | - | 考试成绩判断 | 2/3 |
| 单次四则运算计算器 | 小型计算器（多分支+嵌套） | - | 举例：计算器（多分支+嵌套） / 举例：运算器 | 2/3 |
| 生日歌输出 | 7.20 生日歌 | - | 生日歌 | 2/3 |
| 合法结婚年龄判断 | - | 合法结婚年龄判断 | 合法结婚年龄判断 | 2/3 |
| 猜骰子三次机会 | - | 4.18 色子猜数字游戏 | 猜数字V1 | 2/3 |
| __init__带name实例问候 | - | "魔术"方法__init__() | "魔术"方法__init__() | 2/3 |
| *args求和 | - | 可变长元组参数 (*args) | 可变长元组参数求和 | 2/3 |

## 4.6 只在单轮出现的题组

| semantic_task_name | Run1 | Run2 | Run3 | 出现情况 |
|---|---|---|---|---|
| 按指令序列计算c值并输出a值 | 算术游戏 | - | - | 1/3 |
| 星号三角形图案 | 4.19 三角形图案 | - | - | 1/3 |
| 遇负数终止并统计正数 | 中断循环 | - | - | 1/3 |
| 按已有词频结果排序 | 关键步骤3：对单词的统计值从高到底排序 | - | - | 1/3 |
| 多形状面积计算 | 几何形状的面积 | - | - | 1/3 |
| 多态绘图演示 | 绘制图形 - 多态示例 | - | - | 1/3 |
| 全局变量遮蔽 | 示例：全局变量 | - | - | 1/3 |
| 删除字典末尾键值对 | - | 删除键-值对（3） | - | 1/3 |
| 字典get访问值 | - | 访问字典中的值 | - | 1/3 |
| 学科等级统计（简化总分规则） | - | 统计学科等级水平 - 解法一 / 举例：统计学科等级水平 - 解法二 | - | 1/3 |
| 单曲循环10遍 | - | 单曲循环10遍 | - | 1/3 |
| 函数演唱两首歌曲 | - | 7.1.1 周杰伦，不止一首歌… | - | 1/3 |
| __init__无参实例问候 | - | 7.1 "魔术"方法__init__() / 创建类的实例 | - | 1/3 |
| 创建空类实例 | - | 创建类的实例 | - | 1/3 |
| 二进制四则运算 | - | - | 二进制数运算规则 | 1/3 |
| 二进制位权展开 | - | - | 二进制数的位权展开 | 1/3 |
| 二进制转十进制 | - | - | 二进制数转换为十进制数 | 1/3 |
| 十进制转二进制 | - | - | 十进制数与二进制数的转换 | 1/3 |
| 年历输出 | - | - | 举例：年历输出 | 1/3 |
| 实例方法计算器 | - | - | 创建实例方法并调用 | 1/3 |
| 引用自定义模块输出歌词 | - | - | 引用自定义模块 | 1/3 |
| 递归计算阶乘 | - | - | 计算阶乘 | 1/3 |

## 5. `publish` 失败与 prompt 质量要分开看

## 5.1 第一章 high-risk 属于 gate 误判
- 用户已确认：**第一章确实没有 OJ 例题**。
- 但当前 coverage gate 在“无 baseline”场景下，只要章节页数足够、`oj_candidate_count=0`，就会直接视为 `high risk`。
- 因此三轮全量实验的 `published=0/0/0`，不能直接解释成 prompt 生成失败。
- 更准确的表述应该是：
  - `generate` 已经完成并得到稳定数量的候选题
  - `publish` 被现有 gate 误伤阻断

## 5.2 生成链路仍有残余问题，但不是主结论
- 全量 7 章累计校验失败类型：
  - `First sample does not match first test case`: 7
  - `Fewer than 3 test cases (got 1)`: 3
  - `Fewer than 3 test cases (got 2)`: 1
  - `Reference solution does not appear to use stdin/stdout`: 1
- 这些问题说明 prompt 仍有改进空间，但不足以推翻“按题意口径看，主干题组已经较稳定”的判断。

## 5.3 针对失败类型的修复策略（本轮已实现）
- `First sample does not match first test case`
  - 修复点：prompt 明确自检 `samples[0] == test_cases[0]`，校验层继续强制 fail-fast。
- `Fewer than 3 test cases`
  - 修复点：output-only 豁免已删除；所有题统一要求 `test_cases >= 3`，prompt 固定要求 `3..5` 条。
- `Reference solution does not appear to use stdin/stdout`
  - 修复点：prompt 与校验层都改为“必须同时读 stdin、写 stdout”，不再接受只打印固定结果的代码。
- output-only 遗留题
  - 修复点：若能最小参数化则改造成 OJ 题；若不能稳定改造成 `stdin/stdout`，则在提取阶段直接过滤。
- 第一章被误判 high-risk
  - 修复点：正式发布标准改为按任务信号与可转化性判断；前端联调可显式开启 `skip-coverage-gate`。

## 6. 重判后的最终结论

### 6.1 对 generate 效果的结论
- 只看数量：全量 3 次 `generated=51/51/52`，本来就稳定。
- 按题意归并后再看内容：
  - 三轮共同题组 `28` 个
  - 至少两轮出现的题组 `37` 个
  - 两两语义 Jaccard 在 `0.56~0.62`
- 结论：**按“题意相同即可视为同一题”的标准，全量 3 次生成效果总体是好的。**

### 6.2 对 publish 结论的修正
- 原报告把 `published=0/0/0` 直接当成 prompt 质量差，这个结论不准确。
- 更准确的说法是：
  - `publish` 失败主要来自第一章无 OJ 例题却被 gate 误判
  - 这属于发布门禁问题，不应计入 prompt 生成质量差

### 6.3 最终重判
- 原结论“全量阶段未达标”需要修正为：
  - **按 generate 语义稳定性看：效果总体较好**
  - **按全链路 publish 看：当前结果受 gate 误判影响，不能据此否定 prompt**
- 保留的风险：
  - 仍有 `22` 个题组只出现在单轮，说明长尾题型上还有一定漂移
  - 仍存在少量 testcase/sample/I-O 对齐问题

## 7. 2026-04-03 标准更新（已实现，待下一轮复测）

### 7.1 OJ 化标准
- output-only 不再作为最终可发布形态存在。
- 若原题本质上仍是可计算/可统计/可判断任务，则必须做最小参数化 OJ 化改造。
- 若原题不能稳定改造成 `stdin/stdout`，则直接过滤，不再进入候选题或发布链路。

### 7.2 校验标准
- 所有题统一要求：
  - `test_cases >= 3`
  - `test_cases <= 5`
  - 每个 `test_case.input/output` 非空
  - `samples[0] == test_cases[0]`
  - `reference_solution_code` 必须读 `stdin`、写 `stdout`
- `input_description` 若仍是“无输入/无需输入/no input”，直接判失败。

### 7.3 发布标准
- 正式标准：
  - 章节页数多且 `oj_candidate_count=0`，只有在“该章节存在明确任务信号，且理论上应可 OJ 化”时才算 `high-risk`。
  - 概念章节、纯演示章节、明确不可 `stdin/stdout` 化章节，不应仅因零候选题而阻断发布。
- 前端联调标准：
  - 可通过 `alethicode.language-pack.publish.skip-coverage-gate=true` 临时跳过 coverage gate。
  - 但仍必须处于 `problems_validated`，且至少有 `validation_status='passed'` 的题。

### 7.4 后续实验判定标准
- 主锚点：`document_title + source_pages/page_range`
- 辅助视角：题意是否越界
- 判定原则：
  - 同一页位点内允许最小参数化 OJ 化
  - 只要核心任务不变，就不因标题或措辞变化判为坏结果
  - 只有越界改题、明显遗漏、或长尾漂移显著扩大时，才判为效果差

## 8. 后续最小改进项

1. 先修 coverage gate 对“无 OJ 例题章节”的误判，再重新验证 full publish。  
2. 继续针对 testcase/sample 一致性做 prompt 强化，但不必因为本次全量的 `published=0/0/0` 就否定 generate 阶段。  
3. 下一轮 full7 复测时，优先沿用“文件/页码位点 + 题意是否越界”的双视角，而不是 `source_signature`。  

## 9. 原始数据位置

- 两章改前：`/tmp/two_ppt_repeat3`  
- 两章方案1改后：`/tmp/two_ppt_repeat3_after_prompt_baseline`  
- 全量7章三轮：`/tmp/full7_after_prompt_run3`  
- 两章强化后重跑：`/tmp/two_ppt_repeat3_after_prompt_tuned_v2`
