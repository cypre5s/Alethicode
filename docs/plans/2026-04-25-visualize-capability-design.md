# Visualize Capability（教学可视化）设计

> **文档编号**：ALETH-PLAN-2026-0425-P2
> **关联调研**：[`docs/reports/2026-04-25-deeptutor-inspiration-survey.md`](../reports/2026-04-25-deeptutor-inspiration-survey.md)
> **关联设计**：[`docs/plans/2026-04-25-persistent-memory-layer-design.md`](2026-04-25-persistent-memory-layer-design.md)
> **优先级**：P2（与 P3 并行，弱依赖 P1）
> **作者**：AI Coding Assistant
> **创建日期**：2026-04-25

> **一句话目标**：让 5 教学角色在解释概念时 **能主动画图**——通过 Mermaid（流程图 / 数据结构图 / 时序图）/ Chart.js（复杂度对比 / 学习进度）/ 内联 SVG（数据流 / 内存示意），把"看不见的执行过程"变成"看得见的教学画面"。

---

## 目录

- [一、设计动机](#一设计动机)
- [二、现状盘点](#二现状盘点)
- [三、差距分析](#三差距分析)
- [四、设计目标与非目标](#四设计目标与非目标)
- [五、整体架构](#五整体架构)
- [六、详细设计](#六详细设计)
- [七、可视化场景目录（VisualizeIntent）](#七可视化场景目录visualizeintent)
- [八、契约示例](#八契约示例)
- [九、全链路时序](#九全链路时序)
- [十、工作量评估](#十工作量评估)
- [十一、验收标准](#十一验收标准)
- [十二、风险与缓解](#十二风险与缓解)
- [十三、第一性原理自检](#十三第一性原理自检)
- [附录 A：Mermaid 子集白名单](#附录-amermaid-子集白名单)
- [附录 B：Chart.js 配置 schema](#附录-bchartjs-配置-schema)
- [附录 C：SVG 沙箱白名单](#附录-csvg-沙箱白名单)

---

## 一、设计动机

### 1.1 教学场景痛点

非计算机专业 Python 初学者最大的认知障碍：**"代码在脑子里跑不起来"**。

具体表现：

1. **for 循环边界**：学生不知道 `range(n)` 实际跑了哪几个 i；
2. **递归调用**：学生不知道函数栈是怎么展开 / 收缩的；
3. **列表 in-place 修改**：学生不知道 `lst.append(x)` 与 `lst = lst + [x]` 在内存中的差别；
4. **算法复杂度**：学生不能直观感受 `O(n)` vs `O(n²)` 的差距；
5. **数据结构**：链表 / 树 / 图 / 栈 / 队列的状态变化无图可看；
6. **数据流**：函数调用链、变量传递、返回值流向看不见。

**1000 字解释 ≠ 1 张动图。**

### 1.2 第一性原理

**让学生"看见"概念的最快路径，是让 LLM 把"我现在脑子里在画什么图"用结构化语言（Mermaid / Chart / SVG）输出，前端原生渲染。**

这与 OJ 已有的 `execution_trace_explainer`（运行轨迹真实抽取）是**互补关系**：

- `execution_trace_explainer`：基于真实代码 + Tracer 抽取**事实**（做了什么）；
- `Visualize Capability`：基于 LLM 推理生成**示意**（应该是什么样子）。

两者**不重叠、不替换、各自必要**。

---

## 二、现状盘点

### 2.1 已具备能力（不重做，仅复用）

| 模块 | 现状 |
|---|---|
| `execution_trace_explainer` 卡片 | 已上线（`docs/todos/todo-ai-variable-runtime-visualization.md`，2026-03-29） |
| `PythonExecutionTraceService` + `SimplePythonTracer` | 已实现，限 Python3 顺序 / 分支 / 循环 |
| `CardType` | 9 种枚举（含 `EXECUTION_TRACE_EXPLAINER`） |
| `AiModelGateway.callForJson()` | 已具备结构化输出能力 |
| `tutor_graph` 节点 LLM 调用 | 已统一通过 `LlmClient.generate_json` |
| 前端 `UnifiedAgentPanel.vue` | 已有卡片渲染容器 |
| 前端 Markdown 渲染 | `simditor` 已就绪，可直接复用其代码块渲染 |

### 2.2 现有渲染能力盘点

| 内容类型 | 是否能渲染 | 备注 |
|---|---|---|
| 纯文本 | ✅ | 卡片正文 |
| Markdown | ✅ | simditor + KaTeX |
| 代码高亮 | ✅ | Prism / hljs |
| 数学公式 | ✅ | KaTeX |
| 表格 | ✅ | Markdown table |
| 图片（URL） | ✅ | img 标签 |
| **Mermaid** | ❌ | 不能直接渲染 |
| **Chart.js** | ❌ | 不能直接渲染 |
| **内联 SVG（受信任）** | ❌ | 出于 XSS 默认禁用 |

### 2.3 LLM 端能力盘点

| 能力 | 状态 |
|---|---|
| LLM 输出 Mermaid 文本 | ✅（任意主流模型） |
| LLM 输出 Chart.js JSON | ✅（结构化输出） |
| LLM 输出 SVG | ✅（但需做沙箱） |
| LLM 输出 Manim 脚本 | ✅ 但运行时太重，**本设计不引** |

---

## 三、差距分析

按"教学影响"由强到弱：

### G1. 没有 Mermaid 渲染能力

学生 / 教师场景：解释循环、解释递归调用栈、解释类继承结构、解释状态机时机
当前：只能用文字描述
影响：高频教学场景全部依赖学生"想象力"

### G2. 没有 Chart.js 渲染能力

学生场景：复杂度对比、学习进度曲线、KC 掌握度雷达图
当前：只能贴文字数据
影响：量化对比无法直观呈现

### G3. 没有受信任 SVG 渲染能力

学生场景：链表节点指针、树形结构、内存示意、数据流箭头
当前：完全没有
影响：自定义图无法表达

### G4. LLM 不知道何时该画图

当前：节点 SYSTEM_PROMPT 只让 LLM 输出文本字段
影响：LLM 即使想画图也没有输出通道

### G5. 5 角色无法主动调用 Visualize 工具

当前：5 角色的 prompt 都不知道有 visualize 这个 capability
影响：教学时机最该出图时（"小明你看一下，range(5) 是这样跑的"）反而出不了图

### G6. 学生不能主动请求"画一下"

当前：UnifiedAgentPanel 没有"画图"按钮
影响：学生主动学习路径少了一种

---

## 四、设计目标与非目标

### 4.1 设计目标（必达）

| # | 目标 | 验收方式 |
|---|---|---|
| O1 | 新增 `VisualizeCapability` 服务，能产出 Mermaid / Chart / SVG 三类可视化 | 单测覆盖三类输出 |
| O2 | 新增 `CardType.VISUALIZE` 卡片，渲染在 `UnifiedAgentPanel` | 前端 E2E |
| O3 | 5 教学角色节点的 SYSTEM_PROMPT 显式告知"建议可视化时输出 visualize_intent 字段" | 节点单测 |
| O4 | 学生可主动点"画一下"按钮，触发 Visualize 调用 | 前端交互 + API |
| O5 | 输出 Mermaid / Chart / SVG 后必须做语法 / schema 校验，校验失败 failfast | 校验单测 |
| O6 | 限定 Mermaid 子集 + Chart.js 类型 + SVG 沙箱白名单（防 XSS / 防滥用） | 安全单测 |

### 4.2 非目标（明确不做）

| 非目标 | 原因 |
|---|---|
| 不引入 Manim / 视频生成 | 资源消耗高、延迟高、教学场景不必要 |
| 不引入 PlantUML | Mermaid 已覆盖主流场景 |
| 不引入 D3.js 等通用画图库 | 教学场景不需要任意可编程图，提升认知负担 |
| 不允许任意 HTML 嵌入 | XSS 风险 |
| 不允许 SVG 内嵌 `<script>` / `<foreignObject>` | XSS 风险 |
| 不在 Visualize Capability 里做"运行真实代码出图" | 那是 `execution_trace_explainer` 的职责 |
| 不允许 LLM 自由命名图类型 | 必须落到 VisualizeIntent 枚举 |

---

## 五、整体架构

### 5.1 数据流向

```
                                       ┌──────────────────────────┐
                                       │  前端 UnifiedAgentPanel   │
                                       │   - VisualizeCard 渲染    │
                                       │   - "画一下"按钮入口      │
                                       └──────────▲───────────────┘
                                                  │ HTTP / SSE
                            ┌─────────────────────┴───────────────────────┐
                            │   AITutorController                          │
                            │     POST /ai-tutor/visualize/inline          │
                            │     (学生主动请求 / 工具回调)                 │
                            └─────────────────────┬───────────────────────┘
                                                  │
                            ┌─────────────────────▼───────────────────────┐
                            │   VisualizeCapabilityService (新增)          │
                            │     - VisualizeIntent (enum)                 │
                            │     - VisualizeRequest                       │
                            │     - VisualizeResult (Mermaid/Chart/SVG)    │
                            │     - 校验：MermaidValidator,                 │
                            │              ChartConfigValidator,           │
                            │              SvgSanitizer                    │
                            └─────────────────────┬───────────────────────┘
                                                  │
                                                  ▼
                                ┌─────────────────────────────────────┐
                                │  AiModelGateway.callForJson         │
                                │   (with Visualize SYSTEM_PROMPT)    │
                                └─────────────────────────────────────┘

                            tutor_graph 调用方向
                            ───────────────────
                            5 角色节点 → state["visualize_intent"]
                                ↓
                            projection.py
                                ↓
                            HTTP POST /internal/ai-tutor/visualize/dispatch
                                ↓
                            VisualizeCapabilityService (同上)
                                ↓
                            写入 ai_tutor_card (CardType.VISUALIZE)
```

### 5.2 新增组件清单

| 组件 | 类型 | 责任 |
|---|---|---|
| `VisualizeIntent` | Java enum | 可视化类型枚举：FOR_LOOP_TRACE / RECURSION_STACK / DATA_STRUCTURE_STATE / COMPLEXITY_COMPARE / KC_MASTERY_RADAR / MEMORY_LAYOUT / DATA_FLOW / FLOWCHART |
| `VisualizeCapabilityService` | Java service | 入口：dispatch(VisualizeRequest) → VisualizeResult |
| `MermaidValidator` | Java util | Mermaid 文本子集校验 |
| `ChartConfigValidator` | Java util | Chart.js 配置 JSON schema 校验 |
| `SvgSanitizer` | Java util | SVG 白名单沙箱（基于 jsoup） |
| `VisualizeCard` 内容契约 | DTO | `{ intent, format, payload, alt_text, source_role }` |
| `CardType.VISUALIZE` | 枚举值 | 加到 `CardType` |
| `AITutorController.inlineVisualize()` | REST | `POST /ai-tutor/visualize/inline`（学生主动） |
| `InternalAITutorToolService.dispatchVisualize()` | 内部 API | `POST /internal/ai-tutor/visualize/dispatch`（节点回调） |
| tutor_graph `nodes/visualize.py` | Python node | 在节点 LLM 输出含 `visualize_intent` 时回调 Java |
| `services/tutor-graph/app/nodes/prompts.py` Visualize 注入 | Python | 给 5 角色节点 SYSTEM_PROMPT 增加 visualize_intent 输出说明 |
| `VisualizeRenderer.vue` | 前端组件 | 路由 mermaid / chart / svg 三个子组件 |
| `MermaidRenderer.vue` | 前端组件 | 包装 mermaid.js |
| `ChartRenderer.vue` | 前端组件 | 包装 Chart.js 或 vue-chartjs |
| `SvgRenderer.vue` | 前端组件 | 直接渲染受信任 SVG |
| `useVisualizeApi.js` | 前端 composable | 学生主动触发可视化 |

---

## 六、详细设计

### 6.1 VisualizeIntent 枚举

```java
public enum VisualizeIntent {
    FOR_LOOP_TRACE("for_loop_trace",
        "for 循环迭代过程", "mermaid"),
    RECURSION_STACK("recursion_stack",
        "递归调用栈", "mermaid"),
    DATA_STRUCTURE_STATE("data_structure_state",
        "数据结构状态（链表/树/栈/队列）", "svg"),
    COMPLEXITY_COMPARE("complexity_compare",
        "复杂度对比", "chart"),
    KC_MASTERY_RADAR("kc_mastery_radar",
        "知识点掌握度雷达", "chart"),
    MEMORY_LAYOUT("memory_layout",
        "内存示意（变量/对象/引用）", "svg"),
    DATA_FLOW("data_flow",
        "数据流向（函数调用 / 参数传递）", "mermaid"),
    FLOWCHART("flowchart",
        "通用流程图", "mermaid");

    private final String key;
    private final String label;
    private final String defaultFormat;  // mermaid / chart / svg

    public static Optional<VisualizeIntent> fromKey(String key) { ... }
}
```

> **关键约束**：LLM 不能任意命名 intent。必须落到上面 8 种之一。

### 6.2 VisualizeRequest / VisualizeResult

```java
public record VisualizeRequest(
    VisualizeIntent intent,
    String prompt,                     // 学生 / 节点的描述（"画 range(5) 的迭代"）
    Map<String, Object> contextHints,  // 可选：current_kcs / problem_statement / variables
    Long userId,
    Long problemId,
    String sessionId,
    String sourceRole                  // "student" / "Nene" / "Yoshino" / "Kanna" / "Murasame" / "Tsukinaga"
) {}

public record VisualizeResult(
    VisualizeIntent intent,
    String format,                     // "mermaid" / "chart" / "svg"
    String payload,                    // 实际可渲染内容
    String altText,                    // 无障碍文本
    Map<String, Object> debug          // schema validate 详情、token 使用量
) {}
```

### 6.3 VisualizeCapabilityService

```java
@Service
public class VisualizeCapabilityService {

    private final AiModelGateway aiModelGateway;
    private final MermaidValidator mermaidValidator;
    private final ChartConfigValidator chartConfigValidator;
    private final SvgSanitizer svgSanitizer;

    public VisualizeResult dispatch(VisualizeRequest req) {
        String systemPrompt = buildSystemPrompt(req.intent());
        String userPrompt = buildUserPrompt(req);
        Map<String, Object> raw = aiModelGateway.callForJson(systemPrompt, userPrompt);
        String format = (String) raw.getOrDefault("format", req.intent().defaultFormat());
        String payload = (String) raw.get("payload");
        String alt = (String) raw.getOrDefault("alt_text", "");

        // 校验：失败立即抛 VisualizeValidationException（failfast）
        switch (format) {
            case "mermaid" -> mermaidValidator.validate(payload, req.intent());
            case "chart"   -> chartConfigValidator.validate(payload);
            case "svg"     -> payload = svgSanitizer.sanitize(payload);
            default        -> throw new VisualizeValidationException("unknown format: " + format);
        }
        return new VisualizeResult(req.intent(), format, payload, alt, ...);
    }

    private String buildSystemPrompt(VisualizeIntent intent) {
        return switch (intent) {
            case FOR_LOOP_TRACE     -> SYSTEM_PROMPT_FOR_LOOP_TRACE;
            case RECURSION_STACK    -> SYSTEM_PROMPT_RECURSION_STACK;
            case DATA_STRUCTURE_STATE -> SYSTEM_PROMPT_DATA_STRUCT;
            ...
        };
    }
}
```

**关键约束**：

- 校验失败立即抛异常（failfast），不写降级逻辑；
- 上游捕获 `VisualizeValidationException` 后写入卡片 `status=failed + failure_reason`；
- 不重试（避免无限调 LLM）。

### 6.4 SYSTEM_PROMPT 模板（每种 intent 一份）

#### FOR_LOOP_TRACE（Mermaid）

```
你是教学可视化生成器。请基于学生的描述，生成 for 循环迭代过程的 Mermaid 图。

要求：
- 必须使用 Mermaid flowchart 子集（仅允许：flowchart TD / LR、节点 [text]、箭头 -->）
- 不允许 subgraph、不允许 click、不允许任何 HTML 嵌入
- 节点必须按迭代顺序排列：开始 → i=0 → i=1 → ... → 结束
- 每个迭代节点显示：i 值 + 当次循环体的简短行为
- ≤ 12 个节点（超过 12 用 "..." 节点代替中段）
- alt_text 用一句中文描述图片含义

输出 JSON：
{
  "format": "mermaid",
  "payload": "flowchart TD\n  start([开始]) --> i0[i=0\\nprint(0)]\n  ...",
  "alt_text": "for i in range(5) 的 5 次迭代过程"
}
```

#### COMPLEXITY_COMPARE（Chart.js）

```
你是教学可视化生成器。请生成 Chart.js v4 折线图配置，对比 2-3 种算法的时间复杂度。

要求：
- 必须输出 Chart.js v4 配置 JSON
- type 仅允许：line / bar / radar
- 横轴：n 取值（如 1,10,100,1000,10000）
- 纵轴：操作次数（按公式精确计算，不要拟合）
- datasets 仅允许 2-3 条
- 不允许 plugins.tooltip 自定义函数

输出 JSON：
{
  "format": "chart",
  "payload": "{\"type\":\"line\",\"data\":{...},\"options\":{...}}",
  "alt_text": "O(n) vs O(n²) 的复杂度对比"
}
```

#### DATA_STRUCTURE_STATE（SVG）

```
你是教学可视化生成器。请生成内联 SVG 表示数据结构状态。

要求：
- 必须输出合法 SVG（width/height 必填，viewBox 推荐）
- 仅允许标签：svg, g, rect, circle, ellipse, line, path, polyline, polygon, text, defs, marker
- 仅允许属性：x, y, width, height, cx, cy, r, rx, ry, d, points, x1, x2, y1, y2,
  stroke, stroke-width, fill, font-size, font-family, text-anchor, transform,
  marker-end, viewBox, xmlns, id, class
- 严禁：script, foreignObject, use(href), animate, animateTransform, on* 事件属性
- 禁止内联 style="..."（用属性表达）
- 节点用 rect+text，连线用 path+marker
- 整张图 ≤ 800x600，颜色不超过 5 种

输出 JSON：
{
  "format": "svg",
  "payload": "<svg xmlns=\"http://www.w3.org/2000/svg\" ...>...</svg>",
  "alt_text": "链表 1->2->3 在 append(4) 后的状态"
}
```

### 6.5 校验器实现要点

#### 6.5.1 MermaidValidator

| 检查项 | 实现 |
|---|---|
| 必须以 `flowchart TD` / `flowchart LR` / `sequenceDiagram` / `stateDiagram-v2` / `classDiagram` 开头 | 正则 |
| 行总数 ≤ 50 | 行数 |
| 节点总数 ≤ 30 | 计数 |
| 不允许 `subgraph` / `click` / `style` / `linkStyle` | 黑名单正则 |
| 不允许 `<script` / `javascript:` / `data:` URI | 黑名单 |
| 节点文本不允许 HTML | 正则 |

#### 6.5.2 ChartConfigValidator

基于 JSON Schema 校验：

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["type", "data"],
  "properties": {
    "type": { "enum": ["line", "bar", "radar"] },
    "data": {
      "type": "object",
      "required": ["labels", "datasets"],
      "properties": {
        "labels": { "type": "array", "maxItems": 20 },
        "datasets": {
          "type": "array",
          "minItems": 1,
          "maxItems": 3,
          "items": {
            "type": "object",
            "required": ["label", "data"],
            "properties": {
              "label": { "type": "string", "maxLength": 50 },
              "data": { "type": "array", "maxItems": 20 }
            }
          }
        }
      }
    },
    "options": {
      "type": "object",
      "not": {
        "anyOf": [
          {"required": ["plugins.tooltip.callbacks"]},
          {"required": ["onClick"]},
          {"required": ["onHover"]}
        ]
      }
    }
  }
}
```

#### 6.5.3 SvgSanitizer

基于 jsoup `Cleaner` + 自定义白名单（见附录 C）。

实现要点：

- 解析 SVG → DOM；
- 遍历所有元素，不在白名单的标签全部删除；
- 遍历所有属性，不在白名单的属性全部删除；
- 检测 `xlink:href` / `href` 是否含 `javascript:` 或 `data:`，含则删除；
- 检测属性值是否含 `<script` / `onload`，含则删除；
- 检测整图大小 ≤ 800x600；
- 序列化输出。

### 6.6 tutor_graph 节点改动

#### 6.6.1 节点 SYSTEM_PROMPT 增加 visualize_intent 字段

以 `diagnosis.py` 为例：

```python
SYSTEM_PROMPT_BASE = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生提交了错误的代码。请分析错误原因。

要求：
- 不给完整可提交代码
- ...
- **建议可视化时**：输出 visualize_intent 字段，可选值：
  - "for_loop_trace"：当错误与循环边界 / 迭代次数相关
  - "recursion_stack"：当错误与递归相关
  - "data_structure_state"：当错误与列表 / dict / 字符串状态变化相关
  - "memory_layout"：当错误与变量赋值 / 引用 / in-place 修改相关
  - 留空表示不需要可视化

- 输出 JSON 字段：root_cause, ..., **visualize_intent**, **visualize_prompt**

visualize_prompt 是给可视化生成器的描述，例如 "画 range(5) 的 5 次迭代"。
"""
```

#### 6.6.2 新增 `nodes/visualize_dispatch.py`（节点级回调）

不是独立节点，是 projection 阶段的副作用：

```python
# services/tutor-graph/app/nodes/projection.py

async def project_to_card(state, java_client):
    node_outputs = state.get("node_outputs", {})
    for node_name, output in node_outputs.items():
        intent = output.get("visualize_intent")
        if intent and output.get("visualize_prompt"):
            try:
                viz_card = await java_client.dispatch_visualize(
                    intent=intent,
                    prompt=output["visualize_prompt"],
                    context_hints={
                        "current_kcs": output.get("related_kcs", []),
                        "node_name": node_name,
                    },
                    user_id=state["user_id"],
                    problem_id=state["problem_id"],
                    session_id=state["session_id"],
                    source_role=output.get("mentor_role", "AI"),
                )
                output["visualize_card_id"] = viz_card["id"]
            except Exception as e:
                # failfast：可视化失败不影响主卡片，但要标记
                output["visualize_failed"] = str(e)
```

### 6.7 学生主动触发

#### 6.7.1 API

```http
POST /ai-tutor/visualize/inline
Request:
{
  "session_id": "S001",
  "problem_id": 7,
  "intent": "for_loop_trace",
  "prompt": "画一下 range(5) 是怎么跑的"
}

Response 200:
{
  "card_id": "C-V-001",
  "intent": "for_loop_trace",
  "format": "mermaid",
  "payload": "flowchart TD\n  start([开始]) --> i0[i=0\\nprint(0)]\n  ...",
  "alt_text": "for i in range(5) 的 5 次迭代过程"
}

Response 422 (校验失败 / failfast):
{
  "ok": false,
  "error_code": "VISUALIZE_VALIDATION_FAILED",
  "reason": "Mermaid contains forbidden 'subgraph' keyword"
}
```

#### 6.7.2 前端入口

`UnifiedAgentPanel.vue` 在卡片底部加按钮组：

| 按钮 | 触发 |
|---|---|
| 🔁 让我看一下循环过程 | `intent=for_loop_trace`，`prompt=用当前题代码画循环` |
| 🌳 画一下递归调用栈 | `intent=recursion_stack` |
| 📊 复杂度对比 | `intent=complexity_compare` |
| 🗂 数据结构现在是什么样 | `intent=data_structure_state` |
| 🎯 我的知识点掌握度 | `intent=kc_mastery_radar`（基于 LearnerState） |

按钮的可见性根据当前题 KC + 当前 Phase 推断（避免在不相关阶段塞太多按钮）。

### 6.8 前端渲染组件

#### 6.8.1 `VisualizeRenderer.vue`

```html
<template>
  <div class="visualize-card" :data-intent="intent">
    <div class="visualize-header">
      <span class="visualize-title">{{ title }}</span>
      <span class="visualize-source">来自 {{ sourceRole }}</span>
    </div>
    <component
      :is="rendererFor(format)"
      :payload="payload"
      :alt-text="altText"
    />
    <div v-if="error" class="visualize-error">
      可视化生成失败：{{ error }}
    </div>
  </div>
</template>
```

#### 6.8.2 `MermaidRenderer.vue`

依赖：`mermaid` (npm 包)

实现要点：
- mounted 时调用 `mermaid.render(id, payload)`；
- 渲染失败显示 alt_text + 错误提示；
- 不允许 click 事件（已在校验阶段禁掉，但前端再防一层）。

#### 6.8.3 `ChartRenderer.vue`

依赖：`vue-chartjs` + `chart.js`

实现要点：
- 解析 payload JSON；
- 用 `<Line>` / `<Bar>` / `<Radar>` 之一渲染；
- 失败显示 alt_text。

#### 6.8.4 `SvgRenderer.vue`

实现要点：
- payload 已在后端 sanitized，前端用 `v-html` 直接渲染（**前提是后端校验严格**）；
- 包一层 `<div class="svg-container">` 控制最大尺寸；
- 不允许 SVG 内部脚本（已在后端校验）。

### 6.9 数据库

无新增表。`ai_tutor_card` 现有 `payload (jsonb)` 字段足以承载 VisualizeCard 的 `{intent, format, payload, alt_text, source_role}`。

仅在 `CardType` 枚举里加 `VISUALIZE("visualize", "visualize")`。

---

## 七、可视化场景目录（VisualizeIntent）

| Intent | 默认 format | 触发场景 | 节点输出建议 | 复杂度 |
|---|---|---|---|---|
| FOR_LOOP_TRACE | mermaid | 循环边界错误、循环讲解 | Yoshino / Nene | 低 |
| RECURSION_STACK | mermaid | 递归相关错误 / 讲解 | Yoshino / Murasame | 中 |
| DATA_STRUCTURE_STATE | svg | 列表 / 字典 / 字符串状态变化 | Nene / Yoshino | 中 |
| COMPLEXITY_COMPARE | chart | AC 后讨论复杂度 | Kanna | 低 |
| KC_MASTERY_RADAR | chart | 学生主动看画像 | 学生主动 | 低 |
| MEMORY_LAYOUT | svg | 引用语义错误 / 别名问题 | Yoshino | 高 |
| DATA_FLOW | mermaid | 多函数调用 / 参数传递错误 | Yoshino / Murasame | 中 |
| FLOWCHART | mermaid | 通用算法描述 | Nene | 低 |

---

## 八、契约示例

### 8.1 节点输出（含 visualize_intent）

```json
{
  "root_cause": "你又在 range(n) 边界上漏掉了上界，导致 i 取不到 n-1",
  "what_program_is_doing": "for i in range(5) 实际只跑 i=0..4",
  "expected_behavior": "你以为 i 会跑到 5",
  "fix_direction": "把 range(5) 改成 range(6)，或检查终止条件",
  "is_recurring": true,
  "encouragement": "这是第 4 次了，这次我们用图一起看一下",
  "mentor_role": "Yoshino",
  "visualize_intent": "for_loop_trace",
  "visualize_prompt": "画 for i in range(5): print(i) 的 5 次迭代，标出 i 没有到达 5"
}
```

### 8.2 VisualizeResult（写回 ai_tutor_card）

```json
{
  "id": "C-V-001",
  "card_type": "visualize",
  "payload": {
    "intent": "for_loop_trace",
    "format": "mermaid",
    "payload": "flowchart TD\n  start([for i in range(5)]) --> i0[i=0]\n  i0 --> i1[i=1]\n  i1 --> i2[i=2]\n  i2 --> i3[i=3]\n  i3 --> i4[i=4]\n  i4 --> stop([结束 - i 未到达 5])",
    "alt_text": "for i in range(5) 的 5 次迭代过程，i 实际取值 0,1,2,3,4，未到达 5",
    "source_role": "Yoshino"
  },
  "linked_card_id": "C-E-001"
}
```

### 8.3 学生主动请求

```http
POST /ai-tutor/visualize/inline
{
  "session_id": "S001",
  "problem_id": 7,
  "intent": "kc_mastery_radar",
  "prompt": "我的知识点掌握度怎么样"
}

Response:
{
  "card_id": "C-V-002",
  "intent": "kc_mastery_radar",
  "format": "chart",
  "payload": "{\"type\":\"radar\",\"data\":{\"labels\":[\"for循环\",\"range语义\",\"变量赋值\",\"print格式\",\"字符串切片\"],\"datasets\":[{\"label\":\"小明\",\"data\":[0.34,0.41,0.92,0.88,0.55]}]}}",
  "alt_text": "小明的 5 个知识点掌握度雷达图"
}
```

---

## 九、全链路时序

### 9.1 学生提交错误代码 → Yoshino 主动画图

```
学生提交错误代码
        │
        ▼
ERROR_FEEDBACK 节点 LLM 输出 (含 visualize_intent="for_loop_trace")
        │
        ▼
projection.py
        │ 检测到 visualize_intent
        ▼
java_client.dispatch_visualize(intent, prompt, context_hints, ...)
        │
        ▼
Java InternalAITutorToolService.dispatchVisualize
        │
        ▼
VisualizeCapabilityService.dispatch
        ├─ buildSystemPrompt (FOR_LOOP_TRACE 专用模板)
        ├─ AiModelGateway.callForJson → Mermaid 文本
        ├─ MermaidValidator.validate → 通过
        └─ 写入 ai_tutor_card (CardType.VISUALIZE)
        │
        ▼
返回 card_id 给 projection.py
        │
        ▼
node_outputs.error_diagnosis.visualize_card_id = "C-V-001"
        │
        ▼
SSE → 前端
        │
        ▼
UnifiedAgentPanel:
  ├─ 渲染 ErrorDiagnosisCard (主卡)
  └─ 在主卡下方渲染 VisualizeCard (Mermaid)
        │
        ▼
学生看到一张"i 没有到达 5"的循环示意图
```

### 9.2 学生主动点"画一下"按钮

```
学生在 UnifiedAgentPanel 点 "📊 复杂度对比"
        │
        ▼
useVisualizeApi.requestInline({intent: "complexity_compare", prompt: ...})
        │
        ▼
POST /ai-tutor/visualize/inline
        │
        ▼
AITutorController.inlineVisualize
        │
        ▼
VisualizeCapabilityService.dispatch
        ├─ buildSystemPrompt (COMPLEXITY_COMPARE)
        ├─ AiModelGateway.callForJson → Chart.js JSON
        ├─ ChartConfigValidator.validate → 通过
        └─ 写入 ai_tutor_card
        │
        ▼
Response 200 (含 card payload)
        │
        ▼
前端立即渲染 ChartRenderer，无需等待 SSE
```

---

## 十、工作量评估

> 工作量按"单人工程师 + 已熟悉 Alethicode"估算，单位人日。

| 模块 | 任务 | 人日 |
|---|---|:---:|
| **后端 (Java)** | `VisualizeIntent` enum + `CardType.VISUALIZE` | 0.5 |
|  | `VisualizeCapabilityService` 主服务 + 单测 | 1.5 |
|  | `MermaidValidator` + 单测 | 1 |
|  | `ChartConfigValidator` (JSON Schema) + 单测 | 1 |
|  | `SvgSanitizer` (jsoup 白名单) + 单测 | 1 |
|  | 8 种 Intent 的 SYSTEM_PROMPT 模板 | 1 |
|  | `AITutorController.inlineVisualize` 端点 | 0.5 |
|  | `InternalAITutorToolService.dispatchVisualize` 端点 | 0.5 |
| **tutor_graph (Python)** | `projection.py` 增加 visualize_intent dispatch | 1 |
|  | 5 节点 SYSTEM_PROMPT 增加 visualize_intent 字段说明 | 0.5 |
|  | `clients/java_tools_client.py` 加 dispatch_visualize | 0.5 |
| **前端 (Vue)** | 引入 mermaid + chart.js + vue-chartjs 依赖 | 0.5 |
|  | `VisualizeRenderer.vue` (router) | 0.5 |
|  | `MermaidRenderer.vue` | 1 |
|  | `ChartRenderer.vue` | 1 |
|  | `SvgRenderer.vue` (含尺寸控制) | 0.5 |
|  | `UnifiedAgentPanel.vue` 加按钮组 + 卡片渲染 | 1 |
|  | `useVisualizeApi.js` composable | 0.5 |
| **集成 / E2E** | 8 种 Intent 各 1 个 happy path E2E | 2 |
|  | 校验失败场景测试（Mermaid / Chart / SVG 各 1） | 1 |
| **合计** | | **15 人日** |

---

## 十一、验收标准

### 11.1 单元测试（必过）

| 测试 | 验证点 |
|---|---|
| `VisualizeCapabilityServiceTest#dispatchForLoopTrace` | 输出 format=mermaid，validate 通过 |
| `VisualizeCapabilityServiceTest#dispatchComplexityCompare` | 输出 format=chart，schema validate 通过 |
| `VisualizeCapabilityServiceTest#dispatchDataStructureState` | 输出 format=svg，sanitize 后 payload 合法 |
| `VisualizeCapabilityServiceTest#failsFastWhenLLMReturnsBadFormat` | format=foo 时立刻抛 VisualizeValidationException |
| `MermaidValidatorTest#rejectsSubgraph` | 含 subgraph 立刻抛异常 |
| `MermaidValidatorTest#rejectsClick` | 含 click 立刻抛异常 |
| `MermaidValidatorTest#rejectsScriptInLabel` | 节点文本含 `<script>` 立刻抛异常 |
| `ChartConfigValidatorTest#rejectsCustomTooltipCallback` | options.plugins.tooltip.callbacks 立刻抛异常 |
| `ChartConfigValidatorTest#rejectsTypeBubble` | type=bubble 立刻抛异常 |
| `SvgSanitizerTest#stripsScriptTag` | `<script>` 子元素被剥离 |
| `SvgSanitizerTest#stripsForeignObject` | `<foreignObject>` 被剥离 |
| `SvgSanitizerTest#stripsJavaScriptHref` | `href="javascript:..."` 被剥离 |

### 11.2 Python 单测（必过）

| 测试 | 验证点 |
|---|---|
| `services/tutor-graph/tests/test_projection.py::test_dispatches_visualize_when_intent_present` | node_output 含 visualize_intent 时调用 dispatch_visualize |
| `services/tutor-graph/tests/test_projection.py::test_skips_when_intent_empty` | 空 intent 时不调 |
| `services/tutor-graph/tests/nodes/test_diagnosis.py::test_system_prompt_includes_visualize_options` | SYSTEM_PROMPT 含 visualize_intent 选项说明 |

### 11.3 集成测试（必过）

| 测试 | 验证点 |
|---|---|
| `VisualizeApiIntegrationTest#inlineForLoopTraceReturnsCard` | POST /ai-tutor/visualize/inline 返回 200 + 卡片 |
| `VisualizeApiIntegrationTest#validationFailureReturns422` | LLM mock 返回非法 mermaid 时返回 422 |
| `AITutorWorkflowVisualizeIntegrationTest#errorDiagnosisGeneratesVisualize` | 节点输出含 visualize_intent 时 ai_tutor_card 出现 VISUALIZE 卡 |

### 11.4 前端单测（必过）

| 测试 | 验证点 |
|---|---|
| `MermaidRenderer.spec.js#rendersValidMermaid` | mount 后 svg 元素出现 |
| `ChartRenderer.spec.js#rendersLineChart` | type=line 时 canvas 元素出现 |
| `SvgRenderer.spec.js#rendersSanitizedSvg` | 渲染后 DOM 不含 script 元素 |
| `VisualizeRenderer.spec.js#routesByFormat` | format=mermaid 调用 MermaidRenderer |

### 11.5 E2E 验收（必过）

| 场景 | 期望 |
|---|---|
| 学生提交循环错误 | Yoshino 主动输出 visualize_intent，UnifiedAgentPanel 出现 Mermaid 流程图 |
| 学生 AC 后点"复杂度对比" | 弹出 Chart.js 折线图（O(n) vs O(n²)） |
| 学生主动请求 KC 雷达图 | 出现 radar chart |
| LLM 输出非法 SVG | 卡片显示 "可视化生成失败" + alt_text |
| 学生切换 Phase | Visualize 按钮按 Phase 动态显示 |

### 11.6 教学指标（上线后 4 周内观察）

| 指标 | 目标 |
|---|---|
| 含 Visualize 卡片的会话 vs 不含的，次日 AC 率差 | 含的 + ≥ 8% |
| 学生主动点"画一下"按钮率 | ≥ 15% 的活跃学生触发过至少 1 次 |
| Visualize 卡片正面反馈率（学生点赞） | ≥ 70% |
| Visualize 渲染失败率 | < 3% |

---

## 十二、风险与缓解

| # | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | LLM 输出非法 Mermaid 导致渲染失败 | 中 | 中 | 校验 + 失败 failfast + alt_text 兜底显示 |
| R2 | SVG 注入脚本绕过 sanitizer | 低 | 高 | jsoup 白名单 + 测试覆盖 + 前端二次防护（CSP） |
| R3 | LLM 滥用 visualize_intent 导致每次都画图 | 中 | 低 | SYSTEM_PROMPT 明确"必要时才输出"；节点单测验证 |
| R4 | Chart.js 配置 LLM 拼错维度（labels.length ≠ data.length） | 中 | 低 | schema 校验 maxItems；渲染失败显 alt_text |
| R5 | 可视化 token 成本上涨 | 中 | 中 | 仅在节点 mention visualize_intent 时调；学生主动请求 1 次 = 1 次调 |
| R6 | Mermaid 渲染太慢卡前端 | 低 | 低 | 异步渲染 + loading skeleton |
| R7 | 学生用 Visualize 当玩具，过度调用 | 中 | 低 | 学生主动调用按 IP/UID 限流：每分钟 ≤ 3 次 |

---

## 十三、第一性原理自检

| 自检项 | 通过 |
|---|---|
| 不引入兼容性 / 补丁性方案 | ✅ Visualize 与 execution_trace_explainer 互补，不重叠 |
| 不过度设计 | ✅ 不引入 Manim / D3 / PlantUML；intent 限定 8 种 |
| 不擅自扩展业务目标 | ✅ 仅围绕"教学场景的概念可视化" |
| 不写防御性 / 兜底逻辑 | ✅ 校验失败 failfast；不重试 LLM |
| 全链路逻辑验证 | ✅ 七、九 已覆盖入参 → 处理 → 校验 → 渲染 |
| 重命名全链路同步 | N/A（无重命名） |
| 不做与当前需求无关的兜底 | ✅ 不做"模型无能力时降级到文字描述" |

---

## 附录 A：Mermaid 子集白名单

允许的 Mermaid 类型：

| 类型 | 用途 | 示例 |
|---|---|---|
| `flowchart TD` / `flowchart LR` | 流程图 / 循环迭代 | for_loop_trace / flowchart |
| `sequenceDiagram` | 函数调用时序 | data_flow（多函数） |
| `stateDiagram-v2` | 状态机 | 状态切换教学 |
| `classDiagram` | 类继承（OOP 课程） | 仅教师课件用 |

允许的语法元素：

- 节点：`A[文本]` / `B(文本)` / `C{文本}`
- 箭头：`-->` / `--text-->` / `-.->` / `==>`
- 子图：❌ 禁止
- 点击事件：❌ 禁止
- 自定义样式：❌ 禁止
- HTML：❌ 禁止

## 附录 B：Chart.js 配置 schema

详见 6.5.2 章节 JSON Schema 定义。

允许的 type：`line` / `bar` / `radar`
禁止的 type：`bubble` / `scatter` / `polarArea` / `doughnut` / `pie`（均不在教学场景常用）

## 附录 C：SVG 沙箱白名单

**允许的标签**：
`svg, g, rect, circle, ellipse, line, path, polyline, polygon, text, tspan, defs, marker, linearGradient, stop, title, desc`

**允许的属性**：
`x, y, width, height, cx, cy, r, rx, ry, d, points, x1, x2, y1, y2, dx, dy, stroke, stroke-width, stroke-dasharray, stroke-linecap, stroke-linejoin, fill, fill-opacity, font-size, font-family, font-weight, text-anchor, dominant-baseline, transform, marker-end, marker-start, viewBox, xmlns, id, class, offset, stop-color, stop-opacity, gradientUnits`

**严禁的标签**：
`script, foreignObject, use, animate, animateTransform, animateMotion, set, iframe, image (with href), a (with href), object, embed`

**严禁的属性**：
`on*` (所有事件)、`href`、`xlink:href`、`style` (内联)、`formaction`

**值过滤**：
- 任何属性值含 `javascript:` → 整个属性删除
- 任何属性值含 `data:` (除非为 SVG content) → 整个属性删除
- 任何属性值含 `<script` → 整个元素删除

---

**设计完。**
