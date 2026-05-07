# 注释优化审计记录

按 `AGENTS.md` 注释规范逐文件清理，记录已审计文件避免重复。

## 审计标准

- 保留：设计取舍、边界约束、公开 API docstring、SQL 迁移目的
- 删除：叙述代码行为、重复签名信息、过时 TODO、冗余描述

## 已审计文件

| 日期 | 文件 | 操作 |
|------|------|------|
| 2026-05-06 | `services/tutor-graph/app/nodes/compact.py` | 模块 + 函数 docstring 中文化 |
| 2026-05-06 | `services/tutor-graph/app/nodes/chat.py` | 模块 docstring + 函数 docstring + 旁证函数 docstring |
| 2026-05-06 | `services/tutor-graph/app/graph/state.py` | 模块 docstring 中文化 |
| 2026-05-06 | `services/tutor-graph/app/graph/transitions.py` | 模块 docstring 中文化 |
| 2026-05-06 | `services/tutor-graph/app/graph/builder.py` | 模块 docstring 中文化 |
| 2026-05-06 | `services/tutor-graph/app/tests/test_compact_node.py` | 模块 docstring 中文化 |
| 2026-05-06 | `services/tutor-graph/app/tests/test_chat_node.py` | 模块 docstring 中文化 |
| 2026-05-06 | `backend/.../TutorWorkflowController.java` | compact/fork 端点 Javadoc |
| 2026-05-06 | `backend/.../LanguagePackQaController.java` | compact/fork 端点 Javadoc |
| 2026-05-06 | `backend/.../InternalAITutorToolService.java` | forkSession Javadoc |
| 2026-05-06 | `backend/.../LanguagePackQaService.java` | compactSession/forkSession Javadoc |
| 2026-05-06 | `backend/.../LanguagePackQaServiceImpl.java` | 实现层已合规 |
| 2026-05-06 | `backend/.../InternalAITutorToolServiceImpl.java` | 实现层已合规 |
| 2026-05-06 | `backend/.../V90__session_compact_and_fork.sql` | 迁移注释已合规 |
| 2026-05-06 | `frontend/src/pages/oj/api/aiTutor.js` | 命名自文档化，无需注释 |
| 2026-05-06 | `frontend/src/pages/oj/api/languagePack.js` | 命名自文档化，无需注释 |
| 2026-05-06 | `frontend/src/pages/oj/components/chat/useChatComposer.js` | 模块 JSDoc 已合规 |
| 2026-05-06 | `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue` | name/emits/props 自文档化 |
| 2026-05-06 | `frontend/src/pages/oj/views/problem/Problem.vue` | 命名自文档化 |
| 2026-05-06 | `frontend/src/pages/oj/views/languagepack/LanguagePackQaPage.vue` | 命名自文档化 |
| 2026-05-06 | `frontend/tests/unit/chat-composer.spec.js` | 模块 JSDoc 已合规 |
| 2026-05-06 | `backend/.../TutorWorkflowController.java` | 精简冗长注释（配额/poller 3处） |
| 2026-05-06 | `services/tutor-graph/app/nodes/chat.py` | 精简 card_id 验证注释 |
| 2026-05-06 | `services/tutor-graph/app/nodes/compact.py` | 移除 COMPACT_K 冗余注释 |
| 2026-05-06 | `frontend/.../Problem.vue` | 精简 Unified Chat 引用注释 |
| 2026-05-06 | `frontend/src/pages/oj/api.js` | 将冗长总览注释压缩为单一主注释（中文） |
| 2026-05-06 | `frontend/src/pages/admin/api.js` | 删除首批冗余行为注释，仅保留结构性分段注释 |
| 2026-05-06 | `backend/src/main/java/com/alethicode/**` | 完成注释文件扫描（仅自研目录）；进入分批清理阶段 |
| 2026-05-06 | `frontend/src/**` | 完成注释文件扫描（仅自研目录）；进入分批清理阶段 |
| 2026-05-06 | `services/tutor-graph/app/**` | 完成注释文件扫描；历史已审计文件标记为已复核 |

## 2026-05-06 Python 微服务注释批次

本批按 `AGENTS.md` 最新注释规范处理：只保留帮助程序员理解契约、边界和非显见原因的注释；工具指令和技术标识如 `noqa`、`pragma`、`HTTP`、`LightRAG`、`LangGraph` 保留原文。

### `services/alethicode-rag/app`

- `services/alethicode-rag/app/__init__.py`
- `services/alethicode-rag/app/auth.py`
- `services/alethicode-rag/app/config.py`
- `services/alethicode-rag/app/main.py`
- `services/alethicode-rag/app/rag/__init__.py`
- `services/alethicode-rag/app/rag/builder.py`
- `services/alethicode-rag/app/rag/embeddings.py`
- `services/alethicode-rag/app/rag/llm.py`
- `services/alethicode-rag/app/routes/__init__.py`
- `services/alethicode-rag/app/routes/diagnostics.py`
- `services/alethicode-rag/app/routes/health.py`
- `services/alethicode-rag/app/routes/index.py`
- `services/alethicode-rag/app/routes/query.py`
- `services/alethicode-rag/app/schemas.py`
- `services/alethicode-rag/app/tests/__init__.py`
- `services/alethicode-rag/app/tests/test_auth.py`
- `services/alethicode-rag/app/tests/test_embeddings.py`
- `services/alethicode-rag/app/tests/test_health.py`
- `services/alethicode-rag/app/tests/test_llm_wrapper.py`

### `services/tutor-graph/app`

- `services/tutor-graph/app/auth.py`
- `services/tutor-graph/app/clients/java_tools_client.py`
- `services/tutor-graph/app/clients/llm_client.py`
- `services/tutor-graph/app/config.py`
- `services/tutor-graph/app/eval/anti_cheating_judge.py`
- `services/tutor-graph/app/eval/grader.py`
- `services/tutor-graph/app/graph/builder.py`
- `services/tutor-graph/app/graph/checkpoints.py`
- `services/tutor-graph/app/graph/runtime_events.py`
- `services/tutor-graph/app/graph/state.py`
- `services/tutor-graph/app/graph/transitions.py`
- `services/tutor-graph/app/main.py`
- `services/tutor-graph/app/nodes/ac_review.py`
- `services/tutor-graph/app/nodes/actions.py`
- `services/tutor-graph/app/nodes/coach_plan.py`
- `services/tutor-graph/app/nodes/coding.py`
- `services/tutor-graph/app/nodes/diagnosis.py`
- `services/tutor-graph/app/nodes/evidence.py`
- `services/tutor-graph/app/nodes/ideating.py`
- `services/tutor-graph/app/nodes/ingest.py`
- `services/tutor-graph/app/nodes/knowledge_review.py`
- `services/tutor-graph/app/nodes/langfuse_metadata.py`
- `services/tutor-graph/app/nodes/output_normalization.py`
- `services/tutor-graph/app/nodes/output_sanitization.py`
- `services/tutor-graph/app/nodes/parsons.py`
- `services/tutor-graph/app/nodes/projection.py`
- `services/tutor-graph/app/nodes/prompts/__init__.py`
- `services/tutor-graph/app/nodes/prompts/learner_block.py`
- `services/tutor-graph/app/nodes/reading.py`
- `services/tutor-graph/app/nodes/schema_validation.py`
- `services/tutor-graph/app/nodes/skeleton.py`
- `services/tutor-graph/app/nodes/transfer.py`
- `services/tutor-graph/app/nodes/visualize.py`
- `services/tutor-graph/app/observability.py`
- `services/tutor-graph/app/paths.py`
- `services/tutor-graph/app/tests/test_card_schemas.py`
- `services/tutor-graph/app/tests/test_coach_plan.py`
- `services/tutor-graph/app/tests/test_config_checkpointer.py`
- `services/tutor-graph/app/tests/test_interrupt_flow.py`
- `services/tutor-graph/app/tests/test_java_tools_client_visualize.py`
- `services/tutor-graph/app/tests/test_output_normalization.py`
- `services/tutor-graph/app/tests/test_parsons_node.py`
- `services/tutor-graph/app/tests/test_runtime_events.py`
- `services/tutor-graph/app/tests/test_skeleton_node.py`
- `services/tutor-graph/app/tests/test_state_schema.py`
- `services/tutor-graph/app/tests/test_transfer_idempotency.py`
- `services/tutor-graph/app/tests/test_transitions.py`

## 2026-05-06 前端注释批次

本批处理 `frontend/src` 与 `frontend/tests`：删除模板内冗余分区注释，压缩 API / hook / 测试说明，保留 `eslint`、`@type` 等工具指令和 `WebSocket`、`LangGraph` 等技术标识原文。

### `frontend/src`

- `frontend/src/api/httpClient.js`
- `frontend/src/composables/problem/useSubmission.js`
- `frontend/src/composables/useProfileApi.js`
- `frontend/src/i18n/admin/en-US.js`
- `frontend/src/i18n/admin/zh-CN.js`
- `frontend/src/i18n/admin/zh-TW.js`
- `frontend/src/i18n/index.js`
- `frontend/src/i18n/oj/en-US.js`
- `frontend/src/i18n/oj/zh-CN.js`
- `frontend/src/i18n/oj/zh-TW.js`
- `frontend/src/pages/admin/api.js`
- `frontend/src/pages/admin/components/SideMenu.vue`
- `frontend/src/pages/admin/utils/languagePackContext.js`
- `frontend/src/pages/admin/views/general/BetaFeedback.vue`
- `frontend/src/pages/admin/views/general/SecretsInfra.vue`
- `frontend/src/pages/admin/views/general/UsageStats.vue`
- `frontend/src/pages/admin/views/general/User.vue`
- `frontend/src/pages/admin/views/problem/Problem.vue`
- `frontend/src/pages/oj/api.js`
- `frontend/src/pages/oj/api/aiTutor.js`
- `frontend/src/pages/oj/api/beta.js`
- `frontend/src/pages/oj/api/classroom.js`
- `frontend/src/pages/oj/api/conversation.js`
- `frontend/src/pages/oj/api/languagePack.js`
- `frontend/src/pages/oj/api/parsons.js`
- `frontend/src/pages/oj/api/profile.js`
- `frontend/src/pages/oj/api/shared.js`
- `frontend/src/pages/oj/components/BetaFeedbackButton.vue`
- `frontend/src/pages/oj/components/BetaPrivacyNotice.vue`
- `frontend/src/pages/oj/components/SubmissionRiver.vue`
- `frontend/src/pages/oj/components/chat/composerStorage.js`
- `frontend/src/pages/oj/components/chat/useChatComposer.js`
- `frontend/src/pages/oj/components/skillProfile/KnowledgeStarMap.vue`
- `frontend/src/pages/oj/components/skillProfile/PracticeHeatmap.vue`
- `frontend/src/pages/oj/components/skillProfile/ProblemRecommendations.vue`
- `frontend/src/pages/oj/components/skillProfile/StarMapDetailPanel.vue`
- `frontend/src/pages/oj/components/verticalMenu/VerticalMenuItem.vue`
- `frontend/src/pages/oj/index.js`
- `frontend/src/pages/oj/router/routes.js`
- `frontend/src/pages/oj/views/classroom/AIGeneratedProblems.vue`
- `frontend/src/pages/oj/views/classroom/AssignmentDetail.vue`
- `frontend/src/pages/oj/views/classroom/AssignmentGrading.vue`
- `frontend/src/pages/oj/views/classroom/ClassroomAnalytics.vue`
- `frontend/src/pages/oj/views/classroom/ClassroomAssignment.vue`
- `frontend/src/pages/oj/views/classroom/ClassroomDetail.vue`
- `frontend/src/pages/oj/views/classroom/ClassroomList.vue`
- `frontend/src/pages/oj/views/classroom/CollaborativeCoding.vue`
- `frontend/src/pages/oj/views/classroom/LessonManagement.vue`
- `frontend/src/pages/oj/views/general/HomeDashboard.vue`
- `frontend/src/pages/oj/views/general/HomeLanding.vue`
- `frontend/src/pages/oj/views/problem/CalibrationPanel.vue`
- `frontend/src/pages/oj/views/problem/PreflightDialog.vue`
- `frontend/src/pages/oj/views/problem/Problem.styles.less`
- `frontend/src/pages/oj/views/problem/Problem.vue`
- `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- `frontend/src/pages/oj/views/problem/agentContracts.js`
- `frontend/src/pages/oj/views/problem/cards/parsons/ParsonsRenderer.vue`
- `frontend/src/pages/oj/views/problem/preflightDetectors.js`
- `frontend/src/pages/oj/views/problem/useReferenceParse.js`
- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- `frontend/src/pages/oj/views/setting/Settings.vue`
- `frontend/src/pages/oj/views/setting/children/ProfileSetting.vue`
- `frontend/src/pages/oj/views/submission/SubmissionDetails.vue`
- `frontend/src/pages/oj/views/user/ApplyResetPassword.vue`
- `frontend/src/pages/oj/views/user/Login.vue`
- `frontend/src/pages/oj/views/user/ResetPassword.vue`
- `frontend/src/pages/oj/views/user/StandaloneLogin.vue`
- `frontend/src/pages/oj/views/user/StandaloneRegister.vue`
- `frontend/src/pages/oj/views/user/UserHome.vue`
- `frontend/src/store/index.js`
- `frontend/src/store/modules/problem.js`
- `frontend/src/store/modules/user.js`
- `frontend/src/styles/cardAccentTokens.less`
- `frontend/src/styles/cardSizingTokens.less`
- `frontend/src/styles/common.less`
- `frontend/src/utils/betaTelemetry.js`
- `frontend/src/utils/filters.js`
- `frontend/src/utils/storage.js`
- `frontend/src/utils/time.js`
- `frontend/src/utils/utils.js`

### `frontend/tests`

- `frontend/tests/e2e/cards-design-system.spec.js`
- `frontend/tests/e2e/notebook-calendar.spec.js`
- `frontend/tests/e2e/notebook-day-drawer.spec.js`
- `frontend/tests/e2e/review-package-rating.spec.js`
- `frontend/tests/e2e/visual-compare.js`
- `frontend/tests/test_frontend_smoke.js`
- `frontend/tests/unit/admin-beta-feedback-page.spec.js`
- `frontend/tests/unit/agent-card-kc-refs-contract.spec.js`
- `frontend/tests/unit/chat-composer.spec.js`
- `frontend/tests/unit/classroom-assignment-tutor-panel-contract.spec.js`
- `frontend/tests/unit/manual-content-data.spec.js`
- `frontend/tests/unit/manual-page-source.spec.js`
- `frontend/tests/unit/profile-drawer-contract.spec.js`
- `frontend/tests/unit/unified-agent-panel-warning-contract.spec.js`
- `frontend/tests/unit/unified-chat-context-contract.spec.js`
- `frontend/tests/unit/visualize-renderer-contract.spec.js`

## 2026-05-06 后端主代码注释批次

- 处理范围：`backend/src/main/java`
- 处理策略：翻译关键英文解释，删除装饰性分区注释和空 no-op 注释，保留安全、并发、合约、数据一致性相关说明。
- 状态：已完成第一轮主代码扫描；剩余命中主要为 Javadoc/HTML 标签或代码标识。

### `backend/src/main/java`

- `backend/src/main/java/com/alethicode/config/AlethicodeProperties.java`
- `backend/src/main/java/com/alethicode/config/ExternalDependencyHealthConfig.java`
- `backend/src/main/java/com/alethicode/config/InternalServiceKeyMatcher.java`
- `backend/src/main/java/com/alethicode/config/InternalServiceKeyValidator.java`
- `backend/src/main/java/com/alethicode/config/MultiTierCacheConfig.java`
- `backend/src/main/java/com/alethicode/config/RootPasswordValidator.java`
- `backend/src/main/java/com/alethicode/config/TutorWorkflowWebSocketConfig.java`
- `backend/src/main/java/com/alethicode/config/WorkflowWebSocketConfig.java`
- `backend/src/main/java/com/alethicode/controller/AdminNfkController.java`
- `backend/src/main/java/com/alethicode/controller/GlobalRestExceptionHandler.java`
- `backend/src/main/java/com/alethicode/controller/PrivacyController.java`
- `backend/src/main/java/com/alethicode/controller/ProfileController.java`
- `backend/src/main/java/com/alethicode/controller/TutorWorkflowController.java`
- `backend/src/main/java/com/alethicode/controller/internal/InternalAITutorToolController.java`
- `backend/src/main/java/com/alethicode/controller/internal/InternalLanguagePackQualityController.java`
- `backend/src/main/java/com/alethicode/dto/response/AiProviderValidationCaseResult.java`
- `backend/src/main/java/com/alethicode/mcp/AlethicodeMcpToolProvider.java`
- `backend/src/main/java/com/alethicode/middleware/InternalApiKeyFilter.java`
- `backend/src/main/java/com/alethicode/service/adminproblemcommand/AdminPreflightService.java`
- `backend/src/main/java/com/alethicode/service/ai/AiCircuitBreaker.java`
- `backend/src/main/java/com/alethicode/service/ai/AiModelProfileResolver.java`
- `backend/src/main/java/com/alethicode/service/ai/AiProviderValidationService.java`
- `backend/src/main/java/com/alethicode/service/ai/AiResponseNormalizer.java`
- `backend/src/main/java/com/alethicode/service/ai/CachingAiModelGateway.java`
- `backend/src/main/java/com/alethicode/service/ai/PromptSafetyFilter.java`
- `backend/src/main/java/com/alethicode/service/ai/SpringAiModelGateway.java`
- `backend/src/main/java/com/alethicode/service/ai/SpringAiToolLoopService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/AITutorWorkflowDomainService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/LlmResponseCacheService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/WorkflowCheckpointService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/admin/AdminKcManagementService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/admin/AdminMisconceptionMiningService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/admin/AdminVariantReviewService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/CardSummary.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/ConversationContextService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/ConversationMode.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/CoursewareContextProvider.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/CoursewareSummary.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/ReferenceResolver.java`
- `backend/src/main/java/com/alethicode/service/aitutor/context/impl/CoursewareContextProviderImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/EvalDimension.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/EvalResult.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/TutorEvalHarness.java`
- `backend/src/main/java/com/alethicode/service/aitutor/graph/TutorGraphClient.java`
- `backend/src/main/java/com/alethicode/service/aitutor/graph/TutorWorkflowAuthorizer.java`
- `backend/src/main/java/com/alethicode/service/aitutor/graph/TutorWorkflowProjectionService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/impl/AITutorServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/impl/AITutorWorkflowAdminServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/impl/AITutorWorkflowDomainServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/impl/InternalAITutorToolServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/nfk/NfkInferenceService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/observability/AgentObservabilityService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/observability/AgentTraceContext.java`
- `backend/src/main/java/com/alethicode/service/aitutor/parsons/AdaptiveFadingPolicy.java`
- `backend/src/main/java/com/alethicode/service/aitutor/parsons/ParsonsBlock.java`
- `backend/src/main/java/com/alethicode/service/aitutor/parsons/ParsonsCapabilityService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/parsons/ParsonsDistractorGenerator.java`
- `backend/src/main/java/com/alethicode/service/aitutor/parsons/ParsonsTokenSegmenter.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/AITutorWelcomeService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemorySemanticRetrievalService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/quota/AiTutorQuotaService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ReactResult.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolDefinition.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/ToolExecutor.java`
- `backend/src/main/java/com/alethicode/service/aitutor/react/TutorToolRegistry.java`
- `backend/src/main/java/com/alethicode/service/aitutor/reflection/ReflectionResult.java`
- `backend/src/main/java/com/alethicode/service/aitutor/reflection/ReflectionService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/review/ReviewProblemRatingService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/rollout/RolloutPolicyService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/transfer/TransferVerifierService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/visualize/ChartConfigValidator.java`
- `backend/src/main/java/com/alethicode/service/classroom/ClassroomAiProblemService.java`
- `backend/src/main/java/com/alethicode/service/classroom/ClassroomAnalyticsService.java`
- `backend/src/main/java/com/alethicode/service/classroom/ClassroomLessonService.java`
- `backend/src/main/java/com/alethicode/service/classroom/ai/ClassroomAssignmentSmartComposer.java`
- `backend/src/main/java/com/alethicode/service/compliance/AigcComplianceService.java`
- `backend/src/main/java/com/alethicode/service/compliance/PiplDataSubjectService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ConversationContextServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/KcExtractionServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackProblemJudgeCheckService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackProblemPackageMapper.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackQaServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemJudgeMaterializationHelper.java`
- `backend/src/main/java/com/alethicode/service/languagepack/quality/LanguagePackInitQualityReportService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/quality/ReferenceSolutionLinter.java`
- `backend/src/main/java/com/alethicode/service/languagepack/quality/ReferenceSolutionSelfValidator.java`
- `backend/src/main/java/com/alethicode/service/languagepack/quality/SelfValidationCaseResult.java`
- `backend/src/main/java/com/alethicode/service/monitor/ClassroomMonitorService.java`
- `backend/src/main/java/com/alethicode/service/monitor/StudentRiskDetectionService.java`
- `backend/src/main/java/com/alethicode/service/nfk/NfkDataExportService.java`
- `backend/src/main/java/com/alethicode/service/problem/impl/ProblemQueryServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/rag/HttpRagServiceClient.java`
- `backend/src/main/java/com/alethicode/service/rag/RagIndexOutboxWorker.java`
- `backend/src/main/java/com/alethicode/service/rag/RagIndexQueueService.java`
- `backend/src/main/java/com/alethicode/service/rag/RagServiceClient.java`
- `backend/src/main/java/com/alethicode/service/rag/RagServiceException.java`
- `backend/src/main/java/com/alethicode/service/rag/dto/RagEntityType.java`
- `backend/src/main/java/com/alethicode/service/rag/dto/RagIndexAcceptedResponse.java`
- `backend/src/main/java/com/alethicode/service/rag/dto/RagIndexAction.java`
- `backend/src/main/java/com/alethicode/service/rag/dto/RagIndexRequest.java`
- `backend/src/main/java/com/alethicode/service/rag/dto/RagQueryHits.java`
- `backend/src/main/java/com/alethicode/service/submission/SubmissionDataCollector.java`
- `backend/src/main/java/com/alethicode/service/submission/SubmissionJudgeExecutor.java`
- `backend/src/main/java/com/alethicode/service/system/SystemOptionService.java`
- `backend/src/main/java/com/alethicode/util/BoundedParallel.java`
- `backend/src/main/java/com/alethicode/websocket/ClassroomHandshakeInterceptor.java`
- `backend/src/main/java/com/alethicode/websocket/TutorWorkflowWebSocketHandler.java`

## 2026-05-06 后端测试注释批次

- 处理范围：`backend/src/test/java`
- 处理策略：删除测试步骤型行注释，翻译保留的契约类注释。
- 状态：已完成；剩余命中为 Javadoc/HTML 标签。

### `backend/src/test/java`

- `backend/src/test/java/com/alethicode/architecture/PackageBoundaryArchTest.java`
- `backend/src/test/java/com/alethicode/config/InternalServiceKeyValidatorTest.java`
- `backend/src/test/java/com/alethicode/config/MultiTierCacheConfigTest.java`
- `backend/src/test/java/com/alethicode/controller/AdminConfigControllerContractTest.java`
- `backend/src/test/java/com/alethicode/controller/AdminNfkControllerContractTest.java`
- `backend/src/test/java/com/alethicode/controller/BetaFeedbackControllerContractTest.java`
- `backend/src/test/java/com/alethicode/controller/TutorWorkflowControllerTest.java`
- `backend/src/test/java/com/alethicode/integration/AccountAnnouncementAiIntegrationTest.java`
- `backend/src/test/java/com/alethicode/integration/LanguagePackInitIntegrationTest.java`
- `backend/src/test/java/com/alethicode/integration/LanguagePackQaIntegrationTest.java`
- `backend/src/test/java/com/alethicode/integration/SubmissionPermissionQueryIntegrationTest.java`
- `backend/src/test/java/com/alethicode/integration/support/AITutorWorkflowIntegrationTestSupport.java`
- `backend/src/test/java/com/alethicode/middleware/RateLimitFilterTest.java`
- `backend/src/test/java/com/alethicode/service/account/impl/AccountServiceImplApplyResetPasswordTest.java`
- `backend/src/test/java/com/alethicode/service/account/impl/AccountServiceImplLogoutTest.java`
- `backend/src/test/java/com/alethicode/service/account/impl/PasswordResetMailServiceImplTest.java`
- `backend/src/test/java/com/alethicode/service/ai/AiModelProfileResolverTest.java`
- `backend/src/test/java/com/alethicode/service/ai/AiProviderValidationServiceTest.java`
- `backend/src/test/java/com/alethicode/service/ai/SpringAiModelGatewayContractTest.java`
- `backend/src/test/java/com/alethicode/service/ai/SpringAiToolLoopServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/context/ConversationContextServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/context/ReferenceResolverTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/context/impl/CoursewareContextProviderImplTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/contract/FailureBucketTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/graph/TutorWorkflowAuthorizerCacheTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/graph/TutorWorkflowAuthorizerTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/impl/InternalAITutorToolServiceImplTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/observability/AgentTraceRecorderTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/parsons/AdaptiveFadingPolicyTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/parsons/MasteryNfkProjectionServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/parsons/ParsonsCapabilityServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/parsons/ParsonsDistractorGeneratorSqlSmokeTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/parsons/ParsonsDistractorGeneratorTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/parsons/ParsonsTokenSegmenterTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/policy/TransitionPolicyTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/profile/AITutorWelcomeServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/profile/LearnerMemorySemanticRetrievalServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/profile/LearnerNarrativeSummaryServiceTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/profile/LearnerProfileProjectorTest.java`
- `backend/src/test/java/com/alethicode/service/aitutor/rlhf/PromptVariantSelectorTest.java`
- `backend/src/test/java/com/alethicode/service/classroom/ai/ClassroomAssignmentSmartComposerTest.java`
- `backend/src/test/java/com/alethicode/service/classroom/ai/ClassroomKcResolverTest.java`
- `backend/src/test/java/com/alethicode/service/languagepack/impl/LanguagePackQaServiceImplUsageTest.java`
- `backend/src/test/java/com/alethicode/service/languagepack/impl/ProblemJudgeMaterializationHelperTest.java`
- `backend/src/test/java/com/alethicode/service/languagepack/quality/ReferenceSolutionSelfValidatorTest.java`
- `backend/src/test/java/com/alethicode/service/rag/HttpRagServiceClientTest.java`
- `backend/src/test/java/com/alethicode/service/rag/RagIndexOutboxWorkerOfflineCatchupTest.java`
- `backend/src/test/java/com/alethicode/service/rag/RagIndexOutboxWorkerTest.java`
- `backend/src/test/java/com/alethicode/service/rag/RagIndexQueueServiceTest.java`
- `backend/src/test/java/com/alethicode/util/TotpUtilsTest.java`
- `backend/src/test/java/com/alethicode/websocket/TutorWorkflowWebSocketHandlerTest.java`

## 2026-05-06 全仓库注释扩展批次

本批按用户确认的“所有可读文本文件”范围继续处理，排除密钥、二进制、依赖目录、构建产物和运行时数据。处理原则仍为：只改注释，不改业务逻辑；技术标识如 `LLM`、`OpenTelemetry`、`PgBouncer`、`RedTeamCUA`、`OTLP` 保留原文。

### 部署、脚本与工具

- `start.sh`
- `deploy/docker-compose.yml`
- `scripts/ecs_setup.sh`
- `scripts/deploy/ecs_setup.sh`
- `scripts/deploy_env_to_ecs.sh`
- `scripts/deploy/deploy_env_to_ecs.sh`
- `scripts/configure_mirrors_by_ip.sh`
- `scripts/setup-2c4g-host.sh`
- `scripts/ops/generate_sbom.sh`
- `scripts/ops/rag_backfill.py`
- `scripts/modeling/build_activity_diagrams.py`
- `scripts/modeling/build_staruml_mdj.py`
- `scripts/build_activity_diagrams.py`
- `scripts/build_staruml_mdj.py`
- `tools/__init__.py`
- `tools/ai_tutor/__init__.py`
- `tools/ai_tutor/kt_baseline/__init__.py`

### NFK 与研究目录

- `nfk/evaluation/visualizer.py`
- `nfk/configs/ablation.yaml`
- `nfk/configs/ablation_24gb.yaml`
- `nfk/autodl_setup.sh`
- `research/nfk/autodl_setup.sh`

### Python 微服务补充

- `services/alethicode-rag/pyproject.toml`
- `services/judge-server/judge_server/judge_client.py`
- `services/judge-server/judge_server/server.py`
- `services/judge-server/judge_server/compiler.py`
- `services/tutor-graph/app/eval/red_team/__init__.py`
- `services/tutor-graph/app/eval/red_team/assertions.py`
- `services/tutor-graph/app/eval/red_team/build_dataset.py`
- `services/tutor-graph/app/eval/red_team/case_definitions.py`
- `services/tutor-graph/app/eval/red_team/ci_gate.py`
- `services/tutor-graph/app/eval/red_team/decoupled_runner.py`
- `services/tutor-graph/app/eval/red_team/schema.py`
- `services/tutor-graph/app/eval/red_team/targets.py`
- `services/tutor-graph/app/tests/test_actions_policy.py`
- `services/tutor-graph/app/tests/test_chat_node.py`
- `services/tutor-graph/app/tests/test_compact_node.py`
- `services/tutor-graph/app/tests/test_evidence_requirements.py`

### 前端补充

- `frontend/src/pages/admin/views/general/User.vue`
- `frontend/src/pages/oj/views/problem/PreflightDialog.vue`
- `frontend/src/pages/oj/index.js`
- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- `frontend/tests/test_frontend_smoke.js`
- `frontend/tests/e2e/review-package-rating.spec.js`
- `frontend/tests/e2e/notebook-calendar.spec.js`

### 后端 SQL 补充

- `backend/src/main/resources/db/migration/V56__drop_legacy_ai_workflow_tables.sql`
- `backend/src/main/resources/db/migration/V55__ai_tutor_workflow_projection.sql`
- `backend/src/main/resources/db/migration/V58__ai_tutor_workflow_event_client_event_index.sql`
- `backend/src/main/resources/db/migration/V65__ai_tutor_unified_chat_context.sql`
- `backend/src/main/resources/db/migration/V74__beta_feedback_and_telemetry.sql`
