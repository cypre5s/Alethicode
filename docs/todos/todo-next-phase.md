# Alethicode 下阶段优化计划

> 基于 2026-04-17 深度对标分析确定的三个方向。
> Alethicode 核心壁垒：真实 OJ 判题 + 多 Agent 导学 + 学情画像三位一体。

---

## 行业深度对标（2026-04-17 联网调研）

### 顶尖 AI 教育产品对比

| 产品 | 定位 | 核心能力 | 用户规模 | 差异化 |
|---|---|---|---|---|
| **清华 OpenMAIC** | 多 Agent 互动课堂 | AI 老师语音讲解+白板+AI 同学辩论+自适应引擎 | 清华 700+学生实测 | 从 MOOC 到 MAIC 范式，一键文档→课堂 |
| **学而思小精龙** | 学生端 AI 伴学 | 长期记忆+动态学情诊断+情感计算+OpenClaw Skill 链 | 行业头部 | 22年教育沉淀+Agent 架构+全学科 |
| **Khan Academy Khanmigo** | AI 家教 | GPT-4 驱动，不给答案只引导，覆盖编程/数学/科学 | 全球最大教育平台 | $10K AI 学位(与 Google/Microsoft/Replit 合作) |
| **Replit Agent** | AI 编程平台 | 50+ 语言浏览器 IDE+自然语言→应用+实时协作 | 开发者平台头部 | Vibe Coding + 应用部署一体化 |
| **HUSTOJ** | 开源 OJ | PHP/C++/MySQL，26.04 版已集成 AI（via OpenClaw） | 4K stars | 中国最流行开源 OJ，但 AI 粗糙 |

### Alethicode 竞争力矩阵

| 能力维度 | Alethicode | OpenMAIC | 小精龙 | Khanmigo | Replit |
|---|---|---|---|---|---|
| 真实代码执行+判题 | **强** (OJ Judge) | 无 | 批改不执行 | 无 OJ | 有执行无判题 |
| 多 Agent 导学 | **强** (5 Agent) | **强** (AI 师生) | **强** (Skill 链) | 单 Agent | 单 Agent |
| 学情画像 | **强** (BKT/NFK/记忆衰减) | 认知建模 | **强** (大脑映射) | 有限 | 无 |
| 课件自动化 | **强** (PPT→OJ 题目) | **强** (文档→课堂) | 无 | 有限 | 无 |
| 交互形式 | 文字卡片 | 语音+白板+视频 | 语音+情感 | 文字 | 代码 IDE |
| MCP/生态协议 | **无** | OpenClaw | OpenClaw | 无 | Replit API |
| 教师端 | 运维级 | 课程管理 | 九章龙虾 | 教师面板 | 无 |

### 关键洞察

1. **Alethicode 的真实判题是独家壁垒**：OpenMAIC/小精龙/Khanmigo 都没有真正的 OJ 判题。Replit 有代码执行但不是 OJ 风格的自动评测。HUSTOJ 有 OJ 但 AI 能力粗糙。Alethicode 是唯一把"真实 OJ 判题 + 多 Agent 导学"深度绑定的平台。

2. **MCP 是 2026 年最大的生态杠杆**：110M+ 月下载，Claude/Cursor/OpenAI 原生支持。暴露 MCP Server 等于让 Alethicode 的判题能力可被任何 AI 客户端调用——这是学而思和 Khan Academy 都没做到的。Spring AI MCP SDK 已成熟（`spring-ai-starter-mcp-server-webmvc`），一个 `@McpTool` 注解就能暴露工具。

3. **教师端是商业化的关键瓶颈**：小精龙有"九章龙虾"教师端，Khan Academy 有教师面板。Alethicode 的教师端只是运维级"AI 助教工作台"，缺少班级掌握度热力图、高频错误排行、AI 干预效果追踪等教学级洞察。

4. **Agent 可见性是信任建设**：OpenMAIC 让学生看到 AI 同学发言/辩论，Claude Code 让开发者看到推理链和工具调用。Alethicode 的 ToolCallTimeline/ReasoningChain 组件已存在但利用不足。

---

## 方向 1: MCP Server — 让外部 AI 可调用 Alethicode

### 背景

MCP（Model Context Protocol）是 2026 年 AI Agent 生态的标准工具协议（月下载 110M+，Claude/Cursor/OpenAI 原生支持）。Alethicode 暴露 MCP Server 后，学生可以在 Claude Desktop、Cursor 等任何 AI 客户端中说"帮我看看这道题怎么做"，AI 会通过 MCP 调用 Alethicode 的 Agent 分析结果。

对外叙事："首个支持 MCP 协议的编程教育平台"。

### 暴露的工具（Tools）

| 工具名 | 参数 | 返回 | 数据来源 |
|---|---|---|---|
| `submit_code` | problem_id, code, language | 判题结果（AC/WA/CE/TLE + 错误详情） | `SubmissionService` → Judge |
| `get_learner_profile` | user_id | 掌握度/薄弱 KC/错误模式/frustration/confidence | `LearnerProfileProjector.project()` |
| `search_courseware` | query, language_pack_id | 课件页面匹配列表 | `CoursewareRetrievalService` |
| `get_problem_guide` | problem_id | 审题引导（plain_task/input_translation/approach_direction） | `GuideAgent` or inline guide |
| `diagnose_error` | submission_id | 错误诊断（root_cause/fix_direction/reasoning_chain） | `DiagnosticsAgent` |
| `recommend_problem` | user_id | 基于薄弱 KC 推荐的题目列表 | `BeginnerSupplementPlannerService` |

### 暴露的资源（Resources）

| URI | 返回 |
|---|---|
| `problems/{id}` | 题目详情（title/description/input/output/samples） |
| `submissions/{id}` | 提交详情 + Judge 结果 |
| `learner/{id}/mastery` | KC 掌握度快照 |

### 技术方案

Spring AI MCP Server Starter 已成熟，`@McpTool` 注解一行代码暴露工具。

**Maven 依赖**：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**配置** (`application.yml`)：
```yaml
spring:
  ai:
    mcp:
      server:
        name: alethicode-education
        version: 1.0.0
        description: "AI-powered programming education platform with real OJ judge"
```

**代码示例**（复用已有 Service 层）：
```java
@Service
public class AlethicodeMcpTools {

    private final SubmissionService submissionService;
    private final LearnerProfileProjector profileProjector;
    private final CoursewareRetrievalService coursewareService;

    @McpTool(description = "Submit Python code to real OJ judge for a problem. Returns AC/WA/CE/TLE with error details.")
    public Map<String, Object> submitCode(
        @McpToolParam(description = "Problem ID", required = true) Long problemId,
        @McpToolParam(description = "Source code", required = true) String code,
        @McpToolParam(description = "Language: Python3/C/C++/Java", required = true) String language) {
        // 复用 SubmissionService 判题链路
    }

    @McpTool(description = "Get learner's knowledge mastery, weak KCs, error patterns, and frustration level")
    public Map<String, Object> getLearnerProfile(
        @McpToolParam(description = "User ID", required = true) Long userId) {
        // 复用 LearnerProfileProjector.project()
    }

    @McpTool(description = "Search courseware pages by keyword or concept")
    public List<Map<String, Object>> searchCourseware(
        @McpToolParam(description = "Search query") String query,
        @McpToolParam(description = "Language pack ID") Long languagePackId) {
        // 复用 CoursewareRetrievalService
    }
}
```

- **传输**：SSE over HTTP（默认 `/sse` 端点），兼容所有 MCP 客户端
- **安全**：API Key 认证 + 每工具独立 rate limit（submit_code 严格限制）
- **注册**：Spring AI 自动发现 `@McpTool` 注解，自动生成 JSON schema

### 实施步骤

1. 添加 `spring-ai-starter-mcp-server-webmvc` 依赖到 `pom.xml`
2. 新建 `AlethicodeMcpTools.java`，用 `@McpTool` 注解包装 6 个已有 Service 方法
3. 新建 `AlethicodeMcpResources.java`，暴露 3 个资源
4. 配置 `application.yml` 中 MCP server 参数
5. API Key 认证过滤器
6. 测试：用 Claude Desktop 或 MCP Inspector 连接验证

### 预估工期

2-3 周（含测试和文档）

---

## 方向 2: 教师 Command Center

### 背景

当前"AI 助教工作台"是运维级面板（Agent 调用次数、失败率、Trace 回放）。教师需要的是教学级洞察："我班上哪些学生在哪些知识点上卡住了"、"AI 帮了哪些忙、效果怎么样"。

对标：学而思"九章龙虾"教师端、OpenMAIC 课程管理。

### 面板设计

#### 2.1 班级掌握度热力图

- **布局**：横轴 = KC（知识点），纵轴 = 学生姓名，每格颜色 = mastery 值
- **颜色**：红(< 0.3) → 黄(0.3-0.7) → 绿(> 0.7)
- **交互**：点击某格跳转到该学生在该 KC 上的提交历史
- **数据源**：`learner_kc_mastery` 表，按 `classroom_id` + `language_pack_id` 过滤
- **SQL**：
```sql
SELECT u.username, akc.name as kc_name, lkm.mastery_value
FROM learner_kc_mastery lkm
JOIN "user" u ON u.id = lkm.user_id
JOIN ai_knowledge_component akc ON akc.id = lkm.kc_id
JOIN classroom_member cm ON cm.user_id = lkm.user_id
WHERE cm.classroom_id = ? AND lkm.language_pack_id = ?
ORDER BY u.username, akc.display_order
```

#### 2.2 高频错误排行

- **布局**：按 KC 分组的 misconception 排行，每个 KC 下展示 top-3 错误模式
- **数据源**：`ai_learning_event` (event_type = 'misconception_detected_ast') + `ai_learner_notebook`
- **SQL**：
```sql
SELECT akc.name as kc_name,
       ale.extra_data->>'detector_name' as error_pattern,
       count(*) as frequency
FROM ai_learning_event ale
JOIN ai_problem_kc_mapping apkm ON apkm.problem_id = ale.problem_id
JOIN ai_knowledge_component akc ON akc.id = apkm.kc_id
WHERE ale.event_type IN ('misconception_detected_ast', 'frustration_detected')
  AND ale.created_at > now() - interval '30 day'
GROUP BY akc.name, ale.extra_data->>'detector_name'
ORDER BY frequency DESC
LIMIT 20
```

#### 2.3 AI 干预效果追踪

- **布局**：表格展示"AI 介入前后的提交成功率变化"
- **逻辑**：找到 `ai_workflow_event` 中 ERROR_FEEDBACK 事件，对比介入前后 submission 的 AC 率
- **数据源**：`ai_workflow_event` JOIN `submission` (按 user_id + problem_id + 时间窗口)
- **SQL**：
```sql
WITH interventions AS (
    SELECT awe.session_id, aws.user_id, aws.problem_id,
           awe.created_at as intervention_time
    FROM ai_workflow_event awe
    JOIN ai_workflow_session aws ON aws.session_id = awe.session_id
    WHERE awe.event_type = 'phase_output'
      AND awe.event_data->>'phase' = 'ERROR_FEEDBACK'
      AND awe.created_at > now() - interval '30 day'
)
SELECT i.user_id,
       count(CASE WHEN s.result = 0 AND s.create_time > i.intervention_time THEN 1 END) as ac_after,
       count(CASE WHEN s.result != 0 AND s.create_time < i.intervention_time THEN 1 END) as fail_before
FROM interventions i
JOIN submission s ON s.user_id = i.user_id AND s.problem_id = i.problem_id
GROUP BY i.user_id
```

### 前端实现

- 在 `ObservabilityDashboard.vue` 中新增 Tab"班级洞察"
- 班级选择器：从 `classroom` 列表中选择
- 热力图：使用 ECharts heatmap 组件
- 错误排行：使用 ECharts bar chart
- 干预效果：使用 el-table + 折线图

### 后端实现

- 新建 `AdminCourseInsightController` 中增加 3 个端点（已存在，需扩展）：
  - `GET /api/admin/insight/mastery-heatmap?classroom_id=X&language_pack_id=Y`
  - `GET /api/admin/insight/error-ranking?days=30`
  - `GET /api/admin/insight/intervention-effect?days=30`

### 预估工期

1-2 周

---

## 方向 3: Agent 可见性增强

### 背景

Alethicode 已有 `ToolCallTimeline.vue`、`ReasoningChain.vue`、`EvidenceRefs.vue` 三个可见性组件，但只在 ERROR_FEEDBACK + ReAct 开启时才有内容。需要扩展到所有教学事件。

对标：OpenMAIC 的 AI 同学发言/举手/辩论全可见；Claude Code 的推理链透明。

### 具体改动

#### 3.1 推理链展示扩展

**现状**：只有 DiagnosticsAgent 在 ReAct 模式下返回 `reasoning_chain`
**目标**：所有 Agent 都返回结构化推理链

- `GuideAgent`：返回 `reasoning_chain: [{step:"分析",content:"题目涉及..."},{step:"检索",content:"课件 P12..."},{step:"引导",content:"建议先..."}]`
- `MetacognitiveAgent`：返回 `reasoning_chain: [{step:"回顾",content:"你的解法..."},{step:"反思",content:"发现你..."}]`
- `TransferAgent`：返回 `reasoning_chain: [{step:"关联",content:"这道题和..."},{step:"变式",content:"换个场景..."}]`

**改动**：在各 Agent 的 system prompt 中加入 `reasoning_chain` 输出要求

#### 3.2 工具调用时间线默认展示

**现状**：`ToolCallTimeline.vue` 存在但只在有 `tool_calls` 数据时展示
**目标**：在 UnifiedAgentPanel 中，只要 Agent 输出包含 `tool_calls`，自动展示时间线

**改动**：
- `UnifiedAgentPanel.vue` 中检测 node_outputs 是否包含 `tool_calls`，有则渲染 `ToolCallTimeline`
- 当前只有 DiagnosticsAgent 返回 `tool_calls`，ReAct 默认开启后此组件会有内容

#### 3.3 课件引用卡片

**现状**：`EvidenceRefs.vue` 存在，可展示课件引用
**目标**：Agent 回复中显式引用课件页码，前端渲染为可点击的引用卡片

**改动**：
- Agent prompt 中要求输出 `courseware_refs: [{page: 12, title: "for 循环", snippet: "..."}]`
- `ProblemGuideCard.vue` 已有 `courseware_refs` 字段处理，确保其他卡片也支持
- `EvidenceRefs.vue` 中课件引用支持跳转到课件预览页

### 预估工期

1 周

---

## 实施优先级

| 序号 | 方向 | 预估 | 价值 | 建议 |
|---|---|---|---|---|
| 1 | Agent 可见性增强 | 1 周 | 立即可见，学生体验提升 | 先做 |
| 2 | 教师 Command Center | 1-2 周 | 支撑商业化和院校合作 | 紧随 |
| 3 | MCP Server | 2-3 周 | 行业级差异化标签 | 第三 |

---

## 不建议做的事

- 不建议现在引入语音交互（TTS/ASR 需要音频基础设施，投入产出比低）
- 不建议引入 A2A 协议（生态不成熟，仅 2400 个 repo vs MCP 25000+）
- 不建议重建 L1/L2 自主度系统（已删除，有理由不重做）
- 不建议引入 LangChain/CrewAI 等外部框架（已有完整自研 Agent 栈）
