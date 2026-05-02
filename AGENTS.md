## OJ适用人群
非计算机专业的PY初学者

## 第一性原理

请使用第一性原理思考。你不能总是假设我非常清楚自己想要什么和该怎么得到。请保持审慎，从原始需求和问题出发，如果动机和目标不清晰，停下来和我讨论。

## 代码规范

当你需要编写任何前端代码时，强制使用 
ui-ux-pro-max skill

当你新建任何新的api时，强制符合
api-design-principles skill

当你写完任何代码后，强制使用
code-reviewer skill

任何复杂的任务，强制使用
superpower skill

## 命名规范（强制）

- Java：
  - 类名与文件名必须一致，使用 `PascalCase`（如 `JudgeServerServiceImpl.java`）。
  - 方法名、变量名使用 `camelCase`（如 `lastHeartbeat`）。
  - 常量使用 `UPPER_SNAKE_CASE`。
  - 包名全小写，禁止下划线与大写字母。
  - 数据库表名/字段名保持 `snake_case`，通过实体注解映射，不把数据库命名风格扩散到 Java 变量。
- Vue/前端：
  - 组件文件名使用 `PascalCase.vue`（如 `InfoCard.vue`、`NotFound.vue`）。
  - 组件 `name` 必须与组件语义一致，使用 `PascalCase`。
  - 普通 JS 工具模块文件名使用 `camelCase.js`（如 `simditorFileUpload.js`）。
  - 变量名、函数名统一 `camelCase`；常量统一 `UPPER_SNAKE_CASE`。
  - `i18n` 语言包文件保留标准区域格式（如 `zh-CN.js`、`en-US.js`）。
- Python：
  - 文件名、函数名、变量名使用 `snake_case`。
  - 类名使用 `PascalCase`，常量使用 `UPPER_SNAKE_CASE`。
- 通用约束：
  - 禁止同一语义出现多种拼写（如 `infoCard`/`InfoCard`/`inforCard` 混用）。
  - 重命名必须全链路同步（定义、引用、导入路径、文档）后再结束任务。
  - 不做兼容性别名，不保留旧命名并行路径，直接统一到目标命名。

## 方案规范

当需要你给出方案时必须符合以下规范：

- 不允许给出兼容性或补丁性的方案
- 不允许过度设计，保持最短路径实现且不能违反第一条要求
- 不允许自行给出我提供的需求以外的方案，例如一些兜底和降级方案，这可能导致业务逻辑偏移问题
- 必须确保方案的逻辑正确，必须经过全链路的逻辑验证
- 不要写防御性逻辑，要求failfast
- 默认只围绕用户明确提出的目标设计方案，不擅自扩展业务目标，不引入替代业务路径。
- 优先给出满足目标的最小完整方案，而不是补丁式兼容方案；但如果“最短路径”与“非补丁”冲突，应优先选择不会引入结构性错误的最小正确方案。
- 不做与当前需求无关的兜底、降级或额外分支设计；但为保证逻辑闭合，允许加入必要的输入约束、状态检查和边界保护。
- 输出方案前，按输入、处理流程、状态变化、输出、上下游影响进行链路检查；对无法验证的部分必须明确标注假设和未验证前提，不得将推测表述为已确认事实。

## Alethicode-Academy 增强路线图

> 核心理念：游戏中的每一次编程互动都是真实的学习，不是 Quiz 皮肤

### 一、真实代码执行

CodingChallenge 接入 OJ 后端 Judge，学生在游戏内写真实 Python 代码并实时判题。

- 编程挑战弹出迷你 CodeMirror 编辑器
- 代码通过 `/api/submission` 提交到 Judge
- 角色根据 AC/WA/CE/TLE 判题结果触发对应表情和台词（复用 `EVENT_EXPRESSIONS`）
- 保留 Quiz 式挑战作为低年级/入门补充

### 二、AI 导学角色化

角色不是念台词，而是背后接入 OJ 的多 Agent AI 导学。

- Nene 教学 → 调用 `problem_guide` Agent 生成审题引导
- Yoshino 纠错 → 调用 `error_diagnosis` Agent 分析学生代码的具体错误
- Kanna 总结 → 调用 `post_ac` Agent 给出代码优化方向
- Murasame 进阶 → 调用 `transfer_problem` Agent 推荐迁移题
- 角色 LLM 对话的 system prompt 融入 AI Agent 返回的分析结果

### 三、课件融入剧情

上课场景不再只有文本，而是展示真实课件。

- 「桐生先生讲课」场景拉取 `language-pack` 中对应章节的 PPT 页面
- 课后角色总结引用课件知识点（复用 RAG 检索 + `courseware_refs`）
- 可点击课件引用在游戏内嵌或新标签页查看

### 四、学情贯通

游戏进度 ⇄ OJ 平台双向同步。

- 游戏中 AC 的编程题写入 OJ 的 `submission` 表
- OJ 已有的做题数据影响角色好感度初始值和对话态度
- 使用 KC 掌握度决定章节解锁和角色线进入条件

### 五、自适应难度

编程挑战从 OJ 题库实时选题，不再是固定 29 道。

- 调用 `supplement-plan` API 根据学生薄弱 KC 推荐题目
- 失败次数多时自动降级到 faded_example（渐退示例）
- 角色的教学节奏根据掌握度动态调整

### 六、错误记忆系统

角色在自由对话和教学场景中引用学生的历史错误。

- 接入 OJ 的 `misconception_tracking` 数据
- Nene：「上次你在 for 循环的范围上写错了，记得 range(n) 是 0 到 n-1 哦～」
- Yoshino：「你的缩进问题……第三次了」
- 角色的 LLM system prompt 注入近期错误模式，生成有针对性的对话

### 实施优先级

```
Phase A（可独立完成）：一 + 四（真实判题 + 学情同步）
Phase B（依赖 Phase A）：二 + 六（AI Agent 角色化 + 错误记忆）
Phase C（可并行）：三 + 五（课件融入 + 自适应难度）
```

## 日志维护

如果涉及代码修改，每一次修改完，请把修改符合国际标准的用中文写到CHANGELOG.md中

项目里的 ReAct 默认是关闭的
