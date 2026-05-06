# Alethicode Agent 工作准则

本文件定义 AI Agent 在 Alethicode 项目中的工作方式。规则应保持简洁、
明确、可执行；新增规则只在能减少重复误解或稳定约束项目行为时加入。

## 项目背景

- Alethicode 是面向非计算机专业初学者的智能化编程教育平台，语言教学以 Python
  优先。
- 输出方案、代码和解释时，优先保证初学者可理解、可操作、可验证。
- 项目内 ReAct 默认关闭；除非用户明确要求，不启用或假设 ReAct 流程。

## 基础约定

- 默认使用中文和用户交流；技术名词、命令、路径和代码标识保持原文。
- 终端、源码和文档默认使用 UTF-8 编码，避免中文乱码。
- 前端使用 Node.js `>=20.19.0` 和 npm `>=10.8.2`。
- 后端使用 Java 21 和 Maven 3.9+。
- Python 微服务优先按各服务的 `pyproject.toml` 和 README 安装依赖；不要把
  某个服务的依赖管理方式扩散到整个仓库。

## 开发命令

### 后端 Spring Boot

```bash
cd backend
cp .env.example .env
mvn clean compile -DskipTests
mvn spring-boot:run
mvn test
```

### 前端 Vue 3 + Vite

```bash
cd frontend
npm ci
npm run dev
npm run typecheck
npm run lint
npm test
```

### Python 微服务

```bash
cd services/tutor-graph
python -m pip install -e ".[dev]"
python -m pytest -q

cd ../alethicode-rag
python -m pip install -e ".[dev]"
python -m pytest -q
```

## 仓库结构

```text
Alethicode/
├── backend/          # Spring Boot 后端，Java 21 + Maven
├── frontend/         # Vue 3 + Vite 前端
├── services/         # Python 微服务，含 tutor-graph 与 alethicode-rag
├── contracts/        # API / 工作流合约
├── docs/             # 架构、规格、计划、报告、ADR、指南
├── deploy/           # Docker Compose、Nginx、可观测性和部署配置
├── scripts/          # 运维、验证和数据脚本
├── tools/            # 开发工具
├── research/         # 学术研究目录
└── nfk/              # NFK 实验目录
```

## 关键技术约束

- Java 后端是主业务入口，覆盖账号、题目、提交、课堂、语言包、AI Tutor、
  视频任务、模型网关和可观测性。
- 前端是 Vue 3 + Vite SPA，覆盖学生 OJ、教师管理、课堂协作、课件问答等页面。
- `tutor-graph` 是 LangGraph Tutor 工作流状态的唯一事实来源；Java 侧只维护
  管理端和 UI 恢复所需的投影数据，不直接修改 LangGraph 状态。
- `tutor-graph` 当前依赖进程内事件缓冲和活动任务注册，运行时必须保持
  `--workers 1`。
- `alethicode-rag` 负责课件 RAG 服务，涉及 FastAPI、LightRAG、PostgreSQL
  `pgvector` 和 Memgraph。
- 生产与集成环境依赖 PostgreSQL、Redis、Judge Server、外部 LLM / Embedding
  服务和部署目录下的 Docker Compose 配置。

## 重要阅读入口

- 项目总览优先读 `README.md` 和 `PROJECT.md`。
- 后端开发优先读 `backend/README.md`。
- 文档归档和命名规则优先读 `docs/README.md`。
- 修改 `tutor-graph` 前必须读 `services/tutor-graph/README.md`。
- 涉及架构决策、长期计划或验收报告时，先在 `docs/adr/`、`docs/plans/`、
  `docs/reports/` 和 `docs/todos/` 中查找已有约束。

## 工作原则

- 从用户目标、动机和成功标准出发；如果目标或约束不清楚，先停下来提问。
- 不默认用户已经知道自己要什么、为什么要做、应该怎么做；必要时说明判断
  前提和取舍。
- 只围绕用户明确提出的目标工作，不擅自扩展业务目标，不引入替代业务路径。
- 采用最小完整方案，避免过度设计；不为未提出的需求增加配置、抽象、兼容层
  或降级路径。
- 保持 fail fast。不要写掩盖问题的防御性逻辑；为保证逻辑闭合所需的输入约束、
  状态检查和边界保护可以保留。

## 必用 Skills

- 编写或修改任何前端代码时，必须使用 `ui-ux-pro-max` skill。
- 新建任何 API 时，必须符合 `api-design-principles` skill。
- 完成任何代码修改后，必须使用 `code-reviewer` skill。
- 处理复杂任务时，必须使用 `superpowers` skill。

## 方案规范

- 方案必须是最小正确方案，不允许给出兼容性、补丁性或兜底性的替代方案。
- 当“最短路径”和“非补丁方案”冲突时，优先选择不会引入结构性错误的最小正确
  方案。
- 输出方案前，按输入、处理流程、状态变化、输出、上下游影响进行链路检查。
- 无法验证的部分必须明确标注为假设或未验证前提，不得把推测写成事实。
- 多步骤任务必须先给出简短计划，并为每一步写明对应的验证方式。

## 实施规范

- 修改要外科手术式，只触达完成目标所必需的文件和代码。
- 匹配当前代码风格；不要顺手重构、格式化或“优化”与任务无关的代码。
- 如果发现无关死代码或历史问题，可以在回复中说明，但不要擅自删除。
- 如果本次修改制造了未使用的 import、变量、函数或文件，必须一并清理。
- 每一处改动都应能追溯到用户需求；如果做不到，应删除该改动。

## 命名规范

### Java

- 类名与文件名必须一致，使用 `PascalCase`，如 `JudgeServerServiceImpl.java`。
- 方法名、变量名使用 `camelCase`，如 `lastHeartbeat`。
- 常量使用 `UPPER_SNAKE_CASE`。
- 包名全小写，禁止下划线和大写字母。
- 数据库表名和字段名保持 `snake_case`，通过实体注解映射，不把数据库命名风格
  扩散到 Java 变量。

### Vue 和前端

- 组件文件名使用 `PascalCase.vue`，如 `InfoCard.vue`、`NotFound.vue`。
- 组件 `name` 必须与组件语义一致，使用 `PascalCase`。
- 普通 JavaScript 工具模块文件名使用 `camelCase.js`，如
  `simditorFileUpload.js`。
- 变量名、函数名使用 `camelCase`；常量使用 `UPPER_SNAKE_CASE`。
- `i18n` 语言包文件保留标准区域格式，如 `zh-CN.js`、`en-US.js`。

### Python

- 文件名、函数名、变量名使用 `snake_case`。
- 类名使用 `PascalCase`。
- 常量使用 `UPPER_SNAKE_CASE`。

### 通用命名约束

- 同一语义只能有一种拼写，禁止 `infoCard`、`InfoCard`、`inforCard` 等混用。
- 重命名必须全链路同步，包括定义、引用、导入路径和文档。
- 不做兼容性别名，不保留旧命名并行路径，直接统一到目标命名。

## 验证与交付

- 把任务转化为可验证目标，例如：
  - 添加校验：覆盖非法输入并验证失败路径。
  - 修复缺陷：先复现问题，再验证修复后不再出现。
  - 重构代码：确认重构前后的关键测试或行为一致。
- 修改代码后，运行与改动范围匹配的测试、lint 或构建检查；如果无法运行，
  必须说明原因和剩余风险。
- 涉及代码修改时，必须在 `CHANGELOG.md` 中用中文记录变更，格式遵循常见
  changelog 写法，说明变更类型和影响范围。

## 文档维护

- Markdown 文档使用一个 H1 作为标题，后续使用 H2/H3 建立清晰层级。
- 规则用短句和列表表达，避免中英混排；工具名、文件名、命令和代码标识使用
  反引号。
- 定期删除重复、过时或不可执行的描述，保持文档比规则堆叠更重要。
