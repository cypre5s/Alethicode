# Alethicode 文档真实性与正确性自审报告

> 审查日期：2026-05-07  
> 审查对象：`docs/course-submission/` 与 `docs/course-submission/formal-review/` 下的课程提交文档、正式评审增强版文档、PPT 和 Word 产物。  
> 审查口径：区分“已由源码或配置核验的事实”“课程报告模板基线/待实际更新项”“已修正问题”和“剩余风险”。

## 1. 字符统计

| 文件 | 总字符数 | 非空白字符数 | 行数 | 说明 |
|---|---:|---:|---:|---|
| `formal-review/alethicode-formal-review-all.md` | 175,640 | 160,487 | 3,103 | 不含空格、换行、制表符等空白字符后仍超过 12 万字符。 |

说明：此前口头说明中的字符数包含换行和空格；本次已按用户要求改为以“非空白字符数”为准，并更新了 `formal-review/00-formal-review-index.md` 的统计口径。

## 2. 已核验为真实的项目事实

| 事实项 | 核验来源 | 结论 |
|---|---|---|
| 项目定位为面向非计算机专业 Python 初学者的 AI 智能在线评测平台 | `README.md` | 真实。 |
| 后端使用 Java 21、Spring Boot 3.5.12、Spring AI 1.1.4 | `backend/pom.xml` | 真实。 |
| 后端包含 Spring Security、Redis Session、JPA、Flyway、WebSocket、Actuator、Resilience4j、Micrometer、Sentry 等依赖 | `backend/pom.xml` | 真实。 |
| 前端使用 Vue 3.5、Vite 7.1、Vue Router 4、Vuex 4、Element Plus、CodeMirror、Jest、Playwright | `frontend/package.json` | 真实。 |
| Node.js 要求 `>=20.19.0`，npm 要求 `>=10.8.2` | `frontend/package.json` | 真实。 |
| `tutor-graph` 使用 FastAPI、LangGraph、Python 3.11 相关依赖 | `services/tutor-graph/pyproject.toml` | 真实。 |
| `alethicode-rag` 使用 FastAPI、LightRAG、pgvector、Memgraph 相关依赖 | `services/alethicode-rag/pyproject.toml` | 真实。 |
| 当前可见 Flyway SQL 迁移文件数量为 86 个 | `backend/src/main/resources/db/migration` 文件枚举 | 真实。 |
| 当前可见后端测试文件约 173 个 | `backend/src/test` 文件枚举 | 真实，具体数量以后续新增/删除为准。 |
| 当前可见前端测试文件约 134 个 | `frontend/tests` 文件枚举 | 真实，具体数量以后续新增/删除为准。 |
| 学生端路由包含登录、注册、题目、提交、课堂、课件问答、错题复习、学习者笔记本等 | `frontend/src/pages/oj/router/routes.js` | 真实。 |
| 管理端路由包含题目、用户、判题服务器、语言包、AI 配置、可观测性、反馈、使用统计等 | `frontend/src/pages/admin/router.js` | 真实。 |
| 课件问答 API 位于 `/api/language-pack-qa/*` | `LanguagePackQaController` | 真实。 |
| AI Tutor 工作流 API 位于 `/api/ai/tutor-workflow-sessions/*` | `TutorWorkflowController` | 真实。 |
| 学习者画像 API 位于 `/api/ai/tutor/profile/*` | `ProfileController` | 真实。 |
| 错题复习包 API 位于 `/api/ai/review-packages/*` 和 `/api/admin/ai/review-packages/*` | `ErrorReviewPackageController`、`AdminErrorReviewPackageController` | 真实，已修正原先泛化路径。 |
| 课堂作业 API 位于 `/api/classroom/{classroomId}/assignments/*` | `ClassroomAssignmentController` | 真实，已修正原先泛化路径。 |
| 管理端学情洞察 API 位于 `/api/admin/insight/*` | `AdminCourseInsightController` | 真实，已修正原先错误路径。 |
| 平台公开配置 API 包含 `/api/website`、`/api/languages`、`/api/csrf` | `PlatformConfigController` | 真实。 |
| 管理端配置 API 包含 `/api/admin/website`、`/api/admin/smtp`、`/api/admin/super/*` | `AdminConfigController` | 真实，已修正原先泛化路径。 |

## 3. 模板基线或待实际更新项

以下内容不是运行实测结果，而是为了课程文档完整性提供的“报告模板基线”。最终提交前应使用真实测试执行数据、实际截图和实际时间替换。

| 文档位置 | 内容 | 当前性质 | 处理建议 |
|---|---|---|---|
| `01-project-plan-formal.md` 甘特图日期 | 2026-03 到 2026-05 的阶段日期 | 计划/示例基线 | 若课程有实际开题、开发、验收日期，应替换为真实日期。 |
| `01-project-plan-formal.md` 风险矩阵数值 | 风险概率和影响坐标 | 评估基线 | 可保留为项目评估，也可用团队实际风险评审记录更新。 |
| `05-test-plan-and-report-formal.md` 缺陷统计图 | 严重/高/中/低缺陷数量 | 模板基线 | 必须在执行测试后替换为真实缺陷统计。 |
| `05-test-plan-and-report-formal.md` 缺陷趋势图 | D1-D7 缺陷发现/修复曲线 | 模板基线 | 必须用真实测试周期数据替换。 |
| `05-test-plan-and-report-formal.md` 性能测试曲线图 | 题目列表、AI Tutor 等响应时间 | 示例基线 | 必须用实际环境压测或浏览器/日志数据替换。 |
| `05-test-plan-and-report-formal.md` 测试通过率图 | 通过/失败/阻塞/未执行比例 | 模板基线 | 必须用真实测试执行结果替换。 |
| `06-project-summary-formal.md` 计划进度与实际进度对比 | 实际完成度百分比 | 总结模板基线 | 若要严格真实性，应替换为真实项目进度记录。 |
| `06-project-summary-formal.md` 缺陷模块分布 | 各模块缺陷数量 | 模板基线 | 应用真实缺陷台账更新。 |
| `08-defense-ppt-outline.md` 与 `alethicode-defense.pptx` | 答辩内容和展示节奏 | 基于项目事实的演示稿 | 需要补充姓名、课程、指导教师和真实截图。 |
| `09-demo-video-script.md` | 视频分镜和旁白 | 录制脚本 | 未生成实际视频，需要按脚本录屏。 |

## 4. 已修正的问题

| 问题 | 原描述 | 修正后 |
|---|---|---|
| 字符统计口径不严谨 | 曾口头使用包含空白字符的总字符数 | 已改为统计非空白字符，合并版为 160,487 个非空白字符。 |
| 课堂作业接口路径过于泛化 | `/api/classroom-assignments/*` | `/api/classroom/{classroomId}/assignments/*` |
| 学情洞察接口路径错误 | `/api/admin/course-insight/*` | `/api/admin/insight/*` |
| 错题复习接口路径错误 | `/api/review-package/*` | `/api/ai/review-packages/*` |
| 平台配置接口路径泛化不准确 | `/api/platform-config/*` | `/api/website`、`/api/languages`、`/api/csrf` 和 `/api/admin/website|smtp|super/*` |
| Word 版说明不足 | 仅生成初版 Word | 已补充 `alethicode-formal-review-documents.docx` 正式评审增强版。 |

## 5. 图片/图表描述真实性审查

| 文档 | 图表类型 | 真实性结论 | 需要注意 |
|---|---|---|---|
| 项目计划 | 项目组织结构图、WBS | 基于软件工程角色和当前项目模块划分，结构合理，但不是实际人员名单。 | 提交前应替换为真实姓名、学号、分工。 |
| 项目计划 | 甘特图、里程碑图 | 属于课程计划/复盘基线，不是从 Git 提交历史自动推导。 | 如教师要求真实进度，应按实际周报或提交记录更新日期。 |
| 项目计划 | 风险矩阵 | 风险项来自项目依赖和架构事实，概率/影响坐标为评估值。 | 可保留为项目管理判断，不应说成实测数据。 |
| 需求规格 | 系统上下文图、用例图、DFD、业务流程图 | 用户角色、外部服务和主流程与当前代码/配置基本一致。 | 用例图是概念用例图，不表示每条边都是一个独立 API。 |
| 需求规格 | 状态图 | AI Tutor 阶段与 README 中七阶段导学一致。 | 个别状态是文档抽象，用于说明流程，不等同于数据库枚举全集。 |
| 需求规格 | 界面原型图 | 基于前端题目页功能结构抽象。 | 不是真实截图，正式提交可补浏览器截图。 |
| 需求规格/概要设计 | 概念 ER、逻辑 ER | 反映用户、题目、提交、语言包、课堂、AI 会话等核心关系。 | 概念 ER 不等同于完整物理表全集。 |
| 概要设计 | 系统架构图、分层架构图、组件图、部署图 | 与 `README.md`、`docker-compose.yml`、`pom.xml`、微服务配置基本一致。 | 部署图省略了部分监控/网络细节，表达的是课程评审视角。 |
| 概要设计 | 接口关系图 | 反映前端、后端、Judge、Tutor Graph、RAG、LLM/Embedding 的调用关系。 | 是主链路抽象，不覆盖所有管理端接口。 |
| 详细设计 | 类图 | 类名已抽样核验：`AccountService`、`ProblemQueryServiceImpl`、`SubmissionJudgeExecutor`、`AITutorWorkflowDomainService`、`LanguagePackQaService`、`ClassroomCoreController`、`ClassroomAnalyticsService` 等存在。 | 类图是核心类关系，不是全量类图。 |
| 详细设计 | 时序图、活动图、API 流程图 | 与代码分层和业务链路一致。 | 时序图是典型路径，异常分支在正文说明中覆盖。 |
| 详细设计 | 数据库物理 ER | 已修正为更贴近真实表名，如 `"user"` 用 `user_table` 表示、课件问答使用 `language_pack_chat_session`。 | 物理 ER 是核心表摘录，不是完整 86 个迁移的全量 ER。 |
| 测试计划 | 测试流程图、测试环境部署图、覆盖矩阵 | 测试流程和环境结构与项目测试资产一致。 | 覆盖矩阵中“需在最终环境执行”不是已执行结果。 |
| 测试计划 | 缺陷统计、缺陷趋势、测试进度、性能曲线、通过率 | 已标注为模板基线或待实际执行更新。 | 不应在答辩中称为真实测试结果。 |
| 项目总结 | 项目成果结构图、风险闭环图 | 与交付物和项目管理逻辑一致。 | 属于总结抽象图。 |
| 项目总结 | 进度对比、里程碑完成、工作量、缺陷分布、质量指标 | 多数属于模板基线；质量资产构成中后端测试、前端测试、迁移数量和交付目录文件数已按当前仓库枚举更新。 | 最终提交前最好替换为真实工时、真实缺陷和真实测试通过率。 |

## 6. 正确性结论

1. **架构、技术栈、模块划分、主要路由、微服务、数据库迁移和测试资产描述基本可信**，已通过源码、配置或目录枚举核验。
2. **接口路径中原先存在少量泛化或错误项，已修正并重新生成正式增强版 Markdown 与 Word 文档**。
3. **测试统计、性能曲线、缺陷趋势、进度对比等数值不是实测数据**，已经在索引和相关文档中标注为模板基线或需实际更新。若课程要求严格真实测试报告，必须先运行测试并替换这些数值。
4. **“1000 页”无法仅由 Markdown 行数严格等价换算**，因为 Word 页数取决于模板、字号、图表渲染、页边距和分页规则。本次可核验指标是：合并版 Markdown 不含空白字符 160,428 个。
5. **Mermaid 图在 Markdown 中是可渲染图源码，Word 版目前保留源码块**。如果要求 Word 里直接看到图形，需要将 Mermaid 渲染为 PNG/SVG 后嵌入。
6. **图片/图表描述已复核**：设计型图表中的模块、类名和主要接口已按源码修正；统计型图表继续标注为模板基线或待实测更新，不再当作真实执行结果。

## 7. 提交前建议

1. 运行后端、前端和微服务测试，把真实结果写入 `05-test-plan-and-report-formal.md`。
2. 使用实际测试结果替换缺陷统计、缺陷趋势、性能曲线和通过率图。
3. 按学院模板补充封面、目录、页眉页脚、姓名、学号、班级、指导教师。
4. 将 Mermaid 图渲染为图片后嵌入 Word/PDF。
5. 使用真实浏览器截图替换原型图或补充到附录。
6. 按 `09-demo-video-script.md` 录制功能演示视频，并在提交前检查无密钥、无隐私信息。
