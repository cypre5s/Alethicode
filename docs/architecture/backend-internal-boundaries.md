# backend 内部模块边界规范

> 本规范定义 `backend/src/main/java/com/alethicode/service/` 内的子包划分原则、命名约定与跨域调用约束，是 backend 单体应用的"软边界"治理基础。  
> M-02 Spring Modulith（[ADR-0006](../adr/0006-resilience-engineering.md) 衍生项）将基于本规范定义的域边界落地 `@ApplicationModule` 标注，把"软边界"硬化为编译期约束。

## 一、为什么有这份规范

backend 单体应用承载多业务域（OJ、用户账号、AI 导学、班级教学、公测反馈、平台管理等），子包数量持续增长。在 2026-04-29 的内部重构前，`service/` 包同时存在三种风格混搭：

- 风格 A：顶层散装 `XxxService.java` 接口 + 顶层 `impl/` 子包统一收纳所有 `XxxServiceImpl.java`
- 风格 B：业务域子包 `<domain>/` + 域内 `impl/` 子包  
- 风格 C：业务域子包但平铺所有类（无 `impl/` 划分）

混搭直接后果：

1. 新成员难以判断"我新写的 Service 应该放哪"
2. `service/impl/` 子包中混入了 16 个**直接 `@Service` 但不是任何接口实现**的类，与真正的 `*ServiceImpl` 形成语义冲突
3. 域子包间互相隐式依赖，无法用工具静态识别违规调用

本规范统一到**风格 B**，并补足"无接口的直接 @Service 类如何归位"的规则。

## 二、子包划分原则

### 2.1 按 bounded context（业务限界上下文）划分

每个 `service/<domain>/` 子包对应一个**业务限界上下文**，包内类共享同一个领域语言（ubiquitous language）和一致的状态机。划分边界来自业务，不是来自调用者：

| 错误做法 | 正确做法 |
|---|---|
| 按调用者分（如 `service/admin/` 收纳一切被 admin controller 调用的服务）| 按业务域分（教师管理 KC 是 AI 导学域，归 `aitutor/admin/`）|
| 按技术风格分（如 `service/jdbc/`、`service/cache/`）| 业务域中包含异构实现（同一域内可同时有 JDBC、HTTP、消息队列实现）|

### 2.2 既有子包列表（17 个域）

`service/` 顶层不再容纳任何直接的 `*.java` 文件，全部位于域子包内：

| 域 | 业务边界 | 状态 |
|---|---|---|
| `account/` | 用户账号、登录、Profile | 既有 |
| `admin/` | 平台管理员通用工具（上传等）| 既有 |
| `adminproblemcommand/` | 题目管理（导入/导出/CRUD/题集/前置校验）| 既有 |
| `ai/` | AI 网关、模型路由、熔断 | 既有 |
| `aitutor/` | AI 导学工作流（含 7 phase 状态机、评测、policy、profile 等 27 个子包）| 既有 |
| `aitutor/admin/` | AI 导学的后台工具（KC 管理、误概念挖掘、变体题复审）| **本次新增** |
| `announcement/` | 站内公告 + 发布说明 | **本次新增** |
| `betafeedback/` | 公测反馈学生侧 + 邮件通知 | **本次新增** |
| `betafeedback/admin/` | 公测反馈管理员侧 | **本次新增** |
| `classroom/` | 班级教学（课堂、AI 题目、分析、学习进度、班级洞察）| 既有 |
| `compliance/` | 合规（PIPL、AIGC 内容审核）| 既有 |
| `languagepack/` | 语言包（课程结构、文档解析、答案合成、内容改进）| 既有 |
| `monitor/` | 监控（班级监控、学生风险检测）| 既有 |
| `nats/` | NATS 消息总线适配 | 既有 |
| `nfk/` | NFK 训练数据导出（与 `research/nfk/` 对接）| **本次新增** |
| `problem/` | 题目查询、相关例题查询 | **本次新增** |
| `rag/` | RAG 服务客户端（与 `services/alethicode-rag/` 对接）| 既有 |
| `submission/` | 代码提交、判题派发、判题机管理 | 既有 |
| `system/` | 平台基础设施（系统选项、平台配置、SMTP 邮件、系统管理）| **本次新增** |

### 2.3 新增域的判定标准

满足**全部**以下条件才允许新增域子包：

1. **职责独立**：业务上属于独立的 bounded context，不是任何既有域的子集  
2. **类规模 ≥ 2**：单一类应该归并到最近的现有域，避免域子包碎片化  
3. **未来可独立部署的可能性**：若有迁出 backend 单体的可能（参考 `tutor-graph` / `alethicode-rag` 的拆分轨迹），优先用独立域命名  
4. **包名遵循单一名词**（snake-free，全小写，无下划线，无连写多个名词）

不满足时，归并到最相近的既有域。

## 三、接口与实现的物理布局

```
service/<domain>/
├── XxxService.java              # public interface
├── XxxOtherDomainService.java   # 域内的另一个 public interface
├── DirectFooService.java        # 无接口的 @Service / @Component（直接业务类）
└── impl/
    ├── XxxServiceImpl.java
    └── XxxOtherDomainServiceImpl.java
```

### 3.1 强制规则

| 规则 | 例子 |
|---|---|
| 接口 `XxxService` 与对应实现 `XxxServiceImpl` 必须**同域不同子包**：接口在 `<domain>/`，实现在 `<domain>/impl/` | `account/AccountService.java` ↔ `account/impl/AccountServiceImpl.java` |
| 直接 `@Service` 或 `@Component` 类（无接口）放在 `<domain>/` 顶层，**禁止放进 `<domain>/impl/`** | `classroom/ClassroomLessonService.java`（无接口，直接业务类）|
| 嵌套深度最多 2 层：`<domain>/<sub>/`（如 `betafeedback/admin/`、`aitutor/admin/`），**禁止三层及以上** | `aitutor/admin/AdminKcManagementService.java` 合规；`aitutor/admin/kc/AdminKcManagementService.java` 不合规 |
| 不允许在域子包之外保留散装 service：`service/*.java` 必须为空 | `service/AccountService.java` 不合规 |
| 测试类的物理位置必须与被测类的包路径完全镜像 | `test/.../service/account/impl/AccountServiceImplTest.java` 测试 `main/.../service/account/impl/AccountServiceImpl.java` |

### 3.2 无接口直接 @Service 类的归位规则

历史上有部分类被命名为 `XxxService` 但**没有接口定义**（直接 `@Service` 注解的具体类）。这类类常见于：

- 数据导出/分析（如 `NfkDataExportService`、`CourseInsightService`）  
- 内部工具（如 `BetaFeedbackMailNotifier` 是 `@Component`）
- 单调用方的领域服务（如 `WorkflowCheckpointService` 仅被 `aitutor/impl/AITutorWorkflowAdminServiceImpl` 调用）

**统一处理策略**：

1. 它们 **不属于** "接口实现"，所以不放 `<domain>/impl/`
2. 放在 `<domain>/` 顶层（与接口同级）
3. 类名保持原样，**不强制 `XxxService` 改名为 `XxxComponent`**（命名风格的破坏成本 > 收益）
4. 如果未来重构成接口 + 实现，移到对应位置即可

## 四、跨域调用规则

### 4.1 通过接口调用，不引用具体实现

```java
// ✅ 推荐：依赖接口
@Autowired
private com.alethicode.service.account.AccountService accountService;

// ❌ 禁止：依赖具体实现
@Autowired
private com.alethicode.service.account.impl.AccountServiceImpl accountServiceImpl;
```

例外情况：本域内部代码可以引用本域 `impl/` 下的具体实现（用于复用工具方法、内部协作）。

### 4.2 跨域调用的方向约束

`aitutor/`、`classroom/`、`languagepack/` 是**业务核心域**，可以被 `controller/` 和其他业务域调用，但它们不应反向依赖 `admin/`、`adminproblemcommand/` 等管理域。

例外：`aitutor/admin/` 是 AI 导学的后台工具子域，可以被 `aitutor/` 下的代码反向依赖（典型例子：`aitutor/impl/AITutorWorkflowAdminServiceImpl` 依赖 `aitutor/admin/AdminKcManagementService`）。

### 4.3 禁止域间通过 controller 间接调用

如果 `classroom/` 需要 `submission/` 的能力，应直接 `@Autowired SubmissionService`，不允许 `RestTemplate.exchange("/api/submission/...")` 这种"自己 HTTP 调自己"。

## 五、Spring Modulith（M-02）预留标记

> 本节标记的域将在 [M-02 立项](../todos/) 时落地为 Spring Modulith 模块。本次重构是 M-02 的物理布局准备，不引入 `spring-modulith` 依赖。

### 5.1 候选模块（domain bounded context）

| 域 | 候选 `@ApplicationModule` 名 | 候选 `allowedDependencies` |
|---|---|---|
| `account/` | `account` | `system` |
| `admin/` | `admin` | `account, system` |
| `adminproblemcommand/` | `problem-admin` | `problem, system` |
| `aitutor/` | `tutor` | `account, problem, languagepack, rag, submission, system` |
| `announcement/` | `announcement` | `system` |
| `betafeedback/` | `beta-feedback` | `account, system` |
| `classroom/` | `classroom` | `account, problem, submission, languagepack, system` |
| `compliance/` | `compliance` | `system` |
| `languagepack/` | `language-pack` | `problem, rag, system` |
| `monitor/` | `monitor` | `account, classroom, submission` |
| `nfk/` | `nfk` | `submission, system` |
| `problem/` | `problem` | `system, languagepack` |
| `submission/` | `submission` | `problem, account, system` |
| `system/` | `system` | `(none)` |

### 5.2 与 ai/、rag/、nats/ 的关系

`ai/`、`rag/`、`nats/` 是**横切基础设施**而非业务模块：

- `ai/` 是 AI 网关与熔断，被几乎所有业务域调用
- `rag/` 是 RAG 服务客户端
- `nats/` 是消息总线适配

M-02 落地时这三者标记为 `@ApplicationModule(allowedDependencies = "*")` 或拉到独立的 `infra/` 子包外的 base 包。不在本次重构动它们。

## 六、Lint 与自动校验（M-02 之前）

在 M-02 落地前，以下规则**仅靠人工 PR review** 强制：

1. 顶层 `service/*.java` 必须为空 → 可加 pre-commit hook：`find backend/src/main/java/com/alethicode/service -maxdepth 1 -name "*.java" | wc -l` 应等于 0
2. `service/<domain>/impl/` 内不允许出现非 `*Impl` 文件 → 可加 lint
3. `Class.forName("com.alethicode.service.impl.X")` 等硬编码字面量必须为 0 → `rg "com\\.alethicode\\.service\\.impl\\."` 应无结果

建议在 [`scripts/m12/`](../../scripts/m12/) 下加一个 `check_service_layout.sh`，纳入 CI gate。

## 七、违反此规范的处理

1. PR review 阶段发现 → reviewer 直接打回，要求按本规范重新组织包结构
2. 历史遗留代码 → 在 [`docs/todos/`](../todos/) 起 ticket，**不允许**保留兼容性别名（参考 `AGENTS.md` 命名规范"不做兼容性别名"原则）
3. 紧急 Hotfix 阶段允许临时违反，但必须在 24 小时内或下次冲刺归位

## 八、本次（2026-04-29）实际归位映射

完整归位映射见 [CHANGELOG 2026-04-29 条目](../../CHANGELOG.md)。摘要：

- 50 个 `service/` 散装文件（15 接口 + 18 *ServiceImpl + 16 野生 @Service + 1 @Component）按本规范第二节、第三节物理迁移到对应域子包  
- 14 个测试文件镜像迁移到 `test/.../<domain>/impl/`  
- 全局 164 个 import 引用 + 2 处硬编码字面量同步替换  
- mvn `compile` + `test-compile` + `test`（Service 相关 195 例）全绿
