# Alethicode 课程提交文档包

> 文档包版本：v1.0  
> 生成日期：2026-05-07  
> 项目名称：Alethicode  
> 项目定位：面向非计算机专业 Python 初学者的 AI 智能在线评测平台  
> 编制依据：项目源码、`README.md`、`PROJECT.md`、`backend/pom.xml`、`frontend/package.json`、微服务 `pyproject.toml`、前端路由、后端控制器、数据库迁移与测试目录。  
> 说明：用户提到“实训总结报告模板见附件”，但当前仓库和上下文未发现附件模板。因此本目录先按通用高校实训报告结构编写，收到模板后可迁移到指定版式。

## 交付物清单

| 序号 | 文件 | 对应课程要求 | 说明 |
|---:|---|---|---|
| 1 | `01-project-plan.md` | 项目计划 | 按 ISO/IEC/IEEE 12207 软件生命周期过程组织，覆盖目标、范围、WBS、里程碑、风险、质量与沟通管理。 |
| 2 | `02-software-requirements-specification.md` | 需求规格说明书 | 参考 ISO/IEC/IEEE 29148 编写，覆盖业务背景、用户角色、功能需求、非功能需求、数据需求和验收准则。 |
| 3 | `03-high-level-design.md` | 概要设计说明书 | 参考 IEEE 1016 软件设计描述，覆盖总体架构、部署架构、模块划分、接口和数据流。 |
| 4 | `04-detailed-design.md` | 详细设计说明书 | 面向实现层，覆盖核心模块、类/服务职责、状态机、数据库、接口、安全、异常和缓存设计。 |
| 5 | `05-test-plan-and-report.md` | 测试计划和报告 | 参考 ISO/IEC/IEEE 29119，覆盖测试范围、策略、用例、环境、执行报告和缺陷风险。 |
| 6 | `06-project-summary.md` | 项目总结 | 总结成果、创新点、工程收益、问题和后续改进。 |
| 7 | `07-practice-summary-report.md` | 实训总结报告 | 通用实训报告模板，覆盖任务、过程、收获、问题、反思和致谢。 |
| 8 | `08-defense-ppt-outline.md` | 答辩 PPT 内容稿 | PPT 页结构、讲解要点和答辩重点。 |
| 9 | `09-demo-video-script.md` | 功能演示视频脚本 | 录屏分镜、旁白、操作步骤和验收点。 |
| 10 | `formal-review/` | 正式评审增强版 | 按补充要求生成六大文档增强版，包含甘特图、WBS、用例图、DFD、架构图、部署图、ER 图、类图、时序图、测试覆盖矩阵、缺陷统计图等 Mermaid 图表。 |
| 11 | `alethicode-defense.pptx` | 答辩 PPT | 已生成 15 页答辩 PPT。 |
| 12 | `alethicode-technical-documents.docx` | Word 版技术文档 | 初版六大技术文档合集。 |
| 13 | `alethicode-formal-review-documents.docx` | Word 版正式评审文档 | 正式评审增强版合并文档，包含六大文档、评审附录和真实性自审报告。 |
| 14 | `alethicode-practice-summary-report.docx` | Word 版实训总结 | 通用模板版实训总结报告。 |
| 15 | `formal-review/08-truthfulness-audit.md` | 真实性与正确性自审 | 区分已核验事实、模板基线、已修正问题和剩余风险，并按非空白字符重新计数。 |
| 16 | `formal-review/09-diagram-inventory.md` | 图表覆盖清单 | 逐项对应课程要求，标明每类图表所在文档和覆盖状态。 |

## 项目事实基线

本包尽量以当前代码事实为准，避免直接照搬可能过期的文档描述。

| 分类 | 当前事实 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.12、Spring Security、Spring Session Data Redis、Spring Data JPA、JdbcTemplate、Flyway、Spring AI、Resilience4j、Micrometer、Sentry/GlitchTip。 |
| 前端 | Vue 3.5、Vite 7.1、Vue Router 4、Vuex 4、Element Plus、CodeMirror 6、ECharts、D3、KaTeX、Mermaid、Jest、Playwright。 |
| 微服务 | `tutor-graph`：FastAPI + LangGraph；`alethicode-rag`：FastAPI + LightRAG + PostgreSQL pgvector + Memgraph。 |
| 数据层 | PostgreSQL/pgvector、Redis、Memgraph、NATS、Temporal、文件存储、Flyway 迁移。 |
| 核心角色 | 学生、教师、管理员、系统/内部服务。 |
| 核心功能 | 在线评测、题库管理、提交记录、AI Tutor、课件问答、语言包初始化、课堂协作、学习者画像、错题复习、后台管理、可观测性。 |
| 已下线能力 | Career 模块在当前 README 中标注为 Removed，不作为本次提交的核心演示功能。 |

## 标准映射

| 标准或模型 | 在本文档包中的体现 |
|---|---|
| ISO/IEC/IEEE 12207 | 项目计划按软件生命周期过程组织，包括管理、开发、验证、配置和维护。 |
| ISO/IEC/IEEE 29148 | 需求规格说明书区分业务需求、用户需求、系统需求和验收准则。 |
| IEEE 1016 | 概要设计和详细设计按视图、接口、数据和设计决策组织。 |
| ISO/IEC 25010 | 非功能需求按功能适合性、性能效率、兼容性、可用性、可靠性、安全性、可维护性、可移植性组织。 |
| ISO/IEC/IEEE 29119 | 测试计划和报告按测试级别、测试类型、测试环境、用例、缺陷和结论组织。 |
| OWASP ASVS / Top 10 思路 | 安全章节覆盖认证授权、会话、CSRF、输入校验、内部服务密钥、审计和敏感数据处理。 |

## 提交建议

1. 项目代码提交仓库完整代码，避免只提交生成产物。
2. 技术文档可将本目录 Markdown 转为 Word/PDF；若学院有固定封面和目录模板，建议仅迁移正文结构，不改变技术内容。
3. 答辩 PPT 已生成 `alethicode-defense.pptx`，可继续按学校模板调整封面、页脚和个人信息。
4. 功能演示视频按 `09-demo-video-script.md` 录制，推荐控制在 5 到 8 分钟。
5. 提交前需要用实际运行环境复核截图、接口返回和演示路径，尤其是外部 LLM、Judge Server、RAG 服务和数据库依赖。
